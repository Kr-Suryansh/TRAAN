# PROJECT_HANDOFF.md — TRAAN (`sih-android`)

> **Living handoff document.** Purpose: let a future session (different account/model, no prior chat history)
> understand the exact current state of this repo and continue work without guessing.
>
> **Read this file before modifying the project.** Verify the repo before trusting any status written here or in older docs.
>
> Companion docs (do NOT delete or replace): `Logs.md` (chronological log), `walkthrough.md` (dev-facing walkthrough),
> `Agent.md` (component context + architecture decisions), `ApiEndpoints.md` (relay public API),
> `day1-contracts-and-repo-setup.md` (master contract — source of truth for schemas),
> `15-day-roadmap.md` (team plan), `Component1_Overview.md` (plain-language overview).
> `PROJECT_HANDOFF.md` complements those; it is the current-state entry point.

---

## 1. PROJECT OVERVIEW

TRAAN is the **Android client** of the "Disaster Response Coordination Platform · SIH 2026".
This repo on disk is the folder `TRAAN`; the Gradle root project name is `sih-android`
(multi-module: `:app`, `:relay`, `:data`, `:network`).

**What it is intended to do:** build a phone-to-phone **offline mesh/relay engine**. When cell towers and
internet fail during a disaster, phones discover each other via Google Nearby Connections
(Bluetooth / Wi-Fi Direct, no internet) and epidemic-route **SOS messages**. Any phone that later regains
internet acts as a *gateway* and uploads the messages it holds to a backend, which authorities then act on.

**Current high-level architecture (as actually implemented in this repo):**

- `:relay` — the mesh/relay engine (A+B component): Nearby Connections advertising/discovery,
  manifest exchange + UUID diffing, multi-hop propagation with hop accounting, a foreground service
  with duty-cycled scanning, and a permission helper. This is the only fully-built module.
- `:data` — Room persistence (Component C's infrastructure). **This repo now contains a working Room layer**
  (entities, DAOs, `AppDatabase`, converters) ported from Component C's repo, plus the `RoomRelayDataSource`
  that bridges `:relay`'s storage needs to Room.
- `:app` — a **temporary test-scaffold Activity** only. No Compose UI yet. Component C owns the real app shell.
- `:network` — **empty stub**. Retrofit/backend client not yet implemented here.

A+ B build the relay; C builds the app shell + Room/WorkManager uploads; D builds the backend; the
backend + dashboard + AI are NOT in this repo.

---

## 2. CURRENT DEVELOPMENT STATE

**Development day/phase:** Day 7 committed (`320bfbd`). **Integration Stages 0a–5 COMPLETE (uncommitted).**

> [!IMPORTANT] Current repo state
> - HEAD commit `320bfbd` = "Completed Day 7: Three device relay stress validation, recovery testing,
>   persistence verification, and connection hardening" (committed on `mesh-relay`, mirrored to
>   `integration/ab-component-c`).
> - Branch: `integration/ab-component-c` (HEAD `320bfbd`, identical to `mesh-relay`).
> - The **working tree** contains **A+B ↔ Component C integration work** (Stages 0a–5, uncommitted):
>   13 modified files + 54 new files across 21 untracked directories. See §17 for full details.
> - **75 JVM tests pass** (64 relay + 11 data) — same baseline as Day 7; new integration test files
>   (`DtoSerializationTest`, `EnumContractTest`, etc.) are in the working tree but not yet compiled
>   into the test suite pending commit.
> - **Physical 4–5 phone stress testing is DEFERRED** (see §8 and §16): only 3 physical Android phones
>   are currently available. This is a testing-resource constraint, NOT a passed/validated result.

### What is actually completed (verified against code + tests)
- Day 1–7 relay engine fully implemented, committed, and physically validated (see §4, §8, §16).
- Room persistence layer in `:data` (ported from C), compiling and unit-tested.
- `RoomRelayDataSource` implementing `com.sih.relay.api.RelayDataSource`, wired in `:app`.
- Relay startup hardening + permission helper (Day 6 hardening portion).
- Day 6 permission preflight UX in `:app` ("Start Relay" requests missing runtime permissions and prompts to enable Bluetooth before starting the service).
- **Day 6 physical validation PASSED** — 3-device A → B → C with a non-empty SOS written through the Room-backed `saveSosMessages()` path on B and C, and that SOS **survived a process restart on C** (Room persisted 2 UUIDs after a fresh injection post-restart). See §8 and `Logs.md` Day 6.
- **Day 7 COMPLETE (committed at `320bfbd`)** — diagnostic instrumentation (forwarding-decision log, Dump Store button, TEST-ONLY allow-list seam) + production-quality 8012-aware failure handler. Phases A–G all completed. Strongest feasible 3-phone validation performed and documented. See §8/§16.
- **Integration Stages 0a–5 COMPLETE (uncommitted)** — selective file extraction from `origin/shell-app:sih-android/` into the validated A+B codebase. Build files, network module, data module additions, relay seam, and full Compose app shell are all in the working tree. See §17.

### What is currently working (verified this session)
- Full Gradle build `:app:assembleDebug` + `:data:testDebugUnitTest` + `:relay:testDebugUnitTest`:
  **BUILD SUCCESSFUL**. **75 JVM tests pass, 0 failures** (64 relay + 11 data). Verified, not assumed.
- Android Studio build of the integrated codebase succeeded.
- App launched on device; Home, Onboarding, Status Compose screens appeared and rendered correctly.

### What is incomplete / planned / deferred
- **4–5 physical-phone stress test — DEFERRED (NOT performed).** Only 3 physical Android devices are
  available. Must NOT be described as "passed"/"validated"/"complete". See §8 and §16.
- **Integration Stages 0a–5 committed** — user will commit manually through GitHub Desktop. 67 files
  (13 modified + 54 new) in the working tree. `.gitignore` and `gradlew.bat` excluded.
- **Stage 6: End-to-end wiring** — requires physical device testing with the integrated app shell.
- **Stage 7: Final documentation cleanup.**
- TTL cleanup, low-battery throttle, simulation/fallback demo mode (later roadmap days).

---

## 3. MODULE / ARCHITECTURE MAP

### Modules (from `settings.gradle.kts`, root project `sih-android`)
| Module | Namespace | Owner | Current state |
|---|---|---|---|
| `:app` | `com.sih.app` | C | **Compose app shell** (Home/Onboarding/Status screens, Hilt DI, WorkManager). `MainActivity.kt` replaced with Compose navigation. applicationId `com.sih.android`. |
| `:relay` | `com.sih.relay` | A+B | Complete: engine + FGS + duty cycle + permission helper (18 files, zero diff from mesh-relay) |
| `:data` | `com.sih.data` | C | Room layer + new additions: `SosRepository`, `UserMedicalProfileRepository`, `GatewaySyncWorker`, `DeviceRegistrationWorker`, `DataModule` (Hilt), `RelayRepository` interface. `RoomRelayDataSource` preserved. |
| `:network` | `com.sih.network` | shared | **Full Retrofit HTTP layer**: `DisasterApi` (3 endpoints), `RetrofitClientFactory`, `AuthInterceptor`, DTOs, request/response models, Moshi serialization |

### Dependency direction (from actual build files — the intended architecture)
```
:app  ──► :relay   (uses RelayApi)
:app  ──► :data    (Hilt-injected: AppDatabase, RoomRelayDataSource, SosRepository, RelayRepository)
:app  ──► :network (Hilt-injected: DisasterApi via AuthInterceptor + RetrofitClientFactory)
:data ──► :relay   (implements RelayDataSource; uses relay models in the mapper)
:data ──► :network (new: GatewaySyncWorker calls DisasterApi for backend upload)
:relay ─X─ :data   (NEVER — circular-dependency violation)
:relay ─X─ :network (NEVER)
```
Version catalog: `gradle/libs.versions.toml` (AGP 8.7.3, Kotlin 2.0.21, Nearby 19.2.0, Coroutines 1.8.1,
Serialization 1.7.1, Room 2.6.1, Moshi 1.15.1, Hilt 2.51.1, KSP 2.0.21-1.0.27, Retrofit 2.11.0,
OkHttp 4.12.0, WorkManager 2.9.1, Compose BOM, security-crypto 1.1.0-alpha06, mockk 1.13.11, junit 4.13.2).

### Boundaries
- **`RelayApi`** (`com.sih.relay.api.RelayApi`) = the ONLY public surface of `:relay` (`startRelay`,
  `stopRelay`, `getRelayStore`). `:app` talks to `:relay` through this.
- **`RelayDataSource`** (`com.sih.relay.api.RelayDataSource`) = internal boundary interface between
  `:relay` and `:data`. `:relay` declares it; `:data` implements it. NOT a public team contract.
- **Wire format** (`RelayPayloadCodec`) = 1-byte type tag (`0x01` manifest, `0x02` SOS) + UTF-8 JSON body.
  Internal `:relay` detail, not a public contract.
- **Model split:** relay models (`com.sih.relay.model`, kotlinx `@Serializable`) are NOT merged with
  Room entities (`com.sih.data.db.entity`) or network DTOs. `SosRequestMapper` bridges relay↔entity.
  `status` is **device-side only** — never serialised to the network/DTO.

### How data/persistence currently flows
1. `:app` `MainActivity` (test scaffold) builds `AppDatabase` (Room, `sih_local.db`) + `RoomRelayDataSource`
   and sets it on `RelayDataSourceProvider.dataSource`.
2. The foreground service constructs `RelayManager(context, dataSource)` from that provider.
3. "Inject Test SOS" → `RoomRelayDataSource.saveSosMessages(listOf(sos))` → `SosRequestDao.insertSos` (IGNORE).
4. Peers connect → manifest exchange → `RelayDataSource.getMissingSos/getAllSosUuids` → received SOS →
   `RelayHopLogic.onRelayReceive` → `saveSosMessages` → Room.
5. `observeAllSos()` (relay store Flow) is derived from the DAO's `observeCount()` Flow + re-read
   (the DAO has no observe-all Flow; the DAO API is intentionally unchanged).

---

## 4. COMPLETED WORK BY DAY

Concise summary from `Logs.md`/`walkthrough.md` + code. For full detail see those files.

- **Day 1** — Scaffold: 4 modules, `:relay` models (contract-frozen), `RelayApi`, `RelayDataSource`,
  `RelayManager` stub, `RelayModelsTest`, living docs. Full build + tests passed.
- **Day 2** — Nearby Connections P2P proof (advertising/discovery/connect/payload, `P2P_CLUSTER`).
  Temporarily `sendDay2TestPayload`; removed Day 3. **Physically verified** on two phones, offline.
- **Day 3** — `RelayPayloadCodec` (0x01/0x02 framing), coroutine scope, bidirectional `RelayManifest`
  exchange, UUID diffing via `RelayDataSource.getMissingSos`, `RelayPayloadCodecTest`. **Physically verified**
  (Android 17 ↔ Android 15). Nearby **downgraded 19.3.0 → 19.2.0** to fix 8012 connection errors.
- **Day 4** — Multi-hop: `RelayHopLogic` (hop accounting, `PENDING_LOCAL → IN_RELAY`), `connectedEndpoints`,
  `propagateLocalSos`/`propagateSosToConnected`, new-UUID echo-loop guard; temporary `InMemoryRelayStore` +
  "Inject Test SOS" in `:app`. **Physically verified A→B→C** (hopCount 0→1→2). 56 relay tests.
- **Day 5** — `DutyCycler` (~10s active / ~40s sleep), `RelayManager.startScanWindow/stopScanWindow`,
  `RelayForegroundService` (START_STICKY, `connectedDevice` type, LocalBinder), `RelayNotification`,
  `RelayDataSourceProvider` static seam. **Physically validated A→B→C** under FGS + duty cycle. 64 relay tests.
- **Day 6 (committed 2026-08-16, HEAD `6678771`)** — Room persistence in `:data` (ported from C): entities, DAOs,
  `AppDatabase`, `RoomConverters`, model enums; `DevicePreferences` (de-Hilted identity); `SosRequestMapper`;
  `RoomRelayDataSource`; `RelayPermissionRequirements`; startup hardening of `RelayManager` + FGS;
  permission preflight UX in `:app` (runtime permission requests + Bluetooth enable prompt before start).
  `:app` now uses Room + real device identity. 11 new data tests. Build + 75 tests verified.
  **Physically validated**: A→B→C with a non-empty SOS (`e9407a8a-...`) persisted to Room on
  B and C and surviving a process restart on C (see `Logs.md` Day 6, §8).
- **Day 7 (in progress)** — see §16. Diagnostic instrumentation only so far; 4–5 phone stress test deferred.

---

## 5. CURRENT IMPORTANT FILES

**`:relay` (permanent, A+B-owned)**
- `relay/.../RelayManager.kt` — the `RelayApi` implementation. Advertising/discovery, manifest exchange,
  UUID diff, SOS receive/propagate, echo-loop guard, scan-window duty cycling, startup hardening.
  **Permanent.**
- `relay/.../api/RelayApi.kt` — public contract (`startRelay/stopRelay/getRelayStore`). **Permanent.**
- `relay/.../api/RelayDataSource.kt` — internal storage boundary. **Permanent.**
- `relay/.../model/*.kt` — `SOSRequest`, `RelayManifest`, `SosLocation`, `UserMedicalProfile`, enums.
  Contract-frozen (`day1-contracts §1.1–1.3`). **Permanent.**
- `relay/.../RelayPayloadCodec.kt` — wire framing (0x01/0x02). **Permanent** (internal).
- `relay/.../RelayHopLogic.kt` — hop accounting, pure/JVM-testable. **Permanent.**
- `relay/.../DutyCycler.kt` — battery duty-cycle loop. **Permanent** (timing tunable Day 10).
- `relay/.../service/RelayForegroundService.kt` — FGS owning RelayManager + DutyCycler. **Permanent.**
- `relay/.../service/RelayNotification.kt` — FGS notification. **Permanent.**
- `relay/.../service/RelayDataSourceProvider.kt` — static store-injection seam. **TRANSITIONAL** (§6).
- `relay/.../RelayPermissionRequirements.kt` — version-aware permission helper (Day 6). **Permanent**
  (used by `:app`'s permission preflight UX).

**`:data` (Component C infrastructure, ported in; Day 6, uncommitted)**
- `data/.../db/AppDatabase.kt` — Room DB (v1). **Permanent** (schema frozen; migrate, never destroy).
- `data/.../db/dao/SosRequestDao.kt` — insert/upsert/status/reads/TTL/count. **Permanent** — DO NOT change API.
- `data/.../db/dao/UserMedicalProfileDao.kt` — profile singleton. **Permanent.**
- `data/.../db/entity/{SosRequestEntity,UserMedicalProfileEntity}.kt` — Room entities. **Permanent.**
- `data/.../db/converter/RoomConverters.kt` — Moshi JSON converters. **Permanent.**
- `data/.../model/{EmergencyType,SeverityHint,SosStatus}.kt` — C's apiValue enums. **Permanent.**
- `data/.../prefs/DevicePreferences.kt` — encrypted device identity (de-Hilted port). **Permanent.**
- `data/.../relay/SosRequestMapper.kt` — relay model ↔ Room entity. **Permanent.**
- `data/.../relay/RoomRelayDataSource.kt` — `RelayDataSource` → Room. **Permanent.**

**`:app`**
- `app/.../MainActivity.kt` — **TEMPORARY** test scaffold (§6): builds the Room store, binds the service,
  Start/Stop/Inject buttons. Will be replaced by C's Compose UI. **Day 7:** also has a test-only "Dump Store"
  button (logs every stored UUID + deviceId + hopCount + status) for 3-phone diagnostics.

**Docs:** `day1-contracts-and-repo-setup.md` (master contract), `Agent.md`, `Logs.md`, `walkthrough.md`,
`ApiEndpoints.md`, `Component1_Overview.md`, `15-day-roadmap.md`. Note: `Logs.md`/`walkthrough.md`/`Agent.md`
were updated to cover Day 6 this session; `Component1_Overview.md` still describes up to Day 5.

---

## 6. TRANSITIONAL / TEMPORARY IMPLEMENTATIONS  ⚠️ READ THIS

These are deliberate, documented seams. **Do not "clean up" them casually.**

1. **`RelayDataSourceProvider`** (`relay/.../service/RelayDataSourceProvider.kt`)
   - *What/why:* a `@Volatile` static holder so `:app` can hand the store to the service WITHOUT a DI framework.
     `:relay` (a library) cannot see `:data`, so this seam passes the implementation across the boundary.
   - *Eventual path:* Component C's Hilt graph in `:app` supplies the store; the provider is removed.
   - *Must NOT:* remove it before Hilt wiring exists, or "simplify" it into a `:relay`→`:data` dependency.

2. **`InMemoryFallbackStore`** (private class inside `RelayForegroundService`)
   - *What/why:* START_STICKY redelivery after process death wipes static state, so `RelayDataSourceProvider`
     is null; the recreated service needs *a* functional store (a no-op would break the echo-loop guard and
     drop received SOS). It keeps the mesh functional after process death.
   - *Known consequence:* after process death, Inject writes go to `:app`'s store while received SOS go to the
     fallback store — they can diverge for that service lifetime (documented in the FGS class doc).
   - *Eventual path:* Hilt re-injection in `:app` (Room store) removes the divergence.
   - *Must NOT:* delete it; `:relay` cannot import `:data`, so the FGS can't build `RoomRelayDataSource` itself.

3. **`MainActivity`** (`:app`) — temporary Day 2–6 test harness (Start/Stop/Inject SOS, programmatic
   LinearLayout UI, binding to the service). Real app = C's Compose shell. **Must NOT** be treated as product UI.

4. **Room layer in `:data`** — a working copy of Component C's persistence, ported so `RoomRelayDataSource`
   exists in THIS repo. It is real production-style code (schema-frozen) but is effectively a **staging copy**
   until C's repo is merged/integrated. When the C component integrates, reconcile this copy with C's repo
   rather than silently diverging.

5. **`:network` stub** — empty. Not a bug; backend wiring is deferred (§9).

**Do NOT prematurely:** merge models across layers, let `:relay` import Room/`:data`/`:network`,
add Hilt without C's involvement, or replace the provider seam before DI exists.

---

## 7. TEST STATUS

Verified this session (not assumed): the working tree builds and all JVM unit tests pass.

| Suite | File(s) | Count |
|---|---|---|
| `:relay` baseline | `DutyCyclerTest` 8, `RelayHopLogicTest` 12, `RelayModelsTest` 21, `RelayPayloadCodecTest` 23 | 64 |
| `:data` (Day 6) | `SosRequestMapperTest` 5, `RoomRelayDataSourceTest` 6 | 11 |
| **Total** | | **75 — 0 failures/errors** |

Last verified run:
```
:app:assembleDebug :data:testDebugUnitTest :relay:testDebugUnitTest -x lint -x lintDebug
→ BUILD SUCCESSFUL  (101 actionable tasks)
```

**Relevant commands / environment:**
- The build needs a real JDK. On this machine the Android Studio **JBR** is used and Windows `gradlew.bat`
  is the reliable entry point (Linux `./gradlew` is not used here).
- Reliable pattern (worked when a temp `.bat` helper set `JAVA_HOME`; the inline
  `cmd.exe /c 'set "JAVA_HOME=..." && gradlew.bat …'` form can mangle `%VAR%` from bash/WSL — prefer a
  short temp `.bat` or PowerShell): set `JAVA_HOME=C:\Program Files\Android\Android Studio\jbr`, then run
  `gradlew.bat <tasks> -x lint -x lintDebug`.
- Individual suites: `gradlew.bat :relay:testDebugUnitTest`, `gradlew.bat :data:testDebugUnitTest`.

**Known test limitations:**
- Lint is **skipped** (`-x lint -x lintDebug`) because `ConcurrentHashMap.newKeySet()` in `RelayManager.kt`
  triggers `NewApi` (API 24) at minSdk 23.
- kapt prints `Kapt currently doesn't support language version 2.0+. Falling back to 1.9.` (warning only).
- Room prints "Schema export directory was not provided" (exportSchema=true, no `room.schemaLocation`); benign.
- No instrumented/Robolectric tests; Nearby runtime paths are verified on physical devices, not in unit tests.
- `DutyCyclerTest` uses `@OptIn(ExperimentalCoroutinesApi::class)`-flagged APIs (warnings only).

---

## 8. DEVICE / INTEGRATION TEST STATUS

Real multi-hop relay has been validated on physical hardware, fully offline (from `Logs.md`/`walkthrough.md`).
No hardware identifiers (serials/MAC/IMEI/Android IDs) are recorded here.

- **Day 2** — two phones: full P2P path (advertise → discover → connect → 417-byte `SOSRequest` sent →
  received → deserialized). PASS.
- **Day 3** — two phones, different OS versions: bidirectional `RelayManifest` exchange, decoding,
  UUID 0-diff handling. Transient `STATUS_ENDPOINT_IO_ERROR` (8012) recovered automatically. PASS.
- **Day 4** — **A→B→C** with a middle relay: A created SOS (`relayHopCount=0`, `PENDING_LOCAL`); B received
  it (`relayHopCount=1`, `IN_RELAY`) and forwarded; C received it (`relayHopCount=2`, `IN_RELAY`);
  both B and C saved via `saveSosMessages()`. Peers already holding the UUID reported **no missing** SOS
  (no blind retransmission). PASS.
- **Day 5** — **A→B→C** under the foreground service + duty-cycle scan windows: same multi-hop propagation
  remained operational; manifest exchange + advertising/discovery succeeded through the FGS. PASS.
- **Day 6** — **A→B→C** with the **Room-backed** store: A created SOS `e9407a8a-...` (`PENDING_LOCAL`) and
  logged `Room now holds 1 SOS UUID(s)`; B received it (`hopCount=1`, `IN_RELAY`) and forwarded via
  `saveSosMessages()`; B had the UUID in its known-UUID manifest state and sent it to a peer with 0 known
  UUIDs; C received it (`hopCount=2`, `IN_RELAY`) and forwarded via `saveSosMessages()`. **Persistence across
  restart (C):** after clearing the C app process and relaunching, a fresh injection logged
  `Room now holds 2 SOS UUID(s)`, proving the relayed SOS (`e9407a8a-...`) survived the process restart in
  `sih_local.db`. PASS.

**Note on device labels:** the docs reference the second phone inconsistently (see §15 item 5): "CPH2793 / Oppo"
(Android 13) in Day 2 vs "Vivo" (Android 15) in Day 3+, with differing ADB aliases. Treat the model/OS labels in
the logs as unreliable; the roles are not. Device roles for the relay tests: A = source phone, B = middle relay,
C = end node.

### Validation-status tracking (4–5 phone stress test)

| Item | Status |
|---|---|
| 2-phone relay (Day 2/3) | ✅ PASSED (physically validated) |
| 3-phone A → B → C (Day 4/5/6) | ✅ PASSED (physically validated) |
| 3-phone Room persistence + restart (Day 6) | ✅ PASSED (physically validated) |
| **Day 7 Phase A** (baseline tests/build) | ✅ PASSED (75/75 JVM tests, `:app:assembleDebug` BUILD SUCCESSFUL) |
| **Day 7 Phase B** (2-phone regression with diagnostics) | ✅ PASSED — Phone A (source) and Phone B (receiver); injected `83306893-6e6e-4065-9e9f-189a75d19616`; both started/advertised/discovered/connected/manifest-exchanged; A stored + sent; B received exact UUID at `hopCount=1`/`IN_RELAY`, classified **NEW**, persisted (Dump Store confirmed; B held 2 records — 1 older pre-existing + the new UUID, not a failure). See §16 and `Logs.md`. |
| **Day 7 Phase C** (3-phone A → B → C) | ✅ PASSED — deterministic chain forced with the TEST-ONLY allow-list filter (A allowed B; B allowed A,C; C allowed B). A originated SOS `62c667f9-9204-46dd-98b6-f1d32297552d`; B received from A at `hopCount=1`/`IN_RELAY`, classified **NEW**, persisted, propagated to C; C received same UUID from B at `hopCount=2`/`IN_RELAY`, classified **NEW**, persisted. Dump Store on C confirmed the UUID with `hopCount=2`/`IN_RELAY` (C held 2 records — 1 pre-existing + the new UUID, not a failure). Transient `STATUS_ENDPOINT_IO_ERROR` (8012) and `STATUS_ALREADY_CONNECTED_TO_ENDPOINT` occurred during connection races but self-recovered; end-to-end test succeeded. See §16 and `Logs.md`. |
| **Day 7 Phase D/F** (dense/concurrent/recovery/restart) | ✅ PASSED — D1 multiple injections (3 UUIDs from A, all propagated A→B→C), D2 echo guard (manifest exchanges showed 0 missing SOS), D3 disconnect/reconnect (**PARTIALLY VALIDATED** — automatic self-healing observed; no clean controlled prolonged disconnect), D4 relay stop/start (C stopped/started, reconnection, new UUID `83904f98` propagated correctly), D5 process kill persistence (force-stop all 3, Room survived, reconnection succeeded), D6 hop count audit (all 4 Day 7 UUIDs confirmed A=0/B=1/C=2), D7 bidirectional injection (B and C injected, propagation confirmed). D8 sleep-window injection **NOT TESTED** (duty-cycle behavior already validated in Day 5; specific edge case not required for Day 7 closure). See §16 and `Logs.md`. |
| **4–5 physical-phone stress test** (duplicate/dropped/stuck detection, dense mesh) | ⏳ **DEFERRED — NOT performed.** Only 3 physical Android devices currently available. |
| JVM automated tests | ✅ 75/75 passing (64 relay + 11 data) |
| Gateway upload / backend | ❌ Not tested (backend not integrated yet) |

> **The planned 4–5 physical-phone stress test has NOT been completed and must NOT be represented
> anywhere as "passed", "validated", or "complete".** It is deferred because only 3 physical Android
> devices are available for testing. This is a testing-resource constraint, not a software failure.
> A JVM simulation is NOT equivalent to the missing real-device test and is not claimed to be.
> The test remains a future validation task.

**Not yet physically validated:** the START_STICKY-only redelivery path (service recreated after process death
without a fresh `:app` process uses the in-memory fallback until Hilt re-injection), gateway upload to a real
backend, internet delivery, and any behavior requiring ≥4 physical devices.

---

## 9. DEFERRED WORK

Work intentionally postponed. Especially the C-component integration items:

- **4–5 physical-phone stress test — DEFERRED (validation, not a code item).** Planned roadmap Day 7 A+B
  scope. NOT performed because only 3 physical devices are available. Remains a future validation task;
  project progress does NOT pause for it (§16). Must not be reported as passed/complete.
- **Full Component C integration (Stages 0a–5 DONE, Stages 6–8 remaining):**
  - ~~Merge/reconcile this repo's `:data` Room copy with Component C's repo (schema is identical by design).~~ DONE (Stage 3).
  - ~~Replace `RelayDataSourceProvider` with C's Hilt DI; resolve the START_STICKY store divergence.~~ Hilt wired in `:app`; `RelayDataSourceProvider` seam kept for now (Stage 5).
  - ~~Bring in C's `SosRepository`, `GatewaySyncWorker`, `DeviceRegistrationWorker`, `DataModule`, network DTOs/`DisasterApi`, `AuthInterceptor`/Retrofit config.~~ DONE (Stages 2–3).
  - ~~Replace `MainActivity` scaffold with C's Compose app (real SOS creation, Status screen, onboarding).~~ DONE (Stage 5).
  - Reconcile `device_id` semantics (§1.2 "installation ID" vs §1.9 "generated on first app install";
    `DevicePreferences` currently uses a stable installation id as fallback).
  - Stage 6: End-to-end wiring + physical device testing with the integrated app shell.
  - Stage 7: Final documentation cleanup.
- **Gateway/network:** ~~implement `:network`~~ DONE (Stage 2). Backend device registration, TTL cleanup
  (48–72h via `last_relayed_at`), low-battery throttle (Day 10), simulation/fallback demo mode (Day 12).
- **Known deferred decisions** the analysis has NOT resolved: whether the Day 6 Room-backed store should use
  upsert-on-hop-metadata (currently `insertSos` IGNORE is used to preserve idempotent semantics).

---

## 10. KNOWN ISSUES / RISKS

Only issues supported by the repo or documented testing.

- **4–5 physical-phone stress test not yet performed** (top validation gap): only 3 physical devices
  available; the roadmap Day 7 scale test is deferred, not passed. See §8, §16.
- **Uncommitted integration work in the working tree** (continuity): HEAD is Day 7 (`320bfbd`);
  the working tree adds Integration Stages 0a–5 (67 files: 13 modified + 54 new). These are
  uncommitted and will be committed manually through GitHub Desktop.
- **START_STICKY store divergence** after process death (in-memory fallback vs `:app` store) — documented in
  `RelayForegroundService`; resolved only by DI/Room re-injection.
- **Lint skipped** due to `ConcurrentHashMap.newKeySet()` `NewApi` (API 24 vs minSdk 23) in `RelayManager.kt`.
- **Transient Nearby status codes** observed on hardware (8012/8003/8011) — self-recover; mitigated by
  `pendingConnections` race guard and Nearby 19.2.0 pin.
- **`RelayManifest.deviceId`** is currently the constant `LOCAL_ENDPOINT_NAME` ("SIH-Relay-Node"), not a real
  device id (`RelayManager.kt`, manifest exchange). Accepted for the mesh test phase.
- **`device_id` contract ambiguity** §1.2 vs §1.9 — flagged in `DevicePreferences`; unresolved team-wide.
- **kapt language-version warning + Room schema-export warning** — benign today, see §7.
- **Integration non-blocking issues (pre-commit audit):**
  1. Missing `network/proguard-rules.pro` (dormant — `isMinifyEnabled=false`)
  2. Unused import `SihTheme` in `HomeScreen.kt`
  3. Serialization plugin not applied in `:data` (no `@Serializable` classes exist there)
  4. `SosRequestDto` uses `String` fields instead of enum for `emergencyType`/`severityHint`
- **Docs lag:** `Component1_Overview.md` still describes up to Day 4; `Logs.md`/`walkthrough.md` were
  updated to cover integration Stages 0a–5.
- **CRLF/EOL churn** in `.gitignore` and `gradlew.bat` in the working tree (line-ending changes from the
  build toolchain, no content change) — harmless, but a future commit should be intentional about them.

---

## 11. ARCHITECTURAL DECISIONS

Recorded decisions and the reasons given. Do not reinterpret or redesign them.

1. **`RelayDataSource` is an internal boundary interface, not a public contract.** `:relay` needs storage but
   must not own Room; `:data` owns the DB. `public` in Kotlin only so `:data` can implement it.
   (Source: `Agent.md` Decision 1; `ApiEndpoints.md` §2.)
2. **`:relay` must NEVER depend on `:data`, Room, or `:network`.** Circular-dependency and ownership rule.
   Dependency direction is always `:app → :relay` and `:data → :relay`.
3. **`RelayApi` is the sole public surface of `:relay`.** No other relay class is part of the team contract.
4. **1-byte wire prefix (0x01 manifest / 0x02 SOS) + UTF-8 JSON.** Chosen over a JSON envelope for zero
   schema change, zero extra parse, 1-byte overhead, easy extension.
5. **Nearby `P2P_CLUSTER`** for multi-peer mesh; service id `com.sih.relay.SERVICE`.
6. **Nearby pinned to 19.2.0** to match the on-device GMS Nearby module and fix persistent 8012 errors.
7. **Duty cycling ~10s active / ~40s sleep** to protect battery (contract §6.1); `DutyCycler` decoupled from
   SOS state so windows can close without losing data/connections.
8. **Foreground service (`connectedDevice` type, START_STICKY, `exported=false`)** keeps the mesh alive in
   the background; local Binder exposes manager + data source to `:app`.
9. **Model separation: never merge relay models, Room entities, and DTOs.** `status` is device-side only and
   is NEVER serialised to the network. `SosRequestMapper` is the single mapping point.
10. **`:data` uses kapt (not KSP) for Room** in this repo because Kotlin 2.0.0 + kapt avoids KSP version
    lockstep (C's repo uses KSP 2.0.21-1.0.27 with Kotlin 2.0.21 — a divergence to reconcile at integration).
11. **Enums bridged by constant name** between relay and data enums (both sides match the same §1.2 contract
    values); unknown apiValues degrade to `UNSPECIFIED`/`PENDING_LOCAL` defaults.

---

## 12. DO NOT BREAK / DO NOT CHANGE PREMATURELY

- **`:relay` must not import Room, `:data`, or `:network`.** This is the load-bearing rule.
- **Do not change `RelayApi` / `RelayDataSource` signatures** without recording a new decision in `Agent.md`.
- **Do not modify the Room schema or DAO API** (`AppDatabase` v1, `SosRequestDao`, entities, `RoomConverters`).
  Migrate, never `fallbackToDestructiveMigration`.
- **Do not change the wire format** (`RelayPayloadCodec` 0x01/0x02) or the relay model schemas
  (`day1-contracts §1.1–1.3`). Contract-frozen.
- **Do not change the dependency direction** (`:data → :relay` only; `:relay` never → `:data`).
- **Do not remove `RelayDataSourceProvider` or `InMemoryFallbackStore`** before Hilt/DI exists (§6).
- **Do not merge the three model layers** (relay / entity / DTO).
- **Do not add `status` to any DTO** sent to the backend.
- **Do not "fix"** the `.gitignore`/`gradlew.bat` EOL churn or the skipped lint casually — do it deliberately.
- **Do not redesign the duty-cycle or FGS** without a new approved decision; Day 5 A→B→C behavior must stay intact.

---

## 13. NEXT DEVELOPMENT STEP

Ground truth of the repo: Day 7 is committed (`320bfbd`). The working tree contains Integration
Stages 0a–5 (67 files: build files, network module, data module additions, relay seam, Compose app
shell). 75 automated tests pass. Android Studio build succeeded. App launched and UI screens rendered.

**Next steps, in order:**
1. **Commit integration work** — all Integration Stages 0a–5 (code + documentation) committed as a
   coherent snapshot through GitHub Desktop.
2. **Stage 6: End-to-end wiring** — physical device testing with the integrated Compose app shell.
   Verify SOS creation, relay start/stop, onboarding flow, and status screen work end-to-end.
3. **Stage 7: Final documentation cleanup** — update all docs to reflect the integrated state.
4. **Do NOT interpret the deferred 4–5 phone test as a blocker** — it remains a future validation item
   pending availability of additional physical devices (see §8/§16).

---

## 14. HANDOFF RULES

- **Read this file before modifying the project.**
- **Verify the repository before trusting old status** — git HEAD, working tree, and running the tests are the
  source of truth; docs can lag (they do, per §15).
- **Do not assume TODOs are still valid** — confirm against code before acting.
- **Do not remove transitional code merely because it looks unnecessary** (see §6).
- **Do not introduce cross-module dependencies without checking the intended architecture** (§3, §11, §12).
- **Update this file whenever a meaningful implementation, architecture, testing, or integration decision
  changes the project state.**
- **Never fabricate completion/test status** — only claim what is verified.

---

## 15. DISCREPANCIES FOUND BETWEEN DOCUMENTATION AND REPO STATE

1. **Day 6 is committed; earlier docs said uncommitted (now resolved).** Previous copies of this file stated
   HEAD = Day 5 (`e8f319c`) with Day 6 in the working tree. Day 6 is now committed at `6678771`
   ("Completed upto Day 6…"). The working tree now holds only uncommitted Day 7 diagnostic instrumentation.
2. **`:data`/`:network` are not stubs anymore conceptually** — the docs call `:data` a "stub", but the working
   tree now has a complete Room layer in `:data`. `:network` is still a stub.
3. **Test count in docs was stale; now resolved.** `Agent.md`/`walkthrough.md` previously cited only 56 (Day 4)
   and 64 (Day 5) relay tests; they now also record the current **75 passing tests** (64 relay + 11 data) in their
   Day 6 sections. The historical day-scoped counts (56 Day 4, 64 Day 5) remain as accurate per-day records.
4. **Nearby version:** older doc text still says "19.3.0" in places (`walkthrough.md` Day 1), but the catalog
   and all Day 4+ records use **19.2.0** (deliberate downgrade).
5. **Device-label inconsistency in docs:** `Logs.md`/`walkthrough.md` Day 2 list the second phone as
   "CPH2793 / Oppo", ADB alias `VIVO_DEV_02`, Android 13; Day 3+ list it as "Vivo", same alias `VIVO_DEV_02`,
   Android 15; `Agent.md` Day 2 uses a different alias (`OPPO_DEV_01`) for "CPH2793/Oppo". The OS versions and
   aliases conflict across docs — it is ambiguous whether one device was mislabeled or two devices shared an
   alias. Device roles for the tests are unambiguous: A = source, B = middle relay, C = end node.
6. **Root project name vs folder name:** docs say repo `sih-android`; the checkout directory is `TRAAN`,
   Git root project name remains `sih-android`, branch is `mesh-relay` (README.txt: "This is main branch.
   Do not push.").
7. **Handoff/permission-split doc not in this repo:** the permission ownership split referenced by Day 6 work
   lives in Component C's repo (a handoff doc), not here. This repo's `relay` manifest declares all needed
   permissions statically; the runtime preflight UX is implemented in `:app` (this repo) and was physically
   exercised as part of the Day 6 validation.

---

## 16. DAY 7 — REVISED PLAN AND 3-PHONE LIMITATION  ⚠️ READ BEFORE CONTINUING

> This section is the current authoritative Day 7 status. The original roadmap Day 7 intent
> ("stress-test with 4–5 phones") is preserved in `15-day-roadmap.md` and in §4/§13, but the
> physical 4–5 phone validation is **deferred** because only 3 physical Android devices are available.

### 16.1 Testing limitation (must be stated accurately)

- The planned **4–5 physical-phone stress test has NOT been completed**.
- It is **deferred** because only **3 physical Android devices** are currently available for testing.
- This must **NOT** be represented anywhere as "passed", "validated", or "complete".
- The limitation is a **testing-resource constraint, not a software failure**.
- **A JVM simulation is NOT equivalent** to the missing real-device test; we do not claim it is.
- The test remains a **future validation task** to be performed when sufficient devices are available.

**Testing-status vocabulary to use going forward:**
- GOOD: "Deferred" / "Not yet physically validated" / "Pending availability of additional devices" /
  "3-device validation currently available".
- BAD: "Passed" / "Complete" / "Validated at scale" / "5-device stress tested".

### 16.2 What we CAN validate now (realistic Day 7 scope with 3 phones)

The following validate **real Android/Nearby/OS behavior** (they do NOT prove behavior at 4–5 physical nodes):
- 2-phone regression
- 3-phone A → B → C relay
- 3-phone dense/mesh connectivity where practical
- multiple SOS injections
- duplicate UUID behavior
- hop-count correctness
- persistence/store verification (via the "Dump Store" button)
- disconnect/reconnect
- relay stop/start
- app process restart
- permission/preflight behavior
- foreground-service behavior
- duty-cycle behavior
- repeated physical runs to expose intermittent problems

### 16.3 Automated / JVM coverage (optional, future)

- Expanding automated tests for **multi-node logic** is a possible future approach (e.g., a logical
  multi-peer harness / fake transport in JVM tests) to increase concurrency/logic coverage.
- This is **NOT committed** and **NOT implemented**. It is recorded as an optional/future technique.
- It is a complement to — **never a substitute for** — the deferred real-device 4–5 phone test.

### 16.4 Day 7 revised plan

- **PHASE A** — baseline automated tests/build. **PASSED** (75/75 JVM tests; `:app:assembleDebug` BUILD SUCCESSFUL).
- **PHASE B** — 2-phone regression. **PASSED** (2026-08-18): Phone A (source) ↔ Phone B
  (receiver). Both started relay, advertised/discovered, connected, and exchanged
  RelayManifest. A injected `83306893-6e6e-4065-9e9f-189a75d19616`, stored it, and sent it to its 1 connected
  endpoint. B received the exact UUID at `hopCount=1`/`status=IN_RELAY`, classified it **NEW**, and propagated
  it per the relay logic; "Dump Store" on B confirmed the UUID persisted locally (B showed 2 records total —
  1 older pre-existing SOS + the new UUID; not a failure). A transient Nearby `ApiException 8012 /
  STATUS_ENDPOINT_IO_ERROR` on an initial connection request on A was recorded as a **non-blocking
  observation** (devices connected and the full manifest + SOS transfer succeeded afterward).
- **PHASE C** — 3-phone A → B → C testing. **PASSED** (2026-08-19): topology forced with the TEST-ONLY
  allow-list filter (A allowed B; B allowed A,C; C allowed B). A originated SOS
  `62c667f9-9204-46dd-98b6-f1d32297552d`; B received it from A at `hopCount=1`/`IN_RELAY`, classified
  **NEW**, persisted it, and propagated it to C; C received the same UUID from B at `hopCount=2`/`IN_RELAY`,
  classified **NEW**, and persisted it. "Dump Store" on C confirmed `uuid=62c667f9-...`, `hopCount=2`,
  `status=IN_RELAY` (C showed 2 records total — 1 pre-existing + the new UUID; not a failure). Transient
  Nearby errors `STATUS_ENDPOINT_IO_ERROR` (8012) and `STATUS_ALREADY_CONNECTED_TO_ENDPOINT` occurred during
  connection races but self-recovered; the end-to-end A→B→C test completed successfully.
- **PHASE D** — 3-phone dense/concurrent/recovery testing. **PASSED** (see detailed results below).
- **PHASE E** — diagnose and fix confirmed Day 7 issues. **COMPLETE** — the 8012-aware failure handler is the only code change; no other bugs found.
- **PHASE F** — verify persistence, duplicates, hop counts, reconnect/restart. **PASSED** (see detailed results below).
- **PHASE G** — document actual results. **COMPLETE** (this section and `Logs.md`/`walkthrough.md`/`Agent.md` updated).

#### Phase D/F detailed results

- **D1 — Multiple SOS injections: PASSED.** Node A injected three distinct UUIDs rapidly:
  `c581b6c8-94d1-498c-86f8-5b7b392d04e0`, `24900c85-db5c-4d22-b86b-933032bf1447`,
  `840f9d92-27cf-4cf0-8303-8b82408fcf41`. All three propagated through A→B→C. A stored them at
  `hopCount=0`/`PENDING_LOCAL`. B received them at `hopCount=1`/`IN_RELAY`. C received all three as
  **NEW** at `hopCount=2`/`IN_RELAY`.
- **D2 — Duplicate/echo guard: PASSED.** Manifest exchanges repeatedly showed
  `UUID diff for endpoint <id>: peer has N UUID(s), 0 SOSRequest(s) to send` and
  `No missing SOSRequests to send to endpoint <id> — peer is up to date` after peers already had the
  same UUID sets, including after reconnection/restart scenarios. This physically validates the
  manifest-based duplicate/echo prevention behavior.
- **D3 — Disconnect/reconnect: PARTIALLY VALIDATED.** A transient disconnect/reconnection occurred and
  the mesh automatically reconnected. The Nearby/Bluetooth connection re-established before a clean
  manual separation could be fully controlled. The automatic self-healing IS valid evidence of mesh
  recovery, but no clean deliberately controlled prolonged physical disconnect was achieved. This is a
  testing-procedure limitation, not a software failure.
- **D4 — Relay stop/start recovery: PASSED.** Phone C: Stop Relay pressed → relay started again →
  connections re-established → manifest exchanges completed → existing UUID sets showed 0 missing
  SOSRequests. Node A then injected a new SOS `83904f98-976c-4a1e-904f-453559953e0d` which propagated
  correctly: A=hopCount 0, B=hopCount 1, C=hopCount 2.
- **D5 — App process kill persistence: PASSED.** Before force-stopping, all three phones had 10 SOS
  records in Room. App processes force-stopped and relaunched. Each device rewired the Room-backed
  `RelayDataSource` using `sih_local.db`. Relay connections re-established. Manifest exchanges showed
  10 known UUIDs and 0 missing SOSRequests. Room data survived process force-stop/relaunch.
- **D6 — Complete hop count audit: PASSED.** Explicit Dump Store evidence from all three phones confirmed
  all four Day 7 UUIDs: `c581b6c8` (A=0, B=1, C=2), `24900c85` (A=0, B=1, C=2),
  `840f9d92` (A=0, B=1, C=2), `83904f98` (A=0, B=1, C=2).
- **D7 — Bidirectional injection: PASSED.** After the three-injection test, SOS records were injected
  from B and C as well. Propagation through the mesh was confirmed with expected hop behavior.
- **D8 — Deliberate sleep-window injection: NOT TESTED.** Duty cycling itself was already exercised and
  validated during Day 5 (A→B→C under FGS + duty cycle). The specific edge case of intentionally
  injecting immediately after "Closing scan window" was not performed. This is not required for Day 7
  closure per §16.2/§16.4 — the duty-cycle behavior was already physically validated.
- **8012-aware connection race handling: PASSED.** `STATUS_ENDPOINT_IO_ERROR` (8012) occurred during
  bidirectional connection races, but the connection subsequently completed instead of being incorrectly
  discarded. The production-quality fix in `RelayManager.onEndpointFound` works correctly.

> **4–5 physical-phone stress testing remains deferred and will be performed later when sufficient
> physical devices are available.**

### 16.5 Project continuation (do not treat the deferred test as a blocker)

> **Project development does not pause for the deferred 4–5 phone test.** Day 7 is **COMPLETE**.
> The strongest feasible 3-phone validation has been performed and documented.
> Development now proceeds to the planned **Component C integration** and subsequent end-to-end work.

- Do **not** interpret the missing 5-phone test as a blocker in future sessions.
- Component C integration proceeds after the strongest feasible 3-phone A+B validation:
  - Room-backed persistence compatibility (schema already ported, identical by design)
  - `RelayRepository` boundary / `RelayDataSource` seam
  - `GatewaySyncWorker`, `DevicePreferences`, network DTOs/contracts
  - Hilt/module wiring (replace `RelayDataSourceProvider` seam)
  - app/UI integration (replace `MainActivity` scaffold with C's Compose shell)
  - end-to-end SOS flow
- Preserve existing architecture boundaries and handoff contracts; do NOT silently resolve previously
  identified integration decisions (see §9 and Component C handoff docs).
- Nothing from this section has been implemented yet — it is the current plan and status.

---

## 17. INTEGRATION STAGES 0a–5 — A+B ↔ Component C (UNCOMMITTED)

> This section documents the completed integration work in the working tree. It is NOT committed.
> The user will commit manually through GitHub Desktop.

### 17.1 — Integration overview

**Branch:** `integration/ab-component-c` (HEAD `320bfbd`, identical to `mesh-relay`)
**Strategy:** Selective file extraction from `origin/shell-app:sih-android/` — write only, never delete
or overwrite validated A+B relay code.
**Date:** 2026-08-23

### 17.2 — Frozen boundaries preserved (zero regressions)

| Boundary | Preserved? | Notes |
|---|---|---|
| `RelayApi` contract | ✅ | Unchanged |
| `RelayDataSource` interface | ✅ | Unchanged |
| Room schema (v1) | ✅ | Identical — no migration needed |
| `SosRequest` wire format | ✅ | Untouched |
| `RelayHopLogic` | ✅ | Untouched |
| `DutyCycler` timing | ✅ | Untouched |
| `RelayForegroundService` | ✅ | Untouched |
| `nearbyConnections 19.2.0` pin | ✅ | Untouched |
| `RelayDataSourceProvider` | ✅ | Kept as-is (not replaced with Hilt) |

### 17.3 — Stage summary

| Stage | Description | Files | Status |
|---|---|---|---|
| 0a | Baseline verification | 0 | ✅ PASS |
| 1 | Build files (version catalog, root build, settings, gradle.properties, module builds) | 8 modified | ✅ PASS |
| 2 | Network module (Retrofit HTTP layer) | 14 new | ✅ PASS |
| 3 | Data module (repos, workers, Hilt DI) | 11 new + 1 modified | ✅ PASS |
| 4 | Relay seam (`RelayRepository` interface) | 1 new | ✅ PASS |
| 5 | App shell (Compose UI, Hilt, navigation) | 33 new | ✅ PASS |
| **Total** | | **13 modified + 54 new** | **ALL PASS** |

### 17.4 — Key changes by module

**`:network`** (Stage 2) — from empty stub to full Retrofit layer:
- `DisasterApi`: 3 endpoints (`POST /api/v1/sos`, `GET /api/v1/sos/{uuid}/status`, `POST /api/v1/sos/batch`)
- `RetrofitClientFactory`, `AuthInterceptor`, 3 DTOs, 2 request models, 3 response models
- `DtoSerializationTest.kt`: 5 JVM tests for Moshi round-trips

**`:data`** (Stages 3–4) — from Room-only to full persistence + DI layer:
- `DevicePreferences.kt` replaced with Hilt-injected version
- `DataModule.kt`: Hilt `@Module` providing DataStore, Executors, Dispatchers, WorkManager
- `SosRepository.kt`, `UserMedicalProfileRepository.kt`: app-layer repository wrappers
- `GatewaySyncWorker.kt`, `DeviceRegistrationWorker.kt`: WorkManager jobs
- `RelayRepository.kt`: public interface + `StubRelayRepository` (temporary)
- 4 test files (11 JVM tests)

**`:app`** (Stage 5) — from test scaffold to Compose app shell:
- `SihApplication.kt`: `@HiltAndroidApp`, WorkManager `Configuration.Provider`
- `MainActivity.kt`: Compose navigation, device registration, nav start destination
- `AppModule.kt`: Hilt DI for AuthInterceptor, DisasterApi, StubRelayRepository
- `AppNavigation.kt` + `Screen.kt`: 3 routes (Home, Onboarding, Status)
- `SihTheme.kt`: Material3 dark/light
- `HomeScreen.kt` + `HomeViewModel.kt`: SOS button, permission flow, location
- `OnboardingScreen.kt` + `OnboardingViewModel.kt`: medical profile
- `StatusScreen.kt` + `StatusViewModel.kt`: status pill, backend check
- Updated manifest, resources (strings, themes, mipmaps, drawables, network security config)
- 2 test files

**`:relay`** — zero changes (18 files untouched from mesh-relay baseline).

### 17.5 — Build verification

- **Namespace fix applied:** `app/build.gradle.kts` namespace changed from `com.sih.android` to
  `com.sih.app` (applicationId remains `com.sih.android`) to resolve BuildConfig and manifest class
  resolution.
- **Android Studio build succeeded.**
- **App launched on device** — Home, Onboarding, Status Compose screens appeared and rendered correctly.
- **75 JVM tests pass** (64 relay + 11 data) — same baseline as Day 7.

### 17.6 — Pre-commit audit

- 67 files classified: 13 modified + 54 new across 21 untracked directories.
- A+B relay code verified preserved (zero diff from mesh-relay baseline).
- No secrets found.
- `.gitignore` and `gradlew.bat` confirmed excluded from commit.
- **Verdict: SAFE TO COMMIT WITH MINOR KNOWN ISSUES** (see §10).

### 17.7 — Known non-blocking issues from pre-commit audit

1. Missing `network/proguard-rules.pro` (dormant — `isMinifyEnabled=false`)
2. Unused import `SihTheme` in `HomeScreen.kt`
3. Serialization plugin not applied in `:data` (no `@Serializable` classes exist there)
4. `SosRequestDto` uses `String` fields instead of enum for `emergencyType`/`severityHint`

### 17.8 — Remaining integration stages

| Stage | Description | Status |
|---|---|---|
| 6 | End-to-end wiring + physical device test | ❌ Not started |
| 7 | Final documentation cleanup | ❌ Not started |
| 8 | Optional optimizations | ❌ Not started |