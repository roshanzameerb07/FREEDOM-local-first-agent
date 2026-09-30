"""
FREEDOM AI — QLoRA Memory Smoke Test for Qwen/Qwen3-1.7B on RTX 3050A
Verifies 4-bit quantization, LoRA attachment, forward/backward pass,
optimizer step, and checkpoint save under strict VRAM monitoring.
"""

import os
import sys
import time
import json
import shutil
from pathlib import Path

import torch
import bitsandbytes as bnb
from transformers import (
    AutoTokenizer,
    AutoModelForCausalLM,
    BitsAndBytesConfig,
)
from peft import (
    LoraConfig,
    get_peft_model,
    prepare_model_for_kbit_training,
)

OUTPUT_DIR = Path(__file__).resolve().parent.parent / "reports" / "smoke_test"
OUTPUT_DIR.mkdir(parents=True, exist_ok=True)
CHECKPOINT_DIR = OUTPUT_DIR / "temp_adapter"


def run_smoke_test():
    report = {
        "timestamp": time.strftime("%Y-%m-%d %H:%M:%S"),
        "model_id": "Qwen/Qwen3-1.7B",
        "device": torch.cuda.get_device_name(0),
        "total_vram_gb": round(torch.cuda.get_device_properties(0).total_memory / (1024**3), 3),
        "steps": {},
        "vram_metrics": {},
        "verdict": "FAILED"
    }

    try:
        # Step 1: Initialize VRAM stats
        torch.cuda.empty_cache()
        torch.cuda.reset_peak_memory_stats()
        initial_alloc = torch.cuda.memory_allocated() / (1024**3)
        initial_res = torch.cuda.memory_reserved() / (1024**3)
        report["vram_metrics"]["initial_allocated_gb"] = round(initial_alloc, 3)
        report["vram_metrics"]["initial_reserved_gb"] = round(initial_res, 3)

        # Step 2: Download / Load Tokenizer
        print("Step 1/8: Loading Tokenizer...")
        t0 = time.time()
        tokenizer = AutoTokenizer.from_pretrained(
            "Qwen/Qwen3-1.7B",
            trust_remote_code=True,
            padding_side="right"
        )
        if tokenizer.pad_token is None:
            tokenizer.pad_token = tokenizer.eos_token
        report["steps"]["1_tokenizer_loaded"] = {
            "status": "PASS",
            "vocab_size": len(tokenizer),
            "time_s": round(time.time() - t0, 2)
        }
        print(f"  Tokenizer loaded ({len(tokenizer)} tokens) in {time.time() - t0:.1f}s")

        # Step 3: Configure 4-bit Quantization
        bnb_config = BitsAndBytesConfig(
            load_in_4bit=True,
            bnb_4bit_quant_type="nf4",
            bnb_4bit_use_double_quant=True,
            bnb_4bit_compute_dtype=torch.bfloat16
        )

        # Step 4: Download / Load 4-bit Model
        print("Step 2/8: Loading Qwen/Qwen3-1.7B in 4-bit NF4...")
        t0 = time.time()
        model = AutoModelForCausalLM.from_pretrained(
            "Qwen/Qwen3-1.7B",
            quantization_config=bnb_config,
            device_map="auto",
            trust_remote_code=True,
            torch_dtype=torch.bfloat16
        )
        model_load_time = time.time() - t0
        post_model_alloc = torch.cuda.memory_allocated() / (1024**3)
        post_model_res = torch.cuda.memory_reserved() / (1024**3)
        report["steps"]["2_model_loaded_4bit"] = {
            "status": "PASS",
            "time_s": round(model_load_time, 2),
            "allocated_gb": round(post_model_alloc, 3),
            "reserved_gb": round(post_model_res, 3)
        }
        print(f"  Model loaded in {model_load_time:.1f}s. VRAM allocated: {post_model_alloc:.2f} GB, reserved: {post_model_res:.2f} GB")

        # Step 5: Prepare model for kbit training with gradient checkpointing
        print("Step 3/8: Preparing model for kbit training...")
        model = prepare_model_for_kbit_training(
            model,
            use_gradient_checkpointing=True
        )
        report["steps"]["3_kbit_prep"] = {"status": "PASS"}

        # Step 6: Attach LoRA Adapter
        print("Step 4/8: Attaching LoRA adapter...")
        lora_config = LoraConfig(
            r=8,
            lora_alpha=16,
            lora_dropout=0.05,
            bias="none",
            task_type="CAUSAL_LM",
            target_modules=["q_proj", "k_proj", "v_proj", "o_proj", "gate_proj", "up_proj", "down_proj"]
        )
        model = get_peft_model(model, lora_config)
        trainable_params, all_param = model.get_nb_trainable_parameters()
        report["steps"]["4_lora_attached"] = {
            "status": "PASS",
            "trainable_params": trainable_params,
            "all_params": all_param,
            "trainable_pct": round(100 * trainable_params / all_param, 3)
        }
        print(f"  LoRA attached: {trainable_params:,} trainable / {all_param:,} total ({100 * trainable_params / all_param:.2f}%)")

        # Step 7: Prepare Forward Pass with max_seq_length=512
        print("Step 5/8: Running forward pass with max sequence length 512...")
        # Create dummy input of batch_size=1, seq_len=512
        seq_len = 512
        dummy_input = torch.randint(100, 1000, (1, seq_len), device="cuda", dtype=torch.long)
        dummy_labels = dummy_input.clone()

        t0 = time.time()
        outputs = model(input_ids=dummy_input, labels=dummy_labels)
        loss = outputs.loss
        forward_time = time.time() - t0
        post_fwd_alloc = torch.cuda.memory_allocated() / (1024**3)
        report["steps"]["5_forward_pass"] = {
            "status": "PASS",
            "loss": round(float(loss.item()), 4),
            "time_s": round(forward_time, 3),
            "allocated_gb": round(post_fwd_alloc, 3)
        }
        print(f"  Forward pass: Loss = {loss.item():.4f} in {forward_time:.3f}s. Allocated: {post_fwd_alloc:.2f} GB")

        # Step 8: Backward Pass
        print("Step 6/8: Running backward pass...")
        t0 = time.time()
        loss.backward()
        backward_time = time.time() - t0
        post_bwd_alloc = torch.cuda.memory_allocated() / (1024**3)
        report["steps"]["6_backward_pass"] = {
            "status": "PASS",
            "time_s": round(backward_time, 3),
            "allocated_gb": round(post_bwd_alloc, 3)
        }
        print(f"  Backward pass completed in {backward_time:.3f}s. Allocated: {post_bwd_alloc:.2f} GB")

        # Step 9: 8-bit AdamW Optimizer Update
        print("Step 7/8: Updating weights with 8-bit AdamW...")
        t0 = time.time()
        optimizer = bnb.optim.AdamW8bit(model.parameters(), lr=2e-4)
        optimizer.step()
        optimizer.zero_grad()
        opt_time = time.time() - t0
        post_opt_alloc = torch.cuda.memory_allocated() / (1024**3)
        report["steps"]["7_optimizer_step"] = {
            "status": "PASS",
            "time_s": round(opt_time, 3),
            "allocated_gb": round(post_opt_alloc, 3)
        }
        print(f"  Optimizer update completed in {opt_time:.3f}s. Allocated: {post_opt_alloc:.2f} GB")

        # Step 10: Save Checkpoint
        print("Step 8/8: Saving adapter checkpoint...")
        if CHECKPOINT_DIR.exists():
            shutil.rmtree(CHECKPOINT_DIR)
        t0 = time.time()
        model.save_pretrained(str(CHECKPOINT_DIR))
        save_time = time.time() - t0
        saved_files = [f.name for f in CHECKPOINT_DIR.iterdir()]
        report["steps"]["8_checkpoint_saved"] = {
            "status": "PASS",
            "time_s": round(save_time, 2),
            "saved_files": saved_files
        }
        print(f"  Adapter checkpoint saved in {save_time:.2f}s: {saved_files}")

        # Peak VRAM measurements
        peak_allocated_gb = torch.cuda.max_memory_allocated() / (1024**3)
        peak_reserved_gb = torch.cuda.max_memory_reserved() / (1024**3)
        total_vram_gb = torch.cuda.get_device_properties(0).total_memory / (1024**3)
        headroom_gb = total_vram_gb - peak_reserved_gb

        report["vram_metrics"]["peak_allocated_gb"] = round(peak_allocated_gb, 3)
        report["vram_metrics"]["peak_reserved_gb"] = round(peak_reserved_gb, 3)
        report["vram_metrics"]["total_vram_gb"] = round(total_vram_gb, 3)
        report["vram_metrics"]["headroom_gb"] = round(headroom_gb, 3)
        report["vram_metrics"]["utilization_pct"] = round(100 * peak_reserved_gb / total_vram_gb, 2)

        print("\n=== VRAM PEAK SUMMARY ===")
        print(f"Peak Allocated: {peak_allocated_gb:.2f} GB")
        print(f"Peak Reserved:  {peak_reserved_gb:.2f} GB / {total_vram_gb:.2f} GB ({100 * peak_reserved_gb / total_vram_gb:.1f}%)")
        print(f"VRAM Headroom:  {headroom_gb:.2f} GB")

        report["verdict"] = "PASS"

    except Exception as e:
        import traceback
        report["verdict"] = "FAILED"
        report["error"] = str(e)
        report["traceback"] = traceback.format_exc()
        print(f"\nERROR: {e}")
        traceback.print_exc()

    # Save JSON report
    report_path = OUTPUT_DIR / "qlora_smoke_test_report.json"
    with open(report_path, "w", encoding="utf-8") as f:
        json.dump(report, f, indent=2)
    print(f"\nReport written to: {report_path}")

    return report


if __name__ == "__main__":
    run_smoke_test()
