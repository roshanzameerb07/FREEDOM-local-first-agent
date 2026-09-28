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
 * USER INPUT -> REAL ON-DEVICE GEMMA -> STRUCTURED ToolRequest -> DETERMINISTIC VALIDATION -> ROOM / ACTION
 *
 * Gemma is the primary intent and argument extraction engine.
 * Deterministic pattern matching serves only as a fallback when Gemma is unavailable or output parsing fails.
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
                    val prompt = buildExtractionPrompt(userPrompt)
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
                    val parsedRequest = parseGemmaResponse(rawOutput)
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

    private fun buildExtractionPrompt(userInput: String): String {
        return """
You are FREEDOM AI agent for a rural dairy cooperative.
Analyze the user request and map it to exactly one intent with arguments.

Allowed intents:
1. CREATE_MILK_RECORD (args: farmerName, quantity, fat, snf, paymentStatus). Always set needsConfirmation to true.
2. SEARCH_FARMERS (args: query)
3. GET_FARMER_HISTORY (args: farmerName)
4. GET_TODAY_SUMMARY (args: none)
5. GET_PENDING_PAYMENTS (args: none)
6. GET_PENDING_UPLOADS (args: none)

Rules:
- Output ONLY valid JSON, nothing else. No commentary, no explanation.
- JSON structure:
{
  "intent": "<INTENT_NAME>",
  "args": {
    "<key>": "<value>"
  },
  "needsConfirmation": true
}
- For record creation ("gave", "litres", "fat", "snf"), intent is CREATE_MILK_RECORD and needsConfirmation must be true.
- If data is incomplete (e.g. "Ramesh gave 80 litres" without fat or snf), intent is CREATE_MILK_RECORD, args contain available fields, and needsConfirmation must be true.
- For queries about pending payments/unpaid farmers, intent is GET_PENDING_PAYMENTS.
- For queries about what a farmer gave/delivered/history, intent is GET_FARMER_HISTORY with farmerName.
- For queries about today's summary/records collected today, intent is GET_TODAY_SUMMARY.

User request: $userInput
""".trimIndent()
    }

    fun parseGemmaResponse(raw: String): ToolRequest? {
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

            try {
                val json = JSONObject(clean)
                val intentStr = json.optString("intent").trim().uppercase()
                if (intentStr.isNotEmpty()) {
                    val intent = ToolIntent.valueOf(intentStr)
                    val argsMap = mutableMapOf<String, String>()
                    val argsJson = json.optJSONObject("args")
                    if (argsJson != null) {
                        val keys = argsJson.keys()
                        while (keys.hasNext()) {
                            val key = keys.next()
                            argsMap[key] = argsJson.optString(key, "")
                        }
                    }
                    val needsConfirmation = if (intent == ToolIntent.CREATE_MILK_RECORD) {
                        true
                    } else {
                        json.optBoolean("needsConfirmation", false)
                    }
                    return ToolRequest(intent, argsMap, needsConfirmation)
                }
            } catch (_: Throwable) {
                // Fallback parser if JSONObject fails on unmocked host JVM
            }

            // Resilient Regex fallback parser
            val intentMatch = Regex("\"intent\"\\s*:\\s*\"([A-Za-z0-9_]+)\"", RegexOption.IGNORE_CASE).find(clean)
                ?: return null
            val intent = ToolIntent.valueOf(intentMatch.groupValues[1].uppercase())
            val argsMap = mutableMapOf<String, String>()
            val argsMatch = Regex("\"args\"\\s*:\\s*\\{([^}]*)\\}").find(clean)
            if (argsMatch != null) {
                val innerArgs = argsMatch.groupValues[1]
                Regex("\"([A-Za-z0-9_]+)\"\\s*:\\s*\"([^\"]*)\"").findAll(innerArgs).forEach {
                    argsMap[it.groupValues[1]] = it.groupValues[2]
                }
            }
            val needsConfirmation = if (intent == ToolIntent.CREATE_MILK_RECORD) {
                true
            } else {
                clean.contains(Regex("\"needsConfirmation\"\\s*:\\s*true", RegexOption.IGNORE_CASE))
            }
            return ToolRequest(intent, argsMap, needsConfirmation)
        } catch (e: Exception) {
            return null
        }
    }

    private suspend fun executeFallback(userPrompt: String): LocalEngineResult {
        val lowerPrompt = userPrompt.lowercase()

        // 1. CREATE_MILK_RECORD fallback
        if (lowerPrompt.contains("gave") && (lowerPrompt.contains("litres") || lowerPrompt.contains("liter") || lowerPrompt.contains("l"))) {
            val nameMatch = Regex("([A-Za-z]+)\\s+gave", RegexOption.IGNORE_CASE).find(userPrompt)
            val farmerName = nameMatch?.groupValues?.get(1)?.replaceFirstChar { it.uppercase() } ?: ""
            val qtyMatch = Regex("(\\d+(?:\\.\\d+)?)\\s*(?:litres?|liters?|ltr|l\\b)", RegexOption.IGNORE_CASE).find(userPrompt)
            val quantity = qtyMatch?.groupValues?.get(1) ?: ""

            val args = mutableMapOf<String, String>("farmerName" to farmerName, "quantity" to quantity)
            Regex("fat\\s*[:=]?\\s*(\\d+(?:\\.\\d+)?)", RegexOption.IGNORE_CASE).find(userPrompt)?.let { args["fat"] = it.groupValues[1] }
            Regex("snf\\s*[:=]?\\s*(\\d+(?:\\.\\d+)?)", RegexOption.IGNORE_CASE).find(userPrompt)?.let { args["snf"] = it.groupValues[1] }

            val payment = if (lowerPrompt.contains("paid")) "PAID" else "PENDING"
            args["paymentStatus"] = payment

            return LocalEngineResult.ToolResult(
                ToolRequest(ToolIntent.CREATE_MILK_RECORD, args, needsConfirmation = true)
            )
        }

        // 2. GET_PENDING_PAYMENTS fallback
        if (lowerPrompt.contains("payment") && (lowerPrompt.contains("pending") || lowerPrompt.contains("unpaid") || lowerPrompt.contains("due"))) {
            return LocalEngineResult.ToolResult(
                ToolRequest(ToolIntent.GET_PENDING_PAYMENTS, emptyMap(), needsConfirmation = false)
            )
        }

        // 3. GET_FARMER_HISTORY fallback
        val farmerHistoryMatch = Regex("(?:what did|deliveries for|history for|how much did)\\s+([A-Za-z]+)", RegexOption.IGNORE_CASE).find(userPrompt)
            ?: Regex("([A-Za-z]+)\\s+(?:deliver|give|history)", RegexOption.IGNORE_CASE).find(userPrompt)
        if (farmerHistoryMatch != null && !lowerPrompt.contains("gave")) {
            val farmerName = farmerHistoryMatch.groupValues[1].replaceFirstChar { it.uppercase() }
            return LocalEngineResult.ToolResult(
                ToolRequest(ToolIntent.GET_FARMER_HISTORY, mapOf("farmerName" to farmerName), needsConfirmation = false)
            )
        }

        // 4. GET_TODAY_SUMMARY fallback
        if (lowerPrompt.contains("today") || lowerPrompt.contains("collected today")) {
            return LocalEngineResult.ToolResult(
                ToolRequest(ToolIntent.GET_TODAY_SUMMARY, emptyMap(), needsConfirmation = false)
            )
        }

        // 5. GET_PENDING_UPLOADS fallback
        if (lowerPrompt.contains("upload") || lowerPrompt.contains("sync")) {
            return LocalEngineResult.ToolResult(
                ToolRequest(ToolIntent.GET_PENDING_UPLOADS, emptyMap(), needsConfirmation = false)
            )
        }

        // 6. Generic summary fallback
        val records = repository.getAllRecords().first()
        val summary = "Deterministic Engine: Processed query offline. ${records.size} total local record(s) on device."
        return LocalEngineResult.SummaryResult(
            QueryEngineResult(userPrompt, summary, records, true, "Local SQLite deterministic query execution (Offline)")
        )
    }
}
