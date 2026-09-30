"""
Data Contamination and Leakage Detector.
Detects:
1. Exact prompt overlaps across train, validation, test, and hard_test.
2. Entity name contamination in held-out test splits.
3. High n-gram / Jaccard token similarity (near-duplicate leakage).
"""

import re
from typing import Any, Dict, List, Set, Tuple


def tokenize(text: str) -> Set[str]:
    words = re.findall(r"\w+", text.lower())
    return set(words)


def jaccard_similarity(s1: Set[str], s2: Set[str]) -> float:
    if not s1 or not s2:
        return 0.0
    intersection = len(s1 & s2)
    union = len(s1 | s2)
    return intersection / union if union > 0 else 0.0


class LeakageReport:
    def __init__(self):
        self.exact_overlaps: List[Tuple[str, str, str]] = []  # (splitA, splitB, prompt)
        self.entity_leaks: List[Tuple[str, str, str]] = []    # (entity, test_split, train_prompt)
        self.near_duplicate_leaks: List[Tuple[str, str, float, str, str]] = []  # (splitA, splitB, sim, pA, pB)

    def is_clean(self) -> bool:
        return (len(self.exact_overlaps) == 0 and
                len(self.entity_leaks) == 0 and
                len(self.near_duplicate_leaks) == 0)

    def summary(self) -> Dict[str, Any]:
        return {
            "is_clean": self.is_clean(),
            "exact_overlaps_count": len(self.exact_overlaps),
            "entity_leaks_count": len(self.entity_leaks),
            "near_duplicates_count": len(self.near_duplicate_leaks),
            "sample_exact_overlaps": self.exact_overlaps[:5],
            "sample_entity_leaks": self.entity_leaks[:5],
            "sample_near_duplicates": self.near_duplicate_leaks[:5],
        }


def check_leakage(
    train_data: List[Dict[str, Any]],
    val_data: List[Dict[str, Any]],
    test_data: List[Dict[str, Any]],
    hard_test_data: List[Dict[str, Any]],
    held_out_entities: Set[str],
) -> LeakageReport:
    report = LeakageReport()

    splits = {
        "train": [d["prompt"] for d in train_data],
        "validation": [d["prompt"] for d in val_data],
        "test": [d["prompt"] for d in test_data],
        "hard_test": [d["prompt"] for d in hard_test_data],
    }

    # 1. Exact string overlap across splits
    split_names = list(splits.keys())
    for i in range(len(split_names)):
        for j in range(i + 1, len(split_names)):
            sA, sB = split_names[i], split_names[j]
            setA = set(p.strip().lower() for p in splits[sA])
            setB = set(p.strip().lower() for p in splits[sB])
            common = setA & setB
            for c in common:
                report.exact_overlaps.append((sA, sB, c))

    # 2. Entity contamination: Held-out test entities must NOT appear in train
    for item in train_data:
        p = item["prompt"].lower()
        target_entity = (item.get("target", {}).get("entity") or "").lower()
        for held_out in held_out_entities:
            h_low = held_out.lower()
            if h_low and (h_low in p or h_low == target_entity):
                report.entity_leaks.append((held_out, "test", item["prompt"]))

    # 3. Near-duplicate check (sample-based high token Jaccard similarity > 0.90 between train and test)
    # Check random subset if test dataset is large to maintain performance
    train_tokens = [(p, tokenize(p)) for p in splits["train"][:1000]]
    test_tokens = [(p, tokenize(p)) for p in splits["test"][:500]]

    for t_prompt, t_tok in test_tokens:
        for tr_prompt, tr_tok in train_tokens:
            sim = jaccard_similarity(t_tok, tr_tok)
            if sim >= 0.92:
                # Same question template with identical words
                report.near_duplicate_leaks.append(("test", "train", round(sim, 3), t_prompt, tr_prompt))
                if len(report.near_duplicate_leaks) > 20:
                    break
        if len(report.near_duplicate_leaks) > 20:
            break

    return report
