package com.example.freedom.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import java.util.UUID

/**
 * Authoritative local farmer entity.
 *
 * This is the single source of truth for farmer identity in FREEDOM.
 * All milk collection records (MilkRecordEntity) reference a farmer via farmerId.
 *
 * Design principles:
 * 1. Stable farmerId (UUID) — never changes even if display name is corrected
 * 2. normalizedName — deterministic lowercase/trimmed/collapsed version for matching
 * 3. No implicit merging — similar names are separate entities until explicitly merged
 * 4. isActive — soft-delete support; inactive farmers are excluded from resolution
 */
@Entity(
    tableName = "farmers",
    indices = [
        Index(value = ["normalizedName"], unique = true)
    ]
)
data class FarmerEntity(
    @PrimaryKey
    val farmerId: String = UUID.randomUUID().toString(),

    /** Display name exactly as entered (preserves original casing and spacing) */
    val farmerName: String,

    /**
     * Deterministically normalized version of farmerName.
     * Produced by NameNormalizer.normalize(farmerName).
     * Used for matching and deduplication.
     * Unique index ensures no two farmers share the same normalized name.
     */
    val normalizedName: String,

    /** Epoch milliseconds when this farmer was first registered locally */
    val createdAt: Long = System.currentTimeMillis(),

    /** Whether this farmer is active for entity resolution */
    val isActive: Boolean = true
)
