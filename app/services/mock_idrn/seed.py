"""
seed.py
Script to populate the local mock IDRN database via the DB session.
Source for numbers: IDRN public query tool and District Disaster Management Plans.
DO NOT use this via API. Run with: `python -m app.services.mock_idrn.seed`
"""
import logging
import json
import os
from datetime import datetime
from typing import List, Dict, Any, Optional
from sqlalchemy.orm import Session

from app.models.schemas import Resource, ResourceCategory, ResourceStatus, Location
from app.db.database import init_db, SessionLocal
from app.db.models import ResourceModel

# Set up simple logging for the script
logging.basicConfig(level=logging.INFO, format="%(levelname)s: %(message)s")
logger = logging.getLogger(__name__)

# Sample Data sourced from a typical District Disaster Management Plan (e.g. Dehradun/Uttarkashi)
DEMO_DISTRICT = "Dehradun"

def load_json_dataset() -> List[Dict[str, Any]]:
    # Load from the structured dataset
    current_dir = os.path.dirname(os.path.abspath(__file__))
    json_path = os.path.join(current_dir, "data.json")
    with open(json_path, "r", encoding="utf-8") as f:
        return json.load(f)

def validate_resource_data(data: Dict[str, Any]):
    """
    Validate data constraints strictly before attempting DB insertion.
    Raises ValueError if validation fails.
    """
    # Required fields
    required = [
        "resource_id", "category", "sub_type", "custodian_agency", 
        "quantity_total", "quantity_available", "status", "location", 
        "contact", "last_updated_at"
    ]
    for r in required:
        if r not in data:
            raise ValueError(f"Missing required field: {r}")
    
    # Quantity rules
    q_tot = data["quantity_total"]
    q_avail = data["quantity_available"]
    if q_tot < 0:
        raise ValueError("quantity_total must be >= 0")
    if q_avail < 0:
        raise ValueError("quantity_available must be >= 0")
    if q_avail > q_tot:
        raise ValueError("quantity_available cannot exceed quantity_total")
        
    # Geographic rules
    loc = data["location"]
    if not isinstance(loc.get("lat"), (float, int)) or not isinstance(loc.get("lng"), (float, int)):
        raise ValueError("Latitude and longitude must be valid numbers")
    if not loc.get("district"):
        raise ValueError("District is required")
        
    if data["category"] not in ["medical", "rescue", "shelter", "transport", "communication"]:
        raise ValueError(f"Invalid category: {data['category']}")
    if data["status"] not in ["available", "partially_deployed", "deployed", "maintenance"]:
        raise ValueError(f"Invalid status: {data['status']}")

def seed_database(db: Optional[Session] = None) -> List[Resource]:
    """
    Uses SQLAlchemy Session to upsert records into the PostgreSQL/SQLite Resource table.
    Can be run standalone or passed an existing session.
    """
    logger.info(f"Starting database seed process for mock IDRN registry (District: {DEMO_DISTRICT})...")
    
    # Ensure database schema is initialized
    init_db()
    
    close_db_on_exit = False
    if db is None:
        db = SessionLocal()
        close_db_on_exit = True

    try:
        raw_data = load_json_dataset()
        seeded_resources: List[Resource] = []
        
        for item in raw_data:
            validate_resource_data(item)
            
            loc = Location(
                lat=item["location"]["lat"],
                lng=item["location"]["lng"],
                district=item["location"]["district"]
            )
            
            resource = Resource(
                resource_id=item["resource_id"],
                category=ResourceCategory(item["category"]),
                sub_type=item["sub_type"],
                custodian_agency=item["custodian_agency"],
                quantity_total=item["quantity_total"],
                quantity_available=item["quantity_available"],
                status=ResourceStatus(item["status"]),
                location=loc,
                contact=item["contact"],
                last_updated_at=datetime.fromisoformat(item["last_updated_at"].replace("Z", "+00:00"))
            )

            # Idempotent upsert into database table
            existing_model = db.query(ResourceModel).filter(ResourceModel.resource_id == resource.resource_id).first()
            if existing_model:
                existing_model.category = resource.category.value
                existing_model.sub_type = resource.sub_type
                existing_model.custodian_agency = resource.custodian_agency
                existing_model.quantity_total = resource.quantity_total
                existing_model.quantity_available = resource.quantity_available
                existing_model.status = resource.status.value
                existing_model.lat = resource.location.lat
                existing_model.lng = resource.location.lng
                existing_model.district = resource.location.district
                existing_model.contact = resource.contact
                existing_model.last_updated_at = resource.last_updated_at
            else:
                db.add(ResourceModel.from_pydantic(resource))
                
            seeded_resources.append(resource)
            
            provenance = item.get("provenance", {})
            logger.info(f"Persisted to DB: {resource.quantity_total}x {resource.sub_type} (Agency: {resource.custodian_agency}) [Source: {provenance.get('source_type', 'Unknown')}]")
        
        db.commit()
        logger.info(f"Successfully seeded {len(seeded_resources)} resource records into database table.")
        return seeded_resources

    except Exception as e:
        db.rollback()
        logger.error(f"Failed to seed resource dataset: {e}")
        raise
    finally:
        if close_db_on_exit:
            db.close()

if __name__ == "__main__":
    seed_database()

