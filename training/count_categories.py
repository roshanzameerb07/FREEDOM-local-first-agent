import json
from pathlib import Path

DATASET_DIR = Path("dataset")
splits = ["pilot", "train", "validation", "test", "hard_test"]

def classify(item):
    cat = item.get("metadata", {}).get("category", "")
    reg = item.get("metadata", {}).get("register", "")
    
    mapping = {
        "simple_field_lookup": 1,
        "single_farmer_lookup": 2,
        "multi_field_lookup": 3,
        "time_constrained_lookup": 4,
        "sum_aggregation": 5,
        "avg_aggregation": 6,
        "min_aggregation": 7,
        "max_aggregation": 8,
        "count_records": 9,
        "count_distinct_farmers": 10,
        "numeric_filter": 11,
        "equality_filter": 12,
        "not_null_filter": 13,
        "and_logic_filter": 14,
        "or_logic_filter": 15,
        "group_by": 16,
        "having_filter": 17,
        "order_by_field": 18,
        "order_by_aggregate": 19,
        "limit_query": 20,
        "exists_query": 21,
        "payment_status": 22,
        "payment_method": 23,
        "payment_reference": 24,
        "amount_paid": 25,
        "payment_timestamp": 26,
        "upload_status": 27,
        "multi_aggregation": 28,
        "payment_scope_all": 29,
        "payment_scope_any": 29,
        "conditional_query": 30,
        "explicit_date": 31,
        "explicit_range": 32,
        "relative_date": 33,
        "read_write_contrast": 34,
        "ambiguous_entity": 35,
        "unknown_entity": 36,
        "missing_field": 37,
        "unsupported_request": 38,
        "conversational_pronoun": 39,
        "multi_condition": 45,
        "compositional_query": 46,
    }
    if cat in mapping:
        return mapping[cat]
    if cat == "linguistic_variation":
        reg_map = {
            "indian_english": 40,
            "bad_grammar": 41,
            "short": 42,
            "word_order": 43,
            "paraphrase": 44,
        }
        return reg_map.get(reg, 40)
    return -1

category_names = [
    "Simple field lookup", "Single farmer lookup", "Multi-field lookup", "Time-constrained lookup",
    "SUM aggregation", "AVG aggregation", "MIN aggregation", "MAX aggregation",
    "COUNT records", "COUNT distinct farmers", "Numeric filter", "Equality filter",
    "IS NOT NULL filter", "AND logic filter", "OR logic filter", "GROUP BY clause",
    "HAVING filter", "ORDER BY field", "ORDER BY aggregate", "LIMIT clause",
    "EXISTS query", "Payment status", "Payment method", "Payment reference",
    "Amount paid", "Payment timestamp", "Upload / sync status", "Multi-aggregation",
    "Payment scope (ANY/ALL)", "Conditional query", "Explicit date", "Explicit date range",
    "Relative date period", "Read/write contrast", "Ambiguous entity", "Unknown entity",
    "Missing/unspecified field", "Unsupported request", "Conversational pronoun",
    "Linguistic: Indian-English", "Linguistic: Bad grammar", "Linguistic: Short queries",
    "Linguistic: Word order", "Linguistic: Paraphrases", "Multi-condition queries",
    "Compositional queries"
]

counts = {cid: {s: 0 for s in splits} for cid in range(1, 47)}
unmatched = {s: 0 for s in splits}

for s in splits:
    with open(DATASET_DIR / f"{s}.jsonl", "r", encoding="utf-8") as f:
        for line in f:
            item = json.loads(line)
            cid = classify(item)
            if cid in counts:
                counts[cid][s] += 1
            else:
                unmatched[s] += 1

print("| ID | Category Name | pilot | train | val | test | hard_test | Total |")
print("|---|---|---|---|---|---|---|---|")
for cid in range(1, 47):
    c = counts[cid]
    tot = sum(c.values())
    p, tr, v, te, ht = c["pilot"], c["train"], c["validation"], c["test"], c["hard_test"]
    print(f"| {cid:02d} | {category_names[cid-1]} | {p} | {tr} | {v} | {te} | {ht} | {tot} |")

print("Unmatched:", unmatched)
