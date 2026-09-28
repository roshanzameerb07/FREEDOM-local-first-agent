package com.example.freedom.domain.ai

/**
 * Structured tool execution request produced by on-device Gemma.
 *
 * @param intent Selected capability from the tool registry.
 * @param args Validated/reconciled argument key-value pairs.
 * @param rawArgs Original extracted arguments prior to numeric reconciliation.
 * @param needsConfirmation True if user confirmation is mandatory before execution (e.g. database writes).
 * @param clarificationQuestion If intent is ASK_CLARIFICATION, the prompt to show the user.
 * @param rationale Short explanation of why the tool was selected.
 */
data class ToolRequest(
    val intent: ToolIntent,
    val args: Map<String, String>,
    val rawArgs: Map<String, String> = emptyMap(),
    val needsConfirmation: Boolean = false,
    val clarificationQuestion: String? = null,
    val rationale: String? = null
)
