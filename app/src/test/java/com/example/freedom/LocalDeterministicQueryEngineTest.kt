package com.example.freedom

import com.example.freedom.data.local.entity.FarmerEntity
import com.example.freedom.data.local.entity.MilkRecordEntity
import com.example.freedom.domain.ai.*
import com.example.freedom.domain.model.WorkerProfileRepository
import com.example.freedom.domain.query.FreedomQueryExecutor
import com.example.freedom.domain.query.NameNormalizer
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class LocalDeterministicQueryEngineTest {

    private lateinit var fakeRepository: FakeMilkRecordRepository
    private lateinit var queryExecutor: FreedomQueryExecutor
    private lateinit var queryEngine: LocalDeterministicQueryEngine

    @Before
    fun setUp() {
        val sampleRecords = listOf(
            MilkRecordEntity(
                id = "1",
                farmerName = "Ramesh",
                quantity = 18.0,
                fat = 4.2,
                snf = 8.6,
                paymentStatus = MilkRecordEntity.PAYMENT_PENDING
            ),
            MilkRecordEntity(
                id = "2",
                farmerName = "Suresh",
                quantity = 12.0,
                fat = 4.0,
                snf = 8.5,
                paymentStatus = MilkRecordEntity.PAYMENT_RECORDED_LOCALLY,
                paymentMethod = MilkRecordEntity.METHOD_CASH
            )
        )
        fakeRepository = FakeMilkRecordRepository(sampleRecords)
        queryExecutor = FreedomQueryExecutor(fakeRepository)
        queryEngine = LocalDeterministicQueryEngine(fakeRepository, queryExecutor)
        queryEngine.knownFarmers = listOf(
            FarmerEntity(
                farmerId = "f-1",
                farmerName = "Ramesh",
                normalizedName = NameNormalizer.normalize("Ramesh")
            ),
            FarmerEntity(
                farmerId = "f-2",
                farmerName = "Suresh",
                normalizedName = NameNormalizer.normalize("Suresh")
            )
        )
        WorkerProfileRepository.updateProfileFromAuth("ORG001", "WORKER001", "Ramesh K. (Field Officer)")
    }

    @Test
    fun testWorkerProfileQuery() = runTest {
        val result = queryEngine.executeQuery("What is my officer ID and assigned area?")
        assertTrue(result is LocalEngineResult.SummaryResult)
        val summaryResult = result as LocalEngineResult.SummaryResult
        assertTrue(summaryResult.summary.summary.contains("WORKER001"))
        assertTrue(summaryResult.summary.summary.contains("Ramesh K."))
    }

    @Test
    fun testOrganizationInfoQuery() = runTest {
        val result = queryEngine.executeQuery("Which organization am I working for?")
        assertTrue(result is LocalEngineResult.SummaryResult)
        val summaryResult = result as LocalEngineResult.SummaryResult
        assertTrue(summaryResult.summary.summary.contains("Mandya District Cooperative"))
    }

    @Test
    fun testFarmerCollectionQuery() = runTest {
        val result = queryEngine.executeQuery("What did Ramesh give?")
        assertTrue(result is LocalEngineResult.SummaryResult)
        val summaryResult = result as LocalEngineResult.SummaryResult
        assertTrue(summaryResult.summary.summary.contains("Ramesh"))
        assertTrue(summaryResult.summary.summary.contains("18.0 L"))
    }

    @Test
    fun testPolicyRAGQuery() = runTest {
        val result = queryEngine.executeQuery("When is payment considered complete?")
        assertTrue(result is LocalEngineResult.SummaryResult)
        val summaryResult = result as LocalEngineResult.SummaryResult
        assertTrue(summaryResult.summary.summary.contains("mandatory conditions"))
    }

    @Test
    fun testUnsupportedQuery() = runTest {
        val result = queryEngine.executeQuery("Why was Suresh paid late?")
        assertTrue(result is LocalEngineResult.SummaryResult)
        val summaryResult = result as LocalEngineResult.SummaryResult
        assertTrue(summaryResult.summary.summary.contains("not store payment delay reasons"))
    }
}
