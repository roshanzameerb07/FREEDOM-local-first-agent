package com.example.freedom.framework.session

import com.example.freedom.framework.organization.OrganizationRegistry
import com.example.freedom.framework.security.OrganizationUser
import com.example.freedom.framework.security.UserRole

/**
 * Manages the current session state.
 *
 * Provides the [SessionContext] used by the authorization boundary
 * and query executor to scope data access.
 *
 * Design Principles:
 * - Session is local-first; no cloud session management
 * - Session values are framework-controlled, not model-controlled
 * - Thread-safe via @Volatile
 */
object SessionManager {

    @Volatile
    private var currentSession: SessionContext? = null

    /**
     * Create and activate a session for an authenticated user.
     *
     * @param organizationId The organization the user logged into.
     * @param userId The authenticated user's ID.
     * @param displayName Human-readable user name.
     * @param role The user's role within the organization.
     * @param deviceId The device identifier.
     */
    fun createSession(
        organizationId: String,
        userId: String,
        displayName: String,
        role: UserRole = UserRole.FIELD_WORKER,
        deviceId: String = "DEVICE-LOCAL"
    ): SessionContext {
        val user = OrganizationUser(
            userId = userId,
            organizationId = organizationId,
            displayName = displayName,
            role = role
        )
        val session = SessionContext(
            organizationId = organizationId,
            currentUser = user,
            deviceId = deviceId
        )
        currentSession = session
        return session
    }

    /**
     * Get the current active session.
     * @throws IllegalStateException if no session is active.
     */
    fun getCurrentSession(): SessionContext {
        return currentSession
            ?: throw IllegalStateException("No active session. User must authenticate first.")
    }

    /**
     * Get the current session or null.
     */
    fun getCurrentSessionOrNull(): SessionContext? = currentSession

    /**
     * Check whether a session is active.
     */
    fun hasActiveSession(): Boolean = currentSession != null

    /**
     * End the current session (logout).
     */
    fun endSession() {
        currentSession = null
    }

    /**
     * Clear session state. Used for testing.
     */
    fun clearForTesting() {
        currentSession = null
    }
}
