"""Resource modification to WebSocket integration test."""

import pytest
from httpx import AsyncClient
from fastapi.testclient import TestClient
from app.main import app
from app.core.security import create_authority_jwt

@pytest.fixture
def admin_token(setup_authority):
    return create_authority_jwt(setup_authority.id, "admin")

@pytest.fixture
def sync_client():
    with TestClient(app) as client:
        yield client

def test_resource_to_websocket_integration(sync_client: TestClient, setup_authority, cleanup_phase4_data):
    """
    Integration coverage for:
    Resource modification -> Backend API -> resource_updated WS event -> Dashboard
    
    Verifies the existing flow matches contracts.
    """
    admin_token = create_authority_jwt(setup_authority.id, "admin")
    headers = {"Authorization": f"Bearer {admin_token}"}
    
    # 1. Create a resource via REST API
    res_payload = {
        "category": "medical",
        "sub_type": "ambulance",
        "custodian_agency": "Integration Test Agency",
        "quantity_total": 5,
        "quantity_available": 5,
        "status": "available",
        "contact": "1234567890",
        "location": {"lat": 12.0, "lng": 77.0, "district": "TestDistrict"}
    }
    
    response = sync_client.post("/api/v1/resources", json=res_payload, headers=headers)
    assert response.status_code == 201
    resource_id = response.json()["resource_id"]
    
    # Connect to WebSocket (simulating the Dashboard)
    with sync_client.websocket_connect(f"/ws/incidents?token={admin_token}") as ws:
        # 2. Modify the resource via REST API
        update_payload = {"quantity_available": 4, "status": "partially_deployed"}
        patch_response = sync_client.patch(f"/api/v1/resources/{resource_id}", json=update_payload, headers=headers)
        assert patch_response.status_code == 200
        
        # 3. Backend processes the request and emits `resource_updated` event
        # 4. Receive event on WebSocket
        data = ws.receive_json()
        
        # 5. The event payload matches the existing contract
        assert data["event"] == "resource_updated"
        
        # Validate shape matches Dashboard expectation
        payload = data["data"]
        assert payload["resource_id"] == resource_id
        assert payload["quantity_available"] == 4
        assert payload["status"] == "partially_deployed"
        assert "location" in payload
        assert "last_updated_at" in payload
        assert "category" in payload
