# Agent.md
**Component**: AI Processing & Resource Recommendation Services (Backend)

## Project Context
Building the backend modules for a disaster response coordination platform. The components implemented here are:
1. `app/services/ai`: Gemini API integration for generating cluster summaries and situation briefs.
2. `app/services/optimizer`: Google OR-Tools integration for resource allocation.
3. `app/services/mock_idrn`: Mock resource registry based on IDRN with a seed script.

## Current Implementation State (READY FOR INTEGRATION ✅)

### Database Layer (`app/db/`)
- **`app/db/database.py`**: SQLAlchemy engine, `SessionLocal`, `Base`, `get_db()` generator, and `init_db()`. Supports SQLite for dev/test and PostgreSQL via `DATABASE_URL` env var for production.
- **`app/db/models.py`**: `ResourceModel` (table `resources`) and `IncidentModel` (table `incidents`) with `to_pydantic()` / `from_pydantic()` helpers. PostgreSQL/SQLite is the sole authoritative production source of truth for both incidents and resources. `INCIDENT_STORE` is not used in production runtime paths.

### AI Services (`app/services/ai/`)
- **Incident Summarization** (`summarizer.py`): Gemini `gemini-3.6-flash` synthesizes incident summaries from clustered SOS reports. Strict Pydantic schema validation rejects invalid severity/population. On Gemini failure: `severity = None`, `ai_success = False`, raw SOS preserved. NULL severity persists as `NULL` in DB and reads back as `None` without fabricated `medium` severity. Fallback uses `max(people_counts)` to avoid double-counting. Untrusted SOS text wrapped with injection defense markers.
- **Situation Brief** (`situation_brief.py`): Aggregates active incidents directly from the database (`get_active_incidents(db=db)`), resource inventories (including `partially_deployed`), and OR-Tools recommendations into a skimmable 3–5 sentence paragraph via Gemini. Thread-safe `SituationBriefCache` with `get_cached_situation_brief()` / `refresh_situation_brief()`. Background daemon refresh every 5 minutes. Validates output: single paragraph, max 2000 chars, rejects JSON/prompt-leak phrases. Fallback handles offline/503 states safely.

### Optimizer Services (`app/services/optimizer/`)
- **Allocator** (`allocator.py`): OR-Tools CP-SAT solver assigns resources by severity priority with Haversine distance penalty. Safely handles nullable severity (`severity: Optional[SeverityEnum] = None`). Enforces constraints: `maximum_distance`, `required_resource_categories`, `excluded_resource_ids`, `maximum_allocation`. Eligible statuses: `available` and `partially_deployed` (with `quantity_available > 0`).
- **Incident Service** (`incident_service.py`): 100% DB-backed lifecycle logic. `create_incident()` and `update_incident()` persist to DB first and execute automatic optimization against DB state. Preserves existing valid severity if Gemini fails during an update. `run_on_demand_optimization()` queries active incidents from DB.
- **Resource Registry** (`resource_registry.py`): DB-backed queries for `get_all_resources()` and `get_available_resources()`. Includes `partially_deployed` resources.

### Mock IDRN (`app/services/mock_idrn/`)
- **Seed** (`seed.py`): Idempotent upserts into `ResourceModel` table via SQLAlchemy session. Sources: IDRN Public Query and Dehradun DDMP 2023.

### Safety Invariants (Enforced Across All Services)
- Recommendations never mutate `quantity_available`, never auto-dispatch, never alter resource statuses, and never create `DispatchRecord`s (Component D authority flow responsibility).
- Gemini failure never fabricates severity — returns `None` for new incidents, preserves existing valid severity on updates.
- Untrusted SOS text is delimited and system-prompt-guarded against injection.

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
| `seed_database(db)` | `app.services.mock_idrn.seed` | DB seeding on first run |
| `init_db()` / `get_db()` | `app.db.database` | SQLAlchemy session management |

## Test Coverage (48 Passed, 1 Skipped, 0 Failed — 49 Total)
- `tests/test_ai_summarizer.py`: 9 tests (failure safety, prompt injection, Gemini API)
- `tests/test_db_seed.py`: 3 tests (persistence, idempotency, model conversion)
- `tests/test_incident_integration.py`: 9 tests (auto-recommendation, capacity, priority, failure)
- `tests/test_optimizer.py`: 11 tests (CP-SAT constraints, partially_deployed, priorities)
- `tests/test_p0_remediation.py`: 6 tests (NULL severity roundtrip, Gemini failure persistence, CASE B preservation, restart recovery)
- `tests/test_situation_brief.py`: 11 tests passed + 1 live test skipped (`test_real_gemini_situation_brief_api` skipped due to external Gemini API HTTP 503 UNAVAILABLE / high demand; fallback behavior verified)

## Deviations and Notes
- Pydantic schema stubs in `app/models/schemas.py` should be replaced with actual shared schemas when integrated into the `sih-backend` monorepo.
- Mock IDRN resource intelligence uses data from IDRN public queries and Dehradun DDMP 2023. Not a live IDRN API integration.
- Demand estimation formulas in `allocator.py` (e.g. `shelter = ceil(people / 20)`) are project/demo heuristics used for optimization demonstration, not official NDMA/IDRN allocation rules (P3 item).
- Provenance metadata is kept in `data.json` alongside resource data rather than breaking the canonical `Resource` contract.
- Endpoint dispatch logic is deliberately omitted to maintain separation of concerns (Component D responsibility).
