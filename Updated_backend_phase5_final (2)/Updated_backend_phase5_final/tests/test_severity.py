import pytest
import datetime
import uuid
from sqlalchemy import select
from httpx import AsyncClient

from app.db.engine import AsyncSessionLocal
from app.db._models.incident import Incident
from tests.test_sos import make_batch_payload, make_sos_item, device_token

pytestmark = pytest.mark.asyncio

async def test_severity_simple_medical_not_critical(client: AsyncClient, device_token: str, cleanup_sos_data, cleanup_phase4_data):
    # 1. Simple medical request with 1 affected person -> should NOT automatically become critical, even if hint is critical
    item = make_sos_item(lat=10.0, lng=20.0)
    item["emergency_type"] = "medical"
    item["people_count"] = 1
    item["severity_hint"] = "critical" # hint is critical
    
    payload = make_batch_payload([item])
    headers = {"Authorization": f"Bearer {device_token}"}
    
    resp = await client.post("/api/v1/sos/batch", json=payload, headers=headers)
    assert resp.status_code == 202
    
    async with AsyncSessionLocal() as db:
        res = await db.execute(select(Incident))
        incident = res.scalars().first()
        assert incident is not None
        assert incident.severity == "high" # Capped at high because objective facts (1 person, medical) don't justify critical on their own

async def test_severity_structural_collapse_trapped_critical(client: AsyncClient, device_token: str, cleanup_sos_data, cleanup_phase4_data):
    # 3. Structural collapse with trapped people -> should become critical
    item = make_sos_item(lat=10.1, lng=20.1)
    item["emergency_type"] = "structural_collapse"
    item["people_count"] = 2
    item["severity_hint"] = "medium" # hint is only medium
    
    payload = make_batch_payload([item])
    headers = {"Authorization": f"Bearer {device_token}"}
    
    resp = await client.post("/api/v1/sos/batch", json=payload, headers=headers)
    assert resp.status_code == 202
    
    async with AsyncSessionLocal() as db:
        res = await db.execute(select(Incident).where(Incident.severity == "high"))
        incident = res.scalars().first()
        assert incident is not None
        
    item1 = make_sos_item(lat=10.2, lng=20.2)
    item1["emergency_type"] = "structural_collapse"
    
    item2 = make_sos_item(lat=10.2, lng=20.2)
    item2["emergency_type"] = "trapped"
    dt2 = datetime.datetime.now(datetime.timezone.utc) + datetime.timedelta(minutes=20)
    item2["created_at"] = dt2.isoformat()
    
    payload = make_batch_payload([item1, item2])
    resp = await client.post("/api/v1/sos/batch", json=payload, headers=headers)
    assert resp.status_code == 202
    
    async with AsyncSessionLocal() as db:
        res = await db.execute(select(Incident))
        all_incidents = res.scalars().all()
        for i in all_incidents:
            print(f"Incident: {i.incident_id}, severity: {i.severity}, source_sos: {i.source_sos_uuids}")
            
        res = await db.execute(select(Incident).where(Incident.severity == "critical"))
        incidents = res.scalars().all()
        assert len(incidents) > 0
