package com.example.freedom.ui.screens.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.freedom.data.repository.AuthRepository
import com.example.freedom.data.repository.AuthUser
import com.example.freedom.data.repository.MilkRecordRepository
import kotlinx.coroutines.flow.*

data class HomeUiState(
    val todayLitres: Double = 0.0,
    val recordsTodayCount: Int = 0,
    val pendingUploadCount: Int = 0,
    val totalRecordsCount: Int = 0,
    val currentUser: AuthUser? = null
)

class HomeViewModel(
    private val repository: MilkRecordRepository,
    private val authRepository: AuthRepository
) : ViewModel() {

    val uiState: StateFlow<HomeUiState> = combine(
        repository.getTodayTotalQuantity(),
        repository.getTodayRecordsCount(),
        repository.getPendingUploadCount(),
        repository.getAllRecordsCount(),
        authRepository.currentUser
    ) { litres, todayCount, pendingCount, totalCount, user ->
        HomeUiState(
            todayLitres = litres,
            recordsTodayCount = todayCount,
            pendingUploadCount = pendingCount,
            totalRecordsCount = totalCount,
            currentUser = user
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = HomeUiState()
    )

    fun logout() {
        authRepository.logout()
    }

    class Factory(
        private val repository: MilkRecordRepository,
        private val authRepository: AuthRepository
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return HomeViewModel(repository, authRepository) as T
        }
    }
}
