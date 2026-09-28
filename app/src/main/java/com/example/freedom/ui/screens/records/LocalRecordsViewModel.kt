package com.example.freedom.ui.screens.records

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.freedom.data.local.entity.MilkRecordEntity
import com.example.freedom.data.repository.MilkRecordRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*

data class LocalRecordsUiState(
    val searchQuery: String = "",
    val paymentFilter: String? = null, // null = All, "PENDING", "PAID"
    val uploadFilter: String? = null,   // null = All, "PENDING", "UPLOADED"
    val records: List<MilkRecordEntity> = emptyList(),
    val totalCount: Int = 0,
    val totalQuantityLitres: Double = 0.0
)

class LocalRecordsViewModel(
    private val repository: MilkRecordRepository
) : ViewModel() {

    private val _searchQuery = MutableStateFlow("")
    private val _paymentFilter = MutableStateFlow<String?>(null)
    private val _uploadFilter = MutableStateFlow<String?>(null)

    @OptIn(ExperimentalCoroutinesApi::class)
    val uiState: StateFlow<LocalRecordsUiState> = combine(
        _searchQuery,
        _paymentFilter,
        _uploadFilter
    ) { query, payment, upload ->
        Triple(query, payment, upload)
    }.flatMapLatest { (query, payment, upload) ->
        repository.searchAndFilterRecords(
            query = query,
            paymentStatus = payment,
            uploadStatus = upload
        ).map { recordList ->
            LocalRecordsUiState(
                searchQuery = query,
                paymentFilter = payment,
                uploadFilter = upload,
                records = recordList,
                totalCount = recordList.size,
                totalQuantityLitres = recordList.sumOf { it.quantity }
            )
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = LocalRecordsUiState()
    )

    fun onSearchQueryChange(query: String) {
        _searchQuery.value = query
    }

    fun onPaymentFilterChange(status: String?) {
        _paymentFilter.value = if (_paymentFilter.value == status) null else status
    }

    fun onUploadFilterChange(status: String?) {
        _uploadFilter.value = if (_uploadFilter.value == status) null else status
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
