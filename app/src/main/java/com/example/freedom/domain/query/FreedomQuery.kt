package com.example.freedom.domain.query

/**
 * ═══════════════════════════════════════════════════════════════════════
 * CANONICAL FREEDOM QUERY REPRESENTATION (FROZEN PRE-TRAINING CONTRACT)
 * ═══════════════════════════════════════════════════════════════════════
 *
 * This is the frozen, typed contract between the on-device Qwen3 model
 * and the deterministic Kotlin query executor.
 *
 * Pre-Training Guarantees:
 * 1. The model interprets language into structured intent.
 * 2. Deterministic Kotlin determines database truth.
 * 3. The model NEVER generates raw SQL or absolute epoch timestamps.
 * 4. Entity resolution outcomes are explicit: RESOLVED, AMBIGUOUS, NOT_FOUND.
 * 5. Temporal constraints support relative periods, explicit dates, and explicit ranges.
 * 6. Explicit COUNT semantics: COUNT_RECORDS vs COUNT_DISTINCT_FARMERS.
 * 7. Grouped aggregations identify exact aggregate metric when sorting.
 * 8. Payment scope distinguishes LATEST, ALL, and ANY matching payment records.
 */

// ── Request Classification ───────────────────────────────────────────

enum class RequestType {
    /** Read-only query against the Room database */
    QUERY,
    /** Write operation (record creation) requiring explicit user confirmation */
    WRITE,
    /** Insufficient information to execute; request clarification from user */
    CLARIFY,
    /** Question cannot be answered from the available local data */
    UNSUPPORTED
}

// ── Query Target ─────────────────────────────────────────────────────

enum class QueryTarget {
    MILK_RECORDS,
    WORKER_PROFILE,
    ORGANIZATION_INFO,
    KNOWLEDGE_BASE
}

// ── Entity Scope & Resolution Status ─────────────────────────────────

enum class EntityScope {
    /** Query targets a specific named farmer (entityMention is set) */
    SPECIFIC,
    /** No entity filter — query applies to all records or uses criteria to select */
    ALL
}

enum class EntityResolutionStatus {
    /** Exactly one matching farmer found in local database */
    RESOLVED,
    /** Multiple candidate farmers match the mention; clarification required */
    AMBIGUOUS,
    /** Entity mention does NOT exist in local farmer registry */
    NOT_FOUND,
    /** Query did not target a specific entity (scope is ALL) */
    NOT_APPLICABLE
}

// ── Schema Fields ────────────────────────────────────────────────────

enum class SchemaField {
    // Farmer identity
    FARMER_NAME,
    FARMER_ID,

    // Milk record measurements
    QUANTITY,
    FAT,
    SNF,

    // Payment fields
    PAYMENT_STATUS,
    PAYMENT_METHOD,
    PAYMENT_REFERENCE,
    PAYMENT_TIMESTAMP,
    PAYABLE_AMOUNT,
    AMOUNT_PAID,

    // Record metadata
    CREATED_AT,
    UPDATED_AT,
    UPLOAD_STATUS;

    companion object {
        fun fromString(name: String): SchemaField? {
            val normalized = name.trim().uppercase().replace(" ", "_")
            return entries.firstOrNull { it.name == normalized }
        }
    }
}

// ── Filter System ────────────────────────────────────────────────────

enum class FilterOperator {
    EQUALS,
    NOT_EQUALS,
    GREATER_THAN,
    GREATER_OR_EQUAL,
    LESS_THAN,
    LESS_OR_EQUAL,
    IS_NULL,
    IS_NOT_NULL,
    CONTAINS,
    IN_RANGE
}

data class QueryFilter(
    val field: SchemaField,
    val operator: FilterOperator,
    val value: String? = null,
    val secondaryValue: String? = null
)

enum class LogicOperator {
    AND,
    OR
}

data class FilterGroup(
    val logic: LogicOperator = LogicOperator.AND,
    val conditions: List<QueryFilter> = emptyList()
)

// ── Temporal System ──────────────────────────────────────────────────

enum class RelativePeriod {
    TODAY,
    YESTERDAY,
    THIS_WEEK,
    LAST_WEEK,
    THIS_MONTH,
    LAST_MONTH,
    SINCE_MONDAY,
    LAST_SUNDAY
}

sealed class TemporalConstraint {
    /** Relative period (e.g. TODAY, THIS_WEEK) resolved dynamically */
    data class Relative(val period: RelativePeriod) : TemporalConstraint()

    /** Single explicit date in ISO YYYY-MM-DD or DD/MM/YYYY */
    data class ExplicitDate(val dateString: String) : TemporalConstraint()

    /** Explicit date range [startDate, endDate] inclusive */
    data class ExplicitRange(val startDate: String, val endDate: String) : TemporalConstraint()
}

// ── Aggregation System ───────────────────────────────────────────────

enum class AggregationType {
    SUM,
    /** Counts individual collection records matching query */
    COUNT_RECORDS,
    /** Counts distinct individual farmers contributing matching records */
    COUNT_DISTINCT_FARMERS,
    AVG,
    MIN,
    MAX
}

/**
 * Specification for a single aggregation operation.
 * Supports multiple aggregations per query (e.g. SUM(quantity) + AVG(fat)).
 */
data class AggregationSpec(
    val type: AggregationType,
    val field: SchemaField? = null
)

/**
 * Filter applied to aggregated group results (HAVING clause).
 * E.g. "Farmers whose total milk this week is greater than 20L".
 */
data class HavingFilter(
    val aggregation: AggregationType,
    val field: SchemaField,
    val operator: FilterOperator,
    val value: Double
)

// ── Ordering System ──────────────────────────────────────────────────

enum class SortDirection { ASC, DESC }

sealed class QueryOrder {
    /** Order individual records by a schema field */
    data class ByField(
        val field: SchemaField,
        val direction: SortDirection = SortDirection.DESC
    ) : QueryOrder()

    /**
     * Order grouped results by a specific computed aggregate metric.
     * Unambiguously specifies both the aggregation function and field.
     */
    data class ByAggregate(
        val aggregationType: AggregationType,
        val field: SchemaField? = null,
        val direction: SortDirection = SortDirection.DESC
    ) : QueryOrder()
}

// ── Payment Scope ────────────────────────────────────────────────────

/**
 * Defines evaluation scope for payment queries.
 */
enum class PaymentScope {
    /** Evaluates the latest recorded payment record (default for status checks) */
    LATEST,
    /** Evaluates across all matching records (e.g. "Are all payments completed?") */
    ALL,
    /** Evaluates whether any record matches (e.g. "Did Suresh receive any payment?") */
    ANY
}

// ── Conditional Query Specification ──────────────────────────────────

/**
 * Structured specification for conditional information requests.
 * Defines state-dependent field reporting based on database truth.
 */
data class ConditionalSpec(
    val conditionField: SchemaField = SchemaField.PAYMENT_STATUS,
    val expectedValue: String = "PAID",
    val fieldsOnMatch: List<SchemaField> = listOf(SchemaField.PAYMENT_METHOD, SchemaField.PAYMENT_REFERENCE),
    val fieldsOnMismatch: List<SchemaField> = listOf(SchemaField.PAYMENT_STATUS, SchemaField.PAYABLE_AMOUNT)
)

// ── The Canonical FreedomQuery ────────────────────────────────────────

/**
 * Canonical FREEDOM Query.
 *
 * Every natural-language question is parsed into exactly one instance of this class.
 * Validated by [QueryValidator] and executed by [FreedomQueryExecutor].
 */
data class FreedomQuery(
    // ── Request Classification ──
    val requestType: RequestType = RequestType.QUERY,
    val target: QueryTarget = QueryTarget.MILK_RECORDS,

    // ── Entity Reference ──
    val entityMention: String? = null,
    val entityScope: EntityScope = EntityScope.ALL,

    // ── Field Selection ──
    val select: List<SchemaField> = emptyList(),

    // ── Filtering ──
    val filters: FilterGroup = FilterGroup(),

    // ── Temporal Constraints ──
    val temporalConstraint: TemporalConstraint? = null,

    // ── Multiple Aggregations ──
    val aggregations: List<AggregationSpec> = emptyList(),

    // ── Grouping ──
    val groupBy: List<SchemaField> = emptyList(),

    // ── Group Aggregate Filter (HAVING) ──
    val havingFilter: HavingFilter? = null,

    // ── Ordering ──
    val orderBy: QueryOrder? = null,

    // ── Limit ──
    val limit: Int? = null,

    // ── Existence Check ──
    val existenceCheck: Boolean = false,

    // ── Payment Scope ──
    val paymentScope: PaymentScope = PaymentScope.LATEST,

    // ── Conditional Reporting ──
    val conditionalSpec: ConditionalSpec? = null,

    // ── Write Operation (requestType == WRITE only) ──
    val writeArgs: Map<String, String> = emptyMap(),

    // ── Knowledge Search (target == KNOWLEDGE_BASE only) ──
    val knowledgeQuery: String? = null,

    // ── Clarification / Unsupported ──
    val clarifyReason: String? = null,
    val unsupportedReason: String? = null
)
