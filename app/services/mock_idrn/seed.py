import asyncio
import uuid
from datetime import datetime
from app.db.engine import SessionLocal
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
        for r_data in MOCK_RESOURCES:
            db_res = Resource(
                resource_id=str(uuid.uuid4()),
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
        print("Mock IDRN resources seeded successfully!")

if __name__ == "__main__":
    asyncio.run(seed())
