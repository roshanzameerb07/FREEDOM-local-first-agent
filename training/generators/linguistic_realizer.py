"""
Linguistic Realizer for FREEDOM Canonical Dataset Engineering.
Implements multi-register natural language generation across all 46 semantic categories.
Provides rich parameterization across fields, thresholds, dates, operators, and registers.
Supports partitioned unknown entity names and held-out compositional generalization.
"""

from typing import Any, Dict, List, Tuple
from generators.semantic_query_generator import make_query_template
from generators.entity_vocabulary import get_unknown_name


class QueryRealization:
    def __init__(self, prompt: str, query: Dict[str, Any], category: str, register: str):
        self.prompt = prompt
        self.query = query
        self.category = category
        self.register = register

    def to_dict(self) -> Dict[str, Any]:
        return {
            "prompt": self.prompt,
            "target": self.query,
            "metadata": {
                "category": self.category,
                "register": self.register,
            }
        }


BASE_TIME_OPTIONS = [
    ({"type": "RELATIVE", "period": "TODAY"}, "today"),
    ({"type": "RELATIVE", "period": "YESTERDAY"}, "yesterday"),
    ({"type": "RELATIVE", "period": "THIS_WEEK"}, "this week"),
    ({"type": "RELATIVE", "period": "LAST_WEEK"}, "last week"),
    ({"type": "RELATIVE", "period": "THIS_MONTH"}, "this month"),
    ({"type": "RELATIVE", "period": "LAST_MONTH"}, "last month"),
    ({"type": "RELATIVE", "period": "SINCE_MONDAY"}, "since Monday"),
    ({"type": "RELATIVE", "period": "LAST_SUNDAY"}, "last Sunday"),
    ({"type": "EXPLICIT_DATE", "date": "2026-09-01"}, "on 2026-09-01"),
    ({"type": "EXPLICIT_DATE", "date": "2026-09-05"}, "on 2026-09-05"),
    ({"type": "EXPLICIT_DATE", "date": "2026-09-10"}, "on 2026-09-10"),
    ({"type": "EXPLICIT_DATE", "date": "2026-09-15"}, "on 2026-09-15"),
    ({"type": "EXPLICIT_DATE", "date": "2026-09-18"}, "on 2026-09-18"),
    ({"type": "EXPLICIT_DATE", "date": "2026-09-20"}, "on 2026-09-20"),
    ({"type": "EXPLICIT_DATE", "date": "2026-09-25"}, "on 2026-09-25"),
    ({"type": "EXPLICIT_DATE", "date": "2026-09-28"}, "on 2026-09-28"),
    ({"type": "EXPLICIT_DATE", "date": "2026-09-29"}, "on 2026-09-29"),
    ({"type": "EXPLICIT_DATE", "date": "2026-09-30"}, "on 2026-09-30"),
]


def generate_category_examples(
    category_id: int,
    entity: str,
    variant_idx: int = 0,
    split: str = "train",
    is_hard_test: bool = False
) -> List[QueryRealization]:
    """Generates a list of diverse linguistic realizations for a specific semantic category."""
    out: List[QueryRealization] = []

    # ── 1. Simple field lookup ─────────────────────────────────────────────
    if category_id == 1:
        fields = ["FAT", "SNF", "QUANTITY", "AMOUNT_PAID", "PAYMENT_STATUS", "PAYMENT_METHOD"]
        f = fields[variant_idx % len(fields)]
        q = make_query_template(entity=entity, select=[f])
        f_labels = {
            "FAT": ("fat percentage", "fat content in", "fat"),
            "SNF": ("SNF percentage", "SNF value for", "snf"),
            "QUANTITY": ("milk quantity", "volume of milk from", "milk quantity"),
            "AMOUNT_PAID": ("payment amount", "amount paid to", "amount paid"),
            "PAYMENT_STATUS": ("payment status", "payment status for", "payment status"),
            "PAYMENT_METHOD": ("payment mode", "payment method of", "payment method"),
        }
        l1, l2, l3 = f_labels[f]
        prompts = [
            (f"What was the {l2} {entity}'s milk?", "standard"),
            (f"Show {entity} {l1}.", "concise"),
            (f"How much {l3} did {entity}'s milk have?", "conversational"),
            (f"{entity} {l3} value how much?", "indian_english"),
            (f"{entity} {l3}", "short"),
        ]
        for p, reg in prompts:
            out.append(QueryRealization(p, q, "simple_field_lookup", reg))

    # ── 2. Single farmer lookup ───────────────────────────────────────────
    elif category_id == 2:
        q = make_query_template(
            entity=entity,
            select=["QUANTITY", "FAT", "SNF", "PAYMENT_STATUS"]
        )
        prompts = [
            (f"Show me {entity}'s latest milk collection details.", "standard"),
            (f"All details for {entity}.", "concise"),
            (f"Fetch {entity}'s recent delivery entry.", "conversational"),
            (f"{entity} milk details show.", "indian_english"),
            (f"{entity} delivery record", "short"),
        ]
        for p, reg in prompts:
            out.append(QueryRealization(p, q, "single_farmer_lookup", reg))

    # ── 3. Multi-field lookup ─────────────────────────────────────────────
    elif category_id == 3:
        pairs = [
            (["FAT", "SNF"], "fat and SNF", "fat and snf"),
            (["QUANTITY", "AMOUNT_PAID"], "quantity and payment amount", "quantity and amount"),
            (["QUANTITY", "FAT"], "quantity and fat percentage", "qty and fat"),
            (["PAYMENT_STATUS", "PAYMENT_METHOD"], "payment status and method", "payment status and mode"),
        ]
        sel, desc, short_desc = pairs[variant_idx % len(pairs)]
        q = make_query_template(entity=entity, select=sel)
        prompts = [
            (f"Give me the {desc} for {entity}.", "standard"),
            (f"{entity} {desc}.", "concise"),
            (f"Check {entity}'s {desc}.", "conversational"),
            (f"{entity} {desc} how much?", "indian_english"),
            (f"{entity} {short_desc}", "short"),
        ]
        for p, reg in prompts:
            out.append(QueryRealization(p, q, "multi_field_lookup", reg))

    # ── 4. Time-constrained lookup ────────────────────────────────────────
    elif category_id == 4:
        periods = [
            ("TODAY", "today"),
            ("YESTERDAY", "yesterday"),
            ("THIS_WEEK", "this week"),
            ("LAST_WEEK", "last week"),
            ("THIS_MONTH", "this month"),
            ("SINCE_MONDAY", "since Monday"),
        ]
        period_enum, period_txt = periods[variant_idx % len(periods)]
        q = make_query_template(
            entity=entity,
            select=["QUANTITY", "FAT", "SNF"],
            time_val={"type": "RELATIVE", "period": period_enum}
        )
        prompts = [
            (f"What did {entity} deliver {period_txt}?", "standard"),
            (f"{entity} milk record {period_txt}.", "concise"),
            (f"Did {entity} supply milk {period_txt}? Show the collection.", "conversational"),
            (f"{entity} {period_txt} milk collection how much?", "indian_english"),
            (f"{entity} records {period_txt}", "short"),
        ]
        for p, reg in prompts:
            out.append(QueryRealization(p, q, "time_constrained_lookup", reg))

    # ── 5. SUM aggregation ────────────────────────────────────────────────
    elif category_id == 5:
        periods = [
            ("THIS_WEEK", "this week"),
            ("TODAY", "today"),
            ("YESTERDAY", "yesterday"),
            ("LAST_WEEK", "last week"),
            ("THIS_MONTH", "this month"),
            ("LAST_MONTH", "last month"),
        ]
        period_enum, period_txt = periods[variant_idx % len(periods)]
        q = make_query_template(
            entity=entity,
            select=["QUANTITY"],
            time_val={"type": "RELATIVE", "period": period_enum},
            aggregations=[{"type": "SUM", "field": "QUANTITY"}]
        )
        prompts = [
            (f"How much milk did {entity} provide in total {period_txt}?", "standard"),
            (f"Total milk quantity delivered by {entity} {period_txt}.", "concise"),
            (f"Sum of milk supplied by {entity} {period_txt}.", "conversational"),
            (f"{entity} total milk {period_txt} how many litres?", "indian_english"),
            (f"{entity} sum milk {period_txt}", "short"),
        ]
        for p, reg in prompts:
            out.append(QueryRealization(p, q, "sum_aggregation", reg))

    # ── 6. AVG aggregation ────────────────────────────────────────────────
    elif category_id == 6:
        fields = [
            ("FAT", "fat percentage", "fat"),
            ("SNF", "SNF value", "snf"),
            ("QUANTITY", "milk quantity per delivery", "quantity"),
        ]
        f, f_desc, f_short = fields[variant_idx % len(fields)]
        periods = [("THIS_WEEK", "this week"), ("TODAY", "today"), ("THIS_MONTH", "this month")]
        p_enum, p_txt = periods[(variant_idx // len(fields)) % len(periods)]
        q = make_query_template(
            entity=entity,
            select=[f],
            time_val={"type": "RELATIVE", "period": p_enum},
            aggregations=[{"type": "AVG", "field": f}]
        )
        prompts = [
            (f"What was the average {f_desc} in {entity}'s milk {p_txt}?", "standard"),
            (f"Mean {f_desc} for {entity} {p_txt}.", "concise"),
            (f"Calculate {entity}'s average {f_desc} over {p_txt}.", "conversational"),
            (f"{entity} avg {f_short} {p_txt} how much?", "indian_english"),
            (f"{entity} average {f_short} {p_txt}", "short"),
        ]
        for p, reg in prompts:
            out.append(QueryRealization(p, q, "avg_aggregation", reg))

    # ── 7. MIN aggregation ────────────────────────────────────────────────
    elif category_id == 7:
        fields = [
            ("FAT", "fat percentage", "fat"),
            ("SNF", "SNF reading", "snf"),
            ("QUANTITY", "delivery quantity", "milk"),
        ]
        f, f_desc, f_short = fields[variant_idx % len(fields)]
        periods = [("THIS_WEEK", "this week"), ("THIS_MONTH", "this month")]
        p_enum, p_txt = periods[(variant_idx // len(fields)) % len(periods)]
        q = make_query_template(
            entity=entity,
            select=[f],
            time_val={"type": "RELATIVE", "period": p_enum},
            aggregations=[{"type": "MIN", "field": f}]
        )
        prompts = [
            (f"What was the lowest {f_desc} recorded for {entity} {p_txt}?", "standard"),
            (f"Minimum {f_desc} for {entity} {p_txt}.", "concise"),
            (f"Smallest {f_desc} {entity} had {p_txt}.", "conversational"),
            (f"{entity} minimum {f_short} {p_txt} how much?", "indian_english"),
            (f"{entity} min {f_short} {p_txt}", "short"),
        ]
        for p, reg in prompts:
            out.append(QueryRealization(p, q, "min_aggregation", reg))

    # ── 8. MAX aggregation ────────────────────────────────────────────────
    elif category_id == 8:
        fields = [
            ("FAT", "fat reading", "fat"),
            ("SNF", "SNF reading", "snf"),
            ("QUANTITY", "milk volume", "quantity"),
        ]
        f, f_desc, f_short = fields[variant_idx % len(fields)]
        periods = [("THIS_WEEK", "this week"), ("THIS_MONTH", "this month")]
        p_enum, p_txt = periods[(variant_idx // len(fields)) % len(periods)]
        q = make_query_template(
            entity=entity,
            select=[f],
            time_val={"type": "RELATIVE", "period": p_enum},
            aggregations=[{"type": "MAX", "field": f}]
        )
        prompts = [
            (f"What was the highest {f_desc} for {entity} {p_txt}?", "standard"),
            (f"Peak {f_desc} delivered by {entity} {p_txt}.", "concise"),
            (f"Maximum {f_desc} {entity} brought {p_txt}.", "conversational"),
            (f"{entity} maximum {f_short} {p_txt} how much?", "indian_english"),
            (f"{entity} max {f_short} {p_txt}", "short"),
        ]
        for p, reg in prompts:
            out.append(QueryRealization(p, q, "max_aggregation", reg))

    # ── 9. COUNT records ──────────────────────────────────────────────────
    elif category_id == 9:
        periods = [
            ("THIS_WEEK", "this week"),
            ("THIS_MONTH", "this month"),
            ("TODAY", "today"),
            ("YESTERDAY", "yesterday"),
        ]
        p_enum, p_txt = periods[variant_idx % len(periods)]
        q = make_query_template(
            entity=entity,
            time_val={"type": "RELATIVE", "period": p_enum},
            aggregations=[{"type": "COUNT_RECORDS", "field": None}]
        )
        prompts = [
            (f"How many collections did {entity} make {p_txt}?", "standard"),
            (f"Number of deliveries for {entity} {p_txt}.", "concise"),
            (f"How many times did {entity} bring milk {p_txt}?", "conversational"),
            (f"{entity} delivery count {p_txt} how much?", "indian_english"),
            (f"{entity} how many collections {p_txt}", "short"),
        ]
        for p, reg in prompts:
            out.append(QueryRealization(p, q, "count_records", reg))

    # ── 10. COUNT_DISTINCT_FARMERS ────────────────────────────────────────
    elif category_id == 10:
        filter_options = [
            None,
            ([{"field": "FAT", "op": "GREATER_THAN", "value": "4.0", "secondaryValue": None}], "with fat above 4.0%"),
            ([{"field": "FAT", "op": "GREATER_THAN", "value": "4.2", "secondaryValue": None}], "with fat > 4.2%"),
            ([{"field": "SNF", "op": "GREATER_THAN", "value": "8.5", "secondaryValue": None}], "with SNF above 8.5"),
            ([{"field": "QUANTITY", "op": "GREATER_THAN", "value": "15.0", "secondaryValue": None}], "supplying over 15 litres"),
            ([{"field": "AMOUNT_PAID", "op": "GREATER_THAN", "value": "500", "secondaryValue": None}], "paid over 500 rupees"),
            ([{"field": "PAYMENT_STATUS", "op": "EQUALS", "value": "PAID", "secondaryValue": None}], "with status PAID"),
            ([{"field": "PAYMENT_STATUS", "op": "EQUALS", "value": "PENDING", "secondaryValue": None}], "awaiting payment"),
        ]
        t_val, t_str = BASE_TIME_OPTIONS[variant_idx % len(BASE_TIME_OPTIONS)]
        f_idx = (variant_idx // len(BASE_TIME_OPTIONS)) % len(filter_options)
        f_choice = filter_options[f_idx]
        if f_choice is not None:
            f_list, f_desc = f_choice
            q = make_query_template(
                time_val=t_val,
                filters=f_list,
                aggregations=[{"type": "COUNT_DISTINCT_FARMERS", "field": None}]
            )
            prompts = [
                (f"How many farmers gave milk {f_desc} {t_str}?", "standard"),
                (f"Count of distinct farmers {f_desc} {t_str}.", "concise"),
                (f"How many unique farmers delivered milk {f_desc} {t_str}?", "conversational"),
                (f"{t_str.capitalize()} how many farmers {f_desc} covered?", "indian_english"),
                (f"distinct farmers {f_desc} {t_str}", "short"),
            ]
        else:
            q = make_query_template(
                time_val=t_val,
                aggregations=[{"type": "COUNT_DISTINCT_FARMERS", "field": None}]
            )
            prompts = [
                (f"How many farmers gave milk {t_str}?", "standard"),
                (f"Count of distinct farmers {t_str}.", "concise"),
                (f"How many unique farmers did we collect milk from {t_str}?", "conversational"),
                (f"{t_str.capitalize()} how many farmers covered?", "indian_english"),
                (f"distinct farmers {t_str}", "short"),
            ]
        for p, reg in prompts:
            out.append(QueryRealization(p, q, "count_distinct_farmers", reg))

    # ── 11. Numeric filters ───────────────────────────────────────────────
    elif category_id == 11:
        num_configs = [
            ("QUANTITY", "GREATER_THAN", "20.0", "greater than 20 litres", "quantity > 20 L", "exceeds 20 litres"),
            ("QUANTITY", "LESS_THAN", "10.0", "less than 10 litres", "quantity < 10 L", "under 10 litres"),
            ("FAT", "GREATER_THAN", "4.2", "fat above 4.2%", "fat > 4.2%", "fat higher than 4.2"),
            ("FAT", "LESS_THAN", "3.5", "fat below 3.5%", "fat < 3.5%", "fat under 3.5"),
            ("SNF", "GREATER_THAN", "8.5", "SNF higher than 8.5", "snf > 8.5", "SNF above 8.5"),
            ("SNF", "LESS_THAN", "8.0", "SNF lower than 8.0", "snf < 8.0", "SNF under 8.0"),
            ("AMOUNT_PAID", "GREATER_THAN", "1000", "amount paid above 1000 rupees", "amount > 1000", "payout over 1000"),
            ("QUANTITY", "GREATER_OR_EQUAL", "25.0", "at least 25 litres", "quantity >= 25 L", "25 litres or more"),
        ]
        f, op, val, desc1, desc2, desc3 = num_configs[variant_idx % len(num_configs)]
        use_entity = (variant_idx % 2 == 0)
        t_val, t_str = BASE_TIME_OPTIONS[(variant_idx // len(num_configs)) % len(BASE_TIME_OPTIONS)]
        if use_entity:
            q = make_query_template(
                entity=entity,
                select=["FARMER_NAME", f],
                filters=[{"field": f, "op": op, "value": val, "secondaryValue": None}]
            )
            prompts = [
                (f"Which deliveries for {entity} were {desc1}?", "standard"),
                (f"{entity} records with {desc2}.", "concise"),
                (f"Show me all milk entries for {entity} where {desc3}.", "conversational"),
                (f"{entity} deliveries {desc1} show please.", "indian_english"),
                (f"{entity} {f.lower()} {op.lower()} {val}", "short"),
            ]
        else:
            q = make_query_template(
                time_val=t_val,
                select=["FARMER_NAME", f],
                filters=[{"field": f, "op": op, "value": val, "secondaryValue": None}]
            )
            prompts = [
                (f"Which deliveries were {desc1} {t_str}?", "standard"),
                (f"Records with {desc2} {t_str}.", "concise"),
                (f"Show me all milk entries where {desc3} {t_str}.", "conversational"),
                (f"Deliveries {desc1} {t_str} show please.", "indian_english"),
                (f"{f.lower()} {op.lower()} {val} {t_str}", "short"),
            ]
        for p, reg in prompts:
            out.append(QueryRealization(p, q, "numeric_filter", reg))

    # ── 12. Equality filters ──────────────────────────────────────────────
    elif category_id == 12:
        eq_configs = [
            ("PAYMENT_METHOD", "UPI", "payment method is UPI", "paid via UPI", "settled using UPI"),
            ("PAYMENT_METHOD", "CASH", "payment method is CASH", "paid via cash", "settled in cash"),
            ("PAYMENT_METHOD", "BANK_TRANSFER", "payment method is bank transfer", "paid via bank", "settled through bank transfer"),
            ("PAYMENT_STATUS", "PENDING", "payment status is pending", "pending payment collections", "deliveries waiting for payment"),
            ("PAYMENT_STATUS", "PAID", "payment status is paid", "cleared payment records", "deliveries that are paid"),
            ("UPLOAD_STATUS", "SYNCED", "upload status is synced", "synced collections", "entries uploaded to server"),
            ("UPLOAD_STATUS", "PENDING", "upload status is pending", "pending upload records", "unsynced collections"),
        ]
        f, val, desc1, desc2, desc3 = eq_configs[variant_idx % len(eq_configs)]
        use_entity = (variant_idx % 2 == 0)
        t_val, t_str = BASE_TIME_OPTIONS[(variant_idx // len(eq_configs)) % len(BASE_TIME_OPTIONS)]
        if use_entity:
            q = make_query_template(
                entity=entity,
                select=["FARMER_NAME", f],
                filters=[{"field": f, "op": "EQUALS", "value": val, "secondaryValue": None}]
            )
            prompts = [
                (f"Show {entity}'s records where {desc1}.", "standard"),
                (f"{entity} collections {desc2}.", "concise"),
                (f"List entries for {entity} that were {desc3}.", "conversational"),
                (f"Which collections for {entity} {desc1}?", "indian_english"),
                (f"{entity} {f.lower()} equals {val.lower()}", "short"),
            ]
        else:
            q = make_query_template(
                time_val=t_val,
                select=["FARMER_NAME", f],
                filters=[{"field": f, "op": "EQUALS", "value": val, "secondaryValue": None}]
            )
            prompts = [
                (f"Show all records where {desc1} {t_str}.", "standard"),
                (f"Collections {desc2} {t_str}.", "concise"),
                (f"List entries that were {desc3} {t_str}.", "conversational"),
                (f"Which collections {desc1} {t_str}?", "indian_english"),
                (f"{f.lower()} equals {val.lower()} {t_str}", "short"),
            ]
        for p, reg in prompts:
            out.append(QueryRealization(p, q, "equality_filter", reg))

    # ── 13. Null/not-null filters ─────────────────────────────────────────
    elif category_id == 13:
        null_fields = [
            ("PAYMENT_REFERENCE", "payment reference recorded", "non-null payment reference", "transaction ID present"),
            ("PAYMENT_TIMESTAMP", "payment timestamp recorded", "settlement date present", "payment timestamp non-null"),
            ("UPDATED_AT", "update timestamp present", "modified entries", "updated timestamp recorded"),
        ]
        f, d1, d2, d3 = null_fields[variant_idx % len(null_fields)]
        use_entity = (variant_idx % 2 == 0)
        t_val, t_str = BASE_TIME_OPTIONS[(variant_idx // len(null_fields)) % len(BASE_TIME_OPTIONS)]
        if use_entity:
            q = make_query_template(
                entity=entity,
                select=["FARMER_NAME", f],
                filters=[{"field": f, "op": "IS_NOT_NULL", "value": None, "secondaryValue": None}]
            )
            prompts = [
                (f"Show collections for {entity} that have a {d1}.", "standard"),
                (f"{entity} records with {d2}.", "concise"),
                (f"Which deliveries for {entity} already have a {d3}?", "conversational"),
                (f"{entity} {d1} present records list.", "indian_english"),
                (f"{entity} {f.lower()} not null", "short"),
            ]
        else:
            q = make_query_template(
                time_val=t_val,
                select=["FARMER_NAME", f],
                filters=[{"field": f, "op": "IS_NOT_NULL", "value": None, "secondaryValue": None}]
            )
            prompts = [
                (f"Show collections that have a {d1} {t_str}.", "standard"),
                (f"Records with {d2} {t_str}.", "concise"),
                (f"Which deliveries already have a {d3} {t_str}?", "conversational"),
                (f"{d1.capitalize()} present records list {t_str}.", "indian_english"),
                (f"{f.lower()} not null {t_str}", "short"),
            ]
        for p, reg in prompts:
            out.append(QueryRealization(p, q, "not_null_filter", reg))

    # ── 14. AND Logic Filter ──────────────────────────────────────────────
    elif category_id == 14:
        and_configs = [
            ("QUANTITY", "20.0", "FAT", "4.5", "quantity over 20 litres and fat above 4.5%", "quantity > 20 L and fat > 4.5%"),
            ("FAT", "4.0", "SNF", "8.5", "fat above 4.0% and SNF above 8.5", "fat > 4.0 and snf > 8.5"),
            ("QUANTITY", "15.0", "FAT", "4.0", "quantity over 15 litres and fat over 4.0%", "qty > 15 and fat > 4.0"),
            ("FAT", "3.8", "SNF", "8.2", "fat at least 3.8% and SNF at least 8.2", "fat >= 3.8 and snf >= 8.2"),
        ]
        f1, v1, f2, v2, d1, d2 = and_configs[variant_idx % len(and_configs)]
        use_entity = (variant_idx % 2 == 0)
        t_val, t_str = BASE_TIME_OPTIONS[(variant_idx // len(and_configs)) % len(BASE_TIME_OPTIONS)]
        if use_entity:
            q = make_query_template(
                entity=entity,
                select=["FARMER_NAME", f1, f2],
                filters=[
                    {"field": f1, "op": "GREATER_THAN", "value": v1, "secondaryValue": None},
                    {"field": f2, "op": "GREATER_THAN", "value": v2, "secondaryValue": None},
                ],
                filter_logic="AND"
            )
            prompts = [
                (f"Show records for {entity} with {d1}.", "standard"),
                (f"{entity} deliveries where {d2}.", "concise"),
                (f"Find entries for {entity} having {d1}.", "conversational"),
                (f"{entity} {d1} both matching records show.", "indian_english"),
                (f"{entity} {d2}", "short"),
            ]
        else:
            q = make_query_template(
                time_val=t_val,
                select=["FARMER_NAME", f1, f2],
                filters=[
                    {"field": f1, "op": "GREATER_THAN", "value": v1, "secondaryValue": None},
                    {"field": f2, "op": "GREATER_THAN", "value": v2, "secondaryValue": None},
                ],
                filter_logic="AND"
            )
            prompts = [
                (f"Show records with {d1} {t_str}.", "standard"),
                (f"Deliveries where {d2} {t_str}.", "concise"),
                (f"Find all entries having {d1} {t_str}.", "conversational"),
                (f"Records with {d1} {t_str} show.", "indian_english"),
                (f"{d2} {t_str}", "short"),
            ]
        for p, reg in prompts:
            out.append(QueryRealization(p, q, "and_logic_filter", reg))

    # ── 15. OR Logic Filter ───────────────────────────────────────────────
    elif category_id == 15:
        or_configs = [
            ("PAYMENT_STATUS", "PENDING", "PAYMENT_METHOD", "CASH", "payment is pending or payment method was cash", "payment pending OR method cash"),
            ("PAYMENT_STATUS", "PENDING", "PAYMENT_STATUS", "FAILED", "payment is pending or payment failed", "status pending OR failed"),
            ("PAYMENT_METHOD", "UPI", "PAYMENT_METHOD", "CASH", "paid by UPI or settled in cash", "method upi OR cash"),
            ("PAYMENT_METHOD", "BANK_TRANSFER", "PAYMENT_METHOD", "CHEQUE", "paid via bank transfer or cheque", "method bank OR cheque"),
        ]
        f1, v1, f2, v2, d1, d2 = or_configs[variant_idx % len(or_configs)]
        use_entity = (variant_idx % 2 == 0)
        t_val, t_str = BASE_TIME_OPTIONS[(variant_idx // len(or_configs)) % len(BASE_TIME_OPTIONS)]
        if use_entity:
            q = make_query_template(
                entity=entity,
                select=["FARMER_NAME", f1, f2],
                filters=[
                    {"field": f1, "op": "EQUALS", "value": v1, "secondaryValue": None},
                    {"field": f2, "op": "EQUALS", "value": v2, "secondaryValue": None},
                ],
                filter_logic="OR"
            )
            prompts = [
                (f"Show deliveries for {entity} where {d1}.", "standard"),
                (f"{entity} records with {d2}.", "concise"),
                (f"List entries for {entity} that have {d1}.", "conversational"),
                (f"{entity} {d1} collections.", "indian_english"),
                (f"{entity} {d2}", "short"),
            ]
        else:
            q = make_query_template(
                time_val=t_val,
                select=["FARMER_NAME", f1, f2],
                filters=[
                    {"field": f1, "op": "EQUALS", "value": v1, "secondaryValue": None},
                    {"field": f2, "op": "EQUALS", "value": v2, "secondaryValue": None},
                ],
                filter_logic="OR"
            )
            prompts = [
                (f"Show deliveries where {d1} {t_str}.", "standard"),
                (f"Records with {d2} {t_str}.", "concise"),
                (f"List entries that have {d1} {t_str}.", "conversational"),
                (f"Collections where {d1} {t_str}.", "indian_english"),
                (f"{d2} {t_str}", "short"),
            ]
        for p, reg in prompts:
            out.append(QueryRealization(p, q, "or_logic_filter", reg))

    # ── 16. GROUP BY ──────────────────────────────────────────────────────
    elif category_id == 16:
        gb_aggs = [
            ("SUM", "QUANTITY", "total milk"),
            ("AVG", "FAT", "average fat"),
            ("SUM", "AMOUNT_PAID", "total payout"),
        ]
        agg_type, f, desc = gb_aggs[variant_idx % len(gb_aggs)]
        t_val, t_str = BASE_TIME_OPTIONS[(variant_idx // len(gb_aggs)) % len(BASE_TIME_OPTIONS)]
        q = make_query_template(
            select=["FARMER_NAME", f],
            time_val=t_val,
            aggregations=[{"type": agg_type, "field": f}],
            group_by=["FARMER_NAME"]
        )
        prompts = [
            (f"Show {desc} grouped by farmer {t_str}.", "standard"),
            (f"Farmer-wise {desc} {t_str}.", "concise"),
            (f"Break down {desc} by each farmer {t_str}.", "conversational"),
            (f"Group by farmer {desc} {t_str} list.", "indian_english"),
            (f"group by farmer {agg_type.lower()} {f.lower()} {t_str}", "short"),
        ]
        for p, reg in prompts:
            out.append(QueryRealization(p, q, "group_by", reg))

    # ── 17. HAVING ────────────────────────────────────────────────────────
    elif category_id == 17:
        having_configs = [
            ("SUM", "QUANTITY", "GREATER_THAN", 50.0, "total milk > 50 L"),
            ("SUM", "QUANTITY", "GREATER_THAN", 100.0, "total milk > 100 L"),
            ("AVG", "FAT", "GREATER_THAN", 4.0, "average fat > 4.0"),
            ("AVG", "FAT", "GREATER_THAN", 4.2, "average fat > 4.2"),
        ]
        agg_type, f, op, val, desc = having_configs[variant_idx % len(having_configs)]
        t_val, t_str = BASE_TIME_OPTIONS[(variant_idx // len(having_configs)) % len(BASE_TIME_OPTIONS)]
        q = make_query_template(
            select=["FARMER_NAME", f],
            time_val=t_val,
            aggregations=[{"type": agg_type, "field": f}],
            group_by=["FARMER_NAME"],
            having={"agg": agg_type, "field": f, "op": op, "value": val}
        )
        prompts = [
            (f"Which farmers delivered {desc} {t_str}?", "standard"),
            (f"Farmers with {desc} {t_str}.", "concise"),
            (f"Show farmers having {desc} {t_str}.", "conversational"),
            (f"farmers having {agg_type.lower()} {f.lower()} > {val} {t_str}", "short"),
        ]
        for p, reg in prompts:
            out.append(QueryRealization(p, q, "having_filter", reg))

    # ── 18. ORDER BY field ────────────────────────────────────────────────
    elif category_id == 18:
        ob_configs = [
            ("QUANTITY", "DESC", "quantity from highest to lowest", "quantity descending", "largest quantity first"),
            ("FAT", "DESC", "fat percentage from highest to lowest", "fat descending", "highest fat first"),
            ("FAT", "ASC", "fat percentage from lowest to highest", "fat ascending", "lowest fat first"),
            ("AMOUNT_PAID", "DESC", "amount paid highest to lowest", "amount paid descending", "highest payment first"),
            ("CREATED_AT", "DESC", "creation date newest first", "date descending", "most recent first"),
        ]
        f, d, desc, short_desc, conv_desc = ob_configs[variant_idx % len(ob_configs)]
        use_entity = (variant_idx % 2 == 0)
        t_val, t_str = BASE_TIME_OPTIONS[(variant_idx // len(ob_configs)) % len(BASE_TIME_OPTIONS)]
        if use_entity:
            q = make_query_template(
                entity=entity,
                select=["FARMER_NAME", f],
                order_by={"target": "FIELD", "field": f, "dir": d}
            )
            prompts = [
                (f"Show collections for {entity} ordered by {desc}.", "standard"),
                (f"Sort deliveries for {entity} by {short_desc}.", "concise"),
                (f"List milk collections for {entity} arranged with the {conv_desc}.", "conversational"),
                (f"{entity} {desc} sort records.", "indian_english"),
                (f"{entity} order by {f.lower()} {d.lower()}", "short"),
            ]
        else:
            q = make_query_template(
                time_val=t_val,
                select=["FARMER_NAME", f],
                order_by={"target": "FIELD", "field": f, "dir": d}
            )
            prompts = [
                (f"Show collections ordered by {desc} {t_str}.", "standard"),
                (f"Sort deliveries by {short_desc} {t_str}.", "concise"),
                (f"List milk collections arranged with the {conv_desc} {t_str}.", "conversational"),
                (f"{desc.capitalize()} sort records {t_str}.", "indian_english"),
                (f"order by {f.lower()} {d.lower()} {t_str}", "short"),
            ]
        for p, reg in prompts:
            out.append(QueryRealization(p, q, "order_by_field", reg))

    # ── 19. ORDER BY aggregate ("Who gave the most milk?") ────────────────
    elif category_id == 19:
        agg_ob_configs = [
            ("SUM", "QUANTITY", "DESC", "Who gave the most milk {time}?"),
            ("AVG", "FAT", "DESC", "Who had the highest average fat {time}?"),
            ("SUM", "AMOUNT_PAID", "DESC", "Who received the highest total payment {time}?"),
            ("SUM", "QUANTITY", "ASC", "Who supplied the lowest total milk {time}?"),
        ]
        agg_type, f, d, p1_tmpl = agg_ob_configs[variant_idx % len(agg_ob_configs)]
        t_val, t_str = BASE_TIME_OPTIONS[(variant_idx // len(agg_ob_configs)) % len(BASE_TIME_OPTIONS)]
        q = make_query_template(
            select=["FARMER_NAME", f],
            time_val=t_val,
            aggregations=[{"type": agg_type, "field": f}],
            group_by=["FARMER_NAME"],
            order_by={"target": "AGGREGATE", "aggregation": agg_type, "field": f, "dir": d},
            limit=1
        )
        p1 = p1_tmpl.format(time=t_str)
        prompts = [
            (p1, "standard"),
            (f"Top farmer by {agg_type.lower()} {f.lower()} {t_str}.", "concise"),
            (f"Which farmer had the best {agg_type.lower()} {f.lower()} {t_str}?", "conversational"),
            (f"highest {agg_type.lower()} {f.lower()} {t_str}", "short"),
        ]
        for p, reg in prompts:
            out.append(QueryRealization(p, q, "order_by_aggregate", reg))

    # ── 20. LIMIT ─────────────────────────────────────────────────────────
    elif category_id == 20:
        limits = [
            (3, "TODAY", "top 3 milk suppliers today", "Top 3 farmers by milk collection today", "today"),
            (5, "THIS_WEEK", "top 5 milk suppliers this week", "Top 5 farmers by total milk collection this week", "this week"),
            (10, "THIS_MONTH", "top 10 total milk producers this month", "Top 10 farmers by sum of milk this month", "this month"),
            (3, "YESTERDAY", "top 3 milk suppliers yesterday", "Top 3 farmers by total milk yesterday", "yesterday"),
            (5, "LAST_WEEK", "top 5 milk suppliers last week", "Top 5 farmers by total milk last week", "last week"),
        ]
        k, p_enum, desc, short_desc, time_str = limits[variant_idx % len(limits)]
        use_entity = (variant_idx % 2 == 0)
        if use_entity:
            q = make_query_template(
                entity=entity,
                select=["FARMER_NAME", "QUANTITY"],
                order_by={"target": "FIELD", "field": "QUANTITY", "dir": "DESC"},
                limit=k
            )
            prompts = [
                (f"Show the top {k} deliveries by quantity for {entity}.", "standard"),
                (f"Top {k} collections for {entity}.", "concise"),
                (f"List the largest {k} deliveries brought by {entity}.", "conversational"),
                (f"{entity} top {k} quantity deliveries show.", "indian_english"),
                (f"{entity} top {k} qty", "short"),
            ]
        else:
            q = make_query_template(
                select=["FARMER_NAME", "QUANTITY"],
                time_val={"type": "RELATIVE", "period": p_enum},
                aggregations=[{"type": "SUM", "field": "QUANTITY"}],
                group_by=["FARMER_NAME"],
                order_by={"target": "AGGREGATE", "aggregation": "SUM", "field": "QUANTITY", "dir": "DESC"},
                limit=k
            )
            prompts = [
                (f"Who are the {desc}?", "standard"),
                (f"{short_desc}.", "concise"),
                (f"Show me the {desc}.", "conversational"),
                (f"{desc.capitalize()} list please.", "indian_english"),
                (f"top {k} milk farmers {time_str}", "short"),
            ]
        for p, reg in prompts:
            out.append(QueryRealization(p, q, "limit_query", reg))

    # ── 21. EXISTS ────────────────────────────────────────────────────────
    elif category_id == 21:
        periods = [("TODAY", "today"), ("YESTERDAY", "yesterday"), ("THIS_WEEK", "this week")]
        p_enum, p_txt = periods[variant_idx % len(periods)]
        q = make_query_template(
            entity=entity,
            time_val={"type": "RELATIVE", "period": p_enum},
            exists=True
        )
        prompts = [
            (f"Did {entity} deliver any milk {p_txt}?", "standard"),
            (f"{entity} delivery exists {p_txt}?", "concise"),
            (f"Has {entity} brought milk {p_txt}?", "conversational"),
            (f"{p_txt.capitalize()} {entity} given milk or not?", "indian_english"),
            (f"{entity} present {p_txt}?", "short"),
        ]
        for p, reg in prompts:
            out.append(QueryRealization(p, q, "exists_query", reg))

    # ── 22. Payment status ────────────────────────────────────────────────
    elif category_id == 22:
        q = make_query_template(
            entity=entity,
            select=["PAYMENT_STATUS"],
            payment_scope="LATEST"
        )
        prompts = [
            (f"What is the payment status for {entity}?", "standard"),
            (f"{entity} payment status.", "concise"),
            (f"Has {entity} been paid for their collection?", "conversational"),
            (f"{entity} payment done or pending?", "indian_english"),
            (f"{entity} payment status", "short"),
        ]
        for p, reg in prompts:
            out.append(QueryRealization(p, q, "payment_status", reg))

    # ── 23. Payment method ────────────────────────────────────────────────
    elif category_id == 23:
        q = make_query_template(
            entity=entity,
            select=["PAYMENT_METHOD"],
            payment_scope="LATEST"
        )
        prompts = [
            (f"What payment method was used for {entity}?", "standard"),
            (f"Payment mode for {entity}.", "concise"),
            (f"How did we pay {entity} for their milk?", "conversational"),
            (f"{entity} payment cash or upi?", "indian_english"),
            (f"{entity} payment method", "short"),
        ]
        for p, reg in prompts:
            out.append(QueryRealization(p, q, "payment_method", reg))

    # ── 24. Payment reference ─────────────────────────────────────────────
    elif category_id == 24:
        q = make_query_template(
            entity=entity,
            select=["PAYMENT_REFERENCE"],
            payment_scope="LATEST"
        )
        prompts = [
            (f"What is the payment reference number for {entity}?", "standard"),
            (f"Payment reference ID for {entity}.", "concise"),
            (f"Can you look up the transaction reference for {entity}?", "conversational"),
            (f"{entity} transaction ref number tell.", "indian_english"),
            (f"{entity} payment ref", "short"),
        ]
        for p, reg in prompts:
            out.append(QueryRealization(p, q, "payment_reference", reg))

    # ── 25. Amount paid ───────────────────────────────────────────────────
    elif category_id == 25:
        q = make_query_template(
            entity=entity,
            select=["AMOUNT_PAID"],
            payment_scope="LATEST"
        )
        prompts = [
            (f"How much was paid to {entity}?", "standard"),
            (f"Amount paid to {entity}.", "concise"),
            (f"What payout did {entity} receive?", "conversational"),
            (f"{entity} how much amount paid?", "indian_english"),
            (f"{entity} amount paid", "short"),
        ]
        for p, reg in prompts:
            out.append(QueryRealization(p, q, "amount_paid", reg))

    # ── 26. Payment timestamp ─────────────────────────────────────────────
    elif category_id == 26:
        q = make_query_template(
            entity=entity,
            select=["PAYMENT_TIMESTAMP"],
            payment_scope="LATEST"
        )
        prompts = [
            (f"When was {entity} paid?", "standard"),
            (f"Payment timestamp for {entity}.", "concise"),
            (f"What date and time was {entity}'s payment processed?", "conversational"),
            (f"{entity} payment date and time when done?", "indian_english"),
            (f"{entity} payment time", "short"),
        ]
        for p, reg in prompts:
            out.append(QueryRealization(p, q, "payment_timestamp", reg))

    # ── 27. Upload status ─────────────────────────────────────────────────
    elif category_id == 27:
        sync_modes = [
            ("PENDING", "pending upload to the server", "Unsynced records list.", "have not uploaded yet"),
            ("SYNCED", "synced with the central server", "Synced records list.", "have already uploaded"),
            ("SYNC_FAILED", "marked as sync failed", "Failed sync records list.", "encountered errors while uploading"),
        ]
        st, d1, d2, d3 = sync_modes[variant_idx % len(sync_modes)]
        use_entity = (variant_idx % 2 == 0)
        t_val, t_str = BASE_TIME_OPTIONS[(variant_idx // len(sync_modes)) % len(BASE_TIME_OPTIONS)]
        if use_entity:
            q = make_query_template(
                entity=entity,
                select=["UPLOAD_STATUS"],
                filters=[{"field": "UPLOAD_STATUS", "op": "EQUALS", "value": st, "secondaryValue": None}]
            )
            prompts = [
                (f"Check if {entity}'s records are {d1}.", "standard"),
                (f"{entity} upload status {st.lower()}.", "concise"),
                (f"Have {entity}'s entries uploaded or are they {st.lower()}?", "conversational"),
                (f"{entity} records sync {st.lower()}?", "indian_english"),
                (f"{entity} upload status {st.lower()}", "short"),
            ]
        else:
            q = make_query_template(
                time_val=t_val,
                select=["UPLOAD_STATUS"],
                filters=[{"field": "UPLOAD_STATUS", "op": "EQUALS", "value": st, "secondaryValue": None}]
            )
            prompts = [
                (f"Which records are {d1} {t_str}?", "standard"),
                (f"{d2} {t_str}", "concise"),
                (f"Are there any collection entries that {d3} {t_str}?", "conversational"),
                (f"Which records sync {st.lower()} {t_str}?", "indian_english"),
                (f"upload status {st.lower()} records {t_str}", "short"),
            ]
        for p, reg in prompts:
            out.append(QueryRealization(p, q, "upload_status", reg))

    # ── 28. Multi-aggregation ─────────────────────────────────────────────
    elif category_id == 28:
        multi_aggs = [
            ("QUANTITY", "SUM", "FAT", "AVG", "total milk and average fat", "sum qty and avg fat"),
            ("QUANTITY", "SUM", "AMOUNT_PAID", "SUM", "total milk and total payout", "sum qty and sum amount"),
            ("FAT", "MIN", "FAT", "MAX", "minimum and maximum fat percentage", "min fat and max fat"),
            ("SNF", "AVG", "FAT", "AVG", "average SNF and average fat", "avg snf and avg fat"),
        ]
        f1, a1, f2, a2, desc, short_desc = multi_aggs[variant_idx % len(multi_aggs)]
        q = make_query_template(
            entity=entity,
            select=[f1, f2],
            time_val={"type": "RELATIVE", "period": "THIS_WEEK"},
            aggregations=[
                {"type": a1, "field": f1},
                {"type": a2, "field": f2},
            ]
        )
        prompts = [
            (f"What is {entity}'s {desc} this week?", "standard"),
            (f"{desc.capitalize()} for {entity} this week.", "concise"),
            (f"Calculate both {desc} for {entity} this week.", "conversational"),
            (f"{entity} {desc} for this week show.", "indian_english"),
            (f"{entity} {short_desc} this week", "short"),
        ]
        for p, reg in prompts:
            out.append(QueryRealization(p, q, "multi_aggregation", reg))

    # ── 29. Payment scope (LATEST vs ALL vs ANY) ──────────────────────────
    elif category_id == 29:
        if variant_idx % 2 == 0:
            q_all = make_query_template(
                entity=entity,
                select=["PAYMENT_STATUS"],
                payment_scope="ALL"
            )
            out.append(QueryRealization(f"Are all of {entity}'s payments completed?", q_all, "payment_scope_all", "standard"))
            out.append(QueryRealization(f"Have all payments cleared for {entity}?", q_all, "payment_scope_all", "conversational"))
            out.append(QueryRealization(f"{entity} all deliveries payment done?", q_all, "payment_scope_all", "indian_english"))
        else:
            q_any = make_query_template(
                entity=entity,
                select=["PAYMENT_STATUS"],
                time_val={"type": "RELATIVE", "period": "THIS_WEEK"},
                payment_scope="ANY"
            )
            out.append(QueryRealization(f"Did {entity} receive any payment this week?", q_any, "payment_scope_any", "standard"))
            out.append(QueryRealization(f"Has any payment been made to {entity} this week?", q_any, "payment_scope_any", "conversational"))
            out.append(QueryRealization(f"{entity} any payment received this week?", q_any, "payment_scope_any", "indian_english"))

    # ── 30. Conditional information requests ──────────────────────────────
    elif category_id == 30:
        q = make_query_template(
            entity=entity,
            conditional_spec={
                "conditionField": "PAYMENT_STATUS",
                "expectedValue": "PAID",
                "fieldsOnMatch": ["PAYMENT_METHOD", "PAYMENT_REFERENCE"],
                "fieldsOnMismatch": ["PAYABLE_AMOUNT", "PAYMENT_STATUS"]
            }
        )
        prompts = [
            (f"If {entity} was paid, tell me the method and reference, otherwise show pending amount.", "standard"),
            (f"Check payment for {entity}: show method and reference if paid, else pending amount.", "concise"),
            (f"If {entity} is paid give transaction details, if not paid tell me how much is pending.", "conversational"),
            (f"{entity} paid means give ref and method, pending means how much payable?", "indian_english"),
        ]
        for p, reg in prompts:
            out.append(QueryRealization(p, q, "conditional_query", reg))

    # ── 31. Explicit dates ────────────────────────────────────────────────
    elif category_id == 31:
        dates = [
            "2026-09-01", "2026-09-05", "2026-09-10", "2026-09-15",
            "2026-09-18", "2026-09-20", "2026-09-25", "2026-09-28", "2026-09-29", "2026-09-30"
        ]
        date_str = dates[variant_idx % len(dates)]
        q = make_query_template(
            entity=entity,
            select=["QUANTITY"],
            time_val={"type": "EXPLICIT_DATE", "date": date_str}
        )
        prompts = [
            (f"How much milk did {entity} deliver on {date_str}?", "standard"),
            (f"{entity} milk collection on {date_str}.", "concise"),
            (f"Check {entity}'s milk delivery for {date_str}.", "conversational"),
            (f"On {date_str} {entity} how much milk given?", "indian_english"),
            (f"{entity} {date_str} quantity", "short"),
        ]
        for p, reg in prompts:
            out.append(QueryRealization(p, q, "explicit_date", reg))

    # ── 32. Explicit date ranges ──────────────────────────────────────────
    elif category_id == 32:
        ranges = [
            ("2026-09-01", "2026-09-07"),
            ("2026-09-08", "2026-09-14"),
            ("2026-09-15", "2026-09-21"),
            ("2026-09-22", "2026-09-28"),
            ("2026-09-01", "2026-09-15"),
            ("2026-09-16", "2026-09-30"),
        ]
        start_d, end_d = ranges[variant_idx % len(ranges)]
        q = make_query_template(
            entity=entity,
            select=["QUANTITY"],
            time_val={"type": "EXPLICIT_RANGE", "startDate": start_d, "endDate": end_d},
            aggregations=[{"type": "SUM", "field": "QUANTITY"}]
        )
        prompts = [
            (f"How much milk did {entity} deliver between {start_d} and {end_d}?", "standard"),
            (f"Total milk for {entity} from {start_d} to {end_d}.", "concise"),
            (f"Sum of collections for {entity} between {start_d} and {end_d}.", "conversational"),
            (f"{entity} milk total from {start_d} to {end_d} how much?", "indian_english"),
            (f"{entity} sum {start_d} to {end_d}", "short"),
        ]
        for p, reg in prompts:
            out.append(QueryRealization(p, q, "explicit_range", reg))

    # ── 33. Relative dates (comprehensive coverage) ───────────────────────
    elif category_id == 33:
        periods = [
            ("TODAY", "today"),
            ("YESTERDAY", "yesterday"),
            ("THIS_WEEK", "this week"),
            ("LAST_WEEK", "last week"),
            ("THIS_MONTH", "this month"),
            ("LAST_MONTH", "last month"),
            ("SINCE_MONDAY", "since Monday"),
            ("LAST_SUNDAY", "last Sunday"),
        ]
        period_enum, p_desc = periods[variant_idx % len(periods)]
        q = make_query_template(
            entity=entity,
            select=["QUANTITY"],
            time_val={"type": "RELATIVE", "period": period_enum},
            aggregations=[{"type": "SUM", "field": "QUANTITY"}]
        )
        prompts = [
            (f"Total milk collected for {entity} {p_desc}.", "standard"),
            (f"Sum of quantity for {entity} {p_desc}.", "concise"),
            (f"How many total litres did {entity} bring {p_desc}?", "conversational"),
            (f"{p_desc.capitalize()} {entity} total milk collection how much?", "indian_english"),
            (f"{entity} total milk {p_desc}", "short"),
        ]
        for p, reg in prompts:
            out.append(QueryRealization(p, q, "relative_date", reg))

    # ── 34. Read / Write Distinction (Mandatory Hard Negatives) ───────────
    elif category_id == 34:
        volumes = ["25.0", "20.0", "15.0", "30.0"]
        vol = volumes[variant_idx % len(volumes)]
        q_w = make_query_template(
            req_type="WRITE",
            write_args={"farmerName": entity, "quantity": vol, "paymentStatus": "PENDING"}
        )
        out.append(QueryRealization(f"Record {vol} litres for {entity}.", q_w, "read_write_contrast", "command_write"))
        out.append(QueryRealization(f"Add {vol} litres milk collection for {entity}.", q_w, "read_write_contrast", "command_write"))

        q_r = make_query_template(
            req_type="QUERY",
            entity=entity,
            select=["QUANTITY"],
            filters=[{"field": "QUANTITY", "op": "EQUALS", "value": vol, "secondaryValue": None}],
            exists=True
        )
        out.append(QueryRealization(f"Did {entity} give {vol} litres?", q_r, "read_write_contrast", "query_read"))
        out.append(QueryRealization(f"Was a {vol} litre collection recorded for {entity}?", q_r, "read_write_contrast", "query_read"))

    # ── 35. Ambiguous entity (CLARIFY) ────────────────────────────────────
    elif category_id == 35:
        q = make_query_template(
            req_type="CLARIFY",
            reason=f"Entity mention '{entity}' is ambiguous. Multiple matching farmers exist."
        )
        prompts = [
            (f"How much did {entity} give today?", "ambiguous_entity"),
            (f"Show {entity}'s payment.", "ambiguous_entity"),
            (f"What was {entity}'s fat content?", "ambiguous_entity"),
            (f"Check milk records for {entity}.", "ambiguous_entity"),
        ]
        for p, reg in prompts:
            out.append(QueryRealization(p, q, "ambiguous_entity", reg))

    # ── 36. Unknown entity (CLARIFY / NOT_FOUND) ──────────────────────────
    elif category_id == 36:
        unknown_name = get_unknown_name(split, variant_idx)
        q = make_query_template(
            req_type="CLARIFY",
            reason=f"Farmer '{unknown_name}' is not registered in the local database."
        )
        prompts = [
            (f"What did {unknown_name} give today?", "unknown_entity"),
            (f"Check milk records for {unknown_name}.", "unknown_entity"),
            (f"Has {unknown_name} been paid?", "unknown_entity"),
            (f"Did {unknown_name} bring milk yesterday?", "unknown_entity"),
            (f"Show {unknown_name} collection details.", "unknown_entity"),
            (f"How much milk did {unknown_name} deliver this week?", "unknown_entity"),
            (f"What was the fat reading for {unknown_name}?", "unknown_entity"),
            (f"Is {unknown_name} registered in the system?", "unknown_entity"),
            (f"Search collection history for farmer {unknown_name}.", "unknown_entity"),
            (f"Show total milk volume supplied by {unknown_name}.", "unknown_entity"),
        ]
        for p, reg in prompts:
            out.append(QueryRealization(p, q, "unknown_entity", reg))

    # ── 37. Missing / Unspecified Field (CLARIFY) ─────────────────────────
    elif category_id == 37:
        q = make_query_template(
            req_type="CLARIFY",
            reason=f"Request about {entity} leaves field unspecified; clarify what collection metric or field to retrieve."
        )
        prompts = [
            (f"Tell me about {entity}.", "missing_field"),
            (f"Show {entity}.", "missing_field"),
            (f"What about {entity} today?", "missing_field"),
            (f"Details on {entity} please.", "missing_field"),
            (f"Can you check {entity}?", "missing_field"),
        ]
        for p, reg in prompts:
            out.append(QueryRealization(p, q, "missing_field", reg))

    # ── 38. Unsupported requests (Explicit Reasons) ───────────────────────
    elif category_id == 38:
        mode = variant_idx % 3
        if mode == 0:
            q_delay = make_query_template(
                req_type="UNSUPPORTED",
                reason="Payment delay reasons are not tracked in the local operational database."
            )
            out.append(QueryRealization(f"Why was {entity}'s payment delayed?", q_delay, "unsupported_request", "delay_reason"))
            out.append(QueryRealization(f"What caused the payment delay for {entity}?", q_delay, "unsupported_request", "delay_reason"))
        elif mode == 1:
            q_cause = make_query_template(
                req_type="UNSUPPORTED",
                reason="Causal explanations for milk yield variations are not stored in the database."
            )
            out.append(QueryRealization(f"Why did {entity} give less milk today?", q_cause, "unsupported_request", "causality"))
            out.append(QueryRealization(f"What is the reason {entity}'s fat dropped?", q_cause, "unsupported_request", "causality"))
        else:
            q_hypo = make_query_template(
                req_type="UNSUPPORTED",
                reason="Hypothetical counterfactual queries are unsupported under declarative query semantics."
            )
            out.append(QueryRealization(f"What if the milk price was ₹50 for {entity}?", q_hypo, "unsupported_request", "hypothetical"))
            out.append(QueryRealization(f"What if base rate increased by 10%: how much would {entity} earn?", q_hypo, "unsupported_request", "hypothetical"))

    # ── 39. Conversational Pronoun (CLARIFY) ──────────────────────────────
    elif category_id == 39:
        pronoun_configs = [
            ("he", "his", "him"),
            ("she", "her", "her"),
            ("they", "their", "them"),
        ]
        times = [
            "today", "yesterday", "this week", "last week", "this month",
            "last month", "since Monday", "last Sunday", "on Monday", "on Tuesday",
            "on Wednesday", "on Thursday", "on Friday", "on Saturday",
            "in the morning", "in the evening", "this morning", "yesterday evening"
        ]
        p_sub, p_poss, p_obj = pronoun_configs[variant_idx % len(pronoun_configs)]
        t_str = times[(variant_idx // len(pronoun_configs)) % len(times)]
        q = make_query_template(
            req_type="CLARIFY",
            reason="Ambiguous pronoun 'he/she/they' without explicit entity mention in prompt."
        )
        prompts = [
            (f"How much milk did {p_sub} give {t_str}?", "pronoun_clarify"),
            (f"What was {p_poss} total milk quantity {t_str}?", "pronoun_clarify"),
            (f"Did {p_sub} bring any milk {t_str}?", "pronoun_clarify"),
            (f"Check {p_poss} milk record {t_str}.", "pronoun_clarify"),
            (f"Has {p_poss} payment been settled {t_str}?", "pronoun_clarify"),
            (f"Did {p_sub} receive payment {t_str}?", "pronoun_clarify"),
            (f"What was {p_poss} fat reading {t_str}?", "pronoun_clarify"),
            (f"Did {p_sub} deliver in the morning {t_str}?", "pronoun_clarify"),
        ]
        for p, reg in prompts:
            out.append(QueryRealization(p, q, "conversational_pronoun", reg))

    # ── 40-44. Linguistic Variations (Paraphrases, Indian-English, Grammar) ─
    elif category_id in {40, 41, 42, 43, 44}:
        q = make_query_template(
            entity=entity,
            select=["QUANTITY"],
            time_val={"type": "RELATIVE", "period": "THIS_WEEK"},
            aggregations=[{"type": "SUM", "field": "QUANTITY"}]
        )
        if category_id == 40:  # Indian-English phrasing
            prompts = [
                (f"{entity} total milk supply for this week how much done?", "indian_english"),
                (f"{entity} this week milk given total?", "indian_english"),
                (f"This week {entity} supplied how many litres?", "indian_english"),
            ]
        elif category_id == 41:  # Bad grammar
            prompts = [
                (f"{entity.lower()} milk total how much give this week", "bad_grammar"),
                (f"{entity} this week how many litre milk deliver", "bad_grammar"),
                (f"how much milk give {entity} this week", "bad_grammar"),
            ]
        elif category_id == 42:  # Short queries
            prompts = [
                (f"{entity} week total", "short"),
                (f"{entity} sum milk week", "short"),
                (f"{entity} this week qty", "short"),
            ]
        elif category_id == 43:  # Natural word-order variation
            prompts = [
                (f"This week, what was {entity}'s total milk delivery?", "word_order"),
                (f"For this week, tell me {entity}'s total milk collection.", "word_order"),
                (f"Total milk collection of {entity} for this week: how much is it?", "word_order"),
            ]
        else:  # Paraphrases
            prompts = [
                (f"{entity}'s total milk quantity for this week.", "paraphrase"),
                (f"How much milk was supplied by {entity} this week?", "paraphrase"),
                (f"What volume of milk has {entity} delivered over this week?", "paraphrase"),
            ]
        for p, reg in prompts:
            out.append(QueryRealization(p, q, "linguistic_variation", reg))

    # ── 45. Multi-condition queries ───────────────────────────────────────
    elif category_id == 45:
        cond_configs = [
            ("FAT", "4.2", "SNF", "8.5", "THIS_WEEK", "fat above 4.2% and SNF above 8.5%"),
            ("FAT", "4.0", "QUANTITY", "20.0", "TODAY", "fat over 4.0% and quantity over 20 litres"),
            ("QUANTITY", "15.0", "FAT", "4.5", "YESTERDAY", "quantity above 15 L and fat exceeding 4.5%"),
            ("SNF", "8.2", "FAT", "3.8", "THIS_MONTH", "SNF at least 8.2 and fat at least 3.8%"),
        ]
        f1, v1, f2, v2, p_enum, desc = cond_configs[variant_idx % len(cond_configs)]
        p_txt = p_enum.lower().replace("_", " ")
        q = make_query_template(
            entity=entity,
            select=["FARMER_NAME", f1, f2],
            time_val={"type": "RELATIVE", "period": p_enum},
            filters=[
                {"field": f1, "op": "GREATER_THAN", "value": v1, "secondaryValue": None},
                {"field": f2, "op": "GREATER_THAN", "value": v2, "secondaryValue": None},
            ],
            filter_logic="AND"
        )
        prompts = [
            (f"Show deliveries for {entity} {p_txt} with {desc}.", "standard"),
            (f"{entity} records {p_txt} where {f1.lower()} > {v1} and {f2.lower()} > {v2}.", "concise"),
            (f"Find {entity}'s collections {p_txt} meeting: {desc}.", "conversational"),
            (f"{entity} {p_txt} {desc} matching records.", "indian_english"),
        ]
        for p, reg in prompts:
            out.append(QueryRealization(p, q, "multi_condition", reg))

    # ── 46. Compositional queries (Held-out from training!) ───────────────
    elif category_id == 46:
        filt_options = [
            ({"field": "FAT", "op": "GREATER_THAN", "value": "4.0", "secondaryValue": None}, "fat above 4.0%", "fat > 4.0"),
            ({"field": "FAT", "op": "GREATER_THAN", "value": "4.2", "secondaryValue": None}, "fat over 4.2%", "fat > 4.2"),
            ({"field": "FAT", "op": "GREATER_THAN", "value": "4.5", "secondaryValue": None}, "fat exceeding 4.5%", "fat > 4.5"),
            ({"field": "SNF", "op": "GREATER_THAN", "value": "8.5", "secondaryValue": None}, "SNF above 8.5", "snf > 8.5"),
            ({"field": "SNF", "op": "GREATER_THAN", "value": "8.8", "secondaryValue": None}, "SNF over 8.8", "snf > 8.8"),
            ({"field": "QUANTITY", "op": "GREATER_THAN", "value": "15.0", "secondaryValue": None}, "quantity over 15 litres", "qty > 15"),
            ({"field": "QUANTITY", "op": "GREATER_THAN", "value": "20.0", "secondaryValue": None}, "quantity over 20 litres", "qty > 20"),
            ({"field": "QUANTITY", "op": "GREATER_THAN", "value": "25.0", "secondaryValue": None}, "quantity exceeding 25 litres", "qty > 25"),
            ({"field": "AMOUNT_PAID", "op": "GREATER_THAN", "value": "1000", "secondaryValue": None}, "amount paid above 1000 rupees", "amount > 1000"),
            ({"field": "AMOUNT_PAID", "op": "GREATER_THAN", "value": "2000", "secondaryValue": None}, "payout exceeding 2000", "amount > 2000"),
            ({"field": "PAYMENT_STATUS", "op": "EQUALS", "value": "PAID", "secondaryValue": None}, "paid deliveries", "payment paid"),
        ]
        time_options = [
            ("THIS_WEEK", "this week"), ("TODAY", "today"), ("YESTERDAY", "yesterday"),
            ("THIS_MONTH", "this month"), ("LAST_WEEK", "last week"), ("LAST_MONTH", "last month"),
            ("SINCE_MONDAY", "since Monday"), ("LAST_SUNDAY", "last Sunday")
        ]
        agg_options = [
            ("SUM", "QUANTITY", "total milk", "sum of quantity"),
            ("AVG", "FAT", "average fat", "mean fat"),
            ("SUM", "AMOUNT_PAID", "total payout", "sum of amount paid"),
            ("AVG", "SNF", "average SNF", "mean SNF"),
        ]
        limit_options = [3, 5, 10]

        f_idx = variant_idx % len(filt_options)
        t_idx = (variant_idx // len(filt_options)) % len(time_options)
        a_idx = (variant_idx // (len(filt_options) * len(time_options))) % len(agg_options)
        l_idx = (variant_idx // (len(filt_options) * len(time_options) * len(agg_options))) % len(limit_options)

        filt, f_desc1, f_desc2 = filt_options[f_idx]
        p_enum, t_str = time_options[t_idx]
        agg_type, agg_field, agg_desc1, agg_desc2 = agg_options[a_idx]
        lim = limit_options[l_idx]

        q = make_query_template(
            select=["FARMER_NAME", agg_field],
            time_val={"type": "RELATIVE", "period": p_enum},
            filters=[filt],
            aggregations=[{"type": agg_type, "field": agg_field}],
            group_by=["FARMER_NAME"],
            order_by={"target": "AGGREGATE", "aggregation": agg_type, "field": agg_field, "dir": "DESC"},
            limit=lim
        )
        prompts = [
            (f"Top {lim} farmers by {agg_desc1} {t_str} whose deliveries had {f_desc1}.", "standard"),
            (f"Show top {lim} farmers {t_str} by {agg_desc2} where {f_desc2}.", "concise"),
            (f"Who are the top {lim} milk producers {t_str} among deliveries with {f_desc1}?", "conversational"),
            (f"{t_str.capitalize()} top {lim} farmers {agg_desc1} with {f_desc2} list.", "indian_english"),
        ]
        for p, reg in prompts:
            out.append(QueryRealization(p, q, "compositional_query", reg))

    return out
