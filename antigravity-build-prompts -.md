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
