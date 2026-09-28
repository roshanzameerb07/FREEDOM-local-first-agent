# FREEDOM — Development Context

## Identity
Project: FREEDOM — Local-First AI Agent for Resource-Constrained Android

Positioning:
A reusable local-first AI agent layer for ordinary Android field devices.

Core principle:
Local by default. Cloud by exception. User decides.

## Current implementation
- Native Android
- Kotlin + Jetpack Compose
- Room/SQLite local database
- LiteRT-LM
- Gemma 3 1B INT4 local model
- Structured ToolIntent / ToolRequest flow
- Deterministic Kotlin validation and ToolExecutor
- Offline-first operation
- Demo synchronization flow

## Agent flow
User input
→ local Gemma
→ structured intent + arguments
→ Kotlin validation
→ confirmation when needed
→ deterministic local tool execution
→ Room database
→ user-facing response

The model must not directly write to the database.

## Implemented tools
- CREATE_MILK_RECORD
- SEARCH_FARMERS
- GET_FARMER_HISTORY
- GET_TODAY_SUMMARY
- GET_PENDING_PAYMENTS
- GET_PENDING_UPLOADS

## Demonstration workflow
Milk collection is only the proof-of-concept workflow.

Example:
“Ramesh gave 18 litres, fat 4.2 and SNF 8.6. Payment is pending.”

Expected structured record:
- Farmer: Ramesh
- Quantity: 18 L
- Fat: 4.2
- SNF: 8.6
- Payment: Pending

Ambiguous input must require confirmation rather than silently saving.

## Model
Filename:
gemma3-1b-it-int4.litertlm

Required path:
app/src/main/assets/gemma3-1b-it-int4.litertlm

Expected SHA-256:
1325ae366d31950f137c9c357b9fa89448b176d76998180c08ceaca78bba98be

Model size is approximately 584 MB and is intentionally excluded from GitHub.

## Demo credentials
ORG001 / WORKER001 / 1234

## Important boundaries
- No Gemini/OpenAI/cloud LLM is required for the core agent flow.
- Sync is currently a prototype/demo simulation.
- Do not claim direct integration with Nandini or any real organization's internal systems.
- Defense-related future examples should remain non-operational support workflows.
- Do not replace the deterministic validation layer with unrestricted model-generated database writes.

## Last known physical-device result
The real Gemma model was successfully installed and executed on the target Snapdragon 6s Gen 3 Android phone with approximately 7.2 GB total RAM. A local prompt generated a correct response without a cloud endpoint.

Reported timings:
- engine initialization: ~1.2 s
- generation: ~1.8 s for 128 tokens

## Immediate next validation
Run on the physical phone:
1. Create milk record from natural-language input.
2. Query pending payments.
3. Query farmer history.
4. Test ambiguous quantity and verify confirmation.
5. Repeat with airplane mode enabled.

## Transfer rule
Never commit:
- gemma3-1b-it-int4.litertlm
- .gradle/
- build/
- .idea/
- local.properties
- generated APK/AAB files
