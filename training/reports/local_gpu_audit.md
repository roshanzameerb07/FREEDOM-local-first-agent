# FREEDOM — Local Hardware & GPU Audit

**Date**: 2026-09-30  
**Host Machine**: Windows 11 Laptop  

---

## 1. GPU Specifications (Measured via `nvidia-smi`)

- **Exact GPU Model**: `NVIDIA GeForce RTX 3050 A Laptop GPU`
- **Architecture**: Ada Lovelace (Compute Capability 8.9)
- **Dedicated VRAM**: `4094 MiB` (~4.00 GB)
- **Idle Free VRAM**: `3892 MiB` (~3.80 GB)
- **Driver Version**: `592.82`
- **Supported CUDA Driver Version**: `13.1`
- **GPU Compute Capability**: `8.9`
- **Power Cap**: 35W (Laptop dynamic boost profile)

---

## 2. Host System Specifications

- **Operating System**: Windows 11 (`10.0.26200`, Windows-11-10.0.26200-SP0)
- **Architecture**: 64-bit (`x86_64` / `AMD64`)
- **Total System RAM**: `15.64 GB` (16,015 MiB)
- **Free / Available RAM**: `1.91 GB` (system under active workload)
- **Windows Subsystem for Linux (WSL)**: **Not Installed** (`wsl.exe --status` returned: *"The Windows Subsystem for Linux is not installed"*)
- **CUDA Toolkit / `nvcc`**: **Not Installed / Not on PATH** (`nvcc` binary absent)

---

## 3. Installed Python Environments

- **System Python**: Python 3.14.5 (`C:\Python314\python.exe`)
- **Secondary Python**: Python 3.13.14 (`C:\Program Files\WindowsApps\PythonSoftwareFoundation.Python.3.13_3.13.3824.0_x64__qbz5n2kfra8p0\python3.13.exe`)
- **Preferred Python 3.12**: Not natively pre-installed (available via winget `Python.Python.3.12`)
- **Package Managers**: `pip` (26.1.2), `winget` (v1.29.380)

---

## 4. Key Architectural Implications for QLoRA Fine-Tuning

1. **Dedicated VRAM Ceiling (4.0 GB)**:
   - RTX 3050 A Laptop has exactly **4,094 MiB** VRAM.
   - At 4-bit NormalFloat (NF4), Qwen3-1.7B base model weights occupy:
     $$1.7 \times 10^9 \text{ params} \times 0.5 \text{ bytes/param} \approx 850\text{ MB}$$
   - KV Cache for 512 tokens + LoRA adapter weights (rank 8) + gradients + optimizer states (8-bit paged AdamW) requires approximately 1.5 GB – 2.2 GB.
   - Total theoretical training footprint: ~2.5 GB to 3.2 GB, which *theoretically* fits inside 4.0 GB VRAM if overhead is strictly controlled.
2. **CUDA on Windows**:
   - `bitsandbytes` historically required compiled Windows wheels or `bitsandbytes-windows`.
   - `unsloth` officially targets Linux (Triton compiler requirement); Windows native support for Unsloth is historically experimental or unsupported.
   - An isolated environment with official CUDA-enabled PyTorch build is required.
