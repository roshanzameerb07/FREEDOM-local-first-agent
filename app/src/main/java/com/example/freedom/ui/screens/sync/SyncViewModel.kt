package com.example.freedom.ui.screens.sync

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.freedom.data.repository.MilkRecordRepository
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

data class SyncUiState(
    val todayRecordsCount: Int = 0,
    val pendingUploadCount: Int = 0,
    val totalRecordsCount: Int = 0,
    val isSyncing: Boolean = false,
    val lastSyncMessage: String? = null,
    val isDemoSimulationMode: Boolean = true,
    val uploadWindow: String = "8:00 PM (Configured Batch Window)"
)

class SyncViewModel(
    private val repository: MilkRecordRepository
) : ViewModel() {

    private val _isSyncing = MutableStateFlow(false)
    private val _syncMessage = MutableStateFlow<String?>(null)

    val uiState: StateFlow<SyncUiState> = combine(
        repository.getTodayRecordsCount(),
        repository.getPendingUploadCount(),
        repository.getAllRecordsCount(),
        _isSyncing,
        _syncMessage
    ) { todayCount, pendingCount, totalCount, syncing, message ->
        SyncUiState(
            todayRecordsCount = todayCount,
            pendingUploadCount = pendingCount,
            totalRecordsCount = totalCount,
            isSyncing = syncing,
            lastSyncMessage = message
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = SyncUiState()
    )

    /**
     * Executes local batch sync simulation.
     * Explicitly labeled as simulation — does NOT claim remote cloud server contact or cryptographic operations.
     */
    fun triggerSimulatedBatchSync() {
        viewModelScope.launch {
            _isSyncing.value = true
            _syncMessage.value = "Demo batch packaging: preparing local records for bundle..."
            delay(1000)

            _syncMessage.value = "Processing local batch simulation (No remote server contacted)..."
            delay(800)

            val updatedCount = repository.simulateBatchSync()

            _syncMessage.value = if (updatedCount > 0) {
                "Simulation complete: $updatedCount pending record(s) transitioned to 'UPLOADED' status locally. (No remote server contacted)."
            } else {
                "Simulation complete: All records are already up to date locally (0 pending records). (No remote server contacted)."
            }
            _isSyncing.value = false
        }
    }

    class Factory(private val repository: MilkRecordRepository) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return SyncViewModel(repository) as T
        }
    }
}
