package com.example.freedom.framework.security

import com.example.freedom.domain.query.FreedomQuery
import com.example.freedom.domain.query.QueryTarget
import com.example.freedom.domain.query.RequestType
import com.example.freedom.framework.session.SessionContext

/**
 * Framework-level authorization boundary.
 *
 * Sits between query validation and execution:
 *
 * FreedomQuery → Validation → **AuthorizationPolicy** → FreedomQueryExecutor → Room
 *
 * A query that is syntactically valid can still be rejected if the current
 * user lacks the required permissions.
 *
 * Design Principles:
 * - The model NEVER determines organizationId, userId, role, or permissions
 * - These values come from authenticated local session context
 * - Authorization is centralized here, not scattered in UI code
 */
object AuthorizationPolicy {

    /**
     * Result of an authorization check.
     */
    data class AuthorizationResult(
        val isAuthorized: Boolean,
        val deniedReason: String? = null
    ) {
        companion object {
            fun authorized() = AuthorizationResult(isAuthorized = true)
            fun denied(reason: String) = AuthorizationResult(isAuthorized = false, deniedReason = reason)
        }
    }

    /**
     * Check whether the current session is authorized to execute a query.
     *
     * @param query The validated FreedomQuery to authorize.
     * @param session The current authenticated session context.
     * @return AuthorizationResult indicating whether execution should proceed.
     */
    fun authorize(query: FreedomQuery, session: SessionContext): AuthorizationResult {
        val user = session.currentUser
            ?: return AuthorizationResult.denied("No authenticated user in session.")

        if (!user.isActive) {
            return AuthorizationResult.denied("User account '${user.userId}' is not active.")
        }

        // Check organization match
        if (user.organizationId != session.organizationId) {
            return AuthorizationResult.denied("User organization mismatch.")
        }

        // Map query to required permission
        val requiredPermission = mapQueryToPermission(query)
            ?: return AuthorizationResult.authorized() // No specific permission required

        return if (user.hasPermission(requiredPermission)) {
            AuthorizationResult.authorized()
        } else {
            AuthorizationResult.denied(
                "User '${user.displayName}' (${user.role.displayName}) lacks permission: ${requiredPermission.name}"
            )
        }
    }

    /**
     * Map a FreedomQuery to the permission required to execute it.
     * Returns null if no specific permission is needed.
     */
    private fun mapQueryToPermission(query: FreedomQuery): Permission? {
        return when (query.requestType) {
            RequestType.WRITE -> Permission.WRITE_RECORDS
            RequestType.CLARIFY -> null // Clarification requests don't need authorization
            RequestType.UNSUPPORTED -> null
            RequestType.QUERY -> when (query.target) {
                QueryTarget.MILK_RECORDS -> {
                    if (query.aggregations.isNotEmpty() || query.groupBy.isNotEmpty()) {
                        Permission.VIEW_AGGREGATES
                    } else {
                        Permission.READ_RECORDS
                    }
                }
                QueryTarget.WORKER_PROFILE -> Permission.VIEW_OWN_PROFILE
                QueryTarget.ORGANIZATION_INFO -> Permission.VIEW_ORGANIZATION_INFO
                QueryTarget.KNOWLEDGE_BASE -> Permission.QUERY_KNOWLEDGE_BASE
            }
        }
    }
}
