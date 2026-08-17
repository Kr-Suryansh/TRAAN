"""
incident_service.py
Lifecycle service connecting Incident creation/update to automatic OR-Tools resource recommendation.
Database-backed via SQLAlchemy as the sole authoritative source of truth.
"""
import logging
from typing import List, Dict, Any, Optional
from datetime import datetime
from sqlalchemy.orm import Session

from app.models.schemas import Incident, Resource, RecommendedResource
from app.db.database import init_db, SessionLocal
from app.db.models import IncidentModel
from app.services.optimizer.allocator import optimize_allocations
from app.services.optimizer.resource_registry import get_available_resources

logger = logging.getLogger(__name__)

# Legacy in-memory dictionary maintained for backward-compatible test inspection
INCIDENT_STORE: Dict[str, Incident] = {}

def clear_incident_store():
    """Utility function to reset store during testing."""
    INCIDENT_STORE.clear()

def _get_db(db: Optional[Session]) -> tuple[Session, bool]:
    """Helper to initialize DB and obtain session if not provided."""
    init_db()
    if db is None:
        return SessionLocal(), True
    return db, False

def get_active_incidents(db: Optional[Session] = None) -> List[Incident]:
    """Fetch all active (non-resolved) incidents directly from the database."""
    session, close_on_exit = _get_db(db)
    try:
        models = session.query(IncidentModel).filter(IncidentModel.status != "resolved").all()
        return [m.to_pydantic() for m in models]
    finally:
        if close_on_exit:
            session.close()

def get_incident_by_id(incident_id: str, db: Optional[Session] = None) -> Optional[Incident]:
    """Fetch an incident by ID directly from the database."""
    session, close_on_exit = _get_db(db)
    try:
        model = session.query(IncidentModel).filter(IncidentModel.incident_id == incident_id).first()
        return model.to_pydantic() if model else None
    finally:
        if close_on_exit:
            session.close()

def create_incident(
    incident: Incident, 
    available_resources: Optional[List[Resource]] = None,
    db: Optional[Session] = None
) -> Incident:
    """
    Creates and persists a new incident directly into the database, then automatically 
    triggers OR-Tools resource recommendation and attaches recommendations.
    
    Guarantees:
    - Incident is persisted first to DB.
    - Failure in optimization does NOT delete or roll back the incident.
    - Resource quantity_available is NEVER mutated by recommendations.
    - No automatic dispatch is created.
    """
    session, close_on_exit = _get_db(db)
    try:
        model = IncidentModel.from_pydantic(incident)
        session.merge(model)
        session.commit()
        logger.info(f"Incident {incident.incident_id} created and persisted in DB.")
        
        # Trigger automatic baseline resource optimization against DB
        run_automatic_optimization_for_store(available_resources, db=session)
        
        # Retrieve updated incident from DB
        updated_model = session.query(IncidentModel).filter(IncidentModel.incident_id == incident.incident_id).first()
        result = updated_model.to_pydantic() if updated_model else incident
        
        # Keep INCIDENT_STORE synchronized for legacy test inspectability
        INCIDENT_STORE[result.incident_id] = result
        return result
    except Exception as e:
        session.rollback()
        logger.error(f"Failed to persist incident {incident.incident_id} to DB: {e}")
        raise
    finally:
        if close_on_exit:
            session.close()

def update_incident(
    incident_id: str, 
    updates: Dict[str, Any], 
    available_resources: Optional[List[Resource]] = None,
    db: Optional[Session] = None
) -> Optional[Incident]:
    """
    Updates an existing incident in the database.
    If meaningful fields (severity, people_count, location, status) change,
    automatically re-triggers resource recommendation.
    
    CASE B Preservation Rule:
    If an incident already has a valid severity and an update supplies severity=None (e.g. Gemini failure on refresh),
    the existing valid severity is preserved.
    """
    session, close_on_exit = _get_db(db)
    try:
        model = session.query(IncidentModel).filter(IncidentModel.incident_id == incident_id).first()
        if not model:
            logger.warning(f"Incident {incident_id} not found in DB for update.")
            return None

        existing_incident = model.to_pydantic()
        
        # CASE B Rule: Preserve existing valid severity if update sets severity to None
        if "severity" in updates and updates["severity"] is None and existing_incident.severity is not None:
            logger.info(f"Preserving existing valid severity '{existing_incident.severity}' for incident {incident_id} during update following Gemini failure.")
            updates = dict(updates)
            updates["severity"] = existing_incident.severity

        meaningful_keys = {"severity", "estimated_people_affected", "location", "status", "flags"}
        is_meaningful_update = any(k in updates for k in meaningful_keys)

        updated_data = existing_incident.model_dump()
        updated_data.update(updates)
        updated_data["last_updated_at"] = datetime.utcnow()
        
        updated_incident = Incident(**updated_data)
        updated_model = IncidentModel.from_pydantic(updated_incident)
        session.merge(updated_model)
        session.commit()

        if is_meaningful_update:
            run_automatic_optimization_for_store(available_resources, db=session)

        final_model = session.query(IncidentModel).filter(IncidentModel.incident_id == incident_id).first()
        result = final_model.to_pydantic() if final_model else updated_incident
        INCIDENT_STORE[incident_id] = result
        return result
    except Exception as e:
        session.rollback()
        logger.error(f"Failed to update incident {incident_id} in DB: {e}")
        raise
    finally:
        if close_on_exit:
            session.close()

def run_automatic_optimization_for_store(
    available_resources: Optional[List[Resource]] = None,
    db: Optional[Session] = None
):
    """
    Executes OR-Tools optimization across all active DB incidents
    and updates their recommended_resources fields in the database.
    """
    session, close_on_exit = _get_db(db)
    try:
        active_incidents = get_active_incidents(db=session)
        
        if not active_incidents:
            return

        if available_resources is None:
            try:
                available_resources = get_available_resources(db=session)
            except Exception as e:
                logger.error(f"Failed to fetch resources for automatic optimization: {e}")
                available_resources = []

        valid_resources = get_available_resources(available_resources, db=session)

        try:
            logger.info(f"Starting automatic resource recommendation for {len(active_incidents)} active DB incident(s).")
            assignments = optimize_allocations(active_incidents, valid_resources)
            
            incident_assignments: Dict[str, List[RecommendedResource]] = {inc.incident_id: [] for inc in active_incidents}
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

            for inc_id, recs in incident_assignments.items():
                inc_model = session.query(IncidentModel).filter(IncidentModel.incident_id == inc_id).first()
                if inc_model:
                    inc_model.recommended_resources = [r.model_dump() for r in recs]
                    if inc_id in INCIDENT_STORE:
                        INCIDENT_STORE[inc_id].recommended_resources = recs

            session.commit()
            logger.info("Saved automatic recommendations to database.")

        except Exception as e:
            session.rollback()
            logger.error(f"Automatic resource optimization failed: {e}. Preserving incident DB data without recommendations.")
    finally:
        if close_on_exit:
            session.close()

def run_on_demand_optimization(
    incident_ids: Optional[List[str]] = None,
    constraints: Optional[dict] = None,
    resources: Optional[List[Resource]] = None,
    db: Optional[Session] = None
) -> List[Dict[str, Any]]:
    """
    On-demand full re-solve endpoint helper matching POST /api/v1/optimize/allocate.
    Queries target active incidents directly from the database.
    """
    session, close_on_exit = _get_db(db)
    try:
        if incident_ids:
            models = session.query(IncidentModel).filter(IncidentModel.incident_id.in_(incident_ids)).all()
            target_incidents = [m.to_pydantic() for m in models]
        else:
            target_incidents = get_active_incidents(db=session)

        valid_resources = get_available_resources(resources, db=session)
        return optimize_allocations(target_incidents, valid_resources, constraints=constraints)
    finally:
        if close_on_exit:
            session.close()
