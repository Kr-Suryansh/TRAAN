"""Tests for GET /api/v1/stats/summary."""

from httpx import AsyncClient
import pytest
from app.core.security import create_authority_jwt

@pytest.fixture
def admin_token(setup_authority):
    return create_authority_jwt(setup_authority.id, "admin")

@pytest.mark.asyncio
async def test_stats_summary_status_ok(client: AsyncClient, admin_token, cleanup_phase4_data) -> None:
    """Stats summary returns HTTP 200."""
    headers = {"Authorization": f"Bearer {admin_token}"}
    response = await client.get("/api/v1/stats/summary", headers=headers)
    assert response.status_code == 200

@pytest.mark.asyncio
async def test_stats_summary_shape(client: AsyncClient, admin_token, cleanup_phase4_data) -> None:
    """Stats summary response matches the contract shape exactly."""
    headers = {"Authorization": f"Bearer {admin_token}"}
    response = await client.get("/api/v1/stats/summary", headers=headers)
    body = response.json()

    # Top-level keys
    assert "active_incidents" in body
    assert "total_estimated_people_affected" in body
    assert "resources" in body
    assert "new_incidents_last_15min" in body

    # active_incidents breakdown
    ai = body["active_incidents"]
    for severity in ("critical", "high", "medium", "low"):
        assert severity in ai, f"missing severity key: {severity}"
        assert isinstance(ai[severity], int)

    # resources breakdown
    res = body["resources"]
    assert "available" in res
    assert "deployed" in res

    # numeric types
    assert isinstance(body["total_estimated_people_affected"], int)
    assert isinstance(body["new_incidents_last_15min"], int)
