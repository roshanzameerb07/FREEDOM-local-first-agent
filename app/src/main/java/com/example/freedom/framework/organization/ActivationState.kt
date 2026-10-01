package com.example.freedom.framework.organization

/**
 * Local activation/license abstraction.
 *
 * Represents the activation state of an organization on this device.
 * This is a framework contract — the actual activation mechanism
 * (offline key, QR code, server validation) is pluggable.
 *
 * Design Principles:
 * - Local-first: activation can be validated without network
 * - No payment processing or commercial licensing built-in
 * - Easily replaceable by a future production activation service
 */
data class ActivationState(
    /** Organization this activation belongs to */
    val organizationId: String,

    /** Device identifier (unique per installation) */
    val deviceId: String,

    /** Current activation status */
    val status: ActivationStatus = ActivationStatus.ACTIVE,

    /** Human-readable activation tier/plan name */
    val activationTier: String = "demo",

    /** When this activation was created (epoch ms) */
    val activatedAt: Long = System.currentTimeMillis(),

    /** When this activation expires (epoch ms), null = no expiry */
    val expiresAt: Long? = null,

    /** Maximum number of users allowed under this activation */
    val maxUsers: Int = 10,

    /** Additional entitlement metadata */
    val entitlements: Map<String, String> = emptyMap()
) {
    /**
     * Check whether this activation is currently valid.
     */
    fun isValid(): Boolean {
        if (status != ActivationStatus.ACTIVE) return false
        val now = System.currentTimeMillis()
        return expiresAt == null || now < expiresAt
    }
}

/**
 * Activation status enumeration.
 */
enum class ActivationStatus {
    /** Organization is activated and operational */
    ACTIVE,
    /** Activation has expired */
    EXPIRED,
    /** Activation was revoked */
    REVOKED,
    /** Activation is pending (awaiting validation) */
    PENDING,
    /** Trial/demo activation */
    TRIAL
}
