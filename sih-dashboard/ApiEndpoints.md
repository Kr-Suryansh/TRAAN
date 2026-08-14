# API Endpoints & Contracts — sih-dashboard

This file lists the REST and WebSocket endpoints consumed by the Authority Dashboard.
It mirrors the master contract defined in `day1-contracts-and-repo-setup.md` exactly.

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
- **Returns**: `Incident[]` (refer to schemas)

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
- **Defensive Adapter Note**: Component F incorporates defensive normalization in `fetchIncident()`. If Component D returns the canonical `{ incident, sos_reports }` wrapper or a flat `Incident` object, Component F handles both transparently without throwing.
- **`recommended_resources` Contract Note**: Canonical fields are `{ resource_type: string, quantity: number, reasoning: string }`. Component F defensively renders `resource_type ?? resource_id` if Component D outputs `resource_id`.

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
- **Description**: Force-triggers Gemini to regenerate the `ai_summary` for the incident.
- **Headers**: `Authorization: Bearer <access_token>`
- **Returns**: `Incident`

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

### 2.2 Live Events (Updates List)
The client listens for messages matching the following event payloads:

#### `incident_created`
- **Payload**: `Incident`

#### `incident_updated`
- **Payload**: `Incident`

#### `incident_dispatched`
- **Payload**: `DispatchRecord`

#### `resource_updated`
- **Payload**: `Resource`

#### `situation_brief_updated`
- **Payload**: `{ "text": "string", "updated_at": "ISO8601" }`
