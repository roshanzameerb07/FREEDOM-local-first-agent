package com.example.freedom.framework.model

import com.example.freedom.framework.organization.ModelProviderType

/**
 * Abstract model provider interface.
 *
 * The FREEDOM framework treats model inference as a pluggable boundary:
 *
 * INPUT:  natural-language question
 * OUTPUT: canonical FreedomQuery-compatible semantic representation (JSON)
 *
 * The rest of FREEDOM does not care whether the underlying model is:
 * - Qwen3-1.7B (current FREEDOM-provided model)
 * - Gemma
 * - Another supported local model
 * - A future organization-specific model
 *
 * Design Principles:
 * - Model output is UNTRUSTED INPUT — always validated before execution
 * - Model cannot determine identity, permissions, or security policy
 * - Provider is replaceable without changing FREEDOM Core
 */
interface ModelProvider {

    /** Unique identifier for this model provider */
    val providerId: String

    /** Human-readable display name */
    val displayName: String

    /** Provider type classification */
    val providerType: ModelProviderType

    /** Whether this provider is currently ready for inference */
    fun isAvailable(): Boolean

    /**
     * Generate text from a prompt using the underlying model.
     *
     * @param prompt The full prompt including system instructions.
     * @return The model's raw text response, or null if unavailable.
     */
    suspend fun generateText(prompt: String): String?

    /**
     * Get metadata about this model provider.
     */
    fun getModelInfo(): ModelInfo
}

/**
 * Metadata about a model provider.
 */
data class ModelInfo(
    /** Unique model identifier */
    val modelId: String,

    /** Human-readable model name */
    val displayName: String,

    /** Provider type */
    val providerType: ModelProviderType,

    /** Local path or reference to model artifact */
    val artifactPath: String? = null,

    /** Maximum context length in tokens */
    val contextLength: Int = 2048,

    /** Expected output contract description */
    val outputContract: String = "FreedomQuery JSON",

    /** Whether the model is currently active */
    val isActive: Boolean = true,

    /** Backend used (e.g., CPU, GPU) */
    val backend: String = "CPU",

    /** Initialization time in milliseconds */
    val initDurationMs: Long = 0L,

    /** Last inference time in milliseconds */
    val lastInferenceTimeMs: Long = 0L,

    /** Last error message, if any */
    val lastError: String? = null
)
