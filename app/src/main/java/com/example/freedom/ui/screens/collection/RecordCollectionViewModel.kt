package com.example.freedom.ui.screens.collection

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.freedom.data.local.entity.MilkRecordEntity
import com.example.freedom.data.repository.MilkRecordRepository
import com.example.freedom.domain.ai.PatternBasedVoiceExtractor
import com.example.freedom.domain.ai.VoiceRecordExtractorConduit
import com.example.freedom.domain.validation.MilkRecordValidationResult
import com.example.freedom.domain.validation.MilkRecordValidator
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class RecordCollectionUiState(
    val farmerName: String = "",
    val quantity: String = "",
    val fat: String = "",
    val snf: String = "",
    val paymentStatus: String = "PENDING", // Default to PENDING
    val validationResult: MilkRecordValidationResult? = null,
    val saveSuccess: Boolean = false,
    val lastSavedFarmerName: String? = null,
    val voiceTranscriptInput: String = "",
    val isVoiceProcessing: Boolean = false,
    val voiceExtractionMessage: String? = null
)

class RecordCollectionViewModel(
    private val repository: MilkRecordRepository,
    private val validator: MilkRecordValidator = MilkRecordValidator(),
    private val voiceExtractor: VoiceRecordExtractorConduit = PatternBasedVoiceExtractor()
) : ViewModel() {

    private val _uiState = MutableStateFlow(RecordCollectionUiState())
    val uiState: StateFlow<RecordCollectionUiState> = _uiState.asStateFlow()

    fun onFarmerNameChange(value: String) {
        _uiState.update { it.copy(farmerName = value, validationResult = null, saveSuccess = false) }
    }

    fun onQuantityChange(value: String) {
        _uiState.update { it.copy(quantity = value, validationResult = null, saveSuccess = false) }
    }

    fun onFatChange(value: String) {
        _uiState.update { it.copy(fat = value, validationResult = null, saveSuccess = false) }
    }

    fun onSnfChange(value: String) {
        _uiState.update { it.copy(snf = value, validationResult = null, saveSuccess = false) }
    }

    fun onPaymentStatusChange(value: String) {
        _uiState.update { it.copy(paymentStatus = value, validationResult = null, saveSuccess = false) }
    }

    fun onVoiceTranscriptChange(value: String) {
        _uiState.update { it.copy(voiceTranscriptInput = value) }
    }

    /**
     * Demonstrates the future Voice -> Local SLM -> Structured Draft pipeline
     */
    fun processVoiceSample(sampleTranscript: String = "Ramesh gave 18 litres, fat 4.2 and SNF 8.6. Payment is pending.") {
        viewModelScope.launch {
            _uiState.update { it.copy(isVoiceProcessing = true, voiceTranscriptInput = sampleTranscript) }
            val draft = voiceExtractor.extractRecordFromTranscript(sampleTranscript)
            _uiState.update {
                it.copy(
                    farmerName = draft.farmerName,
                    quantity = draft.quantity,
                    fat = draft.fat,
                    snf = draft.snf,
                    paymentStatus = draft.paymentStatus,
                    isVoiceProcessing = false,
                    voiceExtractionMessage = "Extracted from voice transcript via local AI conduit. Please review & save.",
                    validationResult = null
                )
            }
        }
    }

    fun saveRecord(onSuccess: () -> Unit) {
        val currentState = _uiState.value
        val validation = validator.validate(
            farmerName = currentState.farmerName,
            quantityStr = currentState.quantity,
            fatStr = currentState.fat,
            snfStr = currentState.snf,
            paymentStatus = currentState.paymentStatus
        )

        if (!validation.isValid) {
            _uiState.update { it.copy(validationResult = validation, saveSuccess = false) }
            return
        }

        viewModelScope.launch {
            val record = MilkRecordEntity(
                farmerName = currentState.farmerName.trim(),
                quantity = currentState.quantity.toDouble(),
                fat = currentState.fat.toDouble(),
                snf = currentState.snf.toDouble(),
                paymentStatus = currentState.paymentStatus,
                uploadStatus = MilkRecordEntity.UPLOAD_STATUS_PENDING
            )
            repository.insertRecord(record)

            _uiState.update {
                it.copy(
                    farmerName = "",
                    quantity = "",
                    fat = "",
                    snf = "",
                    paymentStatus = "PENDING",
                    validationResult = null,
                    saveSuccess = true,
                    lastSavedFarmerName = record.farmerName,
                    voiceExtractionMessage = null
                )
            }
            onSuccess()
        }
    }

    fun dismissSuccess() {
        _uiState.update { it.copy(saveSuccess = false) }
    }

    fun fillSampleRecord() {
        _uiState.update {
            it.copy(
                farmerName = "Ramesh",
                quantity = "18.0",
                fat = "4.2",
                snf = "8.6",
                paymentStatus = "PENDING",
                validationResult = null,
                saveSuccess = false,
                voiceExtractionMessage = null
            )
        }
    }

    class Factory(private val repository: MilkRecordRepository) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return RecordCollectionViewModel(repository) as T
        }
    }
}
