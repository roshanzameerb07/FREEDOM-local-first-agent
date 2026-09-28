# FREEDOM — Local-First AI Agent for Resource-Constrained Android

FREEDOM is an offline-first Android AI agent designed for field workflows on ordinary Android phones.

**Core idea:** Local by default. Cloud by exception. User decides.

## Current prototype

The proof-of-concept demonstrates a field dairy collection workflow:

- Local authentication with session worker & organization profile (`WorkerProfileRepository`)
- Local Room/SQLite database with offline ACID transactions
- Natural-language interaction and capability routing through on-device Gemma 3 1B IT
- Grounded local document RAG (Cooperative policies, quality benchmarks, payment completion rules)
- Deterministic validation and calculation (`calculatePayableAmount`)
- Structured tool execution without allowing direct model writes to SQLite
- Decimal fidelity preservation (e.g. 18.5, 4.2, 8.6) via `NumberFidelityReconciler`
- Explicit confirmation UI before writing records
- Cash, UPI, and Bank Transfer recording with reference number enforcement for electronic payments
- Local demo synchronization flow

Milk collection is the demonstration workflow; the underlying agent architecture is intended to be reusable through organization-specific skill/configuration.

## Architecture

**User input → on-device Gemma 3 1B IT → structured ToolRequest → Intent Consistency Gate → deterministic validation & execution → Room SQLite / Local RAG → response**

Critical data writes and calculations are handled strictly by deterministic Kotlin code rather than allowing the model to perform arithmetic or write directly to the database.

## Model

The Android prototype uses:

`gemma3-1b-it-int4.litertlm`

The model is intentionally **not committed to this Git repository** because it is about 584 MB.

Official model repository:

https://huggingface.co/litert-community/Gemma3-1B-IT

The model repository requires accepting the applicable Gemma license before downloading the artifact.

Place the downloaded artifact at:

`app/src/main/assets/gemma3-1b-it-int4.litertlm`

Expected SHA-256:

`1325ae366d31950f137c9c357b9fa89448b176d76998180c08ceaca78bba98be`

## Build & Test

Open the project in Android Studio or use the included Gradle wrapper:

```bash
# Run unit test suite (40 passing tests)
./gradlew testDebugUnitTest

# Assemble debug APK
./gradlew assembleDebug
```

Do not commit `local.properties`, build outputs, Gradle caches, or model artifacts.

## Demo credentials

- Organization: `ORG001`
- Worker: `WORKER001`
- PIN: `1234`

## Offline principle

The entire application runs 100% offline with zero cloud API dependencies. Sync is an explicit, on-demand local simulation showing records queued and marked as ready to send.

## Project status & Verification

- **40/40 unit tests passing** covering deterministic query engine, intent safety gates, decimal fidelity, RAG retrieval, payment validation, and authentication.
- **Physical device tested** on Samsung Galaxy (`RZCY90ETLVZ`) with LiteRT-LM CPU inference in airplane mode.
- Official FREEDOM branding and launcher icons integrated across all density buckets.

