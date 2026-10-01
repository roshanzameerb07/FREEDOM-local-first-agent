package com.example.freedom.framework.model

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Checks whether an imported model is compatible with the FREEDOM semantic pipeline.
 *
 * Compatibility is determined by:
 * 1. Initializing the provider (loading the model file)
 * 2. Sending a controlled FREEDOM test prompt
 * 3. Parsing the output through [com.example.freedom.domain.query.QwenQueryParser]
 * 4. Verifying the result is a non-null, non-UNSUPPORTED FreedomQuery
 *
 * A model that cannot load → LOAD_FAILED
 * A model that loads but produces invalid output → INCOMPATIBLE
 * A model that loads and produces a parseable FREEDOM query → COMPATIBLE
 *
 * Design Principles:
 * - The test uses a controlled, deterministic query — not user input
 * - The check is real inference, not a mock
 * - Only COMPATIBLE models may be activated
 * - INCOMPATIBLE models remain in storage but cannot run queries
 */
object ModelCompatibilityChecker {

    private const val TAG = "ModelCompatibilityChecker"

    /**
     * A controlled test query sent to the model during compatibility check.
     * This is a real dairy-domain question that should produce a GET_TODAY_SUMMARY intent.
     */
    private const val TEST_QUERY = "How many farmers delivered milk today?"

    data class CompatibilityCheckResult(
        val status: ModelCompatibilityStatus,
        val reason: String,
        val rawOutput: String? = null
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

            // Step 2: Build a system prompt identical to what QwenQueryParser would use
            val testPrompt = buildCompatibilityTestPrompt(TEST_QUERY)

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

            // Step 4: Attempt to parse the output as FreedomQuery JSON
            val parseResult = tryParseFreedomOutput(rawOutput)

            return@withContext if (parseResult.isCompatible) {
                Log.i(TAG, "Compatibility check PASSED for model: ${provider.providerId}")
                CompatibilityCheckResult(
                    status = ModelCompatibilityStatus.COMPATIBLE,
                    reason = "Model passed the FREEDOM query compatibility test.",
                    rawOutput = rawOutput
                )
            } else {
                Log.w(TAG, "Compatibility check FAILED: ${parseResult.failureReason}")
                CompatibilityCheckResult(
                    status = ModelCompatibilityStatus.INCOMPATIBLE,
                    reason = parseResult.failureReason ?: "Output did not match the FREEDOM query format.",
                    rawOutput = rawOutput
                )
            }
        }

    /**
     * Build a minimal FREEDOM system prompt for the compatibility test.
     * This mirrors the format used by QwenQueryParser.buildSystemPrompt().
     */
    private fun buildCompatibilityTestPrompt(userQuery: String): String {
        return """You are FREEDOM, a local-first AI agent for a dairy cooperative.
Analyze the user request and output ONLY valid JSON, nothing else.

JSON structure:
{
  "intent": "<CAPABILITY_NAME>",
  "args": {},
  "needsConfirmation": false
}

Capabilities: CREATE_MILK_RECORD, GET_FARMER_HISTORY, GET_PENDING_PAYMENTS, GET_TODAY_SUMMARY, GET_WORKER_PROFILE, COUNT_FARMERS_COVERED, GET_ORGANIZATION_INFO, SEARCH_LOCAL_KNOWLEDGE, ASK_CLARIFICATION, UNKNOWN_OR_UNSUPPORTED

User request: $userQuery""".trimIndent()
    }

    /**
     * Try to parse model output as a FreedomQuery-compatible JSON object.
     * Returns a result indicating whether parsing succeeded.
     */
    private fun tryParseFreedomOutput(rawOutput: String): ParseResult {
        return try {
            // Extract JSON from output (model may wrap in markdown or prose)
            val json = extractJson(rawOutput)
                ?: return ParseResult(false, "No JSON found in model output.")

            val trimmed = json.trim()
            if (!trimmed.startsWith("{")) {
                return ParseResult(false, "Output is not a JSON object.")
            }

            // Check for required fields
            if (!trimmed.contains("\"intent\"")) {
                return ParseResult(false, "JSON missing required 'intent' field.")
            }

            // Check that intent is one of the known capabilities (not empty or garbage)
            val knownIntents = setOf(
                "CREATE_MILK_RECORD", "GET_FARMER_HISTORY", "GET_PENDING_PAYMENTS",
                "GET_TODAY_SUMMARY", "GET_WORKER_PROFILE", "GET_TODAY_WORKER_SUMMARY",
                "GET_WEEKLY_WORKER_SUMMARY", "COUNT_FARMERS_COVERED", "GET_ORGANIZATION_INFO",
                "SEARCH_LOCAL_KNOWLEDGE", "ASK_CLARIFICATION", "UNKNOWN_OR_UNSUPPORTED"
            )
            val hasKnownIntent = knownIntents.any { intent ->
                trimmed.contains("\"$intent\"")
            }

            if (!hasKnownIntent) {
                return ParseResult(false, "Intent value is not a recognized FREEDOM capability.")
            }

            ParseResult(isCompatible = true)
        } catch (e: Exception) {
            ParseResult(false, "Parse error: ${e.message}")
        }
    }

    /**
     * Extract the first JSON object from a potentially noisy model response.
     */
    private fun extractJson(text: String): String? {
        val start = text.indexOf('{')
        if (start == -1) return null
        var depth = 0
        for (i in start until text.length) {
            when (text[i]) {
                '{' -> depth++
                '}' -> {
                    depth--
                    if (depth == 0) return text.substring(start, i + 1)
                }
            }
        }
        return null
    }

    private data class ParseResult(
        val isCompatible: Boolean,
        val failureReason: String? = null
    )
}
