"""Tests for GET /api/v1/stats/summary."""

from httpx import AsyncClient


async def test_stats_summary_status_ok(client: AsyncClient) -> None:
    """Stats summary returns HTTP 200."""
    response = await client.get("/api/v1/stats/summary")
    assert response.status_code == 200


async def test_stats_summary_shape(client: AsyncClient) -> None:
    """Stats summary response matches the contract shape exactly."""
    response = await client.get("/api/v1/stats/summary")
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
