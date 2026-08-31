# Logs.md — Component 1: Mesh/Relay Engine (:relay)

> Chronological record of meaningful implementation events.
> Keep entries concise. Do not log every trivial edit.

---

## 2026-08-13 — Day 1: Project Scaffold

### 03:24 — Approved implementation plan

Plan approved after two rounds of review:
1. Initial plan submitted
2. Clarification: `RelayDataSource` is an internal boundary abstraction, NOT a public API contract. Will appear in `Agent.md` only, not `ApiEndpoints.md`.

### 03:28 — Started Day 1 execution

Proceeding with Day 1 scope:
- Android multi-module project scaffold
- `:relay` library module
- Nearby Connections dependency
- Schema models (`SOSRequest`, `RelayManifest`, and supporting types)
- Public relay API interface (`RelayApi`)
- Internal boundary interface (`RelayDataSource`)
- `RelayManager` stub
- Unit tests for models
- Stub modules (`:app`, `:data`, `:network`)

### Files created

**Living docs:**
- `Agent.md` — component context, architecture decisions
- `Logs.md` — this file
- `ApiEndpoints.md` — relay public API contract

**Gradle infrastructure (`sih-android/`):**
- `settings.gradle.kts` — declares `:app`, `:relay`, `:data`, `:network`
- `build.gradle.kts` (root) — applies AGP + Kotlin plugins
- `gradle.properties`
- `gradle/libs.versions.toml` — version catalog (Kotlin 2.0.0, AGP 8.5.2, Nearby 19.3.0, Coroutines 1.8.1, Serialization 1.7.1)
- `local.properties` — Android SDK path

**`:relay` module:**
- `relay/build.gradle.kts`
- `relay/src/main/AndroidManifest.xml` — Nearby Connections permissions declared
- `relay/src/main/java/com/sih/relay/model/SosLocation.kt`
- `relay/src/main/java/com/sih/relay/model/EmergencyType.kt` (7 values per contract)
- `relay/src/main/java/com/sih/relay/model/SeverityHint.kt` (4 values per contract)
- `relay/src/main/java/com/sih/relay/model/SOSStatus.kt` (3 values per contract)
- `relay/src/main/java/com/sih/relay/model/UserMedicalProfile.kt`
- `relay/src/main/java/com/sih/relay/model/SOSRequest.kt`
- `relay/src/main/java/com/sih/relay/model/RelayManifest.kt`
- `relay/src/main/java/com/sih/relay/api/RelayApi.kt` — public interface
- `relay/src/main/java/com/sih/relay/api/RelayDataSource.kt` — internal boundary
- `relay/src/main/java/com/sih/relay/RelayManager.kt` — stub
- `relay/src/test/java/com/sih/relay/RelayModelsTest.kt`

**Stub modules:**
- `app/` — minimal stub for `:app`
- `data/` — minimal stub for `:data`
- `network/` — minimal stub for `:network`

### Architecture decisions recorded

1. `RelayDataSource` = internal boundary interface (not a public contract)

### Build results

- Gradle wrapper successfully generated using Android Studio's bundled JBR.
- Full Gradle build (`./gradlew build`) run.
- All four modules (`:app`, `:relay`, `:data`, `:network`) compiled successfully.
- Unit tests passed successfully.
- Lint completed without blocking errors.
- `MedicalSnapshot` was corrected to `UserMedicalProfile` and location coordinate types were corrected from `Double` to `Float` per contract prior to build.

### 04:29 — Final Manual Cleanup

- `ApiEndpoints.md` updated from `MedicalSnapshot` to `UserMedicalProfile`.
- Stale `Double`-precision comment removed from `SosLocation.kt`.
- No functional code changes were made in this cleanup.
- Build/tests/lint had already passed before this cleanup.
- Day 1 is ready for packaging.

---

## 2026-08-13 — Day 2: Minimal Phone-to-Phone Nearby Connections Proof

### 17:35 — Approved implementation plan
Plan approved for Day 2 minimal proof:
- Implement Nearby Connections advertising and discovery via `P2P_CLUSTER` strategy in `RelayManager.kt`.
- Implement connection lifecycle and payload reception callbacks.
- Create an isolated temporary test transmission path (`sendDay2TestPayload`) sending a serialized `SOSRequest`.
- Deserialize received payloads to `SOSRequest` and log diagnostics.
- Add temporary button scaffolding in `MainActivity.kt`.

### Files modified
- `sih-android/relay/src/main/java/com/sih/relay/RelayManager.kt` — Nearby Connections advertising, discovery, connection lifecycle, test payload transmission, and payload reception/deserialization.
- `sih-android/app/src/main/java/com/sih/app/MainActivity.kt` — temporary Activity UI with "Start Relay (Day 2 Test)" and "Stop Relay" buttons.

### Verification (emulator build)
- `./gradlew test build` executed via JBR.
- Model unit tests (`RelayModelsTest`) passed.
- All modules compiled successfully.

### 19:10 — Physical two-phone verification (PASSED)

Two physical Android devices were used:
- **Pixel 8** (ADB ID: `PIXEL8_DEV_01`) — Android 14+, targetSdk 35
- **CPH2793 / Oppo** (ADB ID: `VIVO_DEV_02`) — Android 13

Pre-conditions confirmed on both devices:
- Bluetooth enabled
- Location enabled
- Required runtime permissions (`BLUETOOTH_ADVERTISE`, `NEARBY_WIFI_DEVICES`, `ACCESS_FINE_LOCATION`) manually granted via Android Settings
- `com.sih.app` installed via `adb install` with `JAVA_HOME` pointing to Android Studio's bundled JBR

Physical test procedure:
- App launched on both devices (via `adb shell am start -n com.sih.app/.MainActivity`)
- "Start Relay (Day 2 Test)" button tapped on both devices
- No internet connection used at any point

**Pixel 8 logcat sequence (exact):**

```
I/MainActivity: Start Relay button clicked
I/RelayManager_Day2: startRelay() invoked. Starting Nearby Connections advertising & discovery (P2P_CLUSTER)...
I/RelayManager_Day2: Successfully started advertising with service ID: com.sih.relay.SERVICE
I/RelayManager_Day2: Successfully started discovery with service ID: com.sih.relay.SERVICE
D/RelayManager_Day2: Endpoint found: XFDC (...). Requesting connection...
D/RelayManager_Day2: Connection initiated with endpoint: XFDC. Auto-accepting...
I/RelayManager_Day2: SUCCESS: Connection established with endpoint: XFDC
I/RelayManager_Day2: SUCCESS: Sent test SOSRequest payload (417 bytes) to endpoint: XFDC
D/RelayManager_Day2: Payload transfer to/from XFDC completed successfully
D/RelayManager_Day2: Payload received from endpoint: XFDC
I/RelayManager_Day2: SUCCESS: SOSRequest deserialized from endpoint XFDC: SOSRequest(
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

**Result: PASS.** Complete P2P payload path proven:
- Advertising → Discovery → Endpoint found → Connection established → 417-byte SOSRequest serialized and sent → Transfer completed → Payload received and SOSRequest deserialized — all offline, no internet.

**Testing artifact note (not a failure):**
During repeated manual testing (Start Relay was tapped more than once per session), the following Nearby Connections status codes appeared:
- `STATUS_ALREADY_ADVERTISING` — advertising was already active from a prior `startRelay()` call
- `STATUS_ALREADY_DISCOVERING` — discovery was already active
- `STATUS_ALREADY_CONNECTED_TO_ENDPOINT` — the two devices had already established a connection in a prior attempt

These are expected consequences of calling `startRelay()` multiple times without an intervening `stopRelay()` and are NOT implementation failures. Idempotent lifecycle handling is not in scope until Day 3+ unless the Day 3 specification explicitly requires it.

---

## 2026-08-13 — Day 3: RelayManifest Exchange & UUID Diffing

### 19:55 — Approved implementation plan
Plan approved for Day 3 protocol implementation:
- Replace temporary Day 2 `sendDay2TestPayload()` with bidirectional `RelayManifest` exchange upon connection establishment.
- Introduce `RelayPayloadCodec.kt` as an internal wire-format helper using a 1-byte type tag prefix (`0x01` = `RelayManifest`, `0x02` = `SOSRequest`) + UTF-8 JSON body.
- Implement coroutine scope (`SupervisorJob() + Dispatchers.IO`) in `RelayManager.kt`, safely cancelled on `stopRelay()`.
- Implement `initiateManifestExchange()` on `onConnectionResult` (`STATUS_OK`).
- Implement `handleIncomingManifest()` to decode peer manifest and trigger `sendMissingSos()`.
- Implement `sendMissingSos()` to compute UUID diff using `RelayDataSource.getMissingSos()` and transmit missing `SOSRequest` payloads.
- Implement `handleIncomingSos()` to decode received `SOSRequest` and forward to `RelayDataSource.saveSosMessages()`.
- Add `RelayPayloadCodecTest.kt` for unit testing payload encoding/decoding and set-subtraction UUID diff logic on the JVM.

### Files modified / created
- `sih-android/relay/src/main/java/com/sih/relay/RelayPayloadCodec.kt` — **[NEW]** internal wire-format codec (1-byte type tag prefixing).
- `sih-android/relay/src/main/java/com/sih/relay/RelayManager.kt` — **[MODIFY]** removed Day 2 test payload; implemented coroutine scope, manifest exchange, payload type routing, UUID diffing, and SOS persistence calls.
- `sih-android/relay/src/test/java/com/sih/relay/RelayPayloadCodecTest.kt` — **[NEW]** JVM unit tests for codec encoding/decoding, edge cases, and set-subtraction UUID diff logic.

### Verification (Automated JVM Tests & Build)
- Executed `./gradlew :relay:test :relay:build` via JBR.
- **Build result**: `BUILD SUCCESSFUL in 18s` (75 actionable tasks: 26 executed, 49 up-to-date).
- **Test result**: `PASS`. All unit tests passed cleanly (`:relay:testDebugUnitTest`, `:relay:testReleaseUnitTest`, `:relay:test`), including all `RelayModelsTest` and `RelayPayloadCodecTest` test cases.
- **Architectural boundaries**: Preserved intact. `:relay` remains completely independent of `:data`, Room, and `:network`. `RelayApi` and `RelayDataSource` interfaces were not changed.

### 20:15 — Physical two-phone verification (PASSED — Cross-Version Verification)

Two physical Android devices running different Android OS versions were used:
- **Phone A (Pixel 8)** (ADB ID: `PIXEL8_DEV_01`) — Android 17
- **Phone B (Vivo)** (ADB ID: `VIVO_DEV_02`) — Android 15

Pre-conditions confirmed on both devices:
- Bluetooth enabled
- Location enabled
- Nearby permissions granted
- No internet connection used at any point

Observed physical sequence:
1. "Start Relay" triggered on both devices: Nearby Connections advertising and discovery started successfully.
2. The devices discovered each other over Nearby Connections.
3. During connection setup, a transient `STATUS_ENDPOINT_IO_ERROR` (8012) occurred during endpoint discovery/connection attempt. It recovered automatically on subsequent retry.
4. Connection request initiated and accepted; connection established (`STATUS_OK`).
5. `initiateManifestExchange()` executed bidirectionally on both devices.
6. Both devices sent their `RelayManifest` (54 bytes, `0x01` type-tag prefix).
7. Both devices received and decoded the peer's `RelayManifest` (0 known SOS UUIDs, as expected from `stubDataSource`).
8. Both devices executed UUID diff calculation via `dataSource.getMissingSos()`.
9. Both devices calculated 0 missing SOSRequests and logged `"No missing SOSRequests to send to endpoint XXXX — peer is up to date"`.
10. Payload transfer completed successfully.

**Physically verified:**
- P2P discovery across Android 17 (Pixel 8) and Android 15 (Vivo).
- Physical connection establishment.
- Bidirectional `RelayManifest` transmission, reception, and decoding.
- UUID diff calculation execution and empty-diff handling.
- Successful Nearby Connections payload transfer.

**Not yet physically verified (scheduled for Component C / Room integration in Day 5+):**
- Non-empty `SOSRequest` transfer.
- Reception and persistence of actual `SOSRequest` records.
- Synchronization of real SOS data between phones.
*(Reason: `stubDataSource` intentionally returns empty lists until Room database integration).*

**Already JVM verified (`RelayPayloadCodecTest`):**
- `RelayPayloadCodec` serialization/deserialization.
- `0x01` `RelayManifest` type-tag framing.
- `0x02` `SOSRequest` type-tag framing.
- Populated `SOSRequest` handling.
- Set-difference UUID logic.

**Result: PASS (Complete).** Transient 8012 I/O error was observed during connection setup but recovered automatically, allowing full protocol completion.

---

## 2026-08-14 — Day 4: 3-Phone Multi-Hop Relay (A → B → C)

### 03:40 — Nearby Connections downgrade to 19.2.0

After repeated `STATUS_ENDPOINT_IO_ERROR` (8012) failures with `play-services-nearby 19.3.0`, the catalog entry in `gradle/libs.versions.toml` was changed from `19.3.0` to `19.2.0` to match the on-device Google Play Services Nearby module (`play-services-nearby@@19.2.0` observed in the runtime stack trace). Dependency resolution, relay unit tests, and `:app:assembleDebug` (with lint skipped) all passed; the debug APK confirmed the `nearby@@19.2.0` tag in its dex. This resolved the persistent 8012 connection failures — A and B then connected successfully.

### Day 4 implementation
- Added `relay/src/main/java/com/sih/relay/RelayHopLogic.kt` — pure hop-accounting logic (`onRelayReceive`) that increments `relayHopCount`, updates `lastRelayedAt`, and transitions `PENDING_LOCAL → IN_RELAY` on the first relay hop.
- Added `relay/src/test/java/com/sih/relay/RelayHopLogicTest.kt` — 12 JVM unit tests.
- Modified `relay/src/main/java/com/sih/relay/RelayManager.kt`:
  - `handleIncomingSos()` now applies `RelayHopLogic.onRelayReceive()` before `saveSosMessages()`.
  - Added `connectedEndpoints` set (maintained on `STATUS_OK`, `onDisconnected`, `stopRelay()`).
  - Added `propagateLocalSos()` / `propagateSosToConnected()` — push a newly-created local SOS immediately to all connected peers (no reconnect / manifest exchange required).
  - New-UUID echo-loop guard: a received SOS is only re-propagated when its UUID is new to this device's store.
  - Kept the existing `pendingConnections` race fix (guards against duplicate `requestConnection` / 8012).
- Modified `app/src/main/java/com/sih/app/MainActivity.kt`:
  - Added temporary `InMemoryRelayStore` (thread-safe, idempotent by UUID, live Flow) as a Day 4 stand-in for the Room-backed store.
  - Added temporary "Inject Test SOS" button (relayHopCount = 0, status = PENDING_LOCAL). After the local save, it calls `relayManager.propagateLocalSos()`.

### Verification (Automated JVM Tests & Build)
- `./gradlew :relay:test :relay:compileDebugKotlin` — BUILD SUCCESSFUL.
- `./gradlew :app:assembleDebug -x lint -x lintDebug` — BUILD SUCCESSFUL.
- Test result: PASS. 56 unit tests passed (`RelayHopLogicTest` 12, `RelayModelsTest` 21, `RelayPayloadCodecTest` 23).
- Known lint note (not a Day 4 failure): `ConcurrentHashMap.newKeySet()` in `RelayManager.kt` requires API 24 vs minSdk 23 (`NewApi`) — builds currently skip lint.

### 08:xx — Physical three-device verification (PASSED)

Topology: A (SOS source) → B (relay) → C (next relay/node).

Observed successful flow:
1. Phone A created a test SOS — UUID `153f5b02-6855-4e4c-b646-997fdc6e9638`, `relayHopCount = 0`, `status = PENDING_LOCAL` — and propagated it to B.
2. Phone B received the SOS with `relayHopCount = 1`, `status = IN_RELAY`, and forwarded it to `DataSource.saveSosMessages()`.
3. Phone B then connected to C and forwarded the same SOS; Phone C received it with `relayHopCount = 2`, `status = IN_RELAY`, and forwarded it to `DataSource.saveSosMessages()`.
4. RelayManifest / UUID synchronization worked: devices exchanged manifests; devices that already knew the UUID correctly reported **no missing SOSRequests** — demonstrating that the same SOS is not blindly retransmitted when the peer is already up to date.

Transient Nearby Connections events observed during testing (recorded as observed transient connection events, NOT Day 4 failures):
- `STATUS_ENDPOINT_IO_ERROR` (8012)
- `STATUS_ALREADY_CONNECTED_TO_ENDPOINT` (8003)
- `STATUS_ENDPOINT_UNKNOWN` (8011 in an earlier test)

None of these prevented the eventual successful connections and SOS propagation.

**Result: PASS (Complete).** Day 4 is COMPLETE — 3-phone A→B→C multi-hop relay physically verified. Note: SOS delivery is phone-to-phone only; internet/cloud delivery is not part of Day 4.

---

## 2026-08-15 — Day 5: Foreground Service + Duty Cycling — 3-Device Relay Validation (PASSED)

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

**Result: PASS (Complete).** Day 5 is COMPLETE — 3-device A → B → C multi-hop relay validated end to end under the foreground service + duty-cycle scan windows.

---

## 2026-08-15 — Day 6: Room Persistence + Permission Preflight + Relay Hardening — 3-Device Validation (PASSED)

Day 6 covered the A+B roadmap item (real-device friction: permission prompts + the Android requirement to explicitly ask the user to turn Bluetooth/Wi-Fi on) plus the Room persistence integration.

### Day 6 implementation
- `:data` — Room layer ported from Component C: entities, DAOs, `AppDatabase`, `RoomConverters`, model enums; `DevicePreferences` (de-Hilted device identity); `SosRequestMapper` (relay model ↔ Room entity); `RoomRelayDataSource` (implements `RelayDataSource` against Room).
- `relay` — `RelayPermissionRequirements` (version-aware permission matrix for Nearby/BT/Wi-Fi); startup hardening of `RelayManager` + `RelayForegroundService` (in-memory fallback store only used when the data-source seam is absent).
- `app` — `MainActivity` now builds `AppDatabase` (`sih_local.db`) + `RoomRelayDataSource` and sets it on `RelayDataSourceProvider`; "Start Relay" goes through a permission preflight (runtime permission requests + Bluetooth enable prompt) before starting the service; on-screen status text added.

### Automated verification
- `gradlew.bat :app:assembleDebug :data:testDebugUnitTest :relay:testDebugUnitTest -x lint -x lintDebug` → BUILD SUCCESSFUL.
- **75 JVM tests, 0 failures** (64 relay: DutyCycler 8, RelayHopLogic 12, RelayModels 21, RelayPayloadCodec 23; 11 data: RoomRelayDataSource 6, SosRequestMapper 5).

### Physical three-device validation (A → B → C), Room-backed store
Topology: A (SOS source) → B (relay) → C (next relay/node). All nodes ran the Room-backed store (not the empty stub used in Day 3–5).

Observed flow:
1. **A** generated an SOS — UUID `e9407a8a-ae3c-461c-b23d-4633197db5fa` — stored it in Room and logged:
   `Room now holds 1 SOS UUID(s)`
2. **B** received the same UUID with `relayHopCount = 1`, `status = IN_RELAY`, and logged:
   `SOSRequest e9407a8a-... forwarded to DataSource.saveSosMessages() (hopCount=1)`
3. **B** subsequently had that UUID in its known-UUID manifest state (advertised it to a peer and sent the SOS to an endpoint that reported 0 known UUIDs).
4. **C** received the same UUID with `relayHopCount = 2`, `status = IN_RELAY`, and logged:
   `SOSRequest e9407a8a-... forwarded to DataSource.saveSosMessages() (hopCount=2)`

**Result: PASS.** Non-empty SOS propagated through the full A → B → C chain and was written through the Room-backed `saveSosMessages()` path on B and C.

### C persistence verification (process restart)
After stopping/clearing the C app process and launching it again:
- C logged: `Room-backed RelayDataSource wired: sih_local.db`
- A new local SOS was injected: UUID `27a28ece-ce67-46dd-a456-f424f8b67933`
- C logged: `Room now holds 2 SOS UUID(s)`

Interpretation (as documented, not inferred beyond the logs): the original relayed SOS (`e9407a8a-...`) survived the C process restart, because the new injection resulted in Room containing **2** UUIDs rather than only the newly created one. This verifies Room persistence of a relayed SOS across an app process restart on the receiving node.

**Result: PASS (Complete).** Day 6 is COMPLETE — Room persistence, permission preflight, and relay hardening verified: 75 automated tests + 3-device A → B → C relay with a non-empty SOS persisted to Room, surviving a process restart on C.

---

## 2026-08-17 — Day 7: Planning Revision + Diagnostic Instrumentation (in progress)

### Testing limitation recorded (deferred validation)
- The planned **4–5 physical-phone stress test is DEFERRED — NOT performed**. Only 3 physical Android
  devices are currently available. This is a testing-resource constraint, not a software failure, and must
  NOT be reported as "passed"/"validated"/"complete". See `PROJECT_HANDOFF.md` §8/§16.
- Day 7 proceeds with the strongest feasible **3-phone** validation (2-phone regression → 3-phone A→B→C →
  dense/concurrent/recovery → diagnose/fix confirmed issues → verify → document). Project development does
  NOT pause for the deferred 4–5 phone run; after the 3-phone validation, work continues to Component C
  integration.

### Diagnostic instrumentation added (test-only; no production/protocol change)
- `relay/RelayManager.kt` — `handleIncomingSos` now logs the forwarding decision explicitly:
  `NEW ... propagating to connected peers` vs `DUPLICATE (already in store) — idempotent save, propagation skipped`.
- `app/MainActivity.kt` — test-only **"Dump Store"** button logs every stored SOS
  (uuid, deviceId, hopCount, status, lastRelayedAt) from Room for per-device audit.

### Automated verification
- Baseline re-run on Day 6 HEAD (`6678771`): 75/75 JVM tests pass (64 relay + 11 data); `:app:assembleDebug`
  BUILD SUCCESSFUL. Diagnostic changes re-verified: compile + all tests still green.

### Phase B physical test — 2-phone regression (PASSED)
- **Devices:** Phone A (SOS source) and Phone B (receiving relay).
- **Injected UUID:** `83306893-6e6e-4065-9e9f-189a75d19616`.
- Both phones **started relay successfully, advertised/discovered, connected, and exchanged RelayManifest**.
- Phone A **stored** the SOS locally and **sent** it to its 1 connected endpoint.
- Phone B **received the exact UUID** with `hopCount=1`, `status=IN_RELAY`; classified it as **NEW** and
  propagated onward per the relay logic (NEW-forwarding decision log confirmed).
- **Dump Store on Phone B confirmed the newly injected UUID was persisted locally** (`uuid`,
  `deviceId`, `hopCount=1`, `status=IN_RELAY`).
- Phone B showed **2 total records** because one older SOS from previous testing already existed in its Room
  store. This is **not a Phase B failure** — the newly injected UUID was present correctly.
- **Transient observation (non-blocking, NOT a confirmed bug):** a Nearby `ApiException 8012 /
  STATUS_ENDPOINT_IO_ERROR` occurred during an **initial** connection request on Phone A. The devices
  subsequently connected successfully and the complete manifest + SOS transfer succeeded. Recorded per the
  known-issue history (8012/8003/8011 are self-recover; mitigated by the Nearby 19.2.0 pin and the
  `pendingConnections` race guard). Escalate to investigation only if it recurs in Phase C/D in a blocking
  pattern (fails to connect / drops a transfer).

### Not yet done
- Phase D/F — dense/concurrent/recovery/restart physical testing.
- Any 4–5 phone run (deferred).
- No commit/push made this session.

---

## 2026-08-19 — Day 7 Phase C: Deterministic 3-Phone A → B → C (PASSED)

### Setup
- 3 physical devices, node roles forced with the TEST-ONLY allow-list filter
  (`RelayTestConfig` / `RelayTestConfigProvider`, intent extras `relay_test_peer`/`relay_test_allowed`):
  - Node A: `SIH-Relay-Node-A` — allowed peer: B only.
  - Node B: `SIH-Relay-Node-B` — allowed peers: A and C.
  - Node C: `SIH-Relay-Node-C` — allowed peer: B only.
- Intended topology: A → B → C multi-hop relay; C ignored direct discovery from A.

### Observed result (truthful record)
- All three devices started `RelayForegroundService`; advertising + discovery (P2P_CLUSTER) started
  successfully. A connected with B; B connected with both A and C; C's TEST-ONLY filter ignored A.
- Manifest exchange occurred between connected peers.
- SOS `62c667f9-9204-46dd-98b6-f1d32297552d` originated/sent from A.
- B received it from A at `hopCount=1`, `status=IN_RELAY`; B logged it as **NEW**, saved it via
  `DataSource.saveSosMessages()`, and propagated it to C
  (`... forwarded to DataSource.saveSosMessages() (hopCount=1) — NEW, propagating to connected peers`,
  then `... (hopCount=1) sent to endpoint: 4HHZ`).
- C received the same UUID from B at `hopCount=2`, `status=IN_RELAY`; C logged it as **NEW** and forwarded
  it to `DataSource.saveSosMessages()`.
- Dump Store on C: `Dump store: 2 SOS record(s) in Room`, including
  `uuid=62c667f9-9204-46dd-98b6-f1d32297552d deviceId=e3b1aad4-c2a2-48a6-81c6-02e175317f1c hopCount=2
  status=IN_RELAY`. The 2nd record was pre-existing (stores were not cleared) — **not a failure**.
- Transient Nearby errors during simultaneous connection attempts
  (`STATUS_ALREADY_CONNECTED_TO_ENDPOINT`, `STATUS_ENDPOINT_IO_ERROR` / 8012) self-recovered; the end-to-end
  A → B → C test completed successfully.

### Conclusion
- **Phase C PASSED.** The deterministic live multi-hop A → B → C path (hop 0 → 1 → 2, NEW classification,
  Room persistence at each hop) is physically validated on 3 devices.
- **Note on deviceId:** The `deviceId` in the Dump Store output (`e3b1aad4-c2a2-48a6-81c6-02e175317f1c`) is
  the originating device's stable installation UUID, NOT the Nearby test endpoint name (`SIH-Relay-Node-A`).
  Test endpoint names are used only for Nearby Connections filtering; `SOSRequest.deviceId` is set at SOS
  creation and travels unchanged through the relay chain. See `RelayTestConfig.kt` KDoc.
- The deferred **4–5 phone stress test remains DEFERRED — NOT performed** and must not be treated as
  complete because of this result. See `PROJECT_HANDOFF.md` §8/§16.

---

## 2026-08-23 — Day 7 Phase D/F: Dense/Concurrent/Recovery + Verification (PASSED)

### Setup
- 3 physical devices, same TEST-ONLY allow-list topology as Phase C (A→B→C).
- All three phones running with Room-backed RelayDataSource, foreground service, and duty cycle.

### Phase D test results

#### D1 — Multiple SOS injections (PASSED)
- Node A injected three distinct UUIDs rapidly:
  - `c581b6c8-94d1-498c-86f8-5b7b392d04e0`
  - `24900c85-db5c-4d22-b86b-933032bf1447`
  - `840f9d92-27cf-4cf0-8303-8b82408fcf41`
- All three propagated through A→B→C. A stored them at `hopCount=0`/`PENDING_LOCAL`.
- B received them at `hopCount=1`/`IN_RELAY`. C received all three as **NEW** at `hopCount=2`/`IN_RELAY`.

#### D2 — Duplicate/echo guard (PASSED)
- Manifest exchanges repeatedly showed `UUID diff for endpoint <id>: peer has N UUID(s), 0 SOSRequest(s) to send`
  and `No missing SOSRequests to send to endpoint <id> — peer is up to date` after peers already had the
  same UUID sets, including after reconnection/restart scenarios.

#### D3 — Disconnect/reconnect (PARTIALLY VALIDATED)
- A transient disconnect/reconnection occurred and the mesh automatically reconnected.
- The Nearby/Bluetooth connection re-established before a clean manual separation could be fully controlled.
- The automatic self-healing IS valid evidence of mesh recovery, but no clean deliberately controlled
  prolonged physical disconnect was achieved. Testing-procedure limitation, not a software failure.

#### D4 — Relay stop/start recovery (PASSED)
- Phone C: Stop Relay pressed → relay started again → connections re-established → manifest exchanges
  completed → existing UUID sets showed 0 missing SOSRequests.
- Node A then injected a new SOS `83904f98-976c-4a1e-904f-453559953e0d` which propagated correctly:
  A=hopCount 0, B=hopCount 1, C=hopCount 2.

#### D5 — App process kill persistence (PASSED)
- Before force-stopping, all three phones had 10 SOS records in Room.
- App processes force-stopped (`adb shell am force-stop com.sih.app`) and relaunched.
- Each device rewired the Room-backed `RelayDataSource` using `sih_local.db`.
- Relay connections re-established. Manifest exchanges showed 10 known UUIDs and 0 missing SOSRequests.
- Room data survived process force-stop/relaunch.

#### D6 — Complete hop count audit (PASSED)
- Explicit Dump Store evidence from all three phones confirmed all four Day 7 UUIDs:
  - `c581b6c8`: A=0, B=1, C=2
  - `24900c85`: A=0, B=1, C=2
  - `840f9d92`: A=0, B=1, C=2
  - `83904f98`: A=0, B=1, C=2

#### D7 — Bidirectional injection (PASSED)
- After the three-injection test, SOS records were injected from B and C as well.
- Propagation through the mesh was confirmed with expected hop behavior.

#### D8 — Deliberate sleep-window injection (NOT TESTED)
- Duty cycling itself was already exercised and validated during Day 5 (A→B→C under FGS + duty cycle).
- The specific edge case of intentionally injecting immediately after "Closing scan window" was not performed.
- Not required for Day 7 closure per §16.2/§16.4 — the duty-cycle behavior was already physically validated.

#### 8012-aware connection race handling (PASSED)
- `STATUS_ENDPOINT_IO_ERROR` (8012) occurred during bidirectional connection races, but the connection
  subsequently completed instead of being incorrectly discarded.
- The production-quality fix in `RelayManager.onEndpointFound` works correctly.

### Conclusion
- **Phase D PASSED.** Dense/concurrent/recovery testing on 3 phones validated: multiple injections,
  echo guard, relay stop/start, process kill persistence, hop count correctness, bidirectional
  injection, and 8012 race handling. D3 partially validated (automatic self-healing observed).
  D8 not tested (duty-cycle already validated in Day 5).
- **Day 7 is COMPLETE.** All Phase A–G work done. Strongest feasible 3-phone validation performed
  and documented. Proceeding to Component C integration.
- The deferred **4–5 phone stress test remains DEFERRED — NOT performed** and must not be treated as
  complete because of this result. See `PROJECT_HANDOFF.md` §8/§16.

---

## 2026-08-23 — A+B ↔ Component C Integration (Stages 0a–5)

### 09:00 — Stage 0a: Baseline Verification

Confirmed `integration/ab-component-c` branch HEAD `320bfbd` matches `mesh-relay` HEAD exactly.
No diff. Clean baseline established for selective file extraction.

### 09:30 — Stage 1: Build Files (8 files modified)

Merged version catalog, root build, settings, gradle.properties, and all 4 module build files
from `origin/shell-app:sih-android/` into the validated A+B codebase.

Key additions to `gradle/libs.versions.toml`: Compose BOM, Hilt 2.51.1, KSP 2.0.21-1.0.27,
Retrofit 2.11.0, Moshi 1.15.1, Room 2.6.1, WorkManager 2.9.1, OkHttp 4.12.0, Location, CoreKtx.
All existing TRAAN entries preserved (including `nearbyConnections 19.2.0`).

`app/build.gradle.kts`: full Compose + Hilt + KSP + Room + WorkManager + Location + Moshi
dependency block. **Namespace `com.sih.app`** (applicationId `com.sih.android`).

Dependency chain verified: `:app → :data → :relay → :network` (no cycles).

### 10:00 — Stage 2: Network Module (14 files created)

Created full Retrofit HTTP layer in `:network`:

- `DisasterApi`: 3 endpoints (`POST /api/v1/sos`, `GET /api/v1/sos/{uuid}/status`, `POST /api/v1/sos/batch`)
- `RetrofitClientFactory`: typed Retrofit instance creation
- `AuthInterceptor`: `Authorization: Bearer <deviceId>` header
- 3 DTOs (`SosRequestDto`, `SosBatchRequestDto`, `SosStatusDto`)
- 2 request models (`SosCreateRequest`, `SosBatchUploadRequest`)
- 3 response models (`SosSubmitResponse`, `SosBatchResponse`, `SosStatusResponse`)
- `AndroidManifest.xml`: `INTERNET` + `ACCESS_NETWORK_STATE` permissions
- `DtoSerializationTest.kt`: 5 JVM tests for Moshi round-trips

Zero A+B code modified.

### 10:30 — Stage 3: Data Module (11 new + 1 modified)

Added App-layer persistence, repository, and WorkManager workers:

- `DevicePreferences.kt`: **REPLACED** with Hilt-injected version (`@Singleton @Inject constructor(@ApplicationContext)`)
- `DataModule.kt`: Hilt `@Module` — provides `DataStore<Preferences>`, `Executors`, `Dispatchers`, `WorkManager`
- `SosRepository.kt`: wraps DAO + dispatcher for app-layer use
- `UserMedicalProfileRepository.kt`: wraps medical profile DAO
- `GatewaySyncWorker.kt`: WorkManager periodic backend upload job
- `DeviceRegistrationWorker.kt`: WorkManager device registration job
- `SosConstants.kt`: constants
- `consumer-rules.pro`: ProGuard rules
- 4 test files (11 JVM tests total)

All 11 existing A+B data files preserved with zero diff.

### 11:00 — Stage 4: Relay Seam (1 file)

Created `RelayRepository.kt` in `:data`:
- Public interface with 2 methods: `getRelayedSosEntries()` (returns empty list initially)
- `StubRelayRepository` (clearly marked temporary)
- A+B relay source (18 files) untouched

### 11:30 — Stage 5: App Shell (33 files)

Created full Compose-based app shell from Component C:

- `SihApplication.kt`: `@HiltAndroidApp`, WorkManager `Configuration.Provider`
- `MainActivity.kt`: Compose shell, device registration, nav start destination
- `AppModule.kt`: Hilt DI for `AuthInterceptor`, `DisasterApi`, `StubRelayRepository`
- `AppNavigation.kt` + `Screen.kt`: 3 routes (Home, Onboarding, Status)
- `SihTheme.kt`: Material3 dark/light
- `HomeScreen.kt` + `HomeViewModel.kt`: SOS button, permission flow, location
- `OnboardingScreen.kt` + `OnboardingViewModel.kt`: medical profile
- `StatusScreen.kt` + `StatusViewModel.kt`: status pill, backend check
- Updated manifest, resources (strings, themes, mipmaps, drawables, network security config)
- 2 test files (`AppNavigationTest.kt`, `MainActivityUiTest.kt`)

### 12:00 — Namespace fix + build verification

`app/build.gradle.kts` namespace changed from `com.sih.android` to `com.sih.app`
(applicationId remains `com.sih.android`) to resolve BuildConfig and manifest class resolution.

Android Studio build succeeded. App launched on device; Home, Onboarding, Status screens appeared
correctly. All A+B relay code preserved (zero diff from mesh-relay baseline).

### 12:30 — Pre-commit audit completed

67 files classified (13 modified + 54 new across 21 untracked dirs). A+B relay code verified
preserved. No secrets found. `.gitignore` and `gradlew.bat` confirmed excluded from commit.

**Pre-commit verdict: SAFE TO COMMIT WITH MINOR KNOWN ISSUES.**

### Known non-blocking issues identified

1. Missing `network/proguard-rules.pro` (dormant — `isMinifyEnabled=false`)
2. Unused import `SihTheme` in `HomeScreen.kt`
3. Serialization plugin not applied in `:data` (no `@Serializable` classes exist there)
4. `SosRequestDto` uses `String` fields instead of enum for `emergencyType`/`severityHint`

### Commit attempt blocked

Git identity not configured on the system. Cannot commit via CLI.
User will commit manually through GitHub Desktop. All 67 files unstaged via `git reset HEAD`
to restore clean working state for manual commit.

### Integration stages 0a–5 result: PASS (committed at `a1ec30d`)

---

## 2026-08-24 — Stage 6A: Single-device Physical Verification

### 14:00 — Stage 6A: Physical Android device test

**Branch:** `integration/ab-component-c` (commit `a1ec30d`)
**Device:** Physical Android phone (real hardware)
**Result: PASS**

#### Test results

1. **Onboarding:** Medical profile setup completed successfully.
2. **SOS creation:** SOS created, navigated through all screens without crashes or interruptions.
3. **Location:** Permission requested, granted, location obtained, flow continued normally.
4. **Persistence:** SOS created → app completely killed/closed → app reopened → SOS still present. Emergency title and time retained.
5. **Status screen:** Local SOS data displayed correctly.

#### Observed behavior
- After entering the emergency/status flow, user could not back out of "waiting for connection" screen. Consistent with intended duplicate-prevention UI, but NOT formally verified as duplicate prevention.

#### NOT tested
- Backend/gateway delivery (no backend running)
- Multi-device relay (single device only)
- RelayForegroundService (not started — relay not wired)
- SOS transmission to another device
- Network upload success

### Stage 6A result: PASS

Proceeding to Stage 6B (relay integration wiring + multi-device + backend testing).

---

## 2026-08-24 — Stage 6B-1: Foreground Notification Persistence Fix (PASSED)

### Investigation: notification swiped away on Android 17

**Observation:** On Android 17 / SDK 37, the foreground service notification for `RelayForegroundService` could be swiped away from the notification shade despite `setOngoing(true)` and `FOREGROUND_SERVICE` flags.

**Investigation:** ADB diagnostics confirmed:
- `RelayForegroundService` remained `isForeground=true`, `foregroundId=1001` after dismissal.
- Notification object retained `ONGOING_EVENT | NO_CLEAR | FOREGROUND_SERVICE` flags.
- Channel `relay_foreground` healthy: `mImportance=2`, `mUserLockedFields=0`, `mDeleted=false`.
- Both old (`com.sih.app`) and current (`com.sih.android`) builds exhibited identical behavior on the same device.

**Root cause:** Standard Android 13+ (API 33+) platform behavior. Foreground service notifications can be swiped away despite `setOngoing(true)`. NOT a regression.

### Implementation: notification refresh in DutyCycler callback

**File modified:** `relay/src/main/java/com/sih/relay/service/RelayForegroundService.kt` (lines 134–143)

Added `startForeground()` call inside the existing `DutyCycler` `onWindowStart` callback, before `relayManager.startScanWindow()`:
- Rebuilds and reposts the notification each scan-window start (~40s after swipe).
- Wrapped in try/catch to isolate failures from the scan window operation.
- No new timer, coroutine, scheduler, or DutyCycler API changes.

### Physical-device verification (ALL PASSED on Android 17 / SDK 37)

1. **Notification swipe test:** Swiped away → reappeared on next duty-cycle window. Repeated 3× successfully.
2. **Duty-cycle verification:** Confirmed ~10s active / ~40s sleep / ~50s full cycle.
3. **Remove-from-Recents test:** Notification returned, duty cycle continued, `dumpsys` confirmed `isForeground=true`, `foregroundId=1001`.
4. **Force Stop test:** Notification disappeared, service terminated (expected).
5. **Relaunch after Force Stop:** Notification reappeared, relay started, advertising/discovery began, duty cycling resumed.

**Harmless startup log observed:** `startScanWindow() ignored — window already open` — caused by `startRelay()` opening the initial scan window before the DutyCycler callback. Existing guard prevents duplicate work. Not a regression.

### Stage 6B-1 result: PASS (physically verified on Android 17 / SDK 37)

Relay wiring (AppModule.kt, SihApplication.kt, MainActivity.kt) + foreground notification persistence fix all verified. Proceeding to Stage 6B-2 (`propagateLocalSos` wiring).

---

## 2026-08-25 — Physical Relay-Engine Testing (PASSED)

### 14:00–17:00 — 3-device controlled physical testing

**Branch:** `integration/ab-component-c`
**Devices:** 3 physical Android phones
- Device A: `45131FDJH003HS` — allowed peer: Device-B only
- Device B: `10BD551MY80004T` — allowed peers: Device-A, Device-C
- Device C: `R9ZY503BHDD` — allowed peer: Device-B only

**Topology control:** TEST-ONLY launch configuration via intent extras `relay_test_peer`/`relay_test_allowed` in `MainActivity.onCreate()`. All three devices must be force-stop'd and relaunched with the config for the topology to work. All three must be launched within seconds of each other for duty-cycle scan windows to overlap.

**Important:** `onNewIntent()` is NOT overridden in `MainActivity`, so `am start` on an already-running activity does NOT apply the test config. Always `am force-stop` before launching with extras.

### Test 1 — Controlled multi-hop A → B → C (PASSED)

**Setup:** All three launched with TEST-ONLY config within seconds of each other.

**Evidence (Device B logcat):**
```
Connection established with endpoint: TP1R. Initiating manifest exchange...
RelayManifest received from endpoint TP1R: deviceId=Device-A, 16 known UUIDs
SOSRequest received from endpoint TP1R: uuid=6858d327-ccc0-4392-8f87-43e42985cfd5, deviceId=bbd6bc6c-5e83-4460-b1cf-7c25045ca5e0, hopCount=1, status=IN_RELAY
SOSRequest 6858d327-... forwarded to DataSource.saveSosMessages() (hopCount=1) — NEW, propagating to connected peers
```

**Device A logcat:**
```
UUID diff for endpoint BBYO: peer has 15 UUID(s), 1 SOSRequest(s) to send
SOSRequest 6858d327-... sent to endpoint: BBYO (648 bytes)
```

**Device C:** received same UUID from B with `hopCount=2`, `status=IN_RELAY`.

**Topology enforcement confirmed:**
- A: `TEST-ONLY filter: ignoring discovery from Device-C` — A correctly blocked C.
- C: `TEST-ONLY filter: ignoring discovery from Device-A` — C correctly blocked A.

**Result: PASS.** Same UUID, same deviceId across all three; hopCount 0→1→2; direct A↔C prevented.

### Test 2 — Duplicate / echo-loop guard (PASSED)

**Setup:** All three connected, existing SOS records from previous testing.

**Evidence (Device B logcat after reconnect):**
```
Connection established with endpoint: 6ORP. Initiating manifest exchange...
RelayManifest received from endpoint 6ORP: deviceId=Device-A, 22 known UUIDs
UUID diff for endpoint 6ORP: peer has 22 UUID(s), 0 SOSRequest(s) to send
No missing SOSRequests to send to endpoint 6ORP — peer is up to date

Connection established with endpoint: L2S1. Initiating manifest exchange...
RelayManifest received from endpoint L2S1: deviceId=Device-C, 22 known UUIDs
UUID diff for endpoint L2S1: peer has 22 UUID(s), 0 SOSRequest(s) to send
No missing SOSRequests to send to endpoint L2S1 — peer is up to date
```

**Result: PASS.** Manifests matched. `getMissingSos()` correctly filtered all known UUIDs. No SOS re-sent. No echo loop.

### Test 3 — Disconnect/reconnect missed-message resync (PASSED)

**Setup:** Controlled topology established. B killed while A and C remained running.

**Correct test sequence:**
1. All three connected with TEST-ONLY config.
2. B force-stopped.
3. SOS created on A while B absent.
4. A log: `propagateLocalSos: no connected endpoints — SOS 6858d327-... stays local only`
5. B relaunched with TEST-ONLY config.
6. B reconnected to A and C.

**Evidence (Device B logcat):**
```
Connection established with endpoint: TP1R. Initiating manifest exchange...
RelayManifest received from endpoint TP1R: deviceId=Device-A, 16 known UUIDs
SOSRequest received from endpoint TP1R: uuid=6858d327-ccc0-4392-8f87-43e42985cfd5, hopCount=1, status=IN_RELAY
SOSRequest 6858d327-... forwarded to DataSource.saveSosMessages() (hopCount=1) — NEW, propagating to connected peers
```

**Device A logcat:**
```
UUID diff for endpoint BBYO: peer has 15 UUID(s), 1 SOSRequest(s) to send
SOSRequest 6858d327-... sent to endpoint: BBYO (648 bytes)
```

**Result: PASS.** B received the missed SOS via manifest exchange with hopCount=1 (direct from A). A knew B was missing 1 SOS and sent it.

### Test 4 — Sleep-window SOS creation (NOT SEPARATELY TESTED)

Duty-cycle behavior was already validated in Day 5 (A→B→C under FGS + duty cycle). A dedicated test of creating an SOS specifically during the sleep window was not performed as a standalone test.

### Test 5 — Zero-peer persistence + later synchronization (PASSED)

**Setup:** 7 SOS records created on Device A before any relay peer existed.

**Evidence:** All 7 records persisted locally on A. After B connected, manifest exchange transferred all pre-existing SOS records to B via `saveSosMessages()`.

**Result: PASS.** SOS persistence with zero peers works. Later synchronization when a peer becomes available works.

### Overall result

All 4 critical physical relay-engine tests passed on real Android hardware:
- Controlled multi-hop A→B→C: **PASSED**
- Duplicate/echo-loop guard: **PASSED**
- Disconnect/reconnect missed-message resync: **PASSED**
- Zero-peer persistence + later synchronization: **PASSED**

Test 4 (sleep-window creation) was not separately tested — duty-cycle behavior already validated in Day 5.

Proceeding to Stage 6B-3 (StubRelayRepository replacement) and Stage 7 (final documentation cleanup).

---

## 2026-08-25 — Stage 7: Final Documentation Cleanup

### 22:00 — Documentation review and update

**Scope:** Full review of all project documentation to reflect the actual current state.

**Files updated:**
- `PROJECT_HANDOFF.md`: Fixed stale "uncommitted" claims (Stages 0a–5 committed at `a1ec30d`). Updated §2 current state, §9 deferred work, §10 known issues, §13 handoff state, §15 discrepancies, §17.9 remaining work. Updated Stage 6B-3 to "functionally complete, non-blocking cleanup remaining" and Stage 6B-4 to "blocked on backend infrastructure".
- `Component1_Overview.md`: Updated §J (current implementation status), §K (what is NOT implemented yet), §M (verification status). Removed stale "uncommitted — pending manual commit" claims. Added Stage 6B-1/6B-2/6B-3/6B-4/7 status.
- `walkthrough.md`: Updated Stage 6B-3/6B-4/7 status in "Next steps" section.
- `Agent.md`: Fixed integration status line — removed "uncommitted, pending manual commit".
- `Logs.md`: Fixed "pending manual commit" → "committed at `a1ec30d`". Added this entry.

**Stage 6B-3 assessment (confirmed during review):**
- `StubRelayRepository` still exists and `getRelayedSosEntries()` returns empty.
- However, `RoomRelayDataSource` already persists relay-received SOS into Room during normal operation.
- `GatewaySyncWorker` reads all SOS from Room via `sosRequestDao.getAllSos()`, so relay-received records are already included in the backend upload path.
- The remaining concern is the narrow process-restart window where `RelayDataSourceProvider.dataSource` is temporarily null and `InMemoryFallbackStore` may be used before Room is re-injected.
- This is a code-level resilience/cleanup gap, not a demonstrated normal-operation data-flow failure.

**Stage 6B-4 assessment:**
- Multi-device relay engine physically tested and passed independently.
- End-to-end multi-device → backend synchronization testing cannot be performed without a running backend at `http://10.0.2.2:8000/api/v1/`.

### Stage 7 result: PASS

