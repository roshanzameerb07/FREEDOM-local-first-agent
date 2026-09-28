package com.example.freedom.data.repository

import com.example.freedom.data.local.entity.MilkRecordEntity
import kotlinx.coroutines.flow.Flow

interface MilkRecordRepository {
    fun getAllRecords(): Flow<List<MilkRecordEntity>>
    fun searchAndFilterRecords(
        query: String = "",
        paymentStatus: String? = null,
        uploadStatus: String? = null
    ): Flow<List<MilkRecordEntity>>
    fun getTodayRecordsCount(): Flow<Int>
    fun getTodayTotalQuantity(): Flow<Double>
    fun getPendingUploadCount(): Flow<Int>
    fun getAllRecordsCount(): Flow<Int>
    suspend fun insertRecord(record: MilkRecordEntity)
    suspend fun getPendingRecords(): List<MilkRecordEntity>
    suspend fun getRecordsByFarmerName(farmerName: String): List<MilkRecordEntity>
    suspend fun getPendingPaymentRecords(): List<MilkRecordEntity>
    suspend fun getFarmerQuantityThisWeek(farmerName: String): Double
    suspend fun getDistinctFarmersCount(period: String = "today"): Int
    suspend fun getWeeklyTotalQuantity(): Double
    suspend fun getWeeklyRecordsCount(): Int
    suspend fun simulateBatchSync(): Int
}

