package com.example.freedom.domain.ai

import android.util.Log
import com.example.freedom.data.local.entity.FarmerEntity
import com.example.freedom.data.local.entity.MilkRecordEntity
import com.example.freedom.data.repository.MilkRecordRepository
import com.example.freedom.domain.query.FreedomQuery
import com.example.freedom.domain.query.FreedomQueryExecutor
import com.example.freedom.domain.query.QwenQueryParser

/**
 * Result returned by the local query engine.
 */
data class QueryEngineResult(
    val originalPrompt: String,
    val summary: String,
    val records: List<MilkRecordEntity> = emptyList(),
    val success: Boolean = true,
    val executionPipelineDescription: String = "Qwen3 1.7B → FreedomQuery DSL → Room DB",
    val isDeterministicEngine: Boolean = true,
    val debugInfo: String? = null
)

/**
 * Result wrapper for Ask Freedom UI.
 */
sealed class LocalEngineResult {
    data class ToolResult(val request: ToolRequest) : LocalEngineResult()
    data class SummaryResult(val summary: QueryEngineResult) : LocalEngineResult()
}

/**
 * Main FREEDOM Natural Language Query Engine.
 *
 * Delegates entirely to the canonical FreedomQuery pipeline:
 * QwenQueryParser → FreedomQuery → QueryValidator → EntityResolver → FreedomQueryExecutor → Room
 *
 * This class exists as the UI-facing entry point. It does NOT contain
 * query logic itself — all query logic lives in FreedomQueryExecutor.
 */
class LocalDeterministicQueryEngine(
    private val repository: MilkRecordRepository,
    private val queryExecutor: FreedomQueryExecutor = FreedomQueryExecutor(repository)
) {
    /**
     * Known farmers for entity resolution.
     * In production, this would be populated from FarmerRepository.
     * For now, populated externally or left empty (executor handles gracefully).
     */
    var knownFarmers: List<FarmerEntity> = emptyList()

    suspend fun executeQuery(userPrompt: String): LocalEngineResult {
        Log.i("LocalQueryEngine", "Processing query: '${'$'}userPrompt'")

        // 1. Parse natural language → FreedomQuery
        val query = QwenQueryParser.parse(userPrompt, knownFarmers)
        Log.i("LocalQueryEngine", "Structured Query: ${'$'}query")

        // 2. Execute against Room via canonical pipeline
        val result = queryExecutor.execute(query, userPrompt, knownFarmers)

        // 3. Handle write confirmation (maintain UI compatibility)
        if (result.needsWriteConfirmation) {
            val toolReq = ToolRequest(
                intent = ToolIntent.CREATE_MILK_RECORD,
                args = result.writeArgs,
                needsConfirmation = true,
                executionMode = ExecutionMode.GEMMA_EXECUTED
            )
            return LocalEngineResult.ToolResult(toolReq)
        }

        // 4. Return summary result
        val debugText = """
            USER: ${'$'}userPrompt
            QUERY: ${'$'}query
            RESULT: ${'$'}{result.summary}
            RECORDS: ${'$'}{result.records.size}
        """.trimIndent()
        Log.d("LocalQueryEngine", debugText)

        val queryResult = QueryEngineResult(
            originalPrompt = userPrompt,
            summary = result.summary,
            records = result.records,
            success = result.success,
            executionPipelineDescription = "Qwen3 1.7B → FreedomQuery DSL → Room DB",
            isDeterministicEngine = true,
            debugInfo = debugText
        )

        return LocalEngineResult.SummaryResult(queryResult)
    }
}
