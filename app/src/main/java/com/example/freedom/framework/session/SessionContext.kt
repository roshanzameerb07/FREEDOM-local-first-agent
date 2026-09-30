package com.example.freedom.framework.session

import com.example.freedom.framework.security.OrganizationUser

/**
 * Immutable session context for the current authenticated user.
 *
 * All internal values (organizationId, userId, deviceId) are derived
 * from local authenticated context. These values MUST NOT come from
 * model output.
 *
 * Design Principles:
 * - Session context is the single source of truth for identity during query execution
 * - The model cannot set or override session values
 * - Future multi-organization support uses session switching, not string conventions
 */
data class SessionContext(
    /** Organization ID from authenticated login */
    val organizationId: String,

    /** Authenticated user within the organization */
    val currentUser: OrganizationUser?,

    /** Device identifier for this installation */
    val deviceId: String = "DEVICE-LOCAL",

    /** Whether this session is authenticated */
    val isAuthenticated: Boolean = currentUser != null
)
