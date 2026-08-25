# Agent Context & Changes — sih-dashboard

## Project Context
The `sih-dashboard` is the authority-facing operations center of the disaster response platform. When mobile networks fail during a disaster, citizen SOS signals are disseminated via offline mobile mesh networks using epidemic routing. When any node in the mesh achieves internet connectivity, it acts as a gateway and uploads the batch of reports.
The backend clusters these reports into incidents, uses Gemini to summarize them, and runs Google OR-Tools to recommend resources.
The dashboard displays these live recommendations, letting human dispatchers explicitly trigger and coordinate dispatch.

## Component Assignment

- **Component:** Component F (Authority Dashboard) — NOT "Component 5"
- **Role:** React + Vite + Leaflet mapping + WebSocket live updates
- **Scope:** Standalone frontend implemented against the agreed frontend/API contract
- **Backend Integration Status:** NOT performed. D↔F runtime verification is intentionally deferred.

## Implementation Status

Component F is implemented against the agreed frontend/API contract defined in `day1-contracts-and-repo-setup.md`. The dashboard is fully demonstrable using mock data — `VITE_MOCK_MODE` defaults to `true` when absent (i.e., when `VITE_MOCK_MODE` is not explicitly set to `'false'`).

## Constraints (Non-Negotiable)

- SOS reports and incident lists are **text-only** (no photos, no audio).
- Every resource allocation is a **human-approved recommendation** — automatic dispatch is explicitly banned.
- The resource registry is explicitly labeled as a **Mock IDRN-style registry**.

## Files and Architecture

### API Layer (`src/api/`)
- `auth.ts` — `POST /api/v1/auth/authority/login`
- `incidents.ts` — incident CRUD, dispatch, refresh-summary trigger
- `resources.ts` — resource list and update
- `stats.ts` — summary stats and situation brief
- `client.ts` — centralized Axios instance with JWT auth and 401 handling

### State Management
- `src/hooks/useIncidents.ts` — REST + WebSocket merged incident state
- `src/hooks/useResources.ts` — REST + WebSocket merged resource state; owned by `DashboardPage`
- `src/hooks/useSummaryStats.ts` — event-driven stats (no polling)
- `src/hooks/useSituationBrief.ts` — brief + WebSocket update
- `src/context/WebSocketContext.tsx` — JWT auth, reconnect, exponential backoff
- `src/context/AuthContext.tsx` — authority session, auth:expired handling

### Components (`src/components/`)
- `IncidentList.tsx` — severity-priority sorted list using shared `sortIncidents` utility
- `IncidentCard.tsx` — per-incident summary card
- `IncidentDetail.tsx` — full detail including RECOMMENDED vs DISPATCHED distinction
- `DispatchModal.tsx` — human-in-the-loop dispatch flow (checkbox + explicit confirm)
- `IncidentMap.tsx` — Leaflet map with severity-coded markers
- `ResourcePanel.tsx` — grouped category view; accepts resources as props from DashboardPage
- `SituationBrief.tsx` — brief display with loading/error/empty states
- `SummaryStrip.tsx` — top-level incident and resource stats
- `ConnectionStatus.tsx` — live WebSocket connection state indicator

### Utilities
- `src/utils/incidentUtils.ts` — `sortIncidents()` (single source of truth, no duplication)

## Changes Implemented

1. **Types (`src/types/index.ts`)**: Restored canonical `RecommendedResource` contract — `resource_type` is now required per §1.5 of `day1-contracts-and-repo-setup.md`. `resource_id` remains optional as a future compat adapter field.
2. **API Layer (`src/api/incidents.ts`)**: Three contract fixes:
   - `fetchIncidents()` normalises paginated `{items, total, page, size}` backend responses to `Incident[]`. The public frontend API surface remains `Incident[]`; `PaginatedIncidentsResponse` is an internal adapter type only.
   - `fetchIncident()` normalises `source_sos_reports` → `sos_reports` (backend field variance adapter).
   - `refreshSummary()` returns `Promise<void>` — it is an async trigger, not an incident update. The updated incident arrives via the `incident_updated` WebSocket event.
3. **Utility (`src/utils/incidentUtils.ts`)**: Extracted `sortIncidents` into a shared utility. Both `IncidentList.tsx` and tests import the real implementation — no duplication.
4. **State ownership (`ResourcePanel.tsx`)**: ResourcePanel now accepts `resources`, `loading`, and `error` as props from DashboardPage. It no longer calls `useResources()` internally, eliminating duplicate REST requests and state instances.
5. **UI Polish (`IncidentDetail.tsx`, `DispatchModal.tsx`)**: Clear visual distinction between RECOMMENDED (AI/OR-Tools guidance) and ASSIGNED/DISPATCHED (authority-confirmed) resources. `refreshSummary` feedback updated to "Queued — awaiting update…" to match the async trigger contract.
6. **Tests**: Updated to import real `sortIncidents` utility; added canonical `RecommendedResource` contract test; added `refreshSummary` void contract test; added paginated incident normalization tests. **Final result: 47/47 tests pass.**
7. **Test environment (`vite.config.ts`)**: Added `test.env.VITE_MOCK_MODE = 'false'` so `WebSocketContext` constructs real WebSocket instances during tests. Without this, all 14 WS integration tests failed because `MOCK_MODE` evaluated to `true` when `VITE_MOCK_MODE` was undefined.
8. **Documentation**: Fixed "Component 5" → "Component F" in `Agent.md`, `README.md`. Accurately documented D↔F normalization status in all docs. Created `BACKEND_HANDOFF.md` to guide backend engineers during integration. Updated `Logs.md` with a full dated entry for this pass.

## Human-in-the-Loop Safeguard

The `DispatchModal` enforces the non-negotiable dispatch flow:

```
AI / OR-Tools recommendation (clearly labeled RECOMMENDED · Guidance Only)
      ↓
Authority reviews (read-only display in DispatchModal)
      ↓
Authority selects resource and quantity
      ↓
Authority checks mandatory confirmation checkbox
      ↓
Authority clicks Confirm Dispatch (only button path to POST /dispatch)
```

The submit button is disabled until the checkbox is checked. No automated path exists.

## D↔F Integration Status

**Backend integration was NOT performed.**

The following items remain deferred for a separate integration task:
- D↔F runtime verification with `VITE_MOCK_MODE=false`
- Backend pagination UI (page/size controls)
- Confirmation that `refresh-summary` WS flow works end-to-end with Component D
- WebSocket lifecycle race hardening (noted as P2 in the audit)

## Deviations from Initial Plan

- **Mock Mode Toggle**: `VITE_MOCK_MODE` env flag in Axios client enables seamless local/production switching.
- **Auth Expiry Listener**: Global `auth:expired` window event in `AuthContext` handles background session expirations from the API interceptor.
