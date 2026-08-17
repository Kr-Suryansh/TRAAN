# ApiEndpoints.md

*This document matches the master API blueprint from day1-contracts-and-repo-setup.md. Component E service integration details are documented below.*

## AI / Situation Brief
| Method | Endpoint | Auth | Notes |
|---|---|---|---|
| GET | `/api/v1/situation-brief` | Bearer authority | Latest Gemini-generated paragraph, cached, regenerated every 5–10 min. Falls back safely if Gemini is offline/503. |
| POST | `/api/v1/situation-brief/refresh` | Bearer admin | Force regenerate situation brief from active DB incidents and resources. |
| POST | `/api/v1/incidents/{incident_id}/refresh-summary` | Bearer authority | Force Gemini to regenerate `ai_summary`. Preserves existing valid severity on Gemini failure. |

## Optimizer
| Method | Endpoint | Auth | Notes |
|---|---|---|---|
| POST | `/api/v1/optimize/allocate` | Bearer authority | `{incident_ids?, constraints?}` → OR-Tools assignment plan (`[{incident_id, resource_id, quantity, reasoning}]`) |

### Supported Optimizer Constraints (`constraints` JSON object)
- `excluded_resource_ids`: `List[str]` — Drop specific candidate resources before allocation.
- `required_resource_categories`: `List[str]` — Limit candidates to specific resource categories (e.g. `["rescue", "medical"]`).
- `maximum_distance`: `float` — Exclude candidate resources located farther than N kilometers from the incident location.
- `maximum_allocation`: `int` — Cap the maximum quantity of any single resource type allocated to an incident.

### Dispatch Safety Rule
`/api/v1/optimize/allocate` generates **recommendations only**. It does **NOT** dispatch resources, decrement `quantity_available`, alter resource status, or create `DispatchRecord`s. Explicit authority dispatch remains the sole responsibility of Backend Core (Component D).

## Resources (Mock IDRN)
| Method | Endpoint | Auth | Notes |
|---|---|---|---|
| GET | `/api/v1/resources?category=&status=&district=` | Bearer authority | List database-backed resources. Eligible optimization statuses: `available` and `partially_deployed` with `quantity_available > 0`. |
| GET | `/api/v1/resources/{resource_id}` | Bearer authority | Detail |
| POST | `/api/v1/resources` | Bearer admin | Create (seeding only) |
| PATCH | `/api/v1/resources/{resource_id}` | Bearer authority | Update quantity/status |

**Note on Bulk Seeding**: A seed script `app/services/mock_idrn/seed.py` is used to populate the local mock IDRN database via the DB session (`ResourceModel` table) instead of the REST API.

## Service-Level Integration (Component E -> Component D)

Backend Core (Component D) handles REST routing, authentication, and HTTP request parsing, delegating to Component E service functions:

| Endpoint / Trigger | Component E Service Function | Module Path |
|---|---|---|
| `GET /api/v1/situation-brief` | `get_cached_situation_brief(db)` | `app.services.ai.situation_brief` |
| `POST /api/v1/situation-brief/refresh` | `refresh_situation_brief(db)` | `app.services.ai.situation_brief` |
| FastAPI Startup Event | `start_periodic_refresh()` | `app.services.ai.situation_brief` |
| SOS Ingestion / Clustering | `generate_incident_summary(sos_reports)` | `app.services.ai.summarizer` |
| Incident CRUD | `create_incident()`, `update_incident()` | `app.services.optimizer.incident_service` |
| `POST /api/v1/optimize/allocate` | `run_on_demand_optimization()` | `app.services.optimizer.incident_service` |
| Resource Listing / Detail | `get_all_resources()`, `get_available_resources()` | `app.services.optimizer.resource_registry` |
