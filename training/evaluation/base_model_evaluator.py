"""
Base Qwen3 1.7B Model Evaluator for FREEDOM Query Dataset.
Executes prompt inference with system instructions, extracts JSON, and calculates fine-grained AST accuracy.
"""

import argparse
import json
import os
import sys
from pathlib import Path
from typing import Any, Dict, List, Optional
from evaluation.metrics import QueryMetrics
from generators.schema_encoder import canonicalize_query

SYSTEM_PROMPT = """You are FREEDOM AI Query Parser for a dairy field collection database.
Parse the user's natural language question into a JSON structured query.
Output ONLY raw JSON. No thinking tags, no reasoning, no markdown.

JSON Schema keys:
- type: "QUERY" | "WRITE" | "CLARIFY" | "UNSUPPORTED"
- target: "MILK_RECORDS" | "WORKER_PROFILE" | "ORGANIZATION_INFO" | "KNOWLEDGE_BASE"
- entity: string or null
- entityScope: "SPECIFIC" | "ALL"
- select: array of valid fields
- filters: array of filter objects
- filterLogic: "AND" | "OR"
- time: object with type, period, date, startDate, endDate or null
- aggregations: array of aggregation objects
- groupBy: array of fields
- havingFilter: having object or null
- orderBy: orderBy object or null
- limit: integer or null
- exists: boolean
- paymentScope: "LATEST" | "ALL" | "ANY"
- conditionalSpec: conditional object or null
- writeArgs: object or empty
- knowledgeQuery: string or null
- reason: string or null
"""


def parse_model_json(raw_text: str) -> Optional[Dict[str, Any]]:
    """Strips thinking tags or markdown fences and extracts raw JSON object."""
    clean = raw_text.strip()
    if "</think>" in clean:
        clean = clean.split("</think>")[-1].strip()
    if "```json" in clean:
        clean = clean.split("```json")[-1].split("```")[0].strip()
    elif "```" in clean:
        clean = clean.split("```")[-1].split("```")[0].strip()

    start = clean.find("{")
    end = clean.rfind("}")
    if start != -1 and end != -1 and end > start:
        clean = clean[start:end + 1]

    try:
        parsed = json.loads(clean)
        if isinstance(parsed, dict):
            return canonicalize_query(parsed)
    except Exception:
        pass
    return None


def run_evaluation(
    dataset_path: str,
    output_report_path: Optional[str] = None,
    max_samples: Optional[int] = None
) -> Dict[str, Any]:
    """
    Evaluates dataset against Qwen3 model.
    Checks environment capabilities and falls back to diagnostic baseline if PyTorch/CUDA is unavailable.
    """
    # 1. Load dataset
    samples = []
    with open(dataset_path, "r", encoding="utf-8") as f:
        for line in f:
            if line.strip():
                samples.append(json.loads(line))
                if max_samples and len(samples) >= max_samples:
                    break

    print(f"Loaded {len(samples)} evaluation samples from {dataset_path}")

    # 2. Check for PyTorch & Transformers
    torch_available = False
    try:
        import torch
        import transformers
        torch_available = True
    except ImportError:
        pass

    if not torch_available:
        print("[INFO] PyTorch / Transformers is not installed in the local Windows environment.")
        print("[INFO] Reporting environment status and generating reproducible GPU evaluation command.")
        return {
            "status": "ENVIRONMENT_UNSUPPORTED",
            "message": "Local Python 3.14 on Windows lacks PyTorch CUDA binary. Use reproducible Linux/Colab command.",
            "samples_count": len(samples),
            "reproducible_command": (
                "python -m evaluation.base_model_evaluator "
                "--model Qwen/Qwen3-1.7B "
                "--dataset dataset/test.jsonl "
                "--output reports/base_model_evaluation.json"
            )
        }

    # 3. Model execution logic if torch is available
    import torch
    from transformers import AutoModelForCausalLM, AutoTokenizer

    model_name = "Qwen/Qwen3-1.7B"
    print(f"Loading {model_name}...")
    tokenizer = AutoTokenizer.from_pretrained(model_name, trust_remote_code=True)
    model = AutoModelForCausalLM.from_pretrained(
        model_name,
        device_map="auto",
        torch_dtype=torch.bfloat16,
        trust_remote_code=True
    )
    model.eval()

    metrics = QueryMetrics()
    for idx, sample in enumerate(samples):
        prompt = sample["prompt"]
        target = sample["target"]

        messages = [
            {"role": "system", "content": SYSTEM_PROMPT},
            {"role": "user", "content": prompt}
        ]
        text_prompt = tokenizer.apply_chat_template(messages, tokenize=False, add_generation_prompt=True)
        inputs = tokenizer([text_prompt], return_tensors="pt").to(model.device)

        with torch.no_grad():
            generated_ids = model.generate(
                **inputs,
                max_new_tokens=256,
                temperature=0.0,  # deterministic greedy decoding
                do_sample=False
            )
            generated_ids = [
                output_ids[len(input_ids):] for input_ids, output_ids in zip(inputs.input_ids, generated_ids)
            ]
            response = tokenizer.batch_decode(generated_ids, skip_special_tokens=True)[0]

        pred = parse_model_json(response) or {}
        metrics.evaluate_pair(pred, target)

        if (idx + 1) % 50 == 0:
            print(f"Processed {idx + 1}/{len(samples)}...")

    summary = metrics.compute_summary()
    print("Evaluation Summary:")
    print(json.dumps(summary, indent=2))

    if output_report_path:
        with open(output_report_path, "w", encoding="utf-8") as f:
            json.dump(summary, f, indent=2)

    return summary


if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    parser.add_argument("--dataset", type=str, default="dataset/test.jsonl")
    parser.add_argument("--output", type=str, default="reports/base_model_evaluation.json")
    parser.add_argument("--max_samples", type=int, default=None)
    args = parser.parse_args()

    run_evaluation(args.dataset, args.output, args.max_samples)
