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
 * 2. Worker & Organization Profile (deterministic local configuration)
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
        val executionMode: ExecutionMode = ExecutionMode.GEMMA_EXECUTED,
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
                val paymentRaw = request.args["paymentStatus"]?.trim()?.uppercase() ?: MilkRecordEntity.PAYMENT_PENDING
                val normalizedPayment = when (paymentRaw) {
                    "PAID", "RECORDED_LOCALLY", "COMPLETE", "SETTLED" -> MilkRecordEntity.PAYMENT_RECORDED_LOCALLY
                    else -> MilkRecordEntity.PAYMENT_PENDING
                }
                val paymentMethod = request.args["paymentMethod"]?.trim()?.uppercase()
                val paymentRef = request.args["paymentReference"]?.trim()

                val validation = validator.validate(
                    farmerName = farmer,
                    quantityStr = quantityStr,
                    fatStr = fatStr,
                    snfStr = snfStr,
                    paymentStatus = normalizedPayment
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
                        summary = "Missing or invalid information: $errors. Please provide the required details to save.",
                        capabilityUsed = "CREATE_MILK_RECORD",
                        sourceOfTruth = "Input Validation Guardrail",
                        records = emptyList(),
                        extractedParams = request.args,
                        executionMode = request.executionMode,
                        success = false
                    )
                }

                val quantity = quantityStr.toDouble()
                val fat = fatStr.toDouble()
                val snf = snfStr.toDouble()
                val paymentStatus = when (paymentRaw) {
                    "PAID", "RECORDED_LOCALLY" -> MilkRecordEntity.PAYMENT_RECORDED_LOCALLY
                    else -> MilkRecordEntity.PAYMENT_PENDING
                }

                val calculatedAmount = MilkRecordEntity.calculatePayableAmount(quantity, fat, snf)
                val now = System.currentTimeMillis()

                val currentProfile = WorkerProfileRepository.getProfile()
                val entity = MilkRecordEntity(
                    orgId = currentProfile.organizationId,
                    workerId = currentProfile.workerId,
                    farmerName = farmer,
                    quantity = quantity,
                    fat = fat,
                    snf = snf,
                    paymentStatus = paymentStatus,
                    paymentMethod = paymentMethod,
                    paymentReference = paymentRef,
                    paymentTimestamp = if (paymentStatus == MilkRecordEntity.PAYMENT_RECORDED_LOCALLY) now else null,
                    payableAmount = calculatedAmount,
                    amountPaid = if (paymentStatus == MilkRecordEntity.PAYMENT_RECORDED_LOCALLY) calculatedAmount else null,
                    createdAt = now,
                    updatedAt = now,
                    uploadStatus = MilkRecordEntity.UPLOAD_STATUS_PENDING
                )
                repository.insertRecord(entity)

                val statusLabel = if (paymentStatus == MilkRecordEntity.PAYMENT_RECORDED_LOCALLY) {
                    "Recorded locally ($paymentMethod)"
                } else {
                    "Pending payment"
                }

                ExecutionResult(
                    summary = "Saved collection for $farmer: ${"%.1f".format(quantity)} L (Fat ${"%.1f".format(fat)}%, SNF ${"%.1f".format(snf)}%) • ₹${"%.2f".format(calculatedAmount)} • $statusLabel",
                    capabilityUsed = "CREATE_MILK_RECORD",
                    sourceOfTruth = "Local Database",
                    records = listOf(entity),
                    extractedParams = request.args,
                    calculationDetails = "${"%.1f".format(quantity)} L × rate = ₹${"%.2f".format(calculatedAmount)}",
                    executionMode = request.executionMode,
                    success = true
                )
            }

            ToolIntent.SEARCH_FARMERS -> {
                val query = request.args["query"] ?: ""
                val records = repository.searchAndFilterRecords(query = query, paymentStatus = null, uploadStatus = null).first()
                ExecutionResult(
                    summary = if (records.isNotEmpty()) "Found ${records.size} record(s) matching '$query'." else "No records found matching '$query'.",
                    capabilityUsed = "SEARCH_FARMERS",
                    sourceOfTruth = "Local Database",
                    records = records,
                    extractedParams = request.args,
                    executionMode = request.executionMode,
                    success = true
                )
            }

            ToolIntent.GET_FARMER_HISTORY -> {
                val farmer = request.args["farmerName"]?.trim() ?: ""
                val records = if (farmer.isNotEmpty()) repository.getRecordsByFarmerName(farmer) else repository.getAllRecords().first()
                val totalQty = records.sumOf { it.quantity }
                val summary = if (records.isNotEmpty()) {
                    "$farmer has ${records.size} delivery record(s) totaling ${"%.1f".format(totalQty)} L."
                } else {
                    "No local delivery records found for farmer '$farmer'."
                }
                ExecutionResult(
                    summary = summary,
                    capabilityUsed = "GET_FARMER_HISTORY",
                    sourceOfTruth = "Local Database",
                    records = records,
                    extractedParams = request.args,
                    calculationDetails = "Total: ${"%.1f".format(totalQty)} L across ${records.size} deliveries",
                    executionMode = request.executionMode,
                    success = true
                )
            }

            ToolIntent.GET_TODAY_SUMMARY -> {
                val count = repository.getTodayRecordsCount().first()
                val litres = repository.getTodayTotalQuantity().first()
                val allToday = repository.getAllRecords().first().take(count)
                ExecutionResult(
                    summary = "Today: $count collection(s) totaling ${"%.1f".format(litres)} L.",
                    capabilityUsed = "GET_TODAY_SUMMARY",
                    sourceOfTruth = "Local Database",
                    records = allToday,
                    calculationDetails = "Volume: ${"%.1f".format(litres)} L across $count entries",
                    executionMode = request.executionMode,
                    success = true
                )
            }

            ToolIntent.GET_PENDING_PAYMENTS -> {
                val records = repository.getPendingPaymentRecords()
                val total = records.sumOf { it.quantity }
                val totalAmount = records.sumOf { it.payableAmount ?: MilkRecordEntity.calculatePayableAmount(it.quantity, it.fat, it.snf) }
                ExecutionResult(
                    summary = if (records.isNotEmpty()) {
                        "${records.size} pending payment(s) totaling ${"%.1f".format(total)} L (~₹${"%.2f".format(totalAmount)})."
                    } else {
                        "No pending payments. All local collections have been paid or settled."
                    },
                    capabilityUsed = "GET_PENDING_PAYMENTS",
                    sourceOfTruth = "Local Database",
                    records = records,
                    calculationDetails = "${records.size} pending collections totaling ${"%.1f".format(total)} L",
                    executionMode = request.executionMode,
                    success = true
                )
            }

            ToolIntent.GET_PENDING_UPLOADS -> {
                val records = repository.getPendingRecords()
                ExecutionResult(
                    summary = "${records.size} record(s) saved on device awaiting sync.",
                    capabilityUsed = "GET_PENDING_UPLOADS",
                    sourceOfTruth = "Device Storage",
                    records = records,
                    executionMode = request.executionMode,
                    success = true
                )
            }

            ToolIntent.GET_WORKER_PROFILE -> {
                val profile = WorkerProfileRepository.getProfile()
                ExecutionResult(
                    summary = "Officer ID: ${profile.workerId}\nName: ${profile.workerName}\nRole: ${profile.role}\nAssigned Area: ${profile.assignedArea}\nCenter: ${profile.centerName}",
                    capabilityUsed = "GET_WORKER_PROFILE",
                    sourceOfTruth = "Officer Profile",
                    extractedParams = mapOf(
                        "workerId" to profile.workerId,
                        "workerName" to profile.workerName,
                        "role" to profile.role,
                        "assignedArea" to profile.assignedArea,
                        "centerName" to profile.centerName
                    ),
                    executionMode = request.executionMode,
                    success = true
                )
            }

            ToolIntent.GET_ORGANIZATION_INFO -> {
                val org = WorkerProfileRepository.getOrganizationInfo()
                ExecutionResult(
                    summary = "${org.organizationName} (ID: ${org.organizationId})\nRegistration: ${org.registrationNumber}\nRegion: ${org.regionalDistrict}",
                    capabilityUsed = "GET_ORGANIZATION_INFO",
                    sourceOfTruth = "Cooperative Profile",
                    extractedParams = mapOf(
                        "organizationId" to org.organizationId,
                        "organizationName" to org.organizationName,
                        "registration" to org.registrationNumber
                    ),
                    executionMode = request.executionMode,
                    success = true
                )
            }

            ToolIntent.GET_TODAY_WORKER_SUMMARY -> {
                val count = repository.getTodayRecordsCount().first()
                val litres = repository.getTodayTotalQuantity().first()
                val farmers = repository.getDistinctFarmersCount("today")
                val profile = WorkerProfileRepository.getProfile()
                ExecutionResult(
                    summary = "${profile.workerName}: $count collection(s) from $farmers farmer(s) today totaling ${"%.1f".format(litres)} L.",
                    capabilityUsed = "GET_TODAY_WORKER_SUMMARY",
                    sourceOfTruth = "Local Database",
                    calculationDetails = "Volume: ${"%.1f".format(litres)} L | Farmers: $farmers | Entries: $count",
                    executionMode = request.executionMode,
                    success = true
                )
            }

            ToolIntent.GET_WEEKLY_WORKER_SUMMARY -> {
                val specificFarmer = request.args["farmerName"]?.trim()
                if (!specificFarmer.isNullOrEmpty()) {
                    val litres = repository.getFarmerQuantityThisWeek(specificFarmer)
                    ExecutionResult(
                        summary = "$specificFarmer gave ${"%.1f".format(litres)} L this week.",
                        capabilityUsed = "GET_WEEKLY_WORKER_SUMMARY",
                        sourceOfTruth = "Local Database",
                        extractedParams = request.args,
                        calculationDetails = "Weekly volume for $specificFarmer: ${"%.1f".format(litres)} L",
                        executionMode = request.executionMode,
                        success = true
                    )
                } else {
                    val weeklyLitres = repository.getWeeklyTotalQuantity()
                    val weeklyRecords = repository.getWeeklyRecordsCount()
                    val weeklyFarmers = repository.getDistinctFarmersCount("week")
                    ExecutionResult(
                        summary = "This week: ${"%.1f".format(weeklyLitres)} L collected across $weeklyRecords record(s) from $weeklyFarmers farmer(s).",
                        capabilityUsed = "GET_WEEKLY_WORKER_SUMMARY",
                        sourceOfTruth = "Local Database",
                        calculationDetails = "${"%.1f".format(weeklyLitres)} L across $weeklyRecords entries",
                        executionMode = request.executionMode,
                        success = true
                    )
                }
            }

            ToolIntent.COUNT_FARMERS_COVERED -> {
                val period = request.args["period"]?.trim()?.lowercase() ?: "today"
                val count = repository.getDistinctFarmersCount(period)
                val periodDisplay = if (period == "week") "this week" else "today"
                ExecutionResult(
                    summary = "You covered $count farmer(s) $periodDisplay.",
                    capabilityUsed = "COUNT_FARMERS_COVERED",
                    sourceOfTruth = "Local Database",
                    extractedParams = mapOf("period" to period, "count" to count.toString()),
                    calculationDetails = "Distinct count ($periodDisplay): $count",
                    executionMode = request.executionMode,
                    success = true
                )
            }

            ToolIntent.SEARCH_LOCAL_KNOWLEDGE -> {
                val query = request.args["query"]?.trim() ?: ""
                val ragResult = ragRetriever.search(query)
                if (ragResult.hasEvidence && ragResult.topDocument != null) {
                    val doc = ragResult.topDocument

                    // Grounded synthesis using Gemma if available
                    var answerText = doc.content
                    if (AIEngineProvider.isAvailable()) {
                        try {
                            val prompt = LocalContextBuilder.buildRagGroundingPrompt(query, doc)
                            val synth = AIEngineProvider.generateText(prompt)
                            if (!synth.isNullOrBlank()) {
                                answerText = synth.trim()
                            }
                        } catch (_: Exception) {}
                    }

                    ExecutionResult(
                        summary = answerText,
                        capabilityUsed = "SEARCH_LOCAL_KNOWLEDGE",
                        sourceOfTruth = "${doc.documentTitle} • ${doc.clauseOrPage}",
                        ragDocument = doc,
                        extractedParams = mapOf(
                            "query" to query,
                            "clause" to doc.clauseOrPage,
                            "section" to doc.sectionTitle,
                            "confidence" to "%.2f".format(ragResult.confidenceScore)
                        ),
                        executionMode = ExecutionMode.RAG_EXECUTION,
                        success = true
                    )
                } else {
                    ExecutionResult(
                        summary = "Information not found in local cooperative documents. FREEDOM does not make up policies without verified document evidence.",
                        capabilityUsed = "SEARCH_LOCAL_KNOWLEDGE",
                        sourceOfTruth = "Cooperative Knowledge Base",
                        extractedParams = mapOf("query" to query),
                        executionMode = ExecutionMode.RAG_EXECUTION,
                        success = false
                    )
                }
            }

            ToolIntent.ASK_CLARIFICATION -> {
                val question = request.clarificationQuestion ?: request.args["question"]
                ?: "Please provide the missing details (e.g. farmer name, litres, fat, or SNF)."
                ExecutionResult(
                    summary = question,
                    capabilityUsed = "ASK_CLARIFICATION",
                    sourceOfTruth = "Assistant",
                    executionMode = request.executionMode,
                    isClarificationNeeded = true,
                    success = true
                )
            }

            ToolIntent.UNKNOWN_OR_UNSUPPORTED -> {
                ExecutionResult(
                    summary = "I can help with recording milk collections, checking farmer deliveries, reviewing pending payments, checking daily totals, or looking up cooperative policies.",
                    capabilityUsed = "UNKNOWN_OR_UNSUPPORTED",
                    sourceOfTruth = "Assistant",
                    executionMode = request.executionMode,
                    isUnsupported = true,
                    success = false
                )
            }
        }
    }
}
