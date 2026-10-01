# FREEDOM BYOM Architecture Report
## Bring Your Own Model — Local Model Management

**Document Date:** 2026-10-01  
**Branch:** `framework/organization-model-layer`  
**Status:** Implemented

---

## 1. Overview

FREEDOM supports a real **Bring Your Own Model (BYOM)** capability that allows an organization administrator to import a compatible local `.litertlm` model file and use it as the active inference provider for the FREEDOM semantic/query pipeline.

The BYOM system has two distinct layers:

| Layer | Who sees it | What they see |
|-------|------------|---------------|
| **Worker experience** | FIELD_WORKER, MANAGER | Clean operational UI — no model/framework terminology |
| **Admin experience** | ORG_ADMIN | Local Models screen with full model management |

---

## 2. Architecture Principles

All BYOM design follows these invariants:

- **Model output is untrusted input** — always validated through the FreedomQuery parser before execution
- **Organization identity never comes from model output** — always from the authenticated local session
- **SHA-256 is computed locally from actual file bytes** — never trusted from metadata
- **Only COMPATIBLE models may be activated** — compatibility is determined by real inference, not user assertion
- **Removing a model never deletes Room data** — only deletes the model file and manifest
- **Authorization is enforced by code** — MANAGE_MODEL_PROVIDER permission gate, never on the honor system

---

## 3. File Structure

### New Framework Files

| File | Purpose |
|------|---------|
| `framework/model/LocalModelManifest.kt` | Extended metadata: SHA-256, fileSize, localPath, organizationId, compatibilityStatus, lifecycle state |
| `framework/model/ModelImportManager.kt` | SAF file import, single-pass SHA-256, private storage placement |
| `framework/model/LocalLitertModelProvider.kt` | Real LiteRT-LM provider loading from file path (mirrors AIEngineProvider pattern) |
| `framework/model/ModelCompatibilityChecker.kt` | Controlled test prompt → parser → COMPATIBLE/INCOMPATIBLE/LOAD_FAILED |

### New UI Files

| File | Purpose |
|------|---------|
| `ui/screens/admin/LocalModelsViewModel.kt` | Import, check, activate, remove — org identity from session |
| `ui/screens/admin/LocalModelsScreen.kt` | Admin model management UI with file picker and lifecycle cards |

### Modified Files

| File | Change |
|------|--------|
| `ui/navigation/Screen.kt` | Added `AdminLocalModels` route |
| `ui/navigation/FreedomApp.kt` | Added Settings nav item (ORG_ADMIN only), AdminLocalModels screen branch |
| `ui/screens/home/HomeScreen.kt` | Framework status bar hidden from FIELD_WORKER; admin shortcut shown to ORG_ADMIN only |
| `framework/model/ModelProvider.kt` | Extended `ModelInfo` with BYOM fields (source, runtimeType, sha256, organizationId, etc.) |

---

## 4. Model Lifecycle State Machine

```
REGISTERED
    ↓ (compatibility check)
COMPATIBLE_VERIFIED ←→ INCOMPATIBLE
    ↓ (activate)
ACTIVATED
    ↓ (replaced or explicit deactivation)
DEACTIVATED
    ↓ (remove)
REMOVED
```

State transitions are enforced in `LocalModelsViewModel`. INCOMPATIBLE models cannot be activated.

---

## 5. Import Pipeline

```
User taps "Import Local Model"
    ↓
ActivityResultContracts.OpenDocument (accepts */* → validated by extension in ViewModel)
    ↓
ContentResolver.openInputStream(uri)
    ↓ (streaming, 8 KB buffer)
context.filesDir/freedom_models/<uuid>.litertlm
    ↓ (single-pass)
SHA-256 computed over file bytes
    ↓
LocalModelManifest created
    organizationId = from authenticated session (NEVER from file)
    status = REGISTERED
    compatibilityStatus = null
```

---

## 6. Compatibility Check

The compatibility check sends a controlled, deterministic test prompt to the model and verifies the output conforms to the FREEDOM query format:

```
Test prompt → LocalLitertModelProvider.generateText()
    ↓
JSON extraction (finds first {...} in output, handles markdown wrapping)
    ↓
Field validation:
  - JSON object?
  - Contains "intent" key?
  - Intent is a known FREEDOM capability?
    ↓
COMPATIBLE / INCOMPATIBLE / LOAD_FAILED
```

Only COMPATIBLE models may be activated.

---

## 7. Activation and Provider Switching

When an imported model is activated:
1. The previous imported provider (if any) is released and its engine closed
2. A new `LocalLitertModelProvider` is constructed from the manifest
3. `initialize()` is called (GPU → CPU fallback, same as built-in Qwen)
4. The provider is registered and activated in `ModelProviderRegistry`
5. `QwenQueryParser.parse()` routes through `ModelProviderRegistry.getActiveProvider()` — no code change needed

When "Use FREEDOM Model" is selected:
1. `ModelProviderRegistry.disableActiveProvider()` is called
2. The imported engine is released
3. `QwenQueryParser` falls back to `AIEngineProvider` (the built-in Qwen model)

---

## 8. Role-Based Access

| Role | Can see Local Models screen | Can import/activate/remove |
|------|---------------------------|---------------------------|
| FIELD_WORKER | ❌ Never | ❌ Never |
| MANAGER | ❌ Never | ❌ Never |
| ORG_ADMIN | ✅ Yes | ✅ Yes |

Enforcement layers:
1. Bottom nav "Admin" item only rendered if `canManageModels == true`
2. AdminLocalModels screen checks permission at render time, navigates back if unauthorized
3. `LocalModelsViewModel` checks session organizationId before import
4. `Permission.MANAGE_MODEL_PROVIDER` is only in `UserRole.ORG_ADMIN.defaultPermissions`

---

## 9. Worker UI Cleanliness (Phase 11)

FIELD_WORKER sees:
- ✅ "Welcome, [Name]" header
- ✅ Offline status chip ("Ready • Working Offline")
- ✅ Metric cards (volume, entries, pending)
- ✅ "Collect Milk", "Records & Payments", "Ask FREEDOM", "Send Data"
- ❌ No model names (Qwen, Gemma, LiteRT)
- ❌ No organization IDs
- ❌ No framework terminology (JSON, DSL, AST)
- ❌ No technical status bars
- ❌ No Admin nav item

ORG_ADMIN additionally sees:
- ✅ Framework status bar (org ID, role, model name, "Manage" link)
- ✅ "Administration" section with "Local Models" card
- ✅ "Admin" tab in bottom navigation

---

## 10. Organization Scoping

Each `LocalModelManifest` carries `organizationId` set at import time from the authenticated session. This is enforced at the ViewModel level — `ModelImportManager.importModel()` requires an `organizationId` parameter that the ViewModel provides from `SessionManager.getCurrentSessionOrNull()?.organizationId`.

A model imported by ORG001 cannot be activated for ORG002 because:
- The manifest carries `organizationId = "ORG001"`
- The active session must match for the admin to access the Local Models screen at all
- No code path allows cross-organization model activation

---

## 11. Model Failure Handling

If a model fails during inference (`generateText()` returns null):
- `QwenQueryParser.parse()` falls back to `AIEngineProvider` (built-in Qwen)
- If both fail, `parseFallback()` is invoked (deterministic rules)
- No fake results are returned — failure is surfaced as a user-friendly operational message
- Technical error details go to LogCat only

---

## 12. Storage Management

Imported models are stored at:
```
context.filesDir/freedom_models/<uuid>.litertlm
```

This is Android app-private storage — not accessible by other apps.

On removal:
1. Model deactivated if active
2. Engine released (native resources freed)
3. Physical file deleted
4. Manifest removed from in-memory registry
5. Room operational data is NOT affected

---

## 13. Currently Implemented vs. Planned

### Currently Implemented ✅
- `LocalModelManifest` with full lifecycle state machine
- `ModelImportManager` (SAF import, SHA-256, private storage)
- `LocalLitertModelProvider` (real LiteRT-LM Engine from file path)
- `ModelCompatibilityChecker` (controlled inference test)
- `LocalModelsViewModel` (full import → check → activate → remove lifecycle)
- `LocalModelsScreen` (admin UI with file picker, status cards, actions)
- Role-based navigation (ORG_ADMIN-only admin tab + screen)
- FIELD_WORKER clean UI (no technical terms)
- Organization scoping at import time
- Fallback chain: imported model → Qwen → deterministic

### Planned / Future
- Persistence of model manifests across app restarts (currently in-memory only)
- Multiple simultaneous registered models with named switching
- ORGANIZATION_PROVIDED model pre-configuration at org setup
- Model version management and update detection
- Network-based model transfer (organization-controlled, not cloud SaaS)

---

## 14. Runtime Architecture Diagram

```
User natural language query
         ↓
QwenQueryParser.parse()
         ↓
ModelProviderRegistry.getActiveProvider()
    ↓ (if imported model active)              ↓ (if no imported model)
LocalLitertModelProvider                  AIEngineProvider (Qwen)
    .generateText(prompt)                     .generateText(prompt)
         ↓                                         ↓
         └──────────── raw JSON output ────────────┘
                              ↓
                    FreedomQuery parsing + validation
                              ↓
                    AuthorizationPolicy.authorize()
                              ↓
                    FreedomQueryExecutor
                              ↓
                         Room DB
                              ↓
                    Verified, operational result
```

---

*This document describes the FREEDOM BYOM implementation on branch `framework/organization-model-layer`. It covers real implemented capabilities only.*
