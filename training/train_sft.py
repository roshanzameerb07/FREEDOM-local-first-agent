"""
Supervised Fine-Tuning (SFT / QLoRA) Script for Qwen3 1.7B on FREEDOM Query Dataset.
Uses Hugging Face TRL and PEFT.
"""

import argparse
import json
import yaml
from pathlib import Path
from evaluation.base_model_evaluator import SYSTEM_PROMPT


def format_example(example: dict) -> str:
    """Formats prompt and target JSON into Qwen ChatML conversational structure."""
    prompt = example["prompt"]
    target_json_str = json.dumps(example["target"], indent=2)

    return (
        f"<|im_start|>system\n{SYSTEM_PROMPT}<|im_end|>\n"
        f"<|im_start|>user\n{prompt}<|im_end|>\n"
        f"<|im_start|>assistant\n{target_json_str}<|im_end|>"
    )


def main():
    parser = argparse.ArgumentParser(description="FREEDOM Qwen3 1.7B SFT Fine-Tuning")
    parser.add_argument("--config", type=str, default="configs/training_config.yaml")
    args = parser.parse_args()

    config_path = Path(args.config)
    with open(config_path, "r", encoding="utf-8") as f:
        cfg = yaml.safe_load(f)

    print("=" * 60)
    print("FREEDOM AI — Qwen3 1.7B Fine-Tuning Launcher")
    print(f"Base Model: {cfg['model']['base_model_name_or_path']}")
    print(f"Train File: {cfg['dataset']['train_file']}")
    print(f"Validation File: {cfg['dataset']['validation_file']}")
    print(f"Epochs: {cfg['training_arguments']['num_train_epochs']}")
    print(f"Effective Batch Size: {cfg['training_arguments']['per_device_train_batch_size'] * cfg['training_arguments']['gradient_accumulation_steps']}")
    print("=" * 60)

    try:
        import torch
        from datasets import load_dataset
        from transformers import (
            AutoModelForCausalLM,
            AutoTokenizer,
            BitsAndBytesConfig,
            TrainingArguments,
        )
        from peft import LoraConfig, get_peft_model, prepare_model_for_kbit_training
        from trl import SFTTrainer
    except ImportError as e:
        print(f"\n[ERROR] Missing ML packages: {e}")
        print("Please run on an environment with: pip install torch transformers peft trl bitsandbytes accelerate")
        return

    # 1. Tokenizer
    tokenizer = AutoTokenizer.from_pretrained(
        cfg["model"]["base_model_name_or_path"],
        trust_remote_code=True,
        padding_side="right"
    )
    if tokenizer.pad_token is None:
        tokenizer.pad_token = tokenizer.eos_token

    # 2. Quantization Config (QLoRA 4-bit)
    bnb_config = BitsAndBytesConfig(
        load_in_4bit=cfg["model"]["load_in_4bit"],
        bnb_4bit_quant_type=cfg["model"]["bnb_4bit_quant_type"],
        bnb_4bit_compute_dtype=torch.bfloat16 if cfg["model"]["bnb_4bit_compute_dtype"] == "bfloat16" else torch.float16,
        bnb_4bit_use_double_quant=cfg["model"]["bnb_4bit_use_double_quant"]
    )

    # 3. Model
    model = AutoModelForCausalLM.from_pretrained(
        cfg["model"]["base_model_name_or_path"],
        quantization_config=bnb_config,
        device_map="auto",
        trust_remote_code=True
    )
    model = prepare_model_for_kbit_training(model)

    # 4. LoRA Config
    peft_config = LoraConfig(
        r=cfg["lora"]["r"],
        lora_alpha=cfg["lora"]["lora_alpha"],
        target_modules=cfg["lora"]["target_modules"],
        lora_dropout=cfg["lora"]["lora_dropout"],
        bias=cfg["lora"]["bias"],
        task_type=cfg["lora"]["task_type"]
    )
    model = get_peft_model(model, peft_config)
    model.print_trainable_parameters()

    # 5. Dataset Loading & Formatting
    dataset = load_dataset(
        "json",
        data_files={
            "train": cfg["dataset"]["train_file"],
            "validation": cfg["dataset"]["validation_file"],
        }
    )
    train_dataset = dataset["train"].map(lambda x: {"formatted_text": format_example(x)})
    eval_dataset = dataset["validation"].map(lambda x: {"formatted_text": format_example(x)})

    # 6. Training Arguments
    ta = cfg["training_arguments"]
    training_args = TrainingArguments(
        output_dir=ta["output_dir"],
        num_train_epochs=ta["num_train_epochs"],
        per_device_train_batch_size=ta["per_device_train_batch_size"],
        per_device_eval_batch_size=ta["per_device_eval_batch_size"],
        gradient_accumulation_steps=ta["gradient_accumulation_steps"],
        learning_rate=float(ta["learning_rate"]),
        lr_scheduler_type=ta["lr_scheduler_type"],
        warmup_ratio=float(ta["warmup_ratio"]),
        weight_decay=float(ta["weight_decay"]),
        logging_steps=ta["logging_steps"],
        eval_strategy=ta["evaluation_strategy"],
        eval_steps=ta["eval_steps"],
        save_strategy=ta["save_strategy"],
        save_steps=ta["save_steps"],
        save_total_limit=ta["save_total_limit"],
        load_best_model_at_end=ta["load_best_model_at_end"],
        metric_for_best_model=ta["metric_for_best_model"],
        bf16=ta["bf16"],
        gradient_checkpointing=ta["gradient_checkpointing"],
        optim=ta["optim"],
        report_to="none"
    )

    # 7. Trainer
    trainer = SFTTrainer(
        model=model,
        train_dataset=train_dataset,
        eval_dataset=eval_dataset,
        peft_config=peft_config,
        dataset_text_field=cfg["dataset"]["dataset_text_field"],
        max_seq_length=cfg["dataset"]["max_seq_length"],
        tokenizer=tokenizer,
        args=training_args,
        packing=cfg["dataset"]["packing"]
    )

    print("\nStarting SFT training loop...")
    trainer.train()

    final_output = Path(ta["output_dir"]) / "final_adapter"
    trainer.model.save_pretrained(final_output)
    tokenizer.save_pretrained(final_output)
    print(f"\nTraining completed successfully! Adapter saved to: {final_output}")


if __name__ == "__main__":
    main()
