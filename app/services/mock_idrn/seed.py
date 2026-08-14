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
from typing import List, Dict, Any
from app.models.schemas import Resource, ResourceCategory, ResourceStatus, Location

# Set up simple logging for the script
logging.basicConfig(level=logging.INFO, format="%(levelname)s: %(message)s")
logger = logging.getLogger(__name__)

# Sample Data sourced from a typical District Disaster Management Plan (e.g. Dehradun/Uttarkashi)
# Mixed with estimates where gaps exist.
DEMO_DISTRICT = "Dehradun"

def load_json_dataset() -> List[Dict[str, Any]]:
    # Load from the new structured dataset
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
        
    # Enum rules validation is handled implicitly by Pydantic when instantiating Resource,
    # but we can explicitly check here if we want to be overly cautious.
    if data["category"] not in ["medical", "rescue", "shelter", "transport", "communication"]:
        raise ValueError(f"Invalid category: {data['category']}")
    if data["status"] not in ["available", "partially_deployed", "deployed", "maintenance"]:
        raise ValueError(f"Invalid status: {data['status']}")

def seed_database():
    """
    In a real implementation, this would use an SQLAlchemy Session 
    to insert records into the PostgreSQL database.
    Since we are currently building independent modules with Pydantic stubs, 
    we will generate the mock objects and simulate insertion.
    """
    logger.info(f"Starting seed process for mock IDRN registry (District: {DEMO_DISTRICT})...")
    
    raw_data = load_json_dataset()
    seeded_resources: List[Resource] = []
    
    for item in raw_data:
        try:
            validate_resource_data(item)
            
            # Extract canonical resource fields and parse into Pydantic model
            loc = Location(
                lat=item["location"]["lat"],
                lng=item["location"]["lng"],
                district=item["location"]["district"]
            )
            
            # Convert string to enum values via Pydantic instantiation
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
            seeded_resources.append(resource)
            
            # Log provenance separately without altering canonical schema
            provenance = item.get("provenance", {})
            logger.info(f"Inserted: {resource.quantity_total}x {resource.sub_type} (Agency: {resource.custodian_agency}) [Source: {provenance.get('source_type', 'Unknown')}]")
        
        except Exception as e:
            logger.error(f"Failed to seed record {item.get('resource_id', 'Unknown')}: {e}")
            raise
        
    logger.info(f"Successfully seeded {len(seeded_resources)} resource categories into the database.")
    return seeded_resources

if __name__ == "__main__":
    seed_database()
