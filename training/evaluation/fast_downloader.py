"""
Fast parallel multi-threaded chunk downloader for model-00001-of-00002.safetensors
Uses HTTP Range requests with 16 parallel worker threads.
"""

import os
import sys
import time
import urllib.request
from concurrent.futures import ThreadPoolExecutor, as_completed
from pathlib import Path

URL = "https://huggingface.co/Qwen/Qwen3-1.7B/resolve/main/model-00001-of-00002.safetensors"
SNAPSHOT_DIR = Path(os.environ["USERPROFILE"]) / ".cache" / "huggingface" / "hub" / "models--Qwen--Qwen3-1.7B" / "snapshots" / "70d244cc86ccca08cf5af4e1e306ecf908b1ad5e"
TARGET_FILE = SNAPSHOT_DIR / "model-00001-of-00002.safetensors"
PART_FILE = SNAPSHOT_DIR / "model-00001-of-00002.safetensors.part"

CHUNK_SIZE = 16 * 1024 * 1024  # 16 MB chunks
MAX_WORKERS = 16


def download_chunk(start, end, chunk_idx):
    req = urllib.request.Request(
        URL,
        headers={
            "Range": f"bytes={start}-{end}",
            "User-Agent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64)"
        }
    )
    for attempt in range(5):
        try:
            with urllib.request.urlopen(req, timeout=30) as resp:
                data = resp.read()
                expected = end - start + 1
                if len(data) != expected:
                    raise IOError(f"Chunk {chunk_idx}: got {len(data)} bytes, expected {expected}")
                return chunk_idx, start, data
        except Exception as e:
            if attempt == 4:
                raise
            time.sleep(1 + attempt)


def main():
    SNAPSHOT_DIR.mkdir(parents=True, exist_ok=True)
    if TARGET_FILE.exists() and TARGET_FILE.stat().st_size == 3441185608:
        print(f"File already complete: {TARGET_FILE} ({TARGET_FILE.stat().st_size} bytes)")
        return

    print(f"Querying file size from {URL}...")
    req = urllib.request.Request(URL, method="HEAD", headers={"User-Agent": "Mozilla/5.0"})
    with urllib.request.urlopen(req) as resp:
        total_size = int(resp.headers.get("Content-Length", 3441185608))
    print(f"Total size: {total_size / (1024**3):.2f} GB ({total_size} bytes)")

    # Pre-allocate part file
    if not PART_FILE.exists() or PART_FILE.stat().st_size != total_size:
        print(f"Pre-allocating part file: {PART_FILE}...")
        with open(PART_FILE, "wb") as f:
            f.seek(total_size - 1)
            f.write(b"\0")

    # Generate chunks
    chunks = []
    chunk_idx = 0
    for start in range(0, total_size, CHUNK_SIZE):
        end = min(start + CHUNK_SIZE - 1, total_size - 1)
        chunks.append((start, end, chunk_idx))
        chunk_idx += 1

    print(f"Downloading {len(chunks)} chunks using {MAX_WORKERS} parallel threads...")
    t0 = time.time()
    downloaded_bytes = 0

    with open(PART_FILE, "r+b") as out_f:
        with ThreadPoolExecutor(max_workers=MAX_WORKERS) as executor:
            futures = {
                executor.submit(download_chunk, start, end, idx): (start, end, idx)
                for start, end, idx in chunks
            }

            for future in as_completed(futures):
                idx, start, data = future.result()
                out_f.seek(start)
                out_f.write(data)
                downloaded_bytes += len(data)
                elapsed = time.time() - t0
                speed = (downloaded_bytes / (1024**2)) / max(0.1, elapsed)
                pct = 100 * downloaded_bytes / total_size
                mb_done = downloaded_bytes / (1024**2)
                mb_total = total_size / (1024**2)
                print(f"  [{pct:5.1f}%] {mb_done:6.1f} / {mb_total:.1f} MB @ {speed:5.1f} MB/s | chunk {idx+1}/{len(chunks)}", end="\r", flush=True)

    print(f"\nDownload completed in {time.time() - t0:.1f}s!")
    PART_FILE.rename(TARGET_FILE)
    print(f"Saved to: {TARGET_FILE}")


if __name__ == "__main__":
    main()
