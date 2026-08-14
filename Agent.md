# Agent.md — sih-android
> Disaster Response Coordination Platform · SIH 2026  
> Component: Android App Shell  
> Agent: C
> Last Updated: 2026-08-14 (Component C Fix Pass & A+B Handoff Complete)

---

## Project Context

This is the citizen-facing Android application for the Disaster Response Coordination Platform. During disasters, mobile networks fail — this app lets citizens:
1. Create an SOS with a single tap (offline-capable — no network needed)
2. Relay SOS messages phone-to-phone via the `:relay` module (Nearby Connections, epidemic routing — **implemented by Agent A+B, not Agent C**)
3. Automatically upload all stored SOS messages (own + relayed) when internet is available, acting as a gateway
4. Check delivery status of their own SOS

---

## Repository Structure

```
sih-android/
├── app/                    → :app module — UI, navigation, DI wiring (Agent C)
├── data/                   → :data module — Room DB, entities, DAOs, WorkManager (Agent C)
├── network/                → :network module — Retrofit client + models (Agent C)
├── relay/                  → :relay module — Nearby Connections + mesh routing (Agent A+B)
│                             Agent C owns the stub/interface only
├── gradle/
│   └── libs.versions.toml  → Version catalog
├── build.gradle.kts        → Root build file
├── settings.gradle.kts     → Module declarations
├── gradlew / gradlew.bat   → Gradle wrapper
├── Logs.md                 → Chronological action log (this project)
├── Agent.md                → This file — context + change history
└── ApiEndpoints.md         → API contract reference for this component
```

---

## Ownership Boundaries (DO NOT CROSS)

| Component | Owner | Agent C Interaction |
|-----------|-------|---------------------|
| `:app` | Agent C | Full ownership |
| `:data` | Agent C | Full ownership |
| `:network` | Agent C | Full ownership (shared by all Android modules) |
| `:relay` | Agent A+B | Agent C consumes `RelayRepository` interface only |
| Backend REST API | Agent D | Agent C calls endpoints, never modifies |
| Dashboard | Agent F | No interaction |
| AI/Optimizer | Agent E | No interaction |

---

## Architecture Decisions

| Decision | Rationale |
|----------|-----------|
| Hilt for DI | Standard Jetpack DI, works well with Compose + WorkManager |
| Room for local DB | Required by system contract |
| WorkManager for sync | Required by system contract; handles background, retry, constraint-based scheduling |
| EncryptedSharedPreferences | Secure storage for device_jwt — never plain SharedPreferences |
| Retrofit + OkHttp | Standard Android HTTP client |
| Jetpack Compose | Required by system contract |
| Single-module per concern | Matches the contract's module layout exactly |

---

## Non-Negotiable Constraints (from Master Prompt)

1. **SOS must work in airplane mode** — no network call during SOS creation
2. **Text-only** — no photo/camera/voice fields in any schema
3. **Medical profile auto-attached** — never re-typed during emergency
4. **Location captured once** at SOS creation — no background GPS polling
5. **Gateway mode** — upload entire relay store, not just own SOS
6. **Keep uploaded records** until TTL expiry — they may still be needed for relay
7. **device_jwt** from `POST /api/v1/auth/device/register` stored securely
8. **No Personal Account/Login** — Citizen app is 100% anonymous & unblocked by login walls

---

## Change History

### 2026-08-13
- **INITIAL STATE**: Repository created from scratch. No pre-existing code.
- Produced: `Logs.md`, `Agent.md`, `ApiEndpoints.md`
- Status: Inspection complete. Awaiting implementation approval.

### 2026-08-13 (Phase 2 & Refinements)
- **IMPLEMENTED**: `DeviceRegistrationWorker` created and wired to trigger on first launch in `MainActivity.kt`.
- **IMPLEMENTED**: Wired `GatewaySyncWorker` to enforce 7-day TTL cleanup (`deleteExpiredUploaded`) post-upload.
- **UPDATED**: `SosRequestDto` strict alignment with backend contract (added missing `status` field).
- **UPDATED**: GPS location uses `FusedLocationProviderClient` instead of hardcoded `0.0`.
- **RESOLVED**: Fixed Hilt metadata version mismatches and dependency cycles in `:relay`.

### 2026-08-13 (Day 7 — UI Polish)
- **REDESIGNED**: `StatusScreen.kt` — full visual overhaul to match HomeScreen dark palette.
  - "My SOS" title, SOS info card (Emergency + Created), animated status pill, backend check section.
  - Three pill states: 🟡 Waiting to relay / 🔵 Relaying / 🟢 Uploaded (`AnimatedContent` transition).
- **UPDATED**: `StatusViewModel.kt` — added `formattedCreatedAt` + `emergencyLabel` formatting.
- **FIXED**: SOS resumes after process kill — `DevicePreferences` stores `last_sos_uuid`; cold start navigates directly to Status screen.
  - Back ← from Status → Home clears the stored UUID for a clean next session.

### 2026-08-13 (Day 8 — Integration Round 1 Hardening)
- **ADDED**: Immediate `GatewaySyncWorker` one-shot after SOS creation (`DataModule.enqueueImmediateGatewaySync`).
- **FIXED**: `GatewaySyncWorker.getGatewayLocation()` — replaced 0.0/0.0 stub with `FusedLocationProviderClient.lastLocation` (non-blocking cached read).
- **FIXED**: Cleartext HTTP — `network_security_config.xml` added; permits HTTP to `10.0.2.2` + `localhost` in debug. Resolves `CLEARTEXT not permitted` that blocked `DeviceRegistrationWorker`.
- **ADDED**: `StatusViewModel` — `ConnectivityManager.NetworkCallback` auto-fires `checkBackendStatus()` when device comes online. Pill transitions 🟡→🟢 automatically.
- **FIXED**: Input validation in `HomeScreen` and `OnboardingScreen` — strictly strips alphabetic/invalid characters from numeric and phone fields.

### 2026-08-14 (Day 9 — Contract & Security Hardening)
- **FIXED (P0.4.1)**: Removed `status` from `SosRequestDto` as it's device-side only and must never be serialised to the backend.
- **ADDED (P0.4.2)**: Added `DtoSerializationTest` to guarantee `status` is structurally absent from network payloads.
- **UPDATED (P0.4.4)**: `GatewaySyncWorker` now enforces a 72-hour TTL via `SosConstants.TTL_SECONDS` instead of 7 days.
- **FIXED (P0.4.5)**: Fixed 401 infinite loop in `GatewaySyncWorker` by explicitly enqueuing `DeviceRegistrationWorker` before retrying.
- **SECURED (7.1)**: Changed `HttpLoggingInterceptor` level from `BODY` to `HEADERS` in `RetrofitClientFactory` to prevent medical data leak in logcat.

### 2026-08-14 (Component C Fix Pass & A+B Handoff)
- **FIXED (Location Permission Race)**: Replaced race-prone permission launch in `HomeScreen.kt` and `HomeViewModel.kt` with a callback-based permission launcher (`triggerSosWithPermissionResult`). SOS is never created before permission resolution.
- **SECURED (Network Security Config)**: Moved cleartext exception exclusively into `app/src/debug/res/xml/network_security_config.xml` and debug manifest; release build strictly enforces HTTPS.
- **PERSISTED (Onboarding Skip)**: Added `KEY_ONBOARDING_SKIPPED` in `DevicePreferences` so skipping medical setup survives app restarts without re-prompting.
- **VALIDATED (Blood Type Validation)**: Added blood group regex validation in `OnboardingViewModel.kt`.
- **OPTIMIZED (Startup & Status Polling)**: Removed `runBlocking` from `MainActivity.kt` in favor of asynchronous startup destination resolution. `StatusViewModel` now skips polling if device is offline or unregistered.
- **AUTH INTERCEPTOR**: Tightened registration path exclusion to `.endsWith("/auth/device/register")`.
- **COMPREHENSIVE TEST SUITE**: Added unit and viewmodel test coverage across `:app` (`HomeViewModelTest`, `OnboardingViewModelTest`), `:data` (`GatewaySyncWorkerTest`, `TtlConstantTest`), and `:network` (`DtoSerializationTest`).
- **HANDOFF CREATED**: Authored complete specification `COMPONENT_C_TO_AB_RELAY_HANDOFF.md` detailing integration boundaries, persistence ownership, manifest exchange, metadata rules, and guardrails for Agent A+B.
- **VERIFIED**: Local Android SDK installed, unit test suite executed (`BUILD SUCCESSFUL`), and debug APK assembled (`app-debug.apk`).

---

## Known Deviations & Contract Inconsistencies

1. **`device_id` vs `installation ID` Ambiguity:**
   - **Contract Wording (§1.2 & §1.9):** States `device_id` is the installation ID generated on first app install.
   - **Registration Endpoint Contract (§2):** `POST /auth/device/register` takes only `{device_model, app_version}` and returns a newly minted `device_id`.
   - **Current Implementation:** Generates a local fallback UUID immediately to allow offline SOS creation; if registration occurs later, the backend-returned `device_id` is stored. Flagged for backend reconciliation.

---

## Integration Dependencies

| Dependency | Provider | Status | Notes |
|------------|----------|--------|-------|
| `POST /api/v1/auth/device/register` | Agent D (Backend) | ⏳ Awaiting backend | Debug cleartext configuration active for `10.0.2.2:8000` |
| `POST /api/v1/sos/batch` | Agent D (Backend) | ⏳ Awaiting backend | Immediate sync enqueued after SOS creation + periodic background worker |
| `GET /api/v1/sos/{uuid}/status` | Agent D (Backend) | ⏳ Awaiting backend | Auto-polled on connectivity return via `NetworkCallback` (guarded if unregistered) |
| `RelayRepository` implementation | Agent A+B | ⏳ Awaiting mesh engine | Full handoff guide created (`COMPONENT_C_TO_AB_RELAY_HANDOFF.md`) |
