# SIH Backend — Disaster Response Coordination Platform

**Component D — Backend Core · SIH 2026**

Python · FastAPI · PostgreSQL + PostGIS · SQLAlchemy (async) · Alembic · Docker

---

## Quick Start (Docker — recommended)

```bash
# 1. Clone and enter the repo
cd backend

# 2. Copy environment template (edit POSTGRES_PASSWORD and JWT_SECRET_KEY before production)
cp .env.example .env

# 3. Start PostGIS + API in one command
docker compose up --build

# 4. Verify
curl http://localhost:8000/api/v1/health
# → {"status":"ok","database":"ok","postgis_version":"...","api_version":"0.1.0"}

# 5. Swagger UI
open http://localhost:8000/docs
```

---

## Local Development (without Docker)

```bash
# Requires Python 3.12+ and a running PostGIS instance

# 1. Create virtual environment
python -m venv .venv
source .venv/bin/activate      # Windows: .venv\Scripts\activate

# 2. Install dependencies
pip install -r requirements.txt

# 3. Set DATABASE_URL to localhost
# Edit .env: DATABASE_URL=postgresql+asyncpg://sih:sih_dev_secret@localhost:5432/sih_db

# 4. Run the API
uvicorn app.main:app --reload --host 0.0.0.0 --port 8000
```

---

## Database Migrations (Alembic)

```bash
# Apply all migrations
alembic upgrade head

# Create a new migration (after modifying app/db/models.py)
alembic revision --autogenerate -m "describe your change"

# Roll back one migration
alembic downgrade -1

# Show current revision
alembic current
```

> **Note:** The database migrations have been fully generated up to Phase 5. If modifying schemas, ensure you auto-generate a new migration and do not alter applied ones.

---

## Running Tests

```bash
# All tests (no live DB required for Phase 1 tests)
pytest tests/ -v

# With coverage report
pytest tests/ -v --cov=app --cov-report=term-missing
```

---

## Endpoints

| Method | Path | Auth | Description |
|--------|------|------|-------------|
| GET | `/api/v1/health` | none | Liveness + DB check |
| GET | `/api/v1/stats/summary` | none | Dashboard summary strip |
| POST | `/api/v1/auth/device/register` | none | Android device registration |
| POST | `/api/v1/auth/authority/login` | none | Authority login |
| POST | `/api/v1/auth/authority/refresh` | none | Token refresh |
| POST | `/api/v1/sos/batch` | device_jwt | SOS batch ingest |
| GET | `/api/v1/sos/{uuid}/status` | device_jwt | SOS upload status |
| GET | `/api/v1/incidents` | authority | Incident list (filterable) |
| GET | `/api/v1/incidents/{id}` | authority | Incident detail |
| PATCH | `/api/v1/incidents/{id}` | authority | Update incident status |
| GET | `/api/v1/incidents/{id}/recommendations` | authority | AI resource recommendations |
| POST | `/api/v1/incidents/{id}/dispatch` | authority | Human-confirmed dispatch |
| POST | `/api/v1/incidents/{id}/refresh-summary` | authority | Re-run AI summary |
| GET | `/api/v1/resources` | authority | Resource list |
| GET | `/api/v1/resources/{id}` | authority | Resource detail |
| POST | `/api/v1/resources` | admin | Create resource (admin) |
| PATCH | `/api/v1/resources/{id}` | admin | Update resource (admin) |
| GET | `/api/v1/situation-brief` | authority | AI situation brief |
| POST | `/api/v1/situation-brief/refresh` | admin | Trigger brief refresh |
| POST | `/api/v1/optimize/allocate` | authority | On-demand optimization |
| WS | `/ws/incidents?token=<jwt>` | authority | Live incident WebSocket |

---

## Environment Variables

See [`.env.example`](.env.example) for the full list with descriptions.

Key variables:

| Variable | Description |
|----------|-------------|
| `DATABASE_URL` | PostgreSQL+asyncpg connection string |
| `JWT_SECRET_KEY` | HS256 signing secret — generate with `openssl rand -hex 32` |
| `GEMINI_API_KEY` | Set by Component E (AI + Resource Intelligence) |
| `DEBUG` | `true` enables SQL echo and verbose logging |

---

## Directory Structure

```
backend/
├── app/
│   ├── main.py              # FastAPI app, router registration, lifespan
│   ├── config.py            # Settings (pydantic-settings, reads .env)
│   ├── core/
│   │   └── security.py      # JWT + bcrypt (Phase 2)
│   ├── db/
│   │   ├── engine.py        # Async SQLAlchemy engine + get_db dependency
│   │   ├── base.py          # Declarative Base
│   │   └── models.py        # Model registry (import all ORM models here)
│   ├── models/              # Pydantic request/response schemas (per phase)
│   ├── routers/
│   │   └── misc.py          # /health, /stats/summary
│   ├── services/
│   │   ├── ai/              # Gemini stub → Component E replaces
│   │   ├── optimizer/       # OR-Tools stub → Component E replaces
│   │   └── mock_idrn/       # Mock IDRN-style resource registry
│   └── ws/                  # WebSocket connection manager (Phase 5)
├── alembic/                 # DB migrations
├── tests/                   # pytest test suite
├── scripts/                 # seed scripts (Phase 4)
├── docker-compose.yml
├── Dockerfile
├── requirements.txt
├── alembic.ini
├── pytest.ini
└── .env.example
```

---

## Integration Notes

- **Android (Component C):** calls `POST /sos/batch` with `Authorization: Bearer <device_jwt>`
- **AI/Optimizer (Component E):** implements `app/services/ai/gemini.py` and `app/services/optimizer/ortools_solver.py` — replace the stubs in those packages
- **Dashboard (Component F):** connects to `WS /ws/incidents?token=<authority_access_token>` and calls the REST endpoints listed above

> **Mock IDRN notice:** The resource registry is a mock IDRN-style database populated from publicly available disaster-management resource information. It is NOT a live IDRN/government integration.

---

## Current Status & Next Steps

✅ **Phase 1-5 Backend Core Completed**
All backend infrastructure, authentication, SOS clustering, WebSocket capabilities, and incident/resource APIs have been fully implemented, validated, and tested (100% pass rate). 

**Component E Integration:**
The AI and optimizer endpoints currently exist as integration stubs returning empty or null data (located in `app/services/ai/` and `app/services/optimizer/`). The next phase is for Component E to replace these stubs with real implementations connecting to Gemini and OR-Tools.
