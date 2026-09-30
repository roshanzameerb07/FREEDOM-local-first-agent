"""
Programmatic schema encoder and canonical JSON serializer for FREEDOM Query DSL.
Guarantees zero drift between Kotlin Room implementation and dataset generation.
"""

import json
from pathlib import Path
from typing import Any, Dict, List, Optional

SCHEMA_DIR = Path(__file__).resolve().parent.parent / "schema"
SCHEMA_FILE = SCHEMA_DIR / "freedom_query_schema.json"
SEMANTICS_FILE = SCHEMA_DIR / "freedom_query_semantics.json"

with open(SCHEMA_FILE, "r", encoding="utf-8") as f:
    RAW_SCHEMA = json.load(f)

with open(SEMANTICS_FILE, "r", encoding="utf-8") as f:
    RAW_SEMANTICS = json.load(f)

FIELDS = set(RAW_SEMANTICS["fields"].keys())
NUMERIC_FIELDS = {k for k, v in RAW_SEMANTICS["fields"].items() if v.get("numeric", False)}
TEMPORAL_FIELDS = {k for k, v in RAW_SEMANTICS["fields"].items() if v.get("temporal", False)}
GROUPABLE_FIELDS = {k for k, v in RAW_SEMANTICS["fields"].items() if v.get("groupable", False)}
SORTABLE_FIELDS = {k for k, v in RAW_SEMANTICS["fields"].items() if v.get("sortable", False)}

AGGREGATIONS = set(RAW_SEMANTICS["aggregations"].keys())
RELATIVE_PERIODS = set(RAW_SEMANTICS["temporalTokens"]["RELATIVE"])
PAYMENT_SCOPES = set(RAW_SEMANTICS["paymentScopes"].keys())

CANONICAL_KEY_ORDER = [
    "type",
    "target",
    "entity",
    "entityScope",
    "select",
    "filters",
    "filterLogic",
    "time",
    "aggregations",
    "groupBy",
    "havingFilter",
    "orderBy",
    "limit",
    "exists",
    "paymentScope",
    "conditionalSpec",
    "writeArgs",
    "knowledgeQuery",
    "reason",
]


def canonicalize_query(query: Dict[str, Any]) -> Dict[str, Any]:
    """
    Produces a deterministically ordered, normalized, canonical JSON representation
    of a FreedomQuery object.
    """
    out: Dict[str, Any] = {}

    out["type"] = query.get("type", "QUERY")
    out["target"] = query.get("target", "MILK_RECORDS")
    out["entity"] = query.get("entity", None)
    out["entityScope"] = query.get("entityScope", "ALL" if out["entity"] is None else "SPECIFIC")

    # Select: sorted if unordered list
    raw_select = query.get("select", [])
    out["select"] = sorted(list(set(raw_select)))

    # Filters: sorted by field and op for determinism
    raw_filters = query.get("filters", [])
    norm_filters = []
    for f in raw_filters:
        norm_filters.append({
            "field": f["field"],
            "op": f["op"],
            "value": f.get("value", None),
            "secondaryValue": f.get("secondaryValue", None),
        })
    norm_filters.sort(key=lambda x: (x["field"], x["op"], str(x["value"])))
    out["filters"] = norm_filters
    out["filterLogic"] = query.get("filterLogic", "AND")

    # Time constraint
    t = query.get("time", None)
    if t is not None:
        ttype = t.get("type", "RELATIVE")
        if ttype == "RELATIVE":
            out["time"] = {
                "type": "RELATIVE",
                "period": t.get("period", "TODAY"),
                "date": None,
                "startDate": None,
                "endDate": None,
            }
        elif ttype == "EXPLICIT_DATE":
            out["time"] = {
                "type": "EXPLICIT_DATE",
                "period": None,
                "date": t.get("date"),
                "startDate": None,
                "endDate": None,
            }
        elif ttype == "EXPLICIT_RANGE":
            out["time"] = {
                "type": "EXPLICIT_RANGE",
                "period": None,
                "date": None,
                "startDate": t.get("startDate"),
                "endDate": t.get("endDate"),
            }
    else:
        out["time"] = None

    # Aggregations
    raw_aggs = query.get("aggregations", [])
    norm_aggs = []
    for a in raw_aggs:
        norm_aggs.append({
            "type": a["type"],
            "field": a.get("field", None),
        })
    # Preserve semantic order of aggregations if multiple, but normalize keys
    out["aggregations"] = norm_aggs

    # Group By
    out["groupBy"] = sorted(list(set(query.get("groupBy", []))))

    # Having Filter
    hf = query.get("havingFilter", None)
    if hf is not None:
        out["havingFilter"] = {
            "agg": hf["agg"],
            "field": hf["field"],
            "op": hf["op"],
            "value": float(hf["value"]),
        }
    else:
        out["havingFilter"] = None

    # Order By
    ob = query.get("orderBy", None)
    if ob is not None:
        out["orderBy"] = {
            "target": ob["target"],
            "field": ob.get("field", None),
            "aggregation": ob.get("aggregation", None),
            "dir": ob.get("dir", "DESC"),
        }
    else:
        out["orderBy"] = None

    out["limit"] = query.get("limit", None)
    out["exists"] = bool(query.get("exists", False))
    out["paymentScope"] = query.get("paymentScope", "LATEST")

    # Conditional Spec
    cs = query.get("conditionalSpec", None)
    if cs is not None:
        out["conditionalSpec"] = {
            "conditionField": cs.get("conditionField", "PAYMENT_STATUS"),
            "expectedValue": cs.get("expectedValue", "PAID"),
            "fieldsOnMatch": sorted(cs.get("fieldsOnMatch", ["PAYMENT_METHOD", "PAYMENT_REFERENCE"])),
            "fieldsOnMismatch": sorted(cs.get("fieldsOnMismatch", ["PAYMENT_STATUS", "PAYABLE_AMOUNT"])),
        }
    else:
        out["conditionalSpec"] = None

    # Write Args
    wa = query.get("writeArgs", {})
    if wa:
        out["writeArgs"] = {k: str(v) for k, v in sorted(wa.items())}
    else:
        out["writeArgs"] = {}

    out["knowledgeQuery"] = query.get("knowledgeQuery", None)
    out["reason"] = query.get("reason", None)

    # Reorder according to canonical keys
    ordered = {k: out[k] for k in CANONICAL_KEY_ORDER}
    return ordered


def to_canonical_json(query: Dict[str, Any]) -> str:
    """Serializes canonicalized query to strict, deterministic JSON string."""
    return json.dumps(canonicalize_query(query), ensure_ascii=False, indent=2)
