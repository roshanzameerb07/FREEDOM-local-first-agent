package com.example.freedom.framework.model

import com.example.freedom.domain.ai.AIEngineProvider
import com.example.freedom.framework.organization.ModelProviderType

/**
 * Adapter that wraps the existing [AIEngineProvider] singleton as a [ModelProvider].
 *
 * This bridges the current Qwen3-1.7B LiteRT-LM integration into the
 * framework's pluggable model provider abstraction without modifying
 * the existing AIEngineProvider code.
 *
 * This is the FREEDOM-provided model. Organizations may register
 * alternative providers via [ModelProviderRegistry].
 */
class QwenModelProvider : ModelProvider {

    override val providerId: String = PROVIDER_ID

    override val displayName: String = "Qwen3-1.7B INT4 (FREEDOM)"

    override val providerType: ModelProviderType = ModelProviderType.FREEDOM_PROVIDED

    override fun isAvailable(): Boolean = AIEngineProvider.isAvailable()

    override suspend fun generateText(prompt: String): String? {
        return AIEngineProvider.generateText(prompt)
    }

    override fun getModelInfo(): ModelInfo = ModelInfo(
        modelId = "qwen3-1.7b-int4",
        displayName = AIEngineProvider.modelName,
        providerType = ModelProviderType.FREEDOM_PROVIDED,
        artifactPath = null, // Managed by LiteRT-LM asset system
        contextLength = 2048,
        outputContract = "FreedomQuery JSON",
        isActive = AIEngineProvider.isAvailable(),
        backend = AIEngineProvider.backendType,
        initDurationMs = AIEngineProvider.initDurationMs,
        lastInferenceTimeMs = AIEngineProvider.lastInferenceTimeMs,
        lastError = AIEngineProvider.lastError
    )

    companion object {
        const val PROVIDER_ID = "freedom-qwen3-1.7b"
    }
}
