package com.example.freedom

import com.example.freedom.data.local.entity.MilkRecordEntity
import com.example.freedom.data.repository.MilkRecordRepository
import com.example.freedom.domain.ai.*
import com.example.freedom.domain.model.WorkerProfileRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
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
        WorkerProfileRepository.updateProfileFromAuth("ORG001", "WORKER001", "Ramesh K. (Field Officer)")
    }

    // 1. Gemma ToolRequest parsing with Numeric Fidelity
    @Test
    fun `parseGemmaResponse correctly parses standard JSON with exact decimals`() {
        val userInput = "Ramesh gave 18.5 litres, fat 4.2 and SNF 8.6. Payment is pending."
        val rawJson = """
            {
              "intent": "CREATE_MILK_RECORD",
              "args": {
                "farmerName": "Ramesh",
                "quantity": "18.5",
                "fat": "4.2",
                "snf": "8.6",
                "paymentStatus": "PENDING"
              },
              "needsConfirmation": true
            }
        """.trimIndent()

        val request = queryEngine.parseGemmaResponse(rawJson, userInput)
        assertNotNull(request)
        assertEquals(ToolIntent.CREATE_MILK_RECORD, request!!.intent)
        assertEquals("Ramesh", request.args["farmerName"])
        assertEquals("18.5", request.args["quantity"])
        assertEquals("4.2", request.args["fat"])
        assertEquals("8.6", request.args["snf"])
        assertEquals("PENDING", request.args["paymentStatus"])
        assertTrue(request.needsConfirmation)
        assertEquals(ExecutionMode.GEMMA_EXECUTED, request.executionMode)
    }

    @Test
    fun `parseGemmaResponse reconciles truncated decimals from user prompt`() {
        val userInput = "Ramesh gave 18.5 litres, fat 4.2 and SNF 8.6. Payment is pending."
        // Simulate quantized model truncating floats
        val rawJson = """
            {
              "intent": "CREATE_MILK_RECORD",
              "args": {
                "farmerName": "Ramesh",
                "quantity": "18",
                "fat": "4",
                "snf": "8",
                "paymentStatus": "PENDING"
              },
              "needsConfirmation": true
            }
        """.trimIndent()

        val request = queryEngine.parseGemmaResponse(rawJson, userInput)
        assertNotNull(request)
        assertEquals(ToolIntent.CREATE_MILK_RECORD, request!!.intent)
        assertEquals("18.5", request.args["quantity"])
        assertEquals("4.2", request.args["fat"])
        assertEquals("8.6", request.args["snf"])
    }

    @Test
    fun `parseGemmaResponse preserves 2 decimal places such as 4_25 and 8_65`() {
        val userInput = "Suresh gave 18.50 litres, fat 4.25% and SNF 8.65%."
        val rawJson = """
            {
              "intent": "CREATE_MILK_RECORD",
              "args": {
                "farmerName": "Suresh",
                "quantity": "18",
                "fat": "4",
                "snf": "8"
              },
              "needsConfirmation": true
            }
        """.trimIndent()

        val request = queryEngine.parseGemmaResponse(rawJson, userInput)
        assertNotNull(request)
        assertEquals(ToolIntent.CREATE_MILK_RECORD, request!!.intent)
        assertEquals("18.50", request.args["quantity"])
        assertEquals("4.25", request.args["fat"])
        assertEquals("8.65", request.args["snf"])
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
        assertFalse(request.needsConfirmation)
        assertEquals(ExecutionMode.GEMMA_EXECUTED, request.executionMode)
    }

    // 2. Deterministic Routing: Worker Profile
    @Test
    fun `deterministic routing handles officer and worker profile queries`() = runTest {
        val result = queryEngine.executeQuery("What is my officer ID and assigned area?")
        assertTrue(result is LocalEngineResult.ToolResult)
        val toolResult = result as LocalEngineResult.ToolResult
        assertEquals(ToolIntent.GET_WORKER_PROFILE, toolResult.request.intent)

        val execResult = toolExecutor.execute(toolResult.request)
        assertTrue(execResult.success)
        assertEquals("Officer Profile", execResult.sourceOfTruth)
        assertTrue(execResult.summary.contains("WORKER001"))
        assertTrue(execResult.summary.contains("Ramesh K."))
    }

    // 3. Deterministic Routing: Organization Info
    @Test
    fun `deterministic routing handles organization queries`() = runTest {
        val result = queryEngine.executeQuery("Which organization am I working for?")
        assertTrue(result is LocalEngineResult.ToolResult)
        val toolResult = result as LocalEngineResult.ToolResult
        assertEquals(ToolIntent.GET_ORGANIZATION_INFO, toolResult.request.intent)

        val execResult = toolExecutor.execute(toolResult.request)
        assertTrue(execResult.success)
        assertEquals("Cooperative Profile", execResult.sourceOfTruth)
        assertTrue(execResult.summary.contains("Mandya District Cooperative"))
    }

    // 4. Deterministic Routing: Farmers Covered Count
    @Test
    fun `deterministic routing handles distinct farmers count`() = runTest {
        val result = queryEngine.executeQuery("How many farmers did I cover today?")
        assertTrue(result is LocalEngineResult.ToolResult)
        val toolResult = result as LocalEngineResult.ToolResult
        assertEquals(ToolIntent.COUNT_FARMERS_COVERED, toolResult.request.intent)

        val execResult = toolExecutor.execute(toolResult.request)
        assertTrue(execResult.success)
        assertEquals("Local Database", execResult.sourceOfTruth)
        assertTrue(execResult.summary.contains("3 farmer(s)"))
    }

    // 5. Deterministic Routing: Weekly Summary
    @Test
    fun `deterministic routing handles weekly farmer summary`() = runTest {
        val result = queryEngine.executeQuery("How much did Ramesh give this week?")
        assertTrue(result is LocalEngineResult.ToolResult)
        val toolResult = result as LocalEngineResult.ToolResult
        assertEquals(ToolIntent.GET_WEEKLY_WORKER_SUMMARY, toolResult.request.intent)
        assertEquals("Ramesh", toolResult.request.args["farmerName"])

        val execResult = toolExecutor.execute(toolResult.request)
        assertTrue(execResult.success)
        assertEquals("Local Database", execResult.sourceOfTruth)
        assertTrue(execResult.summary.contains("18.0 L"))
    }

    // 6. Deterministic Routing: Local RAG Knowledge
    @Test
    fun `deterministic routing handles local knowledge queries with grounded citations`() = runTest {
        val result = queryEngine.executeQuery("When is payment considered complete?")
        assertTrue(result is LocalEngineResult.ToolResult)
        val toolResult = result as LocalEngineResult.ToolResult
        assertEquals(ToolIntent.SEARCH_LOCAL_KNOWLEDGE, toolResult.request.intent)

        val execResult = toolExecutor.execute(toolResult.request)
        assertTrue(execResult.success)
        assertNotNull(execResult.ragDocument)
        assertEquals("KNOW-PAY-01", execResult.ragDocument!!.id)
        assertTrue(execResult.sourceOfTruth.contains("Clause 4.2"))
        assertTrue(execResult.summary.contains("mandatory conditions"))
        assertEquals(ExecutionMode.RAG_EXECUTION, execResult.executionMode)
    }

    // 7. Deterministic Validation & Guardrails
    @Test
    fun `incomplete input blocks database write without guessing or defaulting to 0`() = runTest {
        val result = queryEngine.executeQuery("Ramesh gave 80 litres.")
        assertTrue(result is LocalEngineResult.ToolResult)
        val toolResult = result as LocalEngineResult.ToolResult
        assertEquals(ToolIntent.CREATE_MILK_RECORD, toolResult.request.intent)

        // Missing fat and snf must NOT be defaulted to 0.0
        assertFalse(toolResult.request.args.containsKey("fat"))
        assertFalse(toolResult.request.args.containsKey("snf"))

        val execResult = toolExecutor.execute(toolResult.request)
        assertFalse("Incomplete record must fail deterministic validation", execResult.success)
        assertTrue(execResult.summary.contains("Missing or invalid information"))
        assertEquals("Input Validation Guardrail", execResult.sourceOfTruth)
        assertEquals(0, fakeRepository.insertedRecords.size)
    }

    @Test
    fun `valid milk record execution succeeds and saves to repository with exact values`() = runTest {
        val request = ToolRequest(
            intent = ToolIntent.CREATE_MILK_RECORD,
            args = mapOf(
                "farmerName" to "Ramesh",
                "quantity" to "18.5",
                "fat" to "4.2",
                "snf" to "8.6",
                "paymentStatus" to "PENDING"
            ),
            needsConfirmation = true
        )

        val execResult = toolExecutor.execute(request)
        assertTrue(execResult.success)
        assertEquals(1, fakeRepository.insertedRecords.size)
        val saved = fakeRepository.insertedRecords[0]
        assertEquals("Ramesh", saved.farmerName)
        assertEquals(18.5, saved.quantity, 0.001)
        assertEquals(4.2, saved.fat, 0.001)
        assertEquals(8.6, saved.snf, 0.001)
        assertEquals("PENDING", saved.paymentStatus)
        assertEquals("ORG001", saved.orgId)
        assertEquals("WORKER001", saved.workerId)
    }

    // 8. Pending payments execution
    @Test
    fun `pending payments execution returns correct pending records from local storage`() = runTest {
        val result = queryEngine.executeQuery("Show pending payments.")
        assertTrue(result is LocalEngineResult.ToolResult)
        val toolResult = result as LocalEngineResult.ToolResult
        assertEquals(ToolIntent.GET_PENDING_PAYMENTS, toolResult.request.intent)

        val execResult = toolExecutor.execute(toolResult.request)
        assertTrue(execResult.success)
        assertEquals(2, execResult.records.size)
    }

    // 9. Payment recording lifecycle test
    @Test
    fun `payment recording updates status to RECORDED_LOCALLY with method and reference`() = runTest {
        fakeRepository.updatePayment(
            id = "1",
            paymentStatus = MilkRecordEntity.PAYMENT_RECORDED_LOCALLY,
            paymentMethod = MilkRecordEntity.METHOD_UPI,
            paymentReference = "UPI-REF-9988",
            amountPaid = 675.0
        )

        val updated = fakeRepository.getRecordById("1")
        assertNotNull(updated)
        assertEquals(MilkRecordEntity.PAYMENT_RECORDED_LOCALLY, updated!!.paymentStatus)
        assertEquals(MilkRecordEntity.METHOD_UPI, updated.paymentMethod)
        assertEquals("UPI-REF-9988", updated.paymentReference)
        assertEquals(675.0, updated.amountPaid ?: 0.0, 0.001)
    }

    private class FakeMilkRecordRepository : MilkRecordRepository {
        val insertedRecords = mutableListOf<MilkRecordEntity>()

        private val sampleRecords = mutableListOf(
            MilkRecordEntity(
                id = "1",
                orgId = "ORG001",
                workerId = "WORKER001",
                farmerName = "Ramesh",
                quantity = 18.0,
                fat = 4.2,
                snf = 8.6,
                paymentStatus = MilkRecordEntity.PAYMENT_PENDING,
                uploadStatus = MilkRecordEntity.UPLOAD_STATUS_PENDING
            ),
            MilkRecordEntity(
                id = "2",
                orgId = "ORG001",
                workerId = "WORKER001",
                farmerName = "Suresh",
                quantity = 12.0,
                fat = 4.0,
                snf = 8.5,
                paymentStatus = MilkRecordEntity.PAYMENT_RECORDED_LOCALLY,
                paymentMethod = MilkRecordEntity.METHOD_CASH,
                uploadStatus = MilkRecordEntity.UPLOAD_STATUS_PENDING
            ),
            MilkRecordEntity(
                id = "3",
                orgId = "ORG001",
                workerId = "WORKER001",
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

        override suspend fun getRecordById(id: String): MilkRecordEntity? {
            return (sampleRecords + insertedRecords).find { it.id == id }
        }

        override suspend fun getPendingRecords(): List<MilkRecordEntity> =
            (sampleRecords + insertedRecords).filter { it.uploadStatus == MilkRecordEntity.UPLOAD_STATUS_PENDING }

        override suspend fun getRecordsByFarmerName(farmerName: String): List<MilkRecordEntity> =
            (sampleRecords + insertedRecords).filter { it.farmerName.equals(farmerName, ignoreCase = true) }

        override suspend fun getPendingPaymentRecords(): List<MilkRecordEntity> =
            (sampleRecords + insertedRecords).filter { it.paymentStatus == MilkRecordEntity.PAYMENT_PENDING }

        override suspend fun getFarmerQuantityThisWeek(farmerName: String): Double =
            (sampleRecords + insertedRecords).filter { it.farmerName.equals(farmerName, ignoreCase = true) }.sumOf { it.quantity }

        override suspend fun getDistinctFarmersCount(period: String): Int =
            (sampleRecords + insertedRecords).map { it.farmerName.lowercase() }.distinct().size

        override suspend fun getWeeklyTotalQuantity(): Double =
            (sampleRecords + insertedRecords).sumOf { it.quantity }

        override suspend fun getWeeklyRecordsCount(): Int =
            (sampleRecords + insertedRecords).size

        override suspend fun updatePayment(
            id: String,
            paymentStatus: String,
            paymentMethod: String?,
            paymentReference: String?,
            amountPaid: Double?
        ) {
            val idx = sampleRecords.indexOfFirst { it.id == id }
            if (idx != -1) {
                val old = sampleRecords[idx]
                sampleRecords[idx] = old.copy(
                    paymentStatus = paymentStatus,
                    paymentMethod = paymentMethod,
                    paymentReference = paymentReference,
                    amountPaid = amountPaid
                )
            }
        }

        override suspend fun simulateBatchSync(): Int = 0
    }
}
