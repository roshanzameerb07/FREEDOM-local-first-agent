package com.example.freedom

import com.example.freedom.data.local.entity.MilkRecordEntity
import com.example.freedom.data.repository.MilkRecordRepository
import com.example.freedom.domain.ai.LocalDeterministicQueryEngine
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class LocalDeterministicQueryEngineTest {

    private lateinit var fakeRepository: FakeMilkRecordRepository
    private lateinit var queryEngine: LocalDeterministicQueryEngine

    @Before
    fun setUp() {
        fakeRepository = FakeMilkRecordRepository()
        queryEngine = LocalDeterministicQueryEngine(fakeRepository)
    }

    @Test
    fun `query pending payments returns only pending records`() = runTest {
        val result = queryEngine.executeQuery("Show farmers whose payment is pending.")

        assertTrue(result.isDeterministicEngine)
        assertEquals(2, result.records.size)
        assertTrue(result.records.all { it.paymentStatus == MilkRecordEntity.PAYMENT_PENDING })
        assertTrue(result.summary.contains("Found 2 record(s) with pending payments"))
    }

    @Test
    fun `query farmer volume returns aggregated litres`() = runTest {
        val result = queryEngine.executeQuery("How much did Ramesh give this week?")

        assertTrue(result.isDeterministicEngine)
        assertEquals(1, result.records.size)
        assertEquals("Ramesh", result.records[0].farmerName)
        assertTrue(result.summary.contains("Ramesh has delivered a total of 18.0 L"))
    }

    @Test
    fun `query today records returns today count and total quantity`() = runTest {
        val result = queryEngine.executeQuery("How many records were collected today?")

        assertTrue(result.isDeterministicEngine)
        assertTrue(result.summary.contains("Today: 3 record(s) collected on device totaling 50.0 L."))
    }

    @Test
    fun `query with non-existent farmer returns clear local message`() = runTest {
        val result = queryEngine.executeQuery("How much did Rajesh give?")

        assertTrue(result.records.isEmpty())
        assertTrue(result.summary.contains("No local records found for farmer 'Rajesh'"))
    }

    private class FakeMilkRecordRepository : MilkRecordRepository {
        private val sampleRecords = listOf(
            MilkRecordEntity(
                farmerName = "Ramesh",
                quantity = 18.0,
                fat = 4.2,
                snf = 8.6,
                paymentStatus = MilkRecordEntity.PAYMENT_PENDING,
                uploadStatus = MilkRecordEntity.UPLOAD_STATUS_PENDING
            ),
            MilkRecordEntity(
                farmerName = "Suresh",
                quantity = 12.0,
                fat = 4.0,
                snf = 8.5,
                paymentStatus = MilkRecordEntity.PAYMENT_PAID,
                uploadStatus = MilkRecordEntity.UPLOAD_STATUS_PENDING
            ),
            MilkRecordEntity(
                farmerName = "Mahesh",
                quantity = 20.0,
                fat = 4.3,
                snf = 8.7,
                paymentStatus = MilkRecordEntity.PAYMENT_PENDING,
                uploadStatus = MilkRecordEntity.UPLOAD_STATUS_PENDING
            )
        )

        override fun getAllRecords(): Flow<List<MilkRecordEntity>> = flowOf(sampleRecords)

        override fun searchAndFilterRecords(
            query: String,
            paymentStatus: String?,
            uploadStatus: String?
        ): Flow<List<MilkRecordEntity>> {
            val filtered = sampleRecords.filter {
                (query.isEmpty() || it.farmerName.contains(query, ignoreCase = true)) &&
                        (paymentStatus == null || it.paymentStatus == paymentStatus) &&
                        (uploadStatus == null || it.uploadStatus == uploadStatus)
            }
            return flowOf(filtered)
        }

        override fun getTodayRecordsCount(): Flow<Int> = flowOf(sampleRecords.size)

        override fun getTodayTotalQuantity(): Flow<Double> = flowOf(sampleRecords.sumOf { it.quantity })

        override fun getPendingUploadCount(): Flow<Int> =
            flowOf(sampleRecords.count { it.uploadStatus == MilkRecordEntity.UPLOAD_STATUS_PENDING })

        override fun getAllRecordsCount(): Flow<Int> = flowOf(sampleRecords.size)

        override suspend fun insertRecord(record: MilkRecordEntity) {}

        override suspend fun getPendingRecords(): List<MilkRecordEntity> =
            sampleRecords.filter { it.uploadStatus == MilkRecordEntity.UPLOAD_STATUS_PENDING }

        override suspend fun getRecordsByFarmerName(farmerName: String): List<MilkRecordEntity> =
            sampleRecords.filter { it.farmerName.equals(farmerName, ignoreCase = true) }

        override suspend fun getPendingPaymentRecords(): List<MilkRecordEntity> =
            sampleRecords.filter { it.paymentStatus == MilkRecordEntity.PAYMENT_PENDING }

        override suspend fun getFarmerQuantityThisWeek(farmerName: String): Double =
            sampleRecords.filter { it.farmerName.equals(farmerName, ignoreCase = true) }.sumOf { it.quantity }

        override suspend fun simulateBatchSync(): Int = 0
    }
}
