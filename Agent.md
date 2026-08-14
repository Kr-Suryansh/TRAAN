# Agent.md
**Component**: AI Processing & Resource Recommendation Services (Backend)

## Project Context
Building the backend modules for a disaster response coordination platform. The components implemented here are:
1. `app/services/ai`: Gemini API integration for generating cluster summaries and situation briefs.
2. `app/services/optimizer`: Google OR-Tools integration for resource allocation.
3. `app/services/mock_idrn`: Mock resource registry based on IDRN with a seed script.

## Current Implementation State (Day 4 Complete)
- **OR-Tools Optimizer**: A standalone allocation optimizer is functional in `app/services/optimizer/allocator.py`. It assigns resources matching requested categories by prioritizing high-severity incidents, mathematically constraining against the `quantity_available` pool. It uses Haversine distance heuristics to penalize allocating remote resources. Tests are defined covering constraints, priorities, missing resources, and capability subsets.
- **Mock IDRN Registry**: The seed script (`seed.py`) pulls from a structured `data.json` containing real, traceable resource numbers for Dehradun district, sourced from the IDRN Public Query and Dehradun DDMP. It validates data before simulated insertion.
- **Gemini Incident Summarization**: The prompt synthesis strategy correctly takes a cluster of identical-event reports and merges them into one JSON schema using `gemini-3.6-flash`, strictly typing the severity enum and boolean flags.

## Day 5/6 Integration Requirements
- The optimizer exposes `optimize_allocations(incidents, resources)`, but it needs to be wired to automatically trigger upon incident creation in Backend Core.
- The `POST /api/v1/optimize/allocate` endpoint needs to be implemented to allow for forced re-solve capabilities.
- A mapper will be needed to transition the optimizer's native assignment payload into the canonical Incident schema's `recommended_resources` representation for persistence.

## Deviations and Notes
- Created stubs in `app/models/schemas.py` since the full FastAPI/SQLAlchemy application was not present. These stubs should be replaced with actual SQLAlchemy models when integrated into the larger `sih-backend` repository.
- Mock IDRN coordinates are generalized to the district level in some cases since exact point locations weren't always available in the DDMP.
- Provenance is kept alongside the data in `data.json` rather than breaking the canonical `Resource` contract.
- Implementation deliberately bypassed implementing endpoint dispatch logic to maintain separation of concerns.
