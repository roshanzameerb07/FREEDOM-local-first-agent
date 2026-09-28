package com.example.freedom.domain.ai

data class ToolRequest(
    val intent: ToolIntent,
    val args: Map<String, String>,
    val needsConfirmation: Boolean = false
)
