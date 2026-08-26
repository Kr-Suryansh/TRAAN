# Antigravity Prompt — Component F (`mahima`) Remediation

You are working on the TRAAN repository.

Your task is to remediate the `mahima` branch implementation of **Component F — Authority Dashboard** so that it is contract-correct and integration-ready with the existing TRAAN backend.

## Source of Truth / Required Reading

Before changing code, inspect these sources in the repository/workspace:

1. `day1-contracts-and-repo-setup.md`
2. The Component F / master prompt available in the project
3. `sih-dashboard/Agent.md`
4. `sih-dashboard/ApiEndpoints.md`
5. The actual backend implementation on the `backend` branch, especially:
   - incident router/model/schema
   - resource endpoints
   - situation brief
   - stats
   - WebSocket event implementation
6. The existing `sih-dashboard` code on the current `mahima` branch.

Do NOT replace the agreed Day 1 contract with assumptions from general knowledge.

The canonical contract is the authority. If there is a conflict between documentation and actual backend behavior, identify it explicitly and reconcile it deliberately rather than silently changing unrelated code.

---

# Primary Objective

Make Component F work correctly in **real backend mode**:

```text
VITE_MOCK_MODE=false
```

The dashboard must correctly consume the actual backend while preserving the existing UI, WebSocket architecture, human-in-the-loop dispatch safeguard, and mock mode.

Do not rewrite the dashboard from scratch.

Do not remove existing functionality merely to make tests pass.

Do not weaken TypeScript types to hide integration problems.

Do not introduce automatic dispatch under any circumstances.

---

# P0 — REQUIRED FIXES

## 1. Fix `GET /incidents` Response Handling

Current Component F incorrectly assumes:

```text
GET /incidents → Incident[]
```

The actual backend returns a paginated response:

```json
{
  "items": [],
  "total": 0,
  "page": 1,
  "size": 50
}
```

### Required work

Inspect:

```text
sih-dashboard/src/api/incidents.ts
sih-dashboard/src/hooks/useIncidents.ts
sih-dashboard/src/components/IncidentList.tsx
```

Implement a properly typed paginated response.

Do NOT simply cast the object to `Incident[]`.

The API layer should explicitly understand the backend response.

At minimum:

```ts
interface PaginatedIncidentsResponse {
  items: Incident[];
  total: number;
  page: number;
  size: number;
}
```

Then make the hook consume `items`.

Preserve pagination metadata so that the architecture can support pagination cleanly.

If the existing Component F contract requires pagination UI, implement it. If the contract only requires the current page, do not invent unnecessary UI; ensure the current page works correctly and document the limitation.

---

# P0 — REQUIRED FIX

## 2. Fix `refresh-summary` Response Handling

Current F treats:

```text
POST /incidents/{incident_id}/refresh-summary
```

as though it immediately returns an `Incident`.

The current backend returns:

```json
{
  "status": "refresh_queued"
}
```

This is an asynchronous trigger.

### Required behavior

The flow must become:

```text
Authority clicks Refresh Summary
        ↓
POST /incidents/{id}/refresh-summary
        ↓
Backend queues refresh
        ↓
Keep existing Incident state
        ↓
Wait for incident_updated WebSocket event
        ↓
Update displayed Incident
```

Do NOT do:

```ts
setIncident(response.data as Incident)
```

when the response is only `{status: "refresh_queued"}`.

The UI should provide appropriate feedback such as:

```text
Summary refresh queued
```

without destroying the currently displayed incident.

If the backend later sends `incident_updated`, the existing WebSocket logic should update the incident normally.

---

# P1 — REQUIRED FIX

## 3. Fix Raw SOS Report Field Mapping

The current backend uses:

```text
source_sos_reports
```

while Component F expects:

```text
sos_reports
```

Inspect:

```text
sih-dashboard/src/api/incidents.ts
sih-dashboard/src/components/IncidentDetail.tsx
sih-dashboard/src/types/index.ts
```

Normalize the backend response in the API layer rather than scattering backend-specific field checks throughout the UI.

The resulting UI must correctly display the contributing raw SOS reports for an incident.

Preserve the canonical backend field in the API contract.

Do not silently rename the shared backend schema.

---

# P1 — REQUIRED FIX

## 4. Preserve Canonical `recommended_resources` Contract

The canonical recommendation shape is:

```ts
{
  resource_type: string;
  quantity: number;
  reasoning: string;
}
```

Do not make all fields optional simply to accommodate speculative backend variants.

If defensive compatibility is genuinely required, isolate it inside the API normalization layer and convert it into the canonical internal type.

The UI should receive a clean canonical type.

---

# WebSocket Requirements

Do not break the existing WebSocket architecture.

Verify that these events continue to work:

```text
incident_created
incident_updated
incident_dispatched
resource_updated
situation_brief_updated
```

Verify:

```text
WS /ws/incidents?token=<authority_access_token>
```

continues to authenticate correctly.

Verify reconnect behavior still triggers REST resynchronization.

Verify malformed/unknown messages remain harmless.

Do not introduce polling timers as a replacement for WebSockets.

---

# Human-in-the-Loop Requirement — NON-NEGOTIABLE

The dispatch workflow must remain:

```text
AI / OR-Tools recommendation
        ↓
Authority reviews recommendation
        ↓
Authority explicitly confirms
        ↓
Dispatch API call
```

Automatic dispatch is prohibited.

Do NOT remove or bypass:

- the confirmation checkbox
- the explicit Confirm Dispatch button
- quantity validation
- human confirmation tests

Do not make dispatch happen from:

- `incident_created`
- `incident_updated`
- AI recommendation generation
- resource recommendation generation
- WebSocket events

The only dispatch action must be an explicit authority interaction.

---

# Mock Mode Requirements

Preserve:

```text
VITE_MOCK_MODE
```

Mock mode must continue working.

Real mode must work with:

```text
VITE_MOCK_MODE=false
```

Do not delete the existing mock dataset.

Keep the Resource Registry explicitly labeled:

```text
Mock IDRN-style
```

Do not claim that the dashboard is connected to a live IDRN government registry unless the actual backend provides such an integration.

---

# Error Handling

Preserve and improve existing error handling.

The dashboard must gracefully handle:

- network failure
- 401 Unauthorized
- 403 Forbidden
- 422 validation failure
- 503 backend unavailable
- WebSocket disconnect
- malformed WebSocket message
- refresh-summary queueing
- empty incident list
- empty resource list

Do not swallow errors silently when the authority user needs to know that an operation failed.

---

# Testing Requirements

Before finishing, inspect and update the existing tests.

At minimum, add/update tests for:

## Incident list API

Verify that:

```json
{
  "items": [ ... ],
  "total": 1,
  "page": 1,
  "size": 50
}
```

is correctly converted into dashboard incident state.

Verify the dashboard does not attempt array operations directly on the paginated wrapper.

## Incident detail

Verify that:

```json
{
  "source_sos_reports": [...]
}
```

appears correctly in Incident Detail.

## Refresh summary

Verify that:

```json
{
  "status": "refresh_queued"
}
```

does NOT replace the current Incident object.

Verify the UI reports that the refresh was queued.

Then simulate:

```text
incident_updated
```

and verify that the updated incident replaces the old incident.

## Dispatch safeguard

Preserve all existing human-in-the-loop tests.

Verify dispatch is impossible without explicit confirmation.

## WebSocket

Preserve tests for:

- `incident_created`
- `incident_updated`
- `resource_updated`
- `situation_brief_updated`
- reconnect
- malformed message
- unknown event

---

# Code Quality Requirements

Follow the existing project architecture.

Prefer:

```text
API normalization
        ↓
canonical TypeScript models
        ↓
hooks/state
        ↓
UI components
```

Avoid:

```text
backend-specific response checks
        ↓
scattered throughout UI components
```

Do not use:

```ts
any
```

to bypass type errors.

Do not suppress TypeScript errors merely to make the build pass.

Do not introduce unnecessary dependencies.

Do not rewrite working components without a reason.

Keep changes focused on Component F remediation.

---

# Documentation Requirements

After fixing the code, update:

```text
sih-dashboard/Agent.md
sih-dashboard/ApiEndpoints.md
```

so they describe the actual implementation.

Do not claim that D↔F normalization is complete unless it actually is.

Document:

- paginated incident response
- incident detail raw SOS field
- asynchronous refresh-summary behavior
- WebSocket-driven incident update after refresh

Remove outdated claims such as "Component 5" if the repository's agreed terminology is Component F.

---

# Validation

Run all applicable commands from `sih-dashboard`:

```bash
npm install
npm run build
npm run test
```

Also run lint if a lint script exists.

The final implementation must pass TypeScript compilation.

Do not stop after unit tests.

Perform a code-level verification of the real backend integration path:

```text
VITE_MOCK_MODE=false
        ↓
GET /api/v1/incidents
        ↓
paginated response
        ↓
Incident[] state
        ↓
IncidentList + IncidentMap
```

Also verify:

```text
Incident Detail
        ↓
source_sos_reports
        ↓
raw SOS reports displayed
```

and:

```text
Refresh Summary
        ↓
refresh_queued
        ↓
current Incident preserved
        ↓
incident_updated WS event
        ↓
updated Incident displayed
```

---

# Important Constraints

1. Do not modify unrelated TRAAN components.
2. Do not modify the backend just to hide a frontend bug unless the shared contract itself is demonstrably wrong and the change is explicitly justified.
3. Do not invent API fields.
4. Do not remove safety checks.
5. Do not replace WebSockets with polling.
6. Do not replace real backend integration with mocks.
7. Do not declare success without running build/tests.
8. Do not merely describe fixes — actually implement them.
9. Keep the existing UI design unless a change is necessary for correctness.
10. Preserve backward compatibility with mock mode.

---

# Final Report Required

When finished, provide a concise remediation report containing:

## Changed Files

List every file modified and why.

## Contract Fixes

For each D↔F mismatch, state:

```text
Old behavior
New behavior
Source-of-truth contract
```

## Tests

Report:

```text
npm run build → PASS/FAIL
npm run test → PASS/FAIL
lint → PASS/FAIL/N/A
```

Include the test count if available.

## Remaining Issues

List any remaining issue that prevents full production/demo acceptance.

Do not claim GREEN/READY if any P0 or P1 issue remains.

## Final Status

Use exactly one:

```text
GREEN — READY FOR INTEGRATION
YELLOW — FUNCTIONAL BUT HAS NON-BLOCKING ISSUES
RED — NOT READY
```

The objective is to bring Component F from the current **RED / HOLD MERGE** state to **GREEN — READY FOR INTEGRATION**, but only if the implementation and validation genuinely support that conclusion.
