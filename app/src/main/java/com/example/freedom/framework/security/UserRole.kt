package com.example.freedom.framework.security

/**
 * User role within an organization.
 *
 * Roles define a set of permissions. The authorization boundary
 * uses roles to determine what queries a user can execute.
 *
 * Design Principles:
 * - Roles are framework-controlled, never model-determined
 * - Permissions are centralized, not scattered in UI code
 * - Extensible via [Permission] set
 */
enum class UserRole(
    val displayName: String,
    val defaultPermissions: Set<Permission>
) {
    FIELD_WORKER(
        displayName = "Field Worker",
        defaultPermissions = setOf(
            Permission.READ_RECORDS,
            Permission.WRITE_RECORDS,
            Permission.VIEW_AGGREGATES,
            Permission.VIEW_OWN_PROFILE,
            Permission.VIEW_ORGANIZATION_INFO,
            Permission.QUERY_KNOWLEDGE_BASE
        )
    ),
    MANAGER(
        displayName = "Manager",
        defaultPermissions = setOf(
            Permission.READ_RECORDS,
            Permission.WRITE_RECORDS,
            Permission.VIEW_OWN_PROFILE,
            Permission.VIEW_ORGANIZATION_INFO,
            Permission.QUERY_KNOWLEDGE_BASE,
            Permission.VIEW_ALL_WORKERS,
            Permission.VIEW_AGGREGATES,
            Permission.MANAGE_PAYMENTS
        )
    ),
    ORG_ADMIN(
        displayName = "Organization Admin",
        defaultPermissions = Permission.entries.toSet()
    )
}

/**
 * Individual permissions that can be granted to users.
 * Permissions are atomic capabilities checked by [AuthorizationPolicy].
 */
enum class Permission {
    READ_RECORDS,
    WRITE_RECORDS,
    VIEW_OWN_PROFILE,
    VIEW_ALL_WORKERS,
    VIEW_ORGANIZATION_INFO,
    VIEW_AGGREGATES,
    QUERY_KNOWLEDGE_BASE,
    MANAGE_PAYMENTS,
    MANAGE_USERS,
    MANAGE_ORGANIZATION,
    MANAGE_MODEL_PROVIDER,
    EXPORT_DATA
}
