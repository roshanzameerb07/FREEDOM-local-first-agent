# FREEDOM — Local-First AI Agent for Resource-Constrained Android

FREEDOM is an offline-first Android AI agent designed for field workflows on ordinary Android phones.

**Core idea:** Local by default. Cloud by exception. User decides.

## Current prototype

The proof-of-concept demonstrates a milk-collection workflow:

- local authentication
- local Room/SQLite storage
- natural-language interaction through a local Gemma 3 1B model
- structured tool/intent selection
- deterministic validation and tool execution
- local records and queries
- confirmation before record creation when required
- demo synchronization flow

Milk collection is the demonstration workflow; the underlying agent architecture is intended to be reusable through organization-specific skill/configuration.

## Architecture

**User input → on-device Gemma → structured ToolRequest → validation → local tool execution → Room/SQLite → response**

Critical data writes and validation are handled by deterministic Kotlin code rather than allowing the model to write directly to the database.

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

## Build

Open the project in Android Studio and allow Gradle to sync.

Use the included Gradle wrapper:

`gradlew.bat assembleDebug`

Do not commit `local.properties`, build outputs, Gradle caches, or model artifacts.

## Demo credentials

- Organization: `ORG001`
- Worker: `WORKER001`
- PIN: `1234`

These are prototype/demo credentials only.

## Offline principle

The core workflow does not require continuous internet connectivity. Synchronization is a separate controlled step and is currently a demo/simulation rather than a direct integration with any real company's internal system.

## Project status

**Phase 1:** native Android foundation and offline workflow — complete.

**Phase 2:** real on-device Gemma inference and agentic tool flow — implemented in the current source tree.

Next validation target: physical end-to-end testing of record creation, pending-payment queries, farmer-history queries, ambiguity confirmation, and airplane-mode operation.

## Model handling

The model file is deliberately excluded from Git. A new developer/laptop must download the exact artifact separately and place it in the path above.

The Gemma model remains subject to its own license and is not redistributed by this repository.
