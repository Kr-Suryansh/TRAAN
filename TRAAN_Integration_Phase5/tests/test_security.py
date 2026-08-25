"""Negative security tests for unauthorized access to protected endpoints."""

import pytest
from httpx import AsyncClient
import uuid
from datetime import datetime, timedelta
from jose import jwt
from app.config import settings
from app.core.security import create_device_jwt, create_authority_jwt

@pytest.fixture
def admin_token(setup_authority):
    return create_authority_jwt(setup_authority.id, "admin")

@pytest.fixture
def device_token():
    return create_device_jwt("gw-test-123")

def create_expired_authority_jwt(authority_id: str, role: str) -> str:
    # Intentionally expired token (in the past)
    expire = datetime.utcnow() - timedelta(minutes=10)
    to_encode = {"sub": authority_id, "role": role, "exp": expire, "type": "authority"}
    return jwt.encode(to_encode, settings.JWT_SECRET_KEY, algorithm=settings.JWT_ALGORITHM)

@pytest.mark.asyncio
async def test_unauthorized_no_token(client: AsyncClient):
    """Test endpoints with no Authorization header."""
    endpoints = [
        ("/api/v1/stats/summary", 401), # security dependency returns 401 when Not Authenticated
        ("/api/v1/incidents", 401),
        ("/api/v1/resources", 401),
        (f"/api/v1/sos/{uuid.uuid4()}/status", 401)
    ]
    for endpoint, expected_status in endpoints:
        response = await client.get(endpoint)
        assert response.status_code == expected_status

@pytest.mark.asyncio
async def test_unauthorized_invalid_token_type(client: AsyncClient, device_token, setup_authority):
    """Test using a device token for authority endpoints, and vice versa."""
    
    # 1. Device token on Authority endpoint
    headers = {"Authorization": f"Bearer {device_token}"}
    response = await client.get("/api/v1/incidents", headers=headers)
    assert response.status_code == 401
    assert "Invalid token type" in response.json()["detail"]

    # 2. Authority token on Device endpoint
    auth_token = create_authority_jwt(setup_authority.id, "admin")
    auth_headers = {"Authorization": f"Bearer {auth_token}"}
    response = await client.get(f"/api/v1/sos/{uuid.uuid4()}/status", headers=auth_headers)
    assert response.status_code == 401
    assert "Invalid token type" in response.json()["detail"]

@pytest.mark.asyncio
async def test_unauthorized_expired_token(client: AsyncClient, setup_authority):
    """Test using an explicitly expired token based on current security behavior."""
    expired_token = create_expired_authority_jwt(setup_authority.id, "admin")
    headers = {"Authorization": f"Bearer {expired_token}"}
    
    response = await client.get("/api/v1/incidents", headers=headers)
    assert response.status_code == 401
    assert "Could not validate credentials" in response.json()["detail"]

@pytest.mark.asyncio
async def test_unauthorized_wrong_device(client: AsyncClient, device_token, cleanup_sos_data):
    """Test device attempting to access another device's SOS status."""
    from tests.test_sos import make_batch_payload, make_sos_item
    
    # Setup: Create an SOS report from device A
    device_a_id = "test-device-id" # This is what make_sos_item uses
    device_a_token = create_device_jwt(device_a_id)
    
    report_uuid = str(uuid.uuid4())
    payload = make_batch_payload([make_sos_item(report_uuid)])
    # Upload requires the gateway token matching gateway_device_id
    gw_token = create_device_jwt("gw-test-123")
    
    # Upload via Gateway
    upload_resp = await client.post("/api/v1/sos/batch", json=payload, headers={"Authorization": f"Bearer {gw_token}"})
    assert upload_resp.status_code == 202
    
    # Device B attempts to get status
    device_b_token = create_device_jwt("some-other-device")
    response = await client.get(f"/api/v1/sos/{report_uuid}/status", headers={"Authorization": f"Bearer {device_b_token}"})
    
    # Should be 403 Forbidden because ownership mismatch
    assert response.status_code == 403
    assert "Device identity mismatch" in response.json()["detail"]

@pytest.mark.asyncio
async def test_unauthorized_wrong_role(client: AsyncClient, setup_authority):
    """Test role restrictions on admin-only endpoints."""
    # POST /api/v1/resources requires "admin" role
    # Let's use a "DDMA" role instead
    wrong_role_token = create_authority_jwt(setup_authority.id, "DDMA")
    headers = {"Authorization": f"Bearer {wrong_role_token}"}
    
    res_payload = {
        "category": "medical",
        "sub_type": "ambulance",
        "custodian_agency": "Test",
        "quantity_total": 5,
        "quantity_available": 5,
        "status": "available",
        "contact": "123",
        "location": {"lat": 12.0, "lng": 77.0, "district": "Test"}
    }
    response = await client.post("/api/v1/resources", json=res_payload, headers=headers)
    assert response.status_code == 403
    assert "Admin role required to create resources" in response.json()["detail"]

@pytest.mark.asyncio
async def test_authority_refresh_valid_token(client: AsyncClient, setup_authority):
    """Test token refresh behaves correctly with refresh token."""
    from app.core.security import create_authority_refresh_jwt
    refresh_token = create_authority_refresh_jwt(setup_authority.id, "admin")
    payload = {"refresh_token": refresh_token}
    response = await client.post("/api/v1/auth/authority/refresh", json=payload)
    assert response.status_code == 200
    assert "access_token" in response.json()
    assert "refresh_token" in response.json()
