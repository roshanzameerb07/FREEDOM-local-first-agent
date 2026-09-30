"""
Dataset Validator for FREEDOM AI Training.
Performs comprehensive structural, semantic, and deduplication verification.
"""

import json
from typing import Any, Dict, List, Tuple
from validators.query_validator import validate_canonical_query


class ValidationReport:
    def __init__(self):
        self.total_checked = 0
        self.valid_count = 0
        self.rejected_count = 0
        self.rejection_reasons: Dict[str, int] = {}
        self.category_counts: Dict[str, int] = {}
        self.register_counts: Dict[str, int] = {}
        self.seen_prompts: set = set()

    def record_rejection(self, reason: str):
        self.rejected_count += 1
        self.rejection_reasons[reason] = self.rejection_reasons.get(reason, 0) + 1

    def record_valid(self, category: str, register: str):
        self.valid_count += 1
        self.category_counts[category] = self.category_counts.get(category, 0) + 1
        self.register_counts[register] = self.register_counts.get(register, 0) + 1

    def summary(self) -> Dict[str, Any]:
        return {
            "total_checked": self.total_checked,
            "valid_count": self.valid_count,
            "rejected_count": self.rejected_count,
            "rejection_rate": round(self.rejected_count / max(1, self.total_checked), 4),
            "rejection_reasons": self.rejection_reasons,
            "category_distribution": self.category_counts,
            "register_distribution": self.register_counts,
        }


def validate_example(example: Dict[str, Any], report: ValidationReport) -> Tuple[bool, List[str]]:
    """
    Validates a single dataset example: { "prompt": ..., "target": ..., "metadata": ... }
    """
    report.total_checked += 1
    errors: List[str] = []

    prompt = example.get("prompt", "")
    target = example.get("target", {})
    meta = example.get("metadata", {})

    # 1. Prompt validity
    if not isinstance(prompt, str) or not prompt.strip():
        errors.append("Empty or non-string prompt")

    # 2. Duplicate prompt detection
    norm_p = prompt.strip().lower()
    if norm_p in report.seen_prompts:
        errors.append("Duplicate prompt")
    report.seen_prompts.add(norm_p)

    # 3. Target AST validation
    is_valid_query, query_errors = validate_canonical_query(target)
    if not is_valid_query:
        errors.extend(query_errors)

    # 4. Semantic alignment check
    req_type = target.get("type")
    if req_type == "WRITE":
        if "record" not in norm_p and "add" not in norm_p and "mark" not in norm_p and "save" not in norm_p:
            errors.append("WRITE target assigned to non-command prompt")

    if errors:
        for err in errors:
            report.record_rejection(err)
        return False, errors

    cat = meta.get("category", "unspecified")
    reg = meta.get("register", "standard")
    report.record_valid(cat, reg)
    return True, []
