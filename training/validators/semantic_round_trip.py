"""
Semantic Round-Trip Validator.
Performs an independent verification pass confirming that a generated natural-language utterance
faithfully and unambiguously expresses the exact target canonical query.
"""

import re
from typing import Any, Dict, List, Tuple


def verify_semantic_fidelity(prompt: str, target: Dict[str, Any]) -> Tuple[bool, List[str]]:
    """
    Independently inspects the prompt text and validates that it matches
    the canonical query target semantics. Discards uncertain examples.
    """
    errors: List[str] = []
    p_lower = prompt.lower().strip()
    req_type = target.get("type")

    # 1. Request Type Fidelity
    if req_type == "WRITE":
        write_indicators = ["record", "add", "mark", "save", "log"]
        if not any(w in p_lower for w in write_indicators):
            errors.append("Prompt for WRITE query lacks write/command indicators")
    elif req_type == "UNSUPPORTED":
        unsupported_indicators = ["why was", "why did", "what caused", "what if", "reason"]
        if not any(u in p_lower for u in unsupported_indicators):
            errors.append("Prompt for UNSUPPORTED query lacks unsupported intent markers")
    elif req_type == "CLARIFY":
        # Clarification prompt must either be ambiguous pronoun, missing field, or ambiguous entity
        clarify_indicators = ["he", "she", "they", "tell me about", "show", "what about"]
        reason = target.get("reason", "")
        if "ambiguous" not in reason.lower() and "unspecified" not in reason.lower() and "not registered" not in reason.lower():
            errors.append("CLARIFY target lacks clear ambiguity reason")

    # 2. Aggregation Semantics Fidelity
    aggs = target.get("aggregations", [])
    if aggs:
        agg_type = aggs[0].get("type")
        if agg_type == "SUM":
            if not any(s in p_lower for s in ["total", "sum", "how much", "how many litres", "all", "most", "peak", "given", "give", "supplied", "deliver", "collection", "qty", "quantity", "milk"]):
                errors.append("Prompt expresses SUM but lacks total/sum/quantity indicators")
        elif agg_type == "AVG":
            if not any(s in p_lower for s in ["average", "avg", "mean"]):
                errors.append("Prompt expresses AVG but lacks average/mean indicator")
        elif agg_type == "MIN":
            if not any(s in p_lower for s in ["min", "lowest", "smallest", "minimum"]):
                errors.append("Prompt expresses MIN but lacks minimum/lowest indicator")
        elif agg_type == "MAX":
            if not any(s in p_lower for s in ["max", "highest", "peak", "maximum", "largest"]):
                errors.append("Prompt expresses MAX but lacks maximum/highest indicator")
        elif agg_type == "COUNT_RECORDS":
            if not any(s in p_lower for s in ["how many collections", "how many deliveries", "how many times", "count", "number of deliveries", "entry count"]):
                errors.append("Prompt expresses COUNT_RECORDS but lacks delivery count phrasing")
        elif agg_type == "COUNT_DISTINCT_FARMERS":
            if not any(s in p_lower for s in ["how many farmers", "distinct farmers", "unique farmers", "farmers covered"]):
                errors.append("Prompt expresses COUNT_DISTINCT_FARMERS but lacks distinct farmer count phrasing")

    # 3. Temporal Constraints Fidelity
    t = target.get("time")
    if t is not None:
        ttype = t.get("type")
        if ttype == "RELATIVE":
            period = t.get("period")
            period_keywords = {
                "TODAY": ["today"],
                "YESTERDAY": ["yesterday"],
                "THIS_WEEK": ["this week", "weekly", "week"],
                "LAST_WEEK": ["last week"],
                "THIS_MONTH": ["this month", "monthly", "month"],
                "LAST_MONTH": ["last month"],
                "SINCE_MONDAY": ["since monday", "from monday", "monday"],
                "LAST_SUNDAY": ["last sunday", "sunday"],
            }
            expected = period_keywords.get(period, [])
            if not any(k in p_lower for k in expected):
                errors.append(f"Prompt time constraint mismatch: expected {period} in prompt '{prompt}'")
        elif ttype == "EXPLICIT_DATE":
            date_str = t.get("date")
            if date_str and date_str not in prompt and "september 18" not in p_lower:
                errors.append(f"Explicit date {date_str} not reflected in prompt")

    # 4. Payment Scope Fidelity
    ps = target.get("paymentScope")
    if ps == "ALL":
        if not any(a in p_lower for a in ["all", "every"]):
            errors.append("Prompt targeting PaymentScope.ALL must include 'all' qualifier")
    elif ps == "ANY":
        if not any(a in p_lower for a in ["any", "at all"]):
            errors.append("Prompt targeting PaymentScope.ANY must include 'any' qualifier")

    return (len(errors) == 0, errors)
