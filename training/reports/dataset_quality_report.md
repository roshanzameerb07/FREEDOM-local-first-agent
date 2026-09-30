# FREEDOM AI — Full Dataset Quality & Leakage Report

- **Train Count**: 5950
- **Validation Count**: 850
- **Test Count**: 850 (Held-out entities & unseen phrasings)
- **Hard Test Count**: 850 (Held-out compositions & challenging orthography)
- **Total Scaled Examples**: 8500

## Contamination & Leakage Analysis

- **Exact String Overlaps across splits**: 0
- **Entity Contamination in Train**: 0
- **High-Jaccard Near-Duplicates**: 0

> **VERIFIED CLEAN:** Zero exact string overlap, zero entity leakage, and zero high-similarity near-duplicate contamination across train and held-out test splits.

## Split Characteristics

| Split | Size | Entities Used | Compositional Holdout (Cat 46) | Orthographic Variation |
| :--- | :--- | :--- | :--- | :--- |
| `train.jsonl` | 5950 | Train Partition (120 names) | Yes (Cat 46 = 0) | Standard |
| `validation.jsonl` | 850 | Val Partition (25 names) | No (Cat 46 = 40) | Standard |
| `test.jsonl` | 850 | Test Partition (30 names) | No (Cat 46 = 40) | Standard |
| `hard_test.jsonl` | 850 | Hard-Test Partition (30 names) | Yes (Cat 46 = 40) | Challenging (Initials/Punctuation) |
