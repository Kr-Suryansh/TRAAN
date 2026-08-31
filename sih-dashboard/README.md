# Authority Dashboard — sih-dashboard
**React + Vite + TypeScript · Component F**

> **Status:** Component F is implemented against the agreed frontend/API contract.
> Backend integration (D↔F runtime verification) is intentionally deferred.
> The dashboard is fully demonstrable using mock data — run `npm run dev` with no `.env.local` to start immediately.
> 
> 📄 **Backend Developers:** See [BACKEND_HANDOFF.md](BACKEND_HANDOFF.md) for full integration specifications, endpoint shapes, WebSocket events, and how to toggle off mock mode.

This repository contains the authority-facing operations dashboard for the TRAAN disaster response platform. Authority dispatchers use this to monitor clustered incidents, review AI/OR-Tools recommendations, and explicitly dispatch resources under human oversight.

---

## 1. Installation

Ensure **Node.js v22+** is installed.

```bash
cd sih-dashboard
npm install
```

---

## 2. Configuration (`.env.local`)

Copy `.env.example` to `.env.local` and override as needed:

| Variable | Default | Description |
|---|---|---|
| `VITE_API_BASE_URL` | `http://localhost:8000/api/v1` | REST API base URL |
| `VITE_WS_URL` | `ws://localhost:8000/ws/incidents` | WebSocket endpoint |
| `VITE_MOCK_MODE` | *(absent = mock on)* | When absent or any value other than `'false'`, the dashboard runs on mock data. Set to `false` to connect to a live backend. |

> **Note:** `VITE_MOCK_MODE` defaults to **on** when the variable is absent. You do not need to set it for local development or demos.

---

## 3. Development Commands

### Start Dev Server
```bash
npm run dev
# Opens http://localhost:5173
```

### Run Tests
```bash
npm run test
# Vitest — 47 tests across 4 test files (all pass)
```

### Build (Type-check + Bundle)
```bash
npm run build
# TypeScript compiler + Vite production build
# Must produce 0 errors
```

---

## 4. Project Structure

```
src/
├── api/                   # REST API client functions
│   ├── client.ts          # Axios instance, JWT interceptor, auth:expired handling
│   ├── auth.ts            # POST /auth/authority/login
│   ├── incidents.ts       # Incident CRUD, dispatch, refresh-summary trigger
│   ├── resources.ts       # Resource list and update
│   └── stats.ts           # Stats summary and situation brief
├── components/            # UI components
│   ├── IncidentList.tsx   # Severity-sorted incident list
│   ├── IncidentCard.tsx   # Per-incident summary card
│   ├── IncidentDetail.tsx # Full detail panel (RECOMMENDED vs DISPATCHED)
│   ├── DispatchModal.tsx  # Human-in-the-loop dispatch flow
│   ├── IncidentMap.tsx    # Leaflet map with severity-coded markers
│   ├── ResourcePanel.tsx  # Grouped resource registry (props-driven)
│   ├── SituationBrief.tsx # Gemini-generated brief display
│   ├── SummaryStrip.tsx   # Top-level stats strip
│   └── ConnectionStatus.tsx # WebSocket connection indicator
├── context/               # React context providers
│   ├── AuthContext.tsx    # Authority session, auth:expired event handler
│   └── WebSocketContext.tsx # JWT auth, reconnect, exponential backoff
├── hooks/                 # Custom React hooks
│   ├── useIncidents.ts    # REST + WebSocket merged incident state
│   ├── useResources.ts    # REST + WebSocket merged resource state
│   ├── useSummaryStats.ts # Event-driven stats (no polling)
│   └── useSituationBrief.ts # Brief + WebSocket update
├── mocks/                 # Mock data for VITE_MOCK_MODE (Jaipur district data)
├── pages/
│   ├── DashboardPage.tsx  # Main layout; single owner of useResources()
│   └── LoginPage.tsx      # Authority login form
├── types/
│   └── index.ts           # Canonical TypeScript interfaces (Day 1 contract)
└── utils/
    └── incidentUtils.ts   # sortIncidents() — single source of truth
```

---

## 5. Design Decisions & Constraints

- **Text-Only**: No photo or audio upload — all SOS reports and incident data are text only.
- **Human-in-the-Loop**: The `DispatchModal` requires an explicit checkbox acknowledgment AND a button click. No automated dispatch path exists.
- **Resource Registry**: Clearly labeled as a Mock IDRN-style registry — not a live registry.
- **AI Recommendations**: All OR-Tools / Gemini recommendations are labeled **"RECOMMENDED · Guidance Only"** and are never automatically acted upon.
- **WebSocket Resilience**: Exponential backoff reconnect; REST resync on every reconnect to recover missed events.
- **State Ownership**: `DashboardPage` is the single owner of `useResources()`. `ResourcePanel` accepts resources as props — no duplicate REST requests.

---

## 6. API Adapters (Frontend-Only)

The following adapters normalise real-backend responses without changing any backend contract:

| Adapter | Handles |
|---|---|
| `fetchIncidents()` | Detects `{items, total, page, size}` paginated response → extracts `items` → returns `Incident[]` |
| `fetchIncident()` | Normalises `source_sos_reports` → `sos_reports`; handles both wrapped and flat response shapes |
| `refreshSummary()` | Returns `Promise<void>`; updated Incident arrives via `incident_updated` WebSocket event |

---

## 7. Backend Integration Status

**D↔F runtime integration is NOT performed.**

The following are deferred to a separate integration task:

- D↔F runtime end-to-end verification (`VITE_MOCK_MODE=false`)
- Pagination UI controls (page/size selectors)
- `refresh-summary` WebSocket flow end-to-end with Component D
- WebSocket lifecycle race hardening
