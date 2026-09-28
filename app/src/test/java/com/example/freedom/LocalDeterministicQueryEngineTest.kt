package com.example.freedom

import com.example.freedom.data.local.entity.MilkRecordEntity
import com.example.freedom.data.repository.MilkRecordRepository
import com.example.freedom.domain.ai.LocalDeterministicQueryEngine
import com.example.freedom.domain.ai.LocalEngineResult
import com.example.freedom.domain.ai.ToolExecutor
import com.example.freedom.domain.ai.ToolIntent
import com.example.freedom.domain.ai.ToolRequest
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class LocalDeterministicQueryEngineTest {

    private lateinit var fakeRepository: FakeMilkRecordRepository
    private lateinit var toolExecutor: ToolExecutor
    private lateinit var queryEngine: LocalDeterministicQueryEngine

    @Before
    fun setUp() {
        fakeRepository = FakeMilkRecordRepository()
        toolExecutor = ToolExecutor(fakeRepository)
        queryEngine = LocalDeterministicQueryEngine(fakeRepository, toolExecutor)
    }

    // 1. Gemma ToolRequest parsing
    @Test
    fun `parseGemmaResponse correctly parses standard JSON`() {
        val rawJson = """
            {
              "intent": "CREATE_MILK_RECORD",
              "args": {
                "farmerName": "Ramesh",
                "quantity": "18.0",
                "fat": "4.2",
                "snf": "8.6",
                "paymentStatus": "PENDING"
              },
              "needsConfirmation": true
            }
        """.trimIndent()

        val request = queryEngine.parseGemmaResponse(rawJson)
        assertNotNull(request)
        assertEquals(ToolIntent.CREATE_MILK_RECORD, request!!.intent)
        assertEquals("Ramesh", request.args["farmerName"])
        assertEquals("18.0", request.args["quantity"])
        assertEquals("4.2", request.args["fat"])
        assertEquals("8.6", request.args["snf"])
        assertEquals("PENDING", request.args["paymentStatus"])
        assertTrue(request.needsConfirmation)
    }

    @Test
    fun `parseGemmaResponse correctly handles markdown code fences`() {
        val fenced = """
            Here is the requested intent:
            ```json
            {
              "intent": "GET_PENDING_PAYMENTS",
              "args": {},
              "needsConfirmation": false
            }
            ```
        """.trimIndent()

        val request = queryEngine.parseGemmaResponse(fenced)
        assertNotNull(request)
        assertEquals(ToolIntent.GET_PENDING_PAYMENTS, request!!.intent)
        assertTrue(request.args.isEmpty())
        assertFalse(request.needsConfirmation)
    }

    // 2. Valid CREATE_MILK_RECORD
    @Test
    fun `valid CREATE_MILK_RECORD executes successfully and inserts into Room`() = runTest {
        val request = ToolRequest(
            intent = ToolIntent.CREATE_MILK_RECORD,
            args = mapOf(
                "farmerName" to "Ramesh",
                "quantity" to "18.0",
                "fat" to "4.2",
                "snf" to "8.6",
                "paymentStatus" to "PENDING"
            ),
            needsConfirmation = true
        )

        val beforeCount = fakeRepository.insertedRecords.size
        val result = toolExecutor.execute(request)

        assertTrue(result.success)
        assertEquals(beforeCount + 1, fakeRepository.insertedRecords.size)
        val saved = fakeRepository.insertedRecords.last()
        assertEquals("Ramesh", saved.farmerName)
        assertEquals(18.0, saved.quantity, 0.01)
        assertEquals(4.2, saved.fat, 0.01)
        assertEquals(8.6, saved.snf, 0.01)
        assertEquals(MilkRecordEntity.PAYMENT_PENDING, saved.paymentStatus)
    }

    // 3. Invalid quantity
    @Test
    fun `CREATE_MILK_RECORD with invalid quantity is rejected without database write`() = runTest {
        val invalidRequests = listOf(
            ToolRequest(ToolIntent.CREATE_MILK_RECORD, mapOf("farmerName" to "Ramesh", "quantity" to "-5.0", "fat" to "4.2", "snf" to "8.6")),
            ToolRequest(ToolIntent.CREATE_MILK_RECORD, mapOf("farmerName" to "Ramesh", "quantity" to "abc", "fat" to "4.2", "snf" to "8.6")),
            ToolRequest(ToolIntent.CREATE_MILK_RECORD, mapOf("farmerName" to "Ramesh", "quantity" to "0.0", "fat" to "4.2", "snf" to "8.6")),
            ToolRequest(ToolIntent.CREATE_MILK_RECORD, mapOf("farmerName" to "Ramesh", "quantity" to "1500.0", "fat" to "4.2", "snf" to "8.6"))
        )

        for (req in invalidRequests) {
            val beforeCount = fakeRepository.insertedRecords.size
            val result = toolExecutor.execute(req)
            assertFalse("Expected failure for quantity ${req.args["quantity"]}", result.success)
            assertEquals("No DB write should occur on invalid quantity", beforeCount, fakeRepository.insertedRecords.size)
        }
    }

    // 4. Missing required argument
    @Test
    fun `CREATE_MILK_RECORD with missing required fat or snf is rejected without database write`() = runTest {
        val missingArgs = ToolRequest(
            intent = ToolIntent.CREATE_MILK_RECORD,
            args = mapOf(
                "farmerName" to "Ramesh",
                "quantity" to "80.0"
                // fat and snf missing
            ),
            needsConfirmation = true
        )

        val beforeCount = fakeRepository.insertedRecords.size
        val result = toolExecutor.execute(missingArgs)

        assertFalse(result.success)
        assertTrue(result.summary.contains("Validation Failed"))
        assertEquals("Database write must not occur when required arguments are missing", beforeCount, fakeRepository.insertedRecords.size)
    }

    // 5. Ambiguous record requiring confirmation
    @Test
    fun `ambiguous prompt Ramesh gave 80 litres triggers CREATE_MILK_RECORD with needsConfirmation true`() = runTest {
        val result = queryEngine.executeQuery("Ramesh gave 80 litres.")

        assertTrue(result is LocalEngineResult.ToolResult)
        val toolResult = result as LocalEngineResult.ToolResult
        assertEquals(ToolIntent.CREATE_MILK_RECORD, toolResult.request.intent)
        assertEquals("Ramesh", toolResult.request.args["farmerName"])
        assertEquals("80", toolResult.request.args["quantity"])
        assertTrue("Must require confirmation before saving", toolResult.request.needsConfirmation)
        assertEquals("Must not write to database before explicit confirmation", 0, fakeRepository.insertedRecords.size)
    }

    // 6. Pending payments query
    @Test
    fun `pending payments query routes to GET_PENDING_PAYMENTS and returns pending records`() = runTest {
        val result = queryEngine.executeQuery("Show farmers whose payment is pending.")

        assertTrue(result is LocalEngineResult.ToolResult)
        val toolResult = result as LocalEngineResult.ToolResult
        assertEquals(ToolIntent.GET_PENDING_PAYMENTS, toolResult.request.intent)
        assertFalse(toolResult.request.needsConfirmation)

        val execResult = toolExecutor.execute(toolResult.request)
        assertTrue(execResult.success)
        assertEquals(2, execResult.records.size)
        assertTrue(execResult.records.all { it.paymentStatus == MilkRecordEntity.PAYMENT_PENDING })
    }

    // 7. Farmer history query
    @Test
    fun `farmer history query routes to GET_FARMER_HISTORY and returns farmer records`() = runTest {
        val result = queryEngine.executeQuery("What did Ramesh deliver?")

        assertTrue(result is LocalEngineResult.ToolResult)
        val toolResult = result as LocalEngineResult.ToolResult
        assertEquals(ToolIntent.GET_FARMER_HISTORY, toolResult.request.intent)
        assertEquals("Ramesh", toolResult.request.args["farmerName"])

        val execResult = toolExecutor.execute(toolResult.request)
        assertTrue(execResult.success)
        assertEquals(1, execResult.records.size)
        assertEquals("Ramesh", execResult.records[0].farmerName)
    }

    // 8. Today's summary query
    @Test
    fun `today summary query routes to GET_TODAY_SUMMARY and returns today metrics`() = runTest {
        val result = queryEngine.executeQuery("How many records were collected today?")

        assertTrue(result is LocalEngineResult.ToolResult)
        val toolResult = result as LocalEngineResult.ToolResult
        assertEquals(ToolIntent.GET_TODAY_SUMMARY, toolResult.request.intent)

        val execResult = toolExecutor.execute(toolResult.request)
        assertTrue(execResult.success)
        assertTrue(execResult.summary.contains("3 record(s)"))
        assertTrue(execResult.summary.contains("50.0 L"))
    }

    private class FakeMilkRecordRepository : MilkRecordRepository {
        val insertedRecords = mutableListOf<MilkRecordEntity>()

        private val sampleRecords = listOf(
            MilkRecordEntity(
                id = "1",
                farmerName = "Ramesh",
                quantity = 18.0,
                fat = 4.2,
                snf = 8.6,
                paymentStatus = MilkRecordEntity.PAYMENT_PENDING,
                uploadStatus = MilkRecordEntity.UPLOAD_STATUS_PENDING
            ),
            MilkRecordEntity(
                id = "2",
                farmerName = "Suresh",
                quantity = 12.0,
                fat = 4.0,
                snf = 8.5,
                paymentStatus = MilkRecordEntity.PAYMENT_PAID,
                uploadStatus = MilkRecordEntity.UPLOAD_STATUS_PENDING
            ),
            MilkRecordEntity(
                id = "3",
                farmerName = "Mahesh",
                quantity = 20.0,
                fat = 4.3,
                snf = 8.7,
                paymentStatus = MilkRecordEntity.PAYMENT_PENDING,
                uploadStatus = MilkRecordEntity.UPLOAD_STATUS_PENDING
            )
        )

        override fun getAllRecords(): Flow<List<MilkRecordEntity>> = flowOf(sampleRecords + insertedRecords)

        override fun searchAndFilterRecords(
            query: String,
            paymentStatus: String?,
            uploadStatus: String?
        ): Flow<List<MilkRecordEntity>> {
            val all = sampleRecords + insertedRecords
            val filtered = all.filter {
                (query.isEmpty() || it.farmerName.contains(query, ignoreCase = true)) &&
                        (paymentStatus == null || it.paymentStatus == paymentStatus) &&
                        (uploadStatus == null || it.uploadStatus == uploadStatus)
            }
            return flowOf(filtered)
        }

        override fun getTodayRecordsCount(): Flow<Int> = flowOf((sampleRecords + insertedRecords).size)

        override fun getTodayTotalQuantity(): Flow<Double> = flowOf((sampleRecords + insertedRecords).sumOf { it.quantity })

        override fun getPendingUploadCount(): Flow<Int> =
            flowOf((sampleRecords + insertedRecords).count { it.uploadStatus == MilkRecordEntity.UPLOAD_STATUS_PENDING })

        override fun getAllRecordsCount(): Flow<Int> = flowOf((sampleRecords + insertedRecords).size)

        override suspend fun insertRecord(record: MilkRecordEntity) {
            insertedRecords.add(record)
        }

        override suspend fun getPendingRecords(): List<MilkRecordEntity> =
            (sampleRecords + insertedRecords).filter { it.uploadStatus == MilkRecordEntity.UPLOAD_STATUS_PENDING }

        override suspend fun getRecordsByFarmerName(farmerName: String): List<MilkRecordEntity> =
            (sampleRecords + insertedRecords).filter { it.farmerName.equals(farmerName, ignoreCase = true) }

        override suspend fun getPendingPaymentRecords(): List<MilkRecordEntity> =
            (sampleRecords + insertedRecords).filter { it.paymentStatus == MilkRecordEntity.PAYMENT_PENDING }

        override suspend fun getFarmerQuantityThisWeek(farmerName: String): Double =
            (sampleRecords + insertedRecords).filter { it.farmerName.equals(farmerName, ignoreCase = true) }.sumOf { it.quantity }

        override suspend fun simulateBatchSync(): Int = 0
    }
}
