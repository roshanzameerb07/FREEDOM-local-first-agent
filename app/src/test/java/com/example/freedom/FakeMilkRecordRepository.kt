package com.example.freedom

import com.example.freedom.data.local.entity.MilkRecordEntity
import com.example.freedom.data.repository.MilkRecordRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

class FakeMilkRecordRepository(
    initialRecords: List<MilkRecordEntity> = emptyList()
) : MilkRecordRepository {

    private val _records = MutableStateFlow(initialRecords)

    fun setRecords(records: List<MilkRecordEntity>) {
        _records.value = records
    }

    override fun getAllRecords(): Flow<List<MilkRecordEntity>> = _records

    override fun searchAndFilterRecords(
        query: String,
        paymentStatus: String?,
        uploadStatus: String?
    ): Flow<List<MilkRecordEntity>> {
        return _records.map { list ->
            list.filter { rec ->
                (query.isEmpty() || rec.farmerName.contains(query, ignoreCase = true)) &&
                (paymentStatus == null || rec.paymentStatus == paymentStatus) &&
                (uploadStatus == null || rec.uploadStatus == uploadStatus)
            }
        }
    }

    override fun getTodayRecordsCount(): Flow<Int> = _records.map { it.size }

    override fun getTodayTotalQuantity(): Flow<Double> = _records.map { it.sumOf { r -> r.quantity } }

    override fun getPendingUploadCount(): Flow<Int> = _records.map { it.count { r -> r.uploadStatus == MilkRecordEntity.UPLOAD_STATUS_PENDING } }

    override fun getAllRecordsCount(): Flow<Int> = _records.map { it.size }

    override suspend fun insertRecord(record: MilkRecordEntity) {
        _records.value = _records.value + record
    }

    override suspend fun getRecordById(id: String): MilkRecordEntity? {
        return _records.value.find { it.id == id }
    }

    override suspend fun getPendingRecords(): List<MilkRecordEntity> {
        return _records.value.filter { it.uploadStatus == MilkRecordEntity.UPLOAD_STATUS_PENDING }
    }

    override suspend fun getRecordsByFarmerName(farmerName: String): List<MilkRecordEntity> {
        return _records.value.filter { it.farmerName.contains(farmerName, ignoreCase = true) }
    }

    override suspend fun getPendingPaymentRecords(): List<MilkRecordEntity> {
        return _records.value.filter { it.paymentStatus == MilkRecordEntity.PAYMENT_PENDING }
    }

    override suspend fun getFarmerQuantityThisWeek(farmerName: String): Double {
        return _records.value
            .filter { it.farmerName.contains(farmerName, ignoreCase = true) }
            .sumOf { it.quantity }
    }

    override suspend fun getDistinctFarmersCount(period: String): Int {
        return _records.value.map { it.farmerName.lowercase() }.distinct().size
    }

    override suspend fun getWeeklyTotalQuantity(): Double {
        return _records.value.sumOf { it.quantity }
    }

    override suspend fun getWeeklyRecordsCount(): Int {
        return _records.value.size
    }

    override suspend fun updatePayment(
        id: String,
        paymentStatus: String,
        paymentMethod: String?,
        paymentReference: String?,
        amountPaid: Double?
    ) {
        _records.value = _records.value.map {
            if (it.id == id) {
                it.copy(
                    paymentStatus = paymentStatus,
                    paymentMethod = paymentMethod,
                    paymentReference = paymentReference,
                    amountPaid = amountPaid
                )
            } else it
        }
    }

    override suspend fun simulateBatchSync(): Int {
        val pendingCount = getPendingRecords().size
        _records.value = _records.value.map { it.copy(uploadStatus = MilkRecordEntity.UPLOAD_STATUS_UPLOADED) }
        return pendingCount
    }
}
