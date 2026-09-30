"""
Strict AST and Semantic Validator for Canonical FreedomQuery objects.
Matches Kotlin QueryValidator.kt invariants exactly.
"""

from typing import Any, Dict, List, Tuple
from generators.schema_encoder import (
    FIELDS,
    NUMERIC_FIELDS,
    TEMPORAL_FIELDS,
    GROUPABLE_FIELDS,
    SORTABLE_FIELDS,
    AGGREGATIONS,
    RELATIVE_PERIODS,
    PAYMENT_SCOPES,
)

VALID_REQUEST_TYPES = {"QUERY", "WRITE", "CLARIFY", "UNSUPPORTED"}
VALID_TARGETS = {"MILK_RECORDS", "WORKER_PROFILE", "ORGANIZATION_INFO", "KNOWLEDGE_BASE"}
VALID_FILTER_OPS = {
    "EQUALS", "NOT_EQUALS", "GREATER_THAN", "GREATER_OR_EQUAL",
    "LESS_THAN", "LESS_OR_EQUAL", "IS_NULL", "IS_NOT_NULL", "CONTAINS", "IN_RANGE"
}


def validate_canonical_query(query: Dict[str, Any]) -> Tuple[bool, List[str]]:
    """
    Validates a canonical query dictionary against frozen Phase 1 semantics.
    Returns (is_valid, list_of_errors).
    """
    errors: List[str] = []

    # 1. Request Type & Target
    req_type = query.get("type")
    if req_type not in VALID_REQUEST_TYPES:
        errors.append(f"Invalid request type: {req_type}")

    target = query.get("target")
    if target not in VALID_TARGETS:
        errors.append(f"Invalid target: {target}")

    # 2. Entity Scope Consistency
    entity = query.get("entity")
    entity_scope = query.get("entityScope")
    if entity_scope == "SPECIFIC" and (entity is None or str(entity).strip() == ""):
        errors.append("SPECIFIC entityScope requires non-empty entity name")
    if entity_scope == "ALL" and entity is not None:
        errors.append("ALL entityScope must have null entity")

    # 3. Select Fields
    select = query.get("select", [])
    for s in select:
        if s not in FIELDS:
            errors.append(f"Invalid select field: {s}")

    # 4. Filter validation
    filters = query.get("filters", [])
    for f in filters:
        fld = f.get("field")
        op = f.get("op")
        if fld not in FIELDS:
            errors.append(f"Filter references unknown field: {fld}")
        if op not in VALID_FILTER_OPS:
            errors.append(f"Filter references unknown operator: {op}")
        if op in {"GREATER_THAN", "GREATER_OR_EQUAL", "LESS_THAN", "LESS_OR_EQUAL", "IN_RANGE"}:
            if fld not in NUMERIC_FIELDS and fld not in TEMPORAL_FIELDS:
                errors.append(f"Relational operator {op} applied to non-ordered field {fld}")

    # 5. Temporal validation
    time_val = query.get("time")
    if time_val is not None:
        t_type = time_val.get("type")
        if t_type not in {"RELATIVE", "EXPLICIT_DATE", "EXPLICIT_RANGE"}:
            errors.append(f"Invalid temporal type: {t_type}")
        if t_type == "RELATIVE":
            p = time_val.get("period")
            if p not in RELATIVE_PERIODS:
                errors.append(f"Invalid relative period: {p}")
        elif t_type == "EXPLICIT_DATE":
            d = time_val.get("date")
            if not d:
                errors.append("EXPLICIT_DATE requires non-empty date string")
        elif t_type == "EXPLICIT_RANGE":
            sd = time_val.get("startDate")
            ed = time_val.get("endDate")
            if not sd or not ed:
                errors.append("EXPLICIT_RANGE requires startDate and endDate")

    # 6. Aggregation validation
    aggs = query.get("aggregations", [])
    for a in aggs:
        agg_type = a.get("type")
        agg_field = a.get("field")
        if agg_type not in AGGREGATIONS:
            errors.append(f"Invalid aggregation type: {agg_type}")
        if agg_type in {"COUNT_RECORDS", "COUNT_DISTINCT_FARMERS"}:
            if agg_field is not None:
                errors.append(f"{agg_type} must not have a target field (must be null)")
        elif agg_type in {"SUM", "AVG", "MIN", "MAX"}:
            if agg_field is None:
                errors.append(f"{agg_type} requires a numeric target field")
            elif agg_field not in NUMERIC_FIELDS:
                errors.append(f"Cannot apply {agg_type} to non-numeric field: {agg_field}")

    # 7. Group By validation
    group_by = query.get("groupBy", [])
    for gb in group_by:
        if gb not in GROUPABLE_FIELDS:
            errors.append(f"Cannot GROUP BY non-groupable field: {gb}")

    # 8. Having Filter validation
    having = query.get("havingFilter")
    if having is not None:
        if not group_by:
            errors.append("HAVING filter requires active GROUP BY")
        h_agg = having.get("agg")
        h_fld = having.get("field")
        if h_agg not in AGGREGATIONS:
            errors.append(f"Invalid HAVING aggregation: {h_agg}")
        if h_fld not in NUMERIC_FIELDS:
            errors.append(f"HAVING filter field must be numeric: {h_fld}")

    # 9. Order By validation
    order_by = query.get("orderBy")
    if order_by is not None:
        target_t = order_by.get("target")
        if target_t == "FIELD":
            f_sort = order_by.get("field")
            if f_sort not in SORTABLE_FIELDS:
                errors.append(f"Cannot sort by unsortable field: {f_sort}")
        elif target_t == "AGGREGATE":
            if not group_by:
                errors.append("Sorting by aggregate requires active GROUP BY")
            if not aggs and not order_by.get("aggregation"):
                errors.append("Sorting by aggregate requires explicit aggregate specification")
        else:
            errors.append(f"Invalid orderBy target: {target_t}")

    # 10. Limit validation
    limit = query.get("limit")
    if limit is not None and (not isinstance(limit, int) or limit <= 0):
        errors.append(f"Limit must be a positive integer: {limit}")

    # 11. Payment Scope validation
    ps = query.get("paymentScope")
    if ps not in PAYMENT_SCOPES:
        errors.append(f"Invalid paymentScope: {ps}")

    # 12. WriteArgs validation
    if req_type == "WRITE":
        wa = query.get("writeArgs", {})
        if "farmerName" not in wa or "quantity" not in wa:
            errors.append("WRITE query requires 'farmerName' and 'quantity' in writeArgs")

    # 13. Clarify / Unsupported reason validation
    if req_type == "CLARIFY" and not query.get("reason"):
        errors.append("CLARIFY query must specify reason")
    if req_type == "UNSUPPORTED" and not query.get("reason"):
        errors.append("UNSUPPORTED query must specify reason")

    return (len(errors) == 0, errors)
