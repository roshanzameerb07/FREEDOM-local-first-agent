# FREEDOM AI — Phase 2: Qwen3 1.7B Training Dataset Engineering

This directory contains the complete dataset engineering, validation, leakage detection, evaluation, and fine-tuning infrastructure for the **FREEDOM** on-device Natural Language Query Rebuild.

---

## 1. Architectural Philosophy & Primary Task

The on-device Qwen3 1.7B model is trained **strictly as a semantic compiler**:

$$\text{Natural Language Question} \xrightarrow{\text{Qwen3 1.7B}} \text{Canonical FreedomQuery JSON}$$

- The model does **NOT** memorize farmer data or perform database lookups.
- The model does **NOT** output epoch timestamps or raw SQL.
- All actual database calculations, temporal window resolution, session scoping, and Room SQLite queries are executed deterministically by Kotlin.

---

## 2. Directory Structure

```
training/
├── dataset/
│   ├── pilot.jsonl              # 500 validated balanced pilot examples
│   ├── train.jsonl              # 5,950 training examples (holds out Category 46)
│   ├── validation.jsonl         # 850 validation examples
│   ├── test.jsonl               # 850 held-out test examples (unseen entities/phrasings)
│   └── hard_test.jsonl          # 850 hard test examples (held-out compositions, initials, edge cases)
├── schema/
│   ├── freedom_query_schema.json    # Strict JSON Schema Draft 2020-12
│   └── freedom_query_semantics.json # Room mapping rules and semantic capabilities
├── generators/
│   ├── schema_encoder.py        # Programmatic schema loader & canonical JSON serializer
│   ├── entity_vocabulary.py     # Partitioned Indian farmer naming vocabulary
│   ├── semantic_query_generator.py # Canonical AST template builder
│   └── linguistic_realizer.py   # Multi-register natural language generation (46 categories)
├── validators/
│   ├── query_validator.py       # AST validation against frozen Phase 1 rules
│   ├── dataset_validator.py     # Structural & deduplication verification
│   └── semantic_round_trip.py   # Independent semantic fidelity checker
├── leakage/
│   └── leakage_detector.py      # Cross-split string, entity, and Jaccard leakage detection
├── evaluation/
│   ├── metrics.py               # Field-by-field, AST, and structural accuracy metrics
│   └── base_model_evaluator.py  # Model inference & evaluation harness
├── configs/
│   ├── training_config.yaml     # Hugging Face TRL & PEFT SFT/QLoRA configuration
│   └── lora_config.json         # PEFT adapter specification
├── reports/
│   ├── pilot_quality_report.md  # Pilot validation and distribution analysis
│   ├── dataset_quality_report.md# Contamination, leakage, and split report
│   ├── base_model_evaluation.md # Baseline evaluation & GPU reproduction instructions
│   └── data_statistics.md       # Tokenization and training load estimates
├── build_dataset.py             # Master generation, validation, and leakage runner
├── train_sft.py                 # SFTTrainer launcher
└── README.md                    # This guide
```

---

## 3. The 46 Supported Semantic Categories

The generator systematically produces examples across:
1. **Simple field lookup** (`QUANTITY`, `FAT`, `SNF`, `PAYABLE_AMOUNT`)
2. **Single farmer lookup** (`entityScope: SPECIFIC`)
3. **Multi-field lookup** (compound select projections)
4. **Time-constrained lookup** (relative periods & explicit dates)
5. **SUM aggregation** (`SUM(QUANTITY)`, `SUM(AMOUNT_PAID)`)
6. **AVG aggregation** (`AVG(FAT)`, `AVG(SNF)`)
7. **MIN aggregation** (`MIN(QUANTITY)`)
8. **MAX aggregation** (`MAX(QUANTITY)`)
9. **COUNT_RECORDS** (collection count)
10. **COUNT_DISTINCT_FARMERS** (authoritative unique `farmerId` count)
11. **Numeric filters** (`GREATER_THAN`, `LESS_THAN`, `IN_RANGE`)
12. **Equality filters** (`EQUALS`, `NOT_EQUALS`)
13. **Null / Not-null filters** (`IS_NULL`, `IS_NOT_NULL`)
14. **AND logic filters** (conjunctions)
15. **OR logic filters** (supported disjunctions)
16. **GROUP BY** (grouping by `FARMER_NAME`, `PAYMENT_STATUS`, `UPLOAD_STATUS`)
17. **HAVING clause** (aggregate threshold filtering)
18. **ORDER BY field** (sorting by schema columns)
19. **ORDER BY aggregate** ("Who gave the most milk?")
20. **LIMIT** (Top $k$ results)
21. **EXISTS** (existence checks)
22. **Payment status** (`PENDING`, `RECORDED_LOCALLY`, `PAID`)
23. **Payment method** (`CASH`, `UPI`, `BANK_TRANSFER`)
24. **Payment reference** (`TXN12345`, `UPI-7788`)
25. **Amount paid** (`AMOUNT_PAID`)
26. **Payment timestamp** (formatting localized recorded timestamp)
27. **Upload status** (`UPLOAD_STATUS`)
28. **Multi-aggregation** (`SUM(QUANTITY)` + `AVG(FAT)`)
29. **Payment scope** (`LATEST`, `ALL`, `ANY`)
30. **Conditional information requests** (state-dependent field reporting)
31. **Explicit single dates** (`2026-09-18`)
32. **Explicit date ranges** (`2026-09-01` to `2026-09-15`)
33. **Relative dates** (`TODAY`, `YESTERDAY`, `THIS_WEEK`, `LAST_WEEK`, `THIS_MONTH`, `LAST_MONTH`, `SINCE_MONDAY`, `LAST_SUNDAY`)
34. **Read / Write distinction** (mandatory contrastive pairs: "Record 25L" [WRITE] vs "Did he give 25L?" [QUERY])
35. **Ambiguous entity** (triggers `CLARIFY`)
36. **Unknown entity** (triggers `CLARIFY / NOT_FOUND`)
37. **Missing field / Unspecified intent** (triggers `CLARIFY`)
38. **Unsupported requests** (delay reasons, causality, counterfactuals -> `UNSUPPORTED`)
39. **Conversational pronoun resolution** ("What did he give?" -> `CLARIFY`)
40. **Indian-English phrasing** ("milk collection how much done today")
41. **Bad grammar / Missing articles** ("suresh milk total how much give this week")
42. **Short queries** ("Suresh today", "Total milk week")
43. **Natural word-order variation** ("This week, what was Suresh's delivery?")
44. **Paraphrases** ("milk quantity", "volume supplied", "milk brought")
45. **Multi-condition queries** (compound filters with quality criteria)
46. **Compositional queries** (ALL + TIME + FILTER + GROUP BY + SUM + ORDER AGGREGATE + LIMIT)

---

## 4. Usage & Commands

### Regenerate Pilot Dataset (500 samples):
```bash
python build_dataset.py --mode pilot
```

### Regenerate Full Multi-Split Dataset (8,500 samples):
```bash
python build_dataset.py --mode full --target_count 8500
```

### Run Base Model Evaluation Benchmark (GPU Environment):
```bash
python -m evaluation.base_model_evaluator \
  --dataset dataset/test.jsonl \
  --output reports/base_model_evaluation.json
```

### Launch Supervised Fine-Tuning (SFT / QLoRA):
```bash
python train_sft.py --config configs/training_config.yaml
```
