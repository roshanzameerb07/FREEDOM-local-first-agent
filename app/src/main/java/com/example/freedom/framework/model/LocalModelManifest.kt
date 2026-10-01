package com.example.freedom.framework.model

/**
 * Extended manifest for an imported local model.
 *
 * Tracks all metadata required for:
 * - Safe file management (path, sha256, size)
 * - Organization scoping (org cannot use another org's model)
 * - Lifecycle state (REGISTERED → COMPATIBLE_VERIFIED → ACTIVATED)
 * - Runtime identity (runtimeType must be LITERT_LM for this version)
 *
 * Design Principles:
 * - organizationId comes from authenticated session, NEVER from model output
 * - sha256 is computed locally from file bytes, NEVER trusted from metadata
 * - compatibilityStatus is determined by a controlled test, not user assertion
 */
data class LocalModelManifest(
    /** Unique model identifier (generated at import time) */
    val modelId: String,

    /** Human-readable display name */
    val displayName: String,

    /** Source classification */
    val source: ModelSource,

    /** Runtime type — only LITERT_LM supported in this version */
    val runtimeType: ModelRuntimeType,

    /** Artifact format type */
    val artifactType: ModelArtifactType,

    /** Absolute path to the model file in FREEDOM private storage */
    val localPath: String,

    /** Original file name from the picker */
    val fileName: String,

    /** File size in bytes */
    val fileSize: Long,

    /** SHA-256 hex digest of the model file, computed at import time */
    val sha256: String,

    /** Organization ID from authenticated session at import time */
    val organizationId: String,

    /** Maximum context length in tokens */
    val contextLength: Int = 2048,

    /** Expected semantic contract description */
    val semanticContract: String = "FreedomQuery JSON",

    /** When this model was imported (epoch ms) */
    val importedAt: Long = System.currentTimeMillis(),

    /** Version tag (user-provided or auto-assigned) */
    val version: String = "1.0",

    /** Current lifecycle status */
    val status: ModelLifecycleStatus = ModelLifecycleStatus.REGISTERED,

    /** Result of the compatibility check (null = not yet checked) */
    val compatibilityStatus: ModelCompatibilityStatus? = null,

    /** Reason if compatibility check failed */
    val compatibilityReason: String? = null
) {
    /** Whether this model is currently the active provider */
    val isActive: Boolean get() = status == ModelLifecycleStatus.ACTIVATED
}

/**
 * Who supplied this model.
 */
enum class ModelSource {
    /** Bundled by the FREEDOM framework (e.g., Qwen3 in APK) */
    FREEDOM_PROVIDED,
    /** Imported by an organization admin from device storage */
    USER_IMPORTED,
    /** Pre-configured by the organization (future use) */
    ORGANIZATION_PROVIDED
}

/**
 * On-device runtime engine type.
 * Only LITERT_LM is supported in the current version.
 */
enum class ModelRuntimeType {
    LITERT_LM,
    /** Reserved for future runtimes */
    UNKNOWN
}

/**
 * Binary artifact format type.
 */
enum class ModelArtifactType {
    LITERTLM,
    UNKNOWN
}

/**
 * Model lifecycle state machine.
 *
 * REGISTERED → (compatibility check) → COMPATIBLE_VERIFIED / INCOMPATIBLE
 * COMPATIBLE_VERIFIED → ACTIVATED
 * ACTIVATED → DEACTIVATED (on replacement or explicit deactivation)
 * Any state → REMOVED
 */
enum class ModelLifecycleStatus {
    /** Imported, not yet compatibility-checked */
    REGISTERED,
    /** Passed the controlled compatibility test */
    COMPATIBLE_VERIFIED,
    /** Failed the compatibility test — cannot be activated */
    INCOMPATIBLE,
    /** Currently the active inference provider */
    ACTIVATED,
    /** Was active, now replaced by another model */
    DEACTIVATED,
    /** Removed from device storage */
    REMOVED
}

/**
 * Result of the compatibility check.
 */
enum class ModelCompatibilityStatus {
    /** Model loaded and passed FREEDOM test prompt */
    COMPATIBLE,
    /** Model loaded but output did not parse to a valid FreedomQuery */
    INCOMPATIBLE,
    /** Model file could not be loaded at all */
    LOAD_FAILED
}
