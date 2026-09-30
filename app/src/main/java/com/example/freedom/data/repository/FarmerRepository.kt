package com.example.freedom.data.repository

import com.example.freedom.data.local.entity.FarmerEntity

/**
 * Repository interface for farmer entity operations.
 */
interface FarmerRepository {
    /** Returns all active farmers (isActive = true), ordered by name */
    suspend fun getActiveFarmers(): List<FarmerEntity>

    /** Returns all farmers including inactive, ordered by name */
    suspend fun getAllFarmers(): List<FarmerEntity>

    /** Lookup by exact farmerId */
    suspend fun getFarmerById(farmerId: String): FarmerEntity?

    /** Lookup by exact normalized name */
    suspend fun getFarmerByNormalizedName(normalizedName: String): FarmerEntity?

    /** Search farmers whose normalized name contains the given token */
    suspend fun searchFarmersByToken(token: String): List<FarmerEntity>

    /** Count of active farmers */
    suspend fun getActiveFarmerCount(): Int

    /**
     * Registers a new farmer. Returns the FarmerEntity if inserted,
     * or the existing farmer if a farmer with the same normalized name already exists.
     */
    suspend fun registerFarmer(farmerName: String): FarmerEntity

    /** Deactivate a farmer (soft delete) */
    suspend fun deactivateFarmer(farmerId: String)

    /** Update a farmer's display name */
    suspend fun updateFarmerName(farmerId: String, newName: String)
}
