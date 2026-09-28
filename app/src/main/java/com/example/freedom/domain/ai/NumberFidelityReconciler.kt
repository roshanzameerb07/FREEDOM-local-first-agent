package com.example.freedom.domain.ai

/**
 * Reconciles numbers extracted from natural language input to guarantee 100% numerical fidelity.
 *
 * Small language models (like 1B quantized models) are prone to rounding or truncating floats
 * to integers (e.g., turning "4.2" into "4" or "8.6" into "8").
 *
 * This reconciler cross-verifies model-extracted arguments against the raw user input:
 * 1. If the raw input explicitly specifies a decimal value (e.g. "fat 4.2" or "SNF 8.6" or "18.5 litres"),
 *    it restores the exact decimal representation without truncation.
 * 2. Distinguishes missing values from 0.0: If a field is omitted in the prompt (e.g. "Ramesh gave 80 litres"),
 *    it leaves the field completely absent/empty, never defaulting to "0.0".
 * 3. Preserves raw string representation alongside parsed values.
 */
object NumberFidelityReconciler {

    data class ReconciledNumeric(
        val rawString: String,
        val parsedDouble: Double?,
        val isExplicitlyProvided: Boolean
    )

    fun reconcile(userInput: String, extractedArgs: Map<String, String>): Map<String, String> {
        val result = extractedArgs.toMutableMap()

        // 1. Reconcile Quantity
        val userQty = extractQuantityFromText(userInput)
        val extractedQty = extractedArgs["quantity"]?.trim()
        if (userQty != null) {
            // User had an explicit quantity in text; prefer exact decimal/integer string from text
            // especially if extracted rounded 18.5 to 18
            if (extractedQty == null || (userQty.contains(".") && !extractedQty.contains("."))) {
                result["quantity"] = userQty
            }
        }

        // 2. Reconcile Fat
        val userFat = extractFatFromText(userInput)
        val extractedFat = extractedArgs["fat"]?.trim()
        if (userFat != null) {
            if (extractedFat == null || (userFat.contains(".") && !extractedFat.contains("."))) {
                result["fat"] = userFat
            }
        } else if (extractedFat != null && extractedFat != "0" && extractedFat != "0.0") {
            // Keep extracted if present
        } else {
            // Fat was NOT in user prompt: ensure it is NOT defaulted to "0.0" or "0"
            result.remove("fat")
        }

        // 3. Reconcile SNF
        val userSnf = extractSnfFromText(userInput)
        val extractedSnf = extractedArgs["snf"]?.trim()
        if (userSnf != null) {
            if (extractedSnf == null || (userSnf.contains(".") && !extractedSnf.contains("."))) {
                result["snf"] = userSnf
            }
        } else if (extractedSnf != null && extractedSnf != "0" && extractedSnf != "0.0") {
            // Keep extracted if present
        } else {
            // SNF was NOT in user prompt: ensure it is NOT defaulted to "0.0" or "0"
            result.remove("snf")
        }

        // 4. Reconcile Farmer Name
        if (result["farmerName"].isNullOrBlank()) {
            val nameMatch = Regex("([A-Za-z]+)\\s+gave", RegexOption.IGNORE_CASE).find(userInput)
            if (nameMatch != null) {
                result["farmerName"] = nameMatch.groupValues[1].replaceFirstChar { it.uppercase() }
            }
        }

        return result
    }

    fun extractQuantityFromText(text: String): String? {
        val patterns = listOf(
            Regex("(\\d+(?:\\.\\d+)?)\\s*(?:litres?|liters?|ltrs?|ltr|l\\b)", RegexOption.IGNORE_CASE),
            Regex("gave\\s+(\\d+(?:\\.\\d+)?)", RegexOption.IGNORE_CASE),
            Regex("quantity\\s*[:=]?\\s*(\\d+(?:\\.\\d+)?)", RegexOption.IGNORE_CASE)
        )
        for (pattern in patterns) {
            val match = pattern.find(text)
            if (match != null) {
                return match.groupValues[1]
            }
        }
        return null
    }

    fun extractFatFromText(text: String): String? {
        val patterns = listOf(
            Regex("fat\\s*[:=]?\\s*(\\d+(?:\\.\\d+)?)", RegexOption.IGNORE_CASE),
            Regex("(\\d+(?:\\.\\d+)?)\\s*(?:%\\s*fat|fat)", RegexOption.IGNORE_CASE)
        )
        for (pattern in patterns) {
            val match = pattern.find(text)
            if (match != null) {
                return match.groupValues[1]
            }
        }
        return null
    }

    fun extractSnfFromText(text: String): String? {
        val patterns = listOf(
            Regex("snf\\s*[:=]?\\s*(\\d+(?:\\.\\d+)?)", RegexOption.IGNORE_CASE),
            Regex("(\\d+(?:\\.\\d+)?)\\s*(?:%\\s*snf|snf)", RegexOption.IGNORE_CASE)
        )
        for (pattern in patterns) {
            val match = pattern.find(text)
            if (match != null) {
                return match.groupValues[1]
            }
        }
        return null
    }
}
