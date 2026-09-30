package com.example.freedom

import com.example.freedom.data.local.entity.FarmerEntity
import com.example.freedom.data.local.entity.MilkRecordEntity
import com.example.freedom.domain.query.*
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.flow.first
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/**
 * Deterministic test suite for the FREEDOM data and query layer.
 *
 * Verifies that the canonical FreedomQuery contract compiles and executes
 * deterministically against Room repository data BEFORE any model fine-tuning.
 * Tests zero natural-language interpretation — only structured queries.
 */
class DeterministicQueryLayerTest {

    private lateinit var fakeRepo: FakeMilkRecordRepository
    private lateinit var executor: FreedomQueryExecutor
    private lateinit var knownFarmers: List<FarmerEntity>

    private val now = System.currentTimeMillis()
    private val todayDateStr: String = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date(now))

    @Before
    fun setUp() {
        val yesterdayCal = Calendar.getInstance().apply {
            timeInMillis = now
            add(Calendar.DAY_OF_YEAR, -1)
        }
        val yesterdayMs = yesterdayCal.timeInMillis

        knownFarmers = listOf(
            FarmerEntity(
                farmerId = "farmer-1",
                farmerName = "Suresh Kumar",
                normalizedName = NameNormalizer.normalize("Suresh Kumar")
            ),
            FarmerEntity(
                farmerId = "farmer-2",
                farmerName = "Suresh Gowda",
                normalizedName = NameNormalizer.normalize("Suresh Gowda")
            ),
            FarmerEntity(
                farmerId = "farmer-3",
                farmerName = "Ramesh Naik",
                normalizedName = NameNormalizer.normalize("Ramesh Naik")
            )
        )

        val records = listOf(
            MilkRecordEntity(
                id = "rec-1",
                farmerId = "farmer-1",
                farmerName = "Suresh Kumar",
                quantity = 25.0,
                fat = 4.6,
                snf = 8.6,
                paymentStatus = MilkRecordEntity.PAYMENT_RECORDED_LOCALLY,
                paymentMethod = MilkRecordEntity.METHOD_UPI,
                paymentReference = "UPI-7788",
                payableAmount = 950.00,
                amountPaid = 950.00,
                paymentTimestamp = now - 3600000L,
                createdAt = now
            ),
            MilkRecordEntity(
                id = "rec-2",
                farmerId = "farmer-2",
                farmerName = "Suresh Gowda",
                quantity = 15.0,
                fat = 4.1,
                snf = 8.4,
                paymentStatus = MilkRecordEntity.PAYMENT_PENDING,
                payableAmount = 550.00,
                createdAt = yesterdayMs
            ),
            MilkRecordEntity(
                id = "rec-3",
                farmerId = "farmer-3",
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

    // ── 1. Explicit Entity Resolution Outcome Tests ──────────────────────

    @Test
    fun `Ramesh Naik resolves to status RESOLVED with correct FarmerEntity`() = runBlocking {
        val query = FreedomQuery(
            requestType = RequestType.QUERY,
            entityMention = "Ramesh Naik",
            entityScope = EntityScope.SPECIFIC
        )
        val result = executor.execute(query, knownFarmers = knownFarmers)
        assertTrue(result.success)
        assertEquals(EntityResolutionStatus.RESOLVED, result.entityResolutionStatus)
        assertEquals("farmer-3", result.resolvedFarmer?.farmerId)
        assertEquals("Ramesh Naik", result.resolvedFarmerName)
        assertEquals(1, result.records.size)
    }

    @Test
    fun `Suresh mention yields status AMBIGUOUS when multiple Suresh candidates exist`() = runBlocking {
        val query = FreedomQuery(
            requestType = RequestType.QUERY,
            entityMention = "Suresh",
            entityScope = EntityScope.SPECIFIC
        )
        val result = executor.execute(query, knownFarmers = knownFarmers)
        assertEquals(EntityResolutionStatus.AMBIGUOUS, result.entityResolutionStatus)
        assertTrue("Ambiguity must require clarification", result.needsClarification)
        assertTrue(result.summary.contains("Multiple farmers match 'Suresh'"))
    }

    @Test
    fun `nonexistent farmer yields status NOT_FOUND and is NOT converted to 0 records`() = runBlocking {
        val query = FreedomQuery(
            requestType = RequestType.QUERY,
            entityMention = "UnknownPerson",
            entityScope = EntityScope.SPECIFIC
        )
        val result = executor.execute(query, knownFarmers = knownFarmers)
        assertEquals("Outcome must be NOT_FOUND, not 0 records", EntityResolutionStatus.NOT_FOUND, result.entityResolutionStatus)
        assertTrue(result.summary.contains("Farmer 'UnknownPerson' is not registered in the local database"))
        assertFalse("Must not report generic 0 records found", result.summary.contains("0 collection record"))
    }

    // ── 2. Structured Temporal Representation Tests ──────────────────────

    @Test
    fun `relative period TODAY resolves records created today`() = runBlocking {
        val query = FreedomQuery(
            requestType = RequestType.QUERY,
            temporalConstraint = TemporalConstraint.Relative(RelativePeriod.TODAY)
        )
        val result = executor.execute(query, knownFarmers = knownFarmers)
        assertTrue(result.success)
        assertEquals(2, result.records.size)
    }

    @Test
    fun `relative period YESTERDAY resolves records created yesterday`() = runBlocking {
        val query = FreedomQuery(
            requestType = RequestType.QUERY,
            temporalConstraint = TemporalConstraint.Relative(RelativePeriod.YESTERDAY)
        )
        val result = executor.execute(query, knownFarmers = knownFarmers)
        assertTrue(result.success)
        assertEquals(1, result.records.size)
        assertEquals("Suresh Gowda", result.records.first().farmerName)
    }

    @Test
    fun `explicit calendar date resolves matching records deterministically`() = runBlocking {
        val query = FreedomQuery(
            requestType = RequestType.QUERY,
            temporalConstraint = TemporalConstraint.ExplicitDate(todayDateStr)
        )
        val result = executor.execute(query, knownFarmers = knownFarmers)
        assertTrue(result.success)
        assertEquals(2, result.records.size)
    }

    @Test
    fun `explicit calendar date range filters records within bounds`() = runBlocking {
        val query = FreedomQuery(
            requestType = RequestType.QUERY,
            temporalConstraint = TemporalConstraint.ExplicitRange(
                startDate = "2020-01-01",
                endDate = "2030-12-31"
            )
        )
        val result = executor.execute(query, knownFarmers = knownFarmers)
        assertTrue(result.success)
        assertEquals(3, result.records.size)
    }

    // ── 3. GROUP_BY + Aggregation + ORDER_BY AGGREGATE + LIMIT ──────────

    @Test
    fun `Who gave the most milk means GROUP_BY farmer SUM quantity ORDER BY AGGREGATE DESC LIMIT 1`() = runBlocking {
        val query = FreedomQuery(
            requestType = RequestType.QUERY,
            groupBy = listOf(SchemaField.FARMER_NAME),
            aggregations = listOf(
                AggregationSpec(AggregationType.SUM, SchemaField.QUANTITY)
            ),
            orderBy = QueryOrder.ByAggregate(
                aggregationType = AggregationType.SUM,
                field = SchemaField.QUANTITY,
                direction = SortDirection.DESC
            ),
            limit = 1
        )
        val result = executor.execute(query, knownFarmers = knownFarmers)
        assertTrue(result.success)
        // Suresh Kumar has 25.0L (highest)
        assertTrue(result.summary.contains("Suresh Kumar had the highest quantity: 25.0 L"))
        assertEquals(1, result.records.size)
        assertEquals("Suresh Kumar", result.records.first().farmerName)
    }

    @Test
    fun `COUNT_RECORDS counts individual collection records`() = runBlocking {
        val query = FreedomQuery(
            requestType = RequestType.QUERY,
            aggregations = listOf(AggregationSpec(AggregationType.COUNT_RECORDS))
        )
        val result = executor.execute(query, knownFarmers = knownFarmers)
        assertTrue(result.success)
        assertTrue(result.summary.contains("3 collection record(s)"))
    }

    @Test
    fun `COUNT_DISTINCT_FARMERS counts unique farmers contributing milk`() = runBlocking {
        val query = FreedomQuery(
            requestType = RequestType.QUERY,
            aggregations = listOf(AggregationSpec(AggregationType.COUNT_DISTINCT_FARMERS))
        )
        val result = executor.execute(query, knownFarmers = knownFarmers)
        assertTrue(result.success)
        assertTrue(result.summary.contains("3 farmer(s)"))
    }

    @Test
    fun `HAVING filter retains only groups meeting aggregate condition`() = runBlocking {
        // Group by farmer, SUM(quantity), HAVING total > 20.0L
        val query = FreedomQuery(
            requestType = RequestType.QUERY,
            groupBy = listOf(SchemaField.FARMER_NAME),
            aggregations = listOf(
                AggregationSpec(AggregationType.SUM, SchemaField.QUANTITY)
            ),
            havingFilter = HavingFilter(
                aggregation = AggregationType.SUM,
                field = SchemaField.QUANTITY,
                operator = FilterOperator.GREATER_THAN,
                value = 20.0
            ),
            orderBy = QueryOrder.ByAggregate(
                aggregationType = AggregationType.SUM,
                field = SchemaField.QUANTITY,
                direction = SortDirection.DESC
            )
        )
        val result = executor.execute(query, knownFarmers = knownFarmers)
        assertTrue(result.success)
        // Suresh Kumar (25.0) and Ramesh Naik (22.5) match; Suresh Gowda (15.0) is excluded
        assertEquals(2, result.groupedResults!!.size)
        assertTrue(result.groupedResults!!.containsKey("Suresh Kumar"))
        assertTrue(result.groupedResults!!.containsKey("Ramesh Naik"))
        assertFalse(result.groupedResults!!.containsKey("Suresh Gowda"))
    }

    // ── 4. Multiple Aggregations ────────────────────────────────────────

    @Test
    fun `multiple aggregations computes both SUM quantity and AVG fat on same query`() = runBlocking {
        val query = FreedomQuery(
            requestType = RequestType.QUERY,
            aggregations = listOf(
                AggregationSpec(AggregationType.SUM, SchemaField.QUANTITY),
                AggregationSpec(AggregationType.AVG, SchemaField.FAT)
            )
        )
        val result = executor.execute(query, knownFarmers = knownFarmers)
        assertTrue(result.success)
        // Total: 62.5 L, Average fat: 4.50%
        assertTrue(result.summary.contains("Total quantity: 62.5 L"))
        assertTrue(result.summary.contains("Average fat: 4.50%"))
    }

    // ── 5. Conditional Reporting Specification ──────────────────────────

    @Test
    fun `conditional reporting displays paid details when payment is complete`() = runBlocking {
        val query = FreedomQuery(
            requestType = RequestType.QUERY,
            entityMention = "Suresh Kumar",
            entityScope = EntityScope.SPECIFIC,
            conditionalSpec = ConditionalSpec(
                conditionField = SchemaField.PAYMENT_STATUS,
                expectedValue = "PAID",
                fieldsOnMatch = listOf(SchemaField.PAYMENT_METHOD, SchemaField.PAYMENT_REFERENCE)
            )
        )
        val result = executor.execute(query, knownFarmers = knownFarmers)
        assertTrue(result.success)
        assertTrue(result.summary.contains("Yes. Suresh Kumar was paid"))
        assertTrue(result.summary.contains("UPI"))
        assertTrue(result.summary.contains("UPI-7788"))
    }

    @Test
    fun `conditional reporting indicates pending when payment is not settled`() = runBlocking {
        val query = FreedomQuery(
            requestType = RequestType.QUERY,
            entityMention = "Ramesh Naik",
            entityScope = EntityScope.SPECIFIC,
            conditionalSpec = ConditionalSpec(
                conditionField = SchemaField.PAYMENT_STATUS,
                expectedValue = "PAID"
            )
        )
    @Test
    fun `payment scope ALL checks if all records are paid`() = runBlocking {
        val query = FreedomQuery(
            requestType = RequestType.QUERY,
            entityMention = "Suresh Kumar",
            entityScope = EntityScope.SPECIFIC,
            select = listOf(SchemaField.PAYMENT_STATUS),
            paymentScope = PaymentScope.ALL
        )
        val result = executor.execute(query, knownFarmers = knownFarmers)
        assertTrue(result.success)
        assertTrue(result.summary.contains("All 1 payment(s) for Suresh Kumar are completed"))
    }

    @Test
    fun `payment scope ANY checks if any record has been paid`() = runBlocking {
        val query = FreedomQuery(
            requestType = RequestType.QUERY,
            entityMention = "Ramesh Naik",
            entityScope = EntityScope.SPECIFIC,
            select = listOf(SchemaField.PAYMENT_STATUS),
            paymentScope = PaymentScope.ANY
        )
        val result = executor.execute(query, knownFarmers = knownFarmers)
        assertTrue(result.success)
        assertTrue(result.summary.contains("has not received any payment"))
    }

    // ── 6. Multi-condition Filter Tests ─────────────────────────────────

    @Test
    fun `multi-condition filter with AND logic evaluates all conditions`() = runBlocking {
        val query = FreedomQuery(
            requestType = RequestType.QUERY,
            filters = FilterGroup(
                logic = LogicOperator.AND,
                conditions = listOf(
                    QueryFilter(SchemaField.FAT, FilterOperator.GREATER_THAN, "4.5"),
                    QueryFilter(SchemaField.PAYMENT_STATUS, FilterOperator.EQUALS, "PENDING")
                )
            )
        )
        val result = executor.execute(query, knownFarmers = knownFarmers)
        assertTrue(result.success)
        assertEquals(1, result.records.size)
        assertEquals("Ramesh Naik", result.records.first().farmerName)
    }

    // ── 7. Read / Write Boundary Tests ──────────────────────────────────

    @Test
    fun `read query never triggers write confirmation`() = runBlocking {
        val query = FreedomQuery(
            requestType = RequestType.QUERY,
            entityMention = "Ramesh Naik",
            entityScope = EntityScope.SPECIFIC
        )
        val result = executor.execute(query, knownFarmers = knownFarmers)
        assertFalse(result.needsWriteConfirmation)
        assertTrue(result.writeArgs.isEmpty())
    }

    @Test
    fun `write query requires explicit confirmation with populated writeArgs`() = runBlocking {
        val query = FreedomQuery(
            requestType = RequestType.WRITE,
            writeArgs = mapOf(
                "farmerName" to "Ramesh Naik",
                "quantity" to "18.5"
            )
        )
        val result = executor.execute(query, knownFarmers = knownFarmers)
        assertTrue(result.needsWriteConfirmation)
        assertEquals("18.5", result.writeArgs["quantity"])
    }

    // ── 8. Operational Schema Field Coverage Tests ───────────────────────

    @Test
    fun `AMOUNT_PAID is queryable via SUM aggregation and filtering`() = runBlocking {
        val query = FreedomQuery(
            requestType = RequestType.QUERY,
            filters = FilterGroup(
                logic = LogicOperator.AND,
                conditions = listOf(
                    QueryFilter(SchemaField.AMOUNT_PAID, FilterOperator.GREATER_THAN, "500.0")
                )
            ),
            aggregations = listOf(
                AggregationSpec(AggregationType.SUM, SchemaField.AMOUNT_PAID)
            )
        )
        val result = executor.execute(query, knownFarmers = knownFarmers)
        assertTrue(result.success)
        assertEquals(1, result.records.size) // Suresh Kumar has amountPaid = 950.0
        assertTrue(result.summary.contains("950.0 ₹"))
    }

    @Test
    fun `PAYMENT_TIMESTAMP is queryable via filter and sort`() = runBlocking {
        val query = FreedomQuery(
            requestType = RequestType.QUERY,
            filters = FilterGroup(
                logic = LogicOperator.AND,
                conditions = listOf(
                    QueryFilter(SchemaField.PAYMENT_TIMESTAMP, FilterOperator.IS_NOT_NULL)
                )
            ),
            orderBy = QueryOrder.ByField(SchemaField.PAYMENT_TIMESTAMP, SortDirection.DESC)
        )
        val result = executor.execute(query, knownFarmers = knownFarmers)
        assertTrue(result.success)
        assertEquals(1, result.records.size) // Suresh Kumar has paymentTimestamp
        assertEquals("Suresh Kumar", result.records.first().farmerName)
    }

    @Test
    fun `UPLOAD_STATUS is queryable via filtering and grouping`() = runBlocking {
        val query = FreedomQuery(
            requestType = RequestType.QUERY,
            filters = FilterGroup(
                logic = LogicOperator.AND,
                conditions = listOf(
                    QueryFilter(SchemaField.UPLOAD_STATUS, FilterOperator.EQUALS, "PENDING")
                )
            ),
            groupBy = listOf(SchemaField.UPLOAD_STATUS),
            aggregations = listOf(AggregationSpec(AggregationType.COUNT_RECORDS))
        )
        val result = executor.execute(query, knownFarmers = knownFarmers)
        assertTrue(result.success)
        assertEquals(3, result.records.size) // All 3 records have uploadStatus PENDING
        assertTrue(result.summary.contains("PENDING: 3.0 record(s)"))
    }

    @Test
    fun `UPDATED_AT is queryable via sorting`() = runBlocking {
        val query = FreedomQuery(
            requestType = RequestType.QUERY,
            orderBy = QueryOrder.ByField(SchemaField.UPDATED_AT, SortDirection.DESC)
        )
        val result = executor.execute(query, knownFarmers = knownFarmers)
        assertTrue(result.success)
        assertEquals(3, result.records.size)
    }

    // ── 9. Authoritative Farmer Identity & Scope Tests ───────────────────

    @Test
    fun `COUNT_DISTINCT_FARMERS ignores null or blank farmerId records`() = runBlocking {
        // Add a record with null farmerId
        val recordsWithNullFarmerId = fakeRepo.getAllRecords().first().toMutableList().apply {
            add(
                MilkRecordEntity(
                    id = "rec-null-farmer",
                    farmerId = null,
                    farmerName = "Anonymous Farmer",
                    quantity = 12.0,
                    fat = 4.0,
                    snf = 8.5,
                    createdAt = now
                )
            )
        }
        val customRepo = FakeMilkRecordRepository(recordsWithNullFarmerId)
        val customExecutor = FreedomQueryExecutor(customRepo)

        val query = FreedomQuery(
            requestType = RequestType.QUERY,
            aggregations = listOf(
                AggregationSpec(AggregationType.COUNT_DISTINCT_FARMERS)
            )
        )
        val result = customExecutor.execute(query, knownFarmers = knownFarmers)
        assertTrue(result.success)
        // 4 total records, but only 3 have distinct non-null authoritative farmerIds
        assertTrue(result.summary.contains("3 farmer(s)"))
    }

    @Test
    fun `session organization and worker scoping excludes mismatched session records`() = runBlocking {
        // Add a record belonging to another organization/worker
        val recordsWithForeignOrg = fakeRepo.getAllRecords().first().toMutableList().apply {
            add(
                MilkRecordEntity(
                    id = "rec-foreign-org",
                    orgId = "ORG_OTHER_99",
                    workerId = "WORKER_FOREIGN_88",
                    farmerId = "farmer-99",
                    farmerName = "Foreign Farmer",
                    quantity = 30.0,
                    fat = 4.5,
                    snf = 8.5,
                    createdAt = now
                )
            )
        }
        val customRepo = FakeMilkRecordRepository(recordsWithForeignOrg)
        val customExecutor = FreedomQueryExecutor(customRepo)

        val query = FreedomQuery(
            requestType = RequestType.QUERY,
            aggregations = listOf(
                AggregationSpec(AggregationType.COUNT_RECORDS)
            )
        )
        val result = customExecutor.execute(query, knownFarmers = knownFarmers)
        assertTrue(result.success)
        // Foreign record was deterministically excluded by session scope
        assertEquals(3, result.records.size)
    }

    // ── 10. Payment Scope & Timestamp Semantics Tests ─────────────────────

    @Test
    fun `When was Suresh payment recorded returns formatted timestamp`() = runBlocking {
        val query = FreedomQuery(
            requestType = RequestType.QUERY,
            entityMention = "Suresh Kumar",
            entityScope = EntityScope.SPECIFIC,
            select = listOf(SchemaField.PAYMENT_TIMESTAMP),
            paymentScope = PaymentScope.LATEST
        )
        val result = executor.execute(query, knownFarmers = knownFarmers)
        assertTrue(result.success)
        assertEquals(EntityResolutionStatus.RESOLVED, result.entityResolutionStatus)
        assertTrue(result.summary.contains("Suresh Kumar's payment was recorded on"))
    }

    @Test
    fun `When was Ramesh payment recorded reports pending when timestamp is null`() = runBlocking {
        val query = FreedomQuery(
            requestType = RequestType.QUERY,
            entityMention = "Ramesh Naik",
            entityScope = EntityScope.SPECIFIC,
            select = listOf(SchemaField.PAYMENT_TIMESTAMP),
            paymentScope = PaymentScope.LATEST
        )
        val result = executor.execute(query, knownFarmers = knownFarmers)
        assertTrue(result.success)
        assertEquals(EntityResolutionStatus.RESOLVED, result.entityResolutionStatus)
        assertTrue(result.summary.contains("No payment timestamp recorded for Ramesh Naik (payment is still pending)"))
    }

    @Test
    fun `How much was Suresh paid evaluates AMOUNT_PAID for latest and all scopes`() = runBlocking {
        val latestQuery = FreedomQuery(
            requestType = RequestType.QUERY,
            entityMention = "Suresh Kumar",
            entityScope = EntityScope.SPECIFIC,
            select = listOf(SchemaField.AMOUNT_PAID),
            paymentScope = PaymentScope.LATEST
        )
        val latestResult = executor.execute(latestQuery, knownFarmers = knownFarmers)
        assertTrue(latestResult.success)
        assertTrue(latestResult.summary.contains("Suresh Kumar was paid ₹950.00 on the latest collection record"))

        val allQuery = FreedomQuery(
            requestType = RequestType.QUERY,
            entityMention = "Suresh Kumar",
            entityScope = EntityScope.SPECIFIC,
            select = listOf(SchemaField.AMOUNT_PAID),
            paymentScope = PaymentScope.ALL
        )
        val allResult = executor.execute(allQuery, knownFarmers = knownFarmers)
        assertTrue(allResult.success)
        assertTrue(allResult.summary.contains("Suresh Kumar was paid ₹950.00 across 1 of 1 collection record(s)"))
    }

    @Test
    fun `Are all of Suresh payments completed evaluates PaymentScope ALL`() = runBlocking {
        val query = FreedomQuery(
            requestType = RequestType.QUERY,
            entityMention = "Suresh Kumar",
            entityScope = EntityScope.SPECIFIC,
            select = listOf(SchemaField.PAYMENT_STATUS),
            paymentScope = PaymentScope.ALL
        )
        val result = executor.execute(query, knownFarmers = knownFarmers)
        assertTrue(result.success)
        assertTrue(result.summary.contains("Yes. All 1 payment(s) for Suresh Kumar are completed"))
    }

    @Test
    fun `Did Suresh Gowda receive any payment evaluates PaymentScope ANY`() = runBlocking {
        val query = FreedomQuery(
            requestType = RequestType.QUERY,
            entityMention = "Suresh Gowda",
            entityScope = EntityScope.SPECIFIC,
            select = listOf(SchemaField.PAYMENT_STATUS),
            paymentScope = PaymentScope.ANY
        )
        val result = executor.execute(query, knownFarmers = knownFarmers)
        assertTrue(result.success)
        assertTrue(result.summary.contains("No. Suresh Gowda has not received any payment in this period"))
    }
}
}
