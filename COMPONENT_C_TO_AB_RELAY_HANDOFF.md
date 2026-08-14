# Component C → A+B Relay Handoff

## 1. Purpose
This document serves as the official integration handoff from Component C (Android App Shell) to Component A+B (Mesh/Relay Engine). It details the existing architecture, the boundaries of ownership, and the exact constraints A+B must respect. Component C owns the UI, the Room database, the Gateway Upload worker, and device preferences. Component C **does not** implement Nearby Connections, epidemic routing, or peer-to-peer manifest exchange. Component A+B is exclusively responsible for the relay mesh.

## 2. Current Component C Architecture
- `RelayRepository`: The interface contract located in `:relay`. Component C relies on this to retrieve SOS records received from peers. Currently implemented as a stub (`StubRelayRepository`) returning an empty list. A+B must implement the real logic here.
- `SosRepository`: Located in `:data`. Manages local SOS creation and exposes local status updates. Does not handle mesh communication.
- `SosRequestDao`: Located in `:data`. The definitive Room database DAO for persisting all SOS records (local and relayed). Handles duplicate safety via `OnConflictStrategy.IGNORE` during insertion.
- `SosRequestEntity`: The local database entity. Includes all Day 1 contract fields plus device-only state (e.g., `status`).
- `DataModule`: Wires the dependencies for Hilt.
- `GatewaySyncWorker`: Located in `:data`. A WorkManager worker triggered by network availability. Reads all records from `SosRequestDao` and `RelayRepository` and uploads them to the backend in a batch.
- `DevicePreferences`: Handles the local `installation_id`, `device_id`, and `device_jwt`.
- `SosConstants`: Centralized constants, notably `TTL_SECONDS` (72 hours) for record cleanup.
- DTOs (`SosRequestDto`, etc.): Network representations located in `:network`. Strictly omit the local `status` field.

## 3. Current Relay Interface
**Interface:** `RelayRepository` (in `sih-android/relay/src/main/kotlin/com/sih/relay/RelayRepository.kt`)

**Method:** `suspend fun getRelayedSosEntries(): List<SosRequestDto>`
- **Parameters:** None.
- **Return Type:** `List<SosRequestDto>`.
- **Current Behavior:** Stub returns `emptyList()`.
- **Caller:** `GatewaySyncWorker` calls this to merge mesh-held records with Room-held records before uploading.
- **Expected A+B Behavior:** Must return all SOS entries currently held by the relay engine that are not already safely persisted in the local Room database.
- **Compatibility Requirements:** Must return standard DTOs. 

## 4. SOS Data Ownership
User creates SOS
    ↓
Component C (ViewModel & SosRepository)
    ↓
Room (SosRequestDao)
    ↓
SOSRequestEntity
    ↓
Relay can read/receive records
    ↓
GatewaySyncWorker
    ↓
POST /api/v1/sos/batch

A device holds its own SOS records AND records received from other devices. Uploaded records are not deleted immediately; they remain in local storage until the TTL expires to ensure they can still propagate across the mesh.

## 5. Exact SOSRequest Contract
The authoritative fields for an SOS report:
- `uuid`: string (UUIDv4)
- `device_id`: string (originating device)
- `created_at`: ISO8601 string
- `location.lat`: float
- `location.lng`: float
- `location.accuracy_m`: float | null
- `is_quick_sos`: boolean
- `emergency_type`: string
- `severity_hint`: string | null
- `people_count`: integer | null
- `medical_snapshot`: UserMedicalProfileDto | null
- `custom_message`: string | null
- `contact_number`: string | null
- `relay_hop_count`: integer
- `last_relayed_at`: ISO8601 string

**Device-Side Only Field:**
- `status`: String (`pending_local`, `in_relay`, `uploaded`). This field **must not** be sent in the backend DTO payload.

## 6. What A+B Must Implement
Component A+B is responsible for:
- Google Nearby Connections implementation (`P2P_CLUSTER` strategy).
- Discovery and advertising (duty-cycled, not continuous).
- BLE-preferred operation to save battery.
- Connection lifecycle management.
- Manifest exchange to compute missing UUIDs.
- SOS transfer over the mesh.
- Duplicate handling during transfer.
- Interrupted transfer handling & reconnect/recovery.
- Malformed payload rejection.
- Updating `relay_hop_count` and `last_relayed_at`.
- Coordinating with TTL.
- Maintaining a foreground service with a persistent notification.
- Managing Nearby/Bluetooth permissions.
- Device restart recovery for the mesh.
- Battery-aware behavior (e.g., throttling scans).

## 7. Relay → C Integration
Phone B receives SOS from A
    ↓
Relay validates payload
    ↓
SOS merged/persisted
    ↓
Room contains SOS
    ↓
C can read it for UI/Gateway
    ↓
GatewaySyncWorker uploads it when connected

**Integration gap — A+B and C must agree on this interface before merge.**
Currently, A+B relies on `RelayRepository.getRelayedSosEntries()` to supply records to the Gateway. A mechanism for the Relay to proactively push received records into Room (e.g., via `SosRequestDao.insertSos`) must be formally agreed upon and implemented.

## 8. C → Relay Integration
Component C expects the relay engine to:
- Be capable of starting/stopping via the foreground service based on user preference or battery mode.
- Access relay-held SOS records via `RelayRepository`.
- Receive newly-created local SOS records to begin advertising them.
- Synchronize persistence safely (using Room as the durable store).
- Manage status transitions accurately (e.g., transitioning an SOS from `pending_local` to `in_relay`).
- Update relay metadata (`hop_count`, `last_relayed_at`).

## 9. Persistence Ownership
- **Room is the durable local storage.** It is the source of truth for all data that must survive app/device restarts.
- Relay must not create a conflicting authoritative SOS database. It should rely on Room or synchronize flawlessly with it.
- `SosRequestDao.insertSos` uses `OnConflictStrategy.IGNORE`, ensuring duplicate UUID insertions are idempotent.
- Failed transfers must never corrupt valid records in Room.
- `GatewaySyncWorker` reads from Room and the Relay interface. Received SOS must be persisted safely so the Gateway can see them.

## 10. Manifest Exchange
A connects to B
    ↓
A sends RelayManifest
B sends RelayManifest
    ↓
Each calculates missing UUIDs
    ↓
Only missing SOS records transfer

**Schema:**
```json
{
  "device_id": "string",
  "known_uuids": ["string", "..."],
  "timestamp": "ISO8601"
}
```
Full-store transfer on every encounter is strictly prohibited. Re-encounters with identical manifests must transfer no SOS payloads.

## 11. Relay Metadata
- `relay_hop_count`: Must increment by 1 at each distinct device hop.
- `last_relayed_at`: Must be updated to the current ISO8601 timestamp at each hop.

`Integration decision required: exact duplicate metadata update semantics must be agreed by C + A+B.`
Duplicate reception must not cause uncontrolled hop-count inflation.

## 12. Status Lifecycle
- `pending_local`: SOS created on the device, not yet picked up by the mesh or uploaded. (Managed by Component C).
- `in_relay`: SOS is actively being broadcasted/transferred via the mesh. (Transition managed by Component A+B).
- `uploaded`: SOS successfully ingested by the backend API. (Managed by Component C / GatewaySyncWorker).

Uploaded records must not be immediately deleted from local persistence.

## 13. Gateway Upload Interaction
Local Room SOS store
    ↓
GatewaySyncWorker reads all records
    ↓
complete local SOS batch merged with relay records
    ↓
POST /api/v1/sos/batch

The batch contains both the device's own SOS and relayed SOS. Accepted and duplicate records returned by the backend become `uploaded`, but remain stored locally until TTL.

## 14. TTL and Cleanup
The current TTL configuration is defined in `SosConstants.TTL_SECONDS` (72 hours). 
`SosRequestDao.deleteExpiredUploaded` cleans up records where `status = 'uploaded'` and `last_relayed_at` is older than the TTL.
There is a documented `TODO` regarding the safe deletion of `pending_local` and `in_relay` records. Safe cleanup of these states depends on final relay lifecycle semantics. A+B must not blindly delete old records without ensuring the epidemic mesh routing policy is respected.

## 15. Failure Handling

| Failure | Required behavior |
|---|---|
| Peer disappears | Recover gracefully |
| Peer disconnects during transfer | Do not corrupt existing data |
| Duplicate SOS | Idempotent merge (Room `IGNORE`) |
| Duplicate manifest | Harmless, do nothing |
| Malformed SOS | Reject safely, log warning |
| Interrupted transfer | Retry later |
| Device restart | Recover persisted state from Room |
| Bluetooth unavailable | Clear handling, prompt user via system UI |
| Nearby failure | Retry/recover via exponential backoff |
| App process killed | SOS survives in Room |
| Backend unavailable | Relay continues locally |
| Network unavailable | Mesh remains independent |

## 16. Security and Privacy
- The system is **text-only**. No photos or audio are allowed.
- The `medical_snapshot` is captured securely at SOS creation time. The broader medical profile remains strictly local.
- There is no independent medical-profile sync across the mesh.
- Avoid logging sensitive payload data (e.g., phone numbers, medical conditions).
- Never log JWTs or raw installation IDs in production.

## 17. Battery Requirements
- Duty-cycled discovery is mandatory.
- No continuous scanning by default.
- BLE is preferred over Wi-Fi Direct for discovery.
- No continuous GPS tracking (location is captured once at SOS generation).
- Foreground service must utilize a persistent notification.
- Battery-aware operation is expected, with low-battery throttling where appropriate.

## 18. Permissions
Component C handles:
- Foreground location permission for the one-shot GPS fix.

Component A+B must handle:
- `BLUETOOTH_SCAN`, `BLUETOOTH_ADVERTISE`, `BLUETOOTH_CONNECT`.
- `ACCESS_WIFI_STATE`, `CHANGE_WIFI_STATE`, `NEARBY_WIFI_DEVICES`.
- Foreground service permissions.
- Ensuring the user is correctly prompted for these permissions when the relay activates.

## 19. Integration Tests
Component A+B must verify the following tests:
1. **Local SOS → Room → relay visibility:** A local SOS correctly appears in the relay mesh.
2. **A → B propagation:** SOS correctly transfers from device A to device B.
3. **A → B → C propagation:** Device C receives A's SOS even though A and C never connect.
4. **Duplicate encounter:** Manifest exchange prevents duplicate SOS payload transfers.
5. **Partial manifest:** Only missing UUIDs transfer.
6. **Gateway:** Relayed records on device B are successfully uploaded by B's GatewaySyncWorker.
7. **Restart:** Records survive a device restart and resume broadcasting.
8. **Interrupted transfer:** Disconnection during transfer causes no corruption and retries successfully later.
9. **TTL:** Cleanup behavior matches the agreed C+A+B semantics.

## 20. Known Integration Gaps

Issue: `device_id` ambiguity
Current behavior: Device generates UUID fallback; Backend generates a new one on registration.
Contract requirement: Contract wording conflicts between §1.2 and §1.9. Registration API doesn't accept client UUID.
Why unresolved: Requires core backend confirmation.
Who must decide: Backend team + Component C.

Issue: Relay persistence ownership
Current behavior: `RelayRepository` returns a list. Room persists everything.
Contract requirement: SOS must be safely stored.
Why unresolved: Exact mechanism for Relay pushing to Room is not codified.
Who must decide: Component C + Component A+B.

Issue: `relay_hop_count` semantics
Current behavior: Not incremented yet.
Contract requirement: Must increment at each hop.
Why unresolved: A+B logic not implemented. Duplicate reception semantics undefined.
Who must decide: Component C + Component A+B.

Issue: `last_relayed_at` semantics
Current behavior: Stored locally at creation.
Contract requirement: Updated at each hop.
Why unresolved: A+B logic not implemented.
Who must decide: Component C + Component A+B.

Issue: `pending_local` / `in_relay` TTL semantics
Current behavior: Only `uploaded` records are cleared after TTL.
Contract requirement: Ensure mesh isn't flooded with stale data indefinitely.
Why unresolved: Routing policy dictates safe deletion.
Who must decide: Component A+B.

## 21. Things A+B Must Not Break
> [!WARNING]
> **Strict Integration Guardrails**
> A+B must not:
> - Change API endpoints or HTTP methods.
> - Change SOS field names.
> - Add photo/audio fields.
> - Add new SOS statuses.
> - Remove `relay_hop_count`.
> - Remove `last_relayed_at`.
> - Require network to create SOS.
> - Delete uploaded SOS immediately.
> - Bypass Room/local persistence.
> - Replace epidemic routing with centralized routing.
> - Introduce continuous GPS tracking.
> - Silently change Component C interfaces/contracts.
> - Rewrite unrelated Component C code.
> - Modify Day 1 schemas without explicit team agreement.

## 22. Exact Files A+B Should Inspect
- `sih-android/relay/src/main/kotlin/com/sih/relay/RelayRepository.kt`: The current interface stub. A+B must implement this.
- `sih-android/data/src/main/kotlin/com/sih/data/db/dao/SosRequestDao.kt`: Understand how Room handles inserts and conflicts.
- `sih-android/data/src/main/kotlin/com/sih/data/worker/GatewaySyncWorker.kt`: Observe how relay records and local records are merged and uploaded.
- `sih-android/network/src/main/kotlin/com/sih/network/model/dto/SosRequestDto.kt`: The strict network contract format.
- `day1-contracts-and-repo-setup.md`: The authoritative Day 1 schemas and architecture constraints.

## 23. Recommended Integration Sequence
1. Inspect relay interface (`RelayRepository.kt`).
2. Inspect Room entities/DAO (`SosRequestDao.kt`).
3. Inspect `SosRepository`.
4. Inspect `GatewaySyncWorker`.
5. Agree on persistence ownership.
6. Implement Nearby Connections behind the existing relay boundary.
7. Connect relay persistence to Room.
8. Implement manifest exchange.
9. Implement missing-message calculation.
10. Implement SOS propagation.
11. Test duplicate/idempotent behavior.
12. Test interrupted transfers.
13. Test restart/recovery.
14. Test gateway upload.
15. Resolve TTL semantics.
16. Run Component C tests.
17. Run full Android build.
18. Perform physical A → B → C demonstration.

## 24. Handoff Acceptance Criteria
- [ ] C ↔ relay boundary understood
- [ ] Exact interfaces identified
- [ ] Room ownership understood
- [ ] Local SOS creation understood
- [ ] Relay can access local SOS
- [ ] Relay can persist received SOS
- [ ] Manifest exchange works
- [ ] Only missing UUIDs transfer
- [ ] A → B → C works
- [ ] Duplicates are idempotent
- [ ] Interrupted transfers are safe
- [ ] Relay metadata is correct
- [ ] GatewaySyncWorker uploads relayed records
- [ ] Uploaded records remain local
- [ ] TTL behavior agreed
- [ ] Battery constraints respected
- [ ] Component C tests remain passing
- [ ] Integration tests pass

## 25. Handoff Summary for A+B
### What Component C provides today
Complete local offline SOS creation, persistent Room storage, exact Day 1 network schemas, Gateway upload logic, and UI bindings.

### What A+B must implement
Nearby Connections clustering, manifest-based epidemic routing, foreground service lifecycle, and battery-aware discovery.

### What A+B must not change
Network endpoints, DTO schemas, Room entity definitions, UI/ViewModel logic, and the rule against continuous GPS/data.

### Exact integration point
`:relay` module's `RelayRepository` and the boundary where Relay pushes received messages into `SosRequestDao`.

### Known unresolved questions
Exact TTL safe-deletion for non-uploaded records, precise metadata duplicate-handling semantics, and the `device_id` vs `installation ID` backend contract ambiguity.

### First three actions A+B should take
1. Read `day1-contracts-and-repo-setup.md`.
2. Inspect `RelayRepository.kt` and `SosRequestDao.kt`.
3. Schedule an integration sync with Component C to formalize the `Relay → Room` insertion path.
