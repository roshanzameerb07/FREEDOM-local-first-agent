package com.example.freedom.framework.organization

/**
 * Reference organization profile for the milk collection domain.
 *
 * This is the first concrete implementation of [OrganizationProfile]
 * and serves as the reference for the FREEDOM framework.
 *
 * The current farmer/milk-collection application uses this profile.
 */
object MilkCollectionProfile {

    /** Default demo organization ID matching existing seed data */
    const val DEFAULT_ORG_ID = "ORG001"

    /**
     * Creates the default milk collection organization profile.
     * Matches the existing Mandya Cooperative demo configuration.
     */
    fun createDefault(): OrganizationProfile = OrganizationProfile(
        organizationId = DEFAULT_ORG_ID,
        organizationName = "Mandya District Cooperative Milk Producers Union (KMF / Nandini)",
        domain = "milk_collection",
        organizationType = OrganizationType.COOPERATIVE,
        enabledEntities = setOf(
            "FARMER",
            "MILK_RECORD"
        ),
        enabledFields = setOf(
            "QUANTITY",
            "FAT",
            "SNF",
            "PAYMENT_STATUS",
            "PAYMENT_METHOD",
            "PAYMENT_REFERENCE",
            "PAYABLE_AMOUNT",
            "AMOUNT_PAID"
        ),
        enabledQueryCapabilities = QueryCapability.DEFAULT_CAPABILITIES,
        activeModelProvider = ModelProviderType.FREEDOM_PROVIDED,
        registrationNumber = "COOP-KAR-MND-4412",
        region = "Mandya, Karnataka"
    )

    /** Dedicated Hackathon Demo Organization ID */
    const val HACKATHON_DEMO_ORG_ID = "FREEDOM-DEMO-001"

    /**
     * Creates the dedicated hackathon demo organization profile.
     */
    fun createHackathonDemoProfile(): OrganizationProfile = OrganizationProfile(
        organizationId = HACKATHON_DEMO_ORG_ID,
        organizationName = "FREEDOM Demo Organization",
        domain = "milk_collection",
        organizationType = OrganizationType.DEMO,
        enabledEntities = setOf(
            "FARMER",
            "MILK_RECORD"
        ),
        enabledFields = setOf(
            "QUANTITY",
            "FAT",
            "SNF",
            "PAYMENT_STATUS",
            "PAYMENT_METHOD",
            "PAYMENT_REFERENCE",
            "PAYABLE_AMOUNT",
            "AMOUNT_PAID"
        ),
        enabledQueryCapabilities = QueryCapability.DEFAULT_CAPABILITIES,
        activeModelProvider = ModelProviderType.FREEDOM_PROVIDED,
        registrationNumber = "DEMO-HACKATHON-2026",
        region = "Mandya Demonstration Route"
    )

    /**
     * Creates a default demo activation state.
     */
    fun createDefaultActivation(deviceId: String = "DEVICE-LOCAL"): ActivationState = ActivationState(
        organizationId = DEFAULT_ORG_ID,
        deviceId = deviceId,
        status = ActivationStatus.ACTIVE,
        activationTier = "demo",
        maxUsers = 10
    )
}
