# TRAAN — Component D Backend Forensic Audit & Remediation Checklist

Date: 2026-08-13
Repository: `Kr-Suryansh/TRAAN`
Branch audited: `backend`

## Audit basis

Primary specifications:
1. `Updated_antigravity-build-prompts.md`
2. `day1-contracts-and-repo-setup.md`

Secondary context:
- `Agent.md`
- `ApiEndpoints.md`
- `Logs.md`
- current repository source/tests/configuration
- current Git history

Evidence rule: executable behavior > source code > tests > OpenAPI/configuration > documentation > developer claims.

## Final decision

**YELLOW — CONDITIONAL PASS / NOT YET FINAL-MERGE READY**

Component D is substantially implemented. The backend has real FastAPI routes, PostgreSQL/PostGIS models, SOS ingestion, deduplication, clustering, incident management, resource management, dispatch, authentication, and WebSocket infrastructure.

However, the repository still has several material acceptance issues. The highest-priority blocker is the database migration/clean-checkout state. There are also contract-validation gaps, incomplete E-owned integrations, and insufficient clean-runtime verification.

---

# 1. P0 — MUST FIX BEFORE INTEGRATION

## D-001 — Reconcile Alembic migration history

**Severity:** P0  
**Status:** BLOCKER

### Finding

The current repository contains a migration adding missing columns, but the documented migration chain claims earlier migrations for Device, Authority, SOSReport, Incident, Resource, and DispatchRecord.

The current branch state must be proven to initialize a completely empty database correctly.

### Required remediation

Ensure the repository contains one coherent Alembic chain that creates all required tables:

- `device`
- `authority`
- `sos_report`
- `incident`
- `resource`
- `dispatch_record`

Then verify:

```bash
docker compose down -v
docker compose up --build
alembic upgrade head
pytest
```

Preferably make the application startup process run migrations explicitly or document the mandatory migration step.

### Acceptance test

A completely fresh PostgreSQL volume must reach a working API without manually creating tables.

---

## D-002 — Run a clean end-to-end verification

**Severity:** P0  
**Status:** UNVERIFIED

The latest commit claims all 35 tests pass, but that claim has not been independently reproduced in the audit environment.

Required:

```bash
docker compose down -v
docker compose up --build
alembic upgrade head
pytest -q
```

Also verify:

- `/api/v1/health`
- `/api/v1/docs`
- `/api/v1/openapi.json`
- device registration
- authority login
- SOS upload
- incident creation
- WebSocket event
- resource dispatch

---

# 2. P1 — CONTRACT / API FIXES

## D-003 — Incident pagination

**Severity:** P1  
**Status:** VERIFY/FIX

`GET /api/v1/incidents` must implement the canonical `page` and `size` contract and return the required paginated response.

Required checks:

- default page
- explicit page
- explicit size
- invalid page
- invalid size
- total count
- item count
- stable ordering

---

## D-004 — `GET /incidents/{id}` response shape

**Severity:** P1  
**Status:** VERIFY/FIX

Confirm that the detail endpoint returns all fields required by the canonical contract, especially:

- incident fields
- assigned resources
- recommended resources
- full source SOS report objects

Do not return only UUIDs if the contract requires nested source SOS objects.

---

## D-005 — `recommended_resources` canonical schema

**Severity:** P1  
**Status:** RECONCILE

The shared contract requires:

```json
{
  "resource_type": "string",
  "quantity": 0,
  "reasoning": "string"
}
```

Do not allow D and F to independently interpret this as `resource_id`.

Make the backend schema, OpenAPI schema, stored JSONB, tests, and dashboard TypeScript types identical.

---

## D-006 — `assigned_resources` canonical schema

**Severity:** P1  
**Status:** RECONCILE

Ensure the response contract consistently represents assigned resource identifiers as the canonical list type.

Test the exact JSON shape.

---

## D-007 — Authority login response

**Severity:** P1  
**Status:** VERIFY

Verify the login response against the Day 1 contract and make sure the fields consumed by Component F are explicitly documented as part of the shared schema.

---

## D-008 — Authority refresh semantics

**Severity:** P1  
**Status:** DESIGN DECISION REQUIRED

Current behavior should be compared against the contract's refresh-token semantics.

Either:
1. implement a true refresh-token flow, or
2. explicitly document and approve the simplified access-token renewal behavior.

Do not let D and F assume different token semantics.

---

# 3. P1 — SECURITY / AUTHORIZATION

## D-009 — Device identity binding

**Severity:** P1  
**Status:** VERIFY/FIX

`POST /api/v1/sos/batch` must enforce that:

```text
payload.gateway_device_id == authenticated device JWT subject
```

Additionally verify the intended relationship between the SOS item's `device_id` and authenticated gateway identity. Do not invent a stronger rule than the shared contract, but explicitly test the required authorization boundary.

---

## D-010 — Role enforcement

**Severity:** P1  
**Status:** VERIFY

Test:

- admin can create resources
- non-admin cannot create resources
- admin can refresh situation brief
- non-admin cannot refresh situation brief
- device JWT cannot access authority routes
- authority JWT cannot access device-only routes

---

## D-011 — JWT secret hygiene

**Severity:** P1  
**Status:** HARDEN

Development defaults such as `changeme` / `dev-only-change-before-production` must not be usable in a final deployment.

Fail startup or emit a clear configuration error when production uses the default secret.

---

## D-012 — CORS

**Severity:** P1  
**Status:** HARDEN

Do not leave:

```text
allow_origins=["*"]
allow_credentials=True
```

for the final deployment.

Use the actual dashboard origin(s).

---

# 4. P1 — INPUT VALIDATION

## D-013 — UUID validation

**Severity:** P1  
**Status:** FIX

Use strict UUID validation for SOS UUID fields rather than accepting arbitrary strings when the contract specifies UUIDv4.

Test malformed UUIDs.

---

## D-014 — Location validation

**Severity:** P1  
**Status:** VERIFY

Enforce:

```text
latitude: -90..90
longitude: -180..180
```

for all externally supplied locations.

---

## D-015 — People count / quantity validation

**Severity:** P1  
**Status:** VERIFY

Reject negative:

- `people_count`
- `quantity`
- `quantity_total`
- `quantity_available`

---

## D-016 — Resource PATCH validation

**Severity:** P1  
**Status:** FIX

`PATCH /resources/{id}` must enforce:

```text
quantity_available >= 0
quantity_available <= quantity_total
status ∈ canonical enum
```

The database constraint alone is insufficient if it only protects the lower bound.

---

## D-017 — Incident status validation

**Severity:** P1  
**Status:** FIX/VERIFY

Use a canonical enum for incident status rather than a free-form string.

---

# 5. P1 — SOS / INCIDENT PIPELINE

## D-018 — Exact UUID deduplication

**Severity:** P1  
**Status:** IMPLEMENTED

Keep and regression-test.

---

## D-019 — Spatial/time deduplication

**Severity:** P1  
**Status:** IMPLEMENTED / TUNE

Keep the two-stage deduplication.

Document the false-positive tradeoff: geographically close SOS reports from different victims may be classified as near duplicates.

---

## D-020 — Incident clustering

**Severity:** P1  
**Status:** IMPLEMENTED / TUNE

`ST_ClusterDBSCAN()` is present.

Review:

- EPS distance
- minimum points
- time relevance
- cluster recalculation behavior

A spatial-only clustering rule can combine reports from different events separated in time.

---

## D-021 — Incident severity ownership

**Severity:** P1  
**Status:** INCOMPLETE UNTIL E INTEGRATION

Current D logic can derive severity from incoming hints.

The final architecture requires Gemini to provide AI severity/urgency information.

D should retain raw reports even when AI is unavailable.

---

## D-022 — `area_name`

**Severity:** P1  
**Status:** INCOMPLETE

The contract expects useful incident area information. Current pipeline behavior should be reconciled with the final requirement for area naming/reverse geocoding.

---

## D-023 — Affected population calculation

**Severity:** P1  
**Status:** REVIEW

Current aggregation behavior should be explicitly justified.

If overlapping SOS reports are intentionally deduplicated, document why `max()` or another aggregation method is used.

---

# 6. P1 — RESOURCE / DISPATCH

## D-024 — Concurrency-safe dispatch

**Severity:** P1  
**Status:** IMPLEMENTED

Keep row locking and transactional resource decrement behavior.

Regression tests required for:

- enough quantity
- insufficient quantity
- exact remaining quantity
- concurrent dispatch attempts

---

## D-025 — Human approval invariant

**Severity:** P1  
**Status:** IMPLEMENTED

No AI/optimizer operation may directly create a dispatch record.

Dispatch must remain an explicit authority action.

---

## D-026 — Resource seed script

**Severity:** P1  
**Status:** MISSING/VERIFY

The expected mock-IDRN seed workflow must exist and actually populate PostgreSQL.

Required command:

```bash
python -m app.services.mock_idrn.seed
```

Seeded data must be clearly labeled as mock data and contain source-traceable demo values.

---

# 7. P1 — AI / OR-TOOLS BOUNDARY

## D-027 — Gemini integration boundary

**Severity:** P1  
**Status:** INTENTIONAL STUB

The Gemini implementation belongs to Component E.

D must expose a clean service boundary and preserve raw incident data if Gemini fails.

---

## D-028 — OR-Tools integration boundary

**Severity:** P1  
**Status:** INTENTIONAL STUB

The optimizer belongs to Component E.

D must preserve incidents when optimization fails and must never automatically dispatch resources.

---

## D-029 — Situation brief

**Severity:** P1  
**Status:** STUB

`GET /situation-brief` and refresh integration may exist as boundaries, but the actual Gemini-generated/cached brief is E-owned work.

Do not mark the complete system as AI-complete until E is integrated.

---

# 8. P1 — WEBSOCKET

## D-030 — WebSocket authentication

**Severity:** P1  
**Status:** IMPLEMENTED

Verify:

- missing token → 1008
- invalid token → 1008
- device token → rejected
- authority token → accepted

---

## D-031 — Required events

**Severity:** P1  
**Status:** IMPLEMENTED / VERIFY

Verify:

```text
incident_created
incident_updated
incident_dispatched
resource_updated
situation_brief_updated
```

and exact:

```json
{
  "event": "event_name",
  "data": {}
}
```

shape.

---

## D-032 — WebSocket integration test

**Severity:** P1  
**Status:** TEST EXISTS / RUNTIME UNVERIFIED

Acceptance scenario:

```text
connect authority WebSocket
        ↓
POST /sos/batch
        ↓
incident pipeline
        ↓
incident_created
```

The event should arrive within the required demo timeframe.

---

# 9. P2 — QUALITY / MAINTAINABILITY

## D-033 — CI pipeline

Add GitHub Actions for:

```text
dependency install
database service
alembic upgrade head
pytest
```

Optionally add lint/type checks.

---

## D-034 — Documentation consistency

Reconcile:

- `Agent.md`
- `ApiEndpoints.md`
- `Logs.md`
- actual source
- actual migration files
- actual test count

Documentation must not claim complete migrations or complete AI integration if those artifacts are absent.

---

## D-035 — Repository hygiene

Ensure final branch does not contain:

```text
.env
.venv
.pytest_cache
build artifacts
temporary ZIP archives
local database volumes
```

Only `.env.example` should be committed for environment configuration.

---

# 10. Required verification matrix

| Area | Required proof |
|---|---|
| Clean DB | Fresh Docker volume + Alembic |
| API | OpenAPI compared to canonical contract |
| Auth | Device + authority + RBAC tests |
| SOS | Exact + near-duplicate tests |
| Pipeline | SOS → incident |
| WebSocket | Incident-created event |
| Resources | CRUD + validation |
| Dispatch | Transaction + concurrency |
| AI boundary | Failure preserves incident |
| Optimizer boundary | Failure preserves incident |
| Seed | Real PostgreSQL insertion |
| Tests | Full suite in clean environment |
| Dashboard integration | D ↔ F real API |
| Documentation | Matches actual repository |

---

# 11. Final acceptance criteria

Component D can be marked final when all of the following are true:

- [ ] Clean PostgreSQL/PostGIS database initializes from repository migrations.
- [ ] `alembic upgrade head` succeeds from empty DB.
- [ ] Full pytest suite passes in clean environment.
- [ ] OpenAPI matches canonical contract.
- [ ] All endpoint paths/methods match.
- [ ] Request/response schemas match.
- [ ] Device and authority auth boundaries are tested.
- [ ] RBAC is tested.
- [ ] SOS exact dedup works.
- [ ] SOS spatial/time dedup works.
- [ ] Incident clustering works.
- [ ] Incident WebSocket event works.
- [ ] Resource validation is strict.
- [ ] Dispatch is transactionally safe.
- [ ] Human approval is required.
- [ ] E integration boundaries are documented.
- [ ] Mock IDRN seed works if owned by the current submission.
- [ ] Repository is clean.
- [ ] Documentation matches the actual code.

## Final classification

**Component D: SUBSTANTIALLY IMPLEMENTED — CONDITIONAL PASS**

**Do not reject/rebuild the architecture. Fix the migration state, contract mismatches, validation/security gaps, and clean-runtime verification first.**
