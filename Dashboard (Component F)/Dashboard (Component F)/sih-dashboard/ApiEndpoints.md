# API Endpoints & Contracts — sih-dashboard (Component F)

This file lists the REST and WebSocket endpoints consumed by the Authority Dashboard.
It mirrors the master contract defined in `day1-contracts-and-repo-setup.md` exactly.

> **Status:** Component F is implemented against the agreed frontend/API contract.
> Backend integration (D↔F runtime verification) is intentionally deferred.
> 
> 📄 **Backend Integration Guide:** For full instructions on connecting a live backend and disabling mock data, refer to [BACKEND_HANDOFF.md](BACKEND_HANDOFF.md).

## Base URL
REST API: `http://localhost:8000/api/v1` (controlled by `VITE_API_BASE_URL` env variable)
WebSocket: `ws://localhost:8000/ws/incidents` (controlled by `VITE_WS_URL` env variable)

---

## 1. REST Endpoints

### 1.1 Auth
#### `POST /auth/authority/login`
- **Description**: Authenticates authority dispatcher.
- **Body**:
  ```json
  {
    "email": "string",
    "password": "string"
  }
  ```
- **Returns**:
  ```json
  {
    "access_token": "string",
    "user": {
      "user_id": "string",
      "name": "string",
      "role": "DDMA | police | fire | ambulance | NDRF | SDRF | admin",
      "agency": "string",
      "email": "string"
    }
  }
  ```

---

### 1.2 Incidents
#### `GET /incidents`
- **Description**: Fetches list of clustered incidents. Can be filtered.
- **Headers**: `Authorization: Bearer <access_token>`
- **Query Params**: `bbox`, `severity`, `status`, `since`
- **Agreed frontend contract return**: `Incident[]`
- **Backend adapter note**: The current backend may return a paginated envelope
  `{ items: Incident[], total, page, size }`. `fetchIncidents()` normalises this
  internally and always returns `Incident[]` to callers. The `PaginatedIncidentsResponse`
  adapter type is internal to `api/incidents.ts` and must not leak into UI components.

#### `GET /incidents/{incident_id}`
- **Description**: Fetches incident details including linked raw SOS reports.
- **Headers**: `Authorization: Bearer <access_token>`
- **Canonical Master Contract Response**:
  ```json
  {
    "incident": "Incident",
    "sos_reports": "SOSRequest[]"
  }
  ```
- **Adapter note**: `fetchIncident()` normalises both:
  - The canonical `{ incident, sos_reports }` wrapped shape
  - A flat `Incident` object where SOS reports are at the top level
  - The backend field `source_sos_reports` is normalised to `sos_reports` (the canonical frontend field)

#### `PATCH /incidents/{incident_id}`
- **Description**: Updates incident status (e.g. acknowledging it).
- **Headers**: `Authorization: Bearer <access_token>`
- **Body**:
  ```json
  {
    "status": "new | acknowledged | dispatched | resolved"
  }
  ```
- **Returns**: `Incident`

#### `POST /incidents/{incident_id}/dispatch`
- **Description**: Creates a new resource dispatch record for an incident.
- **SAFETY**: This endpoint is ONLY called after explicit authority confirmation (checkbox + button).
- **Headers**: `Authorization: Bearer <access_token>`
- **Body**:
  ```json
  {
    "resource_id": "string",
    "quantity": "integer"
  }
  ```
- **Returns**: `DispatchRecord`

#### `POST /incidents/{incident_id}/refresh-summary`
- **Description**: Asynchronous trigger — queues a Gemini AI summary regeneration for the incident.
- **Headers**: `Authorization: Bearer <access_token>`
- **Frontend contract return**: `void` (Promise<void>)
- **Important**: This endpoint does NOT return an updated `Incident`. The frontend
  `refreshSummary()` function returns `Promise<void>`. The updated incident will arrive
  via the `incident_updated` WebSocket event. Callers MUST NOT update incident state
  from this response.
- **Current backend behavior**: Returns `{ "status": "refresh_queued" }`. This is NOT
  a valid `Incident` object and must not be treated as one.

---

### 1.3 Resources (Mock IDRN Registry)
#### `GET /resources`
- **Description**: Lists resources available in the district database.
- **Headers**: `Authorization: Bearer <access_token>`
- **Query Params**: `category`, `status`, `district`
- **Returns**: `Resource[]`

---

### 1.4 Situation Brief & Stats
#### `GET /situation-brief`
- **Description**: Returns the latest cached situation summary written by Gemini.
- **Headers**: `Authorization: Bearer <access_token>`
- **Returns**:
  ```json
  {
    "text": "string",
    "updated_at": "ISO8601"
  }
  ```

#### `GET /stats/summary`
- **Description**: Returns quick totals for the dashboard's summary strip.
- **Headers**: `Authorization: Bearer <access_token>`
- **Returns**:
  ```json
  {
    "active_incidents": {
      "critical": "integer",
      "high": "integer",
      "medium": "integer",
      "low": "integer"
    },
    "total_estimated_people_affected": "integer",
    "resources": {
      "available": "integer",
      "deployed": "integer"
    },
    "new_incidents_last_15min": "integer"
  }
  ```

---

## 2. WebSocket Connection & Events

### 2.1 Connection
`WS /ws/incidents?token=<authority_access_token>`
- The authority JWT is passed as a query param due to browser WebSocket handshake limitations.
- Backend validates the token once at connection initialization.

### 2.2 Live Events

#### `incident_created`
- **Payload**: `Incident`
- **Effect**: Appended to incident list (deduplicated by incident_id)

#### `incident_updated`
- **Payload**: `Incident`
- **Effect**: Replaces the matching incident in state. **This is also how refresh-summary delivers its result.**

#### `incident_dispatched`
- **Payload**: `DispatchRecord`
- **Effect**: Triggers REST resync for incidents

#### `resource_updated`
- **Payload**: `Resource`
- **Effect**: Replaces the matching resource in state

#### `situation_brief_updated`
- **Payload**: `{ "text": "string", "updated_at": "ISO8601" }`
- **Effect**: Updates the situation brief display

---

## 3. Canonical Data Schemas (Key Types)

### `RecommendedResource` (§1.5 Day 1 contract)
```ts
{
  resource_type: string;   // REQUIRED — canonical display field
  quantity: number;        // REQUIRED
  reasoning: string;       // REQUIRED
  resource_id?: string;    // OPTIONAL — future backend compat adapter only
}
```
`resource_type` is the canonical field. `resource_id` is not part of the
agreed Day 1 contract for `recommended_resources` and must not be the
primary display field.

### Incident status values
`new | acknowledged | dispatched | resolved`

### Incident severity values
`critical | high | medium | low`
Priority display order: critical → high → medium → low

### Resource status values
`available | partially_deployed | deployed | maintenance`

---

## 4. Backend Integration Status

**D↔F runtime integration has NOT been performed.**

The following are known for future integration work:

| Item | Status |
|---|---|
| `GET /incidents` pagination | Normalised by frontend adapter |
| `GET /incidents/{id}` `source_sos_reports` | Normalised by frontend adapter |
| `POST /refresh-summary` async WS flow | Frontend treats as void; awaits WS |
| D↔F runtime end-to-end | **Deferred** |
