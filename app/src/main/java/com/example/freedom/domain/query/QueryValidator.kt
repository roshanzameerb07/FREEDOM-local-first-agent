package com.example.freedom.domain.query

/**
 * Validates a [FreedomQuery] before execution.
 *
 * Ensures:
 * - All referenced [SchemaField]s are valid for the requested operation
 * - Aggregation fields are numeric
 * - Multiple aggregations are valid
 * - Filter operators are compatible with field types
 * - GroupBy fields are valid for grouping
 * - HAVING filters and ORDER BY AGGREGATE require an active GROUP BY
 * - Temporal constraints are well-formed
 * - Write operations have required arguments
 * - Limits are positive
 *
 * Returns a [ValidationResult] with all errors found (not just the first).
 */
object QueryValidator {

    data class ValidationResult(
        val isValid: Boolean,
        val errors: List<String> = emptyList()
    )

    /** Fields that contain numeric values and support arithmetic aggregation */
    private val NUMERIC_FIELDS = setOf(
        SchemaField.QUANTITY,
        SchemaField.FAT,
        SchemaField.SNF,
        SchemaField.PAYABLE_AMOUNT,
        SchemaField.AMOUNT_PAID
    )

    /** Fields that contain epoch-ms timestamps and support temporal filtering */
    private val TEMPORAL_FIELDS = setOf(
        SchemaField.CREATED_AT,
        SchemaField.UPDATED_AT,
        SchemaField.PAYMENT_TIMESTAMP
    )

    /** Fields that can be used for grouping */
    private val GROUPABLE_FIELDS = setOf(
        SchemaField.FARMER_NAME,
        SchemaField.FARMER_ID,
        SchemaField.PAYMENT_STATUS,
        SchemaField.PAYMENT_METHOD,
        SchemaField.UPLOAD_STATUS
    )

    /** Fields that can be sorted */
    private val SORTABLE_FIELDS = NUMERIC_FIELDS + TEMPORAL_FIELDS + setOf(
        SchemaField.FARMER_NAME,
        SchemaField.PAYMENT_STATUS,
        SchemaField.PAYMENT_METHOD,
        SchemaField.UPLOAD_STATUS
    )

    /** Required write args for a milk record creation */
    private val REQUIRED_WRITE_ARGS = setOf("farmerName", "quantity")

    fun validate(query: FreedomQuery): ValidationResult {
        val errors = mutableListOf<String>()

        when (query.requestType) {
            RequestType.QUERY -> validateReadQuery(query, errors)
            RequestType.WRITE -> validateWriteQuery(query, errors)
            RequestType.CLARIFY -> {
                if (query.clarifyReason.isNullOrBlank()) {
                    errors.add("CLARIFY request must include a clarifyReason")
                }
            }
            RequestType.UNSUPPORTED -> {
                if (query.unsupportedReason.isNullOrBlank()) {
                    errors.add("UNSUPPORTED request must include an unsupportedReason")
                }
            }
        }

        return ValidationResult(
            isValid = errors.isEmpty(),
            errors = errors
        )
    }

    private fun validateReadQuery(query: FreedomQuery, errors: MutableList<String>) {
        // Validate entity scope consistency
        if (query.entityScope == EntityScope.SPECIFIC && query.entityMention.isNullOrBlank()) {
            errors.add("SPECIFIC entity scope requires a non-blank entityMention")
        }

        // Validate aggregations
        for (agg in query.aggregations) {
            if (agg.type != AggregationType.COUNT_RECORDS && agg.type != AggregationType.COUNT_DISTINCT_FARMERS) {
                if (agg.field == null) {
                    errors.add("Aggregation ${agg.type} requires an aggregationField")
                } else if (agg.field !in NUMERIC_FIELDS) {
                    errors.add("Cannot apply ${agg.type} to non-numeric field ${agg.field.name}")
                }
            }
        }

        // Validate filters
        for (filter in query.filters.conditions) {
            validateFilter(filter, errors)
        }

        // Validate groupBy fields
        for (field in query.groupBy) {
            if (field !in GROUPABLE_FIELDS) {
                errors.add("Cannot GROUP BY ${field.name}; only categorical fields are groupable")
            }
        }

        // Validate HAVING filter requires GROUP BY
        if (query.havingFilter != null) {
            if (query.groupBy.isEmpty()) {
                errors.add("HAVING filter requires an active GROUP BY clause")
            }
            if (query.havingFilter.field !in NUMERIC_FIELDS) {
                errors.add("HAVING filter field ${query.havingFilter.field.name} must be numeric")
            }
        }

        // Validate orderBy
        when (val order = query.orderBy) {
            is QueryOrder.ByField -> {
                if (order.field !in SORTABLE_FIELDS) {
                    errors.add("Cannot sort by ${order.field.name}")
                }
            }
            is QueryOrder.ByAggregate -> {
                if (query.groupBy.isEmpty()) {
                    errors.add("Sorting by aggregate requires an active GROUP BY clause")
                }
                if (query.aggregations.isEmpty()) {
                    errors.add("Sorting by aggregate requires at least one aggregation specification")
                }
            }
            null -> { /* no order specified */ }
        }

        // Validate temporal constraint
        when (val tc = query.temporalConstraint) {
            is TemporalConstraint.ExplicitDate -> {
                if (TemporalResolver.resolveExplicitDate(tc.dateString) == null) {
                    errors.add("Invalid explicit date format: '${tc.dateString}'. Expected yyyy-MM-dd or dd/MM/yyyy")
                }
            }
            is TemporalConstraint.ExplicitRange -> {
                if (TemporalResolver.resolveExplicitRange(tc.startDate, tc.endDate) == null) {
                    errors.add("Invalid explicit date range: '${tc.startDate}' to '${tc.endDate}'. Expected yyyy-MM-dd or dd/MM/yyyy")
                }
            }
            is TemporalConstraint.Relative, null -> { /* valid */ }
        }

        // Validate limit
        if (query.limit != null && query.limit <= 0) {
            errors.add("Limit must be a positive integer, got ${query.limit}")
        }

        // Validate knowledge search
        if (query.target == QueryTarget.KNOWLEDGE_BASE && query.knowledgeQuery.isNullOrBlank()) {
            errors.add("KNOWLEDGE_BASE target requires a non-blank knowledgeQuery")
        }
    }

    private fun validateWriteQuery(query: FreedomQuery, errors: MutableList<String>) {
        if (query.writeArgs.isEmpty()) {
            errors.add("WRITE request must include writeArgs")
            return
        }
        for (required in REQUIRED_WRITE_ARGS) {
            if (query.writeArgs[required].isNullOrBlank()) {
                errors.add("WRITE request missing required argument: $required")
            }
        }
    }

    private fun validateFilter(filter: QueryFilter, errors: MutableList<String>) {
        when (filter.operator) {
            FilterOperator.IS_NULL, FilterOperator.IS_NOT_NULL -> {
                // No value needed
            }
            FilterOperator.IN_RANGE -> {
                if (filter.value == null || filter.secondaryValue == null) {
                    errors.add("IN_RANGE filter on ${filter.field.name} requires both value and secondaryValue")
                }
                if (filter.field !in NUMERIC_FIELDS && filter.field !in TEMPORAL_FIELDS) {
                    errors.add("IN_RANGE is only valid for numeric or temporal fields, not ${filter.field.name}")
                }
            }
            FilterOperator.GREATER_THAN, FilterOperator.GREATER_OR_EQUAL,
            FilterOperator.LESS_THAN, FilterOperator.LESS_OR_EQUAL -> {
                if (filter.field !in NUMERIC_FIELDS && filter.field !in TEMPORAL_FIELDS) {
                    errors.add("Comparison operator ${filter.operator.name} is only valid for numeric/temporal fields, not ${filter.field.name}")
                }
                if (filter.value == null) {
                    errors.add("Comparison filter on ${filter.field.name} requires a value")
                }
            }
            FilterOperator.CONTAINS -> {
                if (filter.field in NUMERIC_FIELDS || filter.field in TEMPORAL_FIELDS) {
                    errors.add("CONTAINS is not valid for numeric/temporal field ${filter.field.name}")
                }
                if (filter.value.isNullOrBlank()) {
                    errors.add("CONTAINS filter on ${filter.field.name} requires a non-blank value")
                }
            }
            FilterOperator.EQUALS, FilterOperator.NOT_EQUALS -> {
                if (filter.value == null) {
                    errors.add("${filter.operator.name} filter on ${filter.field.name} requires a value")
                }
            }
        }
    }

    fun isNumericField(field: SchemaField): Boolean = field in NUMERIC_FIELDS
    fun isTemporalField(field: SchemaField): Boolean = field in TEMPORAL_FIELDS
}
