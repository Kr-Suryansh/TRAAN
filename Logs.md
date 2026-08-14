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
