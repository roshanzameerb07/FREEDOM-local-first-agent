package com.example.freedom.framework.security

/**
 * Represents an authenticated user within an organization.
 *
 * This is the framework-level user identity, distinct from:
 * - organizationId (which organization they belong to)
 * - deviceId (which device they are using)
 * - model output (which must NEVER determine user identity)
 *
 * Design Principles:
 * - userId is an identifier, not a security credential
 * - Role determines permissions, not scattered conditionals
 * - The model cannot choose or modify user identity
 */
data class OrganizationUser(
    /** Unique user identifier within the organization */
    val userId: String,

    /** Organization this user belongs to */
    val organizationId: String,

    /** Human-readable display name */
    val displayName: String,

    /** User's role within the organization */
    val role: UserRole = UserRole.FIELD_WORKER,

    /** Additional permissions beyond role defaults (additive) */
    val additionalPermissions: Set<Permission> = emptySet(),

    /** Revoked permissions (subtractive from role defaults) */
    val revokedPermissions: Set<Permission> = emptySet(),

    /** Whether this user account is active */
    val isActive: Boolean = true
) {
    /**
     * Compute the effective permission set for this user.
     * = (role defaults + additional) - revoked
     */
    fun effectivePermissions(): Set<Permission> {
        return (role.defaultPermissions + additionalPermissions) - revokedPermissions
    }

    /**
     * Check if this user has a specific permission.
     */
    fun hasPermission(permission: Permission): Boolean {
        return permission in effectivePermissions()
    }
}
