package com.example.freedom.domain.ai

import com.example.freedom.data.local.entity.MilkRecordEntity
import com.example.freedom.data.repository.MilkRecordRepository
import com.example.freedom.domain.model.WorkerProfileRepository
import com.example.freedom.domain.rag.KnowledgeDocument
import com.example.freedom.domain.rag.LocalRagRetriever
import com.example.freedom.domain.validation.MilkRecordValidator
import kotlinx.coroutines.flow.first

/**
 * Executes a validated [ToolRequest] against local sources of truth:
 * 1. Room SQLite Database (operational transactions, aggregations, queries)
 * 2. Worker & Organization Profile (deterministic configuration)
 * 3. Local RAG Retriever (organization SOPs, quality policies, regulations)
 *
 * Deterministic Kotlin code is the sole source of truth for all calculations,
 * counts, database writes, and grounded citations. Gemma never modifies SQLite directly
 * and never computes totals.
 */
class ToolExecutor(
    private val repository: MilkRecordRepository,
    private val validator: MilkRecordValidator = MilkRecordValidator(),
    private val ragRetriever: LocalRagRetriever = LocalRagRetriever()
) {

    data class ExecutionResult(
        val summary: String,
        val capabilityUsed: String,
        val sourceOfTruth: String,
        val records: List<MilkRecordEntity> = emptyList(),
        val extractedParams: Map<String, String> = emptyMap(),
        val ragDocument: KnowledgeDocument? = null,
        val calculationDetails: String? = null,
        val success: Boolean = true,
        val isClarificationNeeded: Boolean = false,
        val isUnsupported: Boolean = false
    )

    suspend fun execute(request: ToolRequest): ExecutionResult {
        return when (request.intent) {
            ToolIntent.CREATE_MILK_RECORD -> {
                val farmer = request.args["farmerName"]?.trim() ?: ""
                val quantityStr = request.args["quantity"]?.trim() ?: ""
                val fatStr = request.args["fat"]?.trim() ?: ""
                val snfStr = request.args["snf"]?.trim() ?: ""
                val payment = request.args["paymentStatus"]?.trim()?.uppercase() ?: MilkRecordEntity.PAYMENT_PENDING

                val validation = validator.validate(
                    farmerName = farmer,
                    quantityStr = quantityStr,
                    fatStr = fatStr,
                    snfStr = snfStr,
                    paymentStatus = payment
                )

                if (!validation.isValid) {
                    val errors = listOfNotNull(
                        validation.farmerNameError,
                        validation.quantityError,
                        validation.fatError,
                        validation.snfError,
                        validation.paymentStatusError
                    ).joinToString("; ")
                    return ExecutionResult(
                        summary = "Validation Failed: $errors. Database write aborted.",
                        capabilityUsed = "CREATE_MILK_RECORD",
                        sourceOfTruth = "Deterministic Validation Check (Failed)",
                        records = emptyList(),
                        extractedParams = request.args,
                        success = false
                    )
                }

                val quantity = quantityStr.toDouble()
                val fat = fatStr.toDouble()
                val snf = snfStr.toDouble()
                val paymentStatus = if (payment == "PAID") MilkRecordEntity.PAYMENT_PAID else MilkRecordEntity.PAYMENT_PENDING

                val entity = MilkRecordEntity(
                    farmerName = farmer,
                    quantity = quantity,
                    fat = fat,
                    snf = snf,
                    paymentStatus = paymentStatus,
                    uploadStatus = MilkRecordEntity.UPLOAD_STATUS_PENDING
                )
                repository.insertRecord(entity)

                ExecutionResult(
                    summary = "Successfully saved milk record for $farmer: ${"%.1f".format(quantity)} L, Fat ${"%.1f".format(fat)}%, SNF ${"%.1f".format(snf)}%, Payment: $paymentStatus (Stored Locally).",
                    capabilityUsed = "CREATE_MILK_RECORD",
                    sourceOfTruth = "Local Room SQLite (Offline Insert)",
                    records = listOf(entity),
                    extractedParams = request.args,
                    success = true
                )
            }

            ToolIntent.SEARCH_FARMERS -> {
                val query = request.args["query"] ?: ""
                val records = repository.searchAndFilterRecords(query = query, paymentStatus = null, uploadStatus = null).first()
                ExecutionResult(
                    summary = "Found ${records.size} local record(s) matching '$query'.",
                    capabilityUsed = "SEARCH_FARMERS",
                    sourceOfTruth = "Local Room SQLite",
                    records = records,
                    extractedParams = request.args,
                    success = true
                )
            }

            ToolIntent.GET_FARMER_HISTORY -> {
                val farmer = request.args["farmerName"]?.trim() ?: ""
                val records = if (farmer.isNotEmpty()) repository.getRecordsByFarmerName(farmer) else repository.getAllRecords().first()
                val totalQty = records.sumOf { it.quantity }
                val summary = if (records.isNotEmpty()) {
                    "Found ${records.size} record(s) for $farmer totaling ${"%.1f".format(totalQty)} L."
                } else {
                    "No local delivery records found for farmer '$farmer'."
                }
                ExecutionResult(
                    summary = summary,
                    capabilityUsed = "GET_FARMER_HISTORY",
                    sourceOfTruth = "Local Room SQLite",
                    records = records,
                    extractedParams = request.args,
                    calculationDetails = "Total: ${"%.1f".format(totalQty)} L across ${records.size} deliveries",
                    success = true
                )
            }

            ToolIntent.GET_TODAY_SUMMARY -> {
                val count = repository.getTodayRecordsCount().first()
                val litres = repository.getTodayTotalQuantity().first()
                val allToday = repository.getAllRecords().first().take(count)
                ExecutionResult(
                    summary = "Today's summary: $count record(s) collected locally totaling ${"%.1f".format(litres)} L.",
                    capabilityUsed = "GET_TODAY_SUMMARY",
                    sourceOfTruth = "Local Room SQLite",
                    records = allToday,
                    calculationDetails = "Total Volume: ${"%.1f".format(litres)} L; Total Records: $count",
                    success = true
                )
            }

            ToolIntent.GET_PENDING_PAYMENTS -> {
                val records = repository.getPendingPaymentRecords()
                val total = records.sumOf { it.quantity }
                ExecutionResult(
                    summary = "Found ${records.size} pending payment record(s) totaling ${"%.1f".format(total)} L.",
                    capabilityUsed = "GET_PENDING_PAYMENTS",
                    sourceOfTruth = "Local Room SQLite",
                    records = records,
                    calculationDetails = "Pending Total: ${"%.1f".format(total)} L across ${records.size} records",
                    success = true
                )
            }

            ToolIntent.GET_PENDING_UPLOADS -> {
                val records = repository.getPendingRecords()
                ExecutionResult(
                    summary = "Pending uploads: ${records.size} record(s) queued for batch synchronization.",
                    capabilityUsed = "GET_PENDING_UPLOADS",
                    sourceOfTruth = "Local Room SQLite (Pending Queue)",
                    records = records,
                    success = true
                )
            }

            ToolIntent.GET_WORKER_PROFILE -> {
                val profile = WorkerProfileRepository.getProfile()
                ExecutionResult(
                    summary = "Worker Profile: Officer ID ${profile.workerId}, Name: ${profile.workerName}, Role: ${profile.role}, Assigned Area: ${profile.assignedArea}, Center: ${profile.centerName}.",
                    capabilityUsed = "GET_WORKER_PROFILE",
                    sourceOfTruth = "Local Profile Configuration",
                    extractedParams = mapOf(
                        "workerId" to profile.workerId,
                        "workerName" to profile.workerName,
                        "role" to profile.role,
                        "assignedArea" to profile.assignedArea,
                        "centerName" to profile.centerName
                    ),
                    success = true
                )
            }

            ToolIntent.GET_ORGANIZATION_INFO -> {
                val org = WorkerProfileRepository.getOrganizationInfo()
                ExecutionResult(
                    summary = "Organization: ${org.organizationName} (ID: ${org.organizationId}), Registration: ${org.registrationNumber}, Region: ${org.regionalDistrict} (${org.activeCentersCount} active centers).",
                    capabilityUsed = "GET_ORGANIZATION_INFO",
                    sourceOfTruth = "Local Organization Configuration",
                    extractedParams = mapOf(
                        "organizationId" to org.organizationId,
                        "organizationName" to org.organizationName,
                        "registration" to org.registrationNumber
                    ),
                    success = true
                )
            }

            ToolIntent.GET_TODAY_WORKER_SUMMARY -> {
                val count = repository.getTodayRecordsCount().first()
                val litres = repository.getTodayTotalQuantity().first()
                val farmers = repository.getDistinctFarmersCount("today")
                val profile = WorkerProfileRepository.getProfile()
                ExecutionResult(
                    summary = "Today's Activity for Officer ${profile.workerName} (${profile.workerId}): $count collection(s) from $farmers distinct farmer(s), totaling ${"%.1f".format(litres)} L.",
                    capabilityUsed = "GET_TODAY_WORKER_SUMMARY",
                    sourceOfTruth = "Local Room SQLite + Profile",
                    calculationDetails = "Volume: ${"%.1f".format(litres)} L | Distinct Farmers: $farmers | Records: $count",
                    success = true
                )
            }

            ToolIntent.GET_WEEKLY_WORKER_SUMMARY -> {
                val specificFarmer = request.args["farmerName"]?.trim()
                if (!specificFarmer.isNullOrEmpty()) {
                    val litres = repository.getFarmerQuantityThisWeek(specificFarmer)
                    ExecutionResult(
                        summary = "$specificFarmer delivered ${"%.1f".format(litres)} L this week.",
                        capabilityUsed = "GET_WEEKLY_WORKER_SUMMARY",
                        sourceOfTruth = "Local Room SQLite (Weekly Aggregation)",
                        extractedParams = request.args,
                        calculationDetails = "Weekly Volume for $specificFarmer: ${"%.1f".format(litres)} L",
                        success = true
                    )
                } else {
                    val weeklyLitres = repository.getWeeklyTotalQuantity()
                    val weeklyRecords = repository.getWeeklyRecordsCount()
                    val weeklyFarmers = repository.getDistinctFarmersCount("week")
                    ExecutionResult(
                        summary = "Weekly Collection Total: ${"%.1f".format(weeklyLitres)} L across $weeklyRecords record(s) from $weeklyFarmers distinct farmer(s).",
                        capabilityUsed = "GET_WEEKLY_WORKER_SUMMARY",
                        sourceOfTruth = "Local Room SQLite (Weekly Aggregation)",
                        calculationDetails = "Weekly Volume: ${"%.1f".format(weeklyLitres)} L | Distinct Farmers: $weeklyFarmers | Total Records: $weeklyRecords",
                        success = true
                    )
                }
            }

            ToolIntent.COUNT_FARMERS_COVERED -> {
                val period = request.args["period"]?.trim()?.lowercase() ?: "today"
                val count = repository.getDistinctFarmersCount(period)
                val periodDisplay = if (period == "week") "this week" else "today"
                ExecutionResult(
                    summary = "You covered $count distinct farmer(s) $periodDisplay.",
                    capabilityUsed = "COUNT_FARMERS_COVERED",
                    sourceOfTruth = "Local Room SQLite (Distinct Query)",
                    extractedParams = mapOf("period" to period, "count" to count.toString()),
                    calculationDetails = "Distinct Farmer Count ($periodDisplay): $count",
                    success = true
                )
            }

            ToolIntent.SEARCH_LOCAL_KNOWLEDGE -> {
                val query = request.args["query"]?.trim() ?: ""
                val ragResult = ragRetriever.search(query)
                if (ragResult.hasEvidence && ragResult.topDocument != null) {
                    val doc = ragResult.topDocument
                    ExecutionResult(
                        summary = "Grounded Policy (${doc.clauseOrPage}): ${doc.content}",
                        capabilityUsed = "SEARCH_LOCAL_KNOWLEDGE",
                        sourceOfTruth = "Local Document: ${doc.documentTitle} — ${doc.clauseOrPage}",
                        ragDocument = doc,
                        extractedParams = mapOf("query" to query, "confidence" to "%.2f".format(ragResult.confidenceScore)),
                        success = true
                    )
                } else {
                    ExecutionResult(
                        summary = "Information not found in local organization documents. FREEDOM does not hallucinate facts without verified local evidence.",
                        capabilityUsed = "SEARCH_LOCAL_KNOWLEDGE",
                        sourceOfTruth = "Local Knowledge Base (0 Evidence Matches)",
                        extractedParams = mapOf("query" to query),
                        success = false
                    )
                }
            }

            ToolIntent.ASK_CLARIFICATION -> {
                val question = request.clarificationQuestion ?: request.args["question"]
                ?: "Please clarify your request with missing details (e.g., farmer name, litres, fat, or SNF)."
                ExecutionResult(
                    summary = question,
                    capabilityUsed = "ASK_CLARIFICATION",
                    sourceOfTruth = "Agent Clarification Router",
                    isClarificationNeeded = true,
                    success = true
                )
            }

            ToolIntent.UNKNOWN_OR_UNSUPPORTED -> {
                ExecutionResult(
                    summary = "This request is outside the local capabilities of FREEDOM. Supported capabilities: recording milk collections, querying farmer history, pending payments, daily/weekly totals, officer profile, and cooperative policies.",
                    capabilityUsed = "UNKNOWN_OR_UNSUPPORTED",
                    sourceOfTruth = "Agent Capability Boundary",
                    isUnsupported = true,
                    success = false
                )
            }
        }
    }
}
