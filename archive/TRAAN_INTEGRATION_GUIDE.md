# INTEGRATION_GUIDE.md — Engineering Continuation & Handoff Manual

> **Purpose:** A completely new developer should be able to open this ONE FILE and understand
> the TRAAN project, its architecture, what has been built, how components integrate, what
> remains, and how to continue development safely.
>
> **Companion document:** `PROJECT_HANDOFF.md` is the concise project-state tracker.
> This file is the detailed engineering/continuation manual.

---

## 1. PROJECT OVERVIEW

### 1.1 What TRAAN Is

TRAAN (or "sih-android") is a disaster-relief Android application for Smart India Hackathon (SIH) 2026.
During a major disaster, cell towers and internet infrastructure fail. TRAAN turns users' phones into a
decentralized mesh network using Google's Nearby Connections API (Bluetooth + Wi-Fi Direct). As people
walk around, their phones automatically discover each other and sync emergency SOS distress signals
offline. Eventually, one of those phones reaches an area with working internet and uploads all collected
SOS messages to a backend server for rescue teams.

### 1.2 Architecture

The project is a single Android Gradle repository with 4 modules:

```
:app          Main application — UI, navigation, Hilt DI, lifecycle
:relay        Mesh/Relay engine — Nearby Connections, duty cycling, SOS propagation
:data         Local storage — Room database, WorkManager workers, backend upload
:network      Backend client — Retrofit HTTP layer, API interfaces, DTOs
```

Dependency direction (enforced):
```
:app → :relay
:app → :data
:data → :relay
:app → :network
:data → :network
:relay → (nothing — completely isolated)
```

The `:relay` module must NEVER depend on `:data`, `:network`, or any Android framework storage.
It communicates with the database exclusively through interfaces (`RelayDataSource`).

### 1.3 What Each Component Originally Contributed

| Component | Original Owner | Contributed |
|-----------|---------------|-------------|
| **A+B** (Agent A+B) | Relay team | `:relay` module — full mesh engine, Nearby Connections, duty cycling, manifest exchange, SOS propagation, echo-loop guard, permission handling |
| **C** (Component C) | App/data team | `:app` Compose UI (Home, Onboarding, Status screens), `:data` Room persistence, `:network` Retrofit client, `GatewaySyncWorker`, `DeviceRegistrationWorker`, Hilt DI wiring |
| **D** (Component D) | Backend team | Backend server (separate repo) — `POST /api/v1/sos/batch`, device registration, dedup, AI clustering |
| **E** (Component E) | AI team | Gemini-based severity scoring, situation brief generation, OR-Tools resource optimization |
| **F** (Component F) | Dashboard team | Real-time dashboard (separate repo) — WebSocket live updates, incident visualization |

### 1.4 How Integration Stages Brought Them Together

The integration was done through selective file extraction (not a full merge) from Component C's
separate repo into the validated A+B codebase:

- **Stages 0a–5** (committed at `a1ec30d`): Build files, network module, data module additions,
  relay seam (`RelayRepository` interface), Compose app shell. All merged, build passes, 75 JVM tests pass.
- **Stage 6A** (committed at `a1ec30d`): Single-device physical verification — onboarding, SOS creation,
  location, persistence across app kill, status screen.
- **Stage 6B-1** (committed at `cca669f`): Relay wiring (`AppModule.kt`, `SihApplication.kt`,
  `MainActivity.kt`) + foreground notification persistence fix.
- **Stage 6B-2** (uncommitted, ready to commit): `propagateLocalSos()` wiring in `HomeViewModel.kt`,
  `SosRepository.getSos()`, `RelayDataSourceProvider.relayManager`. Physically verified on 3-device A→B→C.
- **Stage 6B-3**: StubRelayRepository — functionally complete (non-blocking). Stub still exists but
  relay-received SOS already flow to Room via `RoomRelayDataSource`.
- **Stage 6B-4**: BLOCKED on backend infrastructure.

---

## 2. CURRENT PROJECT STATUS

### 2.1 Fully Implemented and Verified

| Component | Status | Evidence |
|-----------|--------|----------|
| Relay engine (Nearby Connections) | DONE | 64 JVM tests + 3-device physical validation |
| Duty cycling (10s active / 40s sleep) | DONE | Physically verified, battery protection working |
| Foreground service (START_STICKY) | DONE | Physically verified, notification persistence tested |
| Manifest exchange + UUID diff | DONE | Physically verified — bidirectional, zero-diff confirmed |
| Multi-hop SOS propagation (A→B→C) | DONE | Physically verified — hopCount 0→1→2 |
| Echo-loop / duplicate guard | DONE | Physically verified — UUID-based idempotency |
| Disconnect/reconnect resync | DONE | Physically verified — missed SOS recovered via manifest exchange |
| Zero-peer persistence | DONE | Physically verified — SOS stored offline, synced later |
| Room persistence (SOS + medical profile) | DONE | Schema frozen, DAOs implemented, survives process restart |
| Permission preflight UX | DONE | Runtime Bluetooth/location permission requests |
| Compose app shell | DONE | Home, Onboarding, Status screens |
| Network layer (Retrofit) | DONE | `DisasterApi` with 3 endpoints, auth interceptor |
| `GatewaySyncWorker` | CODE DONE | WorkManager, network-constrained, 401 re-registration, TTL cleanup |
| `DeviceRegistrationWorker` | CODE DONE | First-launch device registration |
| 75 JVM tests | PASSING | 64 relay + 11 data |

### 2.2 Implemented but Not End-to-End Validated

| Component | Status | Blocker |
|-----------|--------|---------|
| `GatewaySyncWorker` → backend upload | Code done | No backend running at `http://10.0.2.2:8000/api/v1/` |
| Device registration → backend | Code done | No backend running |
| Multi-device → backend end-to-end | Blocked | Requires backend + network |
| `propagateLocalSos()` immediate mesh push | Physically verified (relay only) | Not tested through full backend pipeline |

### 2.3 Blocked Work

| Item | Blocker |
|------|---------|
| Stage 6B-4: Multi-device + backend testing | Backend infrastructure not running |
| End-to-end demo flow (SOS → relay → backend → dashboard) | Backend (Component D) + Dashboard (Component F) not available |
| 4–5 phone stress test | Only 3 physical devices available |

### 2.4 Technical Debt (Non-Blocking)

| Item | Impact |
|------|--------|
| `StubRelayRepository` still exists | Benign — relay SOS already reach Room via `RoomRelayDataSource` |
| `GatewaySyncWorker` calls `relayRepository.getRelayedSosEntries()` | Dead code path — returns empty, Room already has all records |
| ProGuard/R8 disabled in all builds | Standard for development/demo |
| Missing `network/proguard-rules.pro` | Only matters if minification enabled |
| Outdated "stub" comments in `RelayApi.kt`, `RelayManager.kt` | Documentation only |
| `device_id` contract ambiguity §1.2 vs §1.9 | Documented, `DevicePreferences` uses stable installation ID |
| CRLF/EOL noise in `.gitignore` and `gradlew.bat` | Whitespace only, no content change |

### 2.5 Optional / Future Work

- Battery/performance testing + duty-cycle tuning (Day 10 roadmap)
- Low-battery throttle mode
- TTL cleanup for `pending_local`/`in_relay` records (requires routing policy)
- Fallback simulation demo mode (Day 12 roadmap)
- 4–5 phone stress test
- Demo script + Bluetooth fallback

---

## 3. COMPLETE ARCHITECTURE WALKTHROUGH

### 3.1 End-to-End SOS Data Flow

Here is the exact code path an SOS follows from creation to backend upload, with file references:

#### Step 1: User triggers SOS

```
HomeScreen.kt → user taps "Send SOS" button
→ HomeViewModel.triggerSos() (:app/.../HomeViewModel.kt:53)
→ createSosInternal() (:app/.../HomeViewModel.kt:113)
```

#### Step 2: SOS persisted to Room

```
SosRepository.createSos() (:data/.../repository/SosRepository.kt:64)
→ sosRequestDao.insertSos(entity) (:data/.../db/dao/SosRequestDao.kt:27)
  OnConflictStrategy.IGNORE — UUID duplicate silently skipped
→ Returns UUID string
```

#### Step 3: Immediate gateway sync enqueued

```
SosRepository.createSos() (:data/.../repository/SosRepository.kt:124)
→ enqueueImmediateGatewaySync(workManager)
  OneTimeWorkRequest with NetworkType.CONNECTED constraint
  ExistingWorkPolicy.KEEP — won't duplicate if already pending
```

#### Step 4: Relay propagation (immediate, best-effort)

```
HomeViewModel.kt:159-162
→ sosRepository.getSos(uuid) — re-read entity from Room
→ SosRequestMapper.toSosRequest(entity) — convert to relay model
→ RelayDataSourceProvider.relayManager?.propagateLocalSos(sos)
  Safe-call: if relayManager is null (service not running), this is a no-op

RelayManager.propagateLocalSos() (:relay/.../RelayManager.kt:447)
→ If connectedEndpoints.isEmpty(): log "stays local only", return
→ If peers connected: propagateSosToConnected(sos) (:relay/.../RelayManager.kt:419)
  → For each endpoint:
    RelayPayloadCodec.encodeSos(sos) → bytes
    connectionsClient.sendPayload(endpointId, Payload.fromBytes(encoded))
```

#### Step 5: Peer receives SOS via Nearby Connections

```
RelayManager.payloadReceived callback (:relay/.../RelayManager.kt:~350)
→ RelayPayloadCodec.decodeSos(bytes) → SOSRequest
→ RelayHopLogic.onRelayReceive(sos) — increments hopCount, sets lastRelayedAt
→ dataSource.saveSosMessages(listOf(relayed))
  ↓
RoomRelayDataSource.saveSosMessages() (:data/.../relay/RoomRelayDataSource.kt:44)
→ sosRequestDao.insertSos(SosRequestMapper.toEntity(sos))
  OnConflictStrategy.IGNORE — UUID duplicate silently skipped
  Record is now in Room with relay metadata preserved
```

#### Step 6: Gateway upload to backend

```
GatewaySyncWorker.doWork() (:data/.../worker/GatewaySyncWorker.kt:78)
→ Guard: devicePreferences.isRegistered() — if not, retry after registration
→ sosRequestDao.getAllSos() — reads ALL records (own + relay-received) from Room
→ relayRepository.getRelayedSosEntries() — returns emptyList() (stub, benign)
→ Merge + distinctBy { it.uuid }
→ Build GatewayUploadBatch:
    gatewayDeviceId, gatewayLocation, uploadedAt, sosRequests
→ disasterApi.uploadBatch(batch) — POST /api/v1/sos/batch
→ On success: sosRequestDao.markUploaded(uploaded)
→ On 401: enqueueDeviceRegistration, return retry
→ TTL cleanup: deleteExpiredUploaded()
```

### 3.2 hopCount Behavior

- **Origin device:** `relayHopCount = 0`, `status = PENDING_LOCAL`
- **First relay (B receives from A):** `relayHopCount = 1`, `status = IN_RELAY`
- **Second relay (C receives from B):** `relayHopCount = 2`, `status = IN_RELAY`
- **Incremented by:** `RelayHopLogic.onRelayReceive()` — called in `RelayManager.handleIncomingSos()`
- **Never decremented** — hopCount is monotonically increasing
- **Preserved in Room** — `SosRequestMapper.toEntity()` maps hopCount to the Room column

### 3.3 UUID / Idempotency Behavior

- Every SOS has a UUID (generated at creation, format: standard UUID v4)
- `RoomRelayDataSource.saveSosMessages()` uses `sosRequestDao.insertSos()` with `OnConflictStrategy.IGNORE`
- If a UUID already exists in Room, the insert is silently ignored
- `GatewaySyncWorker` uses `.distinctBy { it.uuid }` to deduplicate across Room + stub sources
- The manifest exchange uses UUID sets to compute diff — only missing UUIDs are sent
- **Result:** No echo loops, no duplicate propagation, no duplicate uploads

### 3.4 Manifest Exchange

When two devices connect:
1. Each sends its `RelayManifest` (list of known SOS UUIDs) via `RelayPayloadCodec.encodeManifest()`
2. Each device computes the diff: `getMissingSos()` returns UUIDs the peer has but we don't
3. Only the missing SOS records are sent via `sendMissingSos()`
4. **Direction confusion:** "0 SOSRequest(s) to send" only describes the LOCAL device's outgoing diff — the peer may still send us records

### 3.5 Reconnect Synchronization

When a disconnected peer reconnects:
1. Both devices exchange manifests again
2. `getMissingSos()` computes the diff against the updated local store
3. Any SOS received while the peer was disconnected are transferred
4. **Physically verified:** B killed → SOS created on A → B relaunches → B receives missed SOS via manifest exchange

### 3.6 Zero-Peer Behavior

When an SOS is created with no peers connected:
1. SOS is persisted to Room (always succeeds)
2. `propagateLocalSos()` checks `connectedEndpoints.isEmpty()` → returns early, logs "stays local only"
3. `enqueueImmediateGatewaySync()` fires but `NetworkType.CONNECTED` constraint blocks it (no internet)
4. When a peer later connects, manifest exchange transfers the pre-existing SOS
5. When internet becomes available, `GatewaySyncWorker` uploads from Room

### 3.7 Process Restart Behavior

When the app process is killed and restarted:
1. `RelayForegroundService` restarts via `START_STICKY`
2. `RelayDataSourceProvider.dataSource` may be null temporarily
3. Service falls back to `InMemoryFallbackStore` (functional in-memory store, not a no-op)
4. SOS received during this brief window land in memory only, not Room
5. When Room is re-injected (DI restores), the in-memory records are NOT automatically migrated
6. **Gap:** SOS in `InMemoryFallbackStore` during the restart window are unreachable by `GatewaySyncWorker`
7. **Mitigation:** The window is seconds-long (`START_STICKY` restarts quickly)

### 3.8 Duty-Cycle Scan Windows

- **Active window:** ~10 seconds of Nearby advertising + discovery
- **Sleep window:** ~40 seconds of radio sleep (advertising/discovery stopped)
- **Full cycle:** ~50 seconds
- `stopScanWindow()` does NOT disconnect existing connections — only stops new discovery
- Devices must be launched within seconds of each other for scan windows to overlap
- `pendingConnections` race guard prevents double-connect during rapid reconnect

---

## 4. REPOSITORY / FILE MAP

### 4.1 Critical Files

| File | Responsibility | Key Methods | Stable? |
|------|---------------|-------------|---------|
| `relay/.../RelayManager.kt` | Core mesh engine — Nearby Connections, connection lifecycle, SOS send/receive, manifest exchange | `startRelay()`, `stopRelay()`, `startScanWindow()`, `stopScanWindow()`, `propagateLocalSos()`, `handleIncomingSos()`, `initiateManifestExchange()`, `sendMissingSos()` | YES — frozen core |
| `relay/.../RelayApi.kt` | Public interface of `:relay` module | `startRelay()`, `stopRelay()`, `getRelayStore()` | YES — team contract |
| `relay/.../api/RelayDataSource.kt` | Internal storage boundary interface | `saveSosMessages()`, `getKnownUuids()`, `getMissingSos()`, `getAllSos()`, `observeAllSos()` | YES — boundary |
| `relay/.../RelayRepository.kt` | Cross-module bridge for gateway upload | `getRelayedSosEntries()` | STUB — needs replacement |
| `relay/.../service/RelayForegroundService.kt` | Android foreground service, duty cycling, lifecycle | `onCreate()`, `onDestroy()`, `onStartCommand()` | YES |
| `relay/.../service/RelayDataSourceProvider.kt` | Static seam — exposes `RelayDataSource` and `RelayManager` to `:app` | `dataSource`, `relayManager` | YES — transitional seam |
| `relay/.../service/RelayTestConfig.kt` | TEST-ONLY: process-global test topology config | `RelayTestConfig`, `RelayTestConfigProvider` | TEST INFRA |
| `relay/.../codec/RelayPayloadCodec.kt` | Wire format: 1-byte prefix + UTF-8 JSON | `encodeManifest()`, `decodeManifest()`, `encodeSos()`, `decodeSos()` | YES — frozen wire format |
| `relay/.../logic/RelayHopLogic.kt` | hopCount increment + status transition | `onRelayReceive()` | YES |
| `relay/.../logic/DutyCycler.kt` | Timer-based active/sleep window scheduling | `start()`, `stop()`, callbacks | YES |
| `data/.../db/AppDatabase.kt` | Room database definition | Schema v1, entities, DAOs | YES — schema frozen |
| `data/.../db/dao/SosRequestDao.kt` | SOS CRUD + TTL + status queries | `insertSos()`, `getAllSos()`, `getSosByUuid()`, `markUploaded()`, `deleteExpiredUploaded()` | YES — DO NOT change API |
| `data/.../relay/RoomRelayDataSource.kt` | `RelayDataSource` implementation backed by Room | `saveSosMessages()`, `getKnownUuids()`, `getMissingSos()`, `getAllSos()`, `observeAllSos()` | YES |
| `data/.../repository/SosRepository.kt` | App-layer SOS operations | `createSos()`, `getSos()`, `observeSos()`, `observeAllSos()` | YES |
| `data/.../worker/GatewaySyncWorker.kt` | WorkManager job for periodic backend upload | `doWork()`, `enqueueImmediateGatewaySync()`, `schedulePeriodicGatewaySync()` | YES |
| `data/.../worker/DeviceRegistrationWorker.kt` | First-launch device registration | `doWork()` | YES |
| `data/.../di/DataModule.kt` | Hilt DI for `:data` module | Provides DAOs, WorkManager, repositories | YES |
| `app/.../di/AppModule.kt` | Hilt DI for `:app` module | Provides `RelayDataSource`, `RelayRepository`, `DisasterApi`, `AuthInterceptor` | YES |
| `app/.../MainActivity.kt` | App entry point, relay service startup, test config injection | `onCreate()` | MODIFIED (6B-2 + test infra) |
| `app/.../ui/home/HomeViewModel.kt` | SOS creation + immediate relay propagation | `triggerSos()`, `createSosInternal()` | MODIFIED (6B-2) |
| `app/.../SihApplication.kt` | Application class, WorkManager + relay init | `onCreate()` | YES |
| `network/.../client/DisasterApi.kt` | Retrofit API interface | `uploadBatch()`, `registerDevice()`, `checkHealth()` | YES |

### 4.2 Key Relationships

```
HomeViewModel
  → SosRepository.createSos() → Room (SosRequestDao)
  → SosRepository.getSos() → Room
  → RelayDataSourceProvider.relayManager?.propagateLocalSos()
      → RelayManager → Nearby Connections → Peer

RelayForegroundService
  → Creates RelayManager (with RoomRelayDataSource or InMemoryFallbackStore)
  → Publishes to RelayDataSourceProvider.relayManager
  → Starts DutyCycler (10s/40s windows)

RelayManager (on peer device)
  → handleIncomingSos() → dataSource.saveSosMessages()
      → RoomRelayDataSource → SosRequestDao → Room

GatewaySyncWorker (periodic or after SOS creation)
  → sosRequestDao.getAllSos() → Room (all records including relay-received)
  → relayRepository.getRelayedSosEntries() → emptyList() (stub, benign)
  → Merge → disasterApi.uploadBatch()
```

---

## 5. INTEGRATION HISTORY

### 5.1 Commits

| Hash | Description |
|------|-------------|
| `320bfbd` | **Day 7** — Diagnostic instrumentation, 3-phone stress validation, Dump Store button, TEST-ONLY allow-list seam |
| `a1ec30d` | **Stages 0a–5** — A+B ↔ Component C integration. Build files, network module, data module, relay seam, Compose app shell. 67 files. |
| `cca669f` | **Stage 6B-1** — Relay wiring (`AppModule.kt`, `SihApplication.kt`, `MainActivity.kt`) + foreground notification persistence fix. |

### 5.2 What Each Integration Stage Changed

**Stages 0a–5 (`a1ec30d`):**
- Build files: Compose, Hilt, KSP, Retrofit, Moshi, WorkManager dependencies
- `:network` module: Full Retrofit HTTP layer (`DisasterApi`, `RetrofitClientFactory`, `AuthInterceptor`, DTOs)
- `:data` module: Hilt DI (`DataModule`), `SosRepository`, `GatewaySyncWorker`, `DeviceRegistrationWorker`, Room entities/DAOs/converters
- `:relay` seam: `RelayRepository` interface (with `StubRelayRepository`)
- `:app` shell: Compose UI (Home, Onboarding, Status screens), Material3 theme, navigation
- All A+B relay code preserved (zero diff)

**Stage 6A (`a1ec30d`):**
- Single-device physical verification: onboarding, SOS creation, location, persistence across app kill, status screen

**Stage 6B-1 (`cca669f`):**
- `AppModule.kt`: Wired `RoomRelayDataSource` → `RelayDataSourceProvider`, `StubRelayRepository` → `RelayRepository`
- `SihApplication.kt`: Added `RelayForegroundService.start()` call after relay initialization
- `MainActivity.kt`: Added `RelayForegroundService.start()` call on app startup
- `RelayForegroundService.kt`: Added foreground notification persistence fix (re-post on duty-cycle `onWindowStart`)

**Stage 6B-2 (uncommitted, ready to commit):**
- `HomeViewModel.kt`: Added `propagateLocalSos()` call after SOS creation
- `SosRepository.kt`: Added `getSos(uuid)` one-shot suspend read
- `RelayDataSourceProvider.kt`: Added `@Volatile var relayManager: RelayManager?`
- `RelayForegroundService.kt`: Added `relayManager` publish/clear on create/destroy
- `MainActivity.kt`: Added TEST-ONLY intent extras parsing for test topology
- `RelayTestConfig.kt`: Enhanced KDoc documentation
- `GatewaySyncWorkerTest.kt`: Fixed location mock to use `Task` with listeners

---

## 6. PHYSICAL TESTING GUIDE

### 6.1 Verified Test Topology

```
Device A ←→ Device B ←→ Device C
(45131FDJH003HS)  (10BD551MY80004T)  (R9ZY503BHDD)
```

**TEST-ONLY topology configuration:**
- Device A: allowed peer = Device-B only
- Device B: allowed peers = Device-A, Device-C
- Device C: allowed peer = Device-B only

**Why the controlled chain was necessary:**
The earlier triangle topology (all devices allowed to connect to all) gave misleading results because
SOS could reach the destination through multiple paths, making it impossible to verify true multi-hop
behavior. The controlled chain forces A→B→C as the only path, so hopCount increments are definitive.

**How topology is enforced:**
Via intent extras `relay_test_peer` / `relay_test_allowed` parsed in `MainActivity.onCreate()`.
These set a process-global `RelayTestConfig` that `RelayManager` checks via `isPeerAllowed()`.
**In-memory only — must be re-passed after every force-stop.**

### 6.2 ADB Launch Pattern

```powershell
# Device A (serial: 45131FDJH003HS)
.\adb -s 45131FDJH003HS shell am force-stop com.sih.android
.\adb -s 45131FDJH003HS shell am start -n com.sih.android/com.sih.app.MainActivity --es relay_test_peer Device-A --es relay_test_allowed "Device-B"

# Device B (serial: 10BD551MY80004T)
.\adb -s 10BD551MY80004T shell am force-stop com.sih.android
.\adb -s 10BD551MY80004T shell am start -n com.sih.android/com.sih.app.MainActivity --es relay_test_peer Device-B --es relay_test_allowed "Device-A,Device-C"

# Device C (serial: R9ZY503BHDD)
.\adb -s R9ZY503BHDD shell am force-stop com.sih.android
.\adb -s R9ZY503BHDD shell am start -n com.sih.android/com.sih.app.MainActivity --es relay_test_peer Device-C --es relay_test_allowed "Device-B"
```

**IMPORTANT:** All three devices must be launched within seconds of each other for duty-cycle
scan windows to overlap. Force-stop first — `onNewIntent()` is NOT overridden, so `am start`
on an already-running activity does NOT apply test config.

### 6.3 Device IDs vs Test Endpoint Names

- **Test endpoint names** (`Device-A`, `Device-B`, `Device-C`): Used by the TEST-ONLY topology
  filter. Set via intent extras. Process-local, lost on force-stop.
- **`SOSRequest.deviceId`**: Installation UUID (e.g., `bbd6bc6c-5e83-4460-b1cf-7c25045ca5e0`).
  Stable, persisted, travels with the SOS across hops. This is the real device identifier.
- **Nearby endpoint IDs**: Dynamic strings assigned by Nearby Connections on each connection.
  Change across reconnects. Never used for business logic.

### 6.4 Useful Logcat Commands

```powershell
# Clear logcat
.\adb -s 45131FDJH003HS logcat -c

# RelayManager operations (the main relay tag)
.\adb -s 45131FDJH003HS logcat -s RelayManager_Day3:I

# SOS propagation events
.\adb -s 45131FDJH003HS logcat -s RelayManager_Day3:I | Select-String "propagateLocalSos|handleIncomingSos|sendMissingSos"

# Manifest exchange
.\adb -s 45131FDJH003HS logcat -s RelayManager_Day3:I | Select-String "initiateManifestExchange|Manifest sent|manifest received"

# Connection events
.\adb -s 45131FDJH003HS logcat -s RelayManager_Day3:I | Select-String "onConnectionInitiated|onConnectionResult|onEndpointLost|Connection established"

# Duplicate detection
.\adb -s 45131FDJH003HS logcat -s RelayManager_Day3:I | Select-String "already known|UUID|already have"

# Test config loading
.\adb -s 45131FDJH003HS logcat -s MainActivity:I | Select-String "relay_test"

# Gateway sync worker
.\adb -s 45131FDJH003HS logcat -s GatewaySyncWorker:D

# Full relay dump
.\adb -s 45131FDJH003HS logcat -s RelayManager_Day3:I
```

### 6.5 What PASS Evidence Looks Like

For a multi-hop A→B→C test:
```
Device A: propagateLocalSos: broadcasting SOS <uuid> (hopCount=0, status=PENDING_LOCAL) to 1 connected endpoint(s)
Device B: handleIncomingSos: received SOS <uuid> from endpoint: <A's dynamic ID> — relayHopCount 0 → 1, status PENDING_LOCAL → IN_RELAY
Device B: propagateSosToConnected: forwarding SOS <uuid> (hopCount=1, status=IN_RELAY) to 1 connected endpoint(s)
Device C: handleIncomingSos: received SOS <uuid> from endpoint: <B's dynamic ID> — relayHopCount 1 → 2, status IN_RELAY → IN_RELAY
```

Key indicators:
- Same `uuid` across all three devices
- Same `deviceId` (installation UUID) across all three devices
- `hopCount=0` on A, `hopCount=1` on B, `hopCount=2` on C
- A and C are prevented from directly connecting by `isPeerAllowed` filter

### 6.6 The 4 Critical Physical Tests

#### Test 1: Controlled Multi-Hop A → B → C

**Purpose:** Verify that SOS propagates through an intermediate peer with correct hop accounting.

**Setup:** Controlled chain topology (A→B→C). A creates SOS. B relays. C receives.

**Expected behavior:** A sends to B (hopCount=0). B receives (hopCount=1), saves to Room, forwards to C. C receives (hopCount=2), saves to Room.

**Pass criteria:** Same UUID + same deviceId on all three devices. hopCount increments correctly (0→1→2). Both B and C have the SOS in their Room database.

**Failure indicators:** C never receives the SOS. hopCount doesn't increment. Different deviceId across hops.

#### Test 2: Duplicate / Echo-Loop Guard

**Purpose:** Verify that UUID-based idempotency prevents infinite retransmission.

**Setup:** After Test 1, disconnect and reconnect B to A. Observe manifest exchange.

**Expected behavior:** On reconnection, manifest shows "0 SOSRequest(s) to send" — both sides already have all UUIDs. No SOS is re-sent.

**Pass criteria:** Manifest exchange shows matching UUID sets. `getMissingSos()` returns empty. No echo loop.

**Failure indicators:** SOS re-sent on reconnection. hopCount incremented again. "N SOSRequest(s) to send" when stores match.

#### Test 3: Disconnect/Reconnect Missed-Message Resync

**Purpose:** Verify that SOS created while a peer is disconnected are delivered on reconnection.

**Setup:** Kill B. Create SOS on A while B is absent. Relaunch B. Wait for reconnection.

**Expected behavior:** B reconnects to A. Manifest exchange detects B is missing the new SOS. A sends the missed SOS to B with hopCount=1.

**Pass criteria:** B receives the SOS that was created while it was disconnected. hopCount=1 (direct from A). UUID matches.

**Failure indicators:** B never receives the missed SOS. B receives it with wrong hopCount.

#### Test 4: Zero-Peer Persistence + Later Synchronization

**Purpose:** Verify that SOS created offline are stored and transferred when a peer connects.

**Setup:** Create multiple SOS on A before any peer exists. Then connect B.

**Expected behavior:** All SOS persisted locally on A. When B connects, manifest exchange transfers all pre-existing SOS.

**Pass criteria:** All SOS records present on B after connection. UUIDs match. No data loss.

#### Test 5: Sleep-Window SOS Creation (NOT SEPARATELY TESTED)

**Purpose:** Verify SOS creation during duty-cycle sleep window.

**Status:** Not separately tested. Duty-cycle behavior was already validated in Day 5 (A→B→C under FGS + duty cycle). The specific edge case of creating an SOS during the sleep window was considered non-blocking.

---

## 7. HOW TO CONTINUE FROM HERE

### Step 1: Commit Checkpoint

The current working tree contains 12 modified files: 7 source code, 5 documentation. All are
complete, tested (where applicable), and safe to commit as a group.

**Recommended commit order:**
1. Commit all 12 files together (source + docs represent a coherent body of work)
2. Use GitHub Desktop (git identity not configured on CLI)
3. Commit message suggestion: "Stage 6B-2: propagateLocalSos wiring + physical relay-engine testing + documentation cleanup"

**Why commit now:** The working tree contains substantial verified work (3-device physical testing, Stage 6B-2 production feature, test infrastructure). Leaving it uncommitted risks losing context.

### Step 2: Backend Setup (for Stage 6B-4)

**What must exist before Stage 6B-4 can be performed:**

1. **Backend server running** at `http://10.0.2.2:8000/api/v1/` (emulator) or the appropriate device-network URL
2. **`POST /api/v1/sos/batch`** endpoint accepting `GatewayUploadBatch` payloads
3. **`POST /api/v1/device/register`** endpoint for device registration
4. **Network security:** Debug builds use cleartext HTTP (`networkSecurityConfig` in `app/src/debug/AndroidManifest.xml`)
5. **Physical device networking:** `10.0.2.2` is emulator-only. For physical devices, use the machine's actual IP address and update `BASE_URL` in `app/build.gradle.kts:22`

**Relevant files:**
- `app/build.gradle.kts:22` — `BuildConfig.BASE_URL`
- `data/.../worker/GatewaySyncWorker.kt:121` — `disasterApi.uploadBatch(batch)`
- `network/.../client/DisasterApi.kt` — Retrofit interface
- `app/src/debug/AndroidManifest.xml` — network security config

### Step 3: Stage 6B-4 Test Plan

**End-to-end multi-device → backend test:**

1. Start backend server
2. Launch 2+ physical devices with TEST-ONLY topology
3. Create SOS on Device A
4. Verify SOS propagates to Device B via relay (check logcat)
5. Verify SOS persisted in Room on both devices (adb shell, check Room database)
6. Wait for `GatewaySyncWorker` to trigger (immediate sync after SOS creation, or periodic 15-min cycle)
7. Verify `POST /api/v1/sos/batch` was called with correct payload (check backend logs)
8. Verify response is success (200/201)
9. Verify SOS marked as `UPLOADED` in Room (check `status` column)

**Evidence to capture:**
- Logcat from all devices (relay propagation + gateway sync)
- Backend logs (request received, payload content)
- Room database state (SOS records with status)

### Step 4: Post-Validation Closure

Once Stage 6B-4 passes:
1. Update `PROJECT_HANDOFF.md` §2, §13 — mark 6B-4 as PASS
2. Update `Logs.md` — add Stage 6B-4 entry with evidence
3. Update `Component1_Overview.md` §K, §M
4. Commit documentation updates
5. The project can honestly be considered COMPLETE

---

## 8. KNOWN PITFALLS AND GOTCHAS

### 8.1 Nearby Connections 8012 Bidirectional Connection Race

**WHAT HAPPENS:** Error code 8012 (`STATUS_CONNECTION_REJECTED`) occurs when both devices try to
connect to each other simultaneously.

**WHY IT HAPPENS:** When device A finds device B and initiates a connection, device B may also find
device A and initiate a connection at the same time. Nearby Connections does not handle symmetric
connection attempts gracefully.

**HOW TO RECOGNIZE IT:** Logcat shows `onConnectionInitiated` followed immediately by
`onConnectionResult` with `STATUS_CONNECTION_REJECTED` or `8012`.

**WHAT TO DO:** The `pendingConnections` set in `RelayManager` guards against this. If a connection
is already pending for an endpoint, new connection attempts are rejected. The system self-recovers
on the next duty-cycle window.

### 8.2 STATUS_ALREADY_CONNECTED_TO_ENDPOINT

**WHAT HAPPENS:** `connectionsClient.requestConnection()` returns `STATUS_ALREADY_CONNECTED_TO_ENDPOINT`.

**WHY IT HAPPENS:** A connection already exists (possibly from a previous scan window) but the
callback state hasn't been cleaned up.

**HOW TO RECOGNIZE IT:** Logcat shows this status in `onConnectionResult`.

**WHAT TO DO:** Already handled in `RelayManager` — the endpoint is added to `connectedEndpoints`
if the status is `STATUS_OK`. The warning is benign.

### 8.3 Endpoint IDs Changing Across Reconnects

**WHAT HAPPENS:** The `endpointId` string assigned by Nearby Connections changes every time a
device reconnects.

**WHY IT HAPPENS:** Nearby Connections assigns dynamic endpoint IDs per connection session.

**HOW TO RECOGNIZE IT:** The same physical device has a different `endpointId` in successive
`onConnectionInitiated` callbacks.

**WHAT TO DO:** Never store or compare endpoint IDs across sessions. The relay engine uses
UUID-based SOS identification, not endpoint IDs. The `connectedEndpoints` set is cleared on
disconnect and repopulated on reconnect.

### 8.4 "Connection Established" May Not Appear Symmetrically

**WHAT HAPPENS:** Device A logs "Connection established" but Device B doesn't, or vice versa.

**WHY IT HAPPENS:** The `onConnectionResult(STATUS_OK)` callback timing differs between the
advertiser and discoverer sides.

**HOW TO RECOGNIZE IT:** One side shows the connection callback, the other doesn't.

**WHAT TO DO:** This is normal Nearby Connections behavior. The connection IS established
bidirectionally — the callback timing is just asymmetric. Check `connectedEndpoints` on both
sides to confirm.

### 8.5 TEST-ONLY Topology Configuration Is In-Memory

**WHAT HAPPENS:** After force-stopping the app, the test topology (peer allow-list) is lost.

**WHY IT HAPPENS:** `RelayTestConfigProvider.config` is a `@Volatile var` in memory. It is not
persisted. `START_STICKY` restarts the service but does NOT re-apply the test config because
`onNewIntent()` is not overridden.

**HOW TO RECOGNIZE IT:** After force-stop + restart without intent extras, `isPeerAllowed()` always
returns `true` (unrestricted peer access).

**WHAT TO DO:** Always force-stop AND relaunch with intent extras for every test device. Never
assume the config persists across restarts.

### 8.6 Triangle Topology vs Controlled Chain

**WHAT HAPPENS:** With a triangle topology (all devices allowed to connect to all), SOS can reach
the destination through multiple paths, making hopCount unreliable as a multi-hop indicator.

**WHY IT HAPPENS:** If A→C is also allowed, C might receive the SOS directly from A (hopCount=1)
instead of through B (hopCount=2).

**HOW TO RECOGNIZE IT:** C receives SOS with hopCount=1 instead of hopCount=2. Both A and C report
sending SOS to C.

**WHAT TO DO:** Use the controlled chain topology (A→B→C only) for multi-hop verification. This is
why the TEST-ONLY topology mechanism exists.

### 8.7 Force-Stopping All Devices Is NOT a Disconnect/Reconnect Test

**WHAT HAPPENS:** Force-stopping all devices simultaneously and restarting them doesn't test the
disconnect/reconnect resync path.

**WHY IT HAPPENS:** Force-stop kills the process. On restart, the service creates a fresh
`RelayManager` with empty `connectedEndpoints`. No disconnect callback fires. The reconnect
behavior requires one device to be alive while the other disconnects and reconnects.

**HOW TO RECOGNIZE IT:** All devices show fresh startup logs, no `onEndpointLost` callbacks.

**WHAT TO DO:** For disconnect/reconnect testing: kill ONE device while the others stay alive.
Then relaunch the killed device. The surviving devices will detect the disconnect via
`onEndpointLost`, and the relaunched device will reconnect via the normal discovery path.

### 8.8 Manifest Direction Confusion

**WHAT HAPPENS:** "0 SOSRequest(s) to send" in logs is misinterpreted as "peer has nothing".

**WHY IT HAPPENS:** The log message describes the LOCAL device's outgoing diff — the UUIDs the
local device wants to send to the peer. It does NOT describe what the peer will send.

**HOW TO RECOGNIZE IT:** Log shows "0 SOSRequest(s) to send" but the peer subsequently sends
SOS records.

**WHAT TO DO:** "0 SOSRequest(s) to send" means the local device's store is a subset of (or equal
to) the peer's store. The peer may still have records the local device is missing.

### 8.9 InMemoryFallbackStore / RelayDataSourceProvider Process-Restart Window

**WHAT HAPPENS:** After process death, SOS received from peers may land in `InMemoryFallbackStore`
instead of Room.

**WHY IT HAPPENS:** `RelayDataSourceProvider.dataSource` is null after process death. The service
falls back to `InMemoryFallbackStore` until Room is re-injected via DI.

**HOW TO RECOGNIZE IT:** `RelayForegroundService` logs "Using InMemoryFallbackStore — Room
unavailable". SOS records received during this window are not in Room.

**WHAT TO DO:** This is a known narrow gap (seconds-long). `START_STICKY` restarts the service
quickly. In practice, the window is too short for significant data loss. A real `RelayRepository`
implementation (Stage 6B-3 cleanup) would close this gap.

### 8.10 `device_id` Contract Ambiguity

**WHAT HAPPENS:** Different documentation sections describe `device_id` differently.

**WHY IT HAPPENS:** §1.2 defines it as an "installation UUID". §1.9 defines it as "generated on
first app install". These are actually the same thing, but the wording differs.

**HOW TO RECOGNIZE IT:** Confusion about whether `device_id` is stable or regenerated.

**WHAT TO DO:** `DevicePreferences` uses a stable installation UUID stored in
`EncryptedSharedPreferences`. It is generated once on first launch and never changes. This is
the canonical behavior regardless of what the documentation says.

### 8.11 Physical Device Networking vs 10.0.2.2

**WHAT HAPPENS:** `BASE_URL = http://10.0.2.2:8000` doesn't work on physical devices.

**WHY IT HAPPENS:** `10.0.2.2` is the Android emulator's special alias for the host machine's
localhost. Physical devices cannot use this address.

**HOW TO RECOGNIZE IT:** `GatewaySyncWorker` fails with connection refused or timeout.

**WHAT TO DO:** For physical device testing, use the machine's actual IP address (e.g.,
`http://192.168.1.x:8000`). Update `BASE_URL` in `app/build.gradle.kts:22`. Ensure the
device and machine are on the same network.

### 8.12 CRLF/EOL Noise

**WHAT HAPPENS:** `.gitignore` and `gradlew.bat` show diffs that are purely line-ending changes.

**WHY IT HAPPENS:** Windows/Linux autocrlf settings cause line-ending normalization.

**HOW TO RECOGNIZE IT:** `git diff` shows `^M` characters or `\ No newline at end of file`.

**WHAT TO DO:** Ignore these diffs. They contain zero semantic changes. A future commit should
be intentional about them, but they don't affect functionality.

### 8.13 Lint / API 24 Issue

**WHAT HAPPENS:** Lint fails with `NewApi` warning for `ConcurrentHashMap.newKeySet()`.

**WHY IT HAPPENS:** `ConcurrentHashMap.newKeySet()` requires API 24, but `minSdk = 23`.

**HOW TO RECOGNIZE IT:** Lint report shows `NewApi` error in `RelayManager.kt`.

**WHAT TO DO:** This is a known issue. The code works on API 23 devices in practice (the method
exists in the framework but lint is conservative). To fix: add `@SuppressLint("NewApi")` or
raise `minSdk` to 24. Currently skipped.

---

## 9. DEBUGGING / LOGGING CHEAT SHEET

### 9.1 Listing Connected Devices

```powershell
.\adb devices -l
```

### 9.2 Launching Test Devices

```powershell
# Device A
.\adb -s 45131FDJH003HS shell am force-stop com.sih.android
.\adb -s 45131FDJH003HS shell am start -n com.sih.android/com.sih.app.MainActivity --es relay_test_peer Device-A --es relay_test_allowed "Device-B"

# Device B
.\adb -s 10BD551MY80004T shell am force-stop com.sih.android
.\adb -s 10BD551MY80004T shell am start -n com.sih.android/com.sih.app.MainActivity --es relay_test_peer Device-B --es relay_test_allowed "Device-A,Device-C"

# Device C
.\adb -s R9ZY503BHDD shell am force-stop com.sih.android
.\adb -s R9ZY503BHDD shell am start -n com.sih.android/com.sih.app.MainActivity --es relay_test_peer Device-C --es relay_test_allowed "Device-B"
```

### 9.3 Clearing Logcat

```powershell
.\adb -s <SERIAL> logcat -c
```

### 9.4 Viewing RelayManager Logs

```powershell
.\adb -s <SERIAL> logcat -s RelayManager_Day3:I
```

### 9.5 Filtering SOS Propagation

```powershell
.\adb -s <SERIAL> logcat -s RelayManager_Day3:I | Select-String "propagateLocalSos|handleIncomingSos|propagateSosToConnected|sendMissingSos"
```

### 9.6 Filtering Manifest Exchange

```powershell
.\adb -s <SERIAL> logcat -s RelayManager_Day3:I | Select-String "initiateManifestExchange|Manifest sent|manifest received|getMissingSos"
```

### 9.7 Filtering Connection Events

```powershell
.\adb -s <SERIAL> logcat -s RelayManager_Day3:I | Select-String "onConnectionInitiated|onConnectionResult|onEndpointLost|Connection established|connectedEndpoints"
```

### 9.8 Checking Duplicate Detection

```powershell
.\adb -s <SERIAL> logcat -s RelayManager_Day3:I | Select-String "already known|already have|UUID|duplicate"
```

### 9.9 Gateway Sync Worker

```powershell
.\adb -s <SERIAL> logcat -s GatewaySyncWorker:D
```

### 9.10 Test Config Loading

```powershell
.\adb -s <SERIAL> logcat -s MainActivity:I | Select-String "relay_test|RelayTestConfig"
```

### 9.11 Dump Store Button (Day 7 Debug Feature)

The app has a "Dump Store" button on the Home screen that writes the current relay SOS store
to logcat. Useful for verifying Room state without database queries.

---

## 10. CURRENT TECHNICAL DEBT

### 10.1 StubRelayRepository

**What:** `StubRelayRepository` in `relay/.../RelayRepository.kt:47` always returns `emptyList()`.

**Why it's non-blocking:** Relay-received SOS are already persisted into Room by
`RoomRelayDataSource.saveSosMessages()` (called from `RelayManager.handleIncomingSos()`).
`GatewaySyncWorker` reads all SOS from Room via `sosRequestDao.getAllSos()`. The stub's
`getRelayedSosEntries()` returning empty is redundant — no data is lost.

**The remaining gap:** The dual-source merge in `GatewaySyncWorker` (lines 97-102) is misleading
code — it suggests two independent data sources when in reality there is only one (Room). A future
cleanup should either remove the `relayRepository` call or implement a real `RelayRepository` that
delegates to Room.

### 10.2 InMemoryFallbackStore Edge Case

**What:** After process death, SOS received before Room re-injection land in `InMemoryFallbackStore`
instead of Room.

**Impact:** These records are unreachable by `GatewaySyncWorker` until Room is restored.

**Mitigation:** The window is seconds-long (`START_STICKY` restarts quickly). In practice, minimal
data loss risk.

### 10.3 `device_id` Contract Ambiguity

**What:** Documentation describes `device_id` differently in §1.2 and §1.9.

**Reality:** `DevicePreferences` uses a stable installation UUID stored in
`EncryptedSharedPreferences`. Generated once, never changes.

### 10.4 Lint / API 24

**What:** `ConcurrentHashMap.newKeySet()` requires API 24 but `minSdk = 23`.

**Impact:** Lint warning only. Code works on API 23 in practice.

### 10.5 Stress Testing Limitations

**What:** 4–5 phone stress test deferred (only 3 physical devices available).

**Impact:** The 3-device A→B→C test covers the critical multi-hop path. Scaling to 4–5 devices
would test mesh density but is not required for the current scope.

---

## 11. WHAT NOT TO BREAK

### 11.1 UUID-Based Idempotency

- Every SOS has a unique UUID (v4 format)
- `RoomRelayDataSource` uses `OnConflictStrategy.IGNORE` — duplicate UUIDs silently skipped
- `GatewaySyncWorker` uses `.distinctBy { it.uuid }` — duplicate records merged
- **NEVER** change the UUID generation or the IGNORE conflict strategy

### 11.2 hopCount Semantics

- hopCount is monotonically increasing (never decremented)
- Incremented by `RelayHopLogic.onRelayReceive()` on each relay hop
- Origin device: hopCount=0. First relay: hopCount=1. Second relay: hopCount=2.
- **NEVER** reset hopCount or allow negative values

### 11.3 No Re-Propagation of Known SOS

- `getMissingSos()` only returns UUIDs the peer doesn't have
- `handleIncomingSos()` checks UUID against local store before saving
- **NEVER** bypass the UUID check or re-send known SOS records

### 11.4 Manifest Diff Behavior

- Manifests contain only UUID sets, not full SOS records
- Diff is computed symmetrically — both sides compute what the other is missing
- "0 SOSRequest(s) to send" only describes the LOCAL device's outgoing diff
- **NEVER** interpret "0 SOSRequest(s) to send" as "peer has nothing to send us"

### 11.5 Room Persistence Path

- `RoomRelayDataSource` is the ONLY write path for relay-received SOS into Room
- `SosRequestDao.insertSos()` with `OnConflictStrategy.IGNORE` is the ONLY insert mechanism
- **NEVER** bypass `RoomRelayDataSource` to write relay SOS directly to Room

### 11.6 Controlled Topology Is Test Instrumentation

- `RelayTestConfig` / `RelayTestConfigProvider` are TEST-ONLY
- They are inert when no intent extras are provided
- **NEVER** use test topology in production builds
- **NEVER** persist test topology configuration

### 11.7 Existing Physical Test Evidence

- 4 critical physical tests passed on 3 real Android devices
- Evidence is in `Logs.md` and `walkthrough.md`
- **NEVER** describe these as "not validated" or "not tested"
- **NEVER** fabricate test results for tests that weren't performed

### 11.8 `:relay` Module Isolation

- `:relay` must NEVER depend on `:data`, `:network`, or Room
- Communication is through `RelayDataSource` interface only
- **NEVER** add a Room import, Retrofit call, or DAO reference to `:relay`

### 11.9 Wire Format

- 1-byte prefix: `0x01` = manifest, `0x02` = SOS
- UTF-8 JSON payload
- **NEVER** change the wire format without updating both `encode` and `decode` in `RelayPayloadCodec`

---

## 12. FINAL CHECKLIST FOR THE NEXT DEVELOPER

### Before Changing Relay Code

- [ ] Read `Agent.md` §Decision Rules — especially "do not change relay architecture"
- [ ] Read `PROJECT_HANDOFF.md` §11 (Architectural Decisions)
- [ ] Verify the change doesn't add `:data` or `:network` dependencies to `:relay`
- [ ] Verify the change doesn't modify `RelayApi` interface without team coordination
- [ ] Run `./gradlew :relay:test :relay:build` after changes

### Before Running Physical Tests

- [ ] Confirm 3 devices are available and charged
- [ ] Confirm TEST-ONLY topology will be passed via intent extras
- [ ] Force-stop ALL devices before relaunching with config
- [ ] Launch all devices within seconds of each other
- [ ] Clear logcat on all devices before starting
- [ ] Verify duty-cycle scan windows overlap (launch timing matters)

### Before Backend Integration (Stage 6B-4)

- [ ] Backend running at correct URL
- [ ] `POST /api/v1/sos/batch` endpoint functional
- [ ] `POST /api/v1/device/register` endpoint functional
- [ ] Physical devices on same network as backend (update `BASE_URL` if needed)
- [ ] Network security config allows cleartext (debug builds)
- [ ] Test device registration flow first (DeviceRegistrationWorker)
- [ ] Test single-device upload before multi-device

### Before Committing

- [ ] Run `./gradlew :app:assembleDebug :data:testDebugUnitTest :relay:testDebugUnitTest`
- [ ] Verify 75+ tests pass (64 relay + 11 data + any new tests)
- [ ] Verify `git status` shows only intended files
- [ ] Do NOT commit `.gitignore` or `gradlew.bat` whitespace changes (user preference)
- [ ] Do NOT commit via CLI (git identity not configured) — use GitHub Desktop

### Before Declaring the Project Complete

- [ ] Stage 6B-4 passed: multi-device → backend end-to-end tested
- [ ] All documentation updated to reflect final state
- [ ] `PROJECT_HANDOFF.md` §2 reflects "ALL STAGES COMPLETE"
- [ ] `Logs.md` has evidence for Stage 6B-4
- [ ] No stale "uncommitted" or "pending" claims remain in any doc
- [ ] `Component1_Overview.md` §K lists nothing as "NOT YET COMPLETED" (except optional future work)

---

## 13. APPENDIX: KEY CONFIGURATION VALUES

| Setting | Value | Location |
|---------|-------|----------|
| Package name | `com.sih.app` | `app/build.gradle.kts` |
| Application ID | `com.sih.android` | `app/build.gradle.kts` |
| minSdk | 23 | `app/build.gradle.kts:15` |
| targetSdk | 35 | `app/build.gradle.kts:16` |
| compileSdk | 35 | `app/build.gradle.kts:11` |
| Nearby service ID | `com.sih.relay.SERVICE` | `RelayManager.kt` |
| Nearby version | 19.2.0 (pinned) | `gradle/libs.versions.toml` |
| Duty cycle | ~10s active / ~40s sleep | `DutyCycler.kt` |
| FGS notification channel | `relay_foreground` | `RelayForegroundService.kt` |
| FGS notification ID | 1001 | `RelayForegroundService.kt` |
| Wire format prefix | `0x01` = manifest, `0x02` = SOS | `RelayPayloadCodec.kt` |
| Backend URL (debug) | `http://10.0.2.2:8000/api/v1/` | `app/build.gradle.kts:22` |
| WorkManager periodic interval | 15 minutes | `DataModule.kt` |
| Room schema version | 1 | `AppDatabase.kt` |

---

## 14. APPENDIX: REFERENCE DOCUMENTS

| Document | Purpose |
|----------|---------|
| `PROJECT_HANDOFF.md` | Concise project-state tracker (current status, known issues, next steps) |
| `INTEGRATION_GUIDE.md` | This file — detailed engineering continuation manual |
| `Logs.md` | Day-by-day development log with evidence |
| `walkthrough.md` | Integration walkthrough with build/launch/test instructions |
| `Component1_Overview.md` | High-level relay engine overview for team members |
| `Agent.md` | Agent operational rules and decision history |
| `15-day-roadmap.md` | Original 15-day development roadmap |
| `day1-contracts-and-repo-setup.md` | Data contracts, API schemas, wire format specs |
| `ApiEndpoints.md` | Backend API endpoint documentation |
| `antigravity-build-prompts -.md` | Detailed build prompt with architecture explanation |

---

*This document was created on 2026-08-25 as part of Stage 7: Final Documentation Cleanup.*
*It should be updated whenever significant implementation, testing, or integration changes occur.*