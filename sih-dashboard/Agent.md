# Agent Context & Changes — sih-dashboard

## Project Context
The `sih-dashboard` is the authority-facing operations center of the disaster response platform. When mobile networks fail during a disaster, citizen SOS signals are disseminated via offline mobile mesh networks using epidemic routing. When any node in the mesh achieves internet connectivity, it acts as a gateway and uploads the batch of reports.
The backend clusters these reports into incidents, uses Gemini to summarize them, and runs Google OR-Tools to recommend resources.
The dashboard displays these live recommendations, letting human dispatchers explicitly trigger and coordinate dispatch.

## Component Assignment (F)
- **Role**: Component F (Authority Dashboard).
- **Scope**: Build the dashboard using React + Vite + Leaflet mapping + WebSockets.
- **Goals**: Assemble a high-fidelity dashboard that connects to REST APIs and WebSockets, displaying real-time data under low-network resilience.
- **Constraints**:
  - SOS reports and incident lists are **text-only** (no photos, no audio).
  - Every resource allocation is a **human-approved recommendation** — automatic dispatch is explicitly banned.
  - The resource registry is explicitly labeled as a **Mock IDRN-style registry**.

## Changes Implemented
1. **Types**: Mapped exact schemas for `UserMedicalProfile`, `SOSRequest`, `Incident`, `Resource`, `DispatchRecord`, `AuthorityUser`, and `StatsSummary`.
2. **API Layer**: Implemented full CRUD and custom action endpoints (`/incidents`, `/resources`, `/stats/summary`, `/situation-brief`, `/dispatch`). Added authorization headers using JWT.
3. **WebSocket Management**: Built connection handling, automatic retry backoff, and state indications to the user.
4. **Human-in-the-Loop Safeguard**:
  - The `DispatchModal` forces the authority to check a validation box and click a confirmation button to complete the dispatch workflow.
  - Disabling the submit button unless both conditions are met.
5. **Interactive Mapping**: Color-coded Leaflet markers reflecting the severity metrics of clustered reports and active positions of emergency resources.

## Audit 2 Remediation Changes
29: - **Defensive D↔F Contract Normalization**: `fetchIncident` in `src/api/incidents.ts` automatically normalizes both canonical `{ incident, sos_reports }` wrapped objects and flat `IncidentResponse` shapes returned by Component D.
30: - **Flexible `recommended_resources` Mapping**: `RecommendedResource` interface accepts optional `resource_id` alongside `resource_type`, and `IncidentDetail.tsx` safely falls back between both fields.
31: - **Combined Map+List Acceptance Test**: Added an end-to-end component test in `WebSocketIntegration.test.tsx` proving `incident_created` updates both `IncidentList` and `IncidentMap` simultaneously without page reloads.

## Deviations from Initial Plan
- **Mock Mode Toggle**: Created `VITE_MOCK_MODE` env flag inside the Axios client config rather than switching files. This enables seamless, instantaneous local/production switching without needing code refactoring.
- **Auth Expiry Listener**: Implemented a global window event listener `auth:expired` inside `AuthContext` to instantly trigger state cleanup when the API interceptor catches a `401 Unauthorized` response. This handles background session expirations gracefully.
