package com.example.freedom.framework.organization

/**
 * Registry for organization profiles.
 *
 * In the current implementation, holds a single active profile.
 * Future multi-organization support can extend this without
 * changing the interface contract.
 *
 * Thread-safe via @Volatile and synchronized block.
 */
object OrganizationRegistry {

    @Volatile
    private var activeProfile: OrganizationProfile? = null

    private val registeredProfiles = mutableMapOf<String, OrganizationProfile>()

    /**
     * Register an organization profile.
     * Does NOT activate it — call [setActiveProfile] separately.
     */
    @Synchronized
    fun register(profile: OrganizationProfile) {
        registeredProfiles[profile.organizationId] = profile
    }

    /**
     * Set the active organization profile by ID.
     * @throws IllegalStateException if the profile is not registered.
     */
    @Synchronized
    fun setActiveProfile(organizationId: String) {
        val profile = registeredProfiles[organizationId]
            ?: throw IllegalStateException("Organization '$organizationId' is not registered.")
        activeProfile = profile
    }

    /**
     * Get the currently active organization profile.
     * @throws IllegalStateException if no profile is active.
     */
    fun getActiveProfile(): OrganizationProfile {
        return activeProfile
            ?: throw IllegalStateException("No active organization profile. Call setActiveProfile() first.")
    }

    /**
     * Get the active profile or null if none is set.
     */
    fun getActiveProfileOrNull(): OrganizationProfile? = activeProfile

    /**
     * Check whether an organization profile is active.
     */
    fun hasActiveProfile(): Boolean = activeProfile != null

    /**
     * Get a registered profile by ID.
     */
    fun getProfile(organizationId: String): OrganizationProfile? {
        return registeredProfiles[organizationId]
    }

    /**
     * List all registered organization IDs.
     */
    fun listRegisteredIds(): Set<String> = registeredProfiles.keys.toSet()

    /**
     * Clear all registrations. Used for testing.
     */
    @Synchronized
    fun clearAll() {
        registeredProfiles.clear()
        activeProfile = null
    }
}
