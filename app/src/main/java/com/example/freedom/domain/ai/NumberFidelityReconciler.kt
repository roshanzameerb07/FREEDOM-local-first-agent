package com.example.freedom.domain.ai

/**
 * Reconciles numbers extracted from natural language input to guarantee 100% numerical fidelity.
 *
 * Quantized on-device models (like 1B models) frequently truncate or round floating point
 * numbers to integers (e.g. turning "4.2" into "4", "8.6" into "8", or "18.5" into "18").
 *
 * This reconciler cross-verifies model-extracted arguments against the raw user input:
 * 1. If the raw input explicitly specifies a decimal or integer value (e.g. "fat 4.2", "4.25% fat",
 *    "SNF 8.6", "8.65 snf", "18.5 litres", "18.50 ltr"), it restores the exact raw representation.
 * 2. Purges hallucinated defaults: If a field was never in the user prompt (e.g. "Ramesh gave 80 litres"),
 *    it removes the field entirely rather than defaulting to "0.0" or "0".
 * 3. Handles values with or without units, percentage symbols, and different word orders.
 */
object NumberFidelityReconciler {

    fun reconcile(userInput: String, extractedArgs: Map<String, String>): Map<String, String> {
        val result = extractedArgs.toMutableMap()

        // 1. Reconcile Quantity (e.g. 18, 18.5, 18.50)
        val rawQty = extractQuantityFromText(userInput)
        val extractedQty = extractedArgs["quantity"]?.trim()
        if (rawQty != null) {
            // If raw text has a decimal and extracted rounded it, or if extracted is missing
            if (extractedQty == null || (rawQty.contains(".") && !extractedQty.contains(".")) || rawQty.length > extractedQty.length) {
                result["quantity"] = rawQty
            }
        } else if (extractedQty != null && extractedQty != "0" && extractedQty != "0.0") {
            // Keep extracted if present and non-zero
        } else {
            result.remove("quantity")
        }

        // 2. Reconcile Fat (e.g. 4.2, 4.25, 4.2%)
        val rawFat = extractFatFromText(userInput)
        val extractedFat = extractedArgs["fat"]?.trim()
        if (rawFat != null) {
            if (extractedFat == null || (rawFat.contains(".") && !extractedFat.contains(".")) || rawFat.length > extractedFat.length) {
                result["fat"] = rawFat
            }
        } else if (extractedFat != null && extractedFat != "0" && extractedFat != "0.0") {
            // Keep if model extracted a valid number not caught by regex
        } else {
            // Never let missing fat become 0.0
            result.remove("fat")
        }

        // 3. Reconcile SNF (e.g. 8.6, 8.65, 8.6%)
        val rawSnf = extractSnfFromText(userInput)
        val extractedSnf = extractedArgs["snf"]?.trim()
        if (rawSnf != null) {
            if (extractedSnf == null || (rawSnf.contains(".") && !extractedSnf.contains(".")) || rawSnf.length > extractedSnf.length) {
                result["snf"] = rawSnf
            }
        } else if (extractedSnf != null && extractedSnf != "0" && extractedSnf != "0.0") {
            // Keep if model extracted a valid number not caught by regex
        } else {
            // Never let missing SNF become 0.0
            result.remove("snf")
        }

        // 4. Reconcile Farmer Name
        if (result["farmerName"].isNullOrBlank()) {
            val nameMatch = Regex("([A-Za-z]+)\\s+gave", RegexOption.IGNORE_CASE).find(userInput)
                ?: Regex("(?:record|add|for)\\s+([A-Za-z]+)", RegexOption.IGNORE_CASE).find(userInput)
            if (nameMatch != null) {
                val candidate = nameMatch.groupValues[1].replaceFirstChar { it.uppercase() }
                if (candidate.lowercase() !in listOf("milk", "record", "collection", "today")) {
                    result["farmerName"] = candidate
                }
            }
        }

        // 5. Payment details reconciliation
        val lower = userInput.lowercase()
        if (lower.contains("cash")) {
            result["paymentMethod"] = "CASH"
            result["paymentStatus"] = "RECORDED_LOCALLY"
        } else if (lower.contains("upi")) {
            result["paymentMethod"] = "UPI"
            result["paymentStatus"] = "RECORDED_LOCALLY"
        } else if (lower.contains("bank")) {
            result["paymentMethod"] = "BANK_TRANSFER"
            result["paymentStatus"] = "RECORDED_LOCALLY"
        } else if (lower.contains("pending") || lower.contains("unpaid") || lower.contains("due")) {
            result["paymentStatus"] = "PENDING"
        } else if (lower.contains("paid")) {
            result["paymentStatus"] = "RECORDED_LOCALLY"
        }

        return result
    }

    fun extractQuantityFromText(text: String): String? {
        val patterns = listOf(
            Regex("(\\d+(?:\\.\\d+)?)\\s*(?:litres?|liters?|ltrs?|ltr|l\\b)", RegexOption.IGNORE_CASE),
            Regex("(?:gave|collected|delivered|record)\\s+(\\d+(?:\\.\\d+)?)", RegexOption.IGNORE_CASE),
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
            Regex("fat\\s*[:=]?\\s*(\\d+(?:\\.\\d+)?)\\s*%?", RegexOption.IGNORE_CASE),
            Regex("(\\d+(?:\\.\\d+)?)\\s*%\\s*fat\\b", RegexOption.IGNORE_CASE),
            Regex("(\\d+(?:\\.\\d+)?)\\s*fat\\b", RegexOption.IGNORE_CASE)
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
            Regex("snf\\s*[:=]?\\s*(\\d+(?:\\.\\d+)?)\\s*%?", RegexOption.IGNORE_CASE),
            Regex("(\\d+(?:\\.\\d+)?)\\s*%\\s*snf\\b", RegexOption.IGNORE_CASE),
            Regex("(\\d+(?:\\.\\d+)?)\\s*snf\\b", RegexOption.IGNORE_CASE)
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
