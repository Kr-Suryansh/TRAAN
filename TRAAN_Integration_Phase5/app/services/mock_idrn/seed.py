import asyncio
import uuid
from datetime import datetime
import json
import os
from app.db.engine import AsyncSessionLocal as SessionLocal
from app.db._models.resource import Resource

MOCK_RESOURCES = [
    {
        "category": "medical",
        "sub_type": "ambulance",
        "custodian_agency": "SDRF Unit 3",
        "quantity_total": 5,
        "quantity_available": 5,
        "status": "available",
        "contact": "+91-9999999991",
        "district": "Pune",
        "lat": 18.5204,
        "lng": 73.8567,
    },
    {
        "category": "rescue",
        "sub_type": "JCB",
        "custodian_agency": "NDRF Battalion 5",
        "quantity_total": 2,
        "quantity_available": 2,
        "status": "available",
        "contact": "+91-9999999992",
        "district": "Pune",
        "lat": 18.5244,
        "lng": 73.8667,
    },
]

async def seed():
    async with SessionLocal() as db:
        # Use a fixed namespace for deterministic UUID generation
        NAMESPACE_TRAAN = uuid.uuid5(uuid.NAMESPACE_DNS, "traan.internal")
        
        
        # Load from data.json if available
        current_dir = os.path.dirname(os.path.abspath(__file__))
        json_path = os.path.join(current_dir, "data.json")
        resources_to_seed = MOCK_RESOURCES.copy()
        
        if os.path.exists(json_path):
            try:
                with open(json_path, "r", encoding="utf-8") as f:
                    dehradun_data = json.load(f)
                
                # Transform to match D's flat dict format expected below
                for item in dehradun_data:
                    resources_to_seed.append({
                        "resource_id": item.get("resource_id"),
                        "category": item.get("category"),
                        "sub_type": item.get("sub_type"),
                        "custodian_agency": item.get("custodian_agency"),
                        "quantity_total": item.get("quantity_total"),
                        "quantity_available": item.get("quantity_available"),
                        "status": item.get("status"),
                        "contact": item.get("contact"),
                        "district": item.get("location", {}).get("district", "Dehradun"),
                        "lat": item.get("location", {}).get("lat", 0.0),
                        "lng": item.get("location", {}).get("lng", 0.0),
                    })
                print(f"Loaded {len(dehradun_data)} resources from data.json")
            except Exception as e:
                print(f"Failed to load data.json: {e}")
                
        for r_data in resources_to_seed:
            # Generate deterministic UUID based on agency and type if resource_id not provided
            if r_data.get("resource_id"):
                res_id = r_data["resource_id"]
            else:
                unique_string = f"{r_data['custodian_agency']}-{r_data['sub_type']}-{r_data['district']}"
                res_id = str(uuid.uuid5(NAMESPACE_TRAAN, unique_string))
            
            # Check if it already exists
            from sqlalchemy import select
            existing = (await db.execute(select(Resource).where(Resource.resource_id == res_id))).scalar_one_or_none()
            if existing:
                continue
                
            db_res = Resource(
                resource_id=res_id,
                category=r_data["category"],
                sub_type=r_data["sub_type"],
                custodian_agency=r_data["custodian_agency"],
                quantity_total=r_data["quantity_total"],
                quantity_available=r_data["quantity_available"],
                status=r_data["status"],
                contact=r_data["contact"],
                district=r_data["district"],
                location=f"SRID=4326;POINT({r_data['lng']} {r_data['lat']})",
                last_updated_at=datetime.utcnow()
            )
            db.add(db_res)
        await db.commit()
        print("Mock IDRN resources seeded successfully (skipped duplicates)!")

if __name__ == "__main__":
    asyncio.run(seed())
