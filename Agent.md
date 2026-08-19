# Agent.md
**Component**: AI Processing & Resource Intelligence Services (Backend)

## Project Context
Building the backend modules for a disaster response coordination platform. The components implemented here are:
1. `app/services/ai`: Gemini API (`google-genai==1.5.0`) integration for generating cluster summaries and situation briefs.
2. `app/services/optimizer`: Google OR-Tools (`ortools==9.10.4067`) integration for CP-SAT resource allocation.
3. `app/services/mock_idrn`: Mock resource registry based on IDRN public data and Dehradun DDMP 2023 with an explicit seed script.

## Current Implementation State (SERVICE LAYER READY FOR BACKEND INTEGRATION ✅)

### Database Integration Layer (`app/db/`)
- **Architecture Ownership**: Component D owns the database infrastructure (PostgreSQL, PostGIS, Alembic, canonical DB models: `ResourceModel`, `IncidentModel`, `SOSReport`, `DispatchRecord`, `SessionLocal`, `get_db()`, `init_db()`). Component E operates as a consumer of D's database layer and session management without competing ORM schemas.
- **ORM Models (`app/db/models.py`)**: `ResourceModel` (table `resources`) and `IncidentModel` (table `incidents`) with `to_pydantic()` / `from_pydantic()` helpers. `INCIDENT_STORE` has 0 production runtime dependency.

### AI Services (`app/services/ai/`)
- **Incident Summarization** (`summarizer.py`): Uses Gemini (`google-genai==1.5.0`) with `GenerateContentConfig(response_schema=IncidentSummaryOutput)` to synthesize incident summaries. On Gemini failure: `severity = None`, `ai_success = False`, raw SOS preserved. NULL severity persists as `NULL` in DB and reads back as `None` without fabricated `medium` severity. Fallback uses `max(people_counts)` to avoid double-counting. Fallback medical flag requires explicit emergency evidence (`emergency_type == medical` or distress text), ignoring static background `medical_snapshot` profile attachments. Untrusted SOS text wrapped with injection defense markers.
- **Situation Brief** (`situation_brief.py`): Aggregates active incidents directly from the database (`get_active_incidents(db=db)`), resource inventories, and OR-Tools recommendations into a skimmable 3–5 sentence paragraph via Gemini. Process-local `SituationBriefCache` with thread-safe lock. `start_periodic_refresh()` background daemon worker is idempotent (checks `is_alive()` before spawning threads). Fallback handles offline/503 states safely with `is_fallback = True`.

### Optimizer Services (`app/services/optimizer/`)
- **Allocator** (`allocator.py`): CP-SAT solver (`ortools==9.10.4067`) assigns resources by severity priority with Haversine distance penalty. Safely handles nullable severity (`severity: Optional[SeverityEnum] = None`). Input validation rejects invalid constraints (`maximum_distance < 0`, `maximum_allocation < 0`) before solver execution. Supported constraints: `excluded_resource_ids`, `required_resource_categories`, `maximum_distance`, `maximum_allocation`. Eligible statuses: `available` and `partially_deployed` (with `quantity_available > 0`).
- **Incident Service** (`incident_service.py`): DB-backed lifecycle logic. `create_incident()` and `update_incident()` persist to DB first and execute automatic optimization against DB state. Preserves existing valid severity if Gemini fails during an update. `run_on_demand_optimization()` queries active incidents from DB.
- **Resource Registry** (`resource_registry.py`): DB-backed queries for `get_all_resources()` and `get_available_resources()`. Resource reads strictly query the database and return `[]` if empty (no automatic seeding during reads).

### Mock IDRN (`app/services/mock_idrn/`)
- **Seed** (`seed.py`): Explicit, idempotent seeding entry point (`python -m app.services.mock_idrn.seed`) into `ResourceModel` table via SQLAlchemy session. Sources: IDRN Public Query and Dehradun DDMP 2023.

### Safety Invariants (Enforced Across All Services)
- Recommendations never mutate `quantity_available`, never auto-dispatch, never alter resource statuses, and never create `DispatchRecord`s (Component D authority flow responsibility).
- Gemini failure never fabricates severity — returns `None` for new incidents, preserves existing valid severity on updates.
- Untrusted SOS text is delimited and system-prompt-guarded against injection.

## Shared Schemas & Enums (`app/models/schemas.py`)
- `SOSRequest`: Uses `SOSStatus` enum (`pending_local`, `in_relay`, `uploaded`).
- `Incident`: Uses `IncidentStatus` enum (`new`, `acknowledged`, `dispatched`, `resolved`) and `severity: Optional[SeverityEnum] = None`.
- `RecommendedResource`: Includes `resource_id: Optional[str] = None` to map concrete resources for authority dispatch.
- `DispatchRecord`: Added canonical model with `DispatchStatus` enum (`dispatched`, `en_route`, `arrived`, `completed`).

## Integration API for Backend Core (Component D)

| What to import | Where | Used for |
|---|---|---|
| `get_cached_situation_brief()` | `app.services.ai.situation_brief` | `GET /api/v1/situation-brief` handler |
| `refresh_situation_brief()` | `app.services.ai.situation_brief` | `POST /api/v1/situation-brief/refresh` handler |
| `start_periodic_refresh()` | `app.services.ai.situation_brief` | FastAPI `on_startup` lifecycle event |
| `generate_incident_summary()` | `app.services.ai.summarizer` | SOS → Incident pipeline |
| `create_incident()` / `update_incident()` | `app.services.optimizer.incident_service` | Incident CRUD with DB persistence & auto-optimization |
| `run_on_demand_optimization()` | `app.services.optimizer.incident_service` | `POST /api/v1/optimize/allocate` handler |
| `get_all_resources()` / `get_available_resources()` | `app.services.optimizer.resource_registry` | Resource listing endpoints |
| `seed_database(db)` | `app.services.mock_idrn.seed` | DB seeding via explicit CLI script |
| `init_db()` / `get_db()` | `app.db.database` | SQLAlchemy session management |

## Test Coverage (52 Passed, 0 Skipped, 0 Failed — 52 Total)
- `tests/test_ai_summarizer.py`: 10 tests (coherent summary, fallback, medical flag semantics, missing fields, invalid severity, negative population, malformed JSON, API exception, prompt injection, real Gemini API)
- `tests/test_db_seed.py`: 3 tests (persistence, idempotency, incident DB model)
- `tests/test_incident_integration.py`: 9 tests (auto-recommendation, capacity, priority, failure, dispatch safety)
- `tests/test_optimizer.py`: 13 tests (CP-SAT constraints, partially_deployed, priorities, invalid constraint rejection)
- `tests/test_p0_remediation.py`: 6 tests (NULL severity roundtrip, Gemini failure persistence, CASE B preservation, restart recovery)
- `tests/test_situation_brief.py`: 11 tests (aggregation, critical cases, resource availability, OR-Tools, zero incidents, cache failure handling, untrusted input, no side effects, background refresh, worker idempotency, real Gemini situation brief API)

## Deviations and Notes
- `google-genai` is pinned to `1.5.0` and `ortools` is pinned to `9.10.4067`.
- Mock IDRN resource intelligence uses data from IDRN public queries and Dehradun DDMP 2023. Not a live IDRN API integration.
- Demand estimation formulas in `allocator.py` (e.g. `shelter = ceil(people / 20)`) are project/demo heuristics used for optimization demonstration, not official NDMA/IDRN allocation rules (P3 item).
- SituationBriefCache is process-local, designed for single-worker/hackathon FastAPI deployment. Multi-worker production deployments should utilize centralized caching (e.g. Redis).
