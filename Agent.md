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

### Not yet implemented (scheduled per roadmap)
- Foreground service + duty cycling → Day 5
- Permission UX / "Enable Emergency Mode" flow → Day 6
- TTL cleanup (`last_relayed_at` expiry, 48-72h) → later
- Low-battery throttle mode → Day 10
- Simulation/fallback demo mode → Day 12

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
| `:data` implements `RelayDataSource` | `:relay` → `:data` (via interface) | Pending (Day 2+) | C must write `RoomRelayDataSource` |
| `:app` injects `RelayDataSource` | `:app` wires both | Pending (Day 2+) | Stub injected on Day 1 |
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
