package com.example.freedom.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.freedom.data.local.entity.MilkRecordEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface MilkRecordDao {

    @Query("SELECT * FROM milk_records ORDER BY createdAt DESC")
    fun getAllRecordsFlow(): Flow<List<MilkRecordEntity>>

    @Query("""
        SELECT * FROM milk_records
        WHERE (:query = '' OR LOWER(farmerName) LIKE '%' || LOWER(:query) || '%')
          AND (:paymentStatus IS NULL OR paymentStatus = :paymentStatus)
          AND (:uploadStatus IS NULL OR uploadStatus = :uploadStatus)
        ORDER BY createdAt DESC
    """)
    fun searchAndFilterRecords(
        query: String,
        paymentStatus: String?,
        uploadStatus: String?
    ): Flow<List<MilkRecordEntity>>

    @Query("SELECT COUNT(*) FROM milk_records WHERE createdAt >= :startOfDay AND createdAt <= :endOfDay")
    fun getTodayRecordsCount(startOfDay: Long, endOfDay: Long): Flow<Int>

    @Query("SELECT COALESCE(SUM(quantity), 0.0) FROM milk_records WHERE createdAt >= :startOfDay AND createdAt <= :endOfDay")
    fun getTodayTotalQuantity(startOfDay: Long, endOfDay: Long): Flow<Double>

    @Query("SELECT COUNT(*) FROM milk_records WHERE uploadStatus = 'PENDING'")
    fun getPendingUploadCount(): Flow<Int>

    @Query("SELECT COUNT(*) FROM milk_records")
    fun getAllRecordsCount(): Flow<Int>

    @Query("SELECT COUNT(*) FROM milk_records")
    suspend fun countAllRecords(): Int

    @Query("SELECT * FROM milk_records WHERE uploadStatus = 'PENDING' ORDER BY createdAt ASC")
    suspend fun getPendingRecords(): List<MilkRecordEntity>

    @Query("SELECT * FROM milk_records WHERE LOWER(farmerName) LIKE '%' || LOWER(:farmerName) || '%'")
    suspend fun getRecordsByFarmerName(farmerName: String): List<MilkRecordEntity>

    @Query("SELECT * FROM milk_records WHERE paymentStatus = 'PENDING' ORDER BY createdAt DESC")
    suspend fun getPendingPaymentRecords(): List<MilkRecordEntity>

    @Query("SELECT COALESCE(SUM(quantity), 0.0) FROM milk_records WHERE LOWER(farmerName) LIKE '%' || LOWER(:farmerName) || '%' AND createdAt >= :sinceTimestamp")
    suspend fun getFarmerQuantitySince(farmerName: String, sinceTimestamp: Long): Double

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRecord(record: MilkRecordEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRecords(records: List<MilkRecordEntity>)

    @Query("UPDATE milk_records SET uploadStatus = 'UPLOADED' WHERE id IN (:recordIds)")
    suspend fun markBatchAsUploaded(recordIds: List<String>)

    @Query("UPDATE milk_records SET uploadStatus = 'UPLOADED' WHERE uploadStatus = 'PENDING'")
    suspend fun markAllPendingAsUploaded(): Int
}
