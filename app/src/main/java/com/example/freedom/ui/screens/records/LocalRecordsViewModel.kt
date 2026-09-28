package com.example.freedom.ui.screens.records

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.freedom.data.local.entity.MilkRecordEntity
import com.example.freedom.data.repository.MilkRecordRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

data class LocalRecordsUiState(
    val searchQuery: String = "",
    val paymentFilter: String? = null, // null = All, "PENDING", "RECORDED_LOCALLY"
    val uploadFilter: String? = null,
    val records: List<MilkRecordEntity> = emptyList(),
    val totalCount: Int = 0,
    val totalQuantityLitres: Double = 0.0,
    val selectedRecordForPayment: MilkRecordEntity? = null
)

class LocalRecordsViewModel(
    private val repository: MilkRecordRepository
) : ViewModel() {

    private val _searchQuery = MutableStateFlow("")
    private val _paymentFilter = MutableStateFlow<String?>(null)
    private val _uploadFilter = MutableStateFlow<String?>(null)
    private val _selectedRecord = MutableStateFlow<MilkRecordEntity?>(null)

    @OptIn(ExperimentalCoroutinesApi::class)
    val uiState: StateFlow<LocalRecordsUiState> = combine(
        _searchQuery,
        _paymentFilter,
        _uploadFilter,
        _selectedRecord
    ) { query, payment, upload, selected ->
        RecordFilters(query, payment, upload, selected)
    }.flatMapLatest { filters ->
        repository.searchAndFilterRecords(
            query = filters.query,
            paymentStatus = filters.payment,
            uploadStatus = filters.upload
        ).map { recordList ->
            LocalRecordsUiState(
                searchQuery = filters.query,
                paymentFilter = filters.payment,
                uploadFilter = filters.upload,
                records = recordList,
                totalCount = recordList.size,
                totalQuantityLitres = recordList.sumOf { it.quantity },
                selectedRecordForPayment = filters.selected
            )
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = LocalRecordsUiState()
    )

    private data class RecordFilters(
        val query: String,
        val payment: String?,
        val upload: String?,
        val selected: MilkRecordEntity?
    )

    fun onSearchQueryChange(query: String) {
        _searchQuery.value = query
    }

    fun onPaymentFilterChange(status: String?) {
        _paymentFilter.value = if (_paymentFilter.value == status) null else status
    }

    fun selectRecordForPayment(record: MilkRecordEntity?) {
        _selectedRecord.value = record
    }

    fun recordPayment(recordId: String, method: String, reference: String) {
        viewModelScope.launch {
            val record = repository.getRecordById(recordId)
            val amount = record?.payableAmount ?: (record?.quantity?.let { it * 37.5 } ?: 0.0)
            repository.updatePayment(
                id = recordId,
                paymentStatus = MilkRecordEntity.PAYMENT_RECORDED_LOCALLY,
                paymentMethod = method,
                paymentReference = reference.takeIf { it.isNotBlank() },
                amountPaid = amount
            )
            _selectedRecord.value = null
        }
    }

    fun clearFilters() {
        _searchQuery.value = ""
        _paymentFilter.value = null
        _uploadFilter.value = null
    }

    class Factory(private val repository: MilkRecordRepository) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return LocalRecordsViewModel(repository) as T
        }
    }
}
