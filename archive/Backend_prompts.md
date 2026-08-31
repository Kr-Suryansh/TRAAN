# Antigravity Build Prompts
Disaster Response Coordination Platform · SIH 2026

Six prompts total: one **Master Prompt** describing the whole system, and five **Component Prompts** — one per person/pair. Each component prompt is self-contained (it repeats the schema fields it needs), so it works even if you only paste that one section.

## How to Use These

**Primary mode — 6 humans writing the code, AI assisting (use this by default):** each person keeps the Master Prompt and their own Component Prompt open as context in whatever AI-assisted tool they're using that session, and asks for help with specific pieces — one function, one endpoint, one screen — not "build my whole component unsupervised." You review every suggestion before it's committed, same as any other code. In this mode, the rules in the Global Engineering Instructions (further down) are things *you* hold yourself and your AI assistant to, not instructions an autonomous agent follows on its own — if a suggestion would rename an endpoint or skip error handling, you're the one who catches it. The Agent Completion Report section works well repurposed as your own pre-PR checklist — fill it in yourself before opening a pull request.

**Fallback mode — spawning the 5 agents in Antigravity's Manager Surface:** use this only when a component is badly behind schedule, or you want a rough first draft of a well-specified piece while focused elsewhere. This is where the heavier machinery below (parallel agent spawn, stop-and-report contract protocol, the Completion Report as something an agent produces) is actually designed for. An agent's "task complete" here is still a first draft, not a merge-ready PR — everything still goes through the same human review before merging.

- **If one person is orchestrating everything (fallback mode):** paste the Master Prompt first in Antigravity's Manager Surface, then spawn a separate agent per component using each Component Prompt — this matches Antigravity 2.0's parallel-agent workflow well, since your 5 components are already independent workstreams.
- **If each person is running their own session (primary mode):** everyone pastes the Master Prompt once for shared context, then their own Component Prompt.
- **Attach supporting files if you can:** your `day1-contracts-and-repo-setup.md` and `15-day-roadmap.md` files as project context/knowledge base. The prompts below include the essential schema fields inline so they work without this, but attaching the full docs keeps the agent honest against the exact contract if it tries to invent its own field names.
- **Review before merging, always.** These prompts get an agent to a strong first draft, not a merge-ready PR — route everything through the same feature-branch + review process from the GitHub workflow doc, even code an agent wrote.

---

## Master Prompt (Full System Context)

**[PASTE STARTS HERE]**

I'm building a disaster response coordination platform for a hackathon (15-day build, 6-person team). Here is the full system so you understand how the piece I'll ask you to build fits into the whole.

**The problem:** During floods, earthquakes, and landslides, mobile networks fail. Victims can't call for help; responders get fragmented, duplicate, delayed reports.

**The system, end to end:**
1. A citizen opens the Android app and creates an SOS — one tap is enough (location + a pre-filled medical profile, auto-attached). No photos or voice notes; text-only, to keep every relay hop cheap on both battery and bandwidth. It's stored locally immediately.
2. If the phone already has internet, it uploads the SOS directly.
3. If not, the SOS enters an offline mesh: when this phone comes near any other phone running the app, they exchange a short "manifest" (list of SOS IDs each already holds) and sync only what's missing. Every phone stores a copy of every SOS it has ever received, not just its own — this is epidemic routing. Messages spread through the crowd as phones move and meet each other.
4. **Any phone with internet can act as a gateway** — a victim's own phone once it reaches signal, a bystander, a responder, anyone. It uploads its entire stored batch to the backend.
5. The backend dedupes reports (by ID, then by location+time proximity), clusters nearby reports into "incidents," and uses Gemini to write a one-line summary and severity/urgency flags per incident, plus a periodically-refreshed overall situation brief.
6. Google OR-Tools suggests which resources (ambulances, boats, shelters — pulled from a mock IDRN-style registry, since the real IDRN has no public API) should go to which incident.
7. Authorities see everything live on a web dashboard (pushed via WebSocket, no polling) — a map, a prioritized incident list with condensed AI-generated info, and resource recommendations with reasoning attached.
8. **A human authority reviews and approves dispatch — nothing is automatic.** This creates a dispatch record.

**Tech stack:**
- Android app: Kotlin, Jetpack Compose, Room (local DB), WorkManager (background sync), Google Nearby Connections API (offline relay)
- Backend: Python, FastAPI, PostgreSQL + PostGIS (geospatial queries), WebSocket
- AI/Optimization: Gemini API (summarization), Google OR-Tools (resource assignment)
- Dashboard: React + Vite, Leaflet (maps), WebSocket client

**Repos:**
- `sih-android` — modules `:app` (UI), `:relay` (mesh/Nearby Connections), `:data` (Room/WorkManager), `:network` (Retrofit)
- `sih-backend` — `app/routers`, `app/models`, `app/services/ai`, `app/services/optimizer`, `app/services/mock_idrn`, `app/db`, `app/ws`
- `sih-dashboard` — standard Vite/React structure

**Non-negotiable design constraints:**
- SOS payloads are text-only. No photo or voice-note fields anywhere in the schema.
- Resource allocation is always a human-approved suggestion, never automatic dispatch.
- The resource database is explicitly a mock, styled on IDRN's real structure but not a live government integration — label it as such, don't imply otherwise.
- The relay engine must be battery-conscious: duty-cycled scanning (not continuous), BLE preferred over Wi-Fi Direct by default, one-shot location capture (not continuous GPS polling).

I'll now give you a detailed prompt for one specific component. Build that component to fit cleanly into this larger system — matching schema field names, endpoint paths, and event names exactly as described.


## MASTER PROMPT ADDENDUM — GLOBAL ENGINEERING INSTRUCTIONS

The following instructions are additive. The original Master Prompt above remains unchanged. These rules apply to every component agent and must be followed without changing the existing architecture, schemas, endpoints, event names, repository structure, or acceptance criteria.

### Global Agent Instructions

You are one component agent working inside a larger multi-agent software project.

#### 1. Inspect Before Modifying

Before writing or modifying code:

1. Inspect the existing repository structure.
2. Inspect existing implementations related to your component.
3. Inspect existing dependencies and configuration.
4. Reuse existing types, utilities, services, and conventions where appropriate.
5. Do not restructure unrelated parts of the repository.
6. Do not overwrite working implementations merely to use a preferred architecture.

If the repository already contains an implementation that conflicts with this prompt, identify the conflict before making a breaking change.

#### 2. Contract Immutability

The Master Prompt is the shared system contract.

Do NOT silently rename, remove, reinterpret, or change:

- API endpoints
- HTTP methods
- Request fields
- Response fields
- Schema names
- Enum values
- Status values
- WebSocket event names
- Repository/module names
- Authentication flows
- Required database relationships
- Required acceptance criteria

For example, if the contract specifies:

`POST /api/v1/sos/batch`

do not replace it with:

`POST /api/v1/sos/upload`

If you believe a contract change is necessary, stop and report:

1. The existing contract
2. The proposed change
3. Why the change is necessary
4. Which other components would be affected

Do not make the contract change automatically.

#### 3. Respect Component Ownership

Work only within the component assigned to you.

Do not implement another team's component unless a small mock, interface, adapter, or test stub is required.

Ownership boundaries:

- Mesh/Relay owns Nearby Connections and epidemic-routing behavior.
- Android App Shell owns citizen UI, Room, WorkManager, device registration, and Android-side sync.
- Backend Core owns REST APIs, database logic, deduplication, incident clustering, authentication, and WebSockets.
- AI + Resource Intelligence owns Gemini, OR-Tools, and the mock IDRN-style resource intelligence.
- Authority Dashboard owns the React/Leaflet authority UI and dashboard-side REST/WebSocket integration.

Avoid duplicating core business logic across components.

#### 4. Missing Dependencies

If another component is not ready yet:

1. Do not change the shared contract.
2. Create a mock/stub implementation matching the exact contract.
3. Clearly mark the mock as temporary.
4. Make the mock easy to replace with the real implementation.
5. Keep the component independently testable.

#### 5. Preserve Existing Work

Before changing an existing file:

- Understand why it exists.
- Check whether another component depends on it.
- Make the smallest change required.
- Avoid broad refactors that are unrelated to the current component.

Prefer incremental changes over rewrites.

#### 6. Definition of Done

Do not consider a component complete merely because the code compiles.

Every component should finish with:

- Working implementation
- Unit tests for important logic
- Integration tests where practical
- Error handling
- README/documentation updates where relevant
- `.env.example` where environment variables are required
- Database migrations where applicable
- Seed/mock data where applicable
- Exact setup commands
- Exact build commands
- Exact test commands
- Exact run commands
- List of important files changed
- Known limitations
- Integration dependencies
- Verification of the component's acceptance criteria

---

### Inter-Agent Integration Rules

The five component workstreams are independently developed but must integrate through the contracts already defined above.

#### Rule 1 — Interface First

When one component depends on another component, follow the interface already specified in the Master Prompt before implementing integration.

#### Rule 2 — Mock Before Integration

If the dependency is unavailable, use mock data or a stub that exactly matches the agreed schema and behavior.

#### Rule 3 — No Contract Drift

Do not change another component's expected interface merely to make local development easier.

#### Rule 4 — Backward Compatibility

If an existing endpoint, schema, event, or interface is already being consumed by another component, avoid breaking changes.

#### Rule 5 — Integration Documentation

When completing work, document:

- Interfaces exposed
- Interfaces consumed
- Required environment variables
- Required services
- Assumptions
- Integration steps
- Any known limitations

---

### Failure-Handling Requirements

This is a disaster-response system operating under unreliable network and device conditions. Design for failure, not only the happy path.

#### Mesh / Relay

Handle:

- Peer disappearing during discovery
- Peer disconnecting during transfer
- Interrupted payload transfer
- Duplicate SOS messages
- Repeated manifest exchange
- Reconnection
- Invalid or malformed payloads
- Expired SOS records
- Device restart
- Temporary Nearby Connections/Bluetooth failures

A failed transfer must not corrupt an existing valid SOS record.

Transfers and message merges should be idempotent wherever practical.

#### Android App

Handle:

- Airplane mode
- No network
- Network appearing/disappearing
- App being killed
- Device reboot
- WorkManager retry
- Duplicate uploads
- Failed backend requests
- Expired authentication
- Missing permissions
- Bluetooth disabled
- Nearby Connections unavailable
- Location temporarily unavailable

The application must never require network connectivity merely to create and locally store an SOS.

#### Backend

Handle:

- Duplicate batches
- Duplicate UUIDs
- Partial batch failures
- Invalid SOS payloads
- Database failures
- Gemini API failures
- OR-Tools failures
- WebSocket disconnects
- Expired JWTs
- Invalid JWTs
- Malformed requests
- Resource conflicts

AI or optimization failures must not cause raw SOS or incident data to disappear.

If Gemini is unavailable, retain the incident and raw information.

If OR-Tools fails, retain the incident without a fabricated recommendation.

#### Dashboard

Handle:

- WebSocket disconnect/reconnect
- Expired authority token
- Backend unavailable
- Empty incident data
- Empty resource data
- Missing AI summary
- Missing resource recommendation
- Stale connection state

The dashboard should make connection/error states understandable to the authority user.

---

### Security Requirements

Implement baseline security correctly even though this is a hackathon project.

#### Authentication

- Device authentication uses the specified `device_jwt`.
- Authority authentication uses the specified login/access-token flow.
- JWTs must expire.
- Refresh behavior must follow the existing contract.
- Passwords must never be stored in plaintext.

#### Secrets

Never commit:

- Gemini API keys
- Database passwords
- JWT secrets
- Other credentials

Use environment variables and provide `.env.example` files containing variable names only.

#### API Security

- Validate all incoming request data.
- Use ORM/parameterized database access rather than unsafe SQL string concatenation.
- Validate enum values.
- Validate geographic coordinates.
- Validate numeric ranges such as people counts and resource quantities.
- Reject malformed or obviously invalid payloads cleanly.

#### WebSocket Security

Validate the authority JWT before allowing access to the incident WebSocket.

#### Authorization

Authentication alone is not authorization.

Where the existing role model applies, check roles for privileged operations such as:

- Dispatch
- Resource creation
- Resource modification
- Administrative operations

---

### Gateway Upload Reliability

The Android application may contain SOS messages originating from many other phones.

When connectivity becomes available:

1. Read locally stored SOS records.
2. Upload them through the existing batch endpoint.
3. Allow repeated uploads safely.
4. Rely on backend UUID deduplication for already-known records.
5. Mark successfully acknowledged records as `uploaded`.
6. Keep uploaded records locally until TTL expiration because they may still be needed for relay.

Do not delete an SOS immediately after a successful upload.

The backend must make repeated submission of the same UUID safe and idempotent.

---

### AI Safety and Failure Rules

Gemini is an enhancement layer, not the source of truth.

If Gemini fails:

- Preserve the raw SOS reports.
- Preserve the incident.
- Record or surface the AI-processing failure where appropriate.
- Retry later where appropriate.
- Do not fabricate an AI summary.

Gemini output must be validated before being persisted.

Validate at minimum:

- Required output fields
- Boolean flags
- Severity enum
- Numeric affected-population value
- Reasonable text lengths

Invalid AI output must not corrupt an incident record.

#### OR-Tools Failure

If optimization fails:

- Keep the incident active.
- Do not dispatch anything automatically.
- Do not fabricate a recommendation.
- Surface the failure appropriately.
- Allow another optimization attempt.

---

### Human-in-the-Loop Dispatch — Non-Negotiable

The complete dispatch flow must remain:

```text
SOS Reports
     ↓
Incident
     ↓
Gemini Analysis
     ↓
OR-Tools Recommendation
     ↓
Authority Reviews
     ↓
Authority Explicitly Confirms
     ↓
Dispatch Record
```

Never implement:

```text
SOS
 ↓
AI
 ↓
Automatic Dispatch
```

There must be no code path that dispatches a resource without explicit authority confirmation.

---

### Resource Registry Clarification

The resource database remains a mock IDRN-style registry exactly as specified in the original prompt.

Distinguish clearly between:

**Source data:** real publicly available information where available.

**Local database:** this project's local/mock resource registry.

**Government integration:** no live government API integration unless separately and explicitly implemented and verified.

Do not imply that the application has a live IDRN integration.

Use wording such as:

`Mock IDRN-style resource registry populated using publicly available disaster-management resource information.`

Do not claim:

`Live IDRN integration.`

When using externally sourced resource numbers, preserve their source information where practical.

---

### Mesh Testing Strategy

Support both real-device demonstration and logic-level simulation/testing where practical.

#### Real-device demonstration

Demonstrate:

```text
Phone A
   |
   v
Phone B
   |
   v
Phone C
```

Where:

- A creates SOS-1.
- B receives SOS-1.
- C receives SOS-1 from B.
- A and C never directly communicate.

#### Simulation / Automated Testing

Where practical, provide test utilities for:

- Multiple peers
- Manifest exchange
- Missing-message calculation
- Message propagation
- Duplicate messages
- Interrupted transfer
- TTL expiration

The goal is to test epidemic-routing logic without requiring multiple physical devices for every test.

---

### Dashboard Development Strategy

Follow this development order for the dashboard:

**Phase 1:** Build the complete dashboard using hardcoded mock JSON matching the exact contracts.

**Phase 2:** Replace mock data with REST APIs.

**Phase 3:** Add WebSocket live updates.

**Phase 4:** Add reconnect and error states.

**Phase 5:** Test the complete incident-to-dispatch workflow.

Do not change backend contracts merely to simplify dashboard development.

---

### Global Verification Checklist

Before declaring a component complete, verify:

- The component builds from a clean checkout.
- The component can be started using documented commands.
- Tests can be executed using documented commands.
- Shared schemas, endpoints, and events exactly match this prompt.
- Important failure cases are handled.
- No secrets are committed.
- Component acceptance criteria have been verified one by one.
- Temporary mocks/stubs are clearly identified.
- Another team member can understand how to run and integrate the component from the documentation.

---

### Agent Completion Report

Every component agent must finish by producing this report:

```text
COMPONENT COMPLETION REPORT

Component:
Agent:

1. Implemented
- ...

2. Files Added
- ...

3. Files Modified
- ...

4. APIs / Interfaces Exposed
- ...

5. APIs / Interfaces Consumed
- ...

6. Tests Added
- ...

7. Commands
Build:
Test:
Run:

8. Environment Variables
- ...

9. Integration Dependencies
- ...

10. Known Limitations
- ...

11. Contract Changes
- None
OR
- Proposed change: ...
  Reason: ...
  Affected components: ...

12. Acceptance Criteria Verification
- [ ] ...
- [ ] ...
- [ ] ...

13. Manual Demo Steps
1. ...
2. ...
3. ...
```

---

### Recommended Git Workflow

Use a separate feature branch for each component.

```text
main
│
├── feature/mesh-relay
├── feature/android-shell
├── feature/backend-core
├── feature/ai-optimizer
└── feature/dashboard
```

Do not directly merge agent work into `main`.

Use:

```text
Agent implementation
        ↓
Local build/tests
        ↓
Commit
        ↓
Push feature branch
        ↓
Human review
        ↓
Integration testing
        ↓
Merge
```

Do not treat an agent's "task complete" status as proof that its code is merge-ready.

---

### Final Antigravity Execution Instruction

Before implementing anything:

1. Inspect the repository and determine its current state.
2. Identify the files and modules owned by this component.
3. Produce a concise implementation plan.
4. Follow the existing contracts exactly.
5. Use mocks/stubs where another component is not yet available.
6. Implement the component without unrelated rewrites.
7. Run relevant build, test, and lint/verification commands where available.
8. Verify each acceptance criterion.
9. Document setup, testing, integration dependencies, and limitations.
10. Finish with the Component Completion Report above.

If something is ambiguous, make the smallest reasonable assumption and document it. Do not invent a new architecture when the Master Prompt already defines one.


**[PASTE ENDS HERE]**

---

## Component Prompt 1 — Mesh/Relay Engine
**For: A + B**

**[PASTE STARTS HERE]**

Build the offline peer-to-peer relay engine for an Android disaster-response app, as an Android library module (`:relay`) using Kotlin.

**Core behavior — epidemic routing:**
1. Every device keeps a local store of every SOS message it has ever received or created, not just its own.
2. When two devices running this app come within range, they exchange a manifest — just a list of SOS UUIDs each device already holds — before transferring any actual data.
3. Each device computes the diff (what the other is missing) and sends only those SOS objects, never the full store.
4. Both devices merge whatever they receive into their own local store.

**Schemas to implement:**

`SOSRequest` (text-only, no photos/voice):
```
uuid: string (UUIDv4)
device_id: string
created_at: ISO8601
location: { lat: float, lng: float, accuracy_m: float? }
is_quick_sos: bool
emergency_type: enum [medical, trapped, structural_collapse, flood_rescue, fire, missing_person, unspecified]
severity_hint: enum [critical, high, medium, low]?
people_count: int?
medical_snapshot: object? (copied from local medical profile at send time)
custom_message: string?
contact_number: string?
relay_hop_count: int (increment on every hop)
last_relayed_at: ISO8601 (update on every hop — used for TTL cleanup)
status: enum [pending_local, in_relay, uploaded]
```

`RelayManifest`:
```
device_id: string
known_uuids: string[]
timestamp: ISO8601
```

**Requirements:**
- Use Google's Nearby Connections API, `P2P_CLUSTER` strategy, wrapped in a foreground service (must show a persistent notification, or Android will kill it when the screen turns off).
- Implement duty-cycled discovery: short scan/advertise windows on a timer (e.g. ~10s every 45-60s) rather than continuous scanning — this is the main battery lever, protect it carefully since the target user may be a trapped person with a nearly-dead phone.
- Default to BLE-only discovery (lower power). Only let Nearby Connections escalate to Wi-Fi Direct if it needs to for an actual transfer, then let it drop back down.
- Handle the manifest handshake as the very first thing that happens on any new connection, before any SOS payload transfer.
- Implement TTL-based cleanup: purge SOS entries older than ~48-72 hours from the local relay store based on `last_relayed_at`.
- Handle Android's requirement (introduced in an API update) that apps must explicitly prompt the user to manually enable Bluetooth/Wi-Fi rather than toggling the radios automatically — build a clear "Enable Emergency Mode" prompt/permission flow for this.
- Request and gracefully handle all required runtime permissions (Bluetooth, nearby devices, location) with clear rationale shown to the user.
- Expose a simple interface the app-shell team can call: something like `startRelay()`, `stopRelay()`, `getRelayStore(): Flow<List<SOSRequest>>`.

**Acceptance criteria:**
- Two phones with no prior contact can discover each other and exchange a manifest without any user action beyond having the app open/foregrounded.
- A message can hop from Phone A to Phone C via Phone B, where A and C are never in direct range of each other.
- Re-encountering a peer that already has all your messages transfers only the manifest, not the full SOS data again.
- Explicitly out of scope: any photo or audio transfer logic — the app is text-only.

**[PASTE ENDS HERE]**

---

## Component Prompt 2 — Android App Shell
**For: C**

**[PASTE STARTS HERE]**

Build the citizen-facing Android app UI, local storage, and backend-sync logic, in Kotlin + Jetpack Compose. This consumes the `:relay` module (built separately) for the offline mesh — you don't need to implement Nearby Connections yourself, just call its interface.

**Schemas:**

`UserMedicalProfile` (filled once during onboarding, never during an emergency):
```
name: string?
age: int?
blood_type: string?
medical_conditions: string[]
medications: string[]
allergies: string[]
emergency_contact_name: string?
emergency_contact_number: string?
```

`SOSRequest` — see Component Prompt 1 for the full shape. `medical_snapshot` should be auto-copied from the local `UserMedicalProfile` at the moment of SOS creation.

**Screens to build:**
1. **Onboarding — medical profile setup.** One-time screen, skippable, editable later from settings.
2. **Home / SOS screen.** A large, unmissable SOS button. Tapping it alone must create and store a complete, valid `SOSRequest` with `is_quick_sos: true` — zero typing required. Below/near the button, offer optional fields (emergency type picker, custom message box) for anyone who has time to add detail — never require them.
3. **Status screen.** Shows the current status of the user's own sent SOS (pending_local / in_relay / uploaded), and calls `GET /sos/{uuid}/status` to check delivery once the phone regains connectivity.

**Sync logic (WorkManager):**
- A worker constrained to `NetworkType.CONNECTED`.
- On trigger, read the *entire* local relay store (own SOS plus anything relayed in from other devices — this phone may be acting as a gateway) and POST it as one batch to `POST /api/v1/sos/batch`, auth header `Authorization: Bearer <device_jwt>`.
- Batch body shape:
```
{
  gateway_device_id: string,
  gateway_location: { lat, lng },
  uploaded_at: ISO8601,
  sos_batch: SOSRequest[]
}
```
- On success, mark uploaded SOS entries as `status: uploaded` locally (but keep them stored until TTL expiry — other phones may still need them).
- Device registration: on first app launch, call `POST /api/v1/auth/device/register` to get a `device_id` + `device_jwt`, store both securely locally.

**Local storage:** Room database holding `SOSRequest` entries and the `UserMedicalProfile`.

**Acceptance criteria:**
- The SOS button works with the phone in airplane mode — creates a valid local entry with no network call required.
- A medical profile filled in during onboarding shows up automatically in an SOS sent weeks later, without re-entering anything.
- Explicitly out of scope: camera/photo picker UI, voice recording UI, continuous background location tracking (capture location once at SOS creation time; offer a manual "update my location" action instead of a background stream).

**[PASTE ENDS HERE]**

---

## Component Prompt 3 — Backend Core
**For: D**

**[PASTE STARTS HERE]**

Build the backend for a disaster-response coordination platform using Python, FastAPI, and PostgreSQL + PostGIS. Provide a `docker-compose.yml` that brings up Postgres+PostGIS and the API with one command.

**Endpoints to implement** (base path `/api/v1`):

*Auth*
- `POST /auth/device/register` → `{device_model, app_version}` → `{device_id, device_jwt}`
- `POST /auth/authority/login` → `{email, password}` → `{access_token, user}`
- `POST /auth/authority/refresh` → `{access_token}`

*SOS ingestion*
- `POST /sos/batch` (auth: device_jwt) → accepts a batch of SOS reports, returns `{accepted_uuids, duplicate_uuids}`, 202 Accepted
- `GET /sos/{uuid}/status` (auth: device_jwt)

*Incidents*
- `GET /incidents?bbox=&severity=&status=&since=` (auth: authority)
- `GET /incidents/{id}` (auth: authority) — full detail including all source SOS reports
- `PATCH /incidents/{id}` (auth: authority) — update status
- `GET /incidents/{id}/recommendations` (auth: authority)
- `POST /incidents/{id}/dispatch` (auth: authority) → `{resource_id, quantity}` → creates a dispatch record
- `POST /incidents/{id}/refresh-summary` (auth: authority)

*Resources*
- `GET /resources?category=&status=&district=`, `GET /resources/{id}`, `POST /resources` (admin), `PATCH /resources/{id}`

*AI / optimizer*
- `GET /situation-brief`, `POST /situation-brief/refresh`
- `POST /optimize/allocate` → `{incident_ids?, constraints?}` → assignment plan

*Misc*
- `GET /health`
- `GET /stats/summary` → `{active_incidents: {critical, high, medium, low}, total_estimated_people_affected, resources: {available, deployed}, new_incidents_last_15min}`

**Core logic to build:**
- **Persist incoming reports as `SOSReport` records** — the same fields as the `SOSRequest` shape in Component Prompt 1, plus three backend-only fields: `received_at` (ISO8601), `cluster_id` (string | null, set once clustering assigns it to an `Incident`), and `received_via_gateway_device_id` (string). Store this permanently in PostgreSQL, not just transiently during processing — authorities may need to reference the original raw report later, and re-clustering may need to reprocess it.
- **Dedup:** match incoming SOS first by exact UUID, then by geohash proximity + time-window for near-duplicates from different UUIDs.
- **Clustering:** group nearby, related incidents using PostGIS's `ST_ClusterDBSCAN()`.
- **WebSocket server** at `WS /ws/incidents`, channel `incidents:updates`. Auth via JWT passed as a query param at connect (`?token=<jwt>`), since browsers can't set headers on the WebSocket handshake. Push these events: `incident_created`, `incident_updated`, `incident_dispatched`, `resource_updated`, `situation_brief_updated`.
- **JWT auth**, two separate flows: lightweight `device_jwt` for phones (no personal info, issued at registration), full login-based `access_token` for authorities with roles (DDMA/police/fire/ambulance/NDRF/SDRF/admin).

**Suggested structure:** `app/routers/` (one file per resource group above), `app/models/` (Pydantic schemas), `app/db/` (PostGIS setup, Alembic migrations), `app/ws/` (connection manager).

**Acceptance criteria:**
- Two SOS reports with different UUIDs but near-identical location/time get merged into the same incident cluster.
- A WebSocket client connected with a valid token receives an `incident_created` event within a second of a new batch being processed.
- Swagger UI at `/docs` reflects these exact schemas — that becomes the live contract other teams build against.

**[PASTE ENDS HERE]**

---

## Component Prompt 4 — AI + Resource Intelligence
**For: E**

**[PASTE STARTS HERE]**

Build the AI processing and resource-recommendation services for a disaster-response backend, as Python modules that plug into an existing FastAPI app (`app/services/ai`, `app/services/optimizer`, `app/services/mock_idrn`).

**Job 1 — Per-cluster summarization (Gemini API):**
Input: the merged text fields from every SOS report in a cluster — `emergency_type`, `medical_snapshot`, `custom_message` for each (text-only, no images to process). Output, written onto the `Incident` record:
```
ai_summary: string (one line, e.g. "12 reports near Ward 5, includes 3 medical cases, water rising")
flags: { medical_emergency: bool, trapped: bool, elderly_or_children: bool, structural_damage: bool }
estimated_people_affected: int
severity: enum [critical, high, medium, low]
```

**Job 2 — Situation brief (Gemini API):**
Regenerate every 5-10 minutes (background job), synthesizing current active incidents by severity, resource availability, and any critical cases, into one short skimmable paragraph. Cache the result; serve via `GET /situation-brief`.

**Job 3 — Resource optimizer (Google OR-Tools):**
Input: unresolved incidents (location, severity, `people_count`) + available resources (location, category, `quantity_available`). Output: an assignment plan `[{incident_id, resource_id, quantity, reasoning}]`.
Two trigger paths:
- Automatic baseline: computed and written to `Incident.recommended_resources` whenever a cluster is created or meaningfully updated.
- On-demand full re-solve: `POST /optimize/allocate`, accepting optional `incident_ids` (defaults to all unresolved) and `constraints`.

**Job 4 — Mock resource registry (styled on IDRN, not a live integration):**

Schema (`Resource`):
```
resource_id: string
category: enum [medical, rescue, shelter, transport, communication]
sub_type: string (e.g. ambulance, motorboat, JCB, relief_camp, generator)
custodian_agency: string
quantity_total: int
quantity_available: int
status: enum [available, partially_deployed, deployed, maintenance]
location: { lat, lng, district }
contact: string
last_updated_at: ISO8601
```

Build a seed script (`seed.py`) that loads real district data into this table directly via the DB session (not through the API). Source real numbers from:
- **https://idrn.nidm.gov.in/** — the "Country Wide Query for Resource Inventory — Open to All" tool, no login required. Select state/district/category/item.
- District Disaster Management Plan PDFs — searchable as `"<district name> district disaster management plan" filetype:pdf` — for resource inventories and real agency names.
Fill only genuine gaps in what you find with clearly-reasonable, clearly-labeled estimates — don't invent the whole dataset.

**Acceptance criteria:**
- Given three sample SOS reports describing the same flooding event in different words, the summarizer produces one coherent `ai_summary`, not three.
- The optimizer never recommends more of a resource than `quantity_available`.
- The seed script, run once, populates a demo-district-worth of resources with at least some real, source-traceable numbers.

**[PASTE ENDS HERE]**

---

## Component Prompt 5 — Authority Dashboard
**For: F**

**[PASTE STARTS HERE]**

Build the authority-facing web dashboard using React + Vite, with Leaflet for mapping.

**Data sources:**
- REST (initial load / actions): `GET /incidents`, `GET /incidents/{id}`, `PATCH /incidents/{id}`, `POST /incidents/{id}/dispatch`, `GET /resources`, `GET /situation-brief`, `GET /stats/summary`
- WebSocket (live updates, no polling): connect to `WS /ws/incidents?token=<authority_access_token>`, listen for `incident_created`, `incident_updated`, `incident_dispatched`, `resource_updated`, `situation_brief_updated`

**Screens/components to build:**

1. **Summary strip** (top of dashboard) — from `GET /stats/summary`: active incidents by severity, total estimated people affected, resources available vs. deployed, new incidents in the last 15 minutes. Refresh on relevant WebSocket events, not on a timer.

2. **Map** — Leaflet, severity-colour-coded incident cluster markers plus resource markers, from `GET /incidents` and `GET /resources`.

3. **Incident list** — sorted by severity/recency. Each card shows: `ai_summary`, a severity badge, `report_count`, `estimated_people_affected`, the `flags` as small icons (medical/trapped/elderly-or-children/structural), `area_name`, time elapsed since `first_reported_at`, and `status`. No photo thumbnail — the system is text-only.

4. **Incident detail view** — full incident record, list of contributing raw SOS reports, and `recommended_resources` with each item's `reasoning` shown as plain text next to it.

5. **Dispatch modal** — authority picks a resource and quantity from the recommendation (or overrides it), confirms, calls `POST /incidents/{id}/dispatch`. This is always a human confirmation step — never auto-submit.

6. **Resource panel** — list from `GET /resources`, grouped by category, live-updated on `resource_updated` events.

7. **Situation brief** — displays the text from `GET /situation-brief`, updates on `situation_brief_updated`.

**Build order suggestion:** build every component against hardcoded mock JSON first (matching the shapes above exactly), then swap in the real REST calls, then add the WebSocket layer last — that way the UI is fully testable before the backend is ready.

**Acceptance criteria:**
- A new incident appearing via WebSocket shows up on both the map and the incident list within a second, with no page refresh.
- The dispatch flow always requires an explicit confirm click — there is no path that dispatches a resource without a human clicking a button.

**[PASTE ENDS HERE]**


---

# FINAL ORCHESTRATION NOTE — ADDITIVE

The original Master Prompt and all five original Component Prompts above remain unchanged. The Global Engineering Instructions in the Master Prompt Addendum apply to every component agent.

Recommended multi-agent flow:

**All five agents should be spawned in parallel on Day 1** — every component builds against the mocked contracts first (see the 15-day roadmap), so none of them are blocked waiting on another to "finish." The diagram below shows real dependencies that matter for *integration order* later, not an order to start work in.

```text
                     MASTER PROMPT
                          |
     +---------+----------+----------+---------+
     |         |          |          |         |
     v         v          v          v         v
  Agent A+B  Agent C   Agent D    Agent E   Agent F
  Mesh/Relay Android   Backend   AI/OR-Tools Dashboard

  All five start immediately, against mocks.
  Real dependency to track: E's writes to Incident
  records and F's live data both need D's actual
  schema/endpoints — but E and F still build and
  test against mocked versions of that contract
  from day one, then swap to the real thing once
  D exposes it (Days 5-8, per the roadmap).
```

Development principle:

```text
CONTRACT FIRST
     ↓
MOCK / STUB
     ↓
IMPLEMENT
     ↓
TEST
     ↓
REVIEW
     ↓
INTEGRATE
```

Do not allow one agent to silently change a shared contract and force the other agents to adapt.

For the Manager Surface, use the Master Prompt with the Global Engineering Instructions as the shared system context. Then spawn the component agents using the corresponding original Component Prompt sections.
