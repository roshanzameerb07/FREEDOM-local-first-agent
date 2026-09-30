package com.example.freedom.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.freedom.data.local.entity.FarmerEntity

@Dao
interface FarmerDao {

    @Query("SELECT * FROM farmers WHERE isActive = 1 ORDER BY farmerName ASC")
    suspend fun getAllActiveFarmers(): List<FarmerEntity>

    @Query("SELECT * FROM farmers ORDER BY farmerName ASC")
    suspend fun getAllFarmers(): List<FarmerEntity>

    @Query("SELECT * FROM farmers WHERE farmerId = :farmerId LIMIT 1")
    suspend fun getFarmerById(farmerId: String): FarmerEntity?

    @Query("SELECT * FROM farmers WHERE normalizedName = :normalizedName AND isActive = 1 LIMIT 1")
    suspend fun getFarmerByNormalizedName(normalizedName: String): FarmerEntity?

    @Query("SELECT * FROM farmers WHERE normalizedName LIKE '%' || :token || '%' AND isActive = 1")
    suspend fun getFarmersByNameToken(token: String): List<FarmerEntity>

    @Query("SELECT COUNT(*) FROM farmers WHERE isActive = 1")
    suspend fun getActiveFarmerCount(): Int

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertFarmer(farmer: FarmerEntity): Long

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertFarmers(farmers: List<FarmerEntity>)

    @Query("UPDATE farmers SET isActive = :isActive WHERE farmerId = :farmerId")
    suspend fun setFarmerActive(farmerId: String, isActive: Boolean)

    @Query("UPDATE farmers SET farmerName = :farmerName, normalizedName = :normalizedName WHERE farmerId = :farmerId")
    suspend fun updateFarmerName(farmerId: String, farmerName: String, normalizedName: String)
}
