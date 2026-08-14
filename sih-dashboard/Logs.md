# Dev Logs — sih-dashboard

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
