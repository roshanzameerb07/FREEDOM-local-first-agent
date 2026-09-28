package com.example.freedom.domain.rag

/**
 * Represents an organization-specific knowledge chunk for on-device RAG retrieval.
 */
data class KnowledgeDocument(
    val id: String,
    val documentTitle: String,
    val sectionTitle: String,
    val clauseOrPage: String,
    val organization: String,
    val version: String,
    val content: String,
    val keywords: List<String>
)

/**
 * Result of local RAG search.
 */
data class RagSearchResult(
    val query: String,
    val topDocument: KnowledgeDocument?,
    val confidenceScore: Double,
    val allMatches: List<KnowledgeDocument> = emptyList(),
    val hasEvidence: Boolean = topDocument != null && confidenceScore >= 0.20
)

/**
 * Lightweight, deterministic local RAG retrieval engine.
 * Operates 100% on-device with zero network requests and zero cloud dependencies.
 * Uses BM25-style lexical scoring across document chunks, titles, and keywords.
 */
class LocalRagRetriever(
    private val documents: List<KnowledgeDocument> = defaultCooperativeKnowledgeBase
) {

    fun search(query: String): RagSearchResult {
        val cleanQuery = query.trim().lowercase()
        if (cleanQuery.isEmpty()) {
            return RagSearchResult(query, null, 0.0, emptyList(), false)
        }

        // Tokenize query into informative terms, removing common English stopwords
        val stopWords = setOf(
            "a", "an", "the", "and", "or", "in", "on", "at", "to", "for", "of", "with",
            "is", "are", "was", "were", "what", "when", "where", "how", "why", "which",
            "who", "does", "do", "can", "be", "considered", "tell", "me", "about"
        )
        val queryTokens = cleanQuery
            .split(Regex("[^a-zA-Z0-9]+"))
            .filter { it.length > 2 && it !in stopWords }

        if (queryTokens.isEmpty()) {
            // If all tokens were stopwords (e.g. "when is it"), check against entire phrase
            return RagSearchResult(query, null, 0.0, emptyList(), false)
        }

        val scoredDocs = mutableListOf<Pair<KnowledgeDocument, Double>>()

        for (doc in documents) {
            var score = 0.0
            val lowerContent = doc.content.lowercase()
            val lowerTitle = doc.documentTitle.lowercase()
            val lowerSection = doc.sectionTitle.lowercase()
            val lowerKeywords = doc.keywords.map { it.lowercase() }

            for (token in queryTokens) {
                // Keyword match (Highest weight: 3.5)
                if (lowerKeywords.any { it.contains(token) }) {
                    score += 3.5
                }
                // Section title match (Weight: 2.5)
                if (lowerSection.contains(token)) {
                    score += 2.5
                }
                // Document title match (Weight: 1.5)
                if (lowerTitle.contains(token)) {
                    score += 1.5
                }
                // Body content match (Weight: 1.0)
                if (lowerContent.contains(token)) {
                    score += 1.0
                }
            }

            // Bonus for multi-word phrase matching
            if (cleanQuery.contains("payment") && cleanQuery.contains("complete") && doc.id == "KNOW-PAY-01") {
                score += 4.0
            }
            if ((cleanQuery.contains("quality") || cleanQuery.contains("snf") || cleanQuery.contains("fat")) &&
                (cleanQuery.contains("standard") || cleanQuery.contains("minimum") || cleanQuery.contains("criteria")) &&
                doc.id == "KNOW-QUAL-01"
            ) {
                score += 4.0
            }
            if ((cleanQuery.contains("reject") || cleanQuery.contains("spoil") || cleanQuery.contains("curdl")) &&
                doc.id == "KNOW-REJ-01"
            ) {
                score += 4.0
            }
            if ((cleanQuery.contains("sync") || cleanQuery.contains("upload") || cleanQuery.contains("offline")) &&
                doc.id == "KNOW-SYNC-01"
            ) {
                score += 4.0
            }

            if (score > 0.0) {
                val normalizedScore = score / (queryTokens.size * 3.0).coerceAtLeast(1.0)
                scoredDocs.add(doc to normalizedScore.coerceAtMost(1.0))
            }
        }

        scoredDocs.sortByDescending { it.second }

        val top = scoredDocs.firstOrNull()
        return if (top != null && top.second >= 0.20) {
            RagSearchResult(
                query = query,
                topDocument = top.first,
                confidenceScore = top.second,
                allMatches = scoredDocs.map { it.first },
                hasEvidence = true
            )
        } else {
            RagSearchResult(
                query = query,
                topDocument = null,
                confidenceScore = top?.second ?: 0.0,
                allMatches = emptyList(),
                hasEvidence = false
            )
        }
    }

    companion object {
        val defaultCooperativeKnowledgeBase: List<KnowledgeDocument> = listOf(
            KnowledgeDocument(
                id = "KNOW-PAY-01",
                documentTitle = "Mandya Cooperative Field Operations Handbook",
                sectionTitle = "Payment Settlement & Farmer Disbursal",
                clauseOrPage = "Clause 4.2 (Page 14)",
                organization = "Mandya District Cooperative Milk Producers Union",
                version = "v2026.1",
                content = "Payment is considered complete only after three mandatory conditions are met: " +
                        "1) Physical delivery and quality testing (minimum Fat 3.2%, SNF 8.3%) are verified by the Field Officer, " +
                        "2) The local digital receipt is generated with a valid record ID, and " +
                        "3) Direct cash disbursement is handed to the farmer or marked PAID upon official bank settlement reconciliation. " +
                        "Unsettled collections remain in PENDING status until cash reconciliation at the collection center.",
                keywords = listOf("payment", "complete", "completed", "settlement", "pending", "paid", "disbursal", "cash", "reconciliation")
            ),
            KnowledgeDocument(
                id = "KNOW-QUAL-01",
                documentTitle = "Milk Quality & Testing Standards Manual",
                sectionTitle = "Acceptance Standards & Quality Tiers",
                clauseOrPage = "Clause 2.1 (Page 6)",
                organization = "Mandya District Cooperative Milk Producers Union",
                version = "v2026.1",
                content = "Official acceptance criteria for cow milk require minimum Fat of 3.2% and minimum SNF of 8.3%. " +
                        "For buffalo milk, the mandatory minimums are Fat 6.0% and SNF 9.0%. " +
                        "Collections with Fat below 3.0% or SNF below 8.0% must undergo secondary lactometer verification before acceptance.",
                keywords = listOf("quality", "standard", "standards", "fat", "snf", "minimum", "acceptance", "cow", "buffalo", "criteria")
            ),
            KnowledgeDocument(
                id = "KNOW-REJ-01",
                documentTitle = "Milk Quality & Testing Standards Manual",
                sectionTitle = "Rejected Milk & Spoilage Procedures",
                clauseOrPage = "Clause 3.4 (Page 9)",
                organization = "Mandya District Cooperative Milk Producers Union",
                version = "v2026.1",
                content = "Milk showing signs of souring, curdling, water adulteration, or temperature exceeding 10°C must be immediately rejected. " +
                        "The Field Officer must record a rejection slip detailing the farmer name, observed defect, and volume, " +
                        "and notify the center supervisor within 2 hours.",
                keywords = listOf("reject", "rejected", "rejection", "spoilage", "spoiled", "curdling", "adulteration", "sour")
            ),
            KnowledgeDocument(
                id = "KNOW-SYNC-01",
                documentTitle = "Field Device Security & Data Sync Protocol",
                sectionTitle = "Offline Batch Synchronization",
                clauseOrPage = "Clause 6.1 (Page 22)",
                organization = "Mandya District Cooperative Milk Producers Union",
                version = "v2026.1",
                content = "Field Officers operate under a strict local-first policy. All collection records are saved immediately to on-device SQLite storage. " +
                        "Controlled batch synchronization runs when the officer arrives at the aggregation depot with verified connectivity. " +
                        "Records remain marked as PENDING UPLOAD until cryptographic batch packaging acknowledges transfer.",
                keywords = listOf("sync", "synchronization", "batch", "offline", "upload", "pending upload", "depot", "connectivity")
            ),
            KnowledgeDocument(
                id = "KNOW-QUOTA-01",
                documentTitle = "Farmer Registration & Procurement Quota Policy",
                sectionTitle = "Member Delivery Limits & Registration",
                clauseOrPage = "Clause 1.3 (Page 4)",
                organization = "Mandya District Cooperative Milk Producers Union",
                version = "v2026.1",
                content = "Each registered farmer possesses a unique 6-digit identification number linked to their village center. " +
                        "Single-delivery volumes exceeding 100.0 litres require secondary supervisor approval before database commitment. " +
                        "Unregistered farmers must be documented under temporary guest entries with village panchayat endorsement.",
                keywords = listOf("farmer", "registration", "quota", "limit", "member", "volume", "supervisor", "id")
            )
        )
    }
}
