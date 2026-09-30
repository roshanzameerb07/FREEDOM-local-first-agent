"""
Semantic Query Generator for FREEDOM Canonical DSL.
Generates structured query specifications across all 46 supported semantic categories.
Ensures every query is valid according to the frozen schema before linguistic realization.
"""

from typing import Any, Dict, List, Optional
from generators.schema_encoder import canonicalize_query
from generators.entity_vocabulary import (
    TRAIN_NAMES, VAL_NAMES, TEST_NAMES, HARD_TEST_NAMES, AMBIGUOUS_PAIRS
)


class SemanticQueryTemplate:
    def __init__(self, category_id: int, category_name: str, query_builder, linguistic_templates):
        self.category_id = category_id
        self.category_name = category_name
        self.query_builder = query_builder
        self.linguistic_templates = linguistic_templates


def make_query_template(
    req_type: str = "QUERY",
    target: str = "MILK_RECORDS",
    entity: Optional[str] = None,
    entity_scope: Optional[str] = None,
    select: Optional[List[str]] = None,
    filters: Optional[List[Dict[str, Any]]] = None,
    filter_logic: str = "AND",
    time_val: Optional[Dict[str, Any]] = None,
    aggregations: Optional[List[Dict[str, Any]]] = None,
    group_by: Optional[List[str]] = None,
    having: Optional[Dict[str, Any]] = None,
    order_by: Optional[Dict[str, Any]] = None,
    limit: Optional[int] = None,
    exists: bool = False,
    payment_scope: str = "LATEST",
    conditional_spec: Optional[Dict[str, Any]] = None,
    write_args: Optional[Dict[str, str]] = None,
    knowledge_query: Optional[str] = None,
    reason: Optional[str] = None,
) -> Dict[str, Any]:
    """Helper constructing canonical query dictionary."""
    scope = entity_scope or ("SPECIFIC" if entity else "ALL")
    raw = {
        "type": req_type,
        "target": target,
        "entity": entity,
        "entityScope": scope,
        "select": select or [],
        "filters": filters or [],
        "filterLogic": filter_logic,
        "time": time_val,
        "aggregations": aggregations or [],
        "groupBy": group_by or [],
        "havingFilter": having,
        "orderBy": order_by,
        "limit": limit,
        "exists": exists,
        "paymentScope": payment_scope,
        "conditionalSpec": conditional_spec,
        "writeArgs": write_args or {},
        "knowledgeQuery": knowledge_query,
        "reason": reason,
    }
    return canonicalize_query(raw)
