package com.example.freedom

import com.example.freedom.data.local.entity.FarmerEntity
import com.example.freedom.data.local.entity.MilkRecordEntity
import com.example.freedom.domain.query.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.util.Calendar

class SemanticEvaluationSuiteTest {

    private lateinit var fakeRepo: FakeMilkRecordRepository
    private lateinit var executor: FreedomQueryExecutor
    private lateinit var knownFarmers: List<FarmerEntity>

    @Before
    fun setup() {
        val now = System.currentTimeMillis()
        val yesterdayCal = Calendar.getInstance().apply {
            timeInMillis = now
            add(Calendar.DAY_OF_YEAR, -1)
        }
        val yesterdayMs = yesterdayCal.timeInMillis

        knownFarmers = listOf(
            FarmerEntity(
                farmerId = "1",
                farmerName = "Suresh Kumar",
                normalizedName = NameNormalizer.normalize("Suresh Kumar")
            ),
            FarmerEntity(
                farmerId = "2",
                farmerName = "Suresh Gowda",
                normalizedName = NameNormalizer.normalize("Suresh Gowda")
            ),
            FarmerEntity(
                farmerId = "3",
                farmerName = "Ramesh Naik",
                normalizedName = NameNormalizer.normalize("Ramesh Naik")
            )
        )

        val records = listOf(
            MilkRecordEntity(
                id = "1",
                farmerId = "1",
                farmerName = "Suresh Kumar",
                quantity = 25.0,
                fat = 4.6,
                snf = 8.6,
                paymentStatus = MilkRecordEntity.PAYMENT_RECORDED_LOCALLY,
                paymentMethod = MilkRecordEntity.METHOD_UPI,
                paymentReference = "TXN12345",
                payableAmount = 950.00,
                createdAt = now
            ),
            MilkRecordEntity(
                id = "2",
                farmerId = "2",
                farmerName = "Suresh Gowda",
                quantity = 15.0,
                fat = 4.1,
                snf = 8.4,
                paymentStatus = MilkRecordEntity.PAYMENT_PENDING,
                payableAmount = 550.00,
                createdAt = yesterdayMs
            ),
            MilkRecordEntity(
                id = "3",
                farmerId = "3",
                farmerName = "Ramesh Naik",
                quantity = 22.5,
                fat = 4.8,
                snf = 8.7,
                paymentStatus = MilkRecordEntity.PAYMENT_PENDING,
                payableAmount = 880.00,
                createdAt = now
            )
        )
        fakeRepo = FakeMilkRecordRepository(records)
        executor = FreedomQueryExecutor(fakeRepo)
    }

    // 1. Simple lookups
    @Test
    fun test1_SimpleLookup() = runBlocking {
        val query = QwenQueryParser.parseFallback("What did Ramesh Naik give?", knownFarmers)
        val result = executor.execute(query, knownFarmers = knownFarmers)
        assertTrue(result.success)
        assertTrue(result.summary.contains("Ramesh Naik"))
    }

    // 2. Paraphrases
    @Test
    fun test2_Paraphrase() = runBlocking {
        val q1 = QwenQueryParser.parseFallback("Ramesh Naik's milk quantity", knownFarmers)
        val q2 = QwenQueryParser.parseFallback("How much milk was supplied by Ramesh Naik?", knownFarmers)
        val r1 = executor.execute(q1, knownFarmers = knownFarmers)
        val r2 = executor.execute(q2, knownFarmers = knownFarmers)
        assertTrue(r1.summary.contains("22.5"))
        assertTrue(r2.summary.contains("22.5"))
    }

    // 3. Indian-English phrasing
    @Test
    fun test3_IndianEnglishPhrasing() = runBlocking {
        val query = QwenQueryParser.parseFallback("Ramesh Naik how much milk given today", knownFarmers)
        val result = executor.execute(query, knownFarmers = knownFarmers)
        assertTrue(result.success)
        assertTrue(result.summary.contains("Ramesh Naik"))
    }

    // 4. Bad grammar
    @Test
    fun test4_BadGrammar() = runBlocking {
        val query = QwenQueryParser.parseFallback("ramesh naik milk total how much give this week", knownFarmers)
        val result = executor.execute(query, knownFarmers = knownFarmers)
        assertTrue(result.success)
        assertTrue(result.summary.contains("Ramesh Naik"))
    }

    // 5. Date expressions
    @Test
    fun test5_DateExpressions() = runBlocking {
        val query = QwenQueryParser.parseFallback("What did Ramesh Naik give this week?", knownFarmers)
        val result = executor.execute(query, knownFarmers = knownFarmers)
        assertTrue(result.success)
        assertFalse(result.summary.isEmpty())
    }

    // 6. Numeric filters
    @Test
    fun test6_NumericFilters() = runBlocking {
        val query = QwenQueryParser.parseFallback("Which farmers gave more than 20 litres yesterday?", knownFarmers)
        val result = executor.execute(query, knownFarmers = knownFarmers)
        assertTrue(result.success)
        assertTrue(result.records.all { it.quantity > 20.0 })
    }

    // 7. SUM
    @Test
    fun test7_SUM() = runBlocking {
        val query = QwenQueryParser.parseFallback("Suresh Kumar's total collection since Monday?", knownFarmers)
        val result = executor.execute(query, knownFarmers = knownFarmers)
        assertTrue(result.success)
        assertTrue(result.summary.contains("25.0 L"))
    }

    // 8. COUNT
    @Test
    fun test8_COUNT() = runBlocking {
        val query = FreedomQuery(aggregations = listOf(AggregationSpec(AggregationType.COUNT_RECORDS)))
        val result = executor.execute(query, knownFarmers = knownFarmers)
        assertTrue(result.summary.contains("3 collection record(s)"))
    }

    // 9. AVG
    @Test
    fun test9_AVG() = runBlocking {
        val query = QwenQueryParser.parseFallback("What was Suresh Kumar's average fat this month?", knownFarmers)
        val result = executor.execute(query, knownFarmers = knownFarmers)
        assertTrue(result.success)
        assertTrue(result.summary.contains("4.60%"))
    }

    // 10. MIN/MAX
    @Test
    fun test10_MIN_MAX() = runBlocking {
        val query = QwenQueryParser.parseFallback("Who gave the most milk yesterday?", knownFarmers)
        val result = executor.execute(query, knownFarmers = knownFarmers)
        assertTrue(result.success)
        assertEquals(1, result.records.size)
        assertEquals("Suresh Gowda", result.records.first().farmerName)
    }

    // 11. Sorting
    @Test
    fun test11_Sorting() = runBlocking {
        val query = FreedomQuery(orderBy = QueryOrder.ByField(SchemaField.QUANTITY, SortDirection.DESC))
        val result = executor.execute(query, knownFarmers = knownFarmers)
        assertEquals(25.0, result.records.first().quantity, 0.01)
    }

    // 12. Multiple conditions
    @Test
    fun test12_MultipleConditions() = runBlocking {
        val query = QwenQueryParser.parseFallback("Which farmers had fat above 4.5 and still have pending payment?", knownFarmers)
        val result = executor.execute(query, knownFarmers = knownFarmers)
        assertTrue(result.success)
        assertTrue(result.records.all { it.fat > 4.5 && it.paymentStatus == MilkRecordEntity.PAYMENT_PENDING })
    }

    // 13. Payment queries
    @Test
    fun test13_PaymentQueries() = runBlocking {
        val query = QwenQueryParser.parseFallback("Did Suresh Kumar get paid?", knownFarmers)
        val result = executor.execute(query, knownFarmers = knownFarmers)
        assertTrue(result.success)
        assertTrue(result.summary.contains("Yes") || result.summary.contains("Paid"))
    }

    // 14. Conditional payment queries
    @Test
    fun test14_ConditionalPaymentQueries() = runBlocking {
        val query = QwenQueryParser.parseFallback("If Suresh Kumar was paid, tell me the payment method and reference ID.", knownFarmers)
        val result = executor.execute(query, knownFarmers = knownFarmers)
        assertTrue(result.success)
        assertTrue(result.summary.contains("UPI"))
        assertTrue(result.summary.contains("TXN12345"))
    }

    // 15. Ambiguous farmer names
    @Test
    fun test15_AmbiguousFarmerNames() = runBlocking {
        val query = QwenQueryParser.parseFallback("How much did Suresh give this week?", knownFarmers)
        val result = executor.execute(query, knownFarmers = knownFarmers)
        assertTrue(result.needsClarification)
        assertTrue(result.summary.contains("Multiple farmers match 'Suresh'"))
    }

    // 16. Missing information
    @Test
    fun test16_MissingInformation() = runBlocking {
        val query = QwenQueryParser.parseFallbackWithEntity("What did NonexistentFarmer give?", "NonexistentFarmer")
        val result = executor.execute(query, knownFarmers = knownFarmers)
        assertEquals(EntityResolutionStatus.NOT_FOUND, result.entityResolutionStatus)
        assertTrue(result.summary.contains("not registered in the local database"))
    }

    // 17. Unsupported questions
    @Test
    fun test17_UnsupportedQuestions() = runBlocking {
        val query = QwenQueryParser.parseFallback("Why was Suresh paid late?", knownFarmers)
        val result = executor.execute(query, knownFarmers = knownFarmers)
        assertTrue(result.isUnsupported)
        assertTrue(result.summary.contains("not store payment delay reasons"))
    }

    // 18. Multi-part questions
    @Test
    fun test18_MultiPartQuestions() = runBlocking {
        val query = QwenQueryParser.parseFallback("Did Suresh Kumar's payment get completed? If yes, give me payment method and reference ID.", knownFarmers)
        val result = executor.execute(query, knownFarmers = knownFarmers)
        assertTrue(result.success)
        assertTrue(result.summary.contains("UPI"))
        assertTrue(result.summary.contains("TXN12345"))
    }
}
