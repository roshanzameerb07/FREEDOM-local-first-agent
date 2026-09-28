package com.example.freedom.data.repository

import com.example.freedom.data.local.dao.MilkRecordDao
import com.example.freedom.data.local.entity.MilkRecordEntity
import kotlinx.coroutines.flow.Flow
import java.util.Calendar

class MilkRecordRepositoryImpl(
    private val dao: MilkRecordDao
) : MilkRecordRepository {

    override fun getAllRecords(): Flow<List<MilkRecordEntity>> = dao.getAllRecordsFlow()

    override fun searchAndFilterRecords(
        query: String,
        paymentStatus: String?,
        uploadStatus: String?
    ): Flow<List<MilkRecordEntity>> = dao.searchAndFilterRecords(query, paymentStatus, uploadStatus)

    override fun getTodayRecordsCount(): Flow<Int> {
        val (startOfDay, endOfDay) = getTodayTimestamps()
        return dao.getTodayRecordsCount(startOfDay, endOfDay)
    }

    override fun getTodayTotalQuantity(): Flow<Double> {
        val (startOfDay, endOfDay) = getTodayTimestamps()
        return dao.getTodayTotalQuantity(startOfDay, endOfDay)
    }

    override fun getPendingUploadCount(): Flow<Int> = dao.getPendingUploadCount()

    override fun getAllRecordsCount(): Flow<Int> = dao.getAllRecordsCount()

    override suspend fun insertRecord(record: MilkRecordEntity) {
        dao.insertRecord(record)
    }

    override suspend fun getRecordById(id: String): MilkRecordEntity? {
        return dao.getRecordById(id)
    }

    override suspend fun getPendingRecords(): List<MilkRecordEntity> = dao.getPendingRecords()

    override suspend fun getRecordsByFarmerName(farmerName: String): List<MilkRecordEntity> {
        return dao.getRecordsByFarmerName(farmerName)
    }

    override suspend fun getPendingPaymentRecords(): List<MilkRecordEntity> {
        return dao.getPendingPaymentRecords()
    }

    override suspend fun getFarmerQuantityThisWeek(farmerName: String): Double {
        val calendar = Calendar.getInstance()
        calendar.set(Calendar.DAY_OF_WEEK, calendar.firstDayOfWeek)
        calendar.set(Calendar.HOUR_OF_DAY, 0)
        calendar.set(Calendar.MINUTE, 0)
        calendar.set(Calendar.SECOND, 0)
        calendar.set(Calendar.MILLISECOND, 0)
        val startOfWeek = calendar.timeInMillis
        return dao.getFarmerQuantitySince(farmerName, startOfWeek)
    }

    override suspend fun getDistinctFarmersCount(period: String): Int {
        return when (period.lowercase()) {
            "today" -> {
                val (startOfDay, endOfDay) = getTodayTimestamps()
                dao.getTodayDistinctFarmersCount(startOfDay, endOfDay)
            }
            "week" -> {
                val startOfWeek = getStartOfWeekTimestamp()
                dao.getDistinctFarmersCountSince(startOfWeek)
            }
            else -> dao.getAllDistinctFarmersCount()
        }
    }

    override suspend fun getWeeklyTotalQuantity(): Double {
        val startOfWeek = getStartOfWeekTimestamp()
        return dao.getTotalQuantitySince(startOfWeek)
    }

    override suspend fun getWeeklyRecordsCount(): Int {
        val startOfWeek = getStartOfWeekTimestamp()
        return dao.getRecordsCountSince(startOfWeek)
    }

    override suspend fun updatePayment(
        id: String,
        paymentStatus: String,
        paymentMethod: String?,
        paymentReference: String?,
        amountPaid: Double?
    ) {
        val now = System.currentTimeMillis()
        dao.updatePayment(
            id = id,
            paymentStatus = paymentStatus,
            paymentMethod = paymentMethod,
            paymentReference = paymentReference,
            paymentTimestamp = now,
            amountPaid = amountPaid,
            updatedAt = now
        )
    }

    private fun getStartOfWeekTimestamp(): Long {
        val calendar = Calendar.getInstance()
        calendar.set(Calendar.DAY_OF_WEEK, calendar.firstDayOfWeek)
        calendar.set(Calendar.HOUR_OF_DAY, 0)
        calendar.set(Calendar.MINUTE, 0)
        calendar.set(Calendar.SECOND, 0)
        calendar.set(Calendar.MILLISECOND, 0)
        return calendar.timeInMillis
    }

    /**
     * Prototypes/Simulates the batch sync pipeline.
     * Marks all locally pending records as UPLOADED and returns count of updated records.
     */
    override suspend fun simulateBatchSync(): Int {
        return dao.markAllPendingAsUploaded()
    }

    private fun getTodayTimestamps(): Pair<Long, Long> {
        val calendar = Calendar.getInstance()
        calendar.set(Calendar.HOUR_OF_DAY, 0)
        calendar.set(Calendar.MINUTE, 0)
        calendar.set(Calendar.SECOND, 0)
        calendar.set(Calendar.MILLISECOND, 0)
        val startOfDay = calendar.timeInMillis

        calendar.set(Calendar.HOUR_OF_DAY, 23)
        calendar.set(Calendar.MINUTE, 59)
        calendar.set(Calendar.SECOND, 59)
        calendar.set(Calendar.MILLISECOND, 999)
        val endOfDay = calendar.timeInMillis

        return Pair(startOfDay, endOfDay)
    }
}
