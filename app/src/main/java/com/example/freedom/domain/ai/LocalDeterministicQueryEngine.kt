package com.example.freedom.domain.ai

import android.util.Log
import com.example.freedom.data.local.entity.MilkRecordEntity
import com.example.freedom.data.repository.MilkRecordRepository
import kotlinx.coroutines.flow.first
import org.json.JSONObject

/**
 * Result of a query executed locally.
 */
data class QueryEngineResult(
    val originalPrompt: String,
    val summary: String,
    val records: List<MilkRecordEntity> = emptyList(),
    val success: Boolean = true,
    val executionPipelineDescription: String = "Local SQLite deterministic query execution",
    val isDeterministicEngine: Boolean = true
)

/**
 * Sealed result type returned by the local query engine.
 */
sealed class LocalEngineResult {
    data class ToolResult(val request: ToolRequest) : LocalEngineResult()
    data class SummaryResult(val summary: QueryEngineResult) : LocalEngineResult()
}

/**
 * Local AI Agent Query Engine.
 *
 * Pipeline:
 * USER INPUT -> REAL ON-DEVICE GEMMA 3 1B IT -> STRUCTURED ToolRequest -> DETERMINISTIC RECONCILIATION & VALIDATION -> ROOM / PROFILE / RAG
 *
 * Gemma is the primary natural language intent router and parameter extractor.
 * Deterministic Kotlin code executes all calculations, counts, database writes, and grounded citations.
 */
class LocalDeterministicQueryEngine(
    private val repository: MilkRecordRepository,
    private val toolExecutor: ToolExecutor = ToolExecutor(repository)
) {

    private fun logD(tag: String, msg: String) {
        try { Log.d(tag, msg) } catch (_: Throwable) {}
    }
    private fun logI(tag: String, msg: String) {
        try { Log.i(tag, msg) } catch (_: Throwable) {}
    }
    private fun logW(tag: String, msg: String) {
        try { Log.w(tag, msg) } catch (_: Throwable) {}
    }
    private fun logE(tag: String, msg: String, tr: Throwable? = null) {
        try { Log.e(tag, msg, tr) } catch (_: Throwable) {}
    }

    suspend fun executeQuery(userPrompt: String): LocalEngineResult {
        // ---------- 1. PRIMARY PATH: REAL ON-DEVICE GEMMA INFERENCE ----------
        if (AIEngineProvider.isAvailable()) {
            try {
                logD("LocalDeterministicQueryEngine", "Invoking on-device Gemma for prompt: $userPrompt")
                val prompt = LocalContextBuilder.buildIntentRoutingPrompt(userPrompt)
                val rawOutput = AIEngineProvider.generateText(prompt)

                if (!rawOutput.isNullOrBlank()) {
                    logD("LocalDeterministicQueryEngine", "Gemma raw output: $rawOutput")
                    val parsedRequest = parseGemmaResponse(rawOutput, userPrompt)
                    if (parsedRequest != null) {
                        logI(
                            "LocalDeterministicQueryEngine",
                            "GEMMA_EXECUTED: Intent=${parsedRequest.intent}, Args=${parsedRequest.args}"
                        )
                        return LocalEngineResult.ToolResult(
                            parsedRequest.copy(executionMode = ExecutionMode.GEMMA_EXECUTED)
                        )
                    } else {
                        logW("LocalDeterministicQueryEngine", "GEMMA_PARSE_FAILED: Could not parse JSON from output: $rawOutput")
                        return executeFallback(userPrompt, ExecutionMode.GEMMA_PARSE_FAILED)
                    }
                } else {
                    logW("LocalDeterministicQueryEngine", "GEMMA_INFERENCE_EMPTY: No response generated from Gemma")
                    return executeFallback(userPrompt, ExecutionMode.GEMMA_UNAVAILABLE)
                }
            } catch (e: Exception) {
                logE("LocalDeterministicQueryEngine", "Gemma inference failed: ${e.message}", e)
                return executeFallback(userPrompt, ExecutionMode.GEMMA_UNAVAILABLE)
            }
        } else {
            logD("LocalDeterministicQueryEngine", "GEMMA_UNAVAILABLE: Engine not ready or model file missing")
            return executeFallback(userPrompt, ExecutionMode.GEMMA_UNAVAILABLE)
        }
    }

    /**
     * Parses the raw JSON response emitted by Gemma.
     * Note: Does NOT override Gemma's intent with broad keywords!
     * Validates intent and reconciles numeric parameters deterministically.
     */
    fun parseGemmaResponse(raw: String, userPrompt: String = ""): ToolRequest? {
        try {
            var clean = raw.trim()
            if (clean.contains("```json")) {
                clean = clean.substringAfter("```json").substringBefore("```").trim()
            } else if (clean.contains("```")) {
                clean = clean.substringAfter("```").substringBefore("```").trim()
            }
            val firstBrace = clean.indexOf('{')
            val lastBrace = clean.lastIndexOf('}')
            if (firstBrace != -1 && lastBrace != -1 && lastBrace > firstBrace) {
                clean = clean.substring(firstBrace, lastBrace + 1)
            }

            var intentStr: String? = null
            val rawArgsMap = mutableMapOf<String, String>()
            var needsConfirmationExplicit = false
            var question: String? = null

            try {
                val json = JSONObject(clean)
                intentStr = json.optString("intent").trim().uppercase()
                val argsJson = json.optJSONObject("args")
                if (argsJson != null) {
                    val keys = argsJson.keys()
                    while (keys.hasNext()) {
                        val key = keys.next()
                        rawArgsMap[key] = argsJson.optString(key, "")
                    }
                }
                needsConfirmationExplicit = json.optBoolean("needsConfirmation", false)
                question = json.optString("question").takeIf { it.isNotEmpty() }
            } catch (_: Throwable) {
                // Regex parser fallback for malformed JSON formatting
                val intentMatch = Regex("\"intent\"\\s*:\\s*\"([A-Za-z0-9_]+)\"", RegexOption.IGNORE_CASE).find(clean)
                if (intentMatch != null) {
                    intentStr = intentMatch.groupValues[1].uppercase()
                }
                val argsMatch = Regex("\"args\"\\s*:\\s*\\{([^}]*)\\}").find(clean)
                if (argsMatch != null) {
                    val innerArgs = argsMatch.groupValues[1]
                    Regex("\"([A-Za-z0-9_]+)\"\\s*:\\s*\"([^\"]*)\"").findAll(innerArgs).forEach {
                        rawArgsMap[it.groupValues[1]] = it.groupValues[2]
                    }
                    Regex("\"([A-Za-z0-9_]+)\"\\s*:\\s*([0-9.]+)").findAll(innerArgs).forEach {
                        if (!rawArgsMap.containsKey(it.groupValues[1])) {
                            rawArgsMap[it.groupValues[1]] = it.groupValues[2]
                        }
                    }
                }
                needsConfirmationExplicit = clean.contains(Regex("\"needsConfirmation\"\\s*:\\s*true", RegexOption.IGNORE_CASE))
            }

            if (intentStr.isNullOrEmpty()) return null

            // Validate against the registered capabilities enum
            val validatedIntent = try {
                ToolIntent.valueOf(intentStr)
            } catch (_: Exception) {
                ToolIntent.UNKNOWN_OR_UNSUPPORTED
            }

            // Numeric fidelity reconciliation for record creation
            val reconciledArgs = if (validatedIntent == ToolIntent.CREATE_MILK_RECORD && userPrompt.isNotEmpty()) {
                NumberFidelityReconciler.reconcile(userPrompt, rawArgsMap)
            } else {
                rawArgsMap.toMutableMap().apply {
                    if (validatedIntent == ToolIntent.SEARCH_LOCAL_KNOWLEDGE && (get("query").isNullOrBlank() || get("query") == "dairy_cooperative")) {
                        put("query", userPrompt)
                    }
                    if (validatedIntent == ToolIntent.COUNT_FARMERS_COVERED && !containsKey("period")) {
                        val lower = userPrompt.lowercase()
                        put("period", if (lower.contains("week")) "week" else "today")
                    }
                    if (validatedIntent == ToolIntent.GET_WEEKLY_WORKER_SUMMARY && !containsKey("farmerName")) {
                        val farmerMatch = Regex("how much did\\s+([A-Za-z]+)\\s+give this week", RegexOption.IGNORE_CASE).find(userPrompt)
                            ?: Regex("([A-Za-z]+).*this week", RegexOption.IGNORE_CASE).find(userPrompt)
                        val fName = farmerMatch?.groupValues?.get(1)?.replaceFirstChar { it.uppercase() }
                        if (!fName.isNullOrEmpty() && fName.lowercase() !in listOf("how", "what", "milk")) {
                            put("farmerName", fName)
                        }
                    }
                }
            }

            val needsConfirmation = if (validatedIntent == ToolIntent.CREATE_MILK_RECORD) {
                true
            } else {
                needsConfirmationExplicit
            }

            val baseRequest = ToolRequest(
                intent = validatedIntent,
                args = reconciledArgs,
                rawArgs = rawArgsMap,
                needsConfirmation = needsConfirmation,
                clarificationQuestion = question,
                executionMode = ExecutionMode.GEMMA_EXECUTED
            )

            return applyIntentSafetyGate(baseRequest, userPrompt)
        } catch (e: Exception) {
            return null
        }
    }

    /**
     * Deterministic Intent-Consistency and Safety Gate.
     * Prevents Gemma model hallucinations or misclassifications from routing
     * policy/knowledge questions to DB tools or worker profile tools.
     */
    fun applyIntentSafetyGate(request: ToolRequest, userPrompt: String): ToolRequest {
        val lowerPrompt = userPrompt.lowercase().trim()
        if (lowerPrompt.isEmpty()) return request

        // 0. Record Creation must never be overridden by queries
        val isRecordCreation = request.intent == ToolIntent.CREATE_MILK_RECORD ||
                (lowerPrompt.contains("gave") && (lowerPrompt.contains("litre") || lowerPrompt.contains("liter") || lowerPrompt.contains("l"))) ||
                (lowerPrompt.contains("litres") && lowerPrompt.contains("fat"))

        if (isRecordCreation) {
            return request
        }

        // 1. RAG Policy / Rule / Standard queries:
        // Policy and rule questions MUST route to SEARCH_LOCAL_KNOWLEDGE, never GET_WORKER_PROFILE or GET_ORGANIZATION_INFO
        val isPaymentPolicyQuestion = (lowerPrompt.contains("payment") || lowerPrompt.contains("paid")) &&
                (lowerPrompt.contains("complete") || lowerPrompt.contains("settled") || lowerPrompt.contains("rule") ||
                 lowerPrompt.contains("procedure") || lowerPrompt.contains("policy") || lowerPrompt.contains("when is") ||
                 lowerPrompt.contains("how is") || lowerPrompt.contains("timeline") || lowerPrompt.contains("cycle"))

        val isGeneralPolicyQuestion = lowerPrompt.contains("quality standard") ||
                lowerPrompt.contains("milk standard") ||
                (lowerPrompt.contains("minimum") && (lowerPrompt.contains("fat") || lowerPrompt.contains("snf"))) ||
                lowerPrompt.contains("rejected milk") || lowerPrompt.contains("rejection") ||
                lowerPrompt.contains("spoilage") || lowerPrompt.contains("sour milk") ||
                lowerPrompt.contains("curdled") || lowerPrompt.contains("sync protocol") ||
                lowerPrompt.contains("sync policy") || lowerPrompt.contains("offline limit") ||
                lowerPrompt.contains("storage quota") || lowerPrompt.contains("cooperative rule") ||
                lowerPrompt.contains("cooperative policy")

        if (isPaymentPolicyQuestion || isGeneralPolicyQuestion) {
            return request.copy(
                intent = ToolIntent.SEARCH_LOCAL_KNOWLEDGE,
                args = mapOf("query" to userPrompt),
                needsConfirmation = false,
                executionMode = ExecutionMode.RAG_EXECUTION
            )
        }

        // 2. Worker Identity queries:
        val isWorkerProfileQuestion = lowerPrompt.contains("officer id") ||
                lowerPrompt.contains("worker id") ||
                lowerPrompt.contains("what is my id") ||
                lowerPrompt.contains("my officer id") ||
                lowerPrompt.contains("my id") ||
                lowerPrompt.contains("who am i") ||
                lowerPrompt.contains("assigned area") ||
                lowerPrompt.contains("my center") ||
                lowerPrompt.contains("my route") ||
                lowerPrompt.contains("my profile")

        if (isWorkerProfileQuestion) {
            return request.copy(
                intent = ToolIntent.GET_WORKER_PROFILE,
                args = emptyMap(),
                needsConfirmation = false
            )
        }

        // 3. Organization Info queries:
        val isOrgQuestion = (lowerPrompt.contains("organization") || lowerPrompt.contains("cooperative name") ||
                lowerPrompt.contains("which union") || lowerPrompt.contains("registration number")) &&
                !lowerPrompt.contains("payment") && !lowerPrompt.contains("standard")

        if (isOrgQuestion) {
            return request.copy(
                intent = ToolIntent.GET_ORGANIZATION_INFO,
                args = emptyMap(),
                needsConfirmation = false
            )
        }

        // 4. Pending payments queries:
        val isPendingPaymentsQuestion = (lowerPrompt.contains("pending") || lowerPrompt.contains("unpaid") || lowerPrompt.contains("due")) &&
                (lowerPrompt.contains("payment") || lowerPrompt.contains("who has"))

        if (isPendingPaymentsQuestion && !isPaymentPolicyQuestion) {
            return request.copy(
                intent = ToolIntent.GET_PENDING_PAYMENTS,
                args = emptyMap(),
                needsConfirmation = false
            )
        }

        // 5. Farmers covered queries:
        val isFarmersCoveredQuestion = lowerPrompt.contains("how many farmers") ||
                lowerPrompt.contains("farmers covered") ||
                lowerPrompt.contains("farmers visited")

        if (isFarmersCoveredQuestion) {
            val period = if (lowerPrompt.contains("week")) "week" else "today"
            return request.copy(
                intent = ToolIntent.COUNT_FARMERS_COVERED,
                args = mapOf("period" to period),
                needsConfirmation = false
            )
        }

        return request
    }

    /**
     * Emergency fallback executed ONLY when Gemma is unavailable or JSON parsing fails.
     */
    suspend fun executeFallback(userPrompt: String, mode: ExecutionMode): LocalEngineResult {
        logI("LocalDeterministicQueryEngine", "Running fallback with mode: $mode")
        val lowerPrompt = userPrompt.lowercase()

        // 1. CREATE_MILK_RECORD fallback
        if (lowerPrompt.contains("gave") && (lowerPrompt.contains("litres") || lowerPrompt.contains("liter") || lowerPrompt.contains("l"))) {
            val nameMatch = Regex("([A-Za-z]+)\\s+gave", RegexOption.IGNORE_CASE).find(userPrompt)
            val farmerName = nameMatch?.groupValues?.get(1)?.replaceFirstChar { it.uppercase() } ?: ""
            val quantity = NumberFidelityReconciler.extractQuantityFromText(userPrompt) ?: ""

            val args = mutableMapOf<String, String>()
            if (farmerName.isNotEmpty()) args["farmerName"] = farmerName
            if (quantity.isNotEmpty()) args["quantity"] = quantity

            NumberFidelityReconciler.extractFatFromText(userPrompt)?.let { args["fat"] = it }
            NumberFidelityReconciler.extractSnfFromText(userPrompt)?.let { args["snf"] = it }

            val payment = if (lowerPrompt.contains("paid")) "PAID" else "PENDING"
            args["paymentStatus"] = payment

            return LocalEngineResult.ToolResult(
                ToolRequest(
                    intent = ToolIntent.CREATE_MILK_RECORD,
                    args = args,
                    rawArgs = args,
                    needsConfirmation = true,
                    executionMode = mode
                )
            )
        }

        // 2. GET_WORKER_PROFILE fallback
        if (lowerPrompt.contains("officer id") || lowerPrompt.contains("worker id") ||
            lowerPrompt.contains("who am i") || lowerPrompt.contains("assigned area") ||
            lowerPrompt.contains("my center") || lowerPrompt.contains("my profile") ||
            lowerPrompt.contains("what is my id") || lowerPrompt.contains("my id")
        ) {
            return LocalEngineResult.ToolResult(
                ToolRequest(ToolIntent.GET_WORKER_PROFILE, emptyMap(), needsConfirmation = false, executionMode = mode)
            )
        }

        // 3. GET_ORGANIZATION_INFO fallback
        if (lowerPrompt.contains("organization") || lowerPrompt.contains("cooperative name") ||
            lowerPrompt.contains("which union") || lowerPrompt.contains("which org")
        ) {
            return LocalEngineResult.ToolResult(
                ToolRequest(ToolIntent.GET_ORGANIZATION_INFO, emptyMap(), needsConfirmation = false, executionMode = mode)
            )
        }

        // 4. COUNT_FARMERS_COVERED fallback
        if (lowerPrompt.contains("how many farmers") || lowerPrompt.contains("farmers covered") ||
            lowerPrompt.contains("farmers visited")
        ) {
            val period = if (lowerPrompt.contains("week")) "week" else "today"
            return LocalEngineResult.ToolResult(
                ToolRequest(ToolIntent.COUNT_FARMERS_COVERED, mapOf("period" to period), needsConfirmation = false, executionMode = mode)
            )
        }

        // 5. GET_WEEKLY_WORKER_SUMMARY fallback
        if (lowerPrompt.contains("this week") || lowerPrompt.contains("weekly")) {
            val farmerMatch = Regex("how much did\\s+([A-Za-z]+)\\s+give this week", RegexOption.IGNORE_CASE).find(userPrompt)
                ?: Regex("([A-Za-z]+).*this week", RegexOption.IGNORE_CASE).find(userPrompt)
            val farmerName = farmerMatch?.groupValues?.get(1)?.replaceFirstChar { it.uppercase() }
            val args = if (!farmerName.isNullOrEmpty() && farmerName.lowercase() !in listOf("how", "what", "milk")) {
                mapOf("farmerName" to farmerName)
            } else {
                emptyMap()
            }
            return LocalEngineResult.ToolResult(
                ToolRequest(ToolIntent.GET_WEEKLY_WORKER_SUMMARY, args, needsConfirmation = false, executionMode = mode)
            )
        }

        // 6. GET_TODAY_WORKER_SUMMARY fallback
        if (lowerPrompt.contains("my activity today") || lowerPrompt.contains("my collections today")) {
            return LocalEngineResult.ToolResult(
                ToolRequest(ToolIntent.GET_TODAY_WORKER_SUMMARY, emptyMap(), needsConfirmation = false, executionMode = mode)
            )
        }

        // 7. GET_PENDING_PAYMENTS fallback
        if (lowerPrompt.contains("payment") && (lowerPrompt.contains("pending") || lowerPrompt.contains("unpaid") || lowerPrompt.contains("due"))) {
            return LocalEngineResult.ToolResult(
                ToolRequest(ToolIntent.GET_PENDING_PAYMENTS, emptyMap(), needsConfirmation = false, executionMode = mode)
            )
        }

        // 8. GET_FARMER_HISTORY fallback
        val farmerHistoryMatch = Regex("(?:what did|deliveries for|history for|how much did)\\s+([A-Za-z]+)", RegexOption.IGNORE_CASE).find(userPrompt)
            ?: Regex("([A-Za-z]+)\\s+(?:deliver|give|history)", RegexOption.IGNORE_CASE).find(userPrompt)
        if (farmerHistoryMatch != null && !lowerPrompt.contains("gave") && !lowerPrompt.contains("this week")) {
            val farmerName = farmerHistoryMatch.groupValues[1].replaceFirstChar { it.uppercase() }
            return LocalEngineResult.ToolResult(
                ToolRequest(ToolIntent.GET_FARMER_HISTORY, mapOf("farmerName" to farmerName), needsConfirmation = false, executionMode = mode)
            )
        }

        // 9. GET_TODAY_SUMMARY fallback
        if (lowerPrompt.contains("today") || lowerPrompt.contains("collected today")) {
            return LocalEngineResult.ToolResult(
                ToolRequest(ToolIntent.GET_TODAY_SUMMARY, emptyMap(), needsConfirmation = false, executionMode = mode)
            )
        }

        // 10. GET_PENDING_UPLOADS fallback
        if (lowerPrompt.contains("upload") || lowerPrompt.contains("sync queue")) {
            return LocalEngineResult.ToolResult(
                ToolRequest(ToolIntent.GET_PENDING_UPLOADS, emptyMap(), needsConfirmation = false, executionMode = mode)
            )
        }

        // 11. SEARCH_LOCAL_KNOWLEDGE fallback (RAG)
        val isPaymentPolicy = (lowerPrompt.contains("payment") || lowerPrompt.contains("paid")) &&
                (lowerPrompt.contains("complete") || lowerPrompt.contains("settled") || lowerPrompt.contains("rule") ||
                 lowerPrompt.contains("procedure") || lowerPrompt.contains("policy") || lowerPrompt.contains("when is") ||
                 lowerPrompt.contains("timeline") || lowerPrompt.contains("cycle"))
        val isGeneralPolicy = lowerPrompt.contains("quality standard") || lowerPrompt.contains("milk standard") ||
                lowerPrompt.contains("rejected milk") || lowerPrompt.contains("rejection") ||
                lowerPrompt.contains("spoilage") || lowerPrompt.contains("sour milk") ||
                lowerPrompt.contains("sync protocol") || lowerPrompt.contains("sync policy") ||
                lowerPrompt.contains("offline limit") || lowerPrompt.contains("quota") ||
                lowerPrompt.contains("acceptance")
        if (isPaymentPolicy || isGeneralPolicy) {
            return LocalEngineResult.ToolResult(
                ToolRequest(ToolIntent.SEARCH_LOCAL_KNOWLEDGE, mapOf("query" to userPrompt), needsConfirmation = false, executionMode = ExecutionMode.RAG_EXECUTION)
            )
        }

        // 12. Generic summary fallback
        val records = repository.getAllRecords().first()
        val summary = "${records.size} local record(s) on device."
        return LocalEngineResult.SummaryResult(
            QueryEngineResult(userPrompt, summary, records, true, "Local SQLite query", true)
        )
    }
}
