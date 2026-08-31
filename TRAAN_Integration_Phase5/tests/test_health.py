"""Tests for GET /api/v1/health."""

from httpx import AsyncClient


async def test_health_status_ok(client: AsyncClient) -> None:
    """Health endpoint always returns HTTP 200 with status='ok'."""
    response = await client.get("/api/v1/health")
    assert response.status_code == 200
    body = response.json()
    assert body["status"] == "ok"


async def test_health_has_api_version(client: AsyncClient) -> None:
    """Health response includes the API version string."""
    response = await client.get("/api/v1/health")
    assert "api_version" in response.json()


async def test_health_has_database_field(client: AsyncClient) -> None:
    """Health response includes a 'database' field ('ok' or 'error')."""
    response = await client.get("/api/v1/health")
    body = response.json()
    assert "database" in body
    assert body["database"] in ("ok", "error")


async def test_health_has_postgis_field(client: AsyncClient) -> None:
    """Health response includes a 'postgis_version' field (str or None)."""
    response = await client.get("/api/v1/health")
    body = response.json()
    assert "postgis_version" in body
