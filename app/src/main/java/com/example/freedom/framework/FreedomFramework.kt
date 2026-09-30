package com.example.freedom.framework

import com.example.freedom.framework.model.ModelProviderRegistry
import com.example.freedom.framework.model.QwenModelProvider
import com.example.freedom.framework.organization.MilkCollectionProfile
import com.example.freedom.framework.organization.OrganizationRegistry
import com.example.freedom.framework.session.SessionManager
import com.example.freedom.framework.security.UserRole

/**
 * FREEDOM Framework Bootstrapper.
 *
 * Initializes the framework with the default milk collection reference profile
 * and registers the FREEDOM-provided Qwen3 model.
 *
 * Called once during application startup. Does NOT modify any existing
 * initialization logic — it runs alongside the current MyApplication flow.
 *
 * Design Principles:
 * - Framework initialization is separate from model initialization
 * - Default profile is registered but activation still requires login
 * - Model provider registration is separate from model loading
 */
object FreedomFramework {

    @Volatile
    private var isInitialized = false

    /**
     * Initialize the FREEDOM framework with default configuration.
     * Safe to call multiple times — only runs once.
     */
    @Synchronized
    fun initialize() {
        if (isInitialized) return

        // 1. Register organization profiles
        val defaultProfile = MilkCollectionProfile.createDefault()
        OrganizationRegistry.register(defaultProfile)
        OrganizationRegistry.setActiveProfile(defaultProfile.organizationId)

        val demoProfile = MilkCollectionProfile.createHackathonDemoProfile()
        OrganizationRegistry.register(demoProfile)

        // 2. Register the FREEDOM-provided model provider
        val qwenProvider = QwenModelProvider()
        ModelProviderRegistry.register(qwenProvider)
        ModelProviderRegistry.activate(qwenProvider.providerId)

        isInitialized = true
    }

    /**
     * Called after successful authentication to establish the session.
     */
    fun onUserAuthenticated(
        organizationId: String,
        userId: String,
        displayName: String,
        role: UserRole = UserRole.FIELD_WORKER
    ) {
        if (OrganizationRegistry.getProfile(organizationId) != null) {
            OrganizationRegistry.setActiveProfile(organizationId)
        }
        SessionManager.createSession(
            organizationId = organizationId,
            userId = userId,
            displayName = displayName,
            role = role
        )
    }

    /**
     * Called on logout to clear the session.
     */
    fun onUserLogout() {
        SessionManager.endSession()
    }

    /**
     * Check whether the framework is initialized.
     */
    fun isReady(): Boolean = isInitialized

    /**
     * Get a summary of the current framework state.
     */
    fun getStatusSummary(): String {
        val orgName = try {
            OrganizationRegistry.getActiveProfile().organizationName
        } catch (_: Exception) { "Not set" }

        val modelInfo = ModelProviderRegistry.getActiveModelInfo()
        val modelStatus = if (modelInfo?.isActive == true) {
            "${modelInfo.displayName} (${modelInfo.backend})"
        } else {
            "Not loaded"
        }

        val sessionStatus = if (SessionManager.hasActiveSession()) {
            val session = SessionManager.getCurrentSession()
            "${session.currentUser?.displayName ?: "Unknown"} (${session.currentUser?.role?.displayName ?: "Unknown"})"
        } else {
            "No active session"
        }

        return buildString {
            appendLine("FREEDOM Framework Status")
            appendLine("═══════════════════════")
            appendLine("Organization: $orgName")
            appendLine("Model: $modelStatus")
            appendLine("Session: $sessionStatus")
            appendLine("Initialized: $isInitialized")
        }
    }

    /**
     * Reset the framework. Used for testing only.
     */
    fun resetForTesting() {
        OrganizationRegistry.clearAll()
        ModelProviderRegistry.clearAll()
        SessionManager.clearForTesting()
        isInitialized = false
    }
}
