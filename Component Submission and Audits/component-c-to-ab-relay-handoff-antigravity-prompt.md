# Antigravity Prompt — Component C → A+B Relay Handoff

Create a detailed handoff document for the developers responsible for **Component A+B — Mesh/Relay Engine**, using the **current repository state**.

Before writing:
1. Inspect the current repository and actual Component C implementation.
2. Inspect the current `:relay` module/interface/stub.
3. Inspect Room entities/DAOs, repositories, WorkManager workers, DTOs, and tests relevant to relay integration.
4. Compare against `day1-contracts-and-repo-setup.md` and `Updated_antigravity-build-prompts.md`.
5. Base the guide on actual code and authoritative contracts. Do not describe an older version.
6. Do not modify production code.
7. Do not invent interfaces, fields, endpoints, or behaviors. Mark unresolved issues as integration decisions.

Create only:

`COMPONENT_C_TO_AB_RELAY_HANDOFF.md`

## 1. Purpose

Explain Component C ownership, A+B ownership, the current C ↔ A+B integration boundary, and that C does not implement Nearby Connections or epidemic routing.

## 2. Current Component C Architecture

Inspect and document the actual relevant files/classes, especially:

- `RelayRepository`
- `SosRepository`
- `SosRequestDao`
- `SosRequestEntity`
- `DataModule`
- `GatewaySyncWorker`
- `DevicePreferences`
- `SosConstants`
- relevant DTO/model classes

For each, explain its role, owner, integration relevance, and whether A+B may modify it.

## 3. Current Relay Interface

Read the actual `:relay` interface/stub. Document every public method with:

- name
- parameters
- return type
- current behavior
- caller
- expected A+B behavior
- compatibility requirements

Do not invent methods. If the interface is insufficient, explicitly identify the gap.

## 4. SOS Data Ownership

Document the actual lifecycle:

```text
User creates SOS
    ↓
Component C
    ↓
Room
    ↓
SOSRequestEntity
    ↓
Relay can read/receive records
    ↓
GatewaySyncWorker
    ↓
POST /api/v1/sos/batch
```

Explain that a device may hold its own SOS and SOS received from other devices. Uploaded records remain locally stored until TTL cleanup.

## 5. Exact SOSRequest Contract

Document the exact fields:

- `uuid`
- `device_id`
- `created_at`
- `location.lat`
- `location.lng`
- `location.accuracy_m`
- `is_quick_sos`
- `emergency_type`
- `severity_hint`
- `people_count`
- `medical_snapshot`
- `custom_message`
- `contact_number`
- `relay_hop_count`
- `last_relayed_at`
- `status`

Clearly identify `status` as device-side only, with:

- `pending_local`
- `in_relay`
- `uploaded`

Explain that `status` must not be sent in the backend SOS DTO.

## 6. What A+B Must Implement

Document A+B responsibilities:

- Google Nearby Connections
- `P2P_CLUSTER`
- discovery/advertising
- duty-cycled discovery
- BLE-preferred operation
- connection lifecycle
- manifest exchange
- missing UUID calculation
- SOS transfer
- duplicate handling
- interrupted transfer handling
- reconnect/recovery
- malformed payload handling
- `relay_hop_count`
- `last_relayed_at`
- TTL coordination
- foreground service
- persistent notification
- permissions
- device restart recovery
- battery-aware behavior

Make clear these are not Component C responsibilities.

## 7. Relay → C Integration

Explain how a peer-received SOS becomes available to C:

```text
Phone B receives SOS from A
    ↓
Relay validates
    ↓
SOS merged/persisted
    ↓
Room contains SOS
    ↓
C can read it
    ↓
GatewaySyncWorker uploads it when connected
```

Identify the exact current interfaces/classes. If something is missing, explicitly write:

`Integration gap — A+B and C must agree on this interface before merge.`

Do not invent a final API.

## 8. C → Relay Integration

Explain what C expects from the relay engine, including:

- starting/stopping relay
- accessing relay-held SOS records
- receiving newly-created local SOS
- receiving peer SOS
- persistence synchronization
- status transitions
- relay metadata updates

Use actual interfaces where possible.

## 9. Persistence Ownership

Explain:

- Room is durable local storage.
- SOS data must survive app/process/device restarts.
- Relay must not create a conflicting authoritative SOS database.
- Duplicate UUIDs must be idempotent.
- Failed transfers must never corrupt valid records.
- Received SOS must be persisted safely.
- GatewaySyncWorker must see relayed records.

Inspect the actual DAO and document its behavior.

## 10. Manifest Exchange

Document:

```text
A connects to B
    ↓
A sends RelayManifest
B sends RelayManifest
    ↓
Each calculates missing UUIDs
    ↓
Only missing SOS records transfer
```

Schema:

```json
{
  "device_id": "string",
  "known_uuids": ["string", "..."],
  "timestamp": "ISO8601"
}
```

State that full-store transfer on every encounter is prohibited and re-encounters with no missing UUIDs transfer no SOS payloads.

## 11. Relay Metadata

Document exact semantics for:

- `relay_hop_count`
- `last_relayed_at`

Explain increment/update behavior and that duplicate reception must not cause uncontrolled hop-count inflation.

If exact duplicate semantics are undefined, mark:

`Integration decision required: exact duplicate metadata update semantics must be agreed by C + A+B.`

Do not invent a rule.

## 12. Status Lifecycle

Inspect actual implementation and explain:

```text
pending_local
in_relay
uploaded
```

Identify which component performs each transition.

Do not invent statuses.

Uploaded records must not be immediately deleted.

## 13. Gateway Upload Interaction

Document how relay-held records reach `GatewaySyncWorker`:

```text
Local Room SOS store
    ↓
GatewaySyncWorker
    ↓
complete local SOS batch
    ↓
POST /api/v1/sos/batch
```

Explain that the batch may contain the device's own SOS and relayed SOS, and that accepted/duplicate records become `uploaded` but remain stored until TTL.

## 14. TTL and Cleanup

Document the current TTL configuration and actual DAO behavior.

Explain the existing TODO concerning safe deletion of `pending_local` and `in_relay` records.

Do not instruct A+B to blindly delete old records. State that safe cleanup depends on final relay lifecycle semantics.

## 15. Failure Handling

Create a table covering:

| Failure | Required behavior |
|---|---|
| Peer disappears | Recover gracefully |
| Peer disconnects during transfer | Do not corrupt existing data |
| Duplicate SOS | Idempotent merge |
| Duplicate manifest | Harmless |
| Malformed SOS | Reject safely |
| Interrupted transfer | Retry later |
| Device restart | Recover persisted state |
| Bluetooth unavailable | Clear handling |
| Nearby failure | Retry/recover |
| App process killed | SOS survives |
| Backend unavailable | Relay continues locally |
| Network unavailable | Mesh remains independent |

Base this on the contracts and current implementation.

## 16. Security and Privacy

Document:

- text-only SOS
- no photos/audio
- medical profile remains local
- `medical_snapshot` is created at SOS creation
- no independent medical-profile sync
- avoid sensitive payload logging
- never log JWTs

## 17. Battery Requirements

Document the mandatory requirements:

- duty-cycled discovery
- no continuous scanning by default
- BLE preferred
- no continuous GPS
- foreground service
- persistent notification
- battery-aware operation
- low-battery throttling where appropriate

## 18. Permissions

Inspect current Android permission handling and distinguish:

- permissions C handles
- permissions A+B must handle
- Nearby/Bluetooth permissions
- user-facing permission responsibilities

Do not duplicate ownership unnecessarily.

## 19. Integration Tests

Create concrete tests for:

1. Local SOS → Room → relay visibility.
2. A → B propagation.
3. A → B → C propagation where A and C never connect.
4. Duplicate encounter → manifest only, no duplicate SOS transfer.
5. Partial manifest → only missing UUIDs transfer.
6. Gateway → relayed records uploaded.
7. Restart → records survive.
8. Interrupted transfer → no corruption and later retry.
9. TTL → behavior matches final C+A+B agreement.

Include exact expected results.

## 20. Known Integration Gaps

Inspect the repository and list every real unresolved dependency, especially:

- `device_id` ambiguity
- relay persistence ownership
- `relay_hop_count` semantics
- `last_relayed_at` semantics
- `pending_local` / `in_relay` TTL semantics
- exact `RelayRepository` integration requirements

For each:

```text
Issue:
Current behavior:
Contract requirement:
Why unresolved:
Who must decide:
```

Do not manufacture gaps.

## 21. Things A+B Must Not Break

Create a prominent warning section.

A+B must not:

- change API endpoints or HTTP methods
- change SOS field names
- add photo/audio fields
- add new SOS statuses
- remove `relay_hop_count`
- remove `last_relayed_at`
- require network to create SOS
- delete uploaded SOS immediately
- bypass Room/local persistence
- replace epidemic routing with centralized routing
- introduce continuous GPS tracking
- silently change Component C interfaces/contracts
- rewrite unrelated C code
- modify Day 1 schemas without explicit team agreement

## 22. Exact Files A+B Should Inspect

Inspect the repository and list the exact paths A+B should open before implementation.

For every path explain:

- why it matters
- what A+B must understand
- whether A+B may modify it

Do not guess paths.

## 23. Recommended Integration Sequence

Provide this sequence, adapting it to the actual repository:

1. Inspect relay interface.
2. Inspect Room entities/DAO.
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

Include checkboxes for:

- C ↔ relay boundary understood
- exact interfaces identified
- Room ownership understood
- local SOS creation understood
- relay can access local SOS
- relay can persist received SOS
- manifest exchange works
- only missing UUIDs transfer
- A → B → C works
- duplicates are idempotent
- interrupted transfers are safe
- relay metadata is correct
- GatewaySyncWorker uploads relayed records
- uploaded records remain local
- TTL behavior agreed
- battery constraints respected
- Component C tests remain passing
- integration tests pass

## 25. Handoff Summary for A+B

End with:

### What Component C provides today

### What A+B must implement

### What A+B must not change

### Exact integration point

### Known unresolved questions

### First three actions A+B should take

The summary must be practical enough for A+B to begin implementation without rediscovering Component C.

## Final Requirements

- Inspect the current repository before generating the guide.
- Ground every claim in actual code or authoritative contracts.
- Preserve contract terminology.
- Do not silently resolve ambiguities.
- Do not modify production code.
- Create only `COMPONENT_C_TO_AB_RELAY_HANDOFF.md`.

After creating it, report:

1. File created.
2. Files/classes inspected.
3. Integration boundary identified.
4. Known unresolved issues.
5. Whether the handoff is ready for A+B.
