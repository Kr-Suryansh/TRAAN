"""Tests for GET, POST, PATCH /api/v1/resources"""

import pytest
from httpx import AsyncClient
from app.core.security import create_authority_jwt

@pytest.fixture
def admin_token(setup_authority):
    return create_authority_jwt(setup_authority.id, "admin")

@pytest.fixture
def user_token(setup_authority):
    return create_authority_jwt(setup_authority.id, "user")

@pytest.mark.asyncio
async def test_create_resource_admin(client: AsyncClient, admin_token, cleanup_phase4_data):
    headers = {"Authorization": f"Bearer {admin_token}"}
    payload = {
        "category": "medical",
        "sub_type": "ambulance",
        "custodian_agency": "City Hospital",
        "quantity_total": 5,
        "quantity_available": 5,
        "status": "available",
        "contact": "1234567890",
        "location": {
            "lat": 12.9716,
            "lng": 77.5946,
            "district": "Bengaluru"
        }
    }
    
    resp = await client.post("/api/v1/resources", json=payload, headers=headers)
    assert resp.status_code == 201
    data = resp.json()
    assert "resource_id" in data
    assert data["category"] == "medical"
    assert data["quantity_available"] == 5

@pytest.mark.asyncio
async def test_create_resource_not_admin(client: AsyncClient, user_token, cleanup_phase4_data):
    headers = {"Authorization": f"Bearer {user_token}"}
    payload = {
        "category": "medical",
        "sub_type": "ambulance",
        "custodian_agency": "City Hospital",
        "quantity_total": 5,
        "quantity_available": 5,
        "status": "available",
        "contact": "1234567890",
        "location": {
            "lat": 12.9716,
            "lng": 77.5946,
            "district": "Bengaluru"
        }
    }
    
    resp = await client.post("/api/v1/resources", json=payload, headers=headers)
    assert resp.status_code == 403

@pytest.mark.asyncio
async def test_get_resources(client: AsyncClient, admin_token, cleanup_phase4_data):
    headers = {"Authorization": f"Bearer {admin_token}"}
    
    # Create two resources
    payload1 = {
        "category": "rescue",
        "sub_type": "boat",
        "custodian_agency": "NDRF",
        "quantity_total": 2,
        "quantity_available": 2,
        "status": "available",
        "contact": "9876543210",
        "location": {"lat": 13.0, "lng": 78.0, "district": "Chennai"}
    }
    payload2 = {
        "category": "medical",
        "sub_type": "first_aid",
        "custodian_agency": "Red Cross",
        "quantity_total": 10,
        "quantity_available": 5,
        "status": "partially_deployed",
        "contact": "112",
        "location": {"lat": 13.0, "lng": 78.0, "district": "Bengaluru"}
    }
    await client.post("/api/v1/resources", json=payload1, headers=headers)
    await client.post("/api/v1/resources", json=payload2, headers=headers)
    
    # No filters
    resp = await client.get("/api/v1/resources", headers=headers)
    assert resp.status_code == 200
    assert len(resp.json()) == 2
    
    # Category filter
    resp = await client.get("/api/v1/resources?category=rescue", headers=headers)
    assert len(resp.json()) == 1
    assert resp.json()[0]["category"] == "rescue"
    
    # Status filter
    resp = await client.get("/api/v1/resources?status=partially_deployed", headers=headers)
    assert len(resp.json()) == 1
    assert resp.json()[0]["status"] == "partially_deployed"

    # District filter
    resp = await client.get("/api/v1/resources?district=Chennai", headers=headers)
    assert len(resp.json()) == 1
    data = resp.json()[0]
    assert data["location"]["district"] == "Chennai"

@pytest.mark.asyncio
async def test_get_resource_by_id(client: AsyncClient, admin_token, cleanup_phase4_data):
    headers = {"Authorization": f"Bearer {admin_token}"}
    payload = {
        "category": "rescue",
        "sub_type": "boat",
        "custodian_agency": "NDRF",
        "quantity_total": 2,
        "quantity_available": 2,
        "status": "available",
        "contact": "9876543210",
        "location": {"lat": 13.0, "lng": 78.0, "district": "Chennai"}
    }
    create_resp = await client.post("/api/v1/resources", json=payload, headers=headers)
    resource_id = create_resp.json()["resource_id"]
    
    resp = await client.get(f"/api/v1/resources/{resource_id}", headers=headers)
    assert resp.status_code == 200
    data = resp.json()
    assert data["resource_id"] == resource_id
    assert data["category"] == "rescue"
    
@pytest.mark.asyncio
async def test_get_resource_not_found(client: AsyncClient, admin_token, cleanup_phase4_data):
    headers = {"Authorization": f"Bearer {admin_token}"}
    resp = await client.get("/api/v1/resources/invalid-id", headers=headers)
    assert resp.status_code == 404

@pytest.mark.asyncio
async def test_update_resource(client: AsyncClient, admin_token, cleanup_phase4_data):
    headers = {"Authorization": f"Bearer {admin_token}"}
    payload = {
        "category": "transport",
        "sub_type": "bus",
        "custodian_agency": "KSRTC",
        "quantity_total": 10,
        "quantity_available": 10,
        "status": "available",
        "contact": "111222333",
        "location": {"lat": 12.0, "lng": 77.0, "district": "Mysuru"}
    }
    create_resp = await client.post("/api/v1/resources", json=payload, headers=headers)
    resource_id = create_resp.json()["resource_id"]
    
    patch_payload = {
        "quantity_available": 8,
        "status": "partially_deployed"
    }
    patch_resp = await client.patch(f"/api/v1/resources/{resource_id}", json=patch_payload, headers=headers)
    assert patch_resp.status_code == 200
    data = patch_resp.json()
    assert data["quantity_available"] == 8
    assert data["status"] == "partially_deployed"


# ---------------------------------------------------------------------------
# Regression Tests: Input Validation
# ---------------------------------------------------------------------------

@pytest.mark.asyncio
async def test_resource_patch_negative_quantity(client: AsyncClient, admin_token, cleanup_phase4_data):
    headers = {"Authorization": f"Bearer {admin_token}"}
    payload = {
        "category": "transport",
        "sub_type": "bus",
        "custodian_agency": "KSRTC",
        "quantity_total": 10,
        "quantity_available": 10,
        "status": "available",
        "contact": "111222333",
        "location": {"lat": 12.0, "lng": 77.0, "district": "Mysuru"}
    }
    create_resp = await client.post("/api/v1/resources", json=payload, headers=headers)
    resource_id = create_resp.json()["resource_id"]
    
    patch_payload = {"quantity_available": -5}
    patch_resp = await client.patch(f"/api/v1/resources/{resource_id}", json=patch_payload, headers=headers)
    assert patch_resp.status_code == 422


@pytest.mark.asyncio
async def test_resource_patch_quantity_exceeds_total(client: AsyncClient, admin_token, cleanup_phase4_data):
    headers = {"Authorization": f"Bearer {admin_token}"}
    payload = {
        "category": "transport",
        "sub_type": "bus",
        "custodian_agency": "KSRTC",
        "quantity_total": 10,
        "quantity_available": 10,
        "status": "available",
        "contact": "111222333",
        "location": {"lat": 12.0, "lng": 77.0, "district": "Mysuru"}
    }
    create_resp = await client.post("/api/v1/resources", json=payload, headers=headers)
    resource_id = create_resp.json()["resource_id"]
    
    patch_payload = {"quantity_available": 15}
    patch_resp = await client.patch(f"/api/v1/resources/{resource_id}", json=patch_payload, headers=headers)
    assert patch_resp.status_code == 400
    assert "quantity_available cannot exceed quantity_total" in patch_resp.text


@pytest.mark.asyncio
async def test_resource_patch_invalid_status(client: AsyncClient, admin_token, cleanup_phase4_data):
    headers = {"Authorization": f"Bearer {admin_token}"}
    payload = {
        "category": "transport",
        "sub_type": "bus",
        "custodian_agency": "KSRTC",
        "quantity_total": 10,
        "quantity_available": 10,
        "status": "available",
        "contact": "111222333",
        "location": {"lat": 12.0, "lng": 77.0, "district": "Mysuru"}
    }
    create_resp = await client.post("/api/v1/resources", json=payload, headers=headers)
    resource_id = create_resp.json()["resource_id"]
    
    patch_payload = {"status": "invalid_status"}
    patch_resp = await client.patch(f"/api/v1/resources/{resource_id}", json=patch_payload, headers=headers)
    assert patch_resp.status_code == 422
