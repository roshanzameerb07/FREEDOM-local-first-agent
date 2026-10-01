package com.example.freedom.framework.model

import android.util.Log
import com.example.freedom.framework.organization.ModelProviderType
import com.google.ai.edge.litertlm.Backend
import com.google.ai.edge.litertlm.Content
import com.google.ai.edge.litertlm.Engine
import com.google.ai.edge.litertlm.EngineConfig
import com.google.ai.edge.litertlm.Message
import com.google.ai.edge.litertlm.MessageCallback
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Real on-device model provider that loads a .litertlm file from FREEDOM private storage.
 *
 * Uses the exact same LiteRT-LM API pattern as [com.example.freedom.domain.ai.AIEngineProvider]:
 * engine.createConversation() → sendMessageAsync() → MessageCallback.
 *
 * Lifecycle:
 * 1. Constructed with a [LocalModelManifest]
 * 2. [initialize()] must be called before [generateText()]
 * 3. [release()] must be called when the provider is no longer needed
 *
 * Design Principles:
 * - Model output is UNTRUSTED INPUT — caller must validate before execution
 * - This provider never sets organizationId, userId, or any security context
 * - Only one imported model is active at a time (enforced by ModelProviderRegistry)
 */
class LocalLitertModelProvider(
    private val manifest: LocalModelManifest
) : ModelProvider {

    companion object {
        private const val TAG = "LocalLitertModelProvider"
    }

    override val providerId: String = manifest.modelId
    override val displayName: String = manifest.displayName
    override val providerType: ModelProviderType = when (manifest.source) {
        ModelSource.FREEDOM_PROVIDED -> ModelProviderType.FREEDOM_PROVIDED
        ModelSource.ORGANIZATION_PROVIDED -> ModelProviderType.ORGANIZATION_PROVIDED
        ModelSource.USER_IMPORTED -> ModelProviderType.USER_IMPORTED
    }

    @Volatile
    private var engine: Engine? = null

    @Volatile
    private var backendUsed: String = "Unknown"

    @Volatile
    private var initError: String? = null

    @Volatile
    private var initDurationMs: Long = 0L

    @Volatile
    private var lastInferenceTimeMs: Long = 0L

    /**
     * Initialize the LiteRT-LM engine from the model file path.
     * Attempts GPU first, falls back to CPU — mirroring the pattern in MyApplication.
     *
     * @return true if initialization succeeded, false otherwise
     */
    suspend fun initialize(): Boolean = withContext(Dispatchers.IO) {
        if (engine != null && engine!!.isInitialized()) return@withContext true

        val modelFile = File(manifest.localPath)
        if (!modelFile.exists()) {
            initError = "Model file not found: ${manifest.localPath}"
            Log.e(TAG, initError!!)
            return@withContext false
        }

        val startMs = System.currentTimeMillis()
        return@withContext try {
            var engineInstance: Engine? = null

            // Attempt GPU first, fall back to CPU
            try {
                val gpuConfig = EngineConfig(
                    modelPath = manifest.localPath,
                    backend = Backend.GPU()
                )
                engineInstance = Engine(gpuConfig)
                engineInstance.initialize()
                backendUsed = "GPU"
                Log.i(TAG, "Engine initialized with GPU for model: ${manifest.modelId}")
            } catch (gpuEx: Throwable) {
                Log.w(TAG, "GPU init failed (${gpuEx.message}); trying CPU...")
                val cpuConfig = EngineConfig(
                    modelPath = manifest.localPath,
                    backend = Backend.CPU()
                )
                engineInstance = Engine(cpuConfig)
                engineInstance.initialize()
                backendUsed = "CPU"
                Log.i(TAG, "Engine initialized with CPU for model: ${manifest.modelId}")
            }

            engine = engineInstance
            initDurationMs = System.currentTimeMillis() - startMs
            initError = null
            true
        } catch (e: Exception) {
            initError = "Engine init failed: ${e.message}"
            initDurationMs = System.currentTimeMillis() - startMs
            Log.e(TAG, initError!!, e)
            false
        }
    }

    override fun isAvailable(): Boolean = engine?.isInitialized() == true

    override suspend fun generateText(prompt: String): String? = withContext(Dispatchers.IO) {
        val localEngine = engine ?: run {
            Log.w(TAG, "generateText called but engine is not initialized")
            return@withContext null
        }
        if (!localEngine.isInitialized()) {
            Log.w(TAG, "Engine not initialized")
            return@withContext null
        }

        val startMs = System.currentTimeMillis()
        return@withContext try {
            val conversation = localEngine.createConversation()
            try {
                suspendCancellableCoroutine { cont ->
                    val sb = StringBuilder()
                    conversation.sendMessageAsync(prompt, object : MessageCallback {
                        override fun onMessage(message: Message) {
                            for (content in message.contents.contents) {
                                if (content is Content.Text) {
                                    sb.append(content.text)
                                }
                            }
                        }

                        override fun onDone() {
                            lastInferenceTimeMs = System.currentTimeMillis() - startMs
                            Log.d(TAG, "Inference completed in ${lastInferenceTimeMs}ms")
                            if (cont.isActive) cont.resume(sb.toString().trim())
                        }

                        override fun onError(throwable: Throwable) {
                            lastInferenceTimeMs = System.currentTimeMillis() - startMs
                            initError = throwable.message
                            Log.e(TAG, "Inference error: ${throwable.message}")
                            if (cont.isActive) cont.resumeWithException(throwable)
                        }
                    })
                }
            } finally {
                try { conversation.close() } catch (_: Throwable) {}
            }
        } catch (e: Throwable) {
            lastInferenceTimeMs = System.currentTimeMillis() - startMs
            Log.e(TAG, "generateText failed: ${e.message}")
            null
        }
    }

    override fun getModelInfo(): ModelInfo = ModelInfo(
        modelId = manifest.modelId,
        displayName = manifest.displayName,
        providerType = providerType,
        artifactPath = manifest.localPath,
        contextLength = manifest.contextLength,
        outputContract = manifest.semanticContract,
        isActive = manifest.isActive,
        backend = backendUsed,
        initDurationMs = initDurationMs,
        lastInferenceTimeMs = lastInferenceTimeMs,
        lastError = initError,
        source = manifest.source,
        runtimeType = manifest.runtimeType,
        artifactType = manifest.artifactType,
        fileName = manifest.fileName,
        fileSize = manifest.fileSize,
        sha256 = manifest.sha256,
        organizationId = manifest.organizationId,
        compatibilityStatus = manifest.compatibilityStatus
    )

    /**
     * Release the LiteRT-LM engine and free native resources.
     * Call this when replacing or removing the model.
     */
    fun release() {
        try {
            engine?.close()
        } catch (e: Exception) {
            Log.w(TAG, "Error releasing engine", e)
        } finally {
            engine = null
        }
    }
}
