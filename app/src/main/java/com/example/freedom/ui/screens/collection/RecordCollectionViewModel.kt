package com.example.freedom.ui.screens.collection

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.freedom.data.local.entity.MilkRecordEntity
import com.example.freedom.data.repository.MilkRecordRepository
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
    val isPaymentRecorded: Boolean = false,
    val paymentMethod: String = MilkRecordEntity.METHOD_CASH,
    val paymentReference: String = "",
    val validationResult: MilkRecordValidationResult? = null,
    val saveSuccess: Boolean = false,
    val lastSavedFarmerName: String? = null,
    val isReviewing: Boolean = false
) {
    val estimatedAmount: Double
        get() {
            val q = quantity.toDoubleOrNull() ?: 0.0
            val f = fat.toDoubleOrNull() ?: 0.0
            val s = snf.toDoubleOrNull() ?: 0.0
            return MilkRecordEntity.calculatePayableAmount(q, f, s)
        }
}

class RecordCollectionViewModel(
    private val repository: MilkRecordRepository,
    private val validator: MilkRecordValidator = MilkRecordValidator()
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

    fun onPaymentRecordedToggle(recorded: Boolean) {
        _uiState.update { it.copy(isPaymentRecorded = recorded, validationResult = null) }
    }

    fun onPaymentMethodChange(method: String) {
        _uiState.update { it.copy(paymentMethod = method) }
    }

    fun onPaymentReferenceChange(ref: String) {
        _uiState.update { it.copy(paymentReference = ref) }
    }

    fun onReviewClick() {
        val state = _uiState.value
        val validation = validator.validate(
            farmerName = state.farmerName,
            quantityStr = state.quantity,
            fatStr = state.fat,
            snfStr = state.snf,
            paymentStatus = if (state.isPaymentRecorded) MilkRecordEntity.PAYMENT_RECORDED_LOCALLY else MilkRecordEntity.PAYMENT_PENDING,
            paymentMethod = if (state.isPaymentRecorded) state.paymentMethod else null,
            paymentReference = if (state.isPaymentRecorded) state.paymentReference else null
        )
        if (!validation.isValid) {
            _uiState.update { it.copy(validationResult = validation) }
        } else {
            _uiState.update { it.copy(validationResult = null, isReviewing = true) }
        }
    }

    fun onDismissReview() {
        _uiState.update { it.copy(isReviewing = false) }
    }

    fun confirmAndSave(onSuccess: () -> Unit) {
        val state = _uiState.value
        viewModelScope.launch {
            val q = state.quantity.toDouble()
            val f = state.fat.toDouble()
            val s = state.snf.toDouble()
            val amount = MilkRecordEntity.calculatePayableAmount(q, f, s)
            val paymentStatus = if (state.isPaymentRecorded) MilkRecordEntity.PAYMENT_RECORDED_LOCALLY else MilkRecordEntity.PAYMENT_PENDING
            val now = System.currentTimeMillis()

            val profile = com.example.freedom.domain.model.WorkerProfileRepository.getProfile()
            val record = MilkRecordEntity(
                orgId = profile.organizationId,
                workerId = profile.workerId,
                farmerName = state.farmerName.trim(),
                quantity = q,
                fat = f,
                snf = s,
                paymentStatus = paymentStatus,
                paymentMethod = if (state.isPaymentRecorded) state.paymentMethod else null,
                paymentReference = if (state.isPaymentRecorded && state.paymentReference.isNotBlank()) state.paymentReference.trim() else null,
                paymentTimestamp = if (state.isPaymentRecorded) now else null,
                payableAmount = amount,
                amountPaid = if (state.isPaymentRecorded) amount else null,
                createdAt = now,
                updatedAt = now,
                uploadStatus = MilkRecordEntity.UPLOAD_STATUS_PENDING
            )
            repository.insertRecord(record)

            _uiState.update {
                it.copy(
                    farmerName = "",
                    quantity = "",
                    fat = "",
                    snf = "",
                    isPaymentRecorded = false,
                    paymentReference = "",
                    isReviewing = false,
                    saveSuccess = true,
                    lastSavedFarmerName = record.farmerName
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
                quantity = "18.5",
                fat = "4.2",
                snf = "8.6",
                isPaymentRecorded = false,
                validationResult = null,
                saveSuccess = false
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
