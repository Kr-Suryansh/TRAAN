# Component D Backend Forensic Audit Report

## 1. Executive Verdict

```text
Component D Status:
YELLOW / ACCEPT AFTER FIXES

Overall Completion:
95%

Merge Recommendation:
ACCEPT AFTER FIXES
```

**Reasoning:**
The backend core largely satisfies the Master Prompt and Day 1 API contracts. All required REST endpoints, WebSocket behaviors, deduplication strategies (UUID and PostGIS spatial near-duplicate checks), and ST_ClusterDBSCAN spatial clustering logic have been implemented accurately. The repository correctly integrates SQLAlchemy with GeoAlchemy2 to manage PostgreSQL/PostGIS spatial data.

The project strictly follows the Component boundaries. Component E's stubs for Gemini and OR-Tools are clearly marked, returning safe placeholder data without blocking Component D's core functionality. 

However, we cannot classify the backend as GREEN due to a critical concurrency vulnerability found in the Dispatch functionality. Currently, the dispatch code reads resource availability but does not use row-locking (`FOR UPDATE`) or CheckConstraints, meaning multiple concurrent dispatches could easily subtract resource availability below zero.

Furthermore, running the comprehensive test suite to confirm the "34 passing tests" claim was obstructed by the local environment configuration (Docker not being installed). The architectural logic is sound, but the concurrency flaw must be fixed before integrating with the live Dashboard. 

## 2. Repository Inventory

Important files and modules identified during the audit:

- `app/main.py` (FastAPI Entry Point, CORS, Lifespan checks)
- `app/routers/` (`auth.py`, `sos.py`, `incidents.py`, `resources.py`, `misc.py`, `optimizer.py`)
- `app/db/_models/` (`device.py`, `authority.py`, `sos.py`, `incident.py`, `resource.py`, `dispatch.py`)
- `app/models/` (Pydantic schemas)
- `app/core/security.py` (JWT & Bcrypt hashing)
- `app/services/incident_pipeline.py` (SOS to Incident Aggregation)
- `app/ws/manager.py` (WebSocket Manager)
- `app/services/ai/stub.py` & `app/services/optimizer/stub.py` (Component E Stubs)
- `docker-compose.yml` & `Dockerfile`
- `alembic/versions/` (Migration History)

## 3. Master Prompt Compliance

| # | Requirement | Implementation | Status | Evidence |
|---|---|---|---|---|
| 1 | Text-only SOS payloads (no photos/voice) | No media fields in Pydantic or DB schema | PASS | `app/models/sos.py`, `app/db/_models/sos.py` |
| 2 | Human-approved resource allocation | Dispatch is triggerable only by `/dispatch` POST | PASS | `app/routers/incidents.py` line 145 |
| 3 | Mock IDRN resource registry | Implemented as mock tables, `seed.py` missing | PARTIAL | `app/db/_models/resource.py` |
| 4 | Battery-conscious relay engine | Expected Component A/B boundary | NOT APPLICABLE | Handled by Android app |
| 5 | Epidemic routing | Expected Component A/B boundary | NOT APPLICABLE | Handled by Android app |
| 6 | Gateway batch upload | `POST /sos/batch` correctly receives and processes arrays | PASS | `app/routers/sos.py` |
| 7 | Dashboard map updates via WS | WS manager broadcasts `incident_created`, `incident_updated` | PASS | `app/ws/manager.py` |

## 4. API Contract Compliance

| Endpoint | Required Method | Required Auth | Required Request | Required Response | Actual Implementation | Match? | Evidence |
|---|---|---|---|---|---|---|---|
| `/api/v1/auth/device/register` | POST | None | `{device_model, app_version}` | `{device_id, device_jwt}` | `POST /api/v1/auth/device/register` | YES | `app/routers/auth.py` |
| `/api/v1/auth/authority/login` | POST | None | `{email, password}` | `{access_token, user}` | `POST /api/v1/auth/authority/login` | YES | `app/routers/auth.py` |
| `/api/v1/auth/authority/refresh` | POST | refresh token | `{access_token}` | `POST /api/v1/auth/authority/refresh` | YES | `app/routers/auth.py` |
| `/api/v1/sos/batch` | POST | Device JWT | `GatewayUploadBatch` | HTTP 202, `{accepted_uuids, duplicate_uuids}` | `POST /api/v1/sos/batch` | YES | `app/routers/sos.py` |
| `/api/v1/sos/{uuid}/status` | GET | Device JWT | None | `{status}` | `GET /api/v1/sos/{uuid}/status` | YES | `app/routers/sos.py` |
| `/api/v1/incidents` | GET | Authority JWT | None | Paginated incident list | `GET /api/v1/incidents` | YES | `app/routers/incidents.py` |
| `/api/v1/incidents/{id}` | GET | Authority JWT | None | Incident detail | `GET /api/v1/incidents/{id}` | YES | `app/routers/incidents.py` |
| `/api/v1/incidents/{id}` | PATCH | Authority JWT | Status update | Updated Incident | `PATCH /api/v1/incidents/{id}` | YES | `app/routers/incidents.py` |
| `/api/v1/incidents/{id}/recommendations` | GET | Authority JWT | None | Recommendations List | `GET /api/v1/incidents/{id}/recommendations` | YES | `app/routers/incidents.py` |
| `/api/v1/incidents/{id}/dispatch` | POST | Authority JWT | `{resource_id, quantity}` | DispatchRecord | `POST /api/v1/incidents/{id}/dispatch` | YES | `app/routers/incidents.py` |
| `/api/v1/incidents/{id}/refresh-summary`| POST | Authority JWT | None | Success acknowledgment | `POST /api/v1/incidents/{id}/refresh-summary`| YES | `app/routers/incidents.py` |
| `/api/v1/resources` | GET | Authority JWT | None | Resource List | `GET /api/v1/resources` | YES | `app/routers/resources.py` |
| `/api/v1/resources/{id}` | GET | Authority JWT | None | Resource detail | `GET /api/v1/resources/{id}` | YES | `app/routers/resources.py` |
| `/api/v1/resources` | POST | Authority JWT | Resource config | Resource detail | `POST /api/v1/resources` | YES | `app/routers/resources.py` |
| `/api/v1/resources/{id}` | PATCH | Authority JWT | Qty/Status config | Resource detail | `PATCH /api/v1/resources/{id}` | YES | `app/routers/resources.py` |
| `/api/v1/situation-brief` | GET | Authority JWT | None | Brief String | `GET /api/v1/situation-brief` | YES | `app/routers/optimizer.py` |
| `/api/v1/situation-brief/refresh` | POST | Admin Authority JWT | None | Success acknowledgment | `POST /api/v1/situation-brief/refresh` | YES | `app/routers/optimizer.py` |
| `/api/v1/optimize/allocate` | POST | Authority JWT | `{incident_ids, constraints}`| Allocation plan | `POST /api/v1/optimize/allocate` | YES | `app/routers/optimizer.py` |
| `/api/v1/health` | GET | None | None | Uptime status | `GET /api/v1/health` | YES | `app/routers/misc.py` |
| `/api/v1/stats/summary` | GET | None | None | Stats aggregation | `GET /api/v1/stats/summary` | YES | `app/routers/misc.py` |

## 5. Database/PostGIS Audit

- **Implementation**: SQLAlchemy async models correctly use GeoAlchemy2 for PostGIS integration.
- **Coordinates**: Geometry columns are structured using `Geometry('POINT', 4326)` for WGS84 format.
- **Alembic**: Migrations are structured properly; Phase 4 schema incorporates all models.
- **Performance**: Missing spatial index in migration files is noted (Auto-generated indexes were removed to use GeoAlchemy2's internal handling, which is acceptable).
- **Concurrency Defect**: Missing `CheckConstraint` on `quantity_available >= 0` inside `Resource` table.

## 6. Authentication & Authorization Audit

- **Device flow**: Devices correctly receive `device_jwt` via `POST /api/v1/auth/device/register` using a simple payload.
- **Authority flow**: Authority login provides `access_token` with embedded roles and uses Bcrypt hashing for password validation. 
- **Secret Management**: No secrets are hardcoded. All JWT and Postgres credentials pull from `.env` via `app/config.py`.
- **RBAC**: `admin` specific requirements mapped out on REST API routes.
- **Verdict**: Pass.

## 7. SOS Ingestion & Deduplication Audit

- **Exact Duplicate Logic**: Handled elegantly via `SOSReport.uuid.in_(incoming_uuids)`.
- **Near-Duplicate Logic**: Conducted by a raw SQL PostGIS query using `ST_DWithin` and distance configurations set to `50m` in `app/config.py`.
- **Verdict**: Pass.

## 8. Incident Clustering Audit

- **PostGIS Strategy**: Uses `ST_ClusterDBSCAN` with configured `EPS=0.001` degrees (~111m) and `MINPOINTS=1`. 
- **Centroids**: Calculated appropriately using `ST_Centroid(ST_Collect(location))` through `app/services/incident_pipeline.py`.
- **Optimization Concern**: ST_ClusterDBSCAN currently runs across the entire table via `OVER ()` without partitioning. While fully adequate for a hackathon environment, it wouldn't scale gracefully in enterprise scenarios.
- **Verdict**: Pass.

## 9. WebSocket Audit

- **Path**: `WS /ws/incidents`
- **Auth**: Connection correctly rejects queries lacking valid token scopes. Reads `token` from URL parameters due to browser header limits.
- **Events**: Correctly broadcasts `incident_created`, `incident_updated`, `incident_dispatched`, and `resource_updated`.
- **Verdict**: Pass.

## 10. Dispatch & Resource Audit

- **Human-In-The-Loop**: Fully implemented. Dispatches occur solely through explicit POST to `/dispatch`. No automatic dispatches exist.
- **Constraint Defect**: `POST /incidents/{incident_id}/dispatch` verifies `quantity <= quantity_available` via standard scalar selection. Because there is no PostgreSQL row locking (e.g., `.with_for_update()`), concurrent human dispatches could bypass the check. 
- **Verdict**: Concurrency Defect. 

## 11. Failure-Handling Audit

- **AI Failure boundary**: Gemini stubs explicitly fail cleanly and return non-destructive placeholders without manipulating or dropping original incident metadata.
- **Database Failure boundary**: A resilient FastAPI Lifespan skips crash upon initial DB failures, enabling `/health` checks to cleanly report liveness errors.
- **Verdict**: Pass.

## 12. Test-Suite Audit

- **Tests Provided**: 34 tests defined natively within `tests/` utilizing isolated connection strategies (`NullPool`).
- **Verifiable**: **UNVERIFIED - environment limitation** 
- **Reason**: The runtime evaluation of test validity was halted because Docker is completely absent in the host environment. Establishing a local virtual environment mapping native PostgreSQL/PostGIS behavior was impossible.

## 13. Docker / Setup / Reproducibility Audit

- **Docker Configs**: Checked `Dockerfile` and `docker-compose.yml`. Configured using `postgis/postgis:16-3.4` and `python:3.12-slim`.
- **Startup Configs**: Valid. Uses healthchecks before mounting backend processes. 
- **Verification**: **UNVERIFIED - environment limitation** (Docker inaccessible).

## 14. Security Audit

- Safe parameterized ORM usage with SQLAlchemy.
- Token validation present on WebSockets.
- Standard hashing schemas used for User/Authority Passwords. 
- No committed secrets.

## 15. Documentation-vs-Code Audit

## Claims That Are Incorrect or Unsupported

```text
DOCUMENT CLAIM: All 34 tests pass
ACTUAL REPOSITORY STATE: Tests are unavailable for execution
SEVERITY: MEDIUM (Due to environment constraints)
EVIDENCE: System lacks Docker dependencies to boot testable database
RECOMMENDED ACTION: Re-verify on system containing Docker Desktop.
```

```text
DOCUMENT CLAIM: Backend Core is 100% complete and fully verified.
ACTUAL REPOSITORY STATE: Concurrency flaw in resource dispatching
SEVERITY: HIGH
EVIDENCE: No row locking or check constraints for `quantity_available`
RECOMMENDED ACTION: Refactor dispatch implementation to implement `.with_for_update()`
```

## 16. Contract Drift

No observable contract drift detected. All endpoints accurately mirror definitions provided in `day1-contracts-and-repo-setup.md`.

## 17. Critical Findings

```text
Finding: Race condition on dispatch endpoint causing negative resource quantities.
Why it matters: Multiple dispatchers trying to deploy the same ambulance could simultaneously bypass the quantity check, assigning resources that do not exist.
Expected behavior: The database prevents quantities from dipping below zero. 
Actual behavior: A standard `SELECT` is used prior to subtraction, leading to race conditions.
File: `app/routers/incidents.py`
Relevant function/class: `create_dispatch`
Evidence: Lacks `.with_for_update()` in the query. Lacks check constraints in `app/db/_models/resource.py`.
Recommended fix: Introduce row-locking using `select(Resource).with_for_update()` when checking resources during dispatch. Add a CheckConstraint in SQLAlchemy on `quantity_available >= 0`.
```

```text
Finding: ST_ClusterDBSCAN runs continuously on the entire `sos_report` table.
Why it matters: As thousands of SOS messages flow in, DB compute will bottleneck drastically. 
Expected behavior: Clustering operates solely on unresolved/active windows.
Actual behavior: `OVER ()` scans all historical records. 
File: `app/routers/sos.py`
Relevant function/class: `upload_sos_batch`
Evidence: The raw query executes across `sos_report` without partitions.
Recommended fix: (OPTIMIZATION) Add `WHERE created_at > ...` bounding constraints to the CTE in the PostGIS string.
```

## 18. Missing Tests

Cannot fully review test viability as `pytest` fails due to no PostGIS container availability. 

## 19. Required Fixes Before Merge

1. Refactor `app/routers/incidents.py` line 160 (`select(Resource)`) to apply row-locking via `.with_for_update()`.
2. Add a `CheckConstraint("quantity_available >= 0")` on the `Resource` SQLAlchemy model in `app/db/_models/resource.py` and generate a new Alembic migration.

## 20. Final Acceptance Decision

```text
FINAL DECISION:
YELLOW

The backend cannot be merged into the integration branch until the concurrency defect is patched.

Required actions before merge:
1. Address resource concurrency defect by applying `.with_for_update()` in `app/routers/incidents.py`.
2. Apply `CheckConstraint` onto the Resource database model to ensure inventory can never dip below zero.
3. Validate tests on a Docker-enabled environment. 
```
