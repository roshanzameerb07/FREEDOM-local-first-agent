package com.example.freedom.domain.query

/**
 * Generic deterministic text normalizer for entity names and mentions.
 *
 * Allowed operations:
 * - lowercase
 * - trim leading/trailing whitespace
 * - collapse repeated internal whitespace to a single space
 * - safe punctuation normalization (remove possessives, strip non-alphanumeric except spaces)
 * - Unicode NFC normalization
 *
 * NOT allowed:
 * - Query-specific stop words (e.g. "remove was", "remove this week")
 * - Sentence-level semantic processing
 * - Language-specific abbreviation expansion
 *
 * This normalizer operates on entity names/mentions ONLY, not on full natural-language questions.
 */
object NameNormalizer {

    /**
     * Normalizes a name or entity mention for comparison purposes.
     *
     * @param input Raw entity name or mention string
     * @return Normalized string suitable for exact or token-based matching
     */
    fun normalize(input: String): String {
        if (input.isBlank()) return ""

        var result = input

        // 1. Unicode NFC normalization (compose combining characters)
        result = java.text.Normalizer.normalize(result, java.text.Normalizer.Form.NFC)

        // 2. Lowercase
        result = result.lowercase()

        // 3. Remove possessive suffixes ('s, 's)
        result = result.replace(Regex("['']s\\b"), "")

        // 4. Strip non-alphanumeric characters except spaces
        //    Preserves letters (including Unicode letters) and digits
        result = result.replace(Regex("[^\\p{L}\\p{N}\\s]"), " ")

        // 5. Collapse repeated whitespace to a single space
        result = result.replace(Regex("\\s+"), " ")

        // 6. Trim
        result = result.trim()

        return result
    }

    /**
     * Tokenizes a normalized name into individual name tokens.
     * Filters out tokens shorter than 2 characters.
     *
     * @param normalizedName A string already passed through [normalize]
     * @return List of name tokens
     */
    fun tokenize(normalizedName: String): List<String> {
        if (normalizedName.isBlank()) return emptyList()
        return normalizedName.split(" ").filter { it.length >= 2 }
    }
}
