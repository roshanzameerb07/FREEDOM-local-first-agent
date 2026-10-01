package com.example.freedom.framework.model

import com.example.freedom.framework.organization.ModelProviderType

/**
 * Registry for model providers.
 *
 * Supports the conceptual lifecycle: register → activate → replace → disable.
 *
 * Design Principles:
 * - Model binaries are never hard-coded into source
 * - Multiple providers can be registered but only one is active at a time
 * - Provider switching does not require redesigning FREEDOM Core
 * - Thread-safe via synchronized blocks
 */
object ModelProviderRegistry {

    @Volatile
    private var activeProvider: ModelProvider? = null

    private val registeredProviders = mutableMapOf<String, ModelProvider>()

    /**
     * Register a model provider.
     */
    @Synchronized
    fun register(provider: ModelProvider) {
        registeredProviders[provider.providerId] = provider
    }

    /**
     * Activate a registered provider by ID.
     * @throws IllegalStateException if the provider is not registered.
     */
    @Synchronized
    fun activate(providerId: String) {
        val provider = registeredProviders[providerId]
            ?: throw IllegalStateException("Model provider '$providerId' is not registered.")
        activeProvider = provider
    }

    /**
     * Get the currently active model provider.
     */
    fun getActiveProvider(): ModelProvider? = activeProvider

    /**
     * Get a registered provider by ID.
     */
    fun getProvider(providerId: String): ModelProvider? = registeredProviders[providerId]

    /**
     * Disable the active provider (fallback to deterministic-only mode).
     */
    @Synchronized
    fun disableActiveProvider() {
        activeProvider = null
    }

    /**
     * List all registered provider IDs.
     */
    fun listRegisteredIds(): Set<String> = registeredProviders.keys.toSet()

    /**
     * Check if any provider is active and available.
     */
    fun isModelAvailable(): Boolean = activeProvider?.isAvailable() == true

    /**
     * Get model info for the active provider, or null.
     */
    fun getActiveModelInfo(): ModelInfo? = activeProvider?.getModelInfo()

    /**
     * Clear all registrations. Used for testing.
     */
    @Synchronized
    fun clearAll() {
        registeredProviders.clear()
        activeProvider = null
    }
}
