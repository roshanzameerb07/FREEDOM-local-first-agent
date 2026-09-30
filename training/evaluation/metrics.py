"""
Fine-grained Semantic Evaluation Metrics for FreedomQuery Translation.
Computes field-by-field, AST, and structural accuracies.
"""

from typing import Any, Dict, List


class QueryMetrics:
    def __init__(self):
        self.total = 0
        self.exact_ast_matches = 0
        self.request_type_matches = 0
        self.target_matches = 0
        self.entity_matches = 0
        self.select_matches = 0
        self.filter_matches = 0
        self.temporal_matches = 0
        self.aggregation_matches = 0
        self.groupby_matches = 0
        self.orderby_matches = 0
        self.limit_matches = 0
        self.payment_scope_matches = 0
        self.clarify_total = 0
        self.clarify_matches = 0
        self.unsupported_total = 0
        self.unsupported_matches = 0
        self.read_write_total = 0
        self.read_write_matches = 0

    def evaluate_pair(self, pred: Dict[str, Any], target: Dict[str, Any]):
        self.total += 1

        # Request Type
        req_match = (pred.get("type") == target.get("type"))
        if req_match:
            self.request_type_matches += 1

        # Target
        target_match = (pred.get("target") == target.get("target"))
        if target_match:
            self.target_matches += 1

        # Entity
        p_ent = (pred.get("entity") or "").strip().lower()
        t_ent = (target.get("entity") or "").strip().lower()
        ent_match = (p_ent == t_ent and pred.get("entityScope") == target.get("entityScope"))
        if ent_match:
            self.entity_matches += 1

        # Select
        p_sel = set(pred.get("select", []))
        t_sel = set(target.get("select", []))
        sel_match = (p_sel == t_sel)
        if sel_match:
            self.select_matches += 1

        # Filters
        p_fil = pred.get("filters", [])
        t_fil = target.get("filters", [])
        fil_match = (len(p_fil) == len(t_fil))
        if fil_match:
            for pf, tf in zip(p_fil, t_fil):
                if pf.get("field") != tf.get("field") or pf.get("op") != tf.get("op") or str(pf.get("value")) != str(tf.get("value")):
                    fil_match = False
                    break
        if fil_match:
            self.filter_matches += 1

        # Temporal
        p_time = pred.get("time")
        t_time = target.get("time")
        time_match = (p_time == t_time)
        if time_match:
            self.temporal_matches += 1

        # Aggregation
        p_agg = pred.get("aggregations", [])
        t_agg = target.get("aggregations", [])
        agg_match = (p_agg == t_agg)
        if agg_match:
            self.aggregation_matches += 1

        # Group By
        p_gb = set(pred.get("groupBy", []))
        t_gb = set(target.get("groupBy", []))
        gb_match = (p_gb == t_gb)
        if gb_match:
            self.groupby_matches += 1

        # Order By
        p_ob = pred.get("orderBy")
        t_ob = target.get("orderBy")
        ob_match = (p_ob == t_ob)
        if ob_match:
            self.orderby_matches += 1

        # Limit
        p_lim = pred.get("limit")
        t_lim = target.get("limit")
        lim_match = (p_lim == t_lim)
        if lim_match:
            self.limit_matches += 1

        # Payment Scope
        p_ps = pred.get("paymentScope")
        t_ps = target.get("paymentScope")
        ps_match = (p_ps == t_ps)
        if ps_match:
            self.payment_scope_matches += 1

        # Clarification
        if target.get("type") == "CLARIFY":
            self.clarify_total += 1
            if pred.get("type") == "CLARIFY":
                self.clarify_matches += 1

        # Unsupported
        if target.get("type") == "UNSUPPORTED":
            self.unsupported_total += 1
            if pred.get("type") == "UNSUPPORTED":
                self.unsupported_matches += 1

        # Read/Write
        if target.get("type") in {"QUERY", "WRITE"}:
            self.read_write_total += 1
            if pred.get("type") == target.get("type"):
                self.read_write_matches += 1

        # Exact AST Match
        exact_ast = (
            req_match and target_match and ent_match and sel_match and
            fil_match and time_match and agg_match and gb_match and
            ob_match and lim_match and ps_match
        )
        if exact_ast:
            self.exact_ast_matches += 1

    def compute_summary(self) -> Dict[str, Any]:
        t = max(1, self.total)
        return {
            "total_evaluated": self.total,
            "exact_ast_accuracy": round(self.exact_ast_matches / t, 4),
            "request_type_accuracy": round(self.request_type_matches / t, 4),
            "target_accuracy": round(self.target_matches / t, 4),
            "entity_accuracy": round(self.entity_matches / t, 4),
            "select_field_accuracy": round(self.select_matches / t, 4),
            "filter_accuracy": round(self.filter_matches / t, 4),
            "temporal_accuracy": round(self.temporal_matches / t, 4),
            "aggregation_accuracy": round(self.aggregation_matches / t, 4),
            "grouping_accuracy": round(self.groupby_matches / t, 4),
            "ordering_accuracy": round(self.orderby_matches / t, 4),
            "limit_accuracy": round(self.limit_matches / t, 4),
            "payment_scope_accuracy": round(self.payment_scope_matches / t, 4),
            "clarification_accuracy": round(self.clarify_matches / max(1, self.clarify_total), 4) if self.clarify_total else 1.0,
            "unsupported_accuracy": round(self.unsupported_matches / max(1, self.unsupported_total), 4) if self.unsupported_total else 1.0,
            "read_write_accuracy": round(self.read_write_matches / max(1, self.read_write_total), 4) if self.read_write_total else 1.0,
        }
