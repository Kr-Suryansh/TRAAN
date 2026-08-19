"""
resource_registry.py
Helper module for fetching and filtering database-backed resources from the resource table.
"""
from typing import List, Optional
from sqlalchemy.orm import Session
from app.models.schemas import Resource, ResourceStatus
from app.db.database import SessionLocal, init_db
from app.db.models import ResourceModel
from app.services.mock_idrn.seed import seed_database

def get_all_resources(db: Optional[Session] = None) -> List[Resource]:
    """
    Fetch all resources from the PostgreSQL/SQLite Resource database table.
    Seeds the database if empty.
    """
    init_db()
    close_db_on_exit = False
    if db is None:
        db = SessionLocal()
        close_db_on_exit = True

    try:
        models = db.query(ResourceModel).all()
        return [m.to_pydantic() for m in models]
    finally:
        if close_db_on_exit:
            db.close()

def get_available_resources(resources: List[Resource] = None, db: Optional[Session] = None) -> List[Resource]:
    """
    Filters resources to return only those eligible for optimization:
    - status in [available, partially_deployed] (excludes deployed, maintenance)
    - quantity_available > 0
    """
    if resources is None:
        resources = get_all_resources(db=db)
        
    eligible_statuses = {ResourceStatus.available, ResourceStatus.partially_deployed, "available", "partially_deployed"}
    
    return [
        r for r in resources
        if r.quantity_available > 0 and (
            r.status in eligible_statuses or 
            (hasattr(r.status, "value") and r.status.value in ["available", "partially_deployed"])
        )
    ]

