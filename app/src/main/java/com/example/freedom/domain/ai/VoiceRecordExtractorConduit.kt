package com.example.freedom.domain.ai

/**
 * Data class representing an extracted, unconfirmed draft from voice or natural language.
 *
 * Future Architecture Conduit:
 * USER INPUT (Voice/Audio) -> Whisper/On-Device ASR -> Raw Transcript
 * -> Local SLM (e.g. Gemma 2B / MediaPipe LLM) -> Structured MilkRecordDraft
 * -> Confirmation Screen (User Review) -> MilkRecordValidator -> Local Database
 */
data class MilkRecordDraft(
    val farmerName: String = "",
    val quantity: String = "",
    val fat: String = "",
    val snf: String = "",
    val paymentStatus: String = "PENDING",
    val confidenceScore: Float = 0.0f,
    val sourceTranscript: String = ""
)

interface VoiceRecordExtractorConduit {
    /**
     * Extracts structured fields from natural language speech transcripts.
     * In Phase 1, uses a deterministic pattern extractor to preview the exact contract.
     * In Phase 2, this will route to on-device Gemma / MediaPipe LLM Inference.
     */
    suspend fun extractRecordFromTranscript(transcript: String): MilkRecordDraft
}

class PatternBasedVoiceExtractor : VoiceRecordExtractorConduit {

    override suspend fun extractRecordFromTranscript(transcript: String): MilkRecordDraft {
        val clean = transcript.trim()
        if (clean.isEmpty()) return MilkRecordDraft()

        // Example: "Ramesh gave 18 litres, fat 4.2 and SNF 8.6. Payment is pending."
        var farmerName = ""
        var quantity = ""
        var fat = ""
        var snf = ""
        var paymentStatus = "PENDING"

        // Extract farmer name (e.g., first word or before 'gave'/'delivered')
        val nameMatch = Regex("""^([A-Za-z]+)\s+(?:gave|delivered|brought|collected|supplied)""", RegexOption.IGNORE_CASE).find(clean)
        if (nameMatch != null) {
            farmerName = nameMatch.groupValues[1].replaceFirstChar { it.uppercase() }
        } else {
            val firstWord = clean.split(" ").firstOrNull() ?: ""
            if (firstWord.isNotEmpty()) farmerName = firstWord.replaceFirstChar { it.uppercase() }
        }

        // Extract Quantity (e.g., "18 litres", "18 L", "18.5 liters", "18.5")
        val qtyMatch = Regex("""(\d+(?:\.\d+)?)\s*(?:litres?|liters?|ltr|l\b)""", RegexOption.IGNORE_CASE).find(clean)
        if (qtyMatch != null) {
            quantity = qtyMatch.groupValues[1]
        }

        // Extract Fat (e.g., "fat 4.2" or "4.2 fat")
        val fatMatch = Regex("""(?:fat\s*[:=]?\s*(\d+(?:\.\d+)?)|(\d+(?:\.\d+)?)\s*fat)""", RegexOption.IGNORE_CASE).find(clean)
        if (fatMatch != null) {
            fat = if (fatMatch.groupValues[1].isNotEmpty()) fatMatch.groupValues[1] else fatMatch.groupValues[2]
        }

        // Extract SNF (e.g., "snf 8.6" or "8.6 snf")
        val snfMatch = Regex("""(?:snf\s*[:=]?\s*(\d+(?:\.\d+)?)|(\d+(?:\.\d+)?)\s*snf)""", RegexOption.IGNORE_CASE).find(clean)
        if (snfMatch != null) {
            snf = if (snfMatch.groupValues[1].isNotEmpty()) snfMatch.groupValues[1] else snfMatch.groupValues[2]
        }

        // Extract Payment Status
        if (clean.contains("paid", ignoreCase = true)) {
            paymentStatus = "PAID"
        } else if (clean.contains("pending", ignoreCase = true)) {
            paymentStatus = "PENDING"
        }

        return MilkRecordDraft(
            farmerName = farmerName,
            quantity = quantity,
            fat = fat,
            snf = snf,
            paymentStatus = paymentStatus,
            confidenceScore = 0.95f,
            sourceTranscript = clean
        )
    }
}
