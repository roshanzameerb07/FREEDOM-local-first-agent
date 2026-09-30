# FREEDOM — Local-First AI Agent Framework for Resource-Constrained Android

> **"Local by default. Cloud by exception. User decides."**

[![License](https://img.shields.io/badge/License-Apache_2.0-blue.svg)](LICENSE)
[![Platform](https://img.shields.io/badge/Platform-Android_8.0%2B_(API_26%2B)-green.svg)](https://developer.android.com)
[![Architecture](https://img.shields.io/badge/Architecture-Local--First_Deterministic_DSL-orange.svg)](#architecture)
[![Model](https://img.shields.io/badge/Model-Qwen3--1.7B_INT4-purple.svg)](https://huggingface.co/Qwen)
[![Track](https://img.shields.io/badge/Hackathon-ASYNC'26_Technical_Track-red.svg)](#)

---

## 1. Elevator Pitch

**FREEDOM** is an open, local-first on-device AI agent framework that enables rural and offline field organizations to run domain-specific semantic queries and workflow automation directly on resource-constrained Android smartphones ($< \$150$ retail hardware), with zero cloud inference, zero network dependencies, and guaranteed deterministic data consistency.

---

## 2. Problem & Target Users

### The Problem
- **Connectivity Deserts:** Millions of rural field workers (dairy collection officers, agricultural extension workers, community health representatives) operate in areas with intermittent or zero 4G/5G connectivity.
- **Hallucination Risk:** Standard generative LLMs hallucinate numbers, invent records, and cannot be trusted for financial disbursals or audit-critical transactions.
- **Vendor Lock-in & Data Sovereignty:** Cloud SaaS solutions require sending sensitive operational and farmer data to remote servers, violating organizational data sovereignty.

### Target Users
- **Dairy & Agricultural Cooperatives:** E.g., Mandya District Cooperative Milk Producers Union (KMF / Nandini) with 1,000+ village milk collection centers.
- **Rural Field Workers:** Non-technical operators who interact via conversational speech or text while weighing produce, testing fat/SNF quality, and disbursing payments.
- **Enterprise Administrators:** Managers who configure organization-specific policies, user permissions, and custom local models without re-architecting the core app.

---

## 3. Core Features

### CURRENTLY IMPLEMENTED
- **Local-First Deterministic AI:** On-device semantic parsing into a frozen typed domain-specific language (`FreedomQuery` DSL). Zero raw SQL generation; zero hallucinated facts.
- **Deterministic Execution Engine (`FreedomQueryExecutor`):** 100% Kotlin-evaluated aggregations, multi-condition filtering, temporal parsing, and cooperative payment settlement logic against local Room SQLite.
- **Pluggable Model Provider Layer (`ModelProvider`):** Abstracted model boundary supporting FREEDOM-provided models (`Qwen3-1.7B INT4` via LiteRT-LM) and organization-provided models, with seamless runtime registration and switching.
- **Organization & Role-Based Security:** Bounded `OrganizationProfile` abstraction with centralized RBAC (`FIELD_WORKER`, `MANAGER`, `ORG_ADMIN`) and framework-level `AuthorizationPolicy` evaluated at the query dispatch boundary.
- **Local Session & Data Isolation (`SessionManager`):** Untrusted model outputs are stripped of identity; all database queries and writes are strictly scoped to authenticated local session context (`orgId`, `workerId`).
- **Entity & Temporal Resolvers:** Canonical name normalization and ambiguity detection (`RESOLVED`, `AMBIGUOUS`, `NOT_FOUND`), preventing silent data collisions.
- **Grounded On-Device RAG:** BM25-style lexical search across cooperative operational handbooks, quality guidelines, and payment rules with verifiable citations.
- **Milk Collection Reference Profile (`MilkCollectionProfile`):** Complete field reference implementation with quality-adjusted milk pricing ($\text{Base} + \Delta\text{Fat} + \Delta\text{SNF}$) and payment status management.

### PLANNED / FUTURE EXTENSION
- **Cryptographic Offline QR Activation:** Digital signature verification for air-gapped organization license enrollment.
- **Peer-to-Peer Wi-Fi Direct Depot Sync:** Encrypted device-to-depot batch sync without intermediate internet relays.
- **Additional Domain Reference Profiles:** Warehouse inventory tracking and primary health clinic surveys.
- **NPU / DSP Hardware Acceleration:** LiteRT delegates for Qualcomm Hexagon and MediaTek APUs.

---

## 4. Architecture Diagram

```
User Voice / Text Question
           ↓
+-----------------------------------------------------------+
| FREEDOM MODEL LAYER (ModelProvider)                       |
|   - Active: Qwen3-1.7B INT4 (LiteRT-LM Engine)           |
|   - Fallback: Pattern-based Deterministic Parser          |
+-----------------------------------------------------------+
           ↓ (JSON Output)
+-----------------------------------------------------------+
| CANONICAL FREEDOM QUERY DSL (FreedomQuery)                |
|   - RequestType: QUERY | WRITE | CLARIFY | UNSUPPORTED    |
|   - Target: MILK_RECORDS | WORKER_PROFILE | KNOWLEDGE     |
|   - Scope: SPECIFIC (Entity) | ALL                        |
|   - Aggregations, Filters, GroupBy, Having, OrderBy       |
+-----------------------------------------------------------+
           ↓
+-----------------------------------------------------------+
| QUERY VALIDATOR (QueryValidator)                          |
|   - Type compatibility, numeric constraints, date ranges  |
+-----------------------------------------------------------+
           ↓
+-----------------------------------------------------------+
| AUTHORIZATION POLICY (AuthorizationPolicy)                |
|   - Evaluates SessionContext against UserRole permissions |
|   - Reject unauthorized access before hitting SQLite      |
+-----------------------------------------------------------+
           ↓
+-----------------------------------------------------------+
| DETERMINISTIC EXECUTOR (FreedomQueryExecutor)             |
|   - EntityResolver (exact + token matching, no guessing)  |
|   - TemporalResolver (relative & explicit date math)      |
|   - Scoped Record Fetch (enforced session orgId/workerId) |
|   - Kotlin Aggregations (SUM, AVG, MIN, MAX, COUNT)       |
+-----------------------------------------------------------+
           ↓
+-----------------------------------------------------------+
| LOCAL ROOM SQLITE DATABASE (FreedomDatabase)              |
|   - farmers table (stable UUIDs, normalizedName index)    |
|   - milk_records table (ACID transactions, offline logs)  |
+-----------------------------------------------------------+
           ↓
Verified Result to UI / TTS Output
```

---

## 5. End-to-End Execution Flow

1. **User asks:** *"How much milk did Suresh supply this week?"*
2. **Semantic Parsing:** Active `ModelProvider` (Qwen3-1.7B) extracts structured intent into `FreedomQuery`:
   ```json
   {
     "type": "QUERY",
     "target": "MILK_RECORDS",
     "entity": "Suresh",
     "entityScope": "SPECIFIC",
     "time": { "type": "RELATIVE", "period": "THIS_WEEK" },
     "aggregations": [{ "type": "SUM", "field": "QUANTITY" }]
   }
   ```
3. **Validation:** `QueryValidator` verifies `QUANTITY` is numeric and `THIS_WEEK` is a recognized relative period token.
4. **Authorization:** `AuthorizationPolicy` validates that the active `SessionContext` holds `Permission.READ_RECORDS`.
5. **Entity Resolution:** `EntityResolver` maps `"Suresh"` to registered `FarmerEntity(farmerId="...", farmerName="Suresh")`.
6. **Temporal Resolution:** `TemporalResolver` resolves `THIS_WEEK` into epoch millisecond boundaries $[t_{\text{start}}, t_{\text{end}}]$ relative to device clock.
7. **Database Scoping:** Records are fetched from Room SQLite strictly matching:
   `record.orgId == session.orgId && record.createdAt in startMs..endMs && record.farmerId == suresh.farmerId`.
8. **Deterministic Synthesis:** Kotlin computes exact sum: `12.0 L` across matching records.
9. **UI Display:** Returns verified response: *"Suresh: Total quantity: 12.0 L (across 1 record(s))."*

---

## 6. Tech Stack

- **Platform:** Android Native (Kotlin 2.1.0, Android Gradle Plugin 8.9.0)
- **Minimum SDK:** API 26 (Android 8.0 Oreo) | **Target SDK:** API 36
- **UI Framework:** Jetpack Compose + Material 3
- **Local Persistence:** AndroidX Room 2.7.0-alpha13 + SQLite (with KSP annotation processing)
- **On-Device LLM Runtime:** Google LiteRT-LM (`com.google.ai.edge.litert:litert-lm:1.0.1`)
- **Target Model:** Qwen/Qwen3-1.7B quantized to INT4 (`.litertlm`)
- **Fine-Tuning Toolchain:** PyTorch 2.6.0+cu124, Hugging Face Transformers, PEFT (QLoRA), bitsandbytes

---

## 7. Runtime & Hardware Requirements

### Minimum Device Specs (Field Operation)
- **SoC:** Octa-core ARM64 (MediaTek Helio G85, Qualcomm Snapdragon 680, or equivalent)
- **RAM:** Minimum 4.0 GB physical system RAM
- **Storage:** 2.0 GB free internal storage (model binary + SQLite)
- **OS:** Android 8.0+ (Tested on Android 14 / One UI 6)

### Training Machine Specs (Model Fine-Tuning)
- **Hardware Audited (Local):** NVIDIA GeForce RTX 3050 A Laptop GPU (4,094 MiB VRAM)
  - *Benchmark Finding:* r=16, batch=1 achievable under tight VRAM constraints; sustained training moved to RTX 4050 (6 GB VRAM) for safety margin.
- **Python Environment:** Python 3.12 isolated virtual environment (`training/.venv`)

---

## 8. Installation & Setup

### Prerequisites
- Android Studio Ladybug (2024.2.1+) or command-line Android SDK
- JDK 17 (set via `JAVA_HOME`)

### Clone & Build
```bash
# Clone the repository
git clone https://github.com/roshanzameerb07/FREEDOM-local-first-agent.git
cd FREEDOM-local-first-agent

# Switch to the framework feature branch
git checkout framework/organization-model-layer

# Run all unit tests (Framework + DSL + Domain suites)
./gradlew testDebugUnitTest

# Build the Debug APK
./gradlew assembleDebug
```

Output APK will be generated at:
`app/build/outputs/apk/debug/app-debug.apk`

---

## 9. Demo Credentials

The pre-seeded local SQLite database includes demo credentials:
- **Organization ID:** `ORG001`
- **Worker ID:** `WORKER001`
- **PIN:** `1234`
- **Pre-seeded Farmers:** Ramesh, Suresh, Mahesh
- **Offline Mode:** Turn on Airplane Mode; the entire agent executes with zero network connection.

---

## 10. Testing & Verification

The project includes an extensive test suite across 11 test classes covering 89+ test cases:

```bash
# Run complete unit test suite
./gradlew testDebugUnitTest --info
```

### Key Test Suites
- `FreedomFrameworkTest`: Organization identity, profile registration, activation validity, user roles, permission inheritance, CASE A (authorized query permitted), CASE B (unauthorized query rejected), model provider registration/switching, session isolation, and framework bootstrap.
- `DeterministicQueryLayerTest`: DSL execution invariants, existence checks, aggregations, grouped sorting, payment status logic, and unsupported reason handling.
- `SemanticEvaluationSuiteTest`: End-to-end question parsing and pipeline verification.
- `EntityResolverTest`: Exact canonical match, substring match, multi-token collision detection, and ambiguous name handling.
- `LocalRagRetrieverTest`: Lexical BM25 document scoring, keyword boosts, and citation verification.
- `NumberFidelityReconcilerTest`: Decimal extraction and parameter reconciliation.

---

## 11. Known Limitations & Trade-Offs

- **Model Download Requirement:** On-device SLM binary (~1.2 GB for Qwen3-1.7B INT4) must be placed in application assets or downloaded upon initial setup; it is not bundled in Git.
- **Single Active Organization Profile per Device:** The current skeleton supports multiple registered organizations but switches profiles on session login rather than running concurrent multi-tenant workers simultaneously.
- **Fixed Room Schema:** Schema extension points exist for bounded fields, but fully dynamic arbitrary runtime SQL table creation is explicitly prohibited for stability and security.

---

## 12. Security & Local-First Principles

1. **No Outbound Telemetry:** The app makes zero tracking, telemetry, or analytics requests.
2. **Local Session Authority:** Model outputs cannot manipulate or impersonate user credentials or organization identities.
3. **Data Sovereignty:** Operational data stays on the device until the organization explicitly triggers batch sync.

---

## 13. License & Contribution

- **License:** Apache License 2.0.
- **Contributions:** Pull requests are welcomed on feature branches following the deterministic DSL and local-first architecture guidelines.
