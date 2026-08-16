# Walkthrough — Component 1: Mesh / Relay Engine

> A developer-facing implementation log. Records what was built each day,
> what was tested, how testing was done, and what the result was.
> Append new entries as work progresses. Do not erase historical entries.

---

## Day 1 — Project Scaffold and Data Contracts

**Date:** 2026-08-13
**Scope:** A + B (`:relay` module)

### What was built

- Android multi-module Gradle project (`sih-android/`) with four modules: `:app`, `:relay`, `:data`, `:network`.
- `:relay` library module configured with Nearby Connections 19.3.0, Kotlinx Serialization 1.7.1, and Coroutines 1.8.1.
- All schema model classes implementing the master contract (`day1-contracts-and-repo-setup.md §1.1–§1.3`):
  - `SOSRequest` — core SOS payload (text-only, contract-frozen)
  - `SosLocation` — GPS coordinate object (`lat`/`lng`/`accuracyMeters` as `Float`)
  - `UserMedicalProfile` — medical snapshot auto-attached at SOS creation time
  - `EmergencyType` — 7-value enum
  - `SeverityHint` — 4-value enum
  - `SOSStatus` — 3-value enum (`PENDING_LOCAL`, `IN_RELAY`, `UPLOADED`)
  - `RelayManifest` — handshake payload exchanged between devices before SOS transfer
- `RelayApi` — the three-method public interface (`:app` calls this; never touches internal relay classes)
- `RelayDataSource` — internal boundary interface between `:relay` and `:data`, avoiding a circular dependency
- `RelayManager` — stub implementation of `RelayApi` (all methods were no-ops on Day 1)
- Living documentation: `Agent.md`, `Logs.md`, `ApiEndpoints.md`, `Component1_Overview.md`

### Architecture decisions recorded

1. `RelayDataSource` is an **internal** boundary interface — not a public team API contract. It is `public` in Kotlin only so `:data` can implement it; it is NOT in `ApiEndpoints.md`.
2. `:relay` must NEVER depend on `:data`, Room, Retrofit, or `:network`. Dependency direction is always `:app` → `:relay` and `:data` → `:relay` (for interface implementation).
3. `RelayApi` is the sole public surface of `:relay`. No other class from `:relay` is part of the team contract.

### Build / test result

- Gradle wrapper generated via Android Studio's bundled JBR.
- `./gradlew build` — all four modules compiled successfully.
- `./gradlew :relay:test` — all `RelayModelsTest` unit tests passed.
- Lint: no blocking errors.

**Day 1 result: PASS.**

---

## Day 2 — Minimal Phone-to-Phone Nearby Connections Proof

**Date:** 2026-08-13
**Scope:** A + B (`:relay` module, `:app` temporary test UI)

### Goal

Prove that one physical Android phone can send one real `SOSRequest` to a second nearby phone
over Nearby Connections with no internet — the highest-risk technical uncertainty of the project.

### What was built

#### `relay/…/RelayManager.kt` (modified)

- Constructor now accepts `Context` (required by Nearby Connections SDK) alongside `RelayDataSource`.
- `ConnectionsClient` initialized via `Nearby.getConnectionsClient(context)` using a Kotlin `lazy` delegate.
- `startRelay()` now starts both Nearby Connections advertising and discovery using `P2P_CLUSTER` strategy and service ID `com.sih.relay.SERVICE`.
- `stopRelay()` now calls `stopAdvertising()`, `stopDiscovery()`, and `stopAllEndpoints()`.
- `ConnectionLifecycleCallback` — auto-accepts every incoming connection request.
- `EndpointDiscoveryCallback` — requests a connection to every discovered endpoint.
- `PayloadCallback` — receives byte payloads, deserializes them as `SOSRequest` via `kotlinx.serialization`, and logs the result.
- `sendDay2TestPayload(endpointId)` — **temporary Day 2 test path only.** Constructs a hardcoded `SOSRequest`, serializes it to JSON bytes, and sends it via `connectionsClient.sendPayload()`. This function is clearly marked for replacement in Day 3 and is called immediately when a connection is established.

#### `app/…/MainActivity.kt` (modified)

- Programmatic `LinearLayout` with:
  - Title `TextView` ("Day 2 Relay Test Scaffold")
  - "Start Relay (Day 2 Test)" `Button` → calls `relayManager.startRelay()`
  - "Stop Relay" `Button` → calls `relayManager.stopRelay()`
- `Window.FEATURE_NO_TITLE` applied before `super.onCreate()` to remove the system ActionBar that was covering the first view.
- Explicit background color (`#121212`), button colors, and margins so buttons remain visible regardless of OEM dark themes.
- Stub `RelayDataSource` injected inline — returns empty collections; has no persistence.

### Architecture maintained

- `:relay` still has zero dependency on `:data`, Room, or `:network`.
- `RelayApi` signature is unchanged: `startRelay()`, `stopRelay()`, `getRelayStore()`.
- `RelayDataSource` boundary is unchanged.
- `sendDay2TestPayload()` is deliberately isolated (private, clearly commented) so Day 3 can replace it without touching the connection lifecycle callbacks.

### Build / test result

- `./gradlew test build` — all modules compiled, `RelayModelsTest` unit tests passed.

### Physical device test

**Devices:**
| Device | ADB ID | OS |
|---|---|---|
| Pixel 8 | `PIXEL8_DEV_01` | Android 14+ (targetSdk 35) |
| CPH2793 / Oppo | `VIVO_DEV_02` | Android 13 |

**Pre-conditions:**
- Bluetooth enabled on both devices.
- Location enabled on both devices.
- Runtime permissions manually granted via Android Settings on both devices:
  - `BLUETOOTH_ADVERTISE`
  - `NEARBY_WIFI_DEVICES`
  - `ACCESS_FINE_LOCATION`
- APK installed via `adb install` using Android Studio's bundled JBR (`JAVA_HOME` set accordingly).
- No internet connection used at any point during the test.

**Diagnosis work performed (not part of Day 2 implementation):**
Early testing revealed the buttons were not visually responding. Root-cause investigation via `uiautomator dump` and ADB tap confirmed:
- The ActionBar was covering the first two views (fixed by `Window.FEATURE_NO_TITLE`).
- On the Pixel 8, `BLUETOOTH_ADVERTISE` and `NEARBY_WIFI_DEVICES` permissions were not yet granted (fixed by manual grant in Settings).
- Once both issues were resolved, ADB tap testing confirmed the click listeners fired correctly and `startRelay()` was called successfully on both devices.

**Confirmed logcat sequence (Pixel 8):**

```
I/MainActivity:        Start Relay button clicked
I/RelayManager_Day2:   startRelay() invoked. Starting Nearby Connections advertising & discovery (P2P_CLUSTER)...
I/RelayManager_Day2:   Successfully started advertising with service ID: com.sih.relay.SERVICE
I/RelayManager_Day2:   Successfully started discovery with service ID: com.sih.relay.SERVICE
D/RelayManager_Day2:   Endpoint found: XFDC (...). Requesting connection...
D/RelayManager_Day2:   Connection initiated with endpoint: XFDC. Auto-accepting...
I/RelayManager_Day2:   SUCCESS: Connection established with endpoint: XFDC
I/RelayManager_Day2:   SUCCESS: Sent test SOSRequest payload (417 bytes) to endpoint: XFDC
D/RelayManager_Day2:   Payload transfer to/from XFDC completed successfully
D/RelayManager_Day2:   Payload received from endpoint: XFDC
I/RelayManager_Day2:   SUCCESS: SOSRequest deserialized from endpoint XFDC: SOSRequest(
    uuid=2914cf02-46b6-4af1-a2f7-5b05f1eaaf47,
    deviceId=DAY2-TEST-DEVICE,
    createdAt=2026-08-13T12:00:00Z,
    location=SosLocation(lat=28.6139, lng=77.209, accuracyMeters=5.0),
    isQuickSos=true,
    emergencyType=UNSPECIFIED,
    severityHint=null,
    peopleCount=null,
    medicalSnapshot=null,
    customMessage=null,
    contactNumber=null,
    relayHopCount=0,
    lastRelayedAt=2026-08-13T12:00:00Z,
    status=PENDING_LOCAL
)
```

**Testing artifact (not a failure):**
When "Start Relay" was tapped more than once per session without "Stop Relay" between taps, Nearby Connections returned:
- `STATUS_ALREADY_ADVERTISING`
- `STATUS_ALREADY_DISCOVERING`
- `STATUS_ALREADY_CONNECTED_TO_ENDPOINT`

These are correct Nearby Connections responses to redundant calls. They are not bugs. Idempotent lifecycle management is not in scope until Day 3+.

**Day 2 result: PASS.**

The full P2P offline transport path was confirmed on real hardware:
> Advertising → Discovery → Endpoint found → Connection established → 417-byte SOSRequest serialized and sent → Transfer completed → Received → SOSRequest deserialized correctly

---

## Day 3 — RelayManifest Exchange and UUID Diffing

**Date:** 2026-08-13
**Scope:** A + B (`:relay` module)

### Goal

Replace the temporary Day 2 hardcoded SOS transmission with the real epidemic routing protocol handshake:
> Connection established → bidirectional `RelayManifest` exchange → compute UUID diff → send only missing `SOSRequest` payloads → save received `SOSRequest` records to storage.

### What was built

#### `relay/…/RelayPayloadCodec.kt` (new file)

- Extracted wire-format encoder/decoder object to isolate byte payload framing from Nearby Connections API calls.
- Implements 1-byte type tag prefixing:
  - `0x01` (`TYPE_MANIFEST`) = `RelayManifest` JSON payload
  - `0x02` (`TYPE_SOS`) = `SOSRequest` JSON payload
- Exposes `encodeManifest()`, `encodeSos()`, `peekType()`, `body()`, `decodeManifest()`, `decodeSos()`.
- Documented clearly as an internal `:relay` implementation detail, not a public contract schema change.

#### `relay/…/RelayManager.kt` (modified)

- **Removed** the temporary `sendDay2TestPayload()` method.
- **Added CoroutineScope**: Configured with `SupervisorJob() + Dispatchers.IO`, stored in `relayScope`, and safely cancelled in `stopRelay()`. Recreated on stop so `startRelay()` remains re-invokable.
- **Initiated Manifest Exchange**: `onConnectionResult` `STATUS_OK` launches `initiateManifestExchange(endpointId)`:
  - Fetches local known UUID list via `dataSource.getAllSosUuids()`.
  - Builds `RelayManifest` with ISO 8601 UTC timestamp (minSdk 23 compatible via `SimpleDateFormat`).
  - Encodes via `RelayPayloadCodec.encodeManifest()` (prefix `0x01`) and sends payload via Nearby Connections.
- **Payload Routing**: `payloadCallback` inspects byte 0 via `RelayPayloadCodec.peekType()`:
  - `0x01` → calls `handleIncomingManifest()`
  - `0x02` → calls `handleIncomingSos()`
- **UUID Diff & Missing SOS Transfer**: `handleIncomingManifest()` deserializes peer's manifest and launches `sendMissingSos()`:
  - Calls `dataSource.getMissingSos(peerManifest.knownUuids)` to compute the missing set.
  - Transmits each missing `SOSRequest` as an individual byte payload encoded with prefix `0x02`.
  - Logs diff statistics (`peer has N UUIDs, M SOSRequest(s) to send`).
- **SOS Persistence**: `handleIncomingSos()` deserializes received `SOSRequest` and calls `dataSource.saveSosMessages(listOf(sos))`.

#### `relay/src/test/java/com/sih/relay/RelayPayloadCodecTest.kt` (new file)

- Comprehensive JVM unit test suite covering:
  - Type-tag prefix encoding and distinctness (`TYPE_MANIFEST` = `0x01`, `TYPE_SOS` = `0x02`).
  - Full round-trip serialization/deserialization for `RelayManifest` (including edge cases: empty `knownUuids`, large UUID lists).
  - Full round-trip serialization/deserialization for `SOSRequest`.
  - Byte slicing edge cases (empty input, single-byte type-only input).
  - Set-subtraction UUID diff logic (`localUuids.filter { it !in peerKnownUuids }`) testing empty stores, partial overlaps, complete overlaps, and peer supersets.

### Architecture maintained

- `:relay` has zero dependency on `:data`, Room, or `:network`.
- `RelayApi` signature is unchanged (`startRelay()`, `stopRelay()`, `getRelayStore()`).
- `RelayDataSource` boundary interface is unchanged (`getAllSosUuids()`, `getMissingSos()`, `saveSosMessages()`, `observeAllSos()`).
- `RelayManifest` and `SOSRequest` data schemas remain 100% frozen per master contract.

### Verification result

- **Automated Command**: `./gradlew :relay:test :relay:build` executed via JBR.
- **Build result**: `BUILD SUCCESSFUL in 18s` (75 actionable tasks: 26 executed, 49 up-to-date).
- **Automated Test result**: `PASS`. All unit tests passed cleanly (`:relay:testDebugUnitTest`, `:relay:testReleaseUnitTest`, `:relay:test`), including `RelayModelsTest` and `RelayPayloadCodecTest`.

### Physical device test (Cross-Version Verification)

**Devices:**
| Role | Device Model | OS | ADB ID |
|---|---|---|---|
| Phone A | Pixel 8 | Android 17 | `PIXEL8_DEV_01` |
| Phone B | Vivo phone | Android 15 | `VIVO_DEV_02` |

**Pre-conditions:**
- Bluetooth enabled on both devices.
- Location enabled on both devices.
- Nearby permissions granted on both devices.
- No internet connection used at any point.

**Observed Physical Logcat Flow:**
1. "Start Relay" triggered on both devices: Nearby Connections advertising and discovery initialized successfully.
2. Discovery succeeded; endpoints found.
3. Transient `STATUS_ENDPOINT_IO_ERROR` (8012) occurred during connection setup and recovered automatically on subsequent attempt.
4. Connection request accepted and connection established (`STATUS_OK`).
5. `initiateManifestExchange()` executed bidirectionally on both devices.
6. `RelayManifest` payloads (54 bytes, prefix `0x01`) transmitted and received successfully.
7. Received `RelayManifest` payloads decoded successfully (0 known UUIDs from `stubDataSource`).
8. `dataSource.getMissingSos()` executed UUID diff calculation on both sides.
9. Calculated 0 missing SOSRequests and logged `"No missing SOSRequests to send to endpoint XXXX — peer is up to date"`.
10. Payload transfer completed cleanly.

**Physically verified:**
- P2P discovery & physical connection establishment across Android 17 & Android 15.
- Bidirectional `RelayManifest` transmission (`0x01` prefix tag).
- Manifest reception & decoding.
- `dataSource` UUID diff calculation execution.
- Empty-diff handling (0 missing SOSes).
- Nearby Connections payload transfer completion.

**Not yet physically verified (scheduled for Component C / Room integration in Day 5+):**
- Non-empty `SOSRequest` transfer (`0x02` prefix tag).
- Reception and persistence of actual `SOSRequest` records.
- Synchronization of real SOS data between phones.
*(Reason: `stubDataSource` intentionally returns empty lists until Room database integration).*

**Day 3 result: PASS (Complete).**

---

## Day 4 — Multi-Hop Routing (3-Phone A → B → C Relay Test)

**Date:** 2026-08-14
**Scope:** A + B (`:relay` module, `:app` temporary test UI)

### Goal

Prove a message can hop from Phone A to Phone C through a middle Phone B, with hop accounting
(`relayHopCount` 0 → 1 → 2) applied correctly at each receiving device, using the existing
manifest/UUID-diff protocol — without requiring a reconnect after an SOS is created.

### What was built

#### `relay/…/RelayHopLogic.kt` (new file)

- Pure `internal object` with `onRelayReceive(sos, relayedAt)`:
  - Increments `relayHopCount` on every hop.
  - Updates `lastRelayedAt` to the current hop timestamp.
  - Transitions `PENDING_LOCAL → IN_RELAY` once, on the first relay hop.
- JVM-testable in isolation (no Android/Nearby dependencies).

#### `relay/…/RelayManager.kt` (modified)

- `handleIncomingSos()` now applies `RelayHopLogic.onRelayReceive()` before `dataSource.saveSosMessages()`.
- Added `connectedEndpoints` set (added on `STATUS_OK`, removed on `onDisconnected`, cleared in `stopRelay()`).
- Added `propagateLocalSos(sos)` and `propagateSosToConnected(sos, fromEndpointId)`:
  - Push a newly-created local SOS immediately to every currently-connected peer (no reconnect / manifest exchange required).
  - Exclude the originating endpoint when re-forwarding a received SOS (prevents echo back to the sender).
- Added a new-UUID echo-loop guard: a received SOS is only re-propagated when its UUID is new to this device's store (idempotent `putIfAbsent` save + sender exclusion prevent echo loops).
- Kept the existing `pendingConnections` race fix and the Day 3 manifest-exchange behavior unchanged.

#### `relay/…/RelayHopLogicTest.kt` (new file)

- 12 JVM unit tests for hop accounting (increments, `IN_RELAY` transition, `lastRelayedAt` updates).

#### `app/…/MainActivity.kt` (modified — temporary Day 4 scaffolding)

- Added `InMemoryRelayStore` (thread-safe, idempotent by UUID, live `Flow`), a stand-in for the Room-backed store.
- Added "Inject Test SOS" button: creates an origin SOS (`relayHopCount = 0`, `status = PENDING_LOCAL`), saves it locally, then calls `relayManager.propagateLocalSos()`.

#### `gradle/libs.versions.toml` (modified)

- `nearbyConnections` changed `19.3.0` → `19.2.0` to match the on-device GMS Nearby module (`play-services-nearby@@19.2.0`), resolving the persistent `STATUS_ENDPOINT_IO_ERROR` (8012) seen at `requestConnection()`.

### Architecture maintained

- `:relay` still has zero dependency on `:data`, Room, or `:network`.
- `RelayApi` and `RelayDataSource` interfaces unchanged.
- `RelayManifest` / `SOSRequest` schemas and the wire format (`0x01`/`0x02` type tags) unchanged.
- Day 3 manifest exchange / UUID diffing behavior preserved verbatim.

### Verification result

- **Automated Command**: `./gradlew :relay:test :relay:compileDebugKotlin` via JBR → `BUILD SUCCESSFUL`.
- **Automated Test result**: `PASS`. 56 unit tests passed (`RelayHopLogicTest` 12, `RelayModelsTest` 21, `RelayPayloadCodecTest` 23).
- **APK build**: `./gradlew :app:assembleDebug -x lint -x lintDebug` → `BUILD SUCCESSFUL`; dex confirmed `nearby@@19.2.0`.

### Physical device test (3-phone, A → B → C)

**Topology:** A (SOS source) → B (relay) → C (next relay/node).

**Observed physical flow:**
1. A created a test SOS — UUID `153f5b02-6855-4e4c-b646-997fdc6e9638`, `relayHopCount = 0`, `status = PENDING_LOCAL` — and propagated it to B.
2. B received it with `relayHopCount = 1`, `status = IN_RELAY`, and forwarded it to `DataSource.saveSosMessages()`.
3. B then connected to C and forwarded the same SOS; C received it with `relayHopCount = 2`, `status = IN_RELAY`, and forwarded it to `DataSource.saveSosMessages()`.
4. RelayManifest / UUID synchronization worked: devices exchanged manifests, and devices that already knew the UUID correctly reported **no missing SOSRequests** — the same SOS is not blindly retransmitted when the peer is already up to date.

**Transient Nearby Connections events observed (recorded as transient connection events, NOT Day 4 failures):**
- `STATUS_ENDPOINT_IO_ERROR` (8012)
- `STATUS_ALREADY_CONNECTED_TO_ENDPOINT` (8003)
- `STATUS_ENDPOINT_UNKNOWN` (8011 in an earlier test)

None of these prevented the eventual successful connections and SOS propagation.

**Day 4 result: PASS (Complete).**

---

## Day 5 — Foreground Service + Duty Cycling

**Date:** 2026-08-14
**Scope:** A + B (`:relay` + temporary `:app` scaffolding)

### What was built

1. **`DutyCycler`** (`relay/src/main/java/com/sih/relay/DutyCycler.kt`) — pure Kotlin duty-cycle loop that toggles ONLY the scan/advertise window: ~10s ACTIVE then ~40s SLEEP, per `day1-contracts §6.1`. Deliberately decoupled from SOS state — the relay store and established connections survive window close. Timing and delay are injectable for deterministic JVM tests.
2. **`RelayManager.startScanWindow()` / `stopScanWindow()`** (`internal`, idempotent, guarded by a `scanningWindow` flag) — the Day 5 battery lever. Opening a window starts advertising + discovery; closing it stops only advertising + discovery, keeping connected endpoints alive. `startRelay()`/`stopRelay()` now delegate to these; Day 2–4 semantics unchanged.
3. **`RelayForegroundService`** (`relay/.../service/RelayForegroundService.kt`) — owns `RelayManager` + `DutyCycler`, `START_STICKY`, `exported=false`, `foregroundServiceType="connectedDevice"`, local `Binder` exposing the manager + data source. `ACTION_STOP` stops the relay; `onDestroy` stops duty cycle + relay + scope. No Hilt — uses the `RelayDataSourceProvider` seam.
4. **`RelayNotification`** (`relay/.../service/RelayNotification.kt`) — low-priority ongoing channel "Relay Service", `android.R.drawable.ic_menu_mylocation` icon (library ships no app resources yet).
5. **`RelayDataSourceProvider`** (`relay/.../service/RelayDataSourceProvider.kt`) — process-wide holder so `:app`'s temporary `InMemoryRelayStore` reaches the `:relay` service without a DI framework. Component C replaces this with Hilt.
6. **Manifest** — `<service android:name=".service.RelayForegroundService" android:exported="false" android:foregroundServiceType="connectedDevice"/>` merged into `:app` (verified in merged manifest).
7. **`MainActivity`** (temporary Day 5 scaffold) — sets `RelayDataSourceProvider.dataSource = InMemoryRelayStore`, binds to the service, Start/Stop now start/stop the *service* (relay + duty cycle in one action). Inject Test SOS preserved.

### What was tested (automated)

- **`DutyCyclerTest`**: 8 new deterministic JVM tests (virtual time via `TestScope`) — window opens first, closes after active duration, reopens after sleep, full multi-window cycles, state stays ACTIVE during window, `start()` idempotency, `stop()` halts loop and blocks further windows, `isRunning=false` before start.
- **Full `:relay` suite: 64 tests PASS** (`DutyCyclerTest` 8, `RelayHopLogicTest` 12, `RelayModelsTest` 21, `RelayPayloadCodecTest` 23 — 0 failures/errors).
- **APK build**: `:app:assembleDebug -x lint -x lintDebug` → `BUILD SUCCESSFUL` (`app-debug.apk`). Merged manifest confirms `RelayForegroundService` with `foregroundServiceType="connectedDevice"` + `FOREGROUND_SERVICE`/`FOREGROUND_SERVICE_CONNECTED_DEVICE` permissions.

### Physical device testing (3-device, A → B → C)

Topology: A (SOS source) → B (relay) → C (next relay/node).

Validated flow:
1. Phone A generated an SOSRequest — UUID `37cb7ba8-580c-4bdf-81f3-37392d73e2df`.
2. A successfully sent the SOS to B.
3. B successfully received and relayed the same SOS onward.
4. C successfully received the same UUID from B.
5. C received it with `relayHopCount = 2` and `status = IN_RELAY`.
6. The message was forwarded to `DataSource.saveSosMessages()` on C.
7. The test demonstrates the intended A → B → C multi-hop propagation path.
8. The relay remained operational through the foreground service and duty-cycle scan windows.
9. Nearby Connections advertising/discovery and manifest exchange succeeded between the nodes.

**Day 5 result: PASS (Complete).** 3-device A → B → C multi-hop relay validated end to end under the foreground service + duty-cycle scan windows.

---

## Day 6 — Room Persistence + Permission Preflight + Relay Hardening

**Date:** 2026-08-15
**Scope:** A + B (`:relay`, `:data`, `:app`)

### Goal

Close the real-device friction gap (roadmap Day 6): the app must ask the user for the runtime
permissions Android requires and explicitly ask the user to enable Bluetooth/Wi-Fi rather than
doing so silently. Also replace the temporary in-memory store with real Room persistence so a
relayed SOS is durable.

### What was built

#### `:data` — Room persistence layer
- Room entities + DAOs + `AppDatabase` (`sih_local.db`), `RoomConverters`, model enums (ported from Component C, schema-frozen).
- `DevicePreferences` — de-Hilted encrypted device identity.
- `SosRequestMapper` — relay model ↔ Room entity (single mapping point).
- `RoomRelayDataSource` — implements `RelayDataSource` against Room (`saveSosMessages` via `insertSos` IGNORE, `getAllSosUuids`, `getMissingSos`, `observeAllSos` derived from the DAO count flow).

#### `relay` — permission helper + hardening
- `RelayPermissionRequirements` — version-aware matrix of required runtime permissions and Bluetooth/Wi-Fi enable-intent helpers.
- Startup hardening of `RelayManager` + `RelayForegroundService` (data-source seam null-safe; in-memory fallback store only when the seam is absent).

#### `app` — preflight + Room wiring
- `MainActivity` builds `AppDatabase` + `RoomRelayDataSource` and sets it on `RelayDataSourceProvider` (logs `Room-backed RelayDataSource wired: sih_local.db`).
- "Start Relay" now runs a preflight: request any missing runtime permissions (`onRequestPermissionsResult`), then prompt to enable Bluetooth (`onActivityResult`), then start the foreground service. On-screen status text added.
- "Inject Test SOS" saves through Room and logs `Room now holds N SOS UUID(s)`.

### Architecture maintained
- `:relay` still has zero dependency on `:data`, Room, or `:network`; `RelayApi`/`RelayDataSource` unchanged; wire format unchanged.
- No Hilt; the `RelayDataSourceProvider` seam is preserved (Component C replaces it with Hilt).

### What was tested (automated)
- **75 JVM tests PASS, 0 failures** (64 relay: DutyCycler 8, RelayHopLogic 12, RelayModels 21, RelayPayloadCodec 23; 11 data: RoomRelayDataSource 6, SosRequestMapper 5).
- **Build**: `gradlew.bat :app:assembleDebug :data:testDebugUnitTest :relay:testDebugUnitTest -x lint -x lintDebug` → `BUILD SUCCESSFUL`.

### Physical device validation (3-device, A → B → C, Room-backed store)

Topology: A (SOS source) → B (relay) → C (next relay/node). All nodes used the Room-backed store.

1. A generated an SOS — UUID `e9407a8a-ae3c-461c-b23d-4633197db5fa` — stored it in Room and logged `Room now holds 1 SOS UUID(s)`.
2. B received the same UUID with `relayHopCount = 1`, `status = IN_RELAY`, and logged `forwarded to DataSource.saveSosMessages()`.
3. B had the UUID in its known-UUID manifest state and sent that SOS to a peer with 0 known UUIDs.
4. C received the same UUID with `relayHopCount = 2`, `status = IN_RELAY`, and logged `forwarded to DataSource.saveSosMessages()`.

**Result: PASS.** Non-empty SOS propagated A → B → C and was written through the Room-backed save path on B and C.

### Persistence-across-restart verification (device C)

After stopping/clearing the C app process and relaunching:
- C logged `Room-backed RelayDataSource wired: sih_local.db`.
- A new local SOS was injected (UUID `27a28ece-ce67-46dd-a456-f424f8b67933`).
- C logged `Room now holds 2 SOS UUID(s)`.

Interpretation: the original relayed SOS (`e9407a8a-...`) survived the C process restart, because the
new injection produced 2 UUIDs in Room, not just the newly created one. This verifies that a relayed
SOS persisted in Room on the receiving node across a process restart.

**Day 6 result: PASS (Complete).** Room persistence, permission preflight, and relay hardening are
verified: 75 automated tests + 3-device A → B → C relay with a non-empty SOS persisted to Room and
surviving a process restart on C.
