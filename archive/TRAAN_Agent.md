# Agent.md — Component 1: Mesh/Relay Engine (:relay)

> This file is a **living document**. Update it whenever meaningful implementation changes are made.
> Do not let it drift from the actual state of the code.

---

## Component Identity

| Field | Value |
|---|---|
| **Module** | `:relay` |
| **Owners** | A + B |
| **Repo** | `sih-android` |
| **Package** | `com.sih.relay` |
| **Android type** | Library module (not an app) |
| **Day 1 status** | Scaffold complete — stub implementation |
| **Day 2 status** | Nearby Connections P2P proof — physically verified on two devices |
| **Day 3 status** | Manifest exchange & UUID diffing — physically verified on two devices (Android 17 & Android 15) |
| **Day 4 status** | 3-phone multi-hop relay (A → B → C) — COMPLETE and physically verified |
| **Day 5 status** | Foreground service + duty cycling — COMPLETE and physically verified (3-device) |
| **Day 6 status** | Room persistence + permission preflight + relay hardening — COMPLETE, physically verified (3-device + persistence-across-restart on C) |
| **Day 7 status** | Diagnostic instrumentation + 3-phone stress validation — COMPLETE (committed `320bfbd`) |
| **Integration status** | Stages 0a–5 A+B ↔ Component C — COMPLETE (committed `a1ec30d` on `integration/ab-component-c`) |

---

## Current Implementation State

### Day 1 (complete)
- Android multi-module project scaffold created (`sih-android/`)
- `:relay` library module created with Nearby Connections dependency
- All schema model classes defined: `SOSRequest`, `RelayManifest`, `SosLocation`, `UserMedicalProfile`, `EmergencyType`, `SeverityHint`, `SOSStatus`
- Public API interface defined: `RelayApi` (`startRelay`, `stopRelay`, `getRelayStore`)
- Internal boundary interface defined: `RelayDataSource` (see §Architecture Decisions)
- `RelayManager` stub created — all methods are no-ops
- Unit tests added: `RelayModelsTest` (model construction, enum completeness)
- Stub modules created: `:app`, `:data`, `:network`

### Day 2 (complete — physically verified on two devices)
- Modified `RelayManager` constructor to accept `Context` alongside `RelayDataSource`.
- Initialized Google Play Services `ConnectionsClient` via `Nearby.getConnectionsClient(context)`.
- Implemented `startRelay()`: starts Nearby Connections advertising & discovery with `P2P_CLUSTER` strategy and service ID `"com.sih.relay.SERVICE"`.
- Implemented `stopRelay()`: stops advertising, discovery, and disconnects all endpoints.
- Implemented connection lifecycle callbacks to automatically accept peer connections.
- Implemented temporary Day 2 test path `sendDay2TestPayload(endpointId)`: serializes a test `SOSRequest` using `Json.encodeToString` and sends as byte payload over Nearby Connections.
- Implemented `PayloadCallback`: receives incoming payload bytes, deserializes them to `SOSRequest` using `Json.decodeFromString`, and logs detailed diagnostics.
- Added temporary test scaffolding in `MainActivity.kt` with "Start Relay (Day 2 Test)" and "Stop Relay" buttons.

**Physical verification result (2026-08-13):**
Tested on Pixel 8 (`PIXEL8_DEV_01`) and CPH2793/Oppo (`OPPO_DEV_01`) with permissions manually granted,
Bluetooth enabled, no internet. Full sequence confirmed: advertising → discovery → endpoint found → connection
established → 417-byte SOSRequest transmitted → received and deserialized correctly. See `Logs.md` for the
exact Pixel 8 logcat sequence.

### Day 3 (complete — physically verified on two devices)
- Created `RelayPayloadCodec.kt` internal object for 1-byte payload type prefixing (`0x01` = `RelayManifest`, `0x02` = `SOSRequest`) + UTF-8 JSON encoding/decoding.
- Removed temporary Day 2 `sendDay2TestPayload()` hardcoded transmission.
- Added `CoroutineScope` (`SupervisorJob() + Dispatchers.IO`) in `RelayManager`, safely cancelled on `stopRelay()`.
- Implemented `initiateManifestExchange()`: automatically triggered on `onConnectionResult` (`STATUS_OK`) to send local `RelayManifest` built via `dataSource.getAllSosUuids()`.
- Implemented `handleIncomingManifest()`: decodes peer's manifest and launches `sendMissingSos()`.
- Implemented `sendMissingSos()`: calls `dataSource.getMissingSos(peerKnownUuids)` to compute UUID diff and transmits each missing `SOSRequest` as an individual `0x02`-prefixed byte payload.
- Implemented `handleIncomingSos()`: decodes received `SOSRequest` and forwards to `dataSource.saveSosMessages(listOf(sos))`.
- Created `RelayPayloadCodecTest.kt`: JVM unit test suite covering payload encoding/decoding, type-tag prefixing, edge cases, and set-subtraction UUID diff logic.

**Verification results:**
- **Automated (JVM)**: `./gradlew :relay:test :relay:build` passed cleanly (`BUILD SUCCESSFUL in 18s`).
- **Physical (Cross-Version)**: Verified on Phone A (Pixel 8, Android 17, ADB `PIXEL8_DEV_01`) and Phone B (Vivo, Android 15, ADB `VIVO_DEV_02`). Confirmed advertising, discovery, transient 8012 I/O error recovery, connection establishment, bidirectional `RelayManifest` transmission/reception/decoding (54 bytes, 0 known UUIDs), UUID 0-diff calculation execution via `dataSource.getMissingSos()`, and payload transfer completion.
- *Limitation*: Non-empty `SOSRequest` transfer & persistence await Component C / Room integration in Day 5+.

### Day 4 (complete — physically verified on three devices)
- Created `RelayHopLogic.kt`: pure hop-accounting object (`onRelayReceive`) that increments `relayHopCount`, updates `lastRelayedAt`, and transitions `PENDING_LOCAL → IN_RELAY` on the first relay hop.
- Modified `handleIncomingSos()` to apply `RelayHopLogic.onRelayReceive()` before `dataSource.saveSosMessages()`.
- Added `connectedEndpoints` set to `RelayManager` (added on `STATUS_OK`, removed on `onDisconnected`, cleared in `stopRelay()`).
- Added `propagateLocalSos()` / `propagateSosToConnected()`: a newly-created local SOS is pushed immediately to all currently-connected peers via the existing `RelayPayloadCodec` + Nearby payload mechanism (no reconnect / manifest exchange required).
- Added new-UUID echo-loop guard: a received SOS is only re-propagated when its UUID is genuinely new to this device's store (idempotent save + sender exclusion prevent echo loops).
- Kept the `pendingConnections` race fix intact.
- Added temporary `InMemoryRelayStore` and "Inject Test SOS" button in `MainActivity.kt` (Day 4 test scaffolding only — stands in for the Room-backed store, removed once Component C provides real SOS creation).
- Changed `nearbyConnections` from `19.3.0` to `19.2.0` in `gradle/libs.versions.toml` to match the on-device GMS Nearby module and resolve the persistent 8012 connection failures.
- Created `RelayHopLogicTest.kt`: 12 JVM unit tests.

**Verification results:**
- **Automated (JVM)**: `./gradlew :relay:test :relay:compileDebugKotlin` passed (`BUILD SUCCESSFUL`); 56 unit tests passed (`RelayHopLogicTest` 12, `RelayModelsTest` 21, `RelayPayloadCodecTest` 23).
- **Automated (APK)**: `./gradlew :app:assembleDebug -x lint -x lintDebug` passed; APK confirmed to bundle `nearby@@19.2.0`.
- **Physical (3 devices)**: Verified the A → B → C multi-hop flow (see `Logs.md`). SOS created on A at `relayHopCount=0`/`PENDING_LOCAL`; B received it at `relayHopCount=1`/`IN_RELAY` and saved it; C received it at `relayHopCount=2`/`IN_RELAY` and saved it. RelayManifest/UUID synchronization worked — peers already holding the UUID reported no missing SOSRequests.
- *Known lint note*: `ConcurrentHashMap.newKeySet()` in `RelayManager.kt` reports `NewApi` (API 24 vs minSdk 23); builds currently skip lint.

### Day 5 (complete — physically verified on three devices)
- Created `DutyCycler.kt`: pure Kotlin duty-cycle loop (~10s ACTIVE / ~40s SLEEP per contract §6.1), decoupled from SOS state; injectable timing/delay for JVM tests.
- Added `RelayManager.startScanWindow()`/`stopScanWindow()` (idempotent, guarded) — opening starts advertising/discovery, closing stops only advertising/discovery; connected endpoints survive.
- Created `RelayForegroundService` (`connectedDevice` type, START_STICKY, `exported=false`, local Binder) + `RelayNotification` + `RelayDataSourceProvider` static seam.
- 8 new `DutyCyclerTest` cases; full `:relay` suite = **64 tests PASS**. 3-device A→B→C validated under FGS + duty cycle.

### Day 6 (complete — physically verified on three devices, including persistence across restart)
- `:data` Room layer ported from C: entities, DAOs, `AppDatabase` (`sih_local.db`), `RoomConverters`, model enums; `DevicePreferences` (de-Hilted identity); `SosRequestMapper`; `RoomRelayDataSource` (implements `RelayDataSource` against Room; `saveSosMessages` uses `insertSos` IGNORE).
- `relay`: `RelayPermissionRequirements` (version-aware permission matrix + BT/Wi-Fi enable-intent helpers); startup hardening of `RelayManager` + FGS.
- `app`: `MainActivity` builds `AppDatabase` + `RoomRelayDataSource`, sets it on `RelayDataSourceProvider`, and "Start Relay" runs a permission preflight (runtime permission requests + Bluetooth enable prompt) before starting the service; on-screen status text added.
- **75 JVM tests PASS** (64 relay + 11 data). Build via JBR `gradlew.bat ... -x lint -x lintDebug` → BUILD SUCCESSFUL.
- **Physical (3 devices, Room-backed store):** A created SOS `e9407a8a-...` (`Room now holds 1 SOS UUID(s)`); B received it (`hopCount=1`, `IN_RELAY`) and forwarded via `saveSosMessages()`; B advertised it in its known-UUID manifest; C received it (`hopCount=2`, `IN_RELAY`) and forwarded via `saveSosMessages()`.
- **Persistence-across-restart (C):** after clearing the C process and relaunching, a fresh injection logged `Room now holds 2 SOS UUID(s)` — the relayed SOS survived the restart in `sih_local.db`.
- **Committed** at HEAD `6678771` (2026-08-16).

### Day 7 (COMPLETE — 3-phone scope; 4–5 phone test DEFERRED)
- The planned **4–5 physical-phone stress test has NOT been completed** and is **deferred** (only 3 physical
  devices available; testing-resource constraint, NOT a software failure; NOT reported as passed).
- Diagnostic instrumentation + production-quality 8012 fix:
  `RelayManager.handleIncomingSos` logs NEW-vs-DUPLICATE forwarding decisions; `MainActivity` has a
  test-only "Dump Store" button; TEST-ONLY connection allow-list seam (`RelayTestConfig`/`RelayTestConfigProvider`);
  production-quality 8012-aware failure handler in `RelayManager.onEndpointFound`.
- **Phase A PASSED** — baseline green: 75/75 JVM tests, `:app:assembleDebug` BUILD SUCCESSFUL.
- **Phase B PASSED** — 2-phone physical regression: Phone A (source) and Phone B
  (receiver). Both started relay, advertised/discovered, connected, and exchanged
  RelayManifest. A injected `83306893-6e6e-4065-9e9f-189a75d19616`, stored and sent it; B received the
  exact UUID with `hopCount=1`/`IN_RELAY`, classified **NEW**, and persisted it — confirmed via the
  "Dump Store" button (B showed 2 records total: 1 older pre-existing SOS + the new UUID; not a failure).
  A transient Nearby 8012 `STATUS_ENDPOINT_IO_ERROR` during the initial connection request was recorded as a
  **non-blocking observation** (devices connected and full manifest + SOS transfer succeeded).
- **Phase C PASSED** — deterministic 3-phone A→B→C using the TEST-ONLY allow-list filter (A allowed B;
  B allowed A,C; C allowed B). A originated SOS `62c667f9-9204-46dd-98b6-f1d32297552d`; B received it from
  A at `hopCount=1`/`IN_RELAY`, classified **NEW**, persisted, and propagated to C; C received it from B at
  `hopCount=2`/`IN_RELAY`, classified **NEW**, and persisted (Dump Store on C confirmed; C held 2 records —
  1 pre-existing + the new UUID, not a failure). Transient `STATUS_ENDPOINT_IO_ERROR` (8012) and
  `STATUS_ALREADY_CONNECTED_TO_ENDPOINT` self-recovered; end-to-end test succeeded.
- **Phase D PASSED** — dense/concurrent/recovery testing on 3 phones:
  - D1 multiple injections (3 UUIDs from A, all propagated A→B→C)
  - D2 echo guard (manifest exchanges showed 0 missing SOS)
  - D3 disconnect/reconnect (**PARTIALLY VALIDATED** — automatic self-healing observed; no clean controlled prolonged disconnect)
  - D4 relay stop/start (C stopped/started, reconnection, new UUID `83904f98` propagated correctly)
  - D5 process kill persistence (force-stop all 3, Room survived, reconnection succeeded)
  - D6 hop count audit (all 4 Day 7 UUIDs confirmed A=0/B=1/C=2)
  - D7 bidirectional injection (B and C injected, propagation confirmed)
  - D8 sleep-window injection **NOT TESTED** (duty-cycle already validated in Day 5)
  - 8012 race handling PASSED
- **Phases E/F/G COMPLETE** — no additional bugs found; verification complete; documentation updated.
- **Day 7 is COMPLETE.** Proceeding to Component C integration. See `PROJECT_HANDOFF.md` §16.

### Not yet implemented (scheduled per roadmap)
- **Integration Stages 0a–5 COMMITTED and PUBLISHED** — commit `a1ec30d` on `integration/ab-component-c`.
- **Integration Stage 6A** — single-device physical verification: PASS (onboarding, SOS, location, persistence, status).
- **Integration Stage 6B** — relay integration wiring + multi-device + backend testing: NOT YET COMPLETED.
- TTL cleanup (`last_relayed_at` expiry, 48-72h) → later
- Low-battery throttle mode → Day 10
- Simulation/fallback demo mode → Day 12
- **4–5 phone physical stress test (duplicate/dropped/stuck detection) → DEFERRED; future validation task
  pending availability of additional physical devices.**

---

## Architecture Decisions

### Decision 1 — RelayDataSource: Internal Boundary Interface

**What it is:** A Kotlin `interface` defined inside `:relay` that describes what the relay engine needs from persistent storage — get all UUIDs, retrieve missing SOSes, save received SOSes, observe the store as a `Flow`.

**Why it exists:** `:relay` needs to read and write SOS messages for epidemic routing. However, Room (the actual database library) is owned by `:data` (Component C). If `:relay` imported Room directly, two separate modules would both own the database — a violation of the ownership boundaries defined in `day1-contracts-and-repo-setup.md §4`.

**Visibility:** `RelayDataSource` is `public` in Kotlin (lowercase `public`, which is the default), NOT `internal`. It must be visible to `:data` so Component C can implement it. However, it is NOT a public project API contract — it is a wiring detail.

**What it is NOT:**
- It is NOT listed in `ApiEndpoints.md`
- It is NOT a shared contract between all components
- It is NOT a REST endpoint
- It is only referenced by `:relay` (declares it) and `:data` + `:app` (implement/inject it)

**How wiring works (for `:app` / Component C to implement):**
```
Day 1:
  :app creates a no-op StubRelayDataSource for now
  :app passes it into RelayManager(dataSource = stub)

Day 2+ (when :data has a real Room DAO):
  :data adds dependency on :relay
  :data writes a class (e.g. RoomRelayDataSource) implementing RelayDataSource
  :app injects RoomRelayDataSource into RelayManager
  :relay never imports Room; :data never imports Nearby Connections
```

**Dependency graph:**
```
:app  ──► :relay   (calls startRelay/stopRelay/getRelayStore)
:app  ──► :data    (creates and injects RoomRelayDataSource into RelayManager)
:data ──► :relay   (implements RelayDataSource — added when Room DAO is ready)
:relay ─X─ :data   (NEVER — circular dependency)
:relay ─X─ :network (NEVER — no backend calls in relay)
```



---

## Files Created / Modified

### Day 1
| File | Action | Notes |
|---|---|---|
| `Agent.md` | Created | This file |
| `Logs.md` | Created | Dev log |
| `ApiEndpoints.md` | Created | Relay public API contract |
| `sih-android/settings.gradle.kts` | Created | Project root, declares all 4 modules |
| `sih-android/build.gradle.kts` | Created | Root Gradle, applies plugins |
| `sih-android/gradle.properties` | Created | AndroidX, encoding, JVM args |
| `sih-android/gradle/libs.versions.toml` | Created | Version catalog |
| `sih-android/local.properties` | Created | SDK path (gitignore this) |
| `sih-android/relay/build.gradle.kts` | Created | :relay module config |
| `sih-android/relay/src/main/AndroidManifest.xml` | Created | Nearby Connections permissions |
| `sih-android/relay/src/main/java/com/sih/relay/model/SosLocation.kt` | Created | Schema §1.2 location object |
| `sih-android/relay/src/main/java/com/sih/relay/model/EmergencyType.kt` | Created | Schema §1.2 enum |
| `sih-android/relay/src/main/java/com/sih/relay/model/SeverityHint.kt` | Created | Schema §1.2 enum |
| `sih-android/relay/src/main/java/com/sih/relay/model/SOSStatus.kt` | Created | Schema §1.2 enum |
| `sih-android/relay/src/main/java/com/sih/relay/model/UserMedicalProfile.kt` | Created | Schema §1.1 relay copy |
| `sih-android/relay/src/main/java/com/sih/relay/model/SOSRequest.kt` | Created | Schema §1.2 core payload |
| `sih-android/relay/src/main/java/com/sih/relay/model/RelayManifest.kt` | Created | Schema §1.3 handshake |
| `sih-android/relay/src/main/java/com/sih/relay/api/RelayApi.kt` | Created | Public relay API |
| `sih-android/relay/src/main/java/com/sih/relay/api/RelayDataSource.kt` | Created | Internal boundary interface |
| `sih-android/relay/src/main/java/com/sih/relay/RelayManager.kt` | Created | Stub implementation |
| `sih-android/relay/src/test/java/com/sih/relay/RelayModelsTest.kt` | Created | Model unit tests |
| `sih-android/app/` | Created | Stub :app module |
| `sih-android/data/` | Created | Stub :data module |
| `sih-android/network/` | Created | Stub :network module |

### Day 2
| File | Action | Notes |
|---|---|---|
| `sih-android/relay/src/main/java/com/sih/relay/RelayManager.kt` | Modified | Nearby Connections advertising, discovery, callbacks, `sendDay2TestPayload`, payload reception |
| `sih-android/app/src/main/java/com/sih/app/MainActivity.kt` | Modified | Day 2 test scaffold buttons, `Window.FEATURE_NO_TITLE` fix |

### Day 3
| File | Action | Notes |
|---|---|---|
| `sih-android/relay/src/main/java/com/sih/relay/RelayPayloadCodec.kt` | Created | Internal wire-format codec for 1-byte payload type prefixing (`0x01`/`0x02`) |
| `sih-android/relay/src/main/java/com/sih/relay/RelayManager.kt` | Modified | Replaced Day 2 test payload with bidirectional manifest exchange, coroutine scope, UUID diffing, and SOS persistence calls |
| `sih-android/relay/src/test/java/com/sih/relay/RelayPayloadCodecTest.kt` | Created | JVM unit tests for codec encoding/decoding, edge cases, and set-subtraction UUID diff logic |

### Day 4
| File | Action | Notes |
|---|---|---|
| `sih-android/relay/src/main/java/com/sih/relay/RelayHopLogic.kt` | Created | Pure hop-accounting logic (`onRelayReceive`) — increments hop count, updates last-relayed timestamp, `PENDING_LOCAL → IN_RELAY` |
| `sih-android/relay/src/main/java/com/sih/relay/RelayManager.kt` | Modified | Applied `RelayHopLogic` in `handleIncomingSos`; added `connectedEndpoints`, `propagateLocalSos()`/`propagateSosToConnected()`, new-UUID echo-loop guard; kept `pendingConnections` race fix |
| `sih-android/relay/src/test/java/com/sih/relay/RelayHopLogicTest.kt` | Created | 12 JVM unit tests for hop accounting |
| `sih-android/app/src/main/java/com/sih/app/MainActivity.kt` | Modified | Temporary `InMemoryRelayStore` + "Inject Test SOS" button (Day 4 test scaffolding) |
| `sih-android/gradle/libs.versions.toml` | Modified | `nearbyConnections` `19.3.0` → `19.2.0` (match on-device GMS Nearby module, resolve 8012) |

---

Owned by A+B (`:relay`):
- Nearby Connections API usage
- Epidemic routing logic (manifest exchange, diff computation, SOS merge)
- Relay foreground service
- Duty-cycled BLE discovery
- TTL-based cleanup
- Permission handling for Bluetooth/Location/Nearby
- `RelayApi` public interface
- `RelayDataSource` internal boundary interface

NOT owned by A+B:
- Room database, entities, DAOs → `:data` (C)
- WorkManager upload jobs → `:data` (C)
- Citizen UI / navigation → `:app` (C)
- Backend REST calls → `:network` (shared) + backend (D)
- Backend REST endpoints → backend (D)

---

## Integration Dependencies

| Dependency | Direction | Status | Notes |
|---|---|---|---|
| `:data` implements `RelayDataSource` | `:relay` → `:data` (via interface) | **DONE (Day 6)** | `RoomRelayDataSource` implements it against Room; `SosRequestMapper` bridges relay model ↔ entity |
| `:app` injects `RelayDataSource` | `:app` wires both | **DONE (Day 6)** | `MainActivity` builds `AppDatabase` + `RoomRelayDataSource`, sets it on `RelayDataSourceProvider` |
| Backend `POST /api/v1/sos/batch` | Consumed by `:network` (not `:relay`) | N/A for relay | Relay does not call backend directly |

---

## Known Limitations

### Day 1
- No foreground service exists yet
- No permission requests exist yet
- `:data` has not implemented `RelayDataSource`; `:app` injects a stub

### Day 2
- `sendDay2TestPayload()` sends a hardcoded test SOS immediately on connection — **not the final protocol**. (Replaced in Day 3).
- No idempotent startup guard: calling `startRelay()` twice without `stopRelay()` produces `STATUS_ALREADY_ADVERTISING` / `STATUS_ALREADY_DISCOVERING` from Nearby Connections (testing artifact, not a bug — to be addressed when lifecycle becomes relevant).
- No visible UI feedback when relay fails (e.g. missing permissions) — runtime permission UX is scheduled for Day 6.
- `RelayDataSource` methods (`getAllSosUuids`, `getMissingSos`, `saveSosMessages`) are wired but the injected stub returns empty values — no actual persistence yet.

### Day 3
- Physical two-phone verification for Day 3 is COMPLETE (verifying P2P manifest exchange, decoding, and 0-diff calculation across Android 17 and Android 15).
- The current stub `RelayDataSource` injected by `:app` intentionally returns empty lists (`getAllSosUuids()` returns `emptyList()`, `getMissingSos()` returns `emptyList()`), so exchanged manifests report 0 known UUIDs and 0 missing SOSes until Room is integrated in Day 5+. Non-empty SOSRequest transfer & persistence await Component C / Room database integration.

### Day 4
- Day 4 is COMPLETE (3-phone A → B → C multi-hop relay physically verified).
- `InMemoryRelayStore` and "Inject Test SOS" in `MainActivity.kt` are temporary Day 4 test scaffolding, not production code — they stand in for the Room-backed store that Component C (:data) delivers in Day 5+. They live entirely in `:app`; `:relay`, `RelayApi`, and `RelayDataSource` are unchanged.
- Known lint `NewApi` on `ConcurrentHashMap.newKeySet()` in `RelayManager.kt` (requires API 24; project minSdk is 23) — builds currently skip lint; to be addressed with a minSdk-safe alternative.
- Day 4 verifies phone-to-phone SOS propagation only. Internet/cloud delivery of SOS messages is NOT part of Day 4 and is not claimed.

---

## Deviations from Original Plan

None on Day 1. All decisions match the approved implementation plan.

---

## Final Verification and Cleanup (Day 1)

- **Model Corrections**: `MedicalSnapshot` was corrected to `UserMedicalProfile` to strictly match the contract.
- **Data Types**: `SosLocation` lat/lng/accuracyMeters now use `Float` according to the master contract. The stale `Double` justification was removed.
- **Build Environment**: Gradle wrapper was successfully generated using Android Studio's bundled JBR.
- **Verification**: Full build, unit tests, and lint passed successfully.
- **Final Polish**: A final manual documentation/comment cleanup (updating `ApiEndpoints.md` and `SosLocation.kt` comments) was performed after the build verification. No further architectural changes were made.
