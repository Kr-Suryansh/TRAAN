"""
incident_service.py
Lifecycle service connecting Incident creation/update to automatic OR-Tools resource recommendation.
"""
import logging
from typing import List, Dict, Any, Optional
from datetime import datetime
from app.models.schemas import Incident, Resource, RecommendedResource
from app.services.optimizer.allocator import optimize_allocations
from app.services.optimizer.resource_registry import get_available_resources, get_all_resources

logger = logging.getLogger(__name__)

# In-memory incident storage for Component E service layer
INCIDENT_STORE: Dict[str, Incident] = {}

def clear_incident_store():
    """Utility function to reset store during testing."""
    INCIDENT_STORE.clear()

def create_incident(incident: Incident, available_resources: Optional[List[Resource]] = None) -> Incident:
    """
    Creates and persists a new incident, then automatically triggers OR-Tools 
    resource recommendation and attaches the resulting recommendations to Incident.recommended_resources.
    
    Guarantees:
    - Incident is persisted first.
    - Failure in optimization does NOT delete or roll back the incident.
    - Resource quantity_available is NEVER mutated by recommendations.
    - No automatic dispatch is created.
    """
    # 1. Save incident first
    INCIDENT_STORE[incident.incident_id] = incident
    logger.info(f"Incident {incident.incident_id} created and persisted.")

    # 2. Trigger automatic baseline resource optimization
    run_automatic_optimization_for_store(available_resources)

    # Return the updated incident from store
    return INCIDENT_STORE[incident.incident_id]

def update_incident(incident_id: str, updates: Dict[str, Any], available_resources: Optional[List[Resource]] = None) -> Optional[Incident]:
    """
    Updates an existing incident. If meaningful fields (severity, people_count, location, status) change,
    automatically re-triggers resource recommendation.
    """
    if incident_id not in INCIDENT_STORE:
        logger.warning(f"Incident {incident_id} not found for update.")
        return None

    incident = INCIDENT_STORE[incident_id]
    meaningful_keys = {"severity", "estimated_people_affected", "location", "status", "flags"}
    is_meaningful_update = any(k in updates for k in meaningful_keys)

    # Apply updates
    updated_data = incident.model_dump()
    updated_data.update(updates)
    updated_data["last_updated_at"] = datetime.utcnow()
    
    updated_incident = Incident(**updated_data)
    INCIDENT_STORE[incident_id] = updated_incident
    logger.info(f"Incident {incident_id} updated.")

    if is_meaningful_update:
        run_automatic_optimization_for_store(available_resources)

    return INCIDENT_STORE[incident_id]

def run_automatic_optimization_for_store(available_resources: Optional[List[Resource]] = None):
    """
    Executes OR-Tools optimization across all active incidents in the store
    and updates their recommended_resources fields.
    """
    active_incidents = [inc for inc in INCIDENT_STORE.values() if inc.status not in ["resolved"]]
    
    if not active_incidents:
        return

    if available_resources is None:
        try:
            available_resources = get_available_resources()
        except Exception as e:
            logger.error(f"Failed to fetch resources for automatic optimization: {e}")
            available_resources = []

    # Filter resources to strictly available
    valid_resources = [r for r in available_resources if r.quantity_available > 0 and r.status.value == "available"]

    try:
        logger.info(f"Starting automatic resource recommendation for {len(active_incidents)} active incident(s).")
        assignments = optimize_allocations(active_incidents, valid_resources)
        
        # Group assignments by incident_id
        incident_assignments: Dict[str, List[RecommendedResource]] = {inc.incident_id: [] for inc in active_incidents}
        
        # Build resource lookup table to get sub_type/category
        resource_map = {r.resource_id: r for r in valid_resources}

        for assign in assignments:
            inc_id = assign["incident_id"]
            res_id = assign["resource_id"]
            qty = assign["quantity"]
            reasoning = assign["reasoning"]

            res_obj = resource_map.get(res_id)
            res_type = res_obj.sub_type if res_obj else "unknown_resource"

            rec = RecommendedResource(
                resource_id=res_id,
                resource_type=res_type,
                quantity=qty,
                reasoning=reasoning
            )

            if inc_id in incident_assignments:
                incident_assignments[inc_id].append(rec)

        # Update store with recommendations
        for inc_id, recs in incident_assignments.items():
            if inc_id in INCIDENT_STORE:
                INCIDENT_STORE[inc_id].recommended_resources = recs
                logger.info(f"Incident {inc_id} automatic recommendation updated with {len(recs)} resource type(s).")

    except Exception as e:
        logger.error(f"Automatic resource optimization failed: {e}. Preserving incident data without recommendations.")
        # Note: Do not throw exception, preserve incident safely

def run_on_demand_optimization(
    incident_ids: Optional[List[str]] = None,
    constraints: Optional[dict] = None,
    resources: Optional[List[Resource]] = None
) -> List[Dict[str, Any]]:
    """
    On-demand full re-solve endpoint helper matching POST /api/v1/optimize/allocate.
    If incident_ids provided, considers only those incidents; otherwise considers all unresolved incidents in store.
    """
    if resources is None:
        resources = get_available_resources()

    if incident_ids:
        target_incidents = [INCIDENT_STORE[i_id] for i_id in incident_ids if i_id in INCIDENT_STORE]
    else:
        target_incidents = [inc for inc in INCIDENT_STORE.values() if inc.status not in ["resolved"]]

    valid_resources = [r for r in resources if r.quantity_available > 0 and r.status.value == "available"]

    return optimize_allocations(target_incidents, valid_resources, constraints=constraints)
