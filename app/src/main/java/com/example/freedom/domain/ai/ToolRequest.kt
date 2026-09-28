package com.example.freedom.domain.ai

/**
 * Execution origin to explicitly distinguish how a request was processed.
 */
enum class ExecutionMode {
    GEMMA_EXECUTED,          // Gemma 3 1B on-device parsed intent & extracted args
    GEMMA_UNAVAILABLE,       // LiteRT engine not initialized or model file missing
    GEMMA_PARSE_FAILED,      // Model inference ran but JSON parsing failed
    DETERMINISTIC_EXECUTION, // Emergency fallback pattern matching was used
    RAG_EXECUTION            // Grounded retrieval over local cooperative documents
}

/**
 * Structured tool execution request produced by on-device Gemma or deterministic fallback.
 *
 * @param intent Selected capability from the tool registry.
 * @param args Validated/reconciled argument key-value pairs.
 * @param rawArgs Original extracted arguments prior to numeric reconciliation.
 * @param needsConfirmation True if user confirmation is mandatory before execution (e.g. database writes).
 * @param clarificationQuestion If intent is ASK_CLARIFICATION, the prompt to show the user.
 * @param rationale Short explanation of why the tool was selected.
 * @param executionMode How this request was generated (Gemma vs. deterministic fallback).
 */
data class ToolRequest(
    val intent: ToolIntent,
    val args: Map<String, String>,
    val rawArgs: Map<String, String> = emptyMap(),
    val needsConfirmation: Boolean = false,
    val clarificationQuestion: String? = null,
    val rationale: String? = null,
    val executionMode: ExecutionMode = ExecutionMode.GEMMA_EXECUTED
)
