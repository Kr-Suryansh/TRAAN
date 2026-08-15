# Agent.md
**Component**: AI Processing & Resource Recommendation Services (Backend)

## Project Context
Building the backend modules for a disaster response coordination platform. The components implemented here are:
1. `app/services/ai`: Gemini API integration for generating cluster summaries and situation briefs.
2. `app/services/optimizer`: Google OR-Tools integration for resource allocation.
3. `app/services/mock_idrn`: Mock resource registry based on IDRN with a seed script.

## Current Implementation State (Day 6 Complete)
- **Automatic Resource Recommendation Integration**: Integrated `app/services/optimizer/incident_service.py` into the incident creation and update lifecycle. Whenever an incident is created or meaningfully updated, the existing Day 4 OR-Tools solver (`allocator.py`) is automatically triggered, mapping assignments into `Incident.recommended_resources`.
- **OR-Tools Optimizer**: A standalone allocation optimizer is functional in `app/services/optimizer/allocator.py`. It assigns resources matching requested categories by prioritizing high-severity incidents, mathematically constraining against the `quantity_available` pool. It uses Haversine distance heuristics to penalize allocating remote resources. Tests are defined covering constraints, priorities, missing resources, and capability subsets.
- **Mock IDRN Registry**: The seeder (`seed.py`) and registry helper (`resource_registry.py`) pull from a structured `data.json` containing real, traceable resource numbers for Dehradun district, sourced from the IDRN Public Query and Dehradun DDMP. It filters resources for `quantity_available > 0` and `status == available`.
- **Gemini Incident Summarization**: The integration utilizes `google-genai` and `gemini-3.6-flash` within `app/services/ai/summarizer.py` to synthesize robust incident summaries from collective SOS reports. It validates against `IncidentSummaryOutput` strict schemas (Pydantic models) to reject negative populations or unlisted enums. Fallbacks are integrated to deterministically formulate summaries when API connections timeout or structural output from Gemini is missing, guaranteeing incident progression.
- **On-Demand & Human-in-the-Loop Safeguards**: Recommendations never mutate `quantity_available`, never auto-dispatch resources, and never alter resource statuses. The on-demand endpoint logic (`run_on_demand_optimization`) remains fully supported for manual re-solves via `POST /api/v1/optimize/allocate`.

## Day 7+ Integration Requirements
- Backend Core can directly import `create_incident` and `update_incident` from `app.services.optimizer.incident_service` when processing incoming SOS report clusters.
- The `POST /api/v1/optimize/allocate` HTTP router handler will delegate to `run_on_demand_optimization()`.

## Deviations and Notes
- Created stubs in `app/models/schemas.py` since the full FastAPI/SQLAlchemy application was not present. These stubs should be replaced with actual SQLAlchemy models when integrated into the larger `sih-backend` repository.
- Mock IDRN coordinates are generalized to the district level in some cases since exact point locations weren't always available in the DDMP.
- Provenance is kept alongside the data in `data.json` rather than breaking the canonical `Resource` contract.
- Implementation deliberately bypassed implementing endpoint dispatch logic to maintain separation of concerns.
