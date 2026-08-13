"""
app/services/optimizer — OR-Tools resource allocation service.

COMPONENT BOUNDARY: Implementation is owned by Component E (AI + Resource Intelligence).

Contract (do not change without team coordination):
  allocate_resources(incidents: list[dict], resources: list[dict],
                     constraints: dict | None) -> list[AllocationItem]

Where AllocationItem = {
    "incident_id": str,
    "resource_id": str,
    "quantity": int,
    "reasoning": str,
}
"""

# TODO (Component E): replace with real OR-Tools implementation
# See app/services/optimizer/ortools_solver.py when Component E is ready
