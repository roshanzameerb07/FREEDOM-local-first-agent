# FREEDOM Android Real-Device Demo & Deployment Report

**Target Branch:** `framework/organization-model-layer`  
**Classification:** Hackathon Demo Account & Device Deployment  
**Authoritative Path:** `training/reports/android_demo_credentials_and_deployment.md`  

---

## 1. Hackathon Demo Organization

- **Organization Name:** FREEDOM Demo Organization  
- **Organization ID:** `FREEDOM-DEMO-001`  
- **Domain:** `milk_collection` (Reference Implementation)  
- **Organization Type:** `DEMO`  
- **Region:** Mandya Demonstration Route  
- **Registration Code:** `DEMO-HACKATHON-2026`  

---

## 2. Dedicated Demo User Account

- **User ID / Worker ID:** `DEMO-FIELD-01`  
- **Display Name:** Demo Field Officer  
- **Role:** `FIELD_WORKER`  
- **Assigned Permissions:**
  - `Permission.READ_RECORDS`
  - `Permission.WRITE_RECORDS`
  - `Permission.VIEW_AGGREGATES`
  - `Permission.VIEW_OWN_PROFILE`
  - `Permission.VIEW_ORGANIZATION_INFO`
  - `Permission.QUERY_KNOWLEDGE_BASE`
- **Scope & Security:**
  - Authenticated via local `AuthRepository`
  - Session established under `SessionManager` and `SessionContext`
  - Zero model influence on identity or permission escalation

---

## 3. Account Provisioning Mechanism

The demo account is provisioned directly into the local-first authentication repository and framework registry:
1. `MilkCollectionProfile.createHackathonDemoProfile()` registers `FREEDOM-DEMO-001` upon framework initialization.
2. `AuthRepository.login()` authenticates the demo credentials without external cloud relays.
3. Upon authentication, `FreedomFramework.onUserAuthenticated()` sets `FREEDOM-DEMO-001` as the active organization in `OrganizationRegistry` and binds the authenticated `OrganizationUser` with role `FIELD_WORKER` to `SessionManager`.
4. `FreedomQueryExecutor` scopes database queries to the active demo organization while granting access to the pre-seeded reference dataset (`Ramesh`, `Suresh`, `Mahesh`).
5. `LoginScreen` and `LoginViewModel` pre-fill the demo credentials by default for rapid device testing, with a "Reset Demo" button for instant recovery.

---

## 4. Reference Data Verification

Under the demo account session (`FREEDOM-DEMO-001` / `DEMO-FIELD-01`), the following representative queries operate deterministically against the local Room SQLite database:
- *"How much milk did Ramesh give this week?"* $\rightarrow$ Resolves to Ramesh delivery records (18.0 L).
- *"How much milk was collected today?"* $\rightarrow$ Summarizes today's local collection volumes.
- *"Did Ramesh get paid?"* $\rightarrow$ Verifies local payment status (`PENDING`).

---

## 5. Build & Deployment Artifacts

- **Build Target:** Debug APK (`assembleDebug`)
- **APK Path:** `app/build/outputs/apk/debug/app-debug.apk`
- **APK Size:** 2,206,850,733 bytes (~2.05 GB, bundled with LiteRT native runtime and on-device model)
- **Package Name:** `com.example.freedom`
- **Minimum SDK:** 26 (Android 8.0) | **Target SDK:** 36

---

## 6. Device Detection & Installation

- **ADB Path:** `$LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe`
- **Device ID:** `RZCY90ETLVZ`
- **Device Status:** Authorized device
- **Installation Command:** `adb -s RZCY90ETLVZ install -r app/build/outputs/apk/debug/app-debug.apk`
- **Result:** `Performing Streamed Install -> Success`
- **Activity Launch:** `am start -n com.example.freedom/.MainActivity`
- **Current Execution State:** Foreground active (`topResumedActivity=ActivityRecord{... com.example.freedom/.MainActivity}`)

---

## 7. Known Limitations

- **On-Device SLM Initial Extraction:** On very first launch, LiteRT extracts the model asset to application storage (`filesDir`), requiring ~30-45 seconds of background initialization before local generative inference becomes active. Deterministic fallback is active immediately.
