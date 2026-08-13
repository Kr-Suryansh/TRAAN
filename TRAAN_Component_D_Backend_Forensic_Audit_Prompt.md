# TRAAN — Component D Backend Forensic Audit Prompt

You are performing a **formal acceptance audit of Component D — Backend Core** of the TRAAN disaster-response platform.

This is an **audit, not an implementation task**.

Do **NOT** modify, refactor, rewrite, delete, or “improve” the repository during this audit unless explicitly instructed later. Your job is to determine whether the submitted Backend Core actually satisfies its contractual requirements.

The repository being audited is the current workspace.

## 1. Authoritative Sources

Use the following documents as the specification hierarchy.

### Primary specification

1. `Updated_antigravity-build-prompts.md`

This contains:

- the Master Prompt
- Global Engineering Instructions
- Component D / Backend Core prompt
- global Definition of Done
- failure-handling requirements
- security requirements
- acceptance criteria
- integration requirements

Treat the Backend Core section as the primary component specification.

### API contract

2. `day1-contracts-and-repo-setup.md`

This is the canonical API blueprint.

Verify:

- HTTP methods
- endpoint paths
- request schemas
- response schemas
- authentication requirements
- status codes
- WebSocket endpoint
- WebSocket event names
- database/schema expectations
- integration assumptions

Do **NOT** silently modify or reinterpret the contract.

### Submitted documentation

3. `SIH2026_ComponentD_TechnicalGuide.md`

4. `SIH2026_ComponentD_Handoff_Guide.md`

These are **claims made by the submitting team**, not authoritative proof.

For every important claim in these documents, independently verify it against the repository.

For example, if the guide says:

> “All 34 tests pass”

do not accept that statement without examining the actual tests and, where possible, executing them.

If the guide says:

> “Backend Core is 100% complete”

do not treat that as evidence of completion.

### Current project context

Also inspect:

5. `Agent.md`
6. `ApiEndpoints.md`
7. `Logs.md`

Use these to understand:

- intended repository state
- previous implementation decisions
- known issues
- development history
- API descriptions
- assumptions
- claimed verification

Where these conflict with the Master Prompt or Day 1 contract, explicitly report the conflict.

### Roadmap

If `15-day-roadmap.md` is present in the workspace, inspect it as additional project context.

Use it to determine whether the implementation corresponds to the expected development stages, but do **NOT** treat the roadmap as more authoritative than the Master Prompt or API contract.

---

# 2. Critical Audit Principle

You are NOT being asked:

> “Does the documentation say the backend is complete?”

You are being asked:

> **“Does the actual repository implement the Backend Core exactly as required by the Master Prompt and API contract, and can that implementation be independently verified?”**

Use this evidence hierarchy:

```text
Actual executable behavior
        ↓
Actual source code
        ↓
Actual tests
        ↓
Generated API/OpenAPI schemas
        ↓
Configuration / migrations / Docker
        ↓
Documentation
        ↓
Developer claims
```

Documentation must never be used to prove that code works.

If something cannot be verified, mark it as:

**UNVERIFIED**

rather than assuming it works.

Assume that the developer’s completion report may contain false or overstated claims. Your job is to prove each claim from the repository, not to validate the developer’s narrative.

---

# 3. First Inspect the Entire Repository

Before judging individual features:

1. Print the complete repository tree.
2. Identify the backend entry point.
3. Identify all routers.
4. Identify all models.
5. Identify all Pydantic schemas.
6. Identify all database configuration.
7. Identify all Alembic migrations.
8. Identify all services.
9. Identify authentication/security modules.
10. Identify WebSocket implementation.
11. Identify tests.
12. Identify Docker configuration.
13. Identify environment configuration.
14. Identify seed/mock-data scripts.
15. Identify documentation.
16. Identify generated/cache/build files that should not belong in the repository.

Do not assume that files mentioned in documentation actually exist.

Do not assume that files that exist are actually used.

Trace imports and call paths.

---

# 4. Determine the Actual Backend Core Boundary

According to the architecture, Component D owns:

- REST APIs
- PostgreSQL/PostGIS database logic
- SOS ingestion
- deduplication
- incident clustering
- authentication
- WebSockets
- incident management
- resource management
- dispatch backend logic

Component E owns:

- Gemini
- AI summarization
- situation brief generation
- OR-Tools
- resource optimization intelligence
- mock IDRN resource intelligence/seed data

Do NOT mark Component D incomplete merely because Component E’s intentional stubs exist.

However, verify that D provides the correct interfaces for Component E.

Determine whether the D/E boundary actually matches the contract.

---

# 5. API Contract Audit

Build a complete API audit table.

For EVERY endpoint in `day1-contracts-and-repo-setup.md`, report:

| Endpoint | Required Method | Required Auth | Required Request | Required Response | Actual Implementation | Match? | Evidence |
|---|---|---|---|---|---|---|---|

Audit every endpoint, including:

### Authentication

```text
POST /api/v1/auth/device/register
POST /api/v1/auth/authority/login
POST /api/v1/auth/authority/refresh
```

Verify:

- request fields
- response fields
- JWT type
- expiry
- refresh behavior
- role handling
- password handling
- device registration behavior
- authorization boundaries

### SOS

```text
POST /api/v1/sos/batch
GET /api/v1/sos/{uuid}/status
```

Verify:

- `device_jwt`
- batch schema
- gateway fields
- SOS fields
- HTTP 202
- accepted UUIDs
- duplicate UUIDs
- malformed payload handling
- idempotency
- duplicate handling

### Incidents

```text
GET /api/v1/incidents
GET /api/v1/incidents/{id}
PATCH /api/v1/incidents/{id}
GET /api/v1/incidents/{id}/recommendations
POST /api/v1/incidents/{id}/dispatch
POST /api/v1/incidents/{id}/refresh-summary
```

Verify exact request/response structures.

Pay particular attention to whether:

```text
GET /incidents/{id}
```

actually returns the full information required by the contract, including source SOS information.

Do not accept merely returning SOS UUIDs if the contract requires actual source report details.

### Resources

```text
GET /api/v1/resources
GET /api/v1/resources/{id}
POST /api/v1/resources
PATCH /api/v1/resources/{id}
```

Verify authorization and quantity/status handling.

### AI / optimizer boundaries

```text
GET /api/v1/situation-brief
POST /api/v1/situation-brief/refresh
POST /api/v1/optimize/allocate
```

Verify that D exposes the correct interface even if E’s underlying implementation is currently stubbed.

### Miscellaneous

```text
GET /api/v1/health
GET /api/v1/stats/summary
```

---

# 6. OpenAPI / Swagger Audit

Do not merely inspect route decorators.

Start the application if possible and inspect:

```text
/api/v1/docs
/api/v1/openapi.json
```

or the actual configured paths.

Compare generated OpenAPI schemas against the canonical API blueprint.

Check:

- endpoint paths
- HTTP methods
- request schemas
- response schemas
- enums
- optional vs required fields
- authentication schemes
- status codes

Report any contract drift.

This is especially important because the Master Prompt explicitly states that Swagger should reflect the exact shared schemas.

---

# 7. Database Audit

Inspect:

- SQLAlchemy models
- database configuration
- PostgreSQL configuration
- PostGIS configuration
- Alembic migrations
- indexes
- foreign keys
- constraints
- relationships
- geometry columns
- transaction handling

Verify that the database schema corresponds to the expected:

- device
- authority
- SOS report
- incident
- resource
- dispatch record

structures.

Check migrations in both directions:

```text
upgrade
downgrade
```

Do not only check that `upgrade head` exists.

Look for:

- missing indexes
- duplicate indexes
- invalid downgrade operations
- migration ordering problems
- schema/model mismatch
- nullable/non-nullable mismatches
- missing foreign keys
- missing uniqueness constraints
- unsafe destructive migrations

---

# 8. SOS Ingestion Audit

Trace the complete execution path:

```text
POST /sos/batch
      ↓
authentication
      ↓
request validation
      ↓
exact UUID deduplication
      ↓
spatial/time deduplication
      ↓
database insertion
      ↓
incident clustering
      ↓
incident creation/update
      ↓
WebSocket notification
```

Do not stop at the router.

Follow the actual function calls.

Verify:

### Exact duplicate behavior

If the same UUID is submitted twice:

- it must not create another SOS record
- it must be reported as duplicate
- the original record must remain valid

### Near-duplicate behavior

Different UUIDs with near-identical:

- location
- time

must be treated according to the defined contract.

Verify the actual spatial threshold and temporal threshold.

Do not merely check that a function named `deduplicate()` exists.

---

# 9. Incident Clustering Audit

The Master Prompt requires PostGIS spatial clustering.

Verify:

- PostGIS is actually enabled
- geometry is stored correctly
- coordinates are converted correctly
- clustering uses PostGIS
- `ST_ClusterDBSCAN()` is actually executed
- clustering parameters are sensible
- incident records are created/updated correctly

Test this scenario:

```text
SOS A
UUID = A
location = X
time = T

SOS B
UUID = B
location ≈ X
time ≈ T
```

Expected:

```text
SOS A
   \
    → SAME INCIDENT
   /
SOS B
```

Then test:

```text
SOS A
location = X
time = T

SOS B
location = far away
time = T
```

Expected:

```text
SEPARATE INCIDENTS
```

Also investigate what happens when a later SOS causes two existing clusters to merge.

Do not ignore cluster lifecycle/reconciliation behavior.

---

# 10. WebSocket Audit

Verify the exact contract:

```text
WS /ws/incidents?token=<jwt>
```

Verify:

- JWT authentication
- invalid token rejection
- missing token rejection
- valid token acceptance
- connection manager
- disconnect handling
- multiple clients
- broadcast behavior

Verify EVERY required event:

```text
incident_created
incident_updated
incident_dispatched
resource_updated
situation_brief_updated
```

For each event determine:

- who triggers it
- what data it contains
- whether the payload matches the contract
- whether all connected authorized clients receive it

Most importantly, test the actual end-to-end requirement:

```text
POST /sos/batch
      ↓
incident created
      ↓
WebSocket
      ↓
incident_created
```

The fact that a WebSocket connection can be established is NOT sufficient.

---

# 11. Authentication and Authorization Audit

Audit separately:

### Device authentication

Verify that a device can:

```text
register
  ↓
receive device_id + device_jwt
  ↓
use device_jwt for SOS endpoints
```

Verify that device tokens cannot access authority-only endpoints.

### Authority authentication

Verify:

```text
login
  ↓
access token
  ↓
role-based access
```

Test roles defined by the contract:

```text
DDMA
police
fire
ambulance
NDRF
SDRF
admin
```

Determine which endpoints require:

- authority
- admin
- device
- no authentication

Test unauthorized access.

Do not merely inspect dependency names; actually trace authorization logic.

---

# 12. Dispatch Audit

The Master Prompt makes human-in-the-loop dispatch non-negotiable.

Verify there is NO path equivalent to:

```text
SOS
 ↓
AI
 ↓
automatic dispatch
```

The required flow is:

```text
SOS
 ↓
Incident
 ↓
AI analysis
 ↓
OR-Tools recommendation
 ↓
Authority review
 ↓
Explicit confirmation
 ↓
DispatchRecord
```

Inspect the actual code for automatic dispatch paths.

Verify:

- resource exists
- incident exists
- quantity is valid
- quantity does not exceed available quantity
- resource inventory is updated
- DispatchRecord is created
- correct WebSocket event is emitted
- repeated dispatch behaves safely

---

# 13. Failure-Handling Audit

The Master Prompt explicitly requires failure-oriented design.

Check each of these.

### Backend

- duplicate batches
- duplicate UUIDs
- partial batch failures
- malformed SOS payloads
- invalid SOS fields
- database failure
- Gemini failure boundary
- OR-Tools failure boundary
- WebSocket disconnect
- expired JWT
- invalid JWT
- malformed requests
- resource conflicts

Specifically verify:

> AI failure must not cause raw SOS/incident data to disappear.

and:

> OR-Tools failure must not cause the incident to disappear or create a fabricated recommendation.

---

# 14. Transaction and Concurrency Audit

Inspect database transactions around:

- SOS batch ingestion
- deduplication
- incident creation
- resource quantity updates
- dispatch

Look for race conditions such as:

```text
Resource quantity = 1

Request A sees quantity = 1
Request B sees quantity = 1

A dispatches 1
B dispatches 1
```

Determine whether the database can prevent the resource from becoming negative or being double-dispatched.

If there is no concurrency protection, report it.

---

# 15. Test-Suite Audit

Do NOT simply count tests.

For every test file, determine:

- what functionality it tests
- whether it tests success cases
- whether it tests failure cases
- whether assertions are meaningful
- whether mocks hide the actual behavior
- whether integration tests actually exercise the database
- whether WebSocket tests actually test event delivery

Create:

| Requirement | Test Exists? | Test Meaningful? | Actually Passes? | Gap |
|---|---|---|---|---|

Pay special attention to the Backend Core acceptance criteria.

A test that merely verifies:

```python
assert response.status_code == 200
```

is not sufficient if the requirement is a complex business behavior.

---

# 16. Clean-Install / Reproducibility Audit

Determine whether a new developer can actually run the backend from a clean machine.

Inspect:

- `Dockerfile`
- `docker-compose.yml`
- `requirements.txt` / `pyproject.toml`
- `.env.example`
- Alembic
- startup scripts
- README
- commands in Technical Guide

Verify the documented sequence.

For example:

```text
docker compose up
alembic upgrade head
pytest
```

Determine whether the documented commands actually correspond to the repository.

If Docker is available, EXECUTE the commands.

If Docker is not available, explicitly mark runtime verification as:

**UNVERIFIED — environment limitation**

Do not pretend that it passed.

---

# 17. Secrets and Repository Hygiene

Inspect for:

- `.env`
- API keys
- JWT secrets
- passwords
- private credentials
- hardcoded tokens
- database credentials

Check `.gitignore`.

If real secrets are present:

**CRITICAL SECURITY FINDING**

Do not reproduce the secret value in your report.

---

# 18. Documentation-vs-Code Consistency Audit

Compare:

```text
Technical Guide
Handoff Guide
Agent.md
ApiEndpoints.md
Logs.md
```

against the actual code.

Create a section:

## Claims That Are Incorrect or Unsupported

For every discrepancy:

```text
DOCUMENT CLAIM
ACTUAL REPOSITORY STATE
SEVERITY
EVIDENCE
RECOMMENDED ACTION
```

Examples:

- documentation claims a test exists but it doesn’t
- documentation claims an endpoint exists but it doesn’t
- documentation claims 34 tests pass but a required behavior isn’t tested
- documentation says an endpoint returns raw SOS data but it doesn’t
- documentation describes a feature that is only stubbed

---

# 19. Do Not Confuse Component D and Component E

Explicitly classify every AI/optimization-related finding as one of:

```text
Expected Component E boundary
Actual Component D defect
Integration boundary defect
```

For example:

```text
Gemini stub exists
→ EXPECTED

D cannot provide Incident data required by E
→ COMPONENT D DEFECT

Optimizer endpoint exists but has wrong request schema
→ COMPONENT D / CONTRACT DEFECT

OR-Tools implementation missing
→ NOT A D DEFECT
```

---

# 20. Security Audit

Check at minimum:

- password hashing
- JWT signing
- JWT expiration
- token validation
- refresh-token handling
- role authorization
- SQL injection exposure
- unsafe dynamic SQL
- arbitrary resource access
- malformed input
- excessive payload sizes
- sensitive information in responses
- secrets in source control
- CORS configuration
- WebSocket authentication

Do not invent vulnerabilities. Only report vulnerabilities supported by the code.

---

# 21. Performance / Scalability Sanity Check

This is a hackathon project, so do not demand enterprise-scale architecture.

However, identify obvious problems such as:

- clustering the entire SOS table on every request
- unbounded WebSocket connection lists
- unbounded batch sizes
- expensive N+1 queries
- repeated database queries
- missing spatial indexes
- loading entire datasets unnecessarily

Classify these as:

```text
BLOCKER
HIGH
MEDIUM
LOW
OPTIMIZATION
```

Do not mark a theoretical scalability concern as a blocker unless it affects the required hackathon workflow.

---

# 22. Acceptance Criteria Matrix

At the end, produce a strict matrix for EVERY Backend Core acceptance criterion.

Use:

| # | Acceptance Criterion | Implementation | Test | Runtime Verification | Status | Evidence |
|---|---|---|---|---|---|---|

Statuses must be exactly one of:

```text
PASS
PARTIAL
FAIL
UNVERIFIED
NOT APPLICABLE
```

Never use “probably passes.”

---

# 23. Final Classification

Give the backend exactly one final classification.

### GREEN — ACCEPT

All contractual requirements are implemented and sufficiently verified.

### YELLOW — ACCEPT AFTER FIXES

The architecture and majority of functionality are correct, but specific issues must be fixed before merge.

### RED — REJECT

Major contractual functionality is missing, broken, or incompatible.

Do not classify the backend as GREEN merely because tests pass.

Do not classify it as RED merely because Component E stubs exist.

---

# 24. Final Report Format

Your final response must contain these sections in exactly this order:

## 1. Executive Verdict

State:

```text
Component D Status:
GREEN / YELLOW / RED

Overall Completion:
XX%

Merge Recommendation:
ACCEPT / ACCEPT AFTER FIXES / REJECT
```

Then explain the reasoning in 5–10 concise paragraphs.

## 2. Repository Inventory

Show the important files/modules actually found.

## 3. Master Prompt Compliance

Create a requirement-by-requirement matrix.

## 4. API Contract Compliance

Create the complete endpoint matrix.

## 5. Database/PostGIS Audit

## 6. Authentication & Authorization Audit

## 7. SOS Ingestion & Deduplication Audit

## 8. Incident Clustering Audit

## 9. WebSocket Audit

## 10. Dispatch & Resource Audit

## 11. Failure-Handling Audit

## 12. Test-Suite Audit

## 13. Docker / Setup / Reproducibility Audit

## 14. Security Audit

## 15. Documentation-vs-Code Audit

## 16. Contract Drift

Explicitly list every deviation from the canonical API contract.

## 17. Critical Findings

Group into:

```text
BLOCKER
HIGH
MEDIUM
LOW
```

For every finding provide:

```text
Finding:
Why it matters:
Expected behavior:
Actual behavior:
File:
Relevant function/class:
Evidence:
Recommended fix:
```

## 18. Missing Tests

List tests that should exist but do not.

## 19. Required Fixes Before Merge

Give a concise actionable checklist.

## 20. Final Acceptance Decision

End with:

```text
FINAL DECISION:
[GREEN / YELLOW / RED]

The backend [can / cannot] be merged into the integration branch.

Required actions before merge:
1.
2.
3.
...
```

---

# 25. Important Rules

Follow these rules throughout the audit:

1. **Do not modify the repository.**
2. **Do not generate replacement code unless specifically asked.**
3. **Do not trust documentation claims without evidence.**
4. **Do not assume a test passes because it exists.**
5. **Do not assume an endpoint works because a router exists.**
6. **Trace actual execution paths.**
7. **Use the Master Prompt and Day 1 contract as the specification.**
8. **Do not silently resolve specification conflicts. Report them.**
9. **Clearly distinguish verified, inferred, and unverified findings.**
10. **Do not penalize Component D for functionality explicitly owned by Component E.**
11. **Do not declare the project complete merely because the code compiles.**
12. **Do not make unrelated architectural recommendations.**
13. **Do not hide defects because they are “acceptable for a hackathon.”**
14. **At the same time, do not impose unnecessary enterprise-grade requirements that aren’t in the specification.**
15. **If an external dependency prevents runtime verification, explicitly say so.**
16. **Never expose actual secrets or credentials found during the audit.**
17. **Do not change API contracts to accommodate the implementation.**
18. **If code and documentation disagree, report the disagreement.**
19. **If the specification itself contains a contradiction, identify the contradiction rather than silently choosing one interpretation.**
20. **The goal is acceptance testing, not encouragement of the developer.**

---

# 26. Start the Audit

Begin by inspecting the entire repository and all specified project-context documents.

Do not make changes.

Do not give a preliminary “looks good” assessment.

Perform the complete audit first and then produce the final acceptance report using the exact format above.
