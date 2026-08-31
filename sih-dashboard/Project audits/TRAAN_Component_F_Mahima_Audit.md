# Component F Audit — `mahima` Branch of TRAAN

## Audit Scope

This audit covers the `mahima` branch of `Kr-Suryansh/TRAAN` as Component F (Authority Dashboard), against:

- Master Prompt / Component F requirements
- Day 1 contracts and repository setup
- The actual `backend` branch where D↔F integration was relevant

## Overall Verdict

**RED — NOT MERGE-READY**

The dashboard implementation is substantial and the UI/WebSocket architecture is generally strong. However, there are concrete D↔F runtime contract mismatches that can break the dashboard when connected to the real backend.

The most important blockers are:

1. `GET /incidents` response-shape mismatch.
2. `refresh-summary` response-shape mismatch.
3. `source_sos_reports` vs `sos_reports` mismatch for incident details.
4. Backend pagination is not handled.

---

# 1. File-by-File Audit

## Root / Configuration

| File | Verdict | Finding |
|---|---|---|
| `sih-dashboard/.env.example` | PASS | Correct API and WebSocket variables; no secrets. |
| `sih-dashboard/.gitignore` | PASS | Ignores `node_modules`, `dist`, `*.local`, logs and IDE files. |
| `sih-dashboard/.oxlintrc.json` | PASS | Lint configuration exists. |
| `sih-dashboard/package.json` | PASS | React + Vite + Leaflet + WebSocket-capable stack is appropriate. |
| `sih-dashboard/package-lock.json` | PASS* | Lockfile is committed. Clean-install/build still needs actual execution verification. |
| `sih-dashboard/index.html` | PASS | Normal Vite entry. |
| `sih-dashboard/tsconfig.json` | PASS | Standard project configuration. |
| `sih-dashboard/tsconfig.app.json` | PASS* | Strong compiler checks including unused locals/parameters. |
| `sih-dashboard/tsconfig.node.json` | PASS | Standard Vite configuration typing. |
| `sih-dashboard/vite.config.ts` | PASS | Vite + React + Vitest/jsdom configured correctly. |

## Documentation

| File | Verdict | Finding |
|---|---|---|
| `Agent.md` | FAIL/P1 | Claims defensive D↔F normalization is complete, but does not handle the actual backend `source_sos_reports` field. Also refers to the component as "Component 5" instead of Component F. |
| `ApiEndpoints.md` | FAIL/P0 | Documents `GET /incidents` as returning `Incident[]`, while the actual backend returns a paginated `{items,total,page,size}` object. It also documents the incident-detail response differently from the actual backend. |
| `README.md` | PASS* | Setup is documented, but it says "Component 5" and has a minor mock-mode wording inconsistency. |
| `Logs.md` | P2 | Useful development history, but logs do not prove current clean-build/integration correctness. |
| `README.txt` | P2 | Root-level placeholder; unrelated to core F functionality. |

Documentation currently overstates integration correctness.

---

# 2. API Layer

## `src/api/auth.ts`

**PASS**

Correctly consumes:

`POST /api/v1/auth/authority/login`

and expects:

```json
{
  "access_token": "...",
  "user": {
    "user_id": "...",
    "name": "...",
    "role": "...",
    "agency": "...",
    "email": "..."
  }
}
```

This matches the Day 1 authority-login contract.

Minor limitation: the dashboard logs the user out on 401 rather than implementing refresh-token behavior. This is not a P0 because the Component F requirements do not explicitly require refresh tokens.

## `src/api/client.ts`

**PASS**

Good implementation:

- centralized Axios client
- `/api/v1` base path
- JWT `Authorization: Bearer ...`
- 401 handling
- session cleanup
- `auth:expired` event
- mock/real backend switch

One deployment concern: when `VITE_MOCK_MODE` is absent, the implementation defaults to mock mode. For final deployment, real/mock mode should be explicit.

## `src/api/incidents.ts`

### FAIL — P0

This is the largest issue.

F currently expects:

```text
GET /incidents → Incident[]
```

The actual backend returns:

```json
{
  "items": [],
  "total": 0,
  "page": 1,
  "size": 50
}
```

Therefore, with:

```text
VITE_MOCK_MODE=false
```

`fetchIncidents()` can return an object where the rest of the dashboard expects an array.

The resulting path is:

```text
GET /incidents
    ↓
{ items, total, page, size }
    ↓
useIncidents()
    ↓
setIncidents(data)
    ↓
IncidentList
    ↓
array operations
    ↓
runtime failure
```

### Required fix

Normalize the response:

```ts
const data = res.data;
return Array.isArray(data) ? data : data.items;
```

Preferably, properly type the paginated response and expose pagination rather than hiding it.

This is a real runtime integration blocker.

---

## `fetchIncident()`

### FAIL — P1

F expects either:

```json
{
  "incident": {},
  "sos_reports": []
}
```

or a flat object containing:

```text
sos_reports
```

The actual backend uses:

```text
source_sos_reports
```

Therefore, incident details can load while the raw SOS section remains empty.

### Required fix

Normalize the actual backend field:

```text
source_sos_reports → local sosReports
```

Do not silently redefine the shared backend contract.

---

## `patchIncident()`

**PASS**

Correct endpoint:

```text
PATCH /incidents/{incident_id}
```

Correct request:

```json
{
  "status": "new | acknowledged | dispatched | resolved"
}
```

## `dispatchResource()`

**PASS**

Correct endpoint:

```text
POST /incidents/{incident_id}/dispatch
```

Correct body:

```json
{
  "resource_id": "string",
  "quantity": 1
}
```

Matches the contract and backend.

## `refreshSummary()`

### FAIL — P0/P1

F expects:

```text
POST /incidents/{id}/refresh-summary
→ Incident
```

The current backend returns:

```json
{
  "status": "refresh_queued"
}
```

F then treats that response as an `Incident` and replaces the current incident state.

This can corrupt the detail view or cause runtime errors when fields such as `severity`, `area_name`, `recommended_resources`, etc. are accessed.

### Correct integration behavior

The endpoint should be treated as asynchronous:

```text
POST refresh-summary
        ↓
refresh queued
        ↓
keep current Incident
        ↓
wait for incident_updated WebSocket event
```

Alternatively, D must be changed to return a real `Incident` according to the agreed contract.

---

# 3. `src/api/resources.ts`

**PASS**

Correctly implements resource access:

- `GET /resources`
- `GET /resources/{id}`
- `PATCH /resources/{id}`

The Component F requirements do not require resource creation from the dashboard.

---

# 4. `src/api/stats.ts`

**PASS**

Correctly consumes:

```text
GET /stats/summary
GET /situation-brief
```

The `StatsSummary` structure matches Day 1.

The situation brief structure also matches:

```json
{
  "text": "string",
  "updated_at": "ISO8601"
}
```

---

# 5. `src/types/index.ts`

## PASS* — with contract looseness

Most schemas are accurately represented:

- `UserMedicalProfile`
- `SOSRequest`
- `Incident`
- `Resource`
- `DispatchRecord`
- `AuthorityUser`
- `StatsSummary`
- WebSocket events

The SOS schema remains text-only and does not introduce photos/audio.

### Minor contract issue

The canonical Day 1 contract specifies:

```ts
recommended_resources: {
  resource_type: string;
  quantity: number;
  reasoning: string;
}[]
```

F instead allows:

```ts
resource_type?: string;
resource_id?: string;
```

This weakens the canonical contract.

Unless the shared contract is intentionally changed, it should remain:

```ts
resource_type: string;
quantity: number;
reasoning: string;
```

---

# 6. `src/context/AuthContext.tsx`

**PASS**

Good implementation:

- session-scoped token
- authority user storage
- logout
- auth-expiry handling
- no plaintext password storage

---

# 7. `src/context/WebSocketContext.tsx`

**PASS/P1**

Strong implementation:

- JWT query parameter
- automatic reconnect
- exponential backoff
- connection states
- malformed message handling
- reconnect counter
- REST resynchronization trigger

This aligns well with the low-network resilience requirement.

### Minor concern

There is a potential lifecycle race around closing/replacing WebSocket instances where an old `onclose` can schedule another reconnect while a newer connection is being established.

This is not currently the primary blocker, but the lifecycle should be hardened before final integration.

---

# 8. `src/hooks/useIncidents.ts`

**PASS with P0 dependency**

The REST + WebSocket state model is good.

It handles:

```text
incident_created
→ append if not duplicate
```

```text
incident_updated
→ replace matching incident
```

```text
incident_dispatched
→ REST reload
```

and:

```text
WS reconnect
→ REST resync
```

The main problem is inherited from the incorrect initial `GET /incidents` response handling.

---

# 9. `src/hooks/useResources.ts`

**PASS**

Correctly:

- loads resources from REST
- updates resources through `resource_updated`
- resynchronizes after reconnect

Minor issue: an entirely new resource received through `resource_updated` is ignored instead of inserted. Not explicitly required; classify as P2.

---

# 10. `src/hooks/useSituationBrief.ts`

**PASS**

Correctly implements:

```text
GET /situation-brief
+
situation_brief_updated
```

Also resynchronizes after reconnect.

---

# 11. `src/hooks/useSummaryStats.ts`

**PASS**

Correctly avoids timer polling.

Stats refresh from relevant WebSocket events:

- `incident_created`
- `incident_updated`
- `incident_dispatched`
- `resource_updated`

This is consistent with the requirement to use event-driven updates rather than continuous polling.

---

# 12. `src/pages/DashboardPage.tsx`

**PASS/P2**

Required dashboard areas are assembled:

- Summary Strip
- Map
- Incident List
- Incident Detail
- Resource Panel
- Situation Brief
- Connection Status

### Duplication issue

`DashboardPage` calls `useResources()` while `ResourcePanel` independently calls `useResources()`.

This can result in duplicate REST requests and separate state instances.

A shared resource state should be preferred.

### Layout issue

When an incident is selected, the Resource Panel can be replaced by Incident Detail.

This is not explicitly forbidden by the contract, so classify as P2 rather than a blocker.

---

# 13. `IncidentCard.tsx`

**PASS**

Displays appropriate incident information:

- AI summary
- severity
- report count
- estimated affected population
- flags
- area
- elapsed time
- status

No photo/audio UI is introduced.

---

# 14. `IncidentList.tsx`

**PASS**

Correct severity order:

```text
critical
high
medium
low
```

Within a severity, newer incidents are shown first.

### Integration issue

The actual backend is paginated, but F has no page/size handling.

After fixing the response-shape bug, only the backend's current page will be represented unless pagination is added.

---

# 15. `IncidentMap.tsx`

**PASS**

Good implementation:

- Leaflet
- incident markers
- severity-based colors
- resource markers
- tooltips
- incident selection
- automatic bounds fitting
- no photo/audio functionality

Backend clustering means the dashboard can correctly display clustered incidents as incident-level markers.

---

# 16. `IncidentDetail.tsx`

## FAIL/P1

The UI itself is comprehensive:

- location
- AI summary
- statistics
- emergency types
- flags
- recommendations
- raw SOS reports
- acknowledge
- refresh summary
- dispatch

But there are two integration problems.

### Problem 1 — Raw SOS field mismatch

Backend:

```text
source_sos_reports
```

F expects:

```text
sos_reports
```

Therefore the raw SOS section can be empty against the current backend.

### Problem 2 — Refresh-summary response mismatch

The backend currently returns:

```json
{
  "status": "refresh_queued"
}
```

while F treats it as a complete `Incident`.

This needs to be changed to asynchronous handling or the backend contract must be updated.

---

# 17. `DispatchModal.tsx`

## PASS — strongest part of submission

This correctly enforces the human-in-the-loop requirement.

Actual flow:

```text
Open modal
    ↓
Select resource
    ↓
Set quantity
    ↓
Check authority confirmation checkbox
    ↓
Click Confirm Dispatch
    ↓
POST /incidents/{id}/dispatch
```

The confirm button is disabled until the authority explicitly checks the confirmation box.

Quantity is also validated against available stock.

There is no automatic dispatch path in the component.

This satisfies the core safety requirement:

```text
SOS
 ↓
AI analysis
 ↓
OR-Tools recommendation
 ↓
Authority reviews
 ↓
Authority explicitly confirms
 ↓
Dispatch
```

---

# 18. `ResourcePanel.tsx`

**PASS**

Good implementation:

- grouped categories
- available/total quantities
- resource status
- custodian agency
- district
- contact information
- explicit **"Mock IDRN-style"** label

The last point is important because the requirements prohibit implying a live IDRN integration.

---

# 19. `SituationBrief.tsx`

**PASS**

Correctly displays:

- situation brief
- update timestamp
- loading state
- error state
- empty state

No polling loop is present.

---

# 20. `SummaryStrip.tsx`

**PASS**

Correctly displays:

- critical/high/medium/low incident counts
- affected population
- available resources
- deployed resources
- new incidents in the last 15 minutes

It uses WebSocket-driven refresh rather than timer polling.

---

# 21. `ConnectionStatus.tsx`

**PASS**

Good operational UX:

```text
Connected
Connecting
Disconnected
Error
Reconnect
```

The dashboard clearly exposes the live-feed state to the authority user.

---

# 22. `LoginPage.tsx`

**PASS**

Correct authority login flow:

- email/password
- validation
- loading state
- error state
- mock credentials clearly labeled
- authority-focused messaging

No password persistence is implemented.

---

# 23. `App.tsx`

**PASS**

Correct top-level architecture:

```text
AuthProvider
    ↓
WebSocketProvider
    ↓
Login / Dashboard
```

Good separation of responsibilities.

---

# 24. `main.tsx`

**PASS**

Normal React/Vite entry point.

StrictMode is enabled. This is acceptable, but WebSocket lifecycle code should remain safe under development-mode effect reinitialization.

---

# 25. `src/mocks/index.ts`

**PASS**

Strong mock implementation.

Includes realistic:

- incidents
- resources
- SOS reports
- stats
- situation brief

The shapes closely follow Day 1.

The situation brief explicitly identifies the registry as a mock IDRN-style registry rather than a live government integration.

No photos/audio are introduced.

---

# 26. Tests

## `DispatchModal.test.tsx`

**PASS**

Strong coverage of the critical safety behavior:

- confirm disabled initially
- checkbox enables confirmation
- no dispatch before confirmation
- dispatch only after explicit confirmation
- cancel does not dispatch
- AI recommendations are read-only guidance

This is one of the strongest parts of the submission.

## `WebSocketIntegration.test.tsx`

**PASS/P2**

Good coverage:

- JWT query parameter
- no token → no WebSocket
- `incident_created`
- duplicate incident handling
- no remount
- `incident_updated`
- `resource_updated`
- `situation_brief_updated`
- reconnect resynchronization
- malformed messages
- unknown events

However, the combined IncidentList/IncidentMap test does not fully prove that the map internally processed the new incident; it mostly verifies that the surrounding render reflects the new data.

Also, these are mocked component-level tests and do not prove actual D↔F integration.

## `apiErrors.test.tsx`

**PASS**

Good coverage of:

- network errors
- 401
- 503
- retry
- auth expiry
- dispatch failures
- stats failures

## `incidentUtils.test.ts`

**P2**

The test duplicates the sorting implementation instead of testing an imported production utility.

A better approach is to extract `sortIncidents()` into a shared utility and test the real implementation.

## `setup.ts`

**PASS**

Minimal and appropriate test setup.

---

# 27. Assets

| File | Verdict |
|---|---|
| `public/favicon.svg` | PASS/P2 |
| `public/icons.svg` | PASS/P2 |
| `src/assets/hero.png` | P2 — appears nonessential to core dashboard functionality. |
| `src/assets/react.svg` | P2 — default Vite artifact; apparently unused. |
| `src/assets/vite.svg` | P2 — default Vite artifact; apparently unused. |

Unused Vite starter assets can be removed for a cleaner submission.

---

# 28. CSS

## `src/index.css`

**PASS**

Substantial dashboard-specific styling exists rather than relying on default Vite styles.

## `src/App.css`

**P2**

`App.tsx` does not appear to import `App.css`; `main.tsx` imports `index.css`.

`App.css` therefore appears to be dead/unused CSS.

---

# Contract Audit Summary

| Area | Result |
|---|---|
| React + Vite | PASS |
| Leaflet | PASS |
| REST architecture | PASS |
| JWT auth header | PASS |
| Incident endpoints | **FAIL — response shape mismatch** |
| Incident pagination | **FAIL — not handled** |
| Resource endpoints | PASS |
| Stats | PASS |
| Situation brief | PASS |
| WebSocket URL | PASS |
| WebSocket event names | PASS |
| WebSocket JWT query auth | PASS |
| WebSocket reconnect | PASS* |
| Human-confirmed dispatch | **PASS** |
| Text-only dashboard | PASS |
| Mock IDRN labeling | PASS |
| Incident detail/raw SOS | **FAIL — field mismatch** |
| AI summary display | PASS |
| Recommendation reasoning | PASS |
| Error states | PASS |
| Clean-build verification | **UNVERIFIED** |
| Real D↔F integration | **FAIL / UNVERIFIED** |

---

# Critical Fixes Before Acceptance

## P0-1 — Fix `GET /incidents`

Current F assumption:

```text
GET /incidents → Incident[]
```

Actual D behavior:

```text
GET /incidents →
{
    items: IncidentResponse[],
    total,
    page,
    size
}
```

F must consume `items` and preferably expose pagination.

## P0-2 — Fix `refresh-summary`

Do not do:

```ts
const updated: Incident = await refreshSummary(...);
setIncident(updated);
```

when the backend returns:

```json
{
  "status": "refresh_queued"
}
```

Instead:

```text
POST refresh-summary
        ↓
refresh queued
        ↓
keep current incident
        ↓
wait for incident_updated WS event
```

or make D return a complete `Incident` according to the agreed contract.

## P1-3 — Fix `source_sos_reports`

Normalize:

```text
source_sos_reports → local sosReports
```

so raw SOS reports actually appear in Incident Detail.

## P1-4 — Handle Pagination

Add support for:

```text
page
size
total
```

and provide either pagination or an intentional load-more mechanism.

## P2-5 — Restore Canonical Recommendation Contract

Prefer:

```ts
{
  resource_type: string;
  quantity: number;
  reasoning: string;
}
```

instead of making both `resource_type` and `resource_id` optional.

## P2-6 — Fix Documentation

`Agent.md` and `ApiEndpoints.md` currently claim D↔F normalization is complete even though actual backend integration still contains mismatches.

Documentation should describe the actual agreed contract.

## P2-7 — Remove State Duplication

Avoid independently calling `useResources()` from both `DashboardPage` and `ResourcePanel`.

Use shared state.

---

# Final Acceptance Decision

**Component F / `mahima`: RED — HOLD MERGE**

Approximate assessment:

| Category | Score |
|---|---:|
| UI / UX | 9/10 |
| Component architecture | 8.5/10 |
| WebSocket implementation | 8/10 |
| Human-in-the-loop dispatch | 9.5/10 |
| Mock contract implementation | 9/10 |
| Testing | 8/10 |
| Documentation | 7/10 |
| D↔F integration | 4/10 |
| **Overall** | **7.5/10** |

This is not a failed dashboard implementation. The UI and most of the Component F architecture are solid.

The acceptance failure is specifically due to **real backend contract integration**.

The most serious runtime path is:

```text
Mahima F
    ↓
expects GET /incidents → Incident[]
    ↓
Actual D
    ↓
returns {items, total, page, size}
    ↓
REAL DASHBOARD MODE CAN BREAK
```

And independently:

```text
F refresh-summary
    ↓
expects Incident
    ↓
D currently returns {status: "refresh_queued"}
    ↓
IncidentDetail can be corrupted/crash
```

Therefore:

**Do not merge `mahima` into `dev` yet.**

After fixing the two P0 issues and the `source_sos_reports` mismatch, run:

```bash
npm install
npm run build
npm run test
```

and then perform an actual D↔F integration run with:

```text
VITE_MOCK_MODE=false
```

Only after that should Component F be reconsidered for GREEN/merge acceptance.
