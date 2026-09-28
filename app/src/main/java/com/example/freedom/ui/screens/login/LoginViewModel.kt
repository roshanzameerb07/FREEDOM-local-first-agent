package com.example.freedom.ui.screens.login

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.example.freedom.data.repository.AuthRepository
import com.example.freedom.data.repository.AuthUser
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

data class LoginUiState(
    val orgId: String = "ORG001",
    val workerId: String = "WORKER001",
    val password: String = "1234",
    val errorMessage: String? = null,
    val isLoading: Boolean = false,
    val loggedInUser: AuthUser? = null
)

class LoginViewModel(
    private val authRepository: AuthRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(LoginUiState())
    val uiState: StateFlow<LoginUiState> = _uiState.asStateFlow()

    fun onOrgIdChange(value: String) {
        _uiState.update { it.copy(orgId = value, errorMessage = null) }
    }

    fun onWorkerIdChange(value: String) {
        _uiState.update { it.copy(workerId = value, errorMessage = null) }
    }

    fun onPasswordChange(value: String) {
        _uiState.update { it.copy(password = value, errorMessage = null) }
    }

    fun login(onSuccess: () -> Unit) {
        val currentState = _uiState.value
        val result = authRepository.login(
            orgId = currentState.orgId,
            workerId = currentState.workerId,
            password = currentState.password
        )

        result.onSuccess { user ->
            _uiState.update { it.copy(loggedInUser = user, errorMessage = null) }
            onSuccess()
        }.onFailure { error ->
            _uiState.update { it.copy(errorMessage = error.message ?: "Authentication failed") }
        }
    }

    fun fillDemoCredentials() {
        _uiState.update {
            it.copy(
                orgId = AuthRepository.DEMO_ORG_ID,
                workerId = AuthRepository.DEMO_WORKER_ID,
                password = AuthRepository.DEMO_PASSWORD,
                errorMessage = null
            )
        }
    }

    class Factory(private val authRepository: AuthRepository) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return LoginViewModel(authRepository) as T
        }
    }
}
