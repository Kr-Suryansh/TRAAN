import logging
import math
from typing import List, Dict, Any, Optional
from ortools.sat.python import cp_model
from app.models.schemas import Incident, Resource, SeverityEnum, ResourceStatus, RecommendedResource

logger = logging.getLogger(__name__)


def get_severity_weight(severity: Optional[SeverityEnum]) -> int:
    weights = {
        SeverityEnum.critical: 1000,
        SeverityEnum.high: 500,
        SeverityEnum.medium: 100,
        SeverityEnum.low: 10
    }
    return weights.get(severity, 10) if severity else 10

def haversine_distance(lat1: float, lon1: float, lat2: float, lon2: float) -> float:
    """
    Calculate the great circle distance in kilometers between two points 
    on the earth (specified in decimal degrees).
    """
    R = 6371.0 # Earth radius in kilometers
    
    dlat = math.radians(lat2 - lat1)
    dlon = math.radians(lon2 - lon1)
    
    a = math.sin(dlat / 2)**2 + math.cos(math.radians(lat1)) * math.cos(math.radians(lat2)) * math.sin(dlon / 2)**2
    c = 2 * math.atan2(math.sqrt(a), math.sqrt(1 - a))
    
    return R * c

def estimate_demand(incident: Incident) -> Dict[str, int]:
    """
    Heuristic to estimate how many resources of each category an incident needs.
    Based on severity and estimated people affected.
    
    Note: These demand formulas (e.g. shelter = ceil(people / 20), transport = ceil(people / 10)) 
    are project heuristics used for demo optimization and are not official IDRN/NDMA allocation rules.
    """
    demand = {
        "medical": 0,
        "rescue": 0,
        "shelter": 0,
        "transport": 0,
        "communication": 1 # Baseline for any incident
    }
    
    if incident.severity == SeverityEnum.critical:
        demand["medical"] += 3
        demand["rescue"] += 2
    elif incident.severity == SeverityEnum.high:
        demand["medical"] += 2
        demand["rescue"] += 1
    elif incident.severity == SeverityEnum.medium:
        demand["medical"] += 1
        
    if incident.flags.medical_emergency:
        demand["medical"] += 2
    if incident.flags.trapped or incident.flags.structural_damage:
        demand["rescue"] += 2
        
    people = incident.estimated_people_affected
    if people > 0:
        demand["shelter"] += math.ceil(people / 20)
        demand["transport"] += math.ceil(people / 10)
        
    return demand

def optimize_allocations(
    incidents: List[Incident], 
    resources: List[Resource],
    constraints: Optional[Dict[str, Any]] = None
) -> List[Dict[str, Any]]:
    """
    CP-SAT solver allocating resources to active incidents based on severity prioritization and suitability.
    Never recommends more of a resource than `quantity_available`.
    Considers geographic distance and user-provided optimization constraints.
    Eligible resource statuses: `available` and `partially_deployed` (with positive quantity_available).
    """
    # Parse and validate constraints dictionary before solver execution
    constraints = constraints or {}
    max_dist = constraints.get("maximum_distance")
    req_cats = constraints.get("required_resource_categories")
    excl_ids = set(constraints.get("excluded_resource_ids") or [])
    max_alloc = constraints.get("maximum_allocation")

    if max_dist is not None:
        if not isinstance(max_dist, (int, float)) or max_dist < 0:
            raise ValueError(f"maximum_distance constraint must be a non-negative number, got {max_dist}")
    if max_alloc is not None:
        if not isinstance(max_alloc, int) or max_alloc < 0:
            raise ValueError(f"maximum_allocation constraint must be a non-negative integer, got {max_alloc}")

    try:
        active_incidents = [i for i in incidents if i.status not in ["resolved"]]
        
        # Eligible resource status: available or partially_deployed with quantity_available > 0
        eligible_statuses = {ResourceStatus.available, ResourceStatus.partially_deployed, "available", "partially_deployed"}
        available_resources = [
            r for r in resources 
            if r.quantity_available > 0 and (
                r.status in eligible_statuses or 
                (hasattr(r.status, 'value') and r.status.value in ["available", "partially_deployed"])
            )
        ]

        # Warn on unsupported keys
        supported_keys = {"maximum_distance", "required_resource_categories", "excluded_resource_ids", "maximum_allocation"}
        unsupported = set(constraints.keys()) - supported_keys
        if unsupported:
            logger.warning(f"Optimization received unsupported constraint keys: {unsupported}. They will be ignored.")

        # Apply constraint filters on resources
        if excl_ids:
            available_resources = [r for r in available_resources if r.resource_id not in excl_ids]
            
        if req_cats:
            req_cat_strs = {c.value if hasattr(c, 'value') else str(c) for c in req_cats}
            available_resources = [
                r for r in available_resources 
                if (r.category.value if hasattr(r.category, 'value') else str(r.category)) in req_cat_strs
            ]

        if not active_incidents or not available_resources:
            return []

        model = cp_model.CpModel()
        
        # x[(i, r)] = quantity of resource r assigned to incident i
        x = {}
        
        for i_idx, incident in enumerate(active_incidents):
            for r_idx, resource in enumerate(available_resources):
                # Calculate distance
                dist_km = haversine_distance(
                    incident.location.lat, incident.location.lng,
                    resource.location.lat, resource.location.lng
                )
                
                # Check maximum distance constraint
                if max_dist is not None and dist_km > max_dist:
                    upper_bound = 0
                else:
                    upper_bound = resource.quantity_available
                    if max_alloc is not None:
                        upper_bound = min(upper_bound, max_alloc)

                x[(i_idx, r_idx)] = model.NewIntVar(0, upper_bound, f"assign_i{i_idx}_r{r_idx}")

        # Constraint 1: Do not exceed available resource quantity globally
        for r_idx, resource in enumerate(available_resources):
            cap = resource.quantity_available
            if max_alloc is not None:
                cap = min(cap, max_alloc)
            model.Add(sum(x[(i_idx, r_idx)] for i_idx in range(len(active_incidents))) <= cap)

        # Constraint 2: Do not exceed estimated demand per incident
        for i_idx, incident in enumerate(active_incidents):
            demand = estimate_demand(incident)
            for cat, qty in demand.items():
                matching_r_indices = [
                    r_idx for r_idx, r in enumerate(available_resources) 
                    if (r.category.value if hasattr(r.category, 'value') else str(r.category)) == cat
                ]
                if matching_r_indices:
                    model.Add(sum(x[(i_idx, r_idx)] for r_idx in matching_r_indices) <= qty)

        # Objective: Maximize assigned resources weighted by incident severity, minus distance penalty
        objective_terms = []
        for i_idx, incident in enumerate(active_incidents):
            weight = get_severity_weight(incident.severity)
            
            for r_idx, resource in enumerate(available_resources):
                dist_km = haversine_distance(
                    incident.location.lat, incident.location.lng,
                    resource.location.lat, resource.location.lng
                )
                
                penalty = int(min(dist_km, weight - 1))
                effective_weight = weight - penalty
                objective_terms.append(effective_weight * x[(i_idx, r_idx)])
                
        model.Maximize(sum(objective_terms))
        
        solver = cp_model.CpSolver()
        solver.parameters.max_time_in_seconds = 5.0
        status = solver.Solve(model)
        
        assignments = []
        
        if status == cp_model.OPTIMAL or status == cp_model.FEASIBLE:
            for i_idx, incident in enumerate(active_incidents):
                for r_idx, resource in enumerate(available_resources):
                    assigned_qty = solver.Value(x[(i_idx, r_idx)])
                    if assigned_qty > 0:
                        dist_km = haversine_distance(
                            incident.location.lat, incident.location.lng,
                            resource.location.lat, resource.location.lng
                        )
                        sev_str = incident.severity.value if incident.severity and hasattr(incident.severity, 'value') else (str(incident.severity) if incident.severity else 'unspecified')
                        assignments.append({
                            "incident_id": incident.incident_id,
                            "resource_id": resource.resource_id,
                            "quantity": assigned_qty,
                            "reasoning": f"Prioritized for the {sev_str} severity incident due to {resource.category.value if hasattr(resource.category, 'value') else resource.category} suitability and location (~{dist_km:.1f}km away).",
                            "agency": resource.custodian_agency,
                            "distance_km": round(dist_km, 1)
                        })
        else:
            logger.warning("OR-Tools solver could not find a feasible solution.")
            
        return assignments
    except Exception as e:
        logger.error(f"Optimization failed: {str(e)}")
        return []

