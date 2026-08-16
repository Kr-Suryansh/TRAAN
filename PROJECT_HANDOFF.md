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

**Development day/phase:** Day 5 is the last **committed** day. Day 6 implementation work and its
physical validation exist in the **working tree but are UNCOMMITTED** (see Discrepancies §15).

> [!IMPORTANT] Repo state on disk ≠ repo state at HEAD
> - HEAD commit `e8f319c` = "Completed upto Day 5".
> - The **working tree** additionally contains complete, build-verified, **physically validated Day 6** work:
>   Room persistence in `:data` + `RoomRelayDataSource`, the `SosRequestMapper`, real device identity,
>   `RelayPermissionRequirements`, the permission preflight UX, and relay startup hardening.
> - Day 6 changes are NOT committed but ARE now described in `Logs.md`/`walkthrough.md`/`Agent.md`
>   (updated this session with the actual physical validation evidence).

### What is actually completed (verified against code + tests)
- Day 1–5 relay engine fully implemented and physically validated (see §4, §8).
- Room persistence layer in `:data` (ported from C), compiling and unit-tested.
- `RoomRelayDataSource` implementing `com.sih.relay.api.RelayDataSource`, wired in `:app`.
- Relay startup hardening + permission helper (Day 6 hardening portion).
- Day 6 permission preflight UX in `:app` ("Start Relay" requests missing runtime permissions and prompts to enable Bluetooth before starting the service).
- **Day 6 physical validation PASSED** — 3-device A → B → C with a non-empty SOS written through the Room-backed `saveSosMessages()` path on B and C, and that SOS **survived a process restart on C** (Room persisted 2 UUIDs after a fresh injection post-restart). See §8 and `Logs.md` Day 6.

### What is currently working (verified this session)
- Full Gradle build `:app:assembleDebug` + `:data:testDebugUnitTest` + `:relay:testDebugUnitTest`:
  **BUILD SUCCESSFUL**. **75 JVM tests pass, 0 failures** (64 relay + 11 data). Verified, not assumed.

### What is incomplete / planned / deferred
- Real SOS creation + Compose UI (C's job).
- `:network` module, backend uploads, gateway mode, device registration (C/D).
- TTL cleanup, low-battery throttle, simulation/fallback demo mode (later roadmap days).
- Full integration with Component C's repo (see §9).

---

## 3. MODULE / ARCHITECTURE MAP

### Modules (from `settings.gradle.kts`, root project `sih-android`)
| Module | Namespace | Owner | Current state |
|---|---|---|---|
| `:app` | `com.sih.app` | C | Temporary test-scaffold Activity (Day 4/5/6 test harness) |
| `:relay` | `com.sih.relay` | A+B | Complete: engine + FGS + duty cycle + permission helper |
| `:data` | `com.sih.data` | C | Room layer ported in (Day 6, uncommitted) + `RoomRelayDataSource` |
| `:network` | `com.sih.network` | shared | Empty stub (only manifest + core-ktx) |

### Dependency direction (from actual build files — the intended architecture)
```
:app  ──► :relay   (uses RelayApi)
:app  ──► :data    (builds AppDatabase + RoomRelayDataSource, injects it)
:app  ──► :network (declared dependency; :network is still a stub)
:data ──► :relay   (implements RelayDataSource; uses relay models in the mapper)
:relay ─X─ :data   (NEVER — circular-dependency violation)
:relay ─X─ :network (NEVER)
:data ─X─ :network (in THIS repo :data does not depend on :network; C's repo has data→network)
```
Version catalog: `gradle/libs.versions.toml` (AGP 8.5.2, Kotlin 2.0.0, Nearby 19.2.0, Coroutines 1.8.1,
Serialization 1.7.1, Room 2.6.1, Moshi 1.15.1, security-crypto 1.1.0-alpha06, mockk 1.13.11, junit 4.13.2).

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
- **Day 6 (working tree, uncommitted)** — Room persistence in `:data` (ported from C): entities, DAOs,
  `AppDatabase`, `RoomConverters`, model enums; `DevicePreferences` (de-Hilted identity); `SosRequestMapper`;
  `RoomRelayDataSource`; `RelayPermissionRequirements`; startup hardening of `RelayManager` + FGS;
  permission preflight UX in `:app` (runtime permission requests + Bluetooth enable prompt before start).
  `:app` now uses Room + real device identity. 11 new data tests. Build + 75 tests verified.
  **Physically validated** this session: A→B→C with a non-empty SOS (`e9407a8a-...`) persisted to Room on
  B and C and surviving a process restart on C (see `Logs.md` Day 6, §8).

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
  Start/Stop/Inject buttons. Will be replaced by C's Compose UI.

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

**Not yet physically validated:** the START_STICKY-only redelivery path (service recreated after process death
without a fresh `:app` process uses the in-memory fallback until Hilt re-injection), gateway upload to a real
backend, internet delivery.

---

## 9. DEFERRED WORK

Work intentionally postponed. Especially the C-component integration items:

- **Full Component C integration** (the big one):
  - Merge/reconcile this repo's `:data` Room copy with Component C's repo (schema is identical by design).
  - Replace `RelayDataSourceProvider` with C's Hilt DI; resolve the START_STICKY store divergence.
  - Bring in C's `SosRepository`, `GatewaySyncWorker`, `DeviceRegistrationWorker`, `DataModule`,
    network DTOs/`DisasterApi`, `AuthInterceptor`/Retrofit config.
  - Replace `MainActivity` scaffold with C's Compose app (real SOS creation, Status screen, onboarding).
  - Reconcile `device_id` semantics (§1.2 "installation ID" vs §1.9 "generated on first app install";
    `DevicePreferences` currently uses a stable installation id as fallback).
- **Gateway/network:** implement `:network`, gateway upload path, backend device registration, TTL cleanup
  (48–72h via `last_relayed_at`), low-battery throttle (Day 10), simulation/fallback demo mode (Day 12).
- **Known deferred decisions** the analysis has NOT resolved: whether the Day 6 Room-backed store should use
  upsert-on-hop-metadata (currently `insertSos` IGNORE is used to preserve idempotent semantics).

---

## 10. KNOWN ISSUES / RISKS

Only issues supported by the repo or documented testing.

- **Uncommitted Day 6 working tree** (top risk for continuity): HEAD is Day 5; Day 6 code exists only in the
  working tree. Docs (`Logs.md`/`walkthrough.md`/`Agent.md`) now describe Day 6, but a fresh clone would NOT
  contain the Room layer. (This is a process issue, not a code bug.)
- **START_STICKY store divergence** after process death (in-memory fallback vs `:app` store) — documented in
  `RelayForegroundService`; resolved only by DI/Room re-injection.
- **Lint skipped** due to `ConcurrentHashMap.newKeySet()` `NewApi` (API 24 vs minSdk 23) in `RelayManager.kt`.
- **Transient Nearby status codes** observed on hardware (8012/8003/8011) — self-recover; mitigated by
  `pendingConnections` race guard and Nearby 19.2.0 pin.
- **`RelayManifest.deviceId`** is currently the constant `LOCAL_ENDPOINT_NAME` ("SIH-Relay-Node"), not a real
  device id (`RelayManager.kt`, manifest exchange). Accepted for the mesh test phase.
- **`device_id` contract ambiguity** §1.2 vs §1.9 — flagged in `DevicePreferences`; unresolved team-wide.
- **kapt language-version warning + Room schema-export warning** — benign today, see §7.
- **Docs lag:** `Component1_Overview.md` still describes up to Day 5; `Logs.md`/`walkthrough.md`/`Agent.md`
  were updated to cover Day 6 this session.
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

Ground truth of the repo: the working tree contains verified, uncommitted Day 6 work (implementation,
75 automated tests, and physical A→B→C Room persistence validation); HEAD is Day 5.

Exact next logical tasks, in order:
1. **Commit the Day 6 working tree** (Room layer, `RoomRelayDataSource`, mapper, identity, permission helper,
   permission preflight UX, hardening, tests) — docs (`Logs.md`/`walkthrough.md`/`Agent.md`) are now updated
   to match.
2. **Proceed to roadmap Day 7 for A+B: stress-test with 4–5 phones** (duplicate/dropped/stuck message
   detection) once the Day 6 commit is in place.
3. **Do NOT start full C integration** (Hilt swap, gateway workers, real app shell) until the Day 6 commit is
   done — those are the deferred integration tasks of §9.

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

1. **Day 6 work is uncommitted but now documented.** HEAD (`e8f319c`) = Day 5; the working tree contains the
   full Day 6 implementation, its passing tests, and its physical validation, and `Logs.md`/`walkthrough.md`/
   `Agent.md` now describe Day 6 (updated this session). The changes are still not committed.
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