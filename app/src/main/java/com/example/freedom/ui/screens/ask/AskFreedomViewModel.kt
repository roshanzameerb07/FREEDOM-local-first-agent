package com.example.freedom.ui.screens.ask

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.freedom.data.repository.MilkRecordRepository
import com.example.freedom.domain.ai.LocalDeterministicQueryEngine
import com.example.freedom.domain.ai.QueryEngineResult
import com.example.freedom.domain.ai.LocalEngineResult
import com.example.freedom.domain.ai.ToolRequest
import com.example.freedom.domain.ai.ToolExecutor
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class AskFreedomUiState(
    val queryInput: String = "",
    val isLoading: Boolean = false,
    val lastResult: QueryEngineResult? = null,
    val confirmationRequest: com.example.freedom.domain.ai.ToolRequest? = null,
    val executionResult: com.example.freedom.domain.ai.ToolExecutor.ExecutionResult? = null
)

class AskFreedomViewModel(
    private val repository: MilkRecordRepository,
    private val queryEngine: LocalDeterministicQueryEngine = LocalDeterministicQueryEngine(repository),
    private val toolExecutor: ToolExecutor = ToolExecutor(repository)
) : ViewModel() {

    private val _uiState = MutableStateFlow(AskFreedomUiState())
    val uiState: StateFlow<AskFreedomUiState> = _uiState.asStateFlow()

    fun onQueryInputChange(value: String) {
        _uiState.update { it.copy(queryInput = value) }
    }

    fun submitQuery(prompt: String = _uiState.value.queryInput) {
        if (prompt.trim().isEmpty()) return

        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isLoading = true,
                    queryInput = prompt,
                    lastResult = null,
                    executionResult = null,
                    confirmationRequest = null
                )
            }
            val result = queryEngine.executeQuery(prompt)
            when (result) {
                is com.example.freedom.domain.ai.LocalEngineResult.ToolResult -> {
                    val request = result.request
                    if (request.needsConfirmation) {
                        _uiState.update { it.copy(isLoading = false, confirmationRequest = request) }
                    } else {
                        val execResult = toolExecutor.execute(request)
                        _uiState.update { it.copy(isLoading = false, executionResult = execResult) }
                    }
                }
                is com.example.freedom.domain.ai.LocalEngineResult.SummaryResult -> {
                    _uiState.update { it.copy(isLoading = false, lastResult = result.summary) }
                }
            }
        }
    }

    fun confirmToolExecution() {
        val request = _uiState.value.confirmationRequest ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            val execResult = toolExecutor.execute(request)
            _uiState.update { it.copy(isLoading = false, confirmationRequest = null, executionResult = execResult) }
        }
    }

    fun cancelToolExecution() {
        _uiState.update { it.copy(confirmationRequest = null) }
    }

    class Factory(private val repository: MilkRecordRepository) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return AskFreedomViewModel(repository) as T
        }
    }
}
