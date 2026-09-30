# FREEDOM Framework Architecture Report

**Document Version:** 1.0.0  
**Target Specification:** ASYNC'26 Technical Track  
**Classification:** Local-First Agent Framework  
**Reference Profile:** `milk_collection` (Mandya District Cooperative Milk Producers Union / Nandini)  
**Authoritative Path:** `training/reports/freedom_framework_architecture.md`  

---

## 1. Framework Purpose

FREEDOM originated as an on-device AI system for rural Indian dairy cooperatives, operating fully offline on resource-constrained Android devices. The core design insight is that **the AI model is not the database and is not the application**; it is an untrusted semantic parser translating unstructured human speech or text into typed, verifiable intent.

The FREEDOM Framework transforms this specific dairy application into a **reusable local-first AI framework skeleton**. The long-term objective is to enable any operational enterprise or cooperative to:
1. Establish a cryptographically local organization identity and local activation without cloud lock-in.
2. Define a bounded organization profile describing supported entities, fields, and queries.
3. Manage multi-user access with centralized roles and capability-based authorization.
4. Run with a FREEDOM-provided local small language model (SLM) or connect an organization-provided local adapter.
5. Keep 100% of operational data on-device in Room/SQLite by default.
6. Replace or upgrade the local model without touching core business logic or query execution.
7. Integrate optional, organization-controlled batch synchronization without compromising offline autonomy.

---

## 2. Core Architecture

The framework enforces a strict directional pipeline where natural language is parsed into a typed domain-specific language (FreedomQuery), validated, authorized against session context, and executed deterministically against Room/SQLite:

```
User Input (Voice / Text)
       ↓
Model Layer (ModelProvider Interface)
  [Qwen3-1.7B / Gemma / Org Model]
       ↓
FreedomQuery DSL (Untrusted Semantic Representation)
       ↓
QueryValidator (Structural & Semantic DSL Invariant Checks)
       ↓
AuthorizationPolicy (Session Context + Role Permissions)
       ↓
FreedomQueryExecutor (Deterministic Kotlin Calculations & Aggregations)
       ↓
Room SQLite Database (Local Source of Truth)
       ↓
Verified Result / Structured Output
```

### Invariant Architectural Rules
- **Room SQLite = Single Source of Truth:** The AI model never holds or stores database state.
- **Model Output = Untrusted Input:** Model-generated outputs are treated identically to raw user input; they must pass strict syntactic validation and session authorization before reaching the execution engine.
- **Zero Raw SQL Generation:** The model generates high-level DSL structures (`FreedomQuery`), never raw SQL strings or table mutations.
- **Zero Hallucinated Facts:** Read queries cannot mutate state; missing records return verified negative or not-found statuses rather than hallucinated fallback data.

---

## 3. Organization Model

An organization in FREEDOM is represented by the `OrganizationProfile` abstraction. It delineates the operational boundaries of the deployment:

```kotlin
data class OrganizationProfile(
    val organizationId: String,
    val organizationName: String,
    val domain: String,
    val organizationType: OrganizationType,
    val enabledEntities: Set<String>,
    val enabledFields: Set<String>,
    val enabledQueryCapabilities: Set<QueryCapability>,
    val activeModelProvider: ModelProviderType,
    val policyConfig: Map<String, String>,
    val registrationNumber: String?,
    val region: String?,
    val isActive: Boolean
)
```

### Identity vs. Credentials
`organizationId` is strictly an identifier, not a credential. Possession of an organization ID grants zero execution privileges. Authentication is handled through local session credentials, and queries are verified against the authenticated `SessionContext`.

---

## 4. User and Role Model

The framework defines a centralized role-based access control (RBAC) model supporting multi-user field operations:

```
Organization
    ↓
OrganizationUser (userId, orgId, role, custom permissions)
    ├── FIELD_WORKER : READ_RECORDS, WRITE_RECORDS, VIEW_OWN_PROFILE, QUERY_KNOWLEDGE_BASE
    ├── MANAGER      : + VIEW_AGGREGATES, VIEW_ALL_WORKERS, MANAGE_PAYMENTS
    └── ORG_ADMIN    : All permissions (Full organizational control)
```

### Capability-Based Authorization
Permissions (`Permission` enum) are atomic capabilities. Roles provide sane default sets, but individual users can receive additional permissions or have permissions revoked. Role checks are never hardcoded into UI screens; they are evaluated at the query dispatch boundary by `AuthorizationPolicy`.

---

## 5. Model Provider Architecture

The model execution boundary is completely abstracted behind `ModelProvider`:

```kotlin
interface ModelProvider {
    val providerId: String
    val displayName: String
    val providerType: ModelProviderType
    fun isAvailable(): Boolean
    suspend fun generateText(prompt: String): String?
    fun getModelInfo(): ModelInfo
}
```

The system classifies model origins via `ModelProviderType`:
- `FREEDOM_PROVIDED`: Default pre-quantized LiteRT models (e.g., Qwen3-1.7B INT4).
- `ORGANIZATION_PROVIDED`: Models or fine-tuned LoRA adapters trained and supplied by the client organization.
- `NONE`: Deterministic-only execution fallback when no neural engine is loaded.

`ModelProviderRegistry` coordinates the model lifecycle (`register`, `activate`, `replace`, `disable`) in a thread-safe manner without modifying application code.

---

## 6. Security Boundary

The security model prevents privilege escalation via model hallucination or prompt injection:

1. **Model Output Cannot Choose Identity:** `organizationId`, `userId`, and `role` are resolved exclusively from `SessionManager.getCurrentSession()`. Even if the model outputs an arbitrary `orgId` in a payload, the query executor overrides or rejects it using the local authenticated session context.
2. **Deterministic Scoping:** All database queries are filtered by:
   ```kotlin
   (record.orgId.isBlank() || record.orgId == sessionOrgId) &&
   (record.workerId.isBlank() || record.workerId == sessionWorkerId)
   ```
3. **Query Validation Prior to Authorization:** Malformed queries fail fast in `QueryValidator` before reaching the policy engine or database.
4. **Local Execution:** Zero outbound telemetry, zero remote inference calls, and zero external credential leakage.

---

## 7. Local-First Data Flow

```
1. User speaks prompt: "Show Suresh's pending payment"
2. Voice/Text captured by UI layer
3. QwenQueryParser passes prompt + system instructions to ModelProviderRegistry.getActiveProvider()
4. Active model (Qwen3-1.7B) generates FreedomQuery JSON
5. QwenQueryParser parses JSON into typed FreedomQuery
6. QueryValidator checks field types, numeric constraints, and temporal formats
7. AuthorizationPolicy verifies SessionContext permissions (Permission.READ_RECORDS)
8. FreedomQueryExecutor executes query:
   a. EntityResolver maps "Suresh" -> FarmerEntity (ID, normalized name)
   b. TemporalResolver resolves time period (TODAY / THIS_WEEK / explicit)
   c. Records retrieved from Room SQLite scoped to active session org & worker
   d. Aggregations, sorting, and conditional reports computed deterministically
9. ExecutionResult returned to AskFreedomViewModel
10. UI renders verified factual summary with zero hallucination
```

---

## 8. Organization Profile Concept

The framework decouples application behavior from hardcoded constants. The `OrganizationProfile` specifies:
- Which domain entities are active (e.g., `FARMER`, `MILK_RECORD`).
- Which fields are enabled for querying and reporting (e.g., `QUANTITY`, `FAT`, `SNF`, `PAYMENT_STATUS`).
- Which query capabilities are permitted (e.g., `AGGREGATE_QUERIES`, `TEMPORAL_QUERIES`, `PAYMENT_QUERIES`).

Concrete profiles are registered at application startup via `OrganizationRegistry.register(profile)`.

---

## 9. Bounded Schema Customization

Rather than creating a brittle dynamic SQL runtime or arbitrary entity-relationship engine, FREEDOM implements **bounded customization**:
- Schema extension points allow organizations to register custom fields within defined primitive categories (numeric measurements, categorical statuses, metadata timestamps).
- Business calculations (such as cooperative milk pricing: $\text{Base Rate} + \Delta\text{Fat} + \Delta\text{SNF}$) are configured as pluggable algorithmic formulas rather than model-generated scripts.
- The underlying Room SQLite schema remains strictly typed, validated, and indexed for embedded mobile performance.

---

## 10. Model Replacement Flow

Organizations can replace the local model without re-architecting the application:
1. Train a compatible SLM (e.g., Qwen3-1.7B, Gemma 3 1B) or fine-tune a LoRA adapter on domain-specific prompt-to-JSON pairs matching the `FreedomQuery` specification.
2. Convert and quantize the model to LiteRT-LM format (`.litertlm` / `.tflite`).
3. Package the artifact locally or place it in the application's storage directory.
4. Implement a lightweight `ModelProvider` wrapper (or register via `ModelProviderRegistry.register()`).
5. Call `ModelProviderRegistry.activate(newProviderId)`.
6. All natural language queries immediately route through the new model, while `QueryValidator`, `AuthorizationPolicy`, and `FreedomQueryExecutor` continue enforcing 100% deterministic correctness.

---

## 11. Current Reference Implementation

The existing dairy collection workflow is preserved in its entirety as the **first concrete reference profile**:
- **Profile:** `MilkCollectionProfile` (`ORG001` - Mandya District Cooperative Milk Producers Union).
- **Entities:** `FarmerEntity`, `MilkRecordEntity`.
- **Domain Logic:** Fat/SNF quality pricing formulas, local payment recording, milk collection verification.
- **Tests:** All 69 pre-existing unit and integration tests remain 100% active and healthy.

---

## 12. Future Extensibility

The framework skeleton lays the architectural foundation for:
1. **Multi-Domain Reference Profiles:** Expanding from dairy to agricultural warehouse inventory, rural healthcare clinic visits, and microfinance loan collection.
2. **Organization-Controlled Peer-to-Peer Sync:** Encrypted offline Wi-Fi Direct or Bluetooth sync between field workers and village aggregation depots without intermediate cloud servers.
3. **Hardware Accelerators:** Direct binding to on-device NPU/DSP accelerators through LiteRT delegates.

---

## 13. Current Limitations

1. **Single Active Organization at Runtime:** While multiple organizations can be registered, the application currently operates on one active profile per session.
2. **Predefined Room Schema:** Entities must be compiled into the Room database; fully dynamic runtime SQLite table creation is intentionally not supported.
3. **Local Activation Abstraction:** `ActivationState` is currently a framework contract verified locally; cryptographic certificate signing or QR-based activation verification is reserved for production deployment.

---

## 14. What Is Intentionally NOT Implemented

To protect project stability and maintain focus on the ASYNC'26 core requirements, the following were intentionally excluded:
- **Cloud SaaS / Multi-Tenant Remote Backend:** FREEDOM is explicitly a local-first system.
- **Dynamic SQL Query Generation:** Allowing an LLM to generate raw SQL is a critical security vulnerability and violates deterministic verification.
- **Commercial Billing / Payment Gateway SDKs:** Financial accounting is maintained through deterministic payment status records, not third-party payment gateways.
- **Automated Runtime Migrations for Arbitrary Schemas:** Database migrations remain explicitly managed via Room migrations for zero-loss stability.
