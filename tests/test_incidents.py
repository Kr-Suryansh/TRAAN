"""Tests for Incident Endpoints"""

import pytest
from datetime import datetime, timedelta
from httpx import AsyncClient
import uuid
from app.core.security import create_authority_jwt

@pytest.fixture
def admin_token(setup_authority):
    return create_authority_jwt(setup_authority.id, "admin")

@pytest.fixture
def user_token(setup_authority):
    return create_authority_jwt(setup_authority.id, "user")

# Helper function to seed an incident and resource manually in the DB for testing
# since the API does not expose POST /api/v1/incidents
async def seed_incident_and_resource(client: AsyncClient, admin_token):
    headers = {"Authorization": f"Bearer {admin_token}"}
    
    # Create resource via API
    res_payload = {
        "category": "medical",
        "sub_type": "ambulance",
        "custodian_agency": "City Hospital",
        "quantity_total": 5,
        "quantity_available": 5,
        "status": "available",
        "contact": "1234567890",
        "location": {"lat": 12.0, "lng": 77.0, "district": "Mysuru"}
    }
    resp = await client.post("/api/v1/resources", json=res_payload, headers=headers)
    assert resp.status_code == 201
    resource_id = resp.json()["resource_id"]
    
    # Seed incident directly to db
    from app.db.engine import AsyncSessionLocal
    from app.db._models.incident import Incident
    
    incident_id = str(uuid.uuid4())
    async with AsyncSessionLocal() as db:
        inc = Incident(
            incident_id=incident_id,
            cluster_id=f"cluster_{incident_id}",
            source_sos_uuids=["uuid1", "uuid2"],
            location="SRID=4326;POINT(77.0 12.0)",
            area_name="Test Area",
            emergency_types=["medical"],
            severity="high",
            ai_summary="AI test summary",
            report_count=2,
            estimated_people_affected=10,
            flags={"medical_emergency": True, "trapped": False, "elderly_or_children": False, "structural_damage": False},
            first_reported_at=datetime.utcnow() - timedelta(minutes=10),
            last_updated_at=datetime.utcnow() - timedelta(minutes=5),
            status="new",
            recommended_resources=[{"resource_type": "ambulance", "quantity": 1, "reasoning": "Test"}],
            assigned_resources=[]
        )
        db.add(inc)
        await db.commit()
        
    return incident_id, resource_id


@pytest.mark.asyncio
async def test_get_incidents(client: AsyncClient, admin_token, cleanup_phase4_data):
    headers = {"Authorization": f"Bearer {admin_token}"}
    incident_id, _ = await seed_incident_and_resource(client, admin_token)
    
    # Basic get
    resp = await client.get("/api/v1/incidents", headers=headers)
    assert resp.status_code == 200
    data = resp.json()
    assert len(data["items"]) == 1
    assert data["items"][0]["incident_id"] == incident_id
    
    # Filter by severity
    resp = await client.get("/api/v1/incidents?severity=high", headers=headers)
    assert len(resp.json()["items"]) == 1
    
    resp = await client.get("/api/v1/incidents?severity=low", headers=headers)
    assert len(resp.json()["items"]) == 0
    
    # Filter by status
    resp = await client.get("/api/v1/incidents?status=new", headers=headers)
    assert len(resp.json()["items"]) == 1
    
    # Bounding box filter (76, 11, 78, 13)
    resp = await client.get("/api/v1/incidents?bbox=76.0,11.0,78.0,13.0", headers=headers)
    assert len(resp.json()["items"]) == 1
    
    # Since filter
    past_date = (datetime.utcnow() - timedelta(minutes=15)).isoformat()
    resp = await client.get(f"/api/v1/incidents?since={past_date}", headers=headers)
    assert len(resp.json()["items"]) == 1
    
    future_date = (datetime.utcnow() + timedelta(minutes=15)).isoformat()
    resp = await client.get(f"/api/v1/incidents?since={future_date}", headers=headers)
    assert len(resp.json()["items"]) == 0

@pytest.mark.asyncio
async def test_get_incident_detail(client: AsyncClient, admin_token, cleanup_phase4_data):
    headers = {"Authorization": f"Bearer {admin_token}"}
    incident_id, _ = await seed_incident_and_resource(client, admin_token)
    
    resp = await client.get(f"/api/v1/incidents/{incident_id}", headers=headers)
    assert resp.status_code == 200
    data = resp.json()
    assert data["incident_id"] == incident_id
    assert data["severity"] == "high"

@pytest.mark.asyncio
async def test_update_incident_status(client: AsyncClient, admin_token, cleanup_phase4_data):
    headers = {"Authorization": f"Bearer {admin_token}"}
    incident_id, _ = await seed_incident_and_resource(client, admin_token)
    
    patch_payload = {"status": "acknowledged"}
    resp = await client.patch(f"/api/v1/incidents/{incident_id}/status", json=patch_payload, headers=headers)
    assert resp.status_code == 200
    data = resp.json()
    assert data["status"] == "acknowledged"

@pytest.mark.asyncio
async def test_get_recommendations(client: AsyncClient, admin_token, cleanup_phase4_data):
    headers = {"Authorization": f"Bearer {admin_token}"}
    incident_id, resource_id = await seed_incident_and_resource(client, admin_token)
    
    resp = await client.get(f"/api/v1/incidents/{incident_id}/recommendations", headers=headers)
    assert resp.status_code == 200
    data = resp.json()
    assert len(data) == 1
    assert data[0]["resource_type"] == "ambulance"

@pytest.mark.asyncio
async def test_dispatch_resource(client: AsyncClient, admin_token, cleanup_phase4_data):
    headers = {"Authorization": f"Bearer {admin_token}"}
    incident_id, resource_id = await seed_incident_and_resource(client, admin_token)
    
    # Dispatch
    dispatch_payload = {
        "resource_id": resource_id,
        "quantity": 2
    }
    resp = await client.post(f"/api/v1/incidents/{incident_id}/dispatch", json=dispatch_payload, headers=headers)
    assert resp.status_code == 201
    data = resp.json()
    assert data["status"] == "dispatched"
    assert data["quantity_dispatched"] == 2
    
    # Verify resource was updated
    res_resp = await client.get(f"/api/v1/resources/{resource_id}", headers=headers)
    res_data = res_resp.json()
    assert res_data["quantity_available"] == 3
    assert res_data["status"] == "partially_deployed"
    
    # Verify incident was updated
    inc_resp = await client.get(f"/api/v1/incidents/{incident_id}", headers=headers)
    inc_data = inc_resp.json()
    assert inc_data["status"] == "dispatched"
    assert len(inc_data["assigned_resources"]) == 1
    assert inc_data["assigned_resources"][0] == resource_id

@pytest.mark.asyncio
async def test_dispatch_resource_not_enough(client: AsyncClient, admin_token, cleanup_phase4_data):
    headers = {"Authorization": f"Bearer {admin_token}"}
    incident_id, resource_id = await seed_incident_and_resource(client, admin_token)
    
    # Dispatch
    dispatch_payload = {
        "resource_id": resource_id,
        "quantity": 10
    }
    resp = await client.post(f"/api/v1/incidents/{incident_id}/dispatch", json=dispatch_payload, headers=headers)
    assert resp.status_code == 400
    assert "Not enough resource" in resp.json()["detail"]

@pytest.mark.asyncio
async def test_refresh_summary(client: AsyncClient, admin_token, cleanup_phase4_data):
    headers = {"Authorization": f"Bearer {admin_token}"}
    incident_id, _ = await seed_incident_and_resource(client, admin_token)
    
    resp = await client.post(f"/api/v1/incidents/{incident_id}/refresh-summary", headers=headers)
    assert resp.status_code == 200
    data = resp.json()
    assert data["status"] == "refresh_queued"
