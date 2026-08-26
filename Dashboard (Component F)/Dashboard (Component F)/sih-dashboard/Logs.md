# Dev Logs — sih-dashboard

## 2026-08-14 (Audit 3 — Frontend Polish & Contract-Compliance Pass)

**Branch:** `mahima` | **Build:** ✅ PASS (0 errors) | **Tests:** ✅ 47/47 PASS

### P0 Fix — `fetchIncidents()` Paginated Response Normalization
- Added internal `PaginatedIncidentsResponse` adapter type in `src/api/incidents.ts`.
- `fetchIncidents()` now detects `{items, total, page, size}` backend envelopes and extracts `items` before returning.
- Public return type remains `Incident[]` — `PaginatedIncidentsResponse` does **not** leak into UI components.
- New tests in `apiErrors.test.tsx`: paginated response → `Incident[]` → `IncidentList` renders correctly.

### P1 Fix — `refreshSummary()` Void Return Contract
- Changed return type from `Promise<Incident>` to `Promise<void>`.
- `IncidentDetail.handleRefreshSummary` no longer updates incident state from the return value. Displays "Queued — awaiting update…" instead.
- The updated `Incident` arrives via the `incident_updated` WebSocket event (correct design).
- Prevents the previous runtime corruption where `{status: "refresh_queued"}` was being set as an `Incident`.
- New tests: `refreshSummary` resolves to `undefined`; result is `typeof !== 'object'`.

### P1 Fix — `fetchIncident()` `source_sos_reports` Normalization
- `fetchIncident()` now normalises `source_sos_reports` → `sos_reports` at the API boundary.
- Handles both canonical `{incident, sos_reports}` wrapped shape and flat `Incident` object responses.
- Frontend-only adapter — backend contract is NOT changed.

### P2 Fix — `RecommendedResource` Canonical Type Restored
- `resource_type: string` is now **required** (was `optional`), per §1.5 of `day1-contracts-and-repo-setup.md`.
- `resource_id` remains an optional future compat field.
- `IncidentDetail.tsx` no longer uses `rec.resource_type || rec.resource_id || 'unspecified_resource'` fallback — canonical field is always `resource_type`.
- New tests in `incidentUtils.test.ts`: canonical shape and optional `resource_id` compat.

### P2 Fix — Resource State Ownership (Single Source of Truth)
- `DashboardPage` is now the single owner of `useResources()`.
- Passes `resources`, `loading`, `error` as props to `ResourcePanel`.
- `ResourcePanel` no longer calls `useResources()` internally — eliminates duplicate REST request on dashboard render.

### P2 Fix — `sortIncidents` Extracted Utility
- Created `src/utils/incidentUtils.ts` — exports `sortIncidents()` and `SEVERITY_ORDER`.
- `IncidentList.tsx` now imports from the utility (no more inline duplicate).
- `incidentUtils.test.ts` now imports the real production function (was duplicating the implementation).

### P2 Polish — RECOMMENDED vs DISPATCHED Visual Distinction
- `IncidentDetail.tsx`: AI/OR-Tools recommendations section shows amber "RECOMMENDED · Guidance Only" badge. Dispatched resources section shows green "DISPATCHED" badge. Sections are visually distinct with different background colors.
- `DispatchModal.tsx`: Recommendations section header changed to "AI / OR-Tools Recommendations" with amber "RECOMMENDED · Guidance Only" badge and `aria-label` for accessibility.

### P3 Fix — Test Environment `MOCK_MODE`
- `vite.config.ts`: Added `test.env.VITE_MOCK_MODE = 'false'` so `WebSocketContext` creates real WebSocket instances during tests.
- Without this fix, all 14 WebSocket integration tests failed because `MOCK_MODE` evaluated to `true` in the test environment (since `VITE_MOCK_MODE` was undefined → `undefined !== 'false'` → `true`).

### Documentation
- `BACKEND_HANDOFF.md`: Created comprehensive integration & handoff document for the backend engineer detailing mock mode, `.env.local` configuration, expected REST/WebSocket endpoint shapes, and human-in-the-loop constraints.
- `Agent.md`: Rewrote to reflect Component F (not "Component 5"), full architecture, accurate D↔F integration status, all implemented changes.
- `ApiEndpoints.md`: Rewrote to document pagination adapter note, `source_sos_reports` normalization, `refresh-summary` void contract, and D↔F integration status table.
- `README.md`: Fixed "Component 5" → "Component F"; clarified `VITE_MOCK_MODE` default behavior; added integration status note and link to `BACKEND_HANDOFF.md`.
- `Logs.md`: Added this entry (newest-first).

### Cleanup
- Deleted `src/App.css` — dead code (was never imported).
- Deleted `src/assets/react.svg` — unused Vite starter file.
- Deleted `src/assets/vite.svg` — unused Vite starter file.

### Test Results (Final)
| Test file | Tests | Result |
|---|---|---|
| `incidentUtils.test.ts` | 10 | ✅ All pass |
| `apiErrors.test.tsx` | 15 | ✅ All pass |
| `DispatchModal.test.tsx` | 8 | ✅ All pass |
| `WebSocketIntegration.test.tsx` | 14 | ✅ All pass |
| **Total** | **47** | **✅ All pass** |

---


## 2026-08-13 (Audit 2 Remediation Pass — TRAAN Contract & Integration Review)

### Contract Reconciliation & Defensive Adapters
- **`GET /incidents/{id}` Response Normalization**: Updated `fetchIncident()` in `src/api/incidents.ts` to defensively parse both canonical wrapped `{ incident, sos_reports }` responses and flat `IncidentResponse` backend structures.
- **`recommended_resources` Defensive Rendering**: Updated `RecommendedResource` type definition in `src/types/index.ts` to support optional `resource_id` alongside `resource_type`. Updated `IncidentDetail.tsx` to render `rec.resource_type || rec.resource_id || 'unspecified_resource'` defensively.
- **Combined Map + List Acceptance Test**: Added test in `src/__tests__/WebSocketIntegration.test.tsx` verifying that a WebSocket `incident_created` event updates both `IncidentList` and `IncidentMap` simultaneously without a page refresh.
- **Contract Documentation**: Updated `ApiEndpoints.md` with detailed notes on canonical shapes and defensive adapters.

### Blockers resolved
- Fixed duplicate `borderLeft` property in `src/components/IncidentCard.tsx`.
- Fixed duplicate `gap` property in `src/pages/DashboardPage.tsx`.
- Removed unused `React` default imports from all 7 source files (`App.tsx`, `ConnectionStatus.tsx`, `DispatchModal.tsx`, `IncidentList.tsx`, `IncidentMap.tsx`, `ResourcePanel.tsx`, `SituationBrief.tsx`).
- Removed unused `Anchor` import from `IncidentCard.tsx` and `IncidentDetail.tsx`.
- Replaced `React.ReactNode` references with explicit named `ReactNode` type import in `IncidentCard.tsx`, `IncidentDetail.tsx`.
- Fixed unused parameter warning (`updated` in `handleIncidentUpdated`) using `void updated;` idiom.
- Fixed `vite.config.ts` to import `defineConfig` from `'vitest/config'` instead of `'vite'` — resolves `test` key not recognised by TypeScript.
- Fixed `DispatchModal.test.tsx` TS2774 (condition always true) by replacing `screen.getByTestId` ternary with direct `getElementById`.
- Changed `.env.local` and `.env.example` defaults to `VITE_MOCK_MODE=false` — dashboard now defaults to real backend.

### New features
- **Reconnect resynchronisation**: `WebSocketContext` now tracks `reconnectCount` (incremented on every *re*connect, not the first connect). All 4 hooks (`useIncidents`, `useResources`, `useSummaryStats`, `useSituationBrief`) watch `reconnectCount` and re-fetch from REST on change — recovers events missed during disconnection window.

### New tests (4 files, total test count expanded)
- `src/__tests__/WebSocketIntegration.test.tsx`: Full acceptance criterion coverage
  - `incident_created` → state update, no page refresh, no remount
  - `incident_updated` → updates existing incident in list
  - `resource_updated` → updates resource quantity
  - `situation_brief_updated` → updates brief text
  - Duplicate incident_created protection
  - Reconnect → `fetchIncidents` called again (resync)
  - Reconnect → `fetchResources` called again (resync)
  - Malformed JSON messages silently ignored
  - Unknown event types silently ignored
- `src/__tests__/apiErrors.test.tsx`: REST error and auth coverage
  - `fetchIncidents` network error → error state surfaced
  - 401 / 503 HTTP status codes → error state surfaced
  - Retry after error succeeds and clears error state
  - `fetchResources` network error → error state surfaced
  - `auth:expired` event fires and clears sessionStorage
  - `dispatchResource` 422 / network error → rejects with correct message
  - `useSummaryStats` fetch failure → error string exposed


- **Scaffolded React + Vite + TS project**: Initialized the Vite project using template `react-ts` under `sih-dashboard`.
- **Installed dependencies**:
  - `axios` (HTTP client)
  - `leaflet` & `react-leaflet` (interactive maps)
  - `lucide-react` (icons)
  - `date-fns` (time/date parsing & distance formatting)
  - Dev dependencies: `vitest`, `@testing-library/react`, `@testing-library/user-event`, `@testing-library/jest-dom`, `jsdom`.
- **Created Data Types (`src/types/index.ts`)**: Structured standard TypeScript interfaces strictly mirroring the Master API contract schemas (§1.1 to §1.9).
- **Added Phase 1 Mock Data (`src/mocks/index.ts`)**: Populated realistic data based on Jaipur district DDMP (Sanganer, Mansarovar, Vidyadhar Nagar) for early independent frontend testing.
- **Implemented Axios Client (`src/api/client.ts`)**:
  - Integrated token-based authentication via headers.
  - Implemented automatic token clearing and logout on `401 Unauthorized` responses.
  - Provided `VITE_MOCK_MODE` toggle to switch between mock data and the live REST backend.
- **Developed API Clients (`src/api/`)**: Added `auth.ts`, `incidents.ts`, `resources.ts`, and `stats.ts`.
- **Developed Context Providers & Custom Hooks (`src/context/` & `src/hooks/`)**:
  - `AuthContext`: Manages access token and user info inside session storage.
  - `WebSocketContext`: Implements live event handling with JWT auth query parameter and exponential backoff automatic reconnection.
  - Hooks: `useIncidents`, `useResources`, `useSummaryStats`, `useSituationBrief`.
- **Created Dashboard UI Components**:
  - `SummaryStrip`: Event-driven stats bar without polling.
  - `IncidentMap`: Severity color-coded circles and resource markers with bounds auto-fit.
  - `IncidentCard` & `IncidentList`: Sorted by severity & recency. Text-only format (no photo thumbnails).
  - `IncidentDetail`: Detailed inspection panel containing contributing SOS reports and AI-generated resource suggestions.
  - `DispatchModal`: Enforces a mandatory manual review and explicit confirmation step (preventing auto-dispatch).
  - `ResourcePanel`: Categorized inventory lists highlighting availability and status.
  - `SituationBrief`: High-level brief text block updating automatically on WebSockets.
- **Configured Test Environment**: Added `vite.config.ts` Vitest setup and testing libraries.
- **Created Unit/Integration Tests**:
  - `src/__tests__/DispatchModal.test.tsx`: Validates the human-in-the-loop requirement.
  - `src/__tests__/incidentUtils.test.ts`: Verifies incident sorting and flag parsing.
- **Executed Builds and Tests**:
  - Type-checking completed successfully with `npx tsc --noEmit`.
  - All 13 tests passed successfully.
