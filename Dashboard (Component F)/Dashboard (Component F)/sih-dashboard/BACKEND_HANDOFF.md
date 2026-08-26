# Component F (Authority Dashboard) — Backend Integration & Handoff Guide

> **To the Backend Engineer (Component D):**
> This document explains how the frontend dashboard is currently running on mock data, how to switch it to live backend mode, and the exact REST and WebSocket contracts expected by Component F.

---

## 1. Mock Mode vs. Live Backend Mode

### Current State: Mock Mode Active
By default, the dashboard runs in **Mock Mode** using client-side simulated fixtures.
- **Mock Data File:** [`src/mocks/index.ts`](file:///c:/Users/krsur/Desktop/VS%20Code/PROJECTS%20(Current%20or%20Finished)/TRAAN%20(SIH)/Dashboard%20(Component%20F)/sih-dashboard/src/mocks/index.ts)
- Contains realistic disaster incident scenarios, resources, stats, and Gemini situation briefs for offline testing.
- UI displays a green badge in the header: `● Mock mode — live WS disabled`.

---

## 2. How to Connect the Live Backend

To point the dashboard to your running backend (e.g. FastAPI on `localhost:8000`):

1. Create or edit **`.env.local`** in the `sih-dashboard/` root:
   ```env
   # Set to 'false' to disable mock mode and make real HTTP/WS network requests
   VITE_MOCK_MODE=false

   # REST API Base URL (must point to your backend /api/v1 prefix)
   VITE_API_BASE_URL=http://localhost:8000/api/v1

   # WebSocket updates stream URL
   VITE_WS_URL=ws://localhost:8000/ws/incidents
   ```

2. Restart the Vite development server:
   ```bash
   npm run dev
   ```

3. When live, the header indicator will switch to: `● Live feed` (connected) or `○ Disconnected` (if backend WS is unreachable).

---

## 3. Required REST Endpoints & Contracts

The dashboard consumes the following REST endpoints under `VITE_API_BASE_URL`:

### 3.1 Authentication
- **`POST /auth/authority/login`**
  - **Request Body:** `{ "email": "...", "password": "..." }`
  - **Response (200):**
    ```json
    {
      "access_token": "jwt_token_string",
      "user": {
        "user_id": "usr-001",
        "name": "Dispatcher Name",
        "role": "DDMA",
        "agency": "District Disaster Management Authority",
        "email": "demo@sih.gov.in"
      }
    }
    ```
  - *Note:* The JWT token is automatically passed as `Authorization: Bearer <token>` on all subsequent REST requests and as `?token=<token>` on WebSocket connections.

---

### 3.2 Incidents
- **`GET /incidents`**
  - **Query Params:** `bbox`, `severity`, `status`, `since`
  - **Response (200):** Array of `Incident` objects, OR paginated object `{ items: Incident[], total: number, page: number, size: number }`.
  - *(The frontend has an internal adapter that handles both shapes seamlessly).*

- **`GET /incidents/{incident_id}`**
  - **Response (200):**
    ```json
    {
      "incident": { /* Incident object */ },
      "sos_reports": [ /* Array of SOSRequest objects */ ]
    }
    ```
  - *(Frontend also supports flat incident shapes or `source_sos_reports` field names).*

- **`PATCH /incidents/{incident_id}`**
  - **Request Body:** `{ "status": "acknowledged" | "dispatched" | "resolved" }`
  - **Response (200):** Updated `Incident` object.

- **`POST /incidents/{incident_id}/dispatch`**
  - **Request Body:** `{ "resource_id": "res-001", "quantity": 2 }`
  - **Response (200):** `DispatchRecord` object.

- **`POST /incidents/{incident_id}/refresh-summary`**
  - **Behavior:** Asynchronous trigger to re-run Gemini summary on the incident.
  - **Response:** Backend returns `{ "status": "refresh_queued" }`.
  - **Important:** The frontend treats this endpoint as a fire-and-forget trigger and expects the updated incident with new `ai_summary` to be pushed over the WebSocket via an `incident_updated` event.

---

### 3.3 Resources (Mock IDRN Registry)
- **`GET /resources`**
  - **Query Params:** `category`, `status`, `district`
  - **Response (200):** Array of `Resource` objects.

- **`PATCH /resources/{resource_id}`**
  - **Request Body:** Partial update `{ "quantity_available": 3, "status": "partially_deployed" }`
  - **Response (200):** Updated `Resource` object.

---

### 3.4 Situation Brief & Stats
- **`GET /stats/summary`**
  - **Response (200):**
    ```json
    {
      "active_incidents": { "critical": 1, "high": 2, "medium": 1, "low": 0 },
      "total_estimated_people_affected": 26,
      "resources": { "available": 12, "deployed": 3 },
      "new_incidents_last_15min": 2
    }
    ```

- **`GET /situation-brief`**
  - **Response (200):** `{ "text": "...", "updated_at": "ISO8601" }`

---

## 4. WebSocket Events (`/ws/incidents`)

The dashboard connects to `ws://localhost:8000/ws/incidents?token=<jwt_access_token>`.

Your backend should emit JSON messages in this standard format:
```json
{
  "event": "event_name",
  "data": { /* payload object */ }
}
```

### Supported Events:
| Event Name | Data Payload | Frontend Reaction |
|---|---|---|
| `incident_created` | `Incident` object | Prepend to Incident List & add Map marker without page reload |
| `incident_updated` | `Incident` object | Updates matching incident in-place (used after AI summary refresh) |
| `incident_dispatched` | `DispatchRecord` object | Re-syncs incident and resource counts |
| `resource_updated` | `Resource` object | Updates available stock and status in Resource panel |
| `situation_brief_updated` | `{ "text": "string", "updated_at": "ISO8601" }` | Updates Situation Brief banner |

---

## 5. Canonical Type Reference

All canonical TypeScript schemas are in [`src/types/index.ts`](file:///c:/Users/krsur/Desktop/VS%20Code/PROJECTS%20(Current%20or%20Finished)/TRAAN%20(SIH)/Dashboard%20(Component%20F)/sih-dashboard/src/types/index.ts).

### `RecommendedResource` Contract:
```ts
{
  "resource_type": "motorboat", // REQUIRED: string matching resource sub_type/category
  "quantity": 2,                // REQUIRED: integer
  "reasoning": "Flood depth..." // REQUIRED: explanation from OR-Tools / Gemini
}
```

---

## 6. Constraints & Safety Invariants
1. **Human-in-the-Loop:** The dashboard **never** triggers auto-dispatch. All resource assignments require manual dispatcher confirmation in the `DispatchModal`.
2. **Text-Only:** Payloads should not include binary photos or audio attachments.
