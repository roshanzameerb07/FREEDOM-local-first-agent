package com.example.freedom.framework.model

import android.util.Log
import com.example.freedom.domain.query.FreedomQuery
import com.example.freedom.domain.query.QueryValidator
import com.example.freedom.domain.query.QwenQueryParser
import com.example.freedom.domain.query.RequestType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Checks whether an imported model is compatible with the FREEDOM semantic pipeline.
 *
 * Compatibility is determined by:
 * 1. Initializing the provider (loading the model file)
 * 2. Sending a controlled FREEDOM test prompt using the CURRENT frozen FreedomQuery DSL system prompt
 * 3. Parsing the output through [QwenQueryParser.parseJsonResponse]
 * 4. Validating the resulting [FreedomQuery] with [QueryValidator.validate]
 * 5. Verifying the query is valid and not UNSUPPORTED
 *
 * A model that cannot load → LOAD_FAILED
 * A model that loads but produces invalid FreedomQuery output → INCOMPATIBLE
 * A model that loads and produces a valid FreedomQuery → COMPATIBLE
 *
 * Design Principles:
 * - The test uses a controlled, deterministic query against the frozen FreedomQuery DSL
 * - The check is real inference, not a mock or legacy ToolIntent
 * - Only COMPATIBLE models may be activated
 * - INCOMPATIBLE models remain in storage but cannot run queries
 */
object ModelCompatibilityChecker {

    private const val TAG = "ModelCompatibilityChecker"

    /**
     * A controlled test query sent to the model during compatibility check.
     * Tests the canonical dairy collection query contract against the frozen FreedomQuery DSL.
     */
    const val TEST_QUERY = "How many farmers delivered milk today?"

    data class CompatibilityCheckResult(
        val status: ModelCompatibilityStatus,
        val reason: String,
        val rawOutput: String? = null,
        val parsedQuery: FreedomQuery? = null
    )

    /**
     * Run the compatibility check for a [LocalLitertModelProvider].
     *
     * @param provider A freshly constructed (not yet initialized) provider
     * @return [CompatibilityCheckResult] with status and human-readable reason
     */
    suspend fun check(provider: LocalLitertModelProvider): CompatibilityCheckResult =
        withContext(Dispatchers.IO) {
            // Step 1: Initialize (load model file)
            val initialized = try {
                provider.initialize()
            } catch (e: Exception) {
                Log.e(TAG, "Model failed to load", e)
                return@withContext CompatibilityCheckResult(
                    status = ModelCompatibilityStatus.LOAD_FAILED,
                    reason = "Model file could not be loaded: ${e.message}"
                )
            }

            if (!initialized) {
                return@withContext CompatibilityCheckResult(
                    status = ModelCompatibilityStatus.LOAD_FAILED,
                    reason = "Model engine failed to initialize. Check that the file is a valid .litertlm model."
                )
            }

            // Step 2: Build the full prompt using the frozen FreedomQuery DSL system prompt
            val testPrompt = "${QwenQueryParser.buildSystemPrompt()}\n\nUser Question: \"$TEST_QUERY\"\nJSON Output:"

            // Step 3: Run inference
            val rawOutput = try {
                provider.generateText(testPrompt)
            } catch (e: Exception) {
                Log.e(TAG, "Inference failed during compatibility check", e)
                null
            }

            if (rawOutput.isNullOrBlank()) {
                return@withContext CompatibilityCheckResult(
                    status = ModelCompatibilityStatus.INCOMPATIBLE,
                    reason = "Model produced no output for the test query.",
                    rawOutput = null
                )
            }

            // Step 4: Parse through the current QwenQueryParser and validate with QueryValidator
            val parseResult = evaluateFreedomQueryOutput(rawOutput, TEST_QUERY)

            return@withContext if (parseResult.isCompatible) {
                Log.i(TAG, "Compatibility check PASSED for model: ${provider.providerId}")
                CompatibilityCheckResult(
                    status = ModelCompatibilityStatus.COMPATIBLE,
                    reason = "Model passed the FreedomQuery DSL compatibility test.",
                    rawOutput = rawOutput,
                    parsedQuery = parseResult.query
                )
            } else {
                Log.w(TAG, "Compatibility check FAILED: ${parseResult.failureReason}")
                CompatibilityCheckResult(
                    status = ModelCompatibilityStatus.INCOMPATIBLE,
                    reason = parseResult.failureReason ?: "Output did not conform to the FreedomQuery DSL contract.",
                    rawOutput = rawOutput,
                    parsedQuery = parseResult.query
                )
            }
        }

    /**
     * Parse and validate model output against the current frozen FreedomQuery DSL.
     */
    fun evaluateFreedomQueryOutput(rawOutput: String, userQuery: String = TEST_QUERY): ParseEvaluation {
        val trimmed = rawOutput.trim()
        val jsonClean = if (trimmed.contains("```json")) {
            trimmed.substringAfter("```json").substringBefore("```").trim()
        } else if (trimmed.contains("```")) {
            trimmed.substringAfter("```").substringBefore("```").trim()
        } else {
            val start = trimmed.indexOf('{')
            val end = trimmed.lastIndexOf('}')
            if (start != -1 && end != -1 && end > start) trimmed.substring(start, end + 1) else trimmed
        }

        val jsonObject = try {
            org.json.JSONObject(jsonClean)
        } catch (e: Exception) {
            return ParseEvaluation(
                isCompatible = false,
                failureReason = "Output is not valid JSON: ${e.message}"
            )
        }

        // Must conform to current FreedomQuery schema, NOT legacy ToolIntent schema
        if (jsonObject.has("intent") && !jsonObject.has("type") && !jsonObject.has("target")) {
            return ParseEvaluation(
                isCompatible = false,
                failureReason = "Model returned legacy ToolIntent format instead of FreedomQuery DSL schema."
            )
        }

        if (!jsonObject.has("type") && !jsonObject.has("target")) {
            return ParseEvaluation(
                isCompatible = false,
                failureReason = "Model output missing required FreedomQuery DSL fields ('type' or 'target')."
            )
        }

        val parsedQuery = try {
            QwenQueryParser.parseJsonResponse(rawOutput, userQuery)
        } catch (e: Exception) {
            return ParseEvaluation(
                isCompatible = false,
                failureReason = "Failed to parse model output as FreedomQuery JSON: ${e.message}"
            )
        }

        if (parsedQuery == null) {
            return ParseEvaluation(
                isCompatible = false,
                failureReason = "Model output could not be parsed into a valid FreedomQuery object."
            )
        }

        if (parsedQuery.requestType == RequestType.UNSUPPORTED) {
            return ParseEvaluation(
                isCompatible = false,
                query = parsedQuery,
                failureReason = "Model returned UNSUPPORTED for standard field query: ${parsedQuery.unsupportedReason}"
            )
        }

        val validation = QueryValidator.validate(parsedQuery)
        if (!validation.isValid) {
            val errorSummary = validation.errors.joinToString("; ")
            return ParseEvaluation(
                isCompatible = false,
                query = parsedQuery,
                failureReason = "FreedomQuery failed validation: $errorSummary"
            )
        }

        return ParseEvaluation(
            isCompatible = true,
            query = parsedQuery
        )
    }

    data class ParseEvaluation(
        val isCompatible: Boolean,
        val query: FreedomQuery? = null,
        val failureReason: String? = null
    )
}
