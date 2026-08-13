# sih-android
**Disaster Response Coordination Platform — Citizen Android App**  
SIH 2026 · Agent C (Android App Shell)

---

## Overview

The citizen-facing Android app for offline-capable disaster SOS reporting.

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

1. **`SosRepository.createSos()` makes zero network calls** — works in airplane mode
2. **`SosRequestDto` has no `status` field** — device-side only, never sent to backend
3. **Records are NOT deleted after upload** — relay mesh may still need them (TTL = 7 days)
4. **`UserMedicalProfile` is singleton (id=1)** — auto-snapshotted at SOS creation
5. **Gateway uploads entire local store** — not just own SOS

---

## Integration Dependencies

| Dependency | Provider | Status |
|------------|----------|--------|
| `POST /api/v1/auth/device/register` | Agent D | Awaiting backend (Cleartext HTTP fix applied) |
| `POST /api/v1/sos/batch` | Agent D | Awaiting backend |
| `GET /api/v1/sos/{uuid}/status` | Agent D | Awaiting backend |
| `RelayRepository` real implementation | Agent A+B | Stub (empty list) |

---

## Known Limitations

- No first-launch detection for onboarding redirect — still pending.
- Relay mesh (`Agent A+B`) is currently stubbed out.

---

## Tracking Files

| File | Purpose |
|------|---------|
| [`Logs.md`](Logs.md) | Chronological action log |
| [`Agent.md`](Agent.md) | Context, decisions, change history |
| [`ApiEndpoints.md`](ApiEndpoints.md) | API contract reference |
