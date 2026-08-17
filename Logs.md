# Logs.md

## 2026-08-13 (Day 1 - Component E)
- Project initialized. Created the backend directory structure for the AI and Optimizer services.
- Added `Agent.md`, `Logs.md`, `ApiEndpoints.md` as requested.
- Stubs matching Day 1 Contracts created in `app/models/schemas.py`.

## 2026-08-13 (Day 2 & Day 3 - Component E)
- **Investigation**: Inspected existing `seed.py` and `summarizer.py`.
- **Resource Data Collection**: Selected "Dehradun" district for the hackathon demo. Extracted genuine numbers from IDRN public query and Dehradun DDMP 2023. Created `data.json` with clear provenance mapping and estimate rationale.
- **Seed Validation**: Modified `seed.py` to parse `data.json` and validate quantity constraints (>= 0), geographic data, and Enums before seeding.
- **Gemini Summarization**: Updated `generate_incident_summary` to explicitly merge multiple reports, using structured output schema with `gemini-3.6-flash`.
- **Testing**: Ran `manual_gemini_test.py` proving that 3 similar flood SOS reports reliably generate 1 cohesive incident summary with exact enums (`severity="high"`), correctly counting 1 unique affected person instead of 3.
- **Documentation**: Updated `Logs.md`, `Agent.md`, and ensured `ApiEndpoints.md` wasn't altered. Created `.env.example`.

## 2026-08-14 (Day 4 - Component E)
- **Implementation Start**: Implemented the first working OR-Tools optimizer in `app/services/optimizer/allocator.py`.
- **Optimization Approach Chosen**: 
  - Modeled resource allocation as a constraint satisfaction and maximization problem using Google OR-Tools (CP-SAT solver).
  - Designed an objective function to prioritize critical incidents by severity weight, while mathematically subtracting geographic distance penalty to favor closer resources.
  - Implemented constraints so total assigned quantity never exceeds `quantity_available` globally, nor exceeds the incident demand.
  - Handled exceptions safely returning empty assignments `[]`.
- **Mock Data & Validation**: 
  - Implemented `demo_optimizer.py` utilizing mock incidents (critical and high) competing for a limited set of resources, verifying behavior manually.
  - Test results: `tests/test_optimizer.py` fully constructed covering 8 detailed scenarios including priority handling, capability limitations, missing items, and capacity overrides.
- **Problems Encountered**: Python 3.14 lacks pre-built `pydantic-core` wheels for Windows, blocking automated CI testing in the agent environment. The logic was verified locally.
- **Deviations**: None. Adhered strictly to canonical schemas. The API endpoint (`POST /api/v1/optimize/allocate`) was deliberately left out for Day 5/6 backend integration as requested.

## 2026-08-15 (Day 5 - Component E)
- **Implementation Start**: Real Gemini integration for incident analysis in `app/services/ai/summarizer.py`.
- **Validation**: Enforced strict validation and constraints using Pydantic, restricting output schema strictly to avoid malformed inputs such as negative population, unlisted enums, and missing required properties.
- **Resilience**: Established error barriers around JSON parsing, Pydantic `ValidationError`s, and connection timeouts, substituting deterministic and mathematically derived fallback schemas to guarantee data persistence.
- **Testing Expansion**: Augmented `tests/test_ai_summarizer.py` covering invalid severity, incorrect types, empty responses, partial schemas, timeouts, negative affected numbers, and general Pydantic structural mismatches. Added an integration-level `@pytest.mark.skipif` test to test via a genuine Google-issued GEMINI API key when one is provided.
- **Environment Loading Fix**: Addressed a failure where the real Gemini test was incorrectly skipped because `pytest` did not automatically load `.env` variables at test collection time. Created `tests/conftest.py` to seamlessly execute `dotenv.load_dotenv()` ensuring environment consistency without modifying individual test files or coupling tests to global logic.
- **Gemini Schema Compatibility**: Discovered and resolved a `google-genai` serialization issue where Pydantic `Field` bounds (like `max_length=1000` or `ge=0`) inside `IncidentSummaryOutput` caused generation of invalid JSON schemas that the Gemini API rejected (raising `ValidationError`s). Restored basic typing to the Pydantic schema and shifted bounds validation manually into Python logic post-instantiation.
- **Deviations**: Maintained the API contract without any deviations. No image/voice capability was implemented to abide by the textual restriction. OR-Tools optimizer left functionally completely unaltered as instructed.

## 2026-08-15 (Day 6 - Component E)
- **Implementation Start**: Integrated the existing Day 4 OR-Tools resource optimizer into the incident creation and update lifecycle in `app/services/optimizer/incident_service.py`.
- **Integration Point**: `create_incident(incident, resources)` persists the incident first, fetches available resources (filtered for `quantity_available > 0` and `status == available`), invokes the OR-Tools solver globally, maps assignments into `RecommendedResource` objects, and attaches them to `Incident.recommended_resources`.
- **Automatic Trigger**: Automatic optimization runs upon incident creation and upon meaningful updates (changes to `severity`, `estimated_people_affected`, `location`, `status`, or `flags`).
- **Resilience & Safety**: Optimizer errors are caught gracefully, logging the failure while keeping the incident intact with empty recommendations. Resource `quantity_available` is unmutated by recommendations, and no automatic dispatch or status modification occurs.
- **On-Demand Path**: Maintained `run_on_demand_optimization()` supporting optional `incident_ids` and `constraints` for explicit `POST /api/v1/optimize/allocate` re-solves.
- **Resource Registry**: Added `app/services/optimizer/resource_registry.py` for fetching and filtering mock IDRN-style resources.
- **Schema Traceability**: Added optional `resource_id` field to `RecommendedResource` in `app/models/schemas.py` for complete resource assignment traceability.
- **Testing**: Added 9 comprehensive integration tests in `tests/test_incident_integration.py` verifying automatic recommendation, empty resource fallback, global capacity limits, priority enforcement, failure safety, unmutated resource states, and on-demand paths. All 25 tests in the test suite pass (8 Day 4 optimizer, 8 Day 5 Gemini AI, 9 Day 6 integration).
- **Deviations from initial plan**: None.

## 2026-08-15 (Day 7 - Component E)
- **Implementation Start**: Implemented the overall disaster response situation brief synthesis, caching, and periodic refresh in `app/services/ai/situation_brief.py`.
- **Data Aggregation**: Built `_build_situation_prompt()` to aggregate operational state across all active unresolved incidents (severity counts, total estimated affected people, emergency flags for medical/trapped/vulnerable/structural), resource inventory availability, and Day 6 OR-Tools resource recommendations.
- **Gemini Synthesis & Prompt Hardening**: Used `gemini-3.6-flash` to synthesize one skimmable, professional paragraph (3-5 sentences). Incorporated explicit prompt instructions treating user SOS text as untrusted external inputs (prompt injection defense) and enforcing human-in-the-loop rules (recommendations != dispatch).
- **Caching & Caching Strategy**: Implemented `SituationBriefCache` with thread-safe `_CACHE_LOCK`. Exposed `get_cached_situation_brief()` (matching `GET /api/v1/situation-brief`) and `refresh_situation_brief()` (matching `POST /api/v1/situation-brief/refresh`).
- **Resilience & Fallback**: Handled 0 active incidents with a deterministic stable message. If Gemini API is offline or returns invalid output (raw JSON / prompt leaks), preserves the previous valid cached brief if present, or formulates a safe deterministic fallback summary.
- **Background Refresh**: Implemented `start_periodic_refresh(interval_seconds=300)` using a non-blocking daemon worker thread that handles all exceptions so app startup never crashes.
- **Testing**: Added 12 comprehensive unit and integration tests in `tests/test_situation_brief.py` covering aggregation, prompt contents, empty states, Gemini failure with/without cache, invalid output rejection, untrusted input protection, background refresh thread, zero side-effects on resource states, and real Gemini API execution.
- **Full Test Suite Verification**: All 37 tests across the entire test suite pass cleanly (8 Day 4 optimizer, 8 Day 5 Gemini AI, 9 Day 6 incident integration, 12 Day 7 situation brief).
- **Deviations from initial plan**: None.

## 2026-08-17 (Day 8 — Component E Post-Audit P0 Remediation & Test Hardening)

### P0 Remediation Overview
Following an independent red-team audit, two P0 blockers were identified and remediated:

1. **P0-1 Gemini Failure Safety (Null Severity Handling)**
   - **Problem**: When Gemini failed, `_get_fallback_summary` correctly returned `severity = None`. However, the Pydantic schema typed `severity` as non-nullable `SeverityEnum` defaulting to `medium`, and `IncidentModel.to_pydantic()` previously coerced DB `NULL` values to `SeverityEnum.medium`, silently fabricating a medium severity.
   - **Fix**: Updated `Incident.severity` in `app/models/schemas.py` to `Optional[SeverityEnum] = None`. Updated `IncidentModel.to_pydantic()` in `app/db/models.py` to preserve `NULL` as `None` (`sev = SeverityEnum(self.severity) if self.severity else None`).
   - **CASE B Preservation Rule**: Updated `update_incident()` in `app/services/optimizer/incident_service.py` to preserve an existing valid severity (e.g. `SeverityEnum.high`) if a subsequent update returns `severity=None` due to a Gemini failure.

2. **P0-4 Incident Database Integration (Single Source of Truth)**
   - **Problem**: Production incident reads previously loaded from an in-memory `INCIDENT_STORE` dictionary instead of PostgreSQL/SQLite. Upon application restart, `INCIDENT_STORE` was empty, causing the optimizer and situation brief to see zero active incidents despite DB records existing.
   - **Fix**: Refactored `get_active_incidents()`, `get_incident_by_id()`, `create_incident()`, `update_incident()`, `run_automatic_optimization_for_store()`, and `run_on_demand_optimization()` in `app/services/optimizer/incident_service.py` to query and persist active incidents directly using database sessions (`IncidentModel` table).
   - **Situation Brief Integration**: Refactored `refresh_situation_brief()` in `app/services/ai/situation_brief.py` to fetch active incidents directly from the database using `get_active_incidents(db=db)`.
   - **INCIDENT_STORE Audit**: Removed `INCIDENT_STORE` from production runtime execution paths. `INCIDENT_STORE` is retained only as a secondary sync object for legacy unit-test compatibility.

### Optimizer Constraints & Resource Eligibility (P1 Features Verified)
- All four solver constraints (`excluded_resource_ids`, `required_resource_categories`, `maximum_distance`, `maximum_allocation`) are enforced in `allocator.py`.
- Resource eligibility includes `status in {available, partially_deployed}` with `quantity_available > 0`. `deployed` and `maintenance` remain excluded.
- Zero automatic dispatch: recommendations attach to `Incident.recommended_resources` without mutating `quantity_available` or creating dispatch records.

### Test Suite Results
- **48 Passed, 1 Skipped, 0 Failed** (`pytest -v`, Python 3.11.9).
- Added 6 targeted P0 remediation tests in `tests/test_p0_remediation.py` verifying DB NULL severity round-trip, Gemini failure persistence, CASE B severity preservation, and process restart recovery for DB persistence, optimizer, and situation brief.
- Skipped test: `test_real_gemini_situation_brief_api` skipped due to external Gemini API returning HTTP 503 UNAVAILABLE (`This model is currently experiencing high demand`). Production fallback behavior (`is_fallback=True`, valid deterministic fallback text) was verified independently.

### Remaining Issue (P3)
- **Demand Estimation Formulas**: Resource demand formulas in `allocator.py` (e.g. `shelter = ceil(people / 20)`) are project/demo heuristics used for optimization demonstration, not official NDMA/IDRN allocation rules.

### Final Status: ✅ READY FOR INTEGRATION
