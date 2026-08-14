# Logs — sih-android
> Disaster Response Coordination Platform · SIH 2026  
> Agent: C — Android App Shell  
> Maintained religiously: every significant action, decision, or deviation is logged here.

---

## Log Format
```
[DATE TIME] [ACTION] [COMPONENT] — Description
```

---

## Entries

### 2026-08-13 11:11 IST — INSPECT — Repository
**Action:** Pre-implementation inspection  
**Status:** Complete (no code modified)  
**Findings:**
- `sih-android` repository did not exist on disk
- Workspace `/Users/manasvisingh/Desktop/Traan/` contained only 3 planning documents
- No modules (`:app`, `:data`, `:network`, `:relay`) existed
- No Kotlin, Compose, Room, Retrofit, or WorkManager code found
- Android SDK available: platforms android-34, android-35, android-36, android-36.1
- Build tools available: 35.0.0, 36.0.0, 36.1.0
- Java 23.0.2 available

**Outcome:** Created `sih-android/` directory. No other files modified.

**Reference Documents Read:**
- `Updated_antigravity-build-prompts.md` — full
- `day1-contracts-and-repo-setup.md` — full
- `15-day-roadmap.md` — full

**Produced:**
- `sih-android/Logs.md` (this file)
- `sih-android/Agent.md`
- `sih-android/ApiEndpoints.md`

---

<!-- New entries appended below -->

### 2026-08-13 11:18–11:35 IST — IMPLEMENT — Phase 1: Project Foundation
**Action:** Full scaffold of sih-android multi-module Android project  
**Status:** Files written. Build pending (Gradle bootstrap in progress).

**Files Created (60+ files across 4 modules):**

**Build Infrastructure:**
- `gradle/libs.versions.toml` — version catalog (AGP 8.3.2, Kotlin 2.0.0, KSP 2.0.0-1.0.21)
- `gradle/wrapper/gradle-wrapper.properties` — Gradle 8.7
- `settings.gradle.kts` — 4 modules declared
- `build.gradle.kts` — root, all plugins apply false
- `local.properties` — SDK path
- `.gitignore`

**:network module (pure HTTP, no UI):**
- `DisasterApi.kt` — 3 Retrofit endpoints, exact §2 paths
- `LocationDto.kt`, `UserMedicalProfileDto.kt`, `SosRequestDto.kt` — §1.2 shapes
- `DeviceRegistrationRequest.kt`, `GatewayUploadBatch.kt` — §2 request bodies
- `DeviceRegistrationResponse.kt`, `BatchUploadResponse.kt`, `SosStatusResponse.kt` — §2 responses
- `AuthInterceptor.kt` — dynamic JWT injection, skips /auth/device/register
- `RetrofitClientFactory.kt` — OkHttp + Moshi, debug/release logging

**:relay module (stub):**
- `RelayRepository.kt` — interface + `StubRelayRepository` (returns empty list)

**:data module:**
- `SosStatus.kt`, `EmergencyType.kt`, `SeverityHint.kt` — exact §1.2 enum values
- `SosRequestEntity.kt` — all §1.2 fields (location flat-mapped, status device-side only)
- `UserMedicalProfileEntity.kt` — §1.1 singleton row (id=1)
- `RoomConverters.kt` — List<String> ↔ JSON via Moshi
- `SosRequestDao.kt`, `UserMedicalProfileDao.kt` — full CRUD + TTL expiry
- `AppDatabase.kt` — version 1, exportSchema=true
- `DevicePreferences.kt` — EncryptedSharedPreferences (AES256-GCM)
- `GatewaySyncWorker.kt` — @HiltWorker, CONNECTED constraint, 401/5xx/retry handling
- `SosRepository.kt` — createSos() is fully offline (Room-only)
- `UserMedicalProfileRepository.kt`
- `DataModule.kt` — Hilt providers + schedulePeriodicGatewaySync()

**:app module:**
- `SihApplication.kt` — @HiltAndroidApp, Configuration.Provider for HiltWorkerFactory
- `MainActivity.kt` — single activity, Compose host
- `AppModule.kt` — Hilt wires AuthInterceptor, DisasterApi, RelayRepository
- `SihTheme.kt` — Material3 with SOS red palette
- `Screen.kt`, `AppNavigation.kt` — NavHost with 3 destinations
- `HomeViewModel.kt` + `HomeScreen.kt` — SOS button (offline), optional fields
- `OnboardingViewModel.kt` + `OnboardingScreen.kt` — medical profile, skippable
- `StatusViewModel.kt` + `StatusScreen.kt` — local + backend status
- `strings.xml`, `themes.xml`

**Tests:**
- `EnumContractTest.kt` — verifies all enum apiValues match §1.2 exactly
- `DtoSerializationTest.kt` — verifies JSON keys match contract field names

**Decision: Gradle wrapper bootstrap via `brew install gradle` then `gradle wrapper --gradle-version 8.7`**

**No contract deviations.**

### 2026-08-13 15:45 IST — IMPLEMENT — Phase 2 & App Refinements
**Action:** Executed Phase 2 features and fixed compilation issues.
**Status:** Complete and verified on running Android emulator.

**Actions Taken:**
- **App Compilation Fixes:** Upgraded `security-crypto` to `1.1.0-alpha06`, downgraded Kotlin to `2.0.21` (to fix Hilt metadata version mismatch), and resolved cyclic dependency between `:relay` and `:data`.
- **Device Registration:** Created `DeviceRegistrationWorker` to securely request and store `device_id` and `device_jwt` upon first application launch. Integrated to fire automatically from `MainActivity.kt`.
- **Data Model Alignment:** Fixed `SosRequestDto` to accurately include the `status` field as required by the backend contract, overturning a previous assumption that it was device-side only.
- **Location Fix:** Used `FusedLocationProviderClient` with `CancellationTokenSource` in `HomeViewModel` for one-shot GPS fixes instead of hardcoded 0.0s.
- **TTL Enforced:** Modified `GatewaySyncWorker` to execute `sosRequestDao.deleteExpiredUploaded(cutoff)` for robust local database cleanup of 7+ day old records post-upload.

**Outcome:** App builds successfully (`assembleDebug`), installs successfully, and core Phase 2 functionality operates identically to specifications.

---

### 2026-08-13 16:58 IST — IMPLEMENT — Day 7: My SOS Status Screen Redesign
**Action:** Full redesign of `StatusScreen.kt` + `StatusViewModel.kt`  
**Status:** Complete. BUILD SUCCESSFUL.

**`StatusViewModel.kt`:**
- Added `formattedCreatedAt` — ISO8601 → "h:mm a" via `java.time` formatter
- Added `emergencyLabel` — apiValue → Title Case ("flood_rescue" → "Flood Rescue"), "unspecified" → "Emergency"
- All formatting in ViewModel; composable reads clean strings only

**`StatusScreen.kt`:**
- Dark gradient background matching HomeScreen (`#1A0000` → `#121212`)
- Custom top bar (no TopAppBar scaffold) — back icon left, ↻ refresh right
- Title: "My SOS" + subtitle "Alert sent from this device"
- SOS card: `#1E1E1E` rounded card with Emergency + Created rows
- Status pill with `AnimatedContent`: 🟡 Waiting to relay / 🔵 Relaying / 🟢 Uploaded
- Backend delivery section: spinner / confirmed green / error / idle prompt
- UUID low-prominence monospace footer

**No contract deviations.**

---

### 2026-08-13 17:21 IST — FIX — SOS Resume After Process Kill
**Action:** Status screen now survives app kill + cold restart  
**Status:** Complete. BUILD SUCCESSFUL.

**Problem:** App always cold-started on Home screen. SOS was in Room but navigation state was lost.

**Fix (3 files):**
- `DevicePreferences.kt` — added `saveLastSosUuid(uuid)` / `getLastSosUuid()` / `clearLastSosUuid()`
- `SosRepository.createSos()` — calls `saveLastSosUuid(uuid)` immediately after `insertSos()`
- `MainActivity.kt` — reads stored UUID on cold start; sets `startDestination = status/{uuid}` if present
- `AppNavigation.kt` — `onClearLastSos` callback called when citizen presses ← Back from Status → Home

**Behaviour:** Kill app → reopen → lands on Status screen for the same SOS. Press ← → clears UUID → next launch starts on Home.

---

### 2026-08-13 17:29 IST — IMPLEMENT — Day 8: Integration Round 1 Hardening
**Action:** 4 integration-readiness improvements for the whole-team checkpoint  
**Status:** Complete. BUILD SUCCESSFUL.

**1. Immediate sync after SOS creation:**
- `DataModule.kt` — added `enqueueImmediateGatewaySync(workManager)` function
- `SosRepository.createSos()` — calls it after `insertSos()` (CONNECTED constraint, KEEP policy)
- Citizen with internet sees upload fire in seconds, not up to 15 minutes

**2. Real gateway location:**
- `GatewaySyncWorker.getGatewayLocation()` — replaced 0.0/0.0 stub with `FusedLocationProviderClient.lastLocation` via `suspendCancellableCoroutine`
- Non-blocking cached read; falls back to 0.0/0.0 gracefully; upload never blocked

**3. Cleartext HTTP fix (emulator/dev):**
- `app/src/main/res/xml/network_security_config.xml` — created, permits cleartext to `10.0.2.2`, `localhost`
- `AndroidManifest.xml` — wired `android:networkSecurityConfig="@xml/network_security_config"`
- Fixes `CLEARTEXT communication not permitted` error that blocked `DeviceRegistrationWorker`

**4. Auto-poll on connectivity return:**
- `StatusViewModel.kt` — added `ConnectivityManager.NetworkCallback`, registered in `init`, unregistered in `onCleared()`
- Fires `checkBackendStatus()` automatically when device comes back online
- Status pill transitions 🟡→🟢 without citizen needing to tap ↻
- Guard added: skips overlapping concurrent backend checks

**Supporting changes:**
- `libs.versions.toml` — added `kotlinx-coroutines-play-services` (for `suspendCancellableCoroutine` in worker)
- `data/build.gradle.kts` — added dependency

**No contract deviations.**

### 2026-08-13 17:48 IST — FIX — Strict Input Validation
**Action:** Enforced strict character filtering for numeric/phone inputs  
**Status:** Complete.

**Problem:** `KeyboardType.Number` and `KeyboardType.Phone` only change the soft keyboard; they don't prevent users from pasting alphabets or entering invalid characters.
**Fix:**
- `HomeScreen.kt`: Added `.filter { it.isDigit() }` on the people count field and `.filter { it.isDigit() || it == '+' }` on the contact number field in their respective `onValueChange` lambdas.
- `OnboardingScreen.kt`: Added identical filters for the age and emergency contact number fields.
- Prevents invalid data from reaching the ViewModels and Room database.

---

### 2026-08-13 16:58 IST — IMPLEMENT — Phase 3: My SOS Status Screen
**Action:** Full redesign of `StatusScreen.kt` + minor update to `StatusViewModel.kt`  
**Status:** Complete. `assembleDebug` → BUILD SUCCESSFUL (8s).

**Files Modified:**

**`StatusViewModel.kt`:**
- Added `formattedCreatedAt: String` to `StatusUiState` — ISO8601 → "h:mm a" (e.g. "11:42 AM") via `java.time` formatter, computed in `observeLocalSos()`
- Added `emergencyLabel: String` to `StatusUiState` — apiValue → Title Case (e.g. "flood_rescue" → "Flood Rescue"), "unspecified" → "Emergency"
- All formatting logic confined to ViewModel; composable reads clean strings only

**`StatusScreen.kt`:**
- Full redesign with dark gradient background (`#1A0000` → `#121212`) matching HomeScreen
- No scaffold / no Material3 TopAppBar — custom Row-based top bar (back icon left, refresh icon right)
- **Title:** "My SOS" + subtitle "Alert sent from this device"
- **SOS info card:** `CardSurface (#1E1E1E)` rounded card with `Emergency:` + `Created:` info rows
- **Status pill:** Single large `AnimatedContent`-animated pill with emoji + label + background tint:
  - 🟡 `Waiting to relay` — amber (`#FFC107`) — `PENDING_LOCAL`
  - 🔵 `Relaying` — blue (`#2196F3`) — `IN_RELAY`
  - 🟢 `Uploaded` — green (`#43A047`) — `UPLOADED`
- **Pill subtitle:** context text changes per status
- **Backend delivery section:** spinner / green confirmed / error / idle prompt
- **UUID:** low-prominence monospace footer
- Backend endpoint wired: `GET /api/v1/sos/{uuid}/status` (no contract change — pre-existing)

**No contract deviations.**

### 2026-08-14 IST — IMPLEMENT — Day 9: Contract & Security Hardening
**Action:** Implemented critical contract, security, and sync fixes.
**Status:** Complete. App builds and successfully installed on Pixel_6a emulator.

**Fixes:**
1. **P0.4.1 Contract Violation Fixed:** Removed `status` from `SosRequestDto`. It is device-side only and must never be serialised to the backend.
2. **P0.4.2 Test Coverage:** Added `DtoSerializationTest` to prove `status` is structurally absent from network payloads.
3. **P0.4.4 TTL Enforcement:** Updated `GatewaySyncWorker` to enforce a 72-hour (not 7-day) TTL via `SosConstants.TTL_SECONDS`.
4. **P0.4.5 Sync Infinite Loop:** `GatewaySyncWorker` now explicitly enqueues `DeviceRegistrationWorker` on 401 before retrying to prevent an infinite loop.
5. **Security 7.1 Compliance:** Restricted `RetrofitClientFactory` logging to `HEADERS` (was `BODY`) to prevent medical data and device JWTs from leaking into logcat.
6. **Launch:** Successfully launched the application via ADB on the Pixel_6a AVD.

---

### 2026-08-14 IST — FIX PASS — Component C Architecture & Stability Fix Pass
**Action:** Resolved audit findings, concurrency races, network security, and lifecycle handling.
**Status:** Complete. BUILD SUCCESSFUL.

**Fixes Implemented:**
1. **Location Permission Race:** Re-architected `HomeScreen.kt` and `HomeViewModel.kt` to decouple SOS triggering from permission requests using a callback-based permission launcher (`triggerSosWithPermissionResult`). SOS is never created before permission status is known.
2. **Network Security Config Isolation:** Moved cleartext exception exclusively into `app/src/debug/res/xml/network_security_config.xml` and debug manifest; release build enforces strict HTTPS.
3. **Onboarding Skip Persistence:** Added `KEY_ONBOARDING_SKIPPED` in `DevicePreferences` and wired `OnboardingViewModel.skipOnboarding()` to prevent re-prompting on cold starts.
4. **Blood Type Validation:** Implemented regex validation for standard blood group patterns (`O+`, `A-`, etc.) in `OnboardingViewModel.kt`.
5. **Non-Blocking Startup:** Removed `runBlocking` from `MainActivity.kt` and replaced with async start destination resolution (`resolveStartDestinationAsync()`).
6. **Status Screen Backend Guard:** Injected `DevicePreferences` into `StatusViewModel.kt` to guard against redundant polling if the device is offline or unregistered.
7. **AuthInterceptor Precision:** Changed route matching from `.contains("/auth/device/register")` to `.endsWith("/auth/device/register")`.
8. **TTL Cleanup Policy:** Documented routing coordination `TODO(Integration with A+B)` in `SosRequestDao.kt`.

---

### 2026-08-14 IST — HANDOFF & VERIFICATION — A+B Relay Handoff & Test Suite Verification
**Action:** Produced Component A+B mesh relay handoff guide and verified full Android build suite.
**Status:** Complete.

**1. Handoff Document Authored:**
- Created `COMPONENT_C_TO_AB_RELAY_HANDOFF.md` containing 25 structured sections outlining boundaries, interfaces, Room persistence ownership, manifest exchange, TTL policy, failure modes, battery requirements, and forbidden changes.

**2. Test Coverage & Gradle Verification:**
- Configured local Android SDK (`platforms;android-35`, `build-tools;35.0.0`) and updated `local.properties`.
- Added unit & ViewModel tests:
  - `GatewaySyncWorkerTest.kt` in `:data` (covers success, 401 retry, 500 backoff, deduplication, and TTL cleanup).
  - `HomeViewModelTest.kt` in `:app` (covers permission granted/denied flows, fallback sentinel `0.0, 0.0`, and UI status).
  - `OnboardingViewModelTest.kt` in `:app` (covers skip persistence and blood type validation).
- Executed `./gradlew testDebugUnitTest` across all modules: **BUILD SUCCESSFUL**.
- Executed `./gradlew :app:assembleDebug`: **BUILD SUCCESSFUL** (`app-debug.apk` 20.6 MB produced).
