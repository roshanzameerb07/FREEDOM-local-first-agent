# FREEDOM — Local-First AI Agent for Resource-Constrained Android

## Overview
FREEDOM is an offline, privacy-first Android application designed for rural dairy cooperatives and smallholder field agents operating in connectivity-challenged environments. It bridges on-device Small Language Models (SLMs) with deterministic local SQLite database operations to deliver reliable, explainable business actions without cloud dependencies.

---

## Target Hardware & Runtime Constraints
- **Target OS:** Android 8.0 (API 26) through Android 16 (API 36).
- **Physical Memory Requirement:** Minimum 6 GB physical RAM (~5,000–5,500 MiB visible to Android user-space after OS/kernel reservation).
- **Connectivity:** 100% Offline-capable (validated in Airplane Mode). Zero cloud API dependencies.
- **Model Artifact:** Official Google `gemma3-1b-it-int4.litertlm` (approx. 584 MB, 4-bit quantized Gemma 3 1B Instruction-Tuned).
- **Inference Runtime:** Google LiteRT-LM (`com.google.ai.edge.litertlm.*`, v0.15.0) running on CPU backend with optimized memory footprint.

---

## Architecture & Data Flow

```
+-------------------------------------------------------------------+
|                        USER INPUT (Text)                          |
+-------------------------------------------------------------------+
                                  |
                                  v
+-------------------------------------------------------------------+
|            ON-DEVICE GEMMA 3 1B IT (LiteRT-LM Engine)             |
|   - System Instruction: Strictly map request to ToolRequest JSON  |
|   - Output: JSON containing intent, args, needsConfirmation       |
+-------------------------------------------------------------------+
                                  |
                                  v
+-------------------------------------------------------------------+
|                 STRUCTURED ToolRequest EXTRACTION                 |
|   - Robust JSON extraction (handles markdown fences, trims)       |
|   - Fallback: Deterministic Regex/Pattern matching if SLM offline |
+-------------------------------------------------------------------+
                                  |
                                  v
+-------------------------------------------------------------------+
|                     DETERMINISTIC VALIDATION                      |
|   - MilkRecordValidator verifies farmer name, quantity, fat, snf  |
|   - Rejects missing/negative/out-of-bound values without writing   |
+-------------------------------------------------------------------+
                                  |
                                  v
+-------------------------------------------------------------------+
|                    CONFIRMATION WHEN REQUIRED                     |
|   - Record creation always requires explicit user review & confirm|
|   - Incomplete or ambiguous data displays warnings                |
+-------------------------------------------------------------------+
                                  | (Upon User Confirmation)
                                  v
+-------------------------------------------------------------------+
|                     ToolExecutor (Kotlin Layer)                   |
|   - Validates args once more; never defaults to 0.0               |
|   - Executes Room DAO operations                                  |
+-------------------------------------------------------------------+
                                  |
                                  v
+-------------------------------------------------------------------+
|                       ROOM / LOCAL SQLITE DB                      |
|   - Entity: MilkRecordEntity                                      |
|   - DAO: MilkRecordDao                                            |
|   - Repository: MilkRecordRepository                              |
+-------------------------------------------------------------------+
                                  |
                                  v
+-------------------------------------------------------------------+
|                     UI RESPONSE / RESULT CARD                     |
|   - Execution status (success or validation failure)              |
|   - Real-time matching local records display                      |
|   - 100% Offline verification indicator                           |
+-------------------------------------------------------------------+
```

---

## Tool Registry (6 Core Intents)

1. **`CREATE_MILK_RECORD`**
   - *Arguments:* `farmerName` (String), `quantity` (Double), `fat` (Double), `snf` (Double), `paymentStatus` (String: PENDING/PAID).
   - *Behavior:* Strict deterministic validation. Always prompts for confirmation dialog before Room insertion. Incomplete inputs (e.g. "Ramesh gave 80 litres") are blocked from saving until required metrics are provided.

2. **`SEARCH_FARMERS`**
   - *Arguments:* `query` (String).
   - *Behavior:* Queries local Room database for matching farmer records and returns result list.

3. **`GET_FARMER_HISTORY`**
   - *Arguments:* `farmerName` (String).
   - *Behavior:* Retrieves all historical milk deliveries for the requested farmer directly from SQLite.

4. **`GET_TODAY_SUMMARY`**
   - *Arguments:* None.
   - *Behavior:* Computes total litres collected and total record count on device for the current calendar day.

5. **`GET_PENDING_PAYMENTS`**
   - *Arguments:* None.
   - *Behavior:* Filters local records where `paymentStatus == 'PENDING'`, showing count and total litres due.

6. **`GET_PENDING_UPLOADS`**
   - *Arguments:* None.
   - *Behavior:* Lists local records queued for future batch synchronization (`uploadStatus == 'PENDING'`).

---

## Honesty & Simulation Disclaimers
- **Batch Synchronization:** The sync mechanism is an on-device demo simulation. Records are marked as `UPLOADED` in the local Room database to illustrate the batch lifecycle. No remote cloud servers, end-to-end encryption keys, or external network calls are performed.
- **Voice Extraction:** Voice entry provides simulated audio transcripts for hackathon demonstration. Genuine microphone ASR is not currently embedded to keep runtime footprint minimal.

---

## Current Development Status
- **Build Status:** Compiles with Android Gradle Plugin 9.0.1, Kotlin 2.3.20, Compose BOM 2026.03.01.
- **Unit Tests:** 100% passing across Validator, Repository, Voice Extractor, and QueryEngine tests.
- **Model Verification:** Verified real `gemma3-1b-it-int4.litertlm` artifact placed in internal storage and loaded by LiteRT-LM Engine on physical device.
