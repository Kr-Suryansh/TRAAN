# SIH 2026 — Component D: Complete Technical Code Guide
**Author:** Component D Team  
**Purpose:** A thorough but approachable walkthrough of every major piece of the backend — what it is, what it does, what tool/library is used, and why.  
**Use this to:** understand integration points, explain code to teammates, troubleshoot issues, and verify nothing was built "in air."

---

## ✅ Boundary Compliance Check (vs. Master Prompt)

Before diving in, here is a point-by-point confirmation that we stayed within our lane:

| Constraint from `Updated_antigravity-build-prompts.md` | Status |
|---|---|
| Backend Core owns REST APIs, database logic, deduplication, incident clustering, auth, WebSockets | ✅ All done, nothing else touched |
| Text-only SOS payloads — no photo/voice fields | ✅ No such fields in any schema |
| Resource allocation is always human-approved, never automatic | ✅ Dispatch requires a human `POST /incidents/{id}/dispatch` call — there is zero automatic dispatch code |
| Resource DB is explicitly a mock IDRN-style registry | ✅ Stubs are marked as mock; `scripts/seed.py` left for Component E |
| JWT must expire | ✅ Both device and authority JWTs have expiry set |
| Passwords never stored in plaintext | ✅ bcrypt hashing via `passlib` |
| No secrets committed | ✅ Only `.env.example` committed, never `.env` |
| WebSocket requires JWT validation | ✅ Token validated before connection is accepted |
| Gemini/OR-Tools owned by Component E | ✅ We only left clearly-marked stubs in `app/services/` |
| Endpoint paths exactly match contract | ✅ Confirmed — no renaming, no new paths |
| No code that dispatches without human confirmation | ✅ Single code path for dispatch: the `POST /dispatch` endpoint with authority JWT |

**Verdict: Full compliance. No boundary violations.**

---

## 🗂️ Repository Layout — The Big Picture

```
backend/
├── app/                       ← All Python application code
│   ├── main.py                ← FastAPI app factory + router registration
│   ├── config.py              ← All environment variables
│   ├── core/
│   │   └── security.py        ← JWT creation + validation, password hashing
│   ├── db/
│   │   ├── engine.py          ← SQLAlchemy async engine + get_db() dependency
│   │   ├── base.py            ← SQLAlchemy DeclarativeBase
│   │   ├── models.py          ← Imports all ORM models so Alembic sees them
│   │   └── _models/
│   │       ├── device.py      ← Device table (Android phones)
│   │       ├── authority.py   ← Authority table (Dashboard users)
│   │       ├── sos.py         ← SOSReport table (raw incoming reports)
│   │       ├── incident.py    ← Incident table (clustered incidents)
│   │       ├── resource.py    ← Resource table (mock IDRN inventory)
│   │       └── dispatch.py    ← DispatchRecord table (deployment audit trail)
│   ├── models/                ← Pydantic input/output schemas (NOT database)
│   │   ├── auth.py            ← Request/response models for auth endpoints
│   │   ├── common.py          ← Shared types (e.g. Location)
│   │   ├── sos.py             ← SOS upload request/response models
│   │   ├── incidents.py       ← Incident schemas + DispatchRequest/Response
│   │   ├── resources.py       ← Resource schemas
│   │   └── stats.py           ← Stats summary schema
│   ├── routers/               ← FastAPI routes, one file per feature group
│   │   ├── misc.py            ← /health and /stats/summary
│   │   ├── auth.py            ← Device register, authority login/refresh
│   │   ├── sos.py             ← SOS batch upload + status check
│   │   ├── incidents.py       ← All incident endpoints + dispatch
│   │   ├── resources.py       ← All resource endpoints
│   │   └── optimizer.py       ← AI/optimizer stubs (Component E fills these)
│   ├── services/
│   │   ├── ai/stub.py         ← TEMPORARY — Gemini integration boundary stub
│   │   ├── optimizer/stub.py  ← TEMPORARY — OR-Tools integration boundary stub
│   │   └── incident_pipeline.py ← SOS-cluster → Incident aggregation pipeline
│   └── ws/
│       └── manager.py         ← WebSocket connection manager + /ws/incidents endpoint
├── alembic/
│   ├── env.py                 ← Alembic migration environment config
│   └── versions/
│       ├── 9a8cef361e78_phase_2... ← Device + Authority tables
│       ├── 66a422e96f8f_phase_3... ← SOSReport table
│       └── c50d0ccb37bd_phase_4... ← Incident, Resource, DispatchRecord tables
├── tests/
│   ├── conftest.py            ← Shared pytest fixtures
│   ├── test_health.py
│   ├── test_auth.py
│   ├── test_sos.py
│   ├── test_incidents.py
│   ├── test_resources.py
│   ├── test_ws.py
│   └── test_pipeline.py       ← SOS-to-Incident pipeline integration tests
├── Dockerfile                 ← How to containerize the FastAPI app
├── docker-compose.yml         ← Orchestrates API + PostgreSQL/PostGIS together
├── requirements.txt           ← Python dependencies
├── alembic.ini                ← Alembic configuration
├── pytest.ini                 ← Pytest configuration
├── .env.example               ← Template for environment variables
├── Logs.md                    ← Chronological session log
├── Agent.md                   ← Current project state for any future agent
└── ApiEndpoints.md            ← Implemented API contract documentation
```

---

## 🛠️ Complete Tools & Technologies Used

This table lists every tool, library, or technology used in the backend, what it does, and where it appears in the code.

| # | Tool / Library | Category | What It Does in This Project | Where Used |
|---|---|---|---|---|
| 1 | **FastAPI** | Web Framework | Defines all REST API endpoints. Handles request parsing, response serialization, auto-generates Swagger docs at `/docs`. | `app/main.py`, `app/routers/*.py` |
| 2 | **Uvicorn** | ASGI Server | Runs the FastAPI application inside the Docker container. It's the actual HTTP server process. | `Dockerfile` (CMD line) |
| 3 | **PostgreSQL 15** | Relational Database | Stores all data permanently — devices, authority users, SOS reports, incidents, resources, dispatch records. | Docker `db` container (`postgis/postgis:15-3.4` image) |
| 4 | **PostGIS 3.4** | PostgreSQL Extension | Adds geospatial data types and spatial functions to PostgreSQL. Enables storing GPS coordinates as geometry objects and running spatial queries like "find all points within 50 meters." | Every table with a `location` column, `sos.py` deduplication, `incident_pipeline.py` centroid |
| 5 | **SQLAlchemy (async)** | ORM | Maps Python classes to database tables. Lets you write Python objects instead of raw SQL for most operations. | `app/db/engine.py`, `app/db/_models/*.py`, all routers |
| 6 | **asyncpg** | PostgreSQL Driver | The low-level Python driver that actually sends SQL to PostgreSQL over the network. SQLAlchemy uses it under the hood. Used with `async/await` for non-blocking DB calls. | `app/config.py` (in `DATABASE_URL`), `app/db/engine.py` |
| 7 | **GeoAlchemy2** | SQLAlchemy Extension | Adds PostGIS geometry column types to SQLAlchemy. Allows defining `Column(Geometry('POINT', 4326))` in ORM models. | `app/db/_models/sos.py`, `app/db/_models/incident.py`, `app/db/_models/resource.py` |
| 8 | **Alembic** | DB Migration Tool | Manages database schema changes as a versioned chain of migration scripts. Ensures schema changes are applied safely without destroying data. | `alembic/`, `alembic.ini`, all `versions/` files |
| 9 | **Pydantic v2** | Data Validation | Validates all incoming JSON request bodies and serializes outgoing responses. If Android sends the wrong field type, Pydantic automatically returns a structured 422 error. | `app/models/*.py` |
| 10 | **pydantic-settings** | Configuration | Reads environment variables (or `.env` file) into a typed Python class. | `app/config.py` |
| 11 | **python-jose** | JWT Library | Creates and verifies JSON Web Tokens (JWTs) for both device and authority authentication. Signs tokens with `HS256` algorithm using `JWT_SECRET_KEY`. | `app/core/security.py` |
| 12 | **passlib (bcrypt)** | Password Hashing | Hashes authority passwords before storing in DB. bcrypt is a slow, salted hashing algorithm — deliberately slow to defeat brute-force attacks. | `app/core/security.py` |
| 13 | **Docker** | Containerization | Packages the FastAPI app and its Python dependencies into a reproducible, isolated container image. | `Dockerfile` |
| 14 | **Docker Compose** | Container Orchestration | Starts and links both the `api` and `db` containers together on a shared network with one command. | `docker-compose.yml` |
| 15 | **pytest** | Testing Framework | Runs all automated integration tests. 34 tests covering every major feature. | `tests/*.py`, `pytest.ini` |
| 16 | **pytest-asyncio** | Async Test Plugin | Allows `async def test_...()` functions to work with pytest, since FastAPI is async. | `pytest.ini` (`asyncio_mode = auto`) |
| 17 | **httpx (AsyncClient)** | HTTP Test Client | Makes async HTTP requests to the FastAPI app during tests — no real server needed, goes directly through ASGI. | `tests/conftest.py` |
| 18 | **CORS Middleware** | Security | Allows the React Dashboard (running on a different port/origin) to call the backend API from the browser. | `app/main.py` |
| 19 | **WebSocket (Starlette)** | Real-time Communication | Maintains persistent connections with dashboard clients. Server pushes incident updates instantly without dashboard polling. | `app/ws/manager.py` |
| 20 | **ST_ClusterDBSCAN** | PostGIS Function | Groups nearby SOS report GPS points into spatial clusters. Reports within ~111m of each other get the same `cluster_id`. | `app/routers/sos.py` |
| 21 | **ST_DWithin** | PostGIS Function | Checks if a point is within a given distance (in meters) of another point. Used for near-duplicate SOS detection (50m radius). | `app/routers/sos.py` |
| 22 | **ST_Centroid / ST_Collect** | PostGIS Functions | Calculates the geographic center point of a group of GPS coordinates. Used to find the centroid location of an incident cluster. | `app/services/incident_pipeline.py` |
| 23 | **ST_Y / ST_X** | PostGIS Functions | Extracts latitude (Y) and longitude (X) from a geometry object to convert back to plain floats for JSON responses. | `app/routers/incidents.py`, `app/routers/resources.py` |

---

## 🗃️ PostgreSQL + PostGIS — The Spatial Database

**Why PostgreSQL?**  
PostgreSQL is the most capable open-source relational database. It natively supports JSONB (binary JSON columns), advanced indexing, window functions, and most importantly — extensions. Standard MySQL or SQLite cannot do what we need here.

**Why PostGIS?**  
The core problem of a disaster response system is geographic: "Which SOS reports are near each other?", "What is the center of this cluster?", "Which resources are closest to this incident?" These are **spatial questions** that normal databases cannot answer efficiently.

PostGIS adds:
- **Geometry data type**: Store GPS coordinates as proper geometric objects, not just floats in two separate columns. The database understands these as points on the Earth's surface.
- **Spatial indexing (GIST index)**: Like a regular database index but for geography. Without it, "find all points within 50m" would scan every row. With it, it's fast even with millions of rows.
- **Spatial functions**: A library of built-in functions for geometric calculations that would take thousands of lines of math code to write from scratch.

**How we use PostGIS — explained simply:**

```
Storing a GPS point:
  Python sends: "SRID=4326;POINT(longitude latitude)"
  PostGIS stores: binary geometry object
  SRID=4326 means: "these coordinates are in WGS84 (the GPS coordinate system)"

Reading a GPS point back:
  SELECT ST_Y(location) as lat, ST_X(location) as lng
  ST_Y = latitude (north-south), ST_X = longitude (east-west)
```

**The three key spatial operations we use:**

### 1. Near-Duplicate Detection — `ST_DWithin`
```sql
WHERE ST_DWithin(
    location::geography,                          -- cast to geography type
    ST_MakePoint(lng, lat)::geography,            -- incoming report location
    50.0                                          -- 50 metres
)
```
- `::geography` cast is **critical** — without it, `50.0` means 50 *degrees* (half the Earth). With the cast, it means 50 *metres*.
- If an existing SOS report is within 50m AND within 15 minutes of the incoming one, we treat it as a near-duplicate and reject the new one.

### 2. Spatial Clustering — `ST_ClusterDBSCAN`
```sql
SELECT uuid, ST_ClusterDBSCAN(location, eps := 0.001, minpoints := 1) OVER () as cid
FROM sos_report
```
- DBSCAN = Density-Based Spatial Clustering of Applications with Noise.
- `eps := 0.001` degrees ≈ 111 metres at the equator — the neighbourhood radius.
- `minpoints := 1` — even a single isolated report forms its own cluster.
- `OVER ()` — this is a SQL window function, meaning it runs DBSCAN over all rows at once.
- Output: each row gets a cluster ID (integer). Reports near each other get the same cluster ID.
- We then use `MIN(uuid)` within each cluster as the stable string `cluster_id`.

### 3. Incident Centroid — `ST_Centroid(ST_Collect(...))`
```sql
SELECT ST_Y(ST_Centroid(ST_Collect(location))) as lat,
       ST_X(ST_Centroid(ST_Collect(location))) as lng
FROM sos_report WHERE cluster_id = :cid
```
- `ST_Collect(location)` — combines all point geometries in the cluster into one geometry collection.
- `ST_Centroid(...)` — finds the mathematical centre of that collection.
- The result is the best single GPS coordinate to represent where the incident is occurring.

**Database configuration:**
- Image: `postgis/postgis:15-3.4` (PostgreSQL 15 + PostGIS 3.4)
- Port exposed to host: `5432`
- Database name: `sih_db`
- Username: `sih` / Password: `sih_dev_secret` (development only — change for production)
- SRID used throughout: `4326` (WGS84 — the standard GPS coordinate system)

---

## 🐋 Docker & Docker Compose — The Foundation

**File:** [docker-compose.yml](file:///c:/Users/kmura/backend/docker-compose.yml), [Dockerfile](file:///c:/Users/kmura/backend/Dockerfile)

**Why Docker?**  
The backend requires PostgreSQL *with the PostGIS extension*. PostGIS is a special geospatial plugin that doesn't come with standard Postgres. Instead of requiring every developer to manually install Postgres + PostGIS on their machine (error-prone, version-mismatch nightmare), we containerize the entire stack. One command starts everything exactly the same every time.

**What `docker-compose.yml` does:**
- Starts **two containers** that talk to each other on a private Docker network:
  1. `db` container: Uses `postgis/postgis:15-3.4` image — this is Postgres 15 with PostGIS pre-installed. Sets up a database named `sih_db`, username `sih`, password `sih_dev_secret`.
  2. `api` container: Built from our `Dockerfile`. Runs the FastAPI Python application. Waits for the `db` container to be healthy before starting (using `depends_on` + health checks).

**What `Dockerfile` does:**
- Starts from `python:3.12-slim` (lightweight Python image).
- Copies `requirements.txt` and installs all Python packages.
- Copies the entire `app/` directory.
- Runs `uvicorn app.main:app --host 0.0.0.0 --port 8000` to start the server.

**To start everything:**
```bash
docker compose up --build -d       # Build and start both containers in background
docker compose exec -e PYTHONPATH=/app api alembic upgrade head  # Apply DB schema
```

**To stop:**
```bash
docker compose down
```

---

## ⚙️ Configuration — `app/config.py`

**Tool:** `pydantic-settings`

**What it does:** Reads environment variables (from the shell or a `.env` file) into a Python class. If a variable isn't set, it uses a sensible default.

**Key settings:**
- `DATABASE_URL`: The async SQLAlchemy connection string. In Docker it points to the `db` container. Format: `postgresql+asyncpg://user:password@host:port/dbname`.
- `JWT_SECRET_KEY`: The secret used to sign and verify all JWTs. **Must be changed before production.**
- `DEDUPLICATION_DISTANCE_M`: Default 50m — reports within 50 meters of each other are considered near-duplicates.
- `DEDUPLICATION_TIME_WINDOW_MINUTES`: Default 15 min — only de-dupes reports within this time window.
- `DBSCAN_EPS`: Default `0.001` degrees (≈111m at equator) — the radius for DBSCAN spatial clustering.
- `DBSCAN_MINPOINTS`: Default `1` — even a single SOS can form a cluster.

---

## 🗄️ Database Layer

### `app/db/engine.py` — The Connection Pool

**Tool:** `SQLAlchemy` (async) + `asyncpg`

**What it does:** Creates the async connection engine that all database operations flow through. The key function here is `get_db()`, which is a FastAPI *dependency* — every route function that needs a database session declares `db: AsyncSession = Depends(get_db)` and FastAPI automatically provides a fresh session and closes it after the request.

**Why async?** FastAPI is built on ASGI (async Python web standard). Using async SQLAlchemy means database queries don't block the server — it can handle other requests while waiting for the DB, making it far more efficient under load.

### `app/db/base.py` — The Base Class

**What it does:** Defines `Base = DeclarativeBase()`. Every ORM model class (`class Incident(Base):`) inherits from this. It tells SQLAlchemy "this Python class represents a database table."

### `app/db/models.py` — The Registry

**What it does:** Imports every model file. This is critical for Alembic — Alembic scans `Base.metadata` to detect all tables, and tables only appear in metadata if their model file has been imported. Without this file, Alembic would generate empty migrations.

---

## 🧱 Database Models (ORM) — `app/db/_models/`

These are the Python representations of your PostgreSQL tables.

### `device.py` — Android Device Registry

**Table:** `device`  
**Purpose:** Every Android phone that installs the app registers once. We store its model name and app version, and it gets a UUID `id`. This ID becomes the `sub` (subject) claim inside the device JWT.

### `authority.py` — Authority User Table

**Table:** `authority`  
**Purpose:** Dashboard users (police, DDMA, fire, etc.) stored here. Password is stored as a bcrypt hash, never plaintext. The `role` column is a string that must be one of the seven defined roles.

### `sos.py` — Raw SOS Report Table

**Table:** `sos_report`  
**Tool:** `GeoAlchemy2` for the `location` column  
**Purpose:** Every incoming SOS report is permanently stored here, exactly as received. This is the raw data archive.  
**Key column:** `location = Column(Geometry(geometry_type='POINT', srid=4326))` — SRID 4326 means WGS84 latitude/longitude, the same coordinate system GPS uses. PostGIS stores this as a binary geometry object, not a simple float pair.  
**Key column:** `cluster_id` — initially null. After DBSCAN clustering runs, all reports in the same spatial cluster get the same `cluster_id` string (the UUID of the minimum report in that cluster, for stability).

### `incident.py` — Aggregated Incident Table

**Table:** `incident`  
**Purpose:** The "cleaned up" view of a cluster of SOS reports. One `Incident` = one cluster. This is what the dashboard map shows.  
**Key columns:**
- `cluster_id`: Links back to the DBSCAN cluster ID that formed this incident.
- `source_sos_uuids` (JSONB): The list of raw SOS UUIDs that make up this incident — Component F shows these as the "contributing reports."
- `location` (Geometry Point): The geographic centroid of all contributing SOS reports.
- `flags` (JSONB): `{medical_emergency, trapped, elderly_or_children, structural_damage}` — currently defaults to `false`, will be set by Component E's Gemini analysis.
- `recommended_resources` (JSONB): Component E will write optimizer results here.

### `resource.py` — Mock IDRN Resource Table

**Table:** `resource`  
**Purpose:** The mock resource inventory (ambulances, boats, shelters, etc.). Populated manually or by Component E's `seed.py` script.  
**Key columns:** `quantity_total`, `quantity_available` — dispatch subtracts from `quantity_available`.

### `dispatch.py` — Dispatch Audit Trail

**Table:** `dispatch_record`  
**Purpose:** Every time an authority confirms a dispatch, one row is written here. Immutable audit record. Has FK relationships to both `incident` and `resource`.

---

## 🔐 Authentication — `app/core/security.py` + `app/routers/auth.py`

**Tools:** `passlib` (bcrypt), `python-jose` (JWT)

### How Device JWT Works (Component A/B/C interaction point)

1. Android calls `POST /api/v1/auth/device/register` with `{device_model, app_version}`.
2. We create a `Device` row with a new UUID `id`.
3. We call `create_device_jwt(device_id)` which creates a JWT with claims `{sub: device_id, type: "device", exp: now + 30days}`, signed with `JWT_SECRET_KEY`.
4. We return `{device_id, device_jwt}` to the Android app. The app stores this securely.
5. **For every subsequent SOS upload**, Android sends `Authorization: Bearer <device_jwt>` header.
6. Our `require_device_jwt` dependency calls `verify_jwt(token)` and checks `type == "device"`. If invalid or expired, returns HTTP 401.

### How Authority JWT Works (Component F interaction point)

1. Dashboard calls `POST /api/v1/auth/authority/login` with `{email, password}`.
2. We look up the `Authority` row by email, then call `passlib.verify(password, hashed_password)`. If wrong, HTTP 401.
3. We create an `access_token` JWT with claims `{sub: authority_id, role: "admin", type: "authority", exp: now + 60min}`.
4. Dashboard stores this token and sends `Authorization: Bearer <access_token>` on every API call.
5. Our `require_authority_jwt` dependency validates the token type and role as needed.

**Why two separate JWT types?** Device tokens don't carry role information and have a much longer expiry (30 days) because Android phones can't interactively re-login. Authority tokens are short-lived (1 hour) because they represent human login sessions.

---

## 📦 SOS Ingestion — `app/routers/sos.py`

**Tool:** PostGIS (`ST_DWithin`, `ST_ClusterDBSCAN`)

This is the most complex route. Here is exactly what happens when Android uploads a batch:

### Step 1 — Exact UUID Deduplication
```sql
SELECT uuid FROM sos_report WHERE uuid IN (incoming_uuids)
```
Any UUID already in the DB → immediate `duplicate_uuids`. No DB write needed.

### Step 2 — Spatial + Temporal Near-Duplicate Detection
For each *new* (non-exact-duplicate) report, runs:
```sql
SELECT uuid FROM sos_report 
WHERE ST_DWithin(location::geography, ST_MakePoint(lng, lat)::geography, 50.0)
AND ABS(EXTRACT(EPOCH FROM (created_at - incoming_created_at))) <= 900
LIMIT 1
```
- `::geography` cast — this makes `ST_DWithin`'s `50.0` unit be **meters** (not degrees). Without this cast, 50 would mean 50 degrees, which is nearly half the Earth.
- If a match is found → this report is a near-duplicate and goes into `duplicate_uuids`.

### Step 3 — Batch Insert
All non-duplicate reports are `db.add()`ed and `db.flush()`ed (written to DB but not committed yet).

### Step 4 — DBSCAN Clustering
After committing, runs this PostGIS window function over **all** `sos_report` rows:
```sql
WITH clusters AS (
    SELECT uuid, ST_ClusterDBSCAN(location, eps := 0.001, minpoints := 1) OVER () as cid
    FROM sos_report
),
cluster_uuids AS (
    SELECT cid, MIN(uuid) as stable_cluster_id
    FROM clusters WHERE cid IS NOT NULL GROUP BY cid
)
UPDATE sos_report SET cluster_id = cu.stable_cluster_id
FROM clusters c JOIN cluster_uuids cu ON c.cid = cu.cid
WHERE sos_report.uuid = c.uuid;
```
- `ST_ClusterDBSCAN` groups all points within 0.001 degrees (≈111m) of each other.
- `MIN(uuid)` gives a stable, deterministic cluster ID — if more reports join later, the cluster ID doesn't change.

### Step 5 — Incident Pipeline (Phase 5)
After clustering, calls `await process_sos_clusters(db, accepted_uuids)` — see next section.

---

## 🔄 Incident Pipeline — `app/services/incident_pipeline.py`

**What it does:** Takes the `accepted_uuids` from the SOS upload, finds which `cluster_id`s they belong to, and for each cluster either creates a new `Incident` or updates an existing one.

**For each cluster:**
1. Queries all `SOSReport` rows with that `cluster_id`.
2. **In Python**: calculates `first_reported_at` (min), `last_updated_at` (max), `report_count`, `emergency_types` (distinct set), `estimated_people_affected` (max `people_count`).
3. **In PostGIS**: calculates geographic centroid: `ST_Centroid(ST_Collect(location))` — this gives the mathematical center point of all reports.
4. **Severity derivation**: reads all `severity_hint` values. If any is "critical" → severity is "critical". Falls through "high" → "medium" → "low".
5. **DB Upsert**: checks if `Incident` with this `cluster_id` already exists.
   - If **new**: creates row with `status = "new"`, `flags` all false.
   - If **existing**: updates the aggregated fields (more SOS reports may have joined the cluster).
6. **WebSocket broadcast**: emits `incident_created` or `incident_updated` event with the full `IncidentResponse` payload.

---

## 🚦 FastAPI Application — `app/main.py`

**Tool:** FastAPI + Uvicorn

**FastAPI** is a modern Python web framework built on top of **Starlette** (ASGI) and **Pydantic** (data validation). It automatically:
- Validates request bodies against Pydantic schemas — if Android sends the wrong field type, it returns a structured 422 error automatically.
- Generates the **Swagger UI at `/docs`** — this is the live contract other teams build against, per the master prompt.
- Handles async routes natively.

**Key things in `main.py`:**
- **Lifespan function**: runs code at startup (tests DB connectivity, logs PostGIS version) and shutdown (disposes connection pool). If DB is down at startup, the app still starts — it doesn't crash — so the `/health` endpoint can report the failure gracefully.
- **CORS middleware**: allows the React Dashboard (different port/origin) to call our API from the browser. Set to `allow_origins=["*"]` for development — should be tightened to the dashboard's actual domain in production.
- **Router mounting**: each feature group (`auth`, `sos`, `incidents`, `resources`, `optimizer`) is a separate Python file with its own `APIRouter`. They're all mounted under `/api/v1` prefix here.

---

## 🔌 WebSocket — `app/ws/manager.py`

**Tool:** FastAPI WebSocket support (built-in via Starlette)

**Why WebSocket instead of polling?**  
The master prompt explicitly says "pushed via WebSocket, no polling." Polling means the dashboard would ask "any updates?" every second — wasteful and adds latency. WebSocket is a persistent two-way connection: the server **pushes** updates the moment they happen.

**`ConnectionManager` class:**
- Maintains `self.active_connections: List[WebSocket]` — a list of all currently connected dashboard clients.
- `connect(websocket)`: accepts the connection and adds to the list.
- `disconnect(websocket)`: removes from list when browser disconnects.
- `broadcast_event(event, data)`: loops through all active connections, sends `{"event": "incident_created", "data": {...}}` JSON to each. If a send fails (e.g., browser closed without a clean disconnect), it catches the error and removes that dead connection.

**Authentication on connect:**
```
WS /ws/incidents?token=<authority_access_token>
```
Before accepting the connection, validates the token. If missing or invalid → closes with WebSocket code `1008` (Policy Violation). Browsers can't set custom headers on WebSocket handshake, so the token goes in the query parameter per the master prompt spec.

**Events broadcast:**
- `incident_created` — emitted by pipeline when a new Incident is formed
- `incident_updated` — emitted when `PATCH /incidents/{id}` changes status, or pipeline updates an existing incident
- `incident_dispatched` — emitted when `POST /incidents/{id}/dispatch` creates a DispatchRecord
- `resource_updated` — emitted when resource quantity changes after a dispatch

---

## 📝 Pydantic Models — `app/models/`

**Tool:** Pydantic v2

Pydantic models serve two purposes:
1. **Request validation**: FastAPI uses them to parse and validate incoming JSON bodies.
2. **Response serialization**: FastAPI uses `response_model=IncidentResponse` to serialize Python objects into JSON output.

**Important:** These are NOT the database models. The ORM models (`app/db/_models/`) are SQLAlchemy objects representing database rows. Pydantic models (`app/models/`) are what goes in and out of the HTTP layer.

`ConfigDict(from_attributes=True)` on response models tells Pydantic it's okay to read attributes from an SQLAlchemy ORM object directly (not just from a dict). This is what makes `IncidentResponse.model_validate(incident_orm_obj)` work.

---

## 🔢 Alembic — Database Schema Migrations

**Tool:** Alembic

**Why Alembic?** You can't just delete and recreate the database every time you change a table. Production data must be preserved. Alembic tracks schema changes as a chain of versioned migration scripts.

**Migration chain:**
```
(initial empty DB)
      ↓
9a8cef361e78  Phase 2: device + authority tables
      ↓
66a422e96f8f  Phase 3: sos_report table with PostGIS geometry column
      ↓
c50d0ccb37bd  Phase 4: incident, resource, dispatch_record tables
```

**Key command:** `alembic upgrade head` — applies all pending migrations. Always run this after `docker compose up` before testing.

---

## 🧪 Testing — `tests/`

**Tools:** `pytest`, `pytest-asyncio`, `httpx` (async HTTP test client)

**Key challenge solved:** asyncpg connection pooling conflicts. The app's production engine uses a connection pool (connections stay open for efficiency). This causes problems in tests because pytest reuses the same event loop, and connection pool state bleeds between tests.

**Solution in `conftest.py`:** Override the engine with `NullPool` for tests. `NullPool` creates a brand-new connection for each session and immediately closes it. Slower, but perfectly isolated.

**`conftest.py` fixtures:**
- `client`: Creates an `httpx.AsyncClient` backed by the FastAPI ASGI app. No real HTTP server needed — requests go directly to the app.
- `cleanup_sos_data`: Deletes all `sos_report` rows before and after each test. Prevents spatial near-duplicate false positives between tests.
- `cleanup_phase4_data`: Deletes incident, resource, and dispatch_record rows.
- `setup_authority`: Creates a test authority user and returns the object.

**Test files:**
- `test_health.py`: Checks `/health` returns DB status.
- `test_auth.py`: Tests device register, authority login, token refresh.
- `test_sos.py`: Tests batch upload, exact duplicates, near-duplicates, status check.
- `test_incidents.py`: Tests listing, filtering, status update, dispatch workflow.
- `test_resources.py`: Tests CRUD operations and admin-only restrictions.
- `test_ws.py`: Tests WebSocket auth rejection and valid connection.
- `test_pipeline.py`: Integration test for the full SOS batch → Incident pipeline.

---

## ⚠️ Temporary Stubs — What Component E Must Replace

| File | Function | What it currently returns | What Component E must do |
|---|---|---|---|
| `app/services/ai/stub.py` | `summarize_incident(incident)` | Logs warning, returns `None` | Call Gemini API, return `{ai_summary, flags, severity, estimated_people_affected}` |
| `app/services/ai/stub.py` | `generate_situation_brief(incidents)` | Logs warning, returns `""` | Call Gemini API, return a paragraph string |
| `app/services/optimizer/stub.py` | `allocate_resources(incidents, resources)` | Logs warning, returns `[]` | Run OR-Tools, return list of `{incident_id, resource_id, quantity, reasoning}` |

The router endpoints in `app/routers/optimizer.py` and `app/routers/incidents.py` already call these functions. Component E only needs to replace the function bodies — no router changes needed.

---

## 🧪 How to Test Your Backend Is Working

### Option 1 — Swagger UI (Easiest)
1. Make sure Docker is running: `docker compose up -d`
2. Open **http://localhost:8000/docs** in your browser.
3. You'll see every endpoint listed. Click any endpoint → "Try it out" → fill the form → Execute.
4. You can register a device, copy the JWT, authorize (top right "Authorize" button), and test SOS upload all within the browser.

### Option 2 — `/health` Endpoint Quick Check
Open **http://localhost:8000/api/v1/health** in browser or run:
```bash
curl http://localhost:8000/api/v1/health
```
Should return: `{"status": "ok", "database": "ok", "postgis": "available", "version": "0.1.0"}`  
If `"database": "error"` → PostgreSQL container isn't healthy yet.

### Option 3 — Run the Full Test Suite
```bash
docker compose exec -e PYTHONPATH=/app api pytest tests/ -v
```
All 34 tests must pass. This is the definitive check.

### Option 4 — pgAdmin (Visual Database Inspection)
pgAdmin is a GUI for PostgreSQL. You can use it to browse tables, run SQL queries, and verify data.

**How to set it up:**
1. Download and install pgAdmin from https://www.pgadmin.org/download/
2. Open pgAdmin. Click "Add New Server".
3. In the "General" tab, give it a name: `SIH Dev`.
4. In the "Connection" tab:
   - **Host:** `localhost`
   - **Port:** `5432`
   - **Maintenance database:** `sih_db`
   - **Username:** `sih`
   - **Password:** `sih_dev_secret`
5. Click Save. You should now see the server in the left panel.
6. Navigate to: `SIH Dev → Databases → sih_db → Schemas → public → Tables`
7. Right-click any table → "View/Edit Data → All Rows" to browse records.

**Useful SQL queries to run in pgAdmin's Query Tool:**
```sql
-- Check all SOS reports
SELECT uuid, emergency_type, severity_hint, cluster_id, received_at FROM sos_report;

-- Check all incidents
SELECT incident_id, cluster_id, severity, status, report_count FROM incident;

-- Check resources
SELECT resource_id, category, sub_type, quantity_total, quantity_available FROM resource;

-- Check WebSocket event would be triggered — see which SOSReports have no Incident yet
SELECT s.cluster_id FROM sos_report s
LEFT JOIN incident i ON s.cluster_id = i.cluster_id
WHERE s.cluster_id IS NOT NULL AND i.incident_id IS NULL;
```

### Option 5 — Postman / Bruno (API Testing Tool)
Tools like Postman let you save a collection of API requests and replay them.
1. Create a request: `POST http://localhost:8000/api/v1/auth/device/register` with body `{"device_model":"Test","app_version":"1.0"}`
2. Copy the returned `device_jwt`
3. Create a `POST http://localhost:8000/api/v1/sos/batch` request with `Authorization: Bearer <device_jwt>` header and the full SOS batch body.
4. Check that the response contains `accepted_uuids`.
5. Then check pgAdmin or query `/api/v1/incidents` (with authority token) to verify an Incident was auto-created by the pipeline.

---

*This document was written as of Phase 5 completion. All 34 tests pass. Backend Core (Component D) is complete.*
