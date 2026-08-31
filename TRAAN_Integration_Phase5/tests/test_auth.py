import pytest
from httpx import AsyncClient
from sqlalchemy import select, delete

from app.core.security import hash_password, create_authority_jwt
from app.db._models.authority import Authority
from app.db.engine import AsyncSessionLocal, engine




@pytest.mark.asyncio
async def test_device_register(client: AsyncClient):
    installation_id = str(__import__('uuid').uuid4())
    response = await client.post(
        "/api/v1/auth/device/register",
        json={
            "device_id": installation_id,
            "device_model": "TestModel X",
            "app_version": "1.0.0",
        }
    )
    assert response.status_code == 200
    data = response.json()
    assert "device_id" in data
    assert "device_jwt" in data
    # Model A invariant: backend must echo the Android-supplied UUID unchanged
    assert data["device_id"] == installation_id


@pytest.mark.asyncio
async def test_device_register_idempotent(client: AsyncClient):
    """Re-registering the same device_id must return 200 and a fresh JWT (P0.4.5)."""
    installation_id = str(__import__('uuid').uuid4())
    payload = {"device_id": installation_id, "device_model": "ReRegModel", "app_version": "2.0.0"}

    r1 = await client.post("/api/v1/auth/device/register", json=payload)
    assert r1.status_code == 200
    assert r1.json()["device_id"] == installation_id

    r2 = await client.post("/api/v1/auth/device/register", json=payload)
    assert r2.status_code == 200
    assert r2.json()["device_id"] == installation_id  # same canonical id
    # Verify the second response successfully contains a device JWT
    assert "device_jwt" in r2.json()


@pytest.mark.asyncio
async def test_device_register_invalid_uuid(client: AsyncClient):
    """A non-UUID4 device_id must be rejected with 422."""
    response = await client.post(
        "/api/v1/auth/device/register",
        json={"device_id": "not-a-valid-uuid", "device_model": "X", "app_version": "1.0"}
    )
    assert response.status_code == 422


@pytest.mark.asyncio
async def test_authority_login_success(client: AsyncClient, setup_authority):
    response = await client.post(
        "/api/v1/auth/authority/login",
        json={"email": "test@sih.gov.in", "password": "password123"}
    )
    assert response.status_code == 200
    data = response.json()
    assert "access_token" in data
    assert "refresh_token" in data
    assert "user" in data
    assert data["user"]["email"] == "test@sih.gov.in"
    assert data["user"]["role"] == "admin"


@pytest.mark.asyncio
async def test_authority_login_failure(client: AsyncClient, setup_authority):
    response = await client.post(
        "/api/v1/auth/authority/login",
        json={"email": "test@sih.gov.in", "password": "wrongpassword"}
    )
    assert response.status_code == 401


@pytest.mark.asyncio
async def test_authority_refresh(client: AsyncClient, setup_authority):
    from app.core.security import create_authority_refresh_jwt
    token = create_authority_refresh_jwt(setup_authority.id, setup_authority.role)
    response = await client.post(
        "/api/v1/auth/authority/refresh",
        json={"refresh_token": token}
    )
    assert response.status_code == 200
    data = response.json()
    assert "access_token" in data
    assert "refresh_token" in data


@pytest.mark.asyncio
async def test_authority_refresh_invalid_type(client: AsyncClient, setup_authority):
    from app.core.security import create_authority_jwt
    # Should reject access_token used as refresh_token
    token = create_authority_jwt(setup_authority.id, setup_authority.role)
    response = await client.post(
        "/api/v1/auth/authority/refresh",
        json={"refresh_token": token}
    )
    assert response.status_code == 401
    assert "Invalid token type" in response.json()["detail"]


@pytest.mark.asyncio
async def test_authority_refresh_invalid(client: AsyncClient):
    response = await client.post(
        "/api/v1/auth/authority/refresh",
        json={"refresh_token": "invalid_token"}
    )
    assert response.status_code == 401
