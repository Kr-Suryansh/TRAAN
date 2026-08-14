import logging
import math
from typing import List, Dict, Any
from ortools.sat.python import cp_model
from app.models.schemas import Incident, Resource, SeverityEnum, RecommendedResource

logger = logging.getLogger(__name__)

def get_severity_weight(severity: SeverityEnum) -> int:
    weights = {
        SeverityEnum.critical: 1000,
        SeverityEnum.high: 500,
        SeverityEnum.medium: 100,
        SeverityEnum.low: 10
    }
    return weights.get(severity, 10)

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

def optimize_allocations(incidents: List[Incident], resources: List[Resource], constraints: dict = None) -> List[Dict[str, Any]]:
    """
    Uses Google OR-Tools CP-SAT solver to recommend resource allocations.
    Prioritizes critical incidents.
    Never recommends more of a resource than `quantity_available`.
    Considers geographic distance.
    """
    try:
        active_incidents = [i for i in incidents if i.status not in ["resolved"]]
        available_resources = [r for r in resources if r.quantity_available > 0 and r.status == 'available']
        
        if not active_incidents or not available_resources:
            return []

        model = cp_model.CpModel()
        
        # x[(i, r)] = quantity of resource r assigned to incident i
        x = {}
        
        for i_idx, incident in enumerate(active_incidents):
            for r_idx, resource in enumerate(available_resources):
                max_qty = resource.quantity_available
                x[(i_idx, r_idx)] = model.NewIntVar(0, max_qty, f"assign_i{i_idx}_r{r_idx}")

        # Constraint 1: Do not exceed available resource quantity globally
        for r_idx, resource in enumerate(available_resources):
            model.Add(sum(x[(i_idx, r_idx)] for i_idx in range(len(active_incidents))) <= resource.quantity_available)

        # Constraint 2: Do not exceed estimated demand per incident
        for i_idx, incident in enumerate(active_incidents):
            demand = estimate_demand(incident)
            for cat, qty in demand.items():
                matching_r_indices = [r_idx for r_idx, r in enumerate(available_resources) if r.category.value == cat]
                if matching_r_indices:
                    model.Add(sum(x[(i_idx, r_idx)] for r_idx in matching_r_indices) <= qty)

        # Objective: Maximize assigned resources weighted by incident severity, minus distance penalty
        objective_terms = []
        for i_idx, incident in enumerate(active_incidents):
            weight = get_severity_weight(incident.severity)
            
            for r_idx, resource in enumerate(available_resources):
                # Calculate distance penalty
                dist_km = haversine_distance(
                    incident.location.lat, incident.location.lng,
                    resource.location.lat, resource.location.lng
                )
                
                # Penalty scales with distance. weight is generally 10-1000.
                # A distance of 10km could subtract ~10 points from the weight.
                # Integer arithmetic needed for OR-Tools objective terms, so we round it.
                penalty = int(min(dist_km, weight - 1)) # Ensure penalty doesn't outweigh priority entirely unless very far
                
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
                        assignments.append({
                            "incident_id": incident.incident_id,
                            "resource_id": resource.resource_id,
                            "quantity": assigned_qty,
                            "reasoning": f"Prioritized for the {incident.severity.value} severity incident due to {resource.category.value} suitability and location (~{dist_km:.1f}km away)."
                        })
        else:
            logger.warning("OR-Tools solver could not find a feasible solution.")
            
        return assignments
    except Exception as e:
        logger.error(f"Optimization failed: {str(e)}")
        return []
