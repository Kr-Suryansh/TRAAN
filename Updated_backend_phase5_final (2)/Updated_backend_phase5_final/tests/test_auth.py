import pytest
from httpx import AsyncClient
from sqlalchemy import select, delete

from app.core.security import hash_password, create_authority_jwt
from app.db._models.authority import Authority
from app.db.engine import AsyncSessionLocal, engine




@pytest.mark.asyncio
async def test_device_register(client: AsyncClient):
    response = await client.post(
        "/api/v1/auth/device/register",
        json={"device_model": "TestModel X", "app_version": "1.0.0"}
    )
    assert response.status_code == 200
    data = response.json()
    assert "device_id" in data
    assert "device_jwt" in data


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
