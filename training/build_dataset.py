"""
Master Dataset Engineering Pipeline for FREEDOM AI (Qwen3 1.7B).
Rebalanced Phase 2 Dataset Generation:
1. Pilot Dataset (500 balanced examples across all 46 categories)
2. Scaled Multi-Split Dataset (8,500 balanced examples):
   - train.jsonl (5,950 examples, Cat 36 rebalanced to 135 (~2.3%), Cat 46 strictly held out)
   - validation.jsonl (850 examples, 18 per Cat 1-45, 40 for Cat 46)
   - test.jsonl (850 examples, 18 per Cat 1-45, 40 for Cat 46, held-out entities)
   - hard_test.jsonl (850 examples, 18 per Cat 1-45, 40 for Cat 46, orthographic variations & complex compositions)
Grand Total: 9,000 balanced, schema-valid, semantically-faithful examples.
"""

import argparse
import json
import random
from pathlib import Path
from typing import Any, Dict, List, Set, Tuple

from generators.entity_vocabulary import (
    TRAIN_NAMES, VAL_NAMES, TEST_NAMES, HARD_TEST_NAMES,
    AMBIGUOUS_PAIRS, apply_orthographic_variation
)
from generators.linguistic_realizer import generate_category_examples, QueryRealization
from validators.dataset_validator import ValidationReport, validate_example
from validators.semantic_round_trip import verify_semantic_fidelity
from leakage.leakage_detector import check_leakage

BASE_DIR = Path(__file__).resolve().parent
DATASET_DIR = BASE_DIR / "dataset"
REPORTS_DIR = BASE_DIR / "reports"


def get_pilot_quotas() -> Dict[int, int]:
    """Returns exact quotas for pilot dataset (500 examples)."""
    quotas = {}
    for c in range(1, 41):
        quotas[c] = 11  # 40 * 11 = 440
    for c in range(41, 46):
        quotas[c] = 10  # 5 * 10 = 50
    quotas[46] = 10     # 10
    return quotas


def get_train_quotas() -> Dict[int, int]:
    """Returns exact quotas for train dataset (5,950 examples)."""
    quotas = {}
    for c in range(1, 8):
        quotas[c] = 133  # 7 * 133 = 931
    for c in range(8, 36):
        quotas[c] = 132  # 28 * 132 = 3696
    quotas[36] = 135     # Cat 36: rebalanced from 2,712 down to 135 (~2.27%)
    for c in range(37, 46):
        quotas[c] = 132  # 9 * 132 = 1188
    quotas[46] = 0       # Cat 46: strictly held out from train!
    return quotas


def get_eval_quotas() -> Dict[int, int]:
    """Returns exact quotas for eval splits: val, test, hard_test (850 examples each)."""
    quotas = {}
    for c in range(1, 46):
        quotas[c] = 18   # 45 * 18 = 810
    quotas[46] = 40      # Cat 46: 40 held-out composition examples
    return quotas


def generate_balanced_partition(
    split_name: str,
    names: List[str],
    category_quotas: Dict[int, int],
    is_hard_test: bool = False,
    global_seen_prompts: Set[str] = None
) -> Tuple[List[Dict[str, Any]], ValidationReport]:
    """
    Generates a balanced dataset partition meeting exact category quotas.
    Validates structural schema and semantic fidelity on 100% of candidates.
    """
    if global_seen_prompts is None:
        global_seen_prompts = set()

    report = ValidationReport()
    report.seen_prompts = global_seen_prompts
    partition_examples: List[Dict[str, Any]] = []

    for cat_id in sorted(category_quotas.keys()):
        quota = category_quotas[cat_id]
        if quota <= 0:
            continue

        cat_examples: List[Dict[str, Any]] = []
        variant_counter = 0
        safety_max = 50000

        while len(cat_examples) < quota and variant_counter < safety_max:
            name = names[variant_counter % len(names)]
            if is_hard_test:
                name = apply_orthographic_variation(name, variant_counter)

            realizations = generate_category_examples(
                category_id=cat_id,
                entity=name,
                variant_idx=variant_counter,
                split=split_name,
                is_hard_test=is_hard_test
            )
            variant_counter += 1

            for r in realizations:
                if len(cat_examples) >= quota:
                    break

                ex_dict = r.to_dict()

                # 1. Structural and Schema validation (checks duplicate prompts via report.seen_prompts)
                is_valid, errs = validate_example(ex_dict, report)
                if not is_valid:
                    continue

                # 2. Semantic round-trip validation
                is_faithful, fidelity_errs = verify_semantic_fidelity(ex_dict["prompt"], ex_dict["target"])
                if not is_faithful:
                    for fe in fidelity_errs:
                        report.record_rejection(f"Semantic fidelity: {fe}")
                    continue

                cat_examples.append(ex_dict)

        if len(cat_examples) < quota:
            raise RuntimeError(
                f"Failed to reach quota {quota} for Category {cat_id} in {split_name} (got {len(cat_examples)})"
            )

        partition_examples.extend(cat_examples)

    # Deterministic shuffle so categories are intermixed within split
    rng = random.Random(42 + hash(split_name) % 10000)
    rng.shuffle(partition_examples)

    return partition_examples, report


def build_pilot_dataset(global_seen_prompts: Set[str] = None) -> Tuple[List[Dict[str, Any]], ValidationReport]:
    """Builds and validates the 500-example balanced pilot dataset."""
    print("Generating 500 balanced pilot examples...")
    DATASET_DIR.mkdir(parents=True, exist_ok=True)
    REPORTS_DIR.mkdir(parents=True, exist_ok=True)

    pilot_names = TRAIN_NAMES[:30] + VAL_NAMES[:10]
    quotas = get_pilot_quotas()

    pilot_data, report = generate_balanced_partition(
        split_name="pilot",
        names=pilot_names,
        category_quotas=quotas,
        is_hard_test=False,
        global_seen_prompts=global_seen_prompts
    )

    pilot_file = DATASET_DIR / "pilot.jsonl"
    with open(pilot_file, "w", encoding="utf-8") as f:
        for item in pilot_data:
            f.write(json.dumps(item, ensure_ascii=False) + "\n")

    print(f"Pilot dataset written to {pilot_file} ({len(pilot_data)} examples)")

    # Generate Pilot Quality Report
    report_file = REPORTS_DIR / "pilot_quality_report.md"
    avg_len = sum(len(d["prompt"]) for d in pilot_data) / max(1, len(pilot_data))
    with open(report_file, "w", encoding="utf-8") as f:
        f.write("# FREEDOM AI — Pilot Dataset Quality Report\n\n")
        f.write(f"- **Total Target**: 500 examples\n")
        f.write(f"- **Total Stored Examples**: {len(pilot_data)}\n")
        f.write(f"- **Total Valid Generated**: {report.valid_count}\n")
        f.write(f"- **Total Rejected**: {report.rejected_count}\n")
        f.write(f"- **Rejection Rate**: {report.summary()['rejection_rate'] * 100:.2f}%\n")
        f.write(f"- **Average Input Prompt Length**: {avg_len:.1f} characters\n\n")
        f.write("## Semantic Category Distribution\n\n")
        f.write("| Category | Example Count |\n| :--- | :--- |\n")
        for cat, cnt in sorted(report.category_counts.items(), key=lambda x: -x[1]):
            f.write(f"| `{cat}` | {cnt} |\n")
        f.write("\n## Linguistic Register Distribution\n\n")
        f.write("| Register | Example Count |\n| :--- | :--- |\n")
        for reg, cnt in sorted(report.register_counts.items(), key=lambda x: -x[1]):
            f.write(f"| `{reg}` | {cnt} |\n")
        f.write("\n## Rejection Reasons Breakdown\n\n")
        if report.rejection_reasons:
            for r, cnt in sorted(report.rejection_reasons.items(), key=lambda x: -x[1]):
                f.write(f"- **{r}**: {cnt}\n")
        else:
            f.write("Zero structural or semantic rejections in final pilot.\n")

    print(f"Pilot report written to {report_file}")
    return pilot_data, report


def build_full_dataset(global_seen_prompts: Set[str] = None):
    """
    Builds the full multi-split balanced dataset (8,500 examples):
    - Train: 5,950 examples (Cat 46 held out, Cat 36 = 135)
    - Validation: 850 examples (18 per Cat 1-45, 40 for Cat 46)
    - Test: 850 examples (18 per Cat 1-45, 40 for Cat 46, held-out entities)
    - Hard Test: 850 examples (18 per Cat 1-45, 40 for Cat 46, held-out entities + orthography)
    """
    DATASET_DIR.mkdir(parents=True, exist_ok=True)
    REPORTS_DIR.mkdir(parents=True, exist_ok=True)

    if global_seen_prompts is None:
        global_seen_prompts = set()

    # 1. Train Split
    train_quotas = get_train_quotas()
    print(f"Generating train split ({sum(train_quotas.values())} examples)...")
    train_data, r_train = generate_balanced_partition(
        split_name="train",
        names=TRAIN_NAMES,
        category_quotas=train_quotas,
        is_hard_test=False,
        global_seen_prompts=global_seen_prompts
    )

    # 2. Validation Split
    val_quotas = get_eval_quotas()
    print(f"Generating validation split ({sum(val_quotas.values())} examples)...")
    val_data, r_val = generate_balanced_partition(
        split_name="val",
        names=VAL_NAMES,
        category_quotas=val_quotas,
        is_hard_test=False,
        global_seen_prompts=global_seen_prompts
    )

    # 3. Test Split
    test_quotas = get_eval_quotas()
    print(f"Generating test split ({sum(test_quotas.values())} examples)...")
    test_data, r_test = generate_balanced_partition(
        split_name="test",
        names=TEST_NAMES,
        category_quotas=test_quotas,
        is_hard_test=False,
        global_seen_prompts=global_seen_prompts
    )

    # 4. Hard Test Split
    hard_quotas = get_eval_quotas()
    print(f"Generating hard test split ({sum(hard_quotas.values())} examples)...")
    hard_test_data, r_hard = generate_balanced_partition(
        split_name="hard_test",
        names=HARD_TEST_NAMES,
        category_quotas=hard_quotas,
        is_hard_test=True,
        global_seen_prompts=global_seen_prompts
    )

    # Write JSONL files
    splits = {
        "train.jsonl": train_data,
        "validation.jsonl": val_data,
        "test.jsonl": test_data,
        "hard_test.jsonl": hard_test_data,
    }
    for filename, data in splits.items():
        filepath = DATASET_DIR / filename
        with open(filepath, "w", encoding="utf-8") as f:
            for item in data:
                f.write(json.dumps(item, ensure_ascii=False) + "\n")
        print(f"Wrote {filepath} ({len(data)} examples)")

    # Run Leakage and Contamination Verification
    print("Running data leakage and contamination audit...")
    held_out_entities = set(TEST_NAMES) | set(HARD_TEST_NAMES)
    leakage_rep = check_leakage(train_data, val_data, test_data, hard_test_data, held_out_entities)

    # Write Dataset Quality and Leakage Report
    dataset_report_file = REPORTS_DIR / "dataset_quality_report.md"
    with open(dataset_report_file, "w", encoding="utf-8") as f:
        f.write("# FREEDOM AI — Full Dataset Quality & Leakage Report\n\n")
        f.write(f"- **Train Count**: {len(train_data)}\n")
        f.write(f"- **Validation Count**: {len(val_data)}\n")
        f.write(f"- **Test Count**: {len(test_data)} (Held-out entities & unseen phrasings)\n")
        f.write(f"- **Hard Test Count**: {len(hard_test_data)} (Held-out compositions & challenging orthography)\n")
        f.write(f"- **Total Scaled Examples**: {len(train_data) + len(val_data) + len(test_data) + len(hard_test_data)}\n\n")
        f.write("## Contamination & Leakage Analysis\n\n")
        f.write(f"- **Exact String Overlaps across splits**: {leakage_rep.summary()['exact_overlaps_count']}\n")
        f.write(f"- **Entity Contamination in Train**: {leakage_rep.summary()['entity_leaks_count']}\n")
        f.write(f"- **High-Jaccard Near-Duplicates**: {leakage_rep.summary()['near_duplicates_count']}\n\n")
        if leakage_rep.is_clean():
            f.write("> **VERIFIED CLEAN:** Zero exact string overlap, zero entity leakage, and zero high-similarity near-duplicate contamination across train and held-out test splits.\n\n")
        else:
            f.write(f"> **WARNING:** Contamination detected. Details: {leakage_rep.summary()}\n\n")

        f.write("## Split Characteristics\n\n")
        f.write("| Split | Size | Entities Used | Compositional Holdout (Cat 46) | Orthographic Variation |\n")
        f.write("| :--- | :--- | :--- | :--- | :--- |\n")
        f.write(f"| `train.jsonl` | {len(train_data)} | Train Partition (120 names) | Yes (Cat 46 = 0) | Standard |\n")
        f.write(f"| `validation.jsonl` | {len(val_data)} | Val Partition (25 names) | No (Cat 46 = 40) | Standard |\n")
        f.write(f"| `test.jsonl` | {len(test_data)} | Test Partition (30 names) | No (Cat 46 = 40) | Standard |\n")
        f.write(f"| `hard_test.jsonl` | {len(hard_test_data)} | Hard-Test Partition (30 names) | Yes (Cat 46 = 40) | Challenging (Initials/Punctuation) |\n")

    print(f"Dataset quality report written to {dataset_report_file}")

    # Write Data Statistics Report
    data_stats_file = REPORTS_DIR / "data_statistics.md"
    all_data = train_data + val_data + test_data + hard_test_data
    avg_p_len = sum(len(d["prompt"]) for d in all_data) / len(all_data)
    avg_t_len = sum(len(json.dumps(d["target"])) for d in all_data) / len(all_data)
    with open(data_stats_file, "w", encoding="utf-8") as f:
        f.write("# FREEDOM AI — Dataset Tokenization & Statistics\n\n")
        f.write(f"- **Total Dataset Size**: {len(all_data)} examples\n")
        f.write(f"- **Average Prompt Character Length**: {avg_p_len:.1f}\n")
        f.write(f"- **Estimated Average Prompt Tokens**: {int(avg_p_len / 4.0)}\n")
        f.write(f"- **Average Target JSON Character Length**: {avg_t_len:.1f}\n")
        f.write(f"- **Estimated Average Target Tokens**: {int(avg_t_len / 3.5)}\n")
        f.write(f"- **Estimated Total Training Tokens (Train Split, 3 Epochs)**: ~{int(len(train_data) * (avg_p_len/4.0 + avg_t_len/3.5) * 3):,} tokens\n")

    print(f"Data statistics written to {data_stats_file}")


def build_all():
    """Builds pilot and full dataset with shared global deduplication."""
    print("Starting full balanced dataset regeneration (9,000 target examples)...")
    seen_prompts: Set[str] = set()
    build_pilot_dataset(seen_prompts)
    build_full_dataset(seen_prompts)
    print("All datasets generated successfully!")


if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    parser.add_argument("--mode", type=str, default="all", choices=["pilot", "full", "all"])
    args = parser.parse_args()

    if args.mode == "pilot":
        build_pilot_dataset()
    elif args.mode == "full":
        build_full_dataset()
    elif args.mode == "all":
        build_all()
