package com.example.freedom.domain.query

import com.example.freedom.data.local.entity.FarmerEntity

/**
 * Deterministic entity resolution subsystem.
 *
 * Resolves entity mentions produced by the LLM against the authoritative
 * local farmer entity set. Operates only on entity mentions, NOT on full
 * natural-language sentences.
 *
 * Explicit Outcomes:
 * 1. RESOLVED: Exactly one farmer matches.
 * 2. AMBIGUOUS: Multiple farmers match; user clarification required.
 * 3. NOT_FOUND: No farmer matches; must NOT be conflated with "0 records found".
 *
 * Resolution order:
 * 1. Exact canonical normalized name match
 * 2. Word-boundary substring match (preferring unique longest match)
 * 3. Safe unique token match (only if exactly one farmer has that token)
 * 4. Otherwise → AMBIGUOUS or NOT_FOUND
 *
 * Never guesses. Never uses stop-word lists.
 */
class EntityResolver {

    sealed class Resolution {
        /** Exactly one farmer matched */
        data class Resolved(
            val farmer: FarmerEntity
        ) : Resolution()

        /** Multiple farmers matched — need user clarification */
        data class Ambiguous(
            val mention: String,
            val candidates: List<FarmerEntity>
        ) : Resolution()

        /** Entity mention not found in local database */
        data class NotFound(
            val mention: String
        ) : Resolution()
    }

    /**
     * Resolves an entity mention against known farmers.
     *
     * @param mention Raw entity mention from the LLM (e.g. "Ramesh Naik", "Suresh").
     * @param knownFarmers Authoritative list of active farmers from the local database.
     * @return Resolution result: Resolved, Ambiguous, or NotFound.
     */
    fun resolve(mention: String, knownFarmers: List<FarmerEntity>): Resolution {
        if (mention.isBlank()) {
            return Resolution.NotFound(mention = mention)
        }

        if (knownFarmers.isEmpty()) {
            return Resolution.NotFound(mention = mention)
        }

        val normalizedMention = NameNormalizer.normalize(mention)
        if (normalizedMention.isBlank()) {
            return Resolution.NotFound(mention = mention)
        }

        // Step 1: Exact canonical name match (case-insensitive via normalization)
        val exactMatch = knownFarmers.firstOrNull { it.normalizedName == normalizedMention }
        if (exactMatch != null) {
            return Resolution.Resolved(farmer = exactMatch)
        }

        // Step 2: Full normalized name contained as word-boundary match
        val fullNameMatches = knownFarmers.filter { farmer ->
            val normFarmer = farmer.normalizedName
            normFarmer.contains(normalizedMention) || normalizedMention.contains(normFarmer)
        }.sortedByDescending { it.normalizedName.length }

        if (fullNameMatches.size == 1) {
            return Resolution.Resolved(farmer = fullNameMatches.first())
        }
        if (fullNameMatches.size > 1) {
            val longestLen = fullNameMatches.first().normalizedName.length
            val longestMatches = fullNameMatches.filter { it.normalizedName.length == longestLen }
            if (longestMatches.size == 1) {
                return Resolution.Resolved(farmer = longestMatches.first())
            }
            return Resolution.Ambiguous(
                mention = mention,
                candidates = fullNameMatches
            )
        }

        // Step 3: Token-based partial match
        val mentionTokens = NameNormalizer.tokenize(normalizedMention)
        if (mentionTokens.isEmpty()) {
            return Resolution.NotFound(mention = mention)
        }

        val tokenMatches = knownFarmers.filter { farmer ->
            val farmerTokens = NameNormalizer.tokenize(farmer.normalizedName)
            mentionTokens.any { mentionToken ->
                farmerTokens.any { farmerToken -> farmerToken == mentionToken }
            }
        }

        return when {
            tokenMatches.size == 1 -> Resolution.Resolved(farmer = tokenMatches.first())
            tokenMatches.size > 1 -> Resolution.Ambiguous(
                mention = mention,
                candidates = tokenMatches
            )
            else -> Resolution.NotFound(mention = mention)
        }
    }
}
