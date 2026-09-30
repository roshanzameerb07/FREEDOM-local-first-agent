package com.example.freedom.data.repository

import com.example.freedom.data.local.dao.FarmerDao
import com.example.freedom.data.local.entity.FarmerEntity
import com.example.freedom.domain.query.NameNormalizer

class FarmerRepositoryImpl(
    private val dao: FarmerDao
) : FarmerRepository {

    override suspend fun getActiveFarmers(): List<FarmerEntity> {
        return dao.getAllActiveFarmers()
    }

    override suspend fun getAllFarmers(): List<FarmerEntity> {
        return dao.getAllFarmers()
    }

    override suspend fun getFarmerById(farmerId: String): FarmerEntity? {
        return dao.getFarmerById(farmerId)
    }

    override suspend fun getFarmerByNormalizedName(normalizedName: String): FarmerEntity? {
        return dao.getFarmerByNormalizedName(normalizedName)
    }

    override suspend fun searchFarmersByToken(token: String): List<FarmerEntity> {
        val normalizedToken = NameNormalizer.normalize(token)
        return if (normalizedToken.isNotBlank()) {
            dao.getFarmersByNameToken(normalizedToken)
        } else {
            emptyList()
        }
    }

    override suspend fun getActiveFarmerCount(): Int {
        return dao.getActiveFarmerCount()
    }

    override suspend fun registerFarmer(farmerName: String): FarmerEntity {
        val normalizedName = NameNormalizer.normalize(farmerName)
        require(normalizedName.isNotBlank()) { "Farmer name cannot be blank after normalization" }

        // Check if farmer already exists
        val existing = dao.getFarmerByNormalizedName(normalizedName)
        if (existing != null) {
            return existing
        }

        // Create new farmer
        val farmer = FarmerEntity(
            farmerName = farmerName.trim(),
            normalizedName = normalizedName
        )
        dao.insertFarmer(farmer)
        return farmer
    }

    override suspend fun deactivateFarmer(farmerId: String) {
        dao.setFarmerActive(farmerId, false)
    }

    override suspend fun updateFarmerName(farmerId: String, newName: String) {
        val normalizedName = NameNormalizer.normalize(newName)
        require(normalizedName.isNotBlank()) { "New name cannot be blank after normalization" }
        dao.updateFarmerName(farmerId, newName.trim(), normalizedName)
    }
}
