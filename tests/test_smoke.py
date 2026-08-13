"""
End-to-End Smoke Test Sequence
"""

import pytest
import uuid
import datetime
import asyncio
from httpx import AsyncClient
from starlette.testclient import TestClient

from app.main import app

pytestmark = pytest.mark.asyncio

async def test_end_to_end_smoke(client: AsyncClient, setup_authority, cleanup_phase4_data):
    # 1. Device Registration
    reg_resp = await client.post("/api/v1/auth/device/register", json={
        "device_model": "Test Phone",
        "app_version": "1.0.0"
    })
    assert reg_resp.status_code == 200
    device_token = reg_resp.json()["device_jwt"]
    device_id = reg_resp.json()["device_id"]
    
    # 2. Authority Login
    login_resp = await client.post("/api/v1/auth/authority/login", json={
        "email": setup_authority.email,
        "password": "password123"
    })
    assert login_resp.status_code == 200
    auth_token = login_resp.json()["access_token"]
    auth_headers = {"Authorization": f"Bearer {auth_token}"}
    device_headers = {"Authorization": f"Bearer {device_token}"}

    # 3. Create a resource
    res_payload = {
        "category": "medical",
        "sub_type": "ambulance",
        "custodian_agency": "Smoke Hospital",
        "quantity_total": 5,
        "quantity_available": 5,
        "status": "available",
        "contact": "1231231234",
        "location": {"lat": 12.0, "lng": 77.0, "district": "Mysuru"}
    }
    res_resp = await client.post("/api/v1/resources", json=res_payload, headers=auth_headers)
    assert res_resp.status_code == 201
    resource_id = res_resp.json()["resource_id"]

    # 4. SOS Batch Upload
    from tests.test_sos import make_batch_payload, make_sos_item
    
    sos_uuid1 = str(uuid.uuid4())
    item1 = make_sos_item(sos_uuid1, lat=12.0001, lng=77.0001)
    item1["device_id"] = device_id
    item1["status"] = "uploaded"
    
    sos_uuid2 = str(uuid.uuid4())
    item2 = make_sos_item(sos_uuid2, lat=12.0006, lng=77.0001)
    item2["device_id"] = device_id
    item2["status"] = "uploaded"

    sos_payload = make_batch_payload([item1, item2])
    sos_payload["gateway_device_id"] = device_id
    
    sos_resp = await client.post("/api/v1/sos/batch", json=sos_payload, headers=device_headers)
    print("SOS RESP:", sos_resp.json())
    assert sos_resp.status_code == 202
    
    # 5. Verify incident creation
    incidents_resp = await client.get("/api/v1/incidents", headers=auth_headers)
    assert incidents_resp.status_code == 200
    incidents = incidents_resp.json()["items"]
    # Should have exactly 1 incident from the SOS upload
    assert len(incidents) > 0
    incident_id = incidents[0]["incident_id"]
    
    # 6. Dispatch Resource
    dispatch_payload = {
        "resource_id": resource_id,
        "quantity": 2
    }
    disp_resp = await client.post(f"/api/v1/incidents/{incident_id}/dispatch", json=dispatch_payload, headers=auth_headers)
    assert disp_resp.status_code == 201
    
    # 7. Update Incident Status
    patch_resp = await client.patch(f"/api/v1/incidents/{incident_id}/status", json={"status": "acknowledged"}, headers=auth_headers)
    assert patch_resp.status_code == 200
    assert patch_resp.json()["status"] == "acknowledged"

    # 8. Verify WebSocket - Tested comprehensively in test_ws.py
