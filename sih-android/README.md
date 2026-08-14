# sih-android
**Disaster Response Coordination Platform — Citizen Android App**  
SIH 2026 · Agent C (Android App Shell)

---

## Overview

The citizen-facing Android app for offline-capable disaster SOS reporting.

## Phase 2: Refinements & Fix Pass

The following fixes were implemented to enforce Day 1 architectural constraints and improve stability:
- **Location Permission Race:** Fixed a race condition where SOS was fired before permission could be granted. SOS creation now correctly awaits the launcher callback.
- **Onboarding Skip Persistence:** Skipping the Medical Profile setup is now persisted via SharedPreferences and correctly skips the screen on app restarts.
- **Network Security Separation:** Development cleartext configurations are strictly isolated to the `debug` source set to prevent vulnerabilities in `release` builds.
- **Improved Status Screen Efficiency:** The Status Screen now checks `isRegistered()` before polling the backend, preventing unnecessary 401 calls for offline/unregistered users.
- **Gateway Sync Tests:** Added test coverage for failure, retry, deduplication, and 401 scenarios in the Gateway Upload worker.
- **Blood Type Validation:** Ensures inputs like "A+" or "O-" are correctly formatted during Medical Profile setup.

> **Note:** The ambiguity around `device_id` vs `installation ID` in the Day 1 registration contract requires backend confirmation before resolution. The existing fallback behavior remains in place to ensure offline SOS capabilities are not broken.

- **One tap** creates a valid SOS — works with the phone in **airplane mode**, no network required.
- SOS messages spread phone-to-phone via the **offline mesh relay** (Nearby Connections — implemented by Agent A+B in `:relay`).
- Any phone with internet acts as a **gateway** and uploads the full relay store to the backend.
- Medical profile filled once during onboarding — **auto-attached** to every SOS without re-typing.

---

## Module Structure

```
:app      — Citizen UI (Jetpack Compose), navigation, DI wiring
:data     — Room database, WorkManager sync worker, EncryptedSharedPreferences
:network  — Retrofit client, API interface, request/response models
:relay    — Stub interface (real implementation: Agent A+B)
```

---

## Prerequisites

| Tool | Version |
|------|---------|
| JDK | 11+ (23.0.2 used in dev) |
| Android SDK | 26+ (compileSdk 35) |
| Gradle | 8.7 (via wrapper) |
| AGP | 8.7.3 |
| Kotlin | 2.1.0 |

---

## Build Commands

```bash
# First time: ensure gradlew is executable
chmod +x gradlew

# Build debug APK
./gradlew :app:assembleDebug

# Run unit tests
./gradlew :data:test
./gradlew :network:test

# Run all checks
./gradlew check

# Install on connected device
./gradlew :app:installDebug
```

---

## API Endpoints (consumed)

| Method | Endpoint | When |
|--------|----------|------|
| `POST` | `/api/v1/auth/device/register` | First app launch |
| `POST` | `/api/v1/sos/batch` | WorkManager CONNECTED trigger + immediate one-shot on SOS creation |
| `GET`  | `/api/v1/sos/{uuid}/status` | Status screen + auto-poll on connectivity return |

Full contract: see [`ApiEndpoints.md`](ApiEndpoints.md) and [`../day1-contracts-and-repo-setup.md`](../day1-contracts-and-repo-setup.md).

---

## Backend URL

- **Debug (emulator):** `http://10.0.2.2:8000/api/v1/` (set in `app/build.gradle.kts`)
- **Release:** update `buildConfigField("String", "BASE_URL", ...)` in `app/build.gradle.kts`

---

## Architecture

```
:app (UI)
  └── HomeScreen / OnboardingScreen / StatusScreen (Compose)
  └── ViewModels (Hilt)
  └── AppNavigation (NavHost)
  └── AppModule (Hilt — wires DisasterApi, RelayRepository stub)

:data
  └── Room DB (SosRequestEntity, UserMedicalProfileEntity)
  └── GatewaySyncWorker (WorkManager, NetworkType.CONNECTED)
  └── DevicePreferences (EncryptedSharedPreferences)
  └── SosRepository / UserMedicalProfileRepository
  └── DataModule (Hilt)

:network
  └── DisasterApi (Retrofit interface — 3 endpoints)
  └── AuthInterceptor (Bearer device_jwt)
  └── RetrofitClientFactory (OkHttp + Moshi)
  └── DTOs (Moshi @JsonClass codegen)

:relay (stub — Agent A+B replaces StubRelayRepository)
  └── RelayRepository (interface)
  └── StubRelayRepository (returns empty list)
```

---

## Key Invariants

1. **`SosRepository.createSos()` makes zero network calls** — works in airplane mode.
2. **`SosRequestDto` has no `status` field** — device-side only, never sent to backend.
3. **Records are NOT deleted after upload** — relay mesh may still need them (TTL = 72 hours).
4. **`UserMedicalProfile` is singleton (id=1)** — auto-snapshotted at SOS creation.
5. **Gateway uploads entire local store** — not just own SOS.
6. **Zero personal account / login screens** — citizen SOS creation is completely anonymous and unblocked by login walls.

---

## Integration Dependencies

| Dependency | Provider | Status |
|------------|----------|--------|
| `POST /api/v1/auth/device/register` | Agent D | Awaiting backend (Debug cleartext config active for `10.0.2.2:8000`) |
| `POST /api/v1/sos/batch` | Agent D | Awaiting backend (Immediate sync + periodic background worker) |
| `GET /api/v1/sos/{uuid}/status` | Agent D | Awaiting backend (Auto-polled on network reconnect) |
| `RelayRepository` real implementation | Agent A+B | Stub provided; see [`../COMPONENT_C_TO_AB_RELAY_HANDOFF.md`](../COMPONENT_C_TO_AB_RELAY_HANDOFF.md) |

---

## Known Limitations & Integration Notes

- **A+B Mesh Engine:** `:relay` currently returns empty lists and stub implementations pending Agent A+B integration. Complete handoff specifications are provided in [`COMPONENT_C_TO_AB_RELAY_HANDOFF.md`](../COMPONENT_C_TO_AB_RELAY_HANDOFF.md).
- **`device_id` Contract Reconciliation:** Handled via local UUID fallback for offline capability prior to backend handshake.

---

## Tracking & Handoff Files

| File | Purpose |
|------|---------|
| [`Logs.md`](Logs.md) | Chronological action log |
| [`Agent.md`](Agent.md) | Context, decisions, change history |
| [`ApiEndpoints.md`](ApiEndpoints.md) | API contract reference |
| [`COMPONENT_C_TO_AB_RELAY_HANDOFF.md`](../COMPONENT_C_TO_AB_RELAY_HANDOFF.md) | Complete handoff guide for Agent A+B (Mesh Engine) |
