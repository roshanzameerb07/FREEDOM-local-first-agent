package com.example.freedom.framework.organization

/**
 * Bounded Organization Profile abstraction.
 *
 * Describes an organization's identity, supported domain, entities, fields,
 * and query capabilities. This is a framework contract — concrete profiles
 * are registered at startup (e.g., the milk_collection reference profile).
 *
 * Design Principles:
 * - organizationId is an identifier, NOT a security credential
 * - The profile defines what the organization CAN do, not what it IS allowed to do
 *   (authorization is handled separately by [com.example.freedom.framework.security.AuthorizationPolicy])
 * - Profiles are local-first; no cloud dependency required
 */
data class OrganizationProfile(
    /** Unique organization identifier */
    val organizationId: String,

    /** Human-readable organization name */
    val organizationName: String,

    /** Domain/vertical (e.g., "milk_collection", "warehouse", "field_survey") */
    val domain: String,

    /** Organization type classification */
    val organizationType: OrganizationType = OrganizationType.COOPERATIVE,

    /** Entities this organization operates on */
    val enabledEntities: Set<String> = emptySet(),

    /** Fields this organization uses within enabled entities */
    val enabledFields: Set<String> = emptySet(),

    /** Query capabilities enabled for this organization */
    val enabledQueryCapabilities: Set<QueryCapability> = QueryCapability.DEFAULT_CAPABILITIES,

    /** Active model provider type */
    val activeModelProvider: ModelProviderType = ModelProviderType.FREEDOM_PROVIDED,

    /** Organization-specific policy configuration */
    val policyConfig: Map<String, String> = emptyMap(),

    /** Registration/license number */
    val registrationNumber: String? = null,

    /** Regional information */
    val region: String? = null,

    /** Whether this profile is active */
    val isActive: Boolean = true
)

/**
 * Organization type classification.
 */
enum class OrganizationType {
    COOPERATIVE,
    ENTERPRISE,
    GOVERNMENT,
    NON_PROFIT,
    DEMO
}

/**
 * Query capabilities that can be selectively enabled per organization.
 */
enum class QueryCapability {
    READ_RECORDS,
    WRITE_RECORDS,
    AGGREGATE_QUERIES,
    GROUPED_QUERIES,
    TEMPORAL_QUERIES,
    PAYMENT_QUERIES,
    KNOWLEDGE_BASE,
    WORKER_PROFILE,
    ORGANIZATION_INFO;

    companion object {
        val DEFAULT_CAPABILITIES: Set<QueryCapability> = entries.toSet()
    }
}

/**
 * Model provider type classification.
 */
enum class ModelProviderType {
    /** Model provided and managed by FREEDOM */
    FREEDOM_PROVIDED,
    /** Model provided by the organization itself */
    ORGANIZATION_PROVIDED,
    /** No model available — deterministic fallback only */
    NONE
}
