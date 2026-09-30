package com.example.freedom.domain.query

import com.example.freedom.data.local.entity.FarmerEntity
import com.example.freedom.data.local.entity.MilkRecordEntity
import com.example.freedom.data.repository.MilkRecordRepository
import com.example.freedom.domain.model.WorkerProfileRepository
import com.example.freedom.domain.rag.KnowledgeDocument
import com.example.freedom.domain.rag.LocalRagRetriever
import kotlinx.coroutines.flow.first
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * Deterministic query executor for the canonical FREEDOM Query DSL.
 *
 * Pipeline:
 * FreedomQuery → Validate → Dispatch → Entity Resolution → Temporal Resolution
 * → Filter → Group By / Having → Sort (Field or Aggregate) → Limit → Aggregate → Synthesize Response
 *
 * Explicit Invariants:
 * - Entity absence returns NOT_FOUND status, never conflated with "0 records".
 * - All calculations, aggregations, and conditions are evaluated deterministically in Kotlin.
 * - Read queries NEVER trigger write operations or prompt confirmation dialogues.
 */
class FreedomQueryExecutor(
    private val repository: MilkRecordRepository,
    private val entityResolver: EntityResolver = EntityResolver(),
    private val ragRetriever: LocalRagRetriever = LocalRagRetriever()
) {

    data class ExecutionResult(
        val summary: String,
        val records: List<MilkRecordEntity> = emptyList(),
        val success: Boolean = true,
        val entityResolutionStatus: EntityResolutionStatus = EntityResolutionStatus.NOT_APPLICABLE,
        val resolvedFarmer: FarmerEntity? = null,
        val resolvedFarmerName: String? = null,
        val needsClarification: Boolean = false,
        val needsWriteConfirmation: Boolean = false,
        val writeArgs: Map<String, String> = emptyMap(),
        val isUnsupported: Boolean = false,
        val groupedResults: Map<String, List<MilkRecordEntity>>? = null,
        val validationErrors: List<String> = emptyList()
    )

    suspend fun execute(
        query: FreedomQuery,
        userPrompt: String = "",
        knownFarmers: List<FarmerEntity> = emptyList()
    ): ExecutionResult {

        // Step 1: Validate DSL query structure
        val validation = QueryValidator.validate(query)
        if (!validation.isValid) {
            return ExecutionResult(
                summary = "Query validation failed: ${validation.errors.joinToString("; ")}",
                success = false,
                validationErrors = validation.errors
            )
        }

        // Step 1.5: Framework Authorization Policy boundary (checked when session is active)
        val activeSession = com.example.freedom.framework.session.SessionManager.getCurrentSessionOrNull()
        if (activeSession != null) {
            val authResult = com.example.freedom.framework.security.AuthorizationPolicy.authorize(query, activeSession)
            if (!authResult.isAuthorized) {
                return ExecutionResult(
                    summary = "Authorization denied: ${authResult.deniedReason ?: "User not permitted to execute this query."}",
                    success = false,
                    validationErrors = listOf(authResult.deniedReason ?: "Authorization denied")
                )
            }
        }

        // Step 2: Dispatch by requestType
        return when (query.requestType) {
            RequestType.CLARIFY -> ExecutionResult(
                summary = query.clarifyReason ?: "Please provide more details.",
                needsClarification = true
            )
            RequestType.UNSUPPORTED -> ExecutionResult(
                summary = query.unsupportedReason ?: "This information is not available in local records.",
                isUnsupported = true,
                success = false
            )
            RequestType.WRITE -> ExecutionResult(
                summary = "Please review the milk collection entry details before saving.",
                needsWriteConfirmation = true,
                writeArgs = query.writeArgs
            )
            RequestType.QUERY -> executeReadQuery(query, knownFarmers)
        }
    }

    private suspend fun executeReadQuery(
        query: FreedomQuery,
        knownFarmers: List<FarmerEntity>
    ): ExecutionResult {

        return when (query.target) {
            QueryTarget.WORKER_PROFILE -> executeWorkerProfile()
            QueryTarget.ORGANIZATION_INFO -> executeOrganizationInfo()
            QueryTarget.KNOWLEDGE_BASE -> executeKnowledgeSearch(query)
            QueryTarget.MILK_RECORDS -> executeMilkRecordsQuery(query, knownFarmers)
        }
    }

    private fun executeWorkerProfile(): ExecutionResult {
        val profile = WorkerProfileRepository.getProfile()
        return ExecutionResult(
            summary = "Officer ID: ${profile.workerId}\nName: ${profile.workerName}\nRole: ${profile.role}\nAssigned Area: ${profile.assignedArea}\nCenter: ${profile.centerName}"
        )
    }

    private fun executeOrganizationInfo(): ExecutionResult {
        val org = WorkerProfileRepository.getOrganizationInfo()
        return ExecutionResult(
            summary = "${org.organizationName} (ID: ${org.organizationId})\nRegistration: ${org.registrationNumber}\nRegion: ${org.regionalDistrict}"
        )
    }

    private fun executeKnowledgeSearch(query: FreedomQuery): ExecutionResult {
        val kQuery = query.knowledgeQuery ?: return ExecutionResult(
            summary = "No knowledge query provided.",
            success = false
        )
        val ragResult = ragRetriever.search(kQuery)
        return if (ragResult.hasEvidence && ragResult.topDocument != null) {
            ExecutionResult(
                summary = ragResult.topDocument.content
            )
        } else {
            ExecutionResult(
                summary = "Information not found in local cooperative documents. FREEDOM does not make up policies without verified document evidence.",
                success = false
            )
        }
    }

    private suspend fun executeMilkRecordsQuery(
        query: FreedomQuery,
        knownFarmers: List<FarmerEntity>
    ): ExecutionResult {

        // 1. Entity Resolution
        var resolvedFarmer: FarmerEntity? = null
        var resolutionStatus = EntityResolutionStatus.NOT_APPLICABLE

        if (query.entityScope == EntityScope.SPECIFIC && !query.entityMention.isNullOrBlank()) {
            val resolution = entityResolver.resolve(query.entityMention, knownFarmers)
            when (resolution) {
                is EntityResolver.Resolution.Resolved -> {
                    resolvedFarmer = resolution.farmer
                    resolutionStatus = EntityResolutionStatus.RESOLVED
                }
                is EntityResolver.Resolution.Ambiguous -> {
                    val names = resolution.candidates.map { it.farmerName }.joinToString(", ")
                    return ExecutionResult(
                        summary = "Multiple farmers match '${resolution.mention}': $names. Please specify which farmer.",
                        needsClarification = true,
                        entityResolutionStatus = EntityResolutionStatus.AMBIGUOUS
                    )
                }
                is EntityResolver.Resolution.NotFound -> {
                    // CRITICAL: Entity not found is NOT "0 records found"
                    return ExecutionResult(
                        summary = "Farmer '${resolution.mention}' is not registered in the local database.",
                        records = emptyList(),
                        entityResolutionStatus = EntityResolutionStatus.NOT_FOUND
                    )
                }
            }
        }

        // 2. Temporal Resolution
        val temporalRange: Pair<Long, Long>? = query.temporalConstraint?.let {
            TemporalResolver.resolveConstraint(it)
        }

        // 3. Fetch all local records scoped deterministically to authenticated session orgId and workerId
        val sessionProfile = WorkerProfileRepository.getProfile()
        val activeSession = com.example.freedom.framework.session.SessionManager.getCurrentSessionOrNull()
        val sessionOrgId = activeSession?.organizationId ?: sessionProfile.organizationId
        val sessionWorkerId = activeSession?.currentUser?.userId ?: sessionProfile.workerId
        val isDemoSession = sessionOrgId == "FREEDOM-DEMO-001" || sessionOrgId == "ORG001"
        val allRecords = repository.getAllRecords().first().filter {
            (it.orgId.isBlank() || it.orgId == sessionOrgId || (isDemoSession && it.orgId in listOf("ORG001", "FREEDOM-DEMO-001"))) &&
            (it.workerId.isBlank() || it.workerId == sessionWorkerId || (isDemoSession && it.workerId in listOf("WORKER001", "DEMO-FIELD-01", sessionWorkerId)))
        }

        // 4. Apply farmer identity filter
        var filtered = if (resolvedFarmer != null) {
            allRecords.filter {
                (it.farmerId != null && it.farmerId == resolvedFarmer.farmerId) ||
                it.farmerName.equals(resolvedFarmer.farmerName, ignoreCase = true)
            }
        } else {
            allRecords
        }

        // 5. Apply temporal filter
        if (temporalRange != null) {
            val (startMs, endMs) = temporalRange
            filtered = filtered.filter { it.createdAt in startMs..endMs }
        }

        // 6. Apply filter conditions (AND / OR)
        if (query.filters.conditions.isNotEmpty()) {
            filtered = when (query.filters.logic) {
                LogicOperator.AND -> {
                    var cur = filtered
                    for (c in query.filters.conditions) {
                        cur = cur.filter { matchesFilter(it, c) }
                    }
                    cur
                }
                LogicOperator.OR -> {
                    filtered.filter { rec ->
                        query.filters.conditions.any { matchesFilter(rec, it) }
                    }
                }
            }
        }

        // 7. Existence Check
        if (query.existenceCheck) {
            val label = resolvedFarmer?.farmerName ?: "matching records"
            return if (filtered.isNotEmpty()) {
                ExecutionResult(
                    summary = "Yes, records exist for $label (${filtered.size} delivery record(s) found).",
                    records = filtered,
                    entityResolutionStatus = resolutionStatus,
                    resolvedFarmer = resolvedFarmer,
                    resolvedFarmerName = resolvedFarmer?.farmerName
                )
            } else {
                ExecutionResult(
                    summary = "No collection records found for $label in the specified period.",
                    records = emptyList(),
                    entityResolutionStatus = resolutionStatus,
                    resolvedFarmer = resolvedFarmer,
                    resolvedFarmerName = resolvedFarmer?.farmerName
                )
            }
        }

        // 8. Group By + Having + Aggregate Ordering
        if (query.groupBy.isNotEmpty()) {
            return executeGroupByQuery(query, filtered, resolvedFarmer, resolutionStatus)
        }

        // 9. Plain record ordering & limit
        if (query.orderBy is QueryOrder.ByField) {
            filtered = applyRecordSorting(filtered, query.orderBy)
        }

        val limitedRecords = if (query.limit != null && query.limit > 0) {
            filtered.take(query.limit)
        } else {
            filtered
        }

        // 10. Multiple Aggregations on ungrouped records
        if (query.aggregations.isNotEmpty()) {
            return executeMultipleAggregations(query, limitedRecords, resolvedFarmer, resolutionStatus)
        }

        // 11. Conditional Reporting
        if (query.conditionalSpec != null) {
            return executeConditionalReport(query, limitedRecords, resolvedFarmer, resolutionStatus)
        }

        // 12. Plain factual synthesis
        return synthesizePlainResult(query, limitedRecords, resolvedFarmer, resolutionStatus)
    }

    // ── Group By + Having + Order By Aggregate ───────────────────────

    private fun executeGroupByQuery(
        query: FreedomQuery,
        records: List<MilkRecordEntity>,
        resolvedFarmer: FarmerEntity?,
        resolutionStatus: EntityResolutionStatus
    ): ExecutionResult {
        if (records.isEmpty()) {
            return ExecutionResult(
                summary = "No records found to group.",
                records = emptyList(),
                entityResolutionStatus = resolutionStatus,
                resolvedFarmer = resolvedFarmer,
                resolvedFarmerName = resolvedFarmer?.farmerName
            )
        }

        val groupField = query.groupBy.first()
        val groups = records.groupBy { record ->
            getFieldValue(record, groupField)?.toString()?.trim()?.lowercase() ?: "unknown"
        }

        val primaryAgg = query.aggregations.firstOrNull() ?: AggregationSpec(AggregationType.COUNT_RECORDS)

        // Compute aggregated value for each group
        data class GroupSummary(
            val groupKey: String,
            val displayName: String,
            val groupRecords: List<MilkRecordEntity>,
            val aggregateValue: Double
        )

        val groupSummaries = groups.map { (key, groupRecs) ->
            val displayName = groupRecs.first().let { rec ->
                when (groupField) {
                    SchemaField.FARMER_NAME -> rec.farmerName
                    SchemaField.PAYMENT_STATUS -> rec.paymentStatus
                    SchemaField.PAYMENT_METHOD -> rec.paymentMethod ?: "Unknown"
                    else -> key
                }
            }
            val aggVal = when (primaryAgg.type) {
                AggregationType.SUM -> groupRecs.sumOf { getNumericFieldValue(it, primaryAgg.field ?: SchemaField.QUANTITY) }
                AggregationType.COUNT_RECORDS -> groupRecs.size.toDouble()
                AggregationType.COUNT_DISTINCT_FARMERS -> groupRecs.mapNotNull { it.farmerId?.takeIf { id -> id.isNotBlank() } }.distinct().size.toDouble()
                AggregationType.AVG -> if (groupRecs.isNotEmpty()) groupRecs.sumOf { getNumericFieldValue(it, primaryAgg.field ?: SchemaField.QUANTITY) } / groupRecs.size else 0.0
                AggregationType.MIN -> if (groupRecs.isNotEmpty()) groupRecs.minOf { getNumericFieldValue(it, primaryAgg.field ?: SchemaField.QUANTITY) } else 0.0
                AggregationType.MAX -> if (groupRecs.isNotEmpty()) groupRecs.maxOf { getNumericFieldValue(it, primaryAgg.field ?: SchemaField.QUANTITY) } else 0.0
            }
            GroupSummary(key, displayName, groupRecs, aggVal)
        }.toMutableList()

        // Apply HAVING filter if present
        if (query.havingFilter != null) {
            val hf = query.havingFilter
            groupSummaries.retainAll { group ->
                val groupVal = when (hf.aggregation) {
                    AggregationType.SUM -> group.groupRecords.sumOf { getNumericFieldValue(it, hf.field) }
                    AggregationType.COUNT_RECORDS -> group.groupRecords.size.toDouble()
                    AggregationType.COUNT_DISTINCT_FARMERS -> group.groupRecords.mapNotNull { it.farmerId?.takeIf { id -> id.isNotBlank() } }.distinct().size.toDouble()
                    AggregationType.AVG -> if (group.groupRecords.isNotEmpty()) group.groupRecords.sumOf { getNumericFieldValue(it, hf.field) } / group.groupRecords.size else 0.0
                    AggregationType.MIN -> if (group.groupRecords.isNotEmpty()) group.groupRecords.minOf { getNumericFieldValue(it, hf.field) } else 0.0
                    AggregationType.MAX -> if (group.groupRecords.isNotEmpty()) group.groupRecords.maxOf { getNumericFieldValue(it, hf.field) } else 0.0
                }
                when (hf.operator) {
                    FilterOperator.GREATER_THAN -> groupVal > hf.value
                    FilterOperator.GREATER_OR_EQUAL -> groupVal >= hf.value
                    FilterOperator.LESS_THAN -> groupVal < hf.value
                    FilterOperator.LESS_OR_EQUAL -> groupVal <= hf.value
                    FilterOperator.EQUALS -> groupVal == hf.value
                    else -> true
                }
            }
        }

        // Apply Ordering (by explicit aggregate metric or field)
        when (val order = query.orderBy) {
            is QueryOrder.ByAggregate -> {
                val sortField = order.field ?: primaryAgg.field ?: SchemaField.QUANTITY
                val selector: (GroupSummary) -> Double = { g ->
                    when (order.aggregationType) {
                        AggregationType.SUM -> g.groupRecords.sumOf { getNumericFieldValue(it, sortField) }
                        AggregationType.COUNT_RECORDS -> g.groupRecords.size.toDouble()
                        AggregationType.COUNT_DISTINCT_FARMERS -> g.groupRecords.mapNotNull { it.farmerId?.takeIf { id -> id.isNotBlank() } }.distinct().size.toDouble()
                        AggregationType.AVG -> if (g.groupRecords.isNotEmpty()) g.groupRecords.sumOf { getNumericFieldValue(it, sortField) } / g.groupRecords.size else 0.0
                        AggregationType.MIN -> if (g.groupRecords.isNotEmpty()) g.groupRecords.minOf { getNumericFieldValue(it, sortField) } else 0.0
                        AggregationType.MAX -> if (g.groupRecords.isNotEmpty()) g.groupRecords.maxOf { getNumericFieldValue(it, sortField) } else 0.0
                    }
                }
                if (order.direction == SortDirection.ASC) {
                    groupSummaries.sortBy { selector(it) }
                } else {
                    groupSummaries.sortByDescending { selector(it) }
                }
            }
            is QueryOrder.ByField -> {
                if (order.direction == SortDirection.ASC) {
                    groupSummaries.sortBy { it.displayName }
                } else {
                    groupSummaries.sortByDescending { it.displayName }
                }
            }
            null -> {
                groupSummaries.sortByDescending { it.aggregateValue }
            }
        }

        // Apply Limit
        val finalGroups = if (query.limit != null && query.limit > 0) {
            groupSummaries.take(query.limit)
        } else {
            groupSummaries
        }

        // Build Response
        val sb = StringBuilder()
        if (query.limit == 1 && finalGroups.isNotEmpty()) {
            // E.g. "Who gave the most milk this week?"
            val top = finalGroups.first()
            val fieldLabel = primaryAgg.field?.let { fieldUnit(it) } ?: "records"
            sb.append("${top.displayName} had the highest ${primaryAgg.field?.name?.lowercase() ?: "collection"}: ${"%.1f".format(top.aggregateValue)} $fieldLabel (across ${top.groupRecords.size} record(s)).")
        } else {
            for (g in finalGroups) {
                val unit = primaryAgg.field?.let { fieldUnit(it) } ?: "record(s)"
                sb.appendLine("${g.displayName}: ${"%.1f".format(g.aggregateValue)} $unit (${g.groupRecords.size} record(s))")
            }
        }

        return ExecutionResult(
            summary = sb.toString().trim(),
            records = finalGroups.flatMap { it.groupRecords },
            entityResolutionStatus = resolutionStatus,
            resolvedFarmer = resolvedFarmer,
            resolvedFarmerName = resolvedFarmer?.farmerName,
            groupedResults = finalGroups.associate { it.displayName to it.groupRecords }
        )
    }

    // ── Multiple Aggregations ─────────────────────────────────────────

    private fun executeMultipleAggregations(
        query: FreedomQuery,
        records: List<MilkRecordEntity>,
        resolvedFarmer: FarmerEntity?,
        resolutionStatus: EntityResolutionStatus
    ): ExecutionResult {
        if (records.isEmpty()) {
            val label = resolvedFarmer?.farmerName ?: "matching records"
            return ExecutionResult(
                summary = "No collection records found for $label.",
                records = emptyList(),
                entityResolutionStatus = resolutionStatus,
                resolvedFarmer = resolvedFarmer,
                resolvedFarmerName = resolvedFarmer?.farmerName
            )
        }

        val label = resolvedFarmer?.farmerName ?: "Total"
        val parts = mutableListOf<String>()

        for (agg in query.aggregations) {
            when (agg.type) {
                AggregationType.SUM -> {
                    val field = agg.field ?: SchemaField.QUANTITY
                    val sumVal = records.sumOf { getNumericFieldValue(it, field) }
                    parts.add("Total ${fieldLabel(field)}: ${"%.1f".format(sumVal)} ${fieldUnit(field)}")
                }
                AggregationType.COUNT_RECORDS -> {
                    parts.add("${records.size} collection record(s)")
                }
                AggregationType.COUNT_DISTINCT_FARMERS -> {
                    val distinctCount = records.mapNotNull { it.farmerId?.takeIf { id -> id.isNotBlank() } }.distinct().size
                    parts.add("$distinctCount farmer(s)")
                }
                AggregationType.AVG -> {
                    val field = agg.field ?: SchemaField.FAT
                    val avgVal = records.sumOf { getNumericFieldValue(it, field) } / records.size
                    parts.add("Average ${fieldLabel(field)}: ${"%.2f".format(avgVal)}${fieldSuffix(field)}")
                }
                AggregationType.MIN -> {
                    val field = agg.field ?: SchemaField.QUANTITY
                    val minVal = records.minOf { getNumericFieldValue(it, field) }
                    parts.add("Minimum ${fieldLabel(field)}: ${"%.1f".format(minVal)} ${fieldUnit(field)}")
                }
                AggregationType.MAX -> {
                    val field = agg.field ?: SchemaField.QUANTITY
                    val maxVal = records.maxOf { getNumericFieldValue(it, field) }
                    parts.add("Maximum ${fieldLabel(field)}: ${"%.1f".format(maxVal)} ${fieldUnit(field)}")
                }
            }
        }

        val summary = "$label: ${parts.joinToString(" • ")} (across ${records.size} record(s))."
        return ExecutionResult(
            summary = summary,
            records = records,
            entityResolutionStatus = resolutionStatus,
            resolvedFarmer = resolvedFarmer,
            resolvedFarmerName = resolvedFarmer?.farmerName
        )
    }

    // ── Conditional Reporting ─────────────────────────────────────────

    private fun executeConditionalReport(
        query: FreedomQuery,
        records: List<MilkRecordEntity>,
        resolvedFarmer: FarmerEntity?,
        resolutionStatus: EntityResolutionStatus
    ): ExecutionResult {
        val label = resolvedFarmer?.farmerName ?: "Farmer"
        if (records.isEmpty()) {
            return ExecutionResult(
                summary = "No collection records found for $label.",
                records = emptyList(),
                entityResolutionStatus = resolutionStatus,
                resolvedFarmer = resolvedFarmer,
                resolvedFarmerName = resolvedFarmer?.farmerName
            )
        }

        val spec = query.conditionalSpec!!
        val latest = records.maxByOrNull { it.createdAt }!!
        val actualValue = getFieldValue(latest, spec.conditionField)?.toString() ?: ""

        val isMatch = actualValue.equals(spec.expectedValue, ignoreCase = true) ||
                (spec.expectedValue == "PAID" && actualValue in listOf("PAID", "RECORDED_LOCALLY"))

        val summary = if (isMatch) {
            val method = latest.paymentMethod ?: "CASH"
            val ref = latest.paymentReference ?: "N/A"
            val amount = latest.payableAmount ?: MilkRecordEntity.calculatePayableAmount(latest.quantity, latest.fat, latest.snf)
            "Yes. $label was paid ₹${"%.2f".format(amount)} via $method. Reference: $ref."
        } else {
            val amount = latest.payableAmount ?: MilkRecordEntity.calculatePayableAmount(latest.quantity, latest.fat, latest.snf)
            "No. $label's payment of ₹${"%.2f".format(amount)} for ${"%.1f".format(latest.quantity)} L is still pending."
        }

        return ExecutionResult(
            summary = summary,
            records = records,
            entityResolutionStatus = resolutionStatus,
            resolvedFarmer = resolvedFarmer,
            resolvedFarmerName = resolvedFarmer?.farmerName
        )
    }

    // ── Plain Factual Synthesis ───────────────────────────────────────

    private fun synthesizePlainResult(
        query: FreedomQuery,
        records: List<MilkRecordEntity>,
        resolvedFarmer: FarmerEntity?,
        resolutionStatus: EntityResolutionStatus
    ): ExecutionResult {
        val label = resolvedFarmer?.farmerName

        val summary = when {
            // Evaluated payment timestamp queries (e.g. "When was Suresh's payment recorded?")
            label != null && query.select.contains(SchemaField.PAYMENT_TIMESTAMP) -> {
                if (records.isEmpty()) {
                    "$label has no payment records in this period."
                } else {
                    val latestWithTs = records.filter { it.paymentTimestamp != null }.maxByOrNull { it.paymentTimestamp!! }
                    if (latestWithTs?.paymentTimestamp != null) {
                        val sdf = SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.US).apply {
                            timeZone = TimeZone.getDefault()
                        }
                        val formatted = sdf.format(Date(latestWithTs.paymentTimestamp))
                        "$label's payment was recorded on $formatted."
                    } else {
                        "No payment timestamp recorded for $label (payment is still pending)."
                    }
                }
            }
            // Evaluated amount paid queries (e.g. "How much was Suresh paid?")
            label != null && query.select.contains(SchemaField.AMOUNT_PAID) -> {
                if (records.isEmpty()) {
                    "$label has no payment records in this period."
                } else {
                    when (query.paymentScope) {
                        PaymentScope.LATEST -> {
                            val latest = records.maxByOrNull { it.createdAt }!!
                            val isPaid = latest.paymentStatus in listOf(
                                MilkRecordEntity.PAYMENT_RECORDED_LOCALLY,
                                MilkRecordEntity.PAYMENT_PAID
                            )
                            val amount = latest.amountPaid ?: latest.payableAmount ?: MilkRecordEntity.calculatePayableAmount(latest.quantity, latest.fat, latest.snf)
                            if (isPaid) {
                                "$label was paid ₹${"%.2f".format(amount)} on the latest collection record."
                            } else {
                                "$label has not been paid for the latest collection record (₹${"%.2f".format(amount)} pending)."
                            }
                        }
                        PaymentScope.ALL, PaymentScope.ANY -> {
                            val paidRecs = records.filter {
                                it.paymentStatus in listOf(
                                    MilkRecordEntity.PAYMENT_RECORDED_LOCALLY,
                                    MilkRecordEntity.PAYMENT_PAID
                                )
                            }
                            val totalPaid = paidRecs.sumOf {
                                it.amountPaid ?: it.payableAmount ?: MilkRecordEntity.calculatePayableAmount(it.quantity, it.fat, it.snf)
                            }
                            val totalPending = records.filter { it.paymentStatus == MilkRecordEntity.PAYMENT_PENDING }.sumOf {
                                it.payableAmount ?: MilkRecordEntity.calculatePayableAmount(it.quantity, it.fat, it.snf)
                            }
                            if (paidRecs.isEmpty()) {
                                "$label has not received any payment across ${records.size} collection record(s) (₹${"%.2f".format(totalPending)} pending)."
                            } else {
                                "$label was paid ₹${"%.2f".format(totalPaid)} across ${paidRecs.size} of ${records.size} collection record(s) (₹${"%.2f".format(totalPending)} pending)."
                            }
                        }
                    }
                }
            }
            // Evaluated payment scope queries
            label != null && query.select.contains(SchemaField.PAYMENT_STATUS) -> {
                if (records.isEmpty()) {
                    "$label has no payment records in this period."
                } else {
                    when (query.paymentScope) {
                        PaymentScope.ALL -> {
                            val allPaid = records.all {
                                it.paymentStatus == MilkRecordEntity.PAYMENT_RECORDED_LOCALLY ||
                                it.paymentStatus == MilkRecordEntity.PAYMENT_PAID
                            }
                            if (allPaid) {
                                "Yes. All ${records.size} payment(s) for $label are completed."
                            } else {
                                val pendingCount = records.count { it.paymentStatus == MilkRecordEntity.PAYMENT_PENDING }
                                "No. $pendingCount of ${records.size} payment(s) for $label are still pending."
                            }
                        }
                        PaymentScope.ANY -> {
                            val anyPaid = records.any {
                                it.paymentStatus == MilkRecordEntity.PAYMENT_RECORDED_LOCALLY ||
                                it.paymentStatus == MilkRecordEntity.PAYMENT_PAID
                            }
                            if (anyPaid) {
                                "Yes. $label has received payment on matching collection records."
                            } else {
                                "No. $label has not received any payment in this period."
                            }
                        }
                        PaymentScope.LATEST -> {
                            val latest = records.maxByOrNull { it.createdAt }!!
                            val isPaid = latest.paymentStatus == MilkRecordEntity.PAYMENT_RECORDED_LOCALLY ||
                                    latest.paymentStatus == MilkRecordEntity.PAYMENT_PAID
                            if (isPaid) {
                                val method = latest.paymentMethod ?: "CASH"
                                val ref = latest.paymentReference ?: "N/A"
                                val amount = latest.payableAmount ?: MilkRecordEntity.calculatePayableAmount(latest.quantity, latest.fat, latest.snf)
                                "Yes. $label was paid ₹${"%.2f".format(amount)} via $method. Reference: $ref."
                            } else {
                                val amount = latest.payableAmount ?: MilkRecordEntity.calculatePayableAmount(latest.quantity, latest.fat, latest.snf)
                                "No. $label's payment of ₹${"%.2f".format(amount)} for ${"%.1f".format(latest.quantity)} L is still pending."
                            }
                        }
                    }
                }
            }
            label != null -> {
                if (records.isEmpty()) {
                    "$label has no collection records in this period."
                } else {
                    val totalQty = records.sumOf { it.quantity }
                    val latest = records.maxByOrNull { it.createdAt }!!
                    val isPaid = latest.paymentStatus == MilkRecordEntity.PAYMENT_RECORDED_LOCALLY ||
                            latest.paymentStatus == MilkRecordEntity.PAYMENT_PAID
                    val payText = if (isPaid) "Paid via ${latest.paymentMethod ?: "CASH"}" else "Payment pending"
                    "$label: ${records.size} delivery(ies) totaling ${"%.1f".format(totalQty)} L. Status: $payText."
                }
            }
            records.isNotEmpty() -> {
                val totalQty = records.sumOf { it.quantity }
                val distinctFarmers = records.mapNotNull { it.farmerId?.takeIf { id -> id.isNotBlank() } }.distinct().size
                val farmerText = if (distinctFarmers > 0) " from $distinctFarmers authoritative farmer(s)" else ""
                "Retrieved ${records.size} record(s)$farmerText totaling ${"%.1f".format(totalQty)} L."
            }
            else -> "No matching records found in local database."
        }

        return ExecutionResult(
            summary = summary,
            records = records,
            entityResolutionStatus = resolutionStatus,
            resolvedFarmer = resolvedFarmer,
            resolvedFarmerName = label
        )
    }

    // ── Helper Filtering & Mapping ────────────────────────────────────

    private fun matchesFilter(record: MilkRecordEntity, filter: QueryFilter): Boolean {
        val fieldValue = getFieldValue(record, filter.field)

        return when (filter.operator) {
            FilterOperator.IS_NULL -> fieldValue == null
            FilterOperator.IS_NOT_NULL -> fieldValue != null
            FilterOperator.EQUALS -> {
                if (fieldValue == null) return false
                fieldValue.toString().equals(filter.value ?: "", ignoreCase = true)
            }
            FilterOperator.NOT_EQUALS -> {
                if (fieldValue == null) return filter.value != null
                !fieldValue.toString().equals(filter.value ?: "", ignoreCase = true)
            }
            FilterOperator.CONTAINS -> {
                if (fieldValue == null) return false
                fieldValue.toString().contains(filter.value ?: "", ignoreCase = true)
            }
            FilterOperator.GREATER_THAN -> compareNumeric(fieldValue, filter.value) { a, b -> a > b }
            FilterOperator.GREATER_OR_EQUAL -> compareNumeric(fieldValue, filter.value) { a, b -> a >= b }
            FilterOperator.LESS_THAN -> compareNumeric(fieldValue, filter.value) { a, b -> a < b }
            FilterOperator.LESS_OR_EQUAL -> compareNumeric(fieldValue, filter.value) { a, b -> a <= b }
            FilterOperator.IN_RANGE -> {
                if (fieldValue == null) return false
                val numVal = toNumeric(fieldValue) ?: return false
                val start = filter.value?.toDoubleOrNull() ?: return false
                val end = filter.secondaryValue?.toDoubleOrNull() ?: return false
                numVal in start..end
            }
        }
    }

    private fun getFieldValue(record: MilkRecordEntity, field: SchemaField): Any? {
        return when (field) {
            SchemaField.FARMER_NAME -> record.farmerName
            SchemaField.FARMER_ID -> record.farmerId
            SchemaField.QUANTITY -> record.quantity
            SchemaField.FAT -> record.fat
            SchemaField.SNF -> record.snf
            SchemaField.PAYMENT_STATUS -> record.paymentStatus
            SchemaField.PAYMENT_METHOD -> record.paymentMethod
            SchemaField.PAYMENT_REFERENCE -> record.paymentReference
            SchemaField.PAYMENT_TIMESTAMP -> record.paymentTimestamp
            SchemaField.PAYABLE_AMOUNT -> record.payableAmount
            SchemaField.AMOUNT_PAID -> record.amountPaid
            SchemaField.CREATED_AT -> record.createdAt
            SchemaField.UPDATED_AT -> record.updatedAt
            SchemaField.UPLOAD_STATUS -> record.uploadStatus
        }
    }

    private fun compareNumeric(
        fieldValue: Any?,
        filterValue: String?,
        comparator: (Double, Double) -> Boolean
    ): Boolean {
        if (fieldValue == null || filterValue == null) return false
        val numField = toNumeric(fieldValue) ?: return false
        val numFilter = filterValue.toDoubleOrNull() ?: return false
        return comparator(numField, numFilter)
    }

    private fun toNumeric(value: Any?): Double? {
        return when (value) {
            is Double -> value
            is Long -> value.toDouble()
            is Int -> value.toDouble()
            is Float -> value.toDouble()
            is String -> value.toDoubleOrNull()
            else -> null
        }
    }

    private fun applyRecordSorting(
        records: List<MilkRecordEntity>,
        sort: QueryOrder.ByField
    ): List<MilkRecordEntity> {
        val selector: (MilkRecordEntity) -> Comparable<*>? = { record ->
            when (sort.field) {
                SchemaField.QUANTITY -> record.quantity
                SchemaField.FAT -> record.fat
                SchemaField.SNF -> record.snf
                SchemaField.PAYABLE_AMOUNT -> record.payableAmount ?: 0.0
                SchemaField.AMOUNT_PAID -> record.amountPaid ?: 0.0
                SchemaField.PAYMENT_TIMESTAMP -> record.paymentTimestamp ?: 0L
                SchemaField.CREATED_AT -> record.createdAt
                SchemaField.UPDATED_AT -> record.updatedAt
                SchemaField.FARMER_NAME -> record.farmerName.lowercase()
                SchemaField.PAYMENT_STATUS -> record.paymentStatus
                SchemaField.PAYMENT_METHOD -> record.paymentMethod ?: ""
                SchemaField.UPLOAD_STATUS -> record.uploadStatus
                else -> record.createdAt
            }
        }

        @Suppress("UNCHECKED_CAST")
        return if (sort.direction == SortDirection.ASC) {
            records.sortedBy { selector(it) as? Comparable<Any> }
        } else {
            records.sortedByDescending { selector(it) as? Comparable<Any> }
        }
    }

    private fun getNumericFieldValue(record: MilkRecordEntity, field: SchemaField): Double {
        return when (field) {
            SchemaField.QUANTITY -> record.quantity
            SchemaField.FAT -> record.fat
            SchemaField.SNF -> record.snf
            SchemaField.PAYABLE_AMOUNT -> record.payableAmount ?: 0.0
            SchemaField.AMOUNT_PAID -> record.amountPaid ?: 0.0
            else -> 0.0
        }
    }

    private fun fieldUnit(field: SchemaField): String = when (field) {
        SchemaField.QUANTITY -> "L"
        SchemaField.FAT, SchemaField.SNF -> "%"
        SchemaField.PAYABLE_AMOUNT, SchemaField.AMOUNT_PAID -> "₹"
        else -> ""
    }

    private fun fieldLabel(field: SchemaField): String = when (field) {
        SchemaField.QUANTITY -> "quantity"
        SchemaField.FAT -> "fat"
        SchemaField.SNF -> "SNF"
        SchemaField.PAYABLE_AMOUNT -> "payable amount"
        SchemaField.AMOUNT_PAID -> "amount paid"
        else -> field.name.lowercase()
    }

    private fun fieldSuffix(field: SchemaField): String = when (field) {
        SchemaField.FAT, SchemaField.SNF -> "%"
        else -> ""
    }
}
