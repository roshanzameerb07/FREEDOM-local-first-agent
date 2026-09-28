# FREEDOM — Local-First AI Agent for Resource-Constrained Android

## Overview
FREEDOM is an offline, privacy-first Android application designed for rural dairy cooperatives and smallholder field agents operating in connectivity-challenged environments. It bridges on-device Small Language Models (SLMs) with deterministic local SQLite database operations and local document RAG retrieval to deliver reliable, explainable business actions without cloud dependencies.

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
|                       LocalContextBuilder                         |
|   - Constructs minimal, tailored prompts for Gemma 3 1B IT        |
|   - Defines strict JSON structure & numerical accuracy rules      |
+-------------------------------------------------------------------+
                                  |
                                  v
+-------------------------------------------------------------------+
|            ON-DEVICE GEMMA 3 1B IT (LiteRT-LM Engine)             |
|   - Responsible for intent classification & argument extraction   |
|   - Never computes totals, counts, or modifies SQLite directly    |
+-------------------------------------------------------------------+
                                  |
                                  v
+-------------------------------------------------------------------+
|                    NumberFidelityReconciler                       |
|   - Cross-references extracted args against raw prompt            |
|   - Restores exact decimals (e.g. 18.5, 4.2, 8.6) without loss    |
|   - Distinguishes missing values from 0.0 (prevents defaults)     |
+-------------------------------------------------------------------+
                                  |
                                  v
+-------------------------------------------------------------------+
|              DETERMINISTIC VALIDATION & EXECUTION                 |
|   - MilkRecordValidator verifies bounds (fat/snf: 0-15%, qty > 0) |
|   - Blocks writes on incomplete data (e.g. "Ramesh gave 80 L")    |
|   - Executes Room SQLite queries, Profile queries, or Local RAG   |
+-------------------------------------------------------------------+
                                  |
       +--------------------------+--------------------------+
       |                          |                          |
       v                          v                          v
+--------------+           +--------------+           +--------------+
|  Room SQLite |           | Local Profile|           |  Local RAG   |
| (DB Records, |           | (Officer ID, |           | (Cooperative |
| Totals,      |           |  Area, Union |           |  SOPs, Rules,|
|  Counts)     |           |  Metadata)   |           |  Citations)  |
+--------------+           +--------------+           +--------------+
       |                          |                          |
       +--------------------------+--------------------------+
                                  |
                                  v
+-------------------------------------------------------------------+
|                     UI RESPONSE / RESULT CARD                     |
|   - Capability Badge + Source of Truth Badge                      |
|   - Extracted Parameters breakdown (exact precision)              |
|   - Calculation summary / Verified Answer                         |
|   - Grounded RAG Evidence Box (Document Title, Section, Quote)    |
|   - 100% Offline verification status                              |
+-------------------------------------------------------------------+
```

---

## Tool Registry (Capabilities)

1. **`CREATE_MILK_RECORD`**
   - *Arguments:* `farmerName` (String), `quantity` (Double), `fat` (Double), `snf` (Double), `paymentStatus` (String: PENDING/PAID).
   - *Behavior:* Strict deterministic validation. Decimal fidelity guaranteed via `NumberFidelityReconciler`. Mandatory user confirmation before Room insertion. Missing fields abort database write.

2. **`GET_FARMER_HISTORY`**
   - *Arguments:* `farmerName` (String).
   - *Behavior:* Retrieves historical milk deliveries for the requested farmer directly from SQLite and calculates total volume.

3. **`GET_PENDING_PAYMENTS`**
   - *Arguments:* None.
   - *Behavior:* Filters local records where `paymentStatus == 'PENDING'`, showing count and pending litres.

4. **`GET_TODAY_SUMMARY`**
   - *Arguments:* None.
   - *Behavior:* Computes total litres and records collected on device today.

5. **`GET_WORKER_PROFILE`**
   - *Arguments:* None.
   - *Behavior:* Deterministically returns the active field officer ID (`WORKER001`), name (`Ramesh K.`), assigned area (`Sector 4 — North Mandya Milk Route`), and center.

6. **`GET_ORGANIZATION_INFO`**
   - *Arguments:* None.
   - *Behavior:* Returns cooperative union metadata, registration number, and regional district.

7. **`COUNT_FARMERS_COVERED`**
   - *Arguments:* `period` ("today" | "week" | "all").
   - *Behavior:* Executes `SELECT COUNT(DISTINCT LOWER(farmerName))` from Room SQLite.

8. **`GET_WEEKLY_WORKER_SUMMARY`**
   - *Arguments:* `farmerName` (optional).
   - *Behavior:* Calculates weekly collection volume, records, and distinct farmers from local SQLite.

9. **`SEARCH_LOCAL_KNOWLEDGE` (RAG)**
   - *Arguments:* `query` (String).
   - *Behavior:* Performs BM25-style lexical search over chunked organization documents (Payment Completion Policy, Quality Standards, Spoilage/Rejection Procedures, Sync Protocols). Returns grounded citation and exact quote. Rejects unanswerable queries with zero hallucination.

10. **`GET_PENDING_UPLOADS`**
    - *Arguments:* None.
    - *Behavior:* Lists local records queued for future batch synchronization.

---

## Honesty & Simulation Disclaimers
- **Batch Synchronization:** The sync mechanism is an on-device demo simulation. Records are marked as `UPLOADED` in the local Room database to illustrate the batch lifecycle. No remote cloud servers, end-to-end encryption keys, or external network calls are performed.
- **Voice Extraction:** Voice entry provides simulated audio transcripts for hackathon demonstration. Genuine microphone ASR is not currently embedded to keep runtime footprint minimal.

---

## Current Development Status
- **Build Status:** Compiles cleanly with Android Gradle Plugin 9.0.1, Kotlin 2.3.20, Compose BOM 2026.03.01.
- **Unit Tests:** 30/30 unit tests pass green across `NumberFidelityReconcilerTest`, `LocalRagRetrieverTest`, `LocalDeterministicQueryEngineTest`, `MilkRecordValidatorTest`, `PatternBasedVoiceExtractorTest`, and `AuthRepositoryTest`.
- **Physical Device Tested:** Samsung Galaxy (`RZCY90ETLVZ`) with official Gemma 3 1B IT (`gemma3-1b-it-int4.litertlm`).
- **Airplane Mode Verified:** 100% offline execution verified with Wi-Fi and Cellular disabled.
