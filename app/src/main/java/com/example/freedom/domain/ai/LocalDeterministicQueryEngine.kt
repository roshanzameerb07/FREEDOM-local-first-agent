package com.example.freedom.domain.ai

import android.util.Log
import com.example.freedom.data.local.entity.MilkRecordEntity
import com.example.freedom.data.repository.MilkRecordRepository
import com.google.ai.edge.litertlm.Content
import com.google.ai.edge.litertlm.Message
import com.google.ai.edge.litertlm.MessageCallback
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import org.json.JSONObject

/**
 * Result of a query executed locally.
 */
data class QueryEngineResult(
    val originalPrompt: String,
    val summary: String,
    val records: List<MilkRecordEntity> = emptyList(),
    val success: Boolean = true,
    val executionPipelineDescription: String = "Local SQLite deterministic query execution (Offline)",
    val isDeterministicEngine: Boolean = true
)

/**
 * Sealed result type returned by the local engine.
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
        val engine = AIEngineProvider.engine
        if (engine != null && engine.isInitialized()) {
            try {
                logD("LocalDeterministicQueryEngine", "Invoking on-device Gemma for prompt: $userPrompt")
                val conversation = engine.createConversation()
                try {
                    val prompt = LocalContextBuilder.buildIntentRoutingPrompt(userPrompt)
                    val rawOutput = suspendCancellableCoroutine<String> { cont ->
                        val sb = StringBuilder()
                        conversation.sendMessageAsync(prompt, object : MessageCallback {
                            override fun onMessage(message: Message) {
                                for (content in message.contents.contents) {
                                    if (content is Content.Text) {
                                        sb.append(content.text)
                                    }
                                }
                            }

                            override fun onDone() {
                                if (cont.isActive) {
                                    cont.resume(sb.toString().trim())
                                }
                            }

                            override fun onError(throwable: Throwable) {
                                if (cont.isActive) {
                                    cont.resumeWithException(throwable)
                                }
                            }
                        })
                    }
                    logD("LocalDeterministicQueryEngine", "Gemma raw output: $rawOutput")
                    val parsedRequest = parseGemmaResponse(rawOutput, userPrompt)
                    if (parsedRequest != null) {
                        logI("LocalDeterministicQueryEngine", "Gemma extracted intent: ${parsedRequest.intent} with args: ${parsedRequest.args}")
                        return LocalEngineResult.ToolResult(parsedRequest)
                    } else {
                        logW("LocalDeterministicQueryEngine", "Could not parse JSON from Gemma output: $rawOutput")
                    }
                } finally {
                    try {
                        conversation.close()
                    } catch (_: Exception) {}
                }
            } catch (e: Exception) {
                logE("LocalDeterministicQueryEngine", "Gemma inference failed: ${e.message}", e)
            }
        } else {
            logD("LocalDeterministicQueryEngine", "Gemma engine not ready or not initialized, using deterministic fallback")
        }

        // ---------- 2. FALLBACK PATH: DETERMINISTIC PATTERN MATCHING ----------
        return executeFallback(userPrompt)
    }

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
                // Regex parser fallback for host tests or malformed JSON
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

            val intent = try {
                ToolIntent.valueOf(intentStr)
            } catch (_: Exception) {
                ToolIntent.UNKNOWN_OR_UNSUPPORTED
            }

            var resolvedIntent = intent
            val lower = userPrompt.lowercase()
            if (lower.contains("officer id") || lower.contains("worker id") || lower.contains("assigned area") || lower.contains("my center")) {
                resolvedIntent = ToolIntent.GET_WORKER_PROFILE
            } else if (lower.contains("how many farmers") || lower.contains("farmers covered") || lower.contains("farmers visited")) {
                resolvedIntent = ToolIntent.COUNT_FARMERS_COVERED
            } else if (lower.contains("this week") || lower.contains("weekly")) {
                resolvedIntent = ToolIntent.GET_WEEKLY_WORKER_SUMMARY
            } else if ((intent == ToolIntent.GET_ORGANIZATION_INFO || intent == ToolIntent.UNKNOWN_OR_UNSUPPORTED) &&
                (lower.contains("payment") && (lower.contains("complete") || lower.contains("condition") || lower.contains("rule")) ||
                 lower.contains("quality standard") || lower.contains("snf minimum") || lower.contains("rejected milk") || lower.contains("spoilage") || lower.contains("sync protocol"))
            ) {
                resolvedIntent = ToolIntent.SEARCH_LOCAL_KNOWLEDGE
            }

            // Numeric fidelity reconciliation
            val reconciledArgs = if (resolvedIntent == ToolIntent.CREATE_MILK_RECORD && userPrompt.isNotEmpty()) {
                NumberFidelityReconciler.reconcile(userPrompt, rawArgsMap)
            } else {
                rawArgsMap.toMutableMap().apply {
                    if (resolvedIntent == ToolIntent.SEARCH_LOCAL_KNOWLEDGE && (get("query").isNullOrBlank() || get("query") == "dairy_cooperative")) {
                        put("query", userPrompt)
                    }
                    if (resolvedIntent == ToolIntent.COUNT_FARMERS_COVERED && !containsKey("period")) {
                        put("period", if (lower.contains("week")) "week" else "today")
                    }
                    if (resolvedIntent == ToolIntent.GET_WEEKLY_WORKER_SUMMARY && !containsKey("farmerName")) {
                        val farmerMatch = Regex("how much did\\s+([A-Za-z]+)\\s+give this week", RegexOption.IGNORE_CASE).find(userPrompt)
                            ?: Regex("([A-Za-z]+).*this week", RegexOption.IGNORE_CASE).find(userPrompt)
                        val fName = farmerMatch?.groupValues?.get(1)?.replaceFirstChar { it.uppercase() }
                        if (!fName.isNullOrEmpty() && fName.lowercase() !in listOf("how", "what", "milk")) {
                            put("farmerName", fName)
                        }
                    }
                }
            }

            val needsConfirmation = if (resolvedIntent == ToolIntent.CREATE_MILK_RECORD) {
                true
            } else {
                needsConfirmationExplicit
            }

            return ToolRequest(
                intent = resolvedIntent,
                args = reconciledArgs,
                rawArgs = rawArgsMap,
                needsConfirmation = needsConfirmation,
                clarificationQuestion = question
            )
        } catch (e: Exception) {
            return null
        }
    }

    suspend fun executeFallback(userPrompt: String): LocalEngineResult {
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
                    needsConfirmation = true
                )
            )
        }

        // 2. GET_WORKER_PROFILE fallback
        if (lowerPrompt.contains("officer id") || lowerPrompt.contains("worker id") ||
            lowerPrompt.contains("who am i") || lowerPrompt.contains("assigned area") ||
            lowerPrompt.contains("my center") || lowerPrompt.contains("my profile")
        ) {
            return LocalEngineResult.ToolResult(
                ToolRequest(ToolIntent.GET_WORKER_PROFILE, emptyMap(), needsConfirmation = false)
            )
        }

        // 3. GET_ORGANIZATION_INFO fallback
        if (lowerPrompt.contains("organization") || lowerPrompt.contains("cooperative name") ||
            lowerPrompt.contains("which union") || lowerPrompt.contains("which org")
        ) {
            return LocalEngineResult.ToolResult(
                ToolRequest(ToolIntent.GET_ORGANIZATION_INFO, emptyMap(), needsConfirmation = false)
            )
        }

        // 4. COUNT_FARMERS_COVERED fallback
        if (lowerPrompt.contains("how many farmers") || lowerPrompt.contains("farmers covered") ||
            lowerPrompt.contains("farmers visited")
        ) {
            val period = if (lowerPrompt.contains("week")) "week" else "today"
            return LocalEngineResult.ToolResult(
                ToolRequest(ToolIntent.COUNT_FARMERS_COVERED, mapOf("period" to period), needsConfirmation = false)
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
                ToolRequest(ToolIntent.GET_WEEKLY_WORKER_SUMMARY, args, needsConfirmation = false)
            )
        }

        // 6. GET_TODAY_WORKER_SUMMARY fallback
        if (lowerPrompt.contains("my activity today") || lowerPrompt.contains("my collections today")) {
            return LocalEngineResult.ToolResult(
                ToolRequest(ToolIntent.GET_TODAY_WORKER_SUMMARY, emptyMap(), needsConfirmation = false)
            )
        }

        // 7. GET_PENDING_PAYMENTS fallback
        if (lowerPrompt.contains("payment") && (lowerPrompt.contains("pending") || lowerPrompt.contains("unpaid") || lowerPrompt.contains("due"))) {
            return LocalEngineResult.ToolResult(
                ToolRequest(ToolIntent.GET_PENDING_PAYMENTS, emptyMap(), needsConfirmation = false)
            )
        }

        // 8. GET_FARMER_HISTORY fallback
        val farmerHistoryMatch = Regex("(?:what did|deliveries for|history for|how much did)\\s+([A-Za-z]+)", RegexOption.IGNORE_CASE).find(userPrompt)
            ?: Regex("([A-Za-z]+)\\s+(?:deliver|give|history)", RegexOption.IGNORE_CASE).find(userPrompt)
        if (farmerHistoryMatch != null && !lowerPrompt.contains("gave") && !lowerPrompt.contains("this week")) {
            val farmerName = farmerHistoryMatch.groupValues[1].replaceFirstChar { it.uppercase() }
            return LocalEngineResult.ToolResult(
                ToolRequest(ToolIntent.GET_FARMER_HISTORY, mapOf("farmerName" to farmerName), needsConfirmation = false)
            )
        }

        // 9. GET_TODAY_SUMMARY fallback
        if (lowerPrompt.contains("today") || lowerPrompt.contains("collected today")) {
            return LocalEngineResult.ToolResult(
                ToolRequest(ToolIntent.GET_TODAY_SUMMARY, emptyMap(), needsConfirmation = false)
            )
        }

        // 10. GET_PENDING_UPLOADS fallback
        if (lowerPrompt.contains("upload") || lowerPrompt.contains("sync queue")) {
            return LocalEngineResult.ToolResult(
                ToolRequest(ToolIntent.GET_PENDING_UPLOADS, emptyMap(), needsConfirmation = false)
            )
        }

        // 11. SEARCH_LOCAL_KNOWLEDGE fallback (RAG)
        if (lowerPrompt.contains("payment") && (lowerPrompt.contains("complete") || lowerPrompt.contains("settled") || lowerPrompt.contains("rule")) ||
            lowerPrompt.contains("quality standard") || lowerPrompt.contains("acceptance") ||
            lowerPrompt.contains("rejected milk") || lowerPrompt.contains("spoilage") ||
            lowerPrompt.contains("sync protocol") || lowerPrompt.contains("quota")
        ) {
            return LocalEngineResult.ToolResult(
                ToolRequest(ToolIntent.SEARCH_LOCAL_KNOWLEDGE, mapOf("query" to userPrompt), needsConfirmation = false)
            )
        }

        // 12. Generic summary fallback
        val records = repository.getAllRecords().first()
        val summary = "Deterministic Engine: Processed query offline. ${records.size} total local record(s) on device."
        return LocalEngineResult.SummaryResult(
            QueryEngineResult(userPrompt, summary, records, true, "Local SQLite deterministic query execution (Offline)")
        )
    }
}
