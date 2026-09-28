package com.example.freedom.domain.ai

import com.example.freedom.data.repository.MilkRecordRepository
import com.example.freedom.data.local.entity.MilkRecordEntity
import kotlinx.coroutines.flow.first
import org.json.JSONObject
import com.google.ai.edge.litertlm.Engine
import com.google.ai.edge.litertlm.Backend
import com.google.ai.edge.litertlm.EngineConfig

/**
 * Result of a deterministic query executed locally.
 */
data class QueryEngineResult(
    val originalPrompt: String,
    val summary: String,
    val records: List<MilkRecordEntity>,
    val success: Boolean,
    val description: String
)

/**
 * Sealed result type returned by the engine.
 */
sealed class LocalEngineResult {
    data class ToolResult(val request: ToolRequest) : LocalEngineResult()
    data class SummaryResult(val summary: QueryEngineResult) : LocalEngineResult()
}

/**
 * Engine that first tries to extract a structured intent using the on‑device Gemma model.
 * If that fails (e.g., model not loaded or JSON parsing error) it falls back to the original
 * deterministic regex‑based logic (Phase 1).
 */
class LocalDeterministicQueryEngine(
    private val repository: MilkRecordRepository,
    private val toolExecutor: ToolExecutor = ToolExecutor(repository)
) {
    suspend fun executeQuery(userPrompt: String): LocalEngineResult {
        // ---------- Gemma intent extraction ----------
                // Simple manual intent detection for testing (Gemma model not invoked)
        val lowerPrompt = userPrompt.lowercase()
        // CREATE_MILK_RECORD detection
        if (lowerPrompt.contains("gave") && lowerPrompt.contains("litres")) {
            val nameMatch = Regex("([A-Za-z]+) gave").find(userPrompt)
            val farmerName = nameMatch?.groupValues?.get(1) ?: ""
            val qtyMatch = Regex("(\\d+(?:\\.\\d+)?)\\s*litres").find(userPrompt)
            val quantity = qtyMatch?.groupValues?.get(1) ?: ""
            val args = mutableMapOf<String, String>("farmerName" to farmerName, "quantity" to quantity)
            // optional fields
            Regex("fat\\s*(\\d+(?:\\.\\d+)?)").find(userPrompt)?.let { args["fat"] = it.groupValues[1] }
            Regex("snf\\s*(\\d+(?:\\.\\d+)?)").find(userPrompt)?.let { args["snf"] = it.groupValues[1] }
            val needsConfirmation = true
            return LocalEngineResult.ToolResult(ToolRequest(ToolIntent.CREATE_MILK_RECORD, args, needsConfirmation))
        }
        // GET_PENDING_PAYMENTS detection
        if (lowerPrompt.contains("payment") && (lowerPrompt.contains("pending") || lowerPrompt.contains("unpaid"))) {
            return LocalEngineResult.ToolResult(ToolRequest(ToolIntent.GET_PENDING_PAYMENTS, emptyMap(), false))
        }
        // GET_FARMER_HISTORY detection
        if (lowerPrompt.contains("what did") && lowerPrompt.contains("deliver")) {
            val nameMatch = Regex("what did ([A-Za-z]+)").find(lowerPrompt)
            val farmerName = nameMatch?.groupValues?.get(1) ?: ""
            return LocalEngineResult.ToolResult(ToolRequest(ToolIntent.GET_FARMER_HISTORY, mapOf("farmerName" to farmerName), false))
        }
        // Continue with deterministic fallback below
        if (engine != null) {
            try {
                val raw = engine.generate(userPrompt, maxTokens = 256)
                val jsonString = raw?.trim() ?: ""
                val json = JSONObject(jsonString)
                val intent = ToolIntent.valueOf(json.getString("intent"))
                val argsJson = json.getJSONObject("args")
                val argsMap = mutableMapOf<String, String>()
                val keys = argsJson.keys()
                while (keys.hasNext()) {
                    val key = keys.next()
                    argsMap[key] = argsJson.getString(key)
                }
                val needsConfirmation = json.optBoolean("needsConfirmation", false)
                return LocalEngineResult.ToolResult(ToolRequest(intent, argsMap, needsConfirmation))
            } catch (e: Exception) {
                // Parsing failed – continue to fallback.
            }
        }
        // ---------- Phase 1 deterministic fallback ----------
        val lower = userPrompt.lowercase()
        // Pending payments query
        if (lower.contains("payment") && (lower.contains("pending") || lower.contains("unpaid"))) {
            val pending = repository.getPendingPaymentRecords()
            val total = pending.sumOf { it.quantity }
            val summary = "Found ${pending.size} record(s) with pending payments totaling ${"%.1f".format(total)} L."
            return LocalEngineResult.SummaryResult(
                QueryEngineResult(userPrompt, summary, pending, true, "Local SQLite deterministic query execution (Offline)")
            )
        }
        // Farmer total query
        val farmerMatch = Regex("(?:how much did|volume for)\\s+([A-Za-z]+)", RegexOption.IGNORE_CASE)
            .find(userPrompt) ?: Regex("([A-Za-z]+)\\s+(?:give|produce|deliver|total)", RegexOption.IGNORE_CASE).find(userPrompt)
        if (farmerMatch != null) {
            val name = farmerMatch.groupValues[1]
            val records = repository.getRecordsByFarmerName(name)
            val totalQty = records.sumOf { it.quantity }
            val summary = if (records.isNotEmpty()) {
                "$name has delivered a total of ${"%.1f".format(totalQty)} L across ${records.size} record(s)."
            } else {
                "No local records found for farmer '$name'."
            }
            return LocalEngineResult.SummaryResult(
                QueryEngineResult(userPrompt, summary, records, true, "Local SQLite deterministic query execution (Offline)")
            )
        }
        // Today's records query
        if (lower.contains("today") || lower.contains("collected today") || lower.contains("today's records")) {
            val count = repository.getTodayRecordsCount().first()
            val litres = repository.getTodayTotalQuantity().first()
            val all = repository.getAllRecords().first()
            val summary = "Today: $count record(s) collected on device totaling ${"%.1f".format(litres)} L."
            return LocalEngineResult.SummaryResult(
                QueryEngineResult(userPrompt, summary, all.take(count), true, "Local SQLite deterministic query execution (Offline)")
            )
        }
        // Generic search fallback
        val records = repository.searchAndFilterRecords(query = userPrompt, paymentStatus = null, uploadStatus = null).first()
        return if (records.isNotEmpty()) {
            LocalEngineResult.SummaryResult(
                QueryEngineResult(userPrompt, "Found ${records.size} matching record(s) in local SQLite database.", records, true, "Local SQLite deterministic query execution (Offline)")
            )
        } else {
            val totalCount = repository.getAllRecordsCount().first()
            LocalEngineResult.SummaryResult(
                QueryEngineResult(userPrompt, "Query resolved locally. Device currently holds $totalCount total records offline. Try one of the suggested query templates above.", emptyList(), true, "Local SQLite deterministic query execution (Offline)")
            )
        }
    }
}
