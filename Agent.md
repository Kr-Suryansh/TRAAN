# SIH Backend — Agent Context

## Project

SIH 2026 — Disaster Response Coordination Platform

Component: D — Backend Core

This repository contains the backend core of the SafeLink disaster-response platform.

The backend receives SOS data from Android devices, stores and processes incidents, provides APIs for authority dashboards, manages resources, and exposes real-time incident updates.

---

## Technology Stack

- Python 3.12+
- FastAPI
- PostgreSQL
- PostGIS
- SQLAlchemy (async)
- Alembic
- Docker / Docker Compose
- JWT authentication
- bcrypt / passlib
- Pydantic

AI and optimization are owned by Component E.

---

## Component Ownership

### Component D — Backend Core — THIS REPOSITORY

Responsible for:

- Backend API
- Database
- Authentication
- SOS ingestion
- Incident management
- Resource management
- WebSocket infrastructure
- Backend business logic
- Integration points for AI/optimization services

### Component C — Android

Responsible for:

- Android application
- SOS creation
- Offline storage
- Mesh / relay / store-and-forward
- Sending SOS batches to backend when connectivity is available

Android communicates with this backend using the documented API contract.

### Component E — AI / Resource Intelligence

Responsible for:

- Gemini API integration
- AI summaries
- AI severity/insights
- OR-Tools optimization
- Resource allocation intelligence

Do NOT implement Gemini or OR-Tools inside Component D unless explicitly requested for integration.

### Component F — Authority Dashboard

Responsible for:

- React dashboard
- Incident visualization
- Map
- Resource views
- AI insights display
- Authority-facing UI

Dashboard communicates with this backend using the documented REST APIs and WebSocket contract.

---

# IMPORTANT CONTRACT RULE

The Master API Blueprint / Backend Core contract is the source of truth.

DO NOT casually change:

- endpoint paths
- HTTP methods
- request fields
- response fields
- authentication method
- JWT expectations
- enum values
- WebSocket event names
- API behavior expected by Android or Dashboard

If an implementation requires changing a contract:

1. STOP.
2. Report the conflict.
3. Explain why the change is required.
4. Update ApiEndpoints.md only after the team agrees.

Never silently invent a new API.

---

# Current Implementation Status

## Phase 1 — Infrastructure / Setup

COMPLETED.

Implemented:

- FastAPI application
- Docker configuration
- Docker Compose
- PostgreSQL/PostGIS
- SQLAlchemy async setup
- Alembic setup
- configuration management
- health endpoint
- stats endpoint/stub
- testing infrastructure

Verification:

- Docker containers started successfully.
- PostgreSQL container became healthy.
- PostGIS was available.
- FastAPI connected successfully to PostgreSQL/PostGIS.
- `/api/v1/health` returned database = ok.
- Phase 1 tests passed.

---

## Phase 2 — Authentication

COMPLETED.

Implemented:

- Device registration
- Device JWT
- Authority login
- Authority JWT
- Authority refresh
- Password hashing
- JWT verification dependencies
- Device database model
- Authority database model
- Alembic Phase 2 migration
- Authentication tests

Phase 2 test result:

11 tests passed.

The Authority login response uses the minimum internal user representation currently implemented:

- id
- email
- role

The Master contract only guarantees:

{
  "access_token": "string",
  "user": {}
}

Therefore id/email/role must NOT be treated as additional dashboard contract requirements unless the shared contract is explicitly updated.

---

# Authority Roles

The currently defined authority roles are exactly:

- DDMA
- police
- fire
- ambulance
- NDRF
- SDRF
- admin

Do not rename or change these enum values.

---

# JWT

## Device JWT

Used by Android devices for protected SOS APIs.

Current claims include:

- sub
- exp
- type = device

## Authority JWT

Used by authorities for protected REST APIs and WebSocket authentication.

Current claims include:

- sub
- role
- exp
- type = authority

Do not change JWT behavior without checking the shared contract.

---

# Current Database

PostgreSQL + PostGIS is used as the backend database.

Alembic manages database schema migrations.

Current Alembic head: 10df91729621 (Post-audit check constraint migration)

Migration chain:
- 9a8cef361e78 — Phase 2: Device + Authority models
- 66a422e96f8f — Phase 3: SOSReport model
- c50d0ccb37bd — Phase 4: Incident, Resource, DispatchRecord models
- 10df91729621 — Post-Audit: Add check constraint for resource quantity_available

Never manually modify an already-applied migration.

For schema changes, create a new Alembic migration.

---

# Current Repository Structure

```
backend/
├── app/
│   ├── main.py
│   ├── config.py
│   ├── core/
│   │   └── security.py
│   ├── db/
│   │   ├── engine.py
│   │   ├── base.py
│   │   ├── models.py
│   │   └── _models/
│   │       ├── device.py
│   │       ├── authority.py
│   │       ├── sos.py
│   │       ├── incident.py
│   │       ├── resource.py
│   │       └── dispatch.py
│   ├── models/
│   │   ├── auth.py
│   │   ├── common.py
│   │   ├── sos.py
│   │   ├── incidents.py
│   │   ├── resources.py
│   │   └── stats.py
│   ├── routers/
│   │   ├── misc.py
│   │   ├── auth.py
│   │   ├── sos.py
│   │   ├── incidents.py
│   │   └── resources.py
│   ├── services/
│   │   ├── ai/
│   │   │   └── stub.py
│   │   ├── optimizer/
│   │   │   └── stub.py
│   │   └── mock_idrn/
│   └── ws/
│       └── manager.py
├── alembic/
│   └── versions/
│       ├── 9a8cef361e78_phase_2_...
│       ├── 66a422e96f8f_phase_3_...
│       └── c50d0ccb37bd_phase_4_...
├── tests/
│   ├── conftest.py
│   ├── test_auth.py
│   ├── test_health.py
│   ├── test_sos.py
│   └── test_stats.py
├── scripts/
├── Dockerfile
├── docker-compose.yml
├── requirements.txt
├── alembic.ini
├── pytest.ini
├── README.md
├── Logs.md
├── Agent.md
└── ApiEndpoints.md
```

---

## Phase 3 — SOS Ingestion

COMPLETED.

Implemented:
- SOS request and response schemas (SOSRequest, BatchUpload, BatchResponse)
- Deduplication settings in configuration
- SOSReport SQLAlchemy model with PostGIS Geometry
- Alembic Phase 3 migration
- `POST /api/v1/sos/batch` with exact UUID deduplication, spatial/time deduplication, and ST_ClusterDBSCAN clustering
- `GET /api/v1/sos/{uuid}/status`
- Integration tests for SOS endpoints

---

## Phase 4 — Incident & Resource Management

COMPLETED (code implemented, tests pending, documentation updated).

### Database Models

- `app/db/_models/incident.py` — Incident model
  - incident_id (PK), cluster_id (unique), source_sos_uuids (JSONB)
  - location (Geometry POINT 4326), area_name
  - emergency_types (JSONB), severity, ai_summary
  - report_count, estimated_people_affected, flags (JSONB)
  - first_reported_at, last_updated_at, status
  - recommended_resources (JSONB), assigned_resources (JSONB)

- `app/db/_models/resource.py` — Resource model
  - resource_id (PK), category, sub_type, custodian_agency
  - quantity_total, quantity_available, status
  - location (Geometry POINT 4326), district (internal index field)
  - contact, last_updated_at

- `app/db/_models/dispatch.py` — DispatchRecord model
  - dispatch_id (PK), incident_id (FK), resource_id (FK)
  - quantity_dispatched, dispatched_by, dispatched_at
  - eta_minutes, status

### Pydantic Schemas

- `app/models/incidents.py` — IncidentFlags, IncidentBase, IncidentResponse, IncidentStatusUpdate, DispatchRequest, DispatchResponse
- `app/models/resources.py` — Location (with district), ResourceBase, ResourceCreate, ResourceUpdate, ResourceResponse
- `app/models/stats.py` — ActiveIncidents, ResourceStats, StatsSummaryResponse

### REST Endpoints

- `GET /api/v1/incidents` — list with bbox/severity/status/since filters, authority JWT required
- `GET /api/v1/incidents/{id}` — full incident detail, authority JWT required
- `PATCH /api/v1/incidents/{id}` — update status, emits WS event, authority JWT required
- `GET /api/v1/incidents/{id}/recommendations` — AI boundary stub, returns stored recommendations
- `POST /api/v1/incidents/{id}/dispatch` — creates DispatchRecord, updates Resource availability, emits WS events, authority JWT required
- `POST /api/v1/incidents/{id}/refresh-summary` — AI boundary stub, authority JWT required
- `GET /api/v1/resources` — list with category/status/district filters, authority JWT required
- `GET /api/v1/resources/{id}` — authority JWT required
- `POST /api/v1/resources` — admin role required
- `PATCH /api/v1/resources/{id}` — authority JWT required, emits WS event
- `GET /api/v1/stats/summary` — real DB aggregation (was stub)

### WebSocket

- `WS /ws/incidents?token=<authority_access_token>` — validates authority JWT from query param, rejects with code 1008 if missing/invalid
- Events broadcast: `incident_created`, `incident_updated`, `incident_dispatched`, `resource_updated`, `situation_brief_updated`
- Manager: in-memory ConnectionManager (per-worker), sufficient for hackathon scope

### AI / OR-Tools Boundaries

- `app/services/ai/stub.py` — `summarize_incident()`, `generate_situation_brief()` stubs
- `app/services/optimizer/stub.py` — `allocate_resources()` stub
- Both stubs log warnings and return null/empty data; clearly marked for replacement by Component E

### Alembic Migration

- Revision: c50d0ccb37bd
- Revises: 66a422e96f8f (Phase 3)
- Creates: incident, resource, dispatch_record tables with indexes
- Applied successfully; current head confirmed

### Known Limitations / Remaining Work

1. **Resource seed data** not yet created — `scripts/seed.py` does not yet exist

### Architectural Decisions

- `district` field stored in Resource DB column for efficient filtering; NOT an extra public API field — it is part of the `location` object in the contract: `{lat, lng, district}`
- Geometry stored as `Geometry(POINT, 4326)` consistent with Phase 3 SOSReport
- WebSocket token auth via `?token=` query param per the contract (browsers cannot set headers on WS handshake)
- Dispatch is strictly human-in-the-loop: no automatic dispatch code path exists. It utilizes row-level locking (`.with_for_update()`) to prevent race conditions and over-allocation.
- In-memory WebSocket ConnectionManager is per-worker only; for multi-worker production, Redis PubSub would be required

### Deviations from Contract

None. All implemented endpoints follow the authoritative Master Prompt / Day 1 contract exactly.

### Unresolved Contract Ambiguities

Two specific contract ambiguities remain explicitly unresolved per the Master Prompt instructions:
1. **Authority Refresh Token**: `POST /auth/authority/login` responds with `{access_token, user}` according to the Day 1 schema, but `/refresh` expects a refresh token. The existing JWT schema is preserved to avoid silent contract deviations.
2. **Area Name Ownership**: `IncidentResponse` includes a reverse-geocoded `area_name`, but mapping ownership is not explicitly assigned to Component D, so it currently remains unassigned (defaults to `None`).

---

# Current Status

- **Phase 1-5**: COMPLETED.
- **Component D Audit Remediation**: COMPLETED. (Included `last_seen_at` on Device, `name`/`agency` on Authority, paginated `/api/v1/incidents`, stringent security rules on SOS Gateway & Admin routes, and validation schemas).
- **Component D Forensic Audit Remediation**: COMPLETED. (Consolidated DB schemas, resolved spatial array overlaps with UUIDs, fixed incident PATCH route, enforced resource dispatch locking, implemented identical-batch deduplication, idempotent IDRN seeding, and strict enums).
- **Tests**: 43/43 passing (Full suite verified against fresh DB).

The Backend Core (Component D) is fully 100% verified and ready for Component E integration.

---

# Integration Principle

Backend must remain independently runnable.

The backend should expose stable APIs so that:

Android → Backend

Backend → Dashboard

Backend ↔ Component E

can be integrated later without rewriting existing contracts.

When another component depends on an API, use ApiEndpoints.md as the current backend contract reference.

---

# Development Rule

Before making changes:

- inspect existing implementation
- understand dependencies
- preserve existing contracts
- make the smallest required change
- run tests
- document changes in Logs.md
- update ApiEndpoints.md if an API changes

Never rewrite working architecture unnecessarily.