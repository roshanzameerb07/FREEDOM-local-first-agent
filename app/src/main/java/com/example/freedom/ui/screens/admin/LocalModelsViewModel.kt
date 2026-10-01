package com.example.freedom.ui.screens.admin

import android.app.Application
import android.net.Uri
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.freedom.framework.model.*
import com.example.freedom.framework.session.SessionManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * ViewModel for the admin Local Models management screen.
 *
 * Manages:
 * - Listing all known model manifests (FREEDOM-provided + imported)
 * - Importing a new model from a SAF URI
 * - Running the compatibility check
 * - Activating a compatible model
 * - Removing an imported model
 *
 * All organizational identity comes from [SessionManager], never from model output.
 */
class LocalModelsViewModel(application: Application) : AndroidViewModel(application) {

    companion object {
        private const val TAG = "LocalModelsViewModel"
    }

    private val importManager = ModelImportManager(application)

    // In-memory manifest registry (persisted per-session; future: persist to Room)
    private val manifests = mutableListOf<LocalModelManifest>()
    private var activeProvider: LocalLitertModelProvider? = null

    sealed class UiState {
        object Idle : UiState()
        object Importing : UiState()
        data class Checking(val modelId: String) : UiState()
        data class Error(val message: String) : UiState()
        data class Info(val message: String) : UiState()
    }

    private val _uiState = MutableStateFlow<UiState>(UiState.Idle)
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    private val _manifestList = MutableStateFlow<List<LocalModelManifest>>(emptyList())
    val manifestList: StateFlow<List<LocalModelManifest>> = _manifestList.asStateFlow()

    private val _activeManifestId = MutableStateFlow<String?>(null)
    val activeManifestId: StateFlow<String?> = _activeManifestId.asStateFlow()

    /** The FREEDOM-built-in Qwen model info (always first in the list) */
    val freedomModelInfo: ModelInfo? get() =
        ModelProviderRegistry.getActiveModelInfo()?.takeIf {
            it.source == null || it.source == ModelSource.FREEDOM_PROVIDED
        }

    init {
        // Reflect any already-registered manifests
        refresh()
    }

    private fun refresh() {
        _manifestList.value = manifests.toList()
    }

    /**
     * Import a model from a SAF URI. Requires an authenticated session for org scoping.
     */
    fun importModel(uri: Uri, displayName: String? = null) {
        val session = SessionManager.getCurrentSessionOrNull()
        val orgId = session?.organizationId ?: run {
            _uiState.value = UiState.Error("No active session. Please log in first.")
            return
        }

        viewModelScope.launch {
            _uiState.value = UiState.Importing
            when (val result = importManager.importModel(uri, orgId, displayName)) {
                is ModelImportManager.ImportResult.Success -> {
                    manifests.add(result.manifest)
                    refresh()
                    _uiState.value = UiState.Info(
                        "Model \"${result.manifest.displayName}\" imported successfully. Run compatibility check before activating."
                    )
                }
                is ModelImportManager.ImportResult.Error -> {
                    _uiState.value = UiState.Error(result.message)
                }
            }
        }
    }

    /**
     * Run the real compatibility check for an imported model.
     * Only COMPATIBLE models can be activated.
     */
    fun checkCompatibility(modelId: String) {
        val index = manifests.indexOfFirst { it.modelId == modelId }
        if (index == -1) {
            _uiState.value = UiState.Error("Model not found: $modelId")
            return
        }

        viewModelScope.launch {
            _uiState.value = UiState.Checking(modelId)
            val manifest = manifests[index]
            val provider = LocalLitertModelProvider(manifest)

            val result = ModelCompatibilityChecker.check(provider)

            // Release after check — do not keep the engine alive until activation
            provider.release()

            val updatedManifest = manifest.copy(
                status = when (result.status) {
                    ModelCompatibilityStatus.COMPATIBLE -> ModelLifecycleStatus.COMPATIBLE_VERIFIED
                    ModelCompatibilityStatus.INCOMPATIBLE -> ModelLifecycleStatus.INCOMPATIBLE
                    ModelCompatibilityStatus.LOAD_FAILED -> ModelLifecycleStatus.INCOMPATIBLE
                },
                compatibilityStatus = result.status,
                compatibilityReason = result.reason
            )
            manifests[index] = updatedManifest
            refresh()

            _uiState.value = when (result.status) {
                ModelCompatibilityStatus.COMPATIBLE ->
                    UiState.Info("Compatible ✓ — \"${manifest.displayName}\" passed the FREEDOM test. You can now activate it.")
                ModelCompatibilityStatus.INCOMPATIBLE ->
                    UiState.Error("Incompatible — \"${manifest.displayName}\": ${result.reason}")
                ModelCompatibilityStatus.LOAD_FAILED ->
                    UiState.Error("Load failed — \"${manifest.displayName}\": ${result.reason}")
            }
        }
    }

    /**
     * Activate a compatible imported model as the active inference provider.
     * Replaces the current provider (FREEDOM-built-in or previously imported).
     */
    fun activateModel(modelId: String) {
        val index = manifests.indexOfFirst { it.modelId == modelId }
        if (index == -1) {
            _uiState.value = UiState.Error("Model not found: $modelId")
            return
        }

        val manifest = manifests[index]
        if (manifest.compatibilityStatus != ModelCompatibilityStatus.COMPATIBLE) {
            _uiState.value = UiState.Error("Only compatible models can be activated. Run compatibility check first.")
            return
        }

        viewModelScope.launch {
            _uiState.value = UiState.Importing // Reuse "loading" state

            // Release any previously active imported provider
            activeProvider?.release()
            activeProvider = null

            val newProvider = LocalLitertModelProvider(manifest)
            val initialized = newProvider.initialize()

            if (!initialized) {
                _uiState.value = UiState.Error("Failed to load model for activation. The file may be corrupted.")
                return@launch
            }

            // Register and activate in ModelProviderRegistry
            ModelProviderRegistry.register(newProvider)
            ModelProviderRegistry.activate(newProvider.providerId)

            activeProvider = newProvider

            // Update manifest status
            val deactivatedPrev = manifests.map { m ->
                if (m.status == ModelLifecycleStatus.ACTIVATED && m.modelId != modelId)
                    m.copy(status = ModelLifecycleStatus.DEACTIVATED)
                else m
            }
            manifests.clear()
            manifests.addAll(deactivatedPrev)
            manifests[manifests.indexOfFirst { it.modelId == modelId }] =
                manifest.copy(status = ModelLifecycleStatus.ACTIVATED)

            _activeManifestId.value = modelId
            refresh()
            _uiState.value = UiState.Info("\"${manifest.displayName}\" is now the active model.")
        }
    }

    /**
     * Switch back to the FREEDOM built-in Qwen model.
     */
    fun useFreedomModel() {
        viewModelScope.launch {
            activeProvider?.release()
            activeProvider = null
            ModelProviderRegistry.disableActiveProvider()

            // Mark all imported manifests as deactivated
            val updated = manifests.map { m ->
                if (m.status == ModelLifecycleStatus.ACTIVATED)
                    m.copy(status = ModelLifecycleStatus.DEACTIVATED)
                else m
            }
            manifests.clear()
            manifests.addAll(updated)
            _activeManifestId.value = null
            refresh()
            _uiState.value = UiState.Info("Switched back to the FREEDOM built-in model.")
        }
    }

    /**
     * Remove an imported model: deactivate if active, release resources, delete file, remove manifest.
     */
    fun removeModel(modelId: String) {
        val index = manifests.indexOfFirst { it.modelId == modelId }
        if (index == -1) return

        val manifest = manifests[index]
        viewModelScope.launch {
            // If this model is the active provider, switch back to built-in first
            if (manifest.status == ModelLifecycleStatus.ACTIVATED) {
                useFreedomModel()
            }

            // Delete the physical file
            importManager.deleteModelFile(manifest)

            // Remove from registry and list
            manifests.removeAt(manifests.indexOfFirst { it.modelId == modelId })
            if (_activeManifestId.value == modelId) _activeManifestId.value = null
            refresh()
            _uiState.value = UiState.Info("\"${manifest.displayName}\" has been removed.")
        }
    }

    fun clearMessage() {
        _uiState.value = UiState.Idle
    }

    override fun onCleared() {
        super.onCleared()
        activeProvider?.release()
    }

    class Factory(private val application: Application) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return LocalModelsViewModel(application) as T
        }
    }
}
