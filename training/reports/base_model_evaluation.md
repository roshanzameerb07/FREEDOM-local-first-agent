# FREEDOM AI — Base Qwen3 1.7B Model Evaluation Report

**Evaluation Target**: Untrained / Base `Qwen/Qwen3-1.7B` (Zero-shot performance)  
**Evaluation Set**: `dataset/test.jsonl` (850 held-out examples, unseen entity vocabulary, unseen phrasings)  
**Status**: Environment Diagnostic & Reproducible Verification Harness Prepared  

---

## 1. Local Environment Audit

- **Operating System**: Windows 11 AMD64
- **Python Runtime**: Python 3.14.5 (`C:\Python314\python.exe`)
- **PyTorch / Transformers Status**: Not available (Python 3.14 is a pre-release/preview version on Windows without pre-built CUDA PyTorch wheel binaries).
- **Execution Policy**: Per directive `<base_model_evaluation>` ("*Do not fabricate results. If Qwen cannot be evaluated in the current environment, report that explicitly and create the exact reproducible evaluation command for a supported GPU environment*"), no synthetic or simulated accuracy metrics are claimed.

---

## 2. Theoretical Base Model Baseline (Zero-Shot Analysis)

Without fine-tuning on the canonical `FreedomQuery` contract:
- **Base Model Knowledge**: `Qwen/Qwen3-1.7B` has general instruction-following and coding capabilities, but has zero prior knowledge of:
  1. The custom typed enum schema (`COUNT_RECORDS` vs `COUNT_DISTINCT_FARMERS`, `SPECIFIC` vs `ALL`, `LATEST` vs `ANY`).
  2. The strict key ordering and null normalization rules.
  3. The exact condition field and temporal token conventions (`SINCE_MONDAY`, `LAST_SUNDAY`).
- **Expected Zero-Shot Exact AST Match Accuracy**: `< 2%` (Base models commonly hallucinate raw SQL, markdown fences, thinking tags, or invented keys like `"amount": 25` instead of `"select": ["QUANTITY"]`).

---

## 3. Exact Reproducible GPU Evaluation Procedure

To evaluate the base model on a standard GPU server (Linux / Ubuntu 22.04 with CUDA 12.1+ or Google Colab / RunPod A10G/T4):

### Step 1: Environment Setup
```bash
git clone <freedom-repo>
cd freedom/training

# Python 3.10 / 3.11 virtual environment
python3 -m venv venv
source venv/bin/activate

pip install --upgrade pip
pip install torch torchvision --index-url https://download.pytorch.org/whl/cu121
pip install transformers accelerate sentencepiece
```

### Step 2: Run Evaluation Benchmark
```bash
python -m evaluation.base_model_evaluator \
  --dataset dataset/test.jsonl \
  --output reports/base_model_evaluation.json
```

### Measured Metric Checklist:
- `exact_ast_accuracy`: Complete JSON structural equality with canonical AST.
- `request_type_accuracy`: QUERY vs WRITE vs CLARIFY vs UNSUPPORTED.
- `target_accuracy`: MILK_RECORDS vs WORKER_PROFILE vs ORGANIZATION_INFO vs KNOWLEDGE_BASE.
- `entity_accuracy`: Exact farmer entity string match without full question leakage.
- `select_field_accuracy`: Set equality of projected schema fields.
- `filter_accuracy`: Exact match of field, operator, value, secondaryValue.
- `temporal_accuracy`: Canonical temporal constraint mapping (RELATIVE / EXPLICIT_DATE / EXPLICIT_RANGE).
- `aggregation_accuracy`: SUM, COUNT_RECORDS, COUNT_DISTINCT_FARMERS, AVG, MIN, MAX.
- `grouping_accuracy`: Exact grouping dimensions.
- `ordering_accuracy`: Field and aggregate ordering specifications.
- `limit_accuracy`: Integer limit extraction.
- `payment_scope_accuracy`: LATEST vs ALL vs ANY.
- `clarification_accuracy`: Accurate abstention on ambiguous entities/pronouns.
- `unsupported_accuracy`: Accurate identification of out-of-domain/delay reason requests.
- `read_write_accuracy`: Accurate discrimination between read queries and record creation commands.
