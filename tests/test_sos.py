"""
Phase 3 — SOS Ingestion integration tests.

These tests use the real Docker PostgreSQL/PostGIS database.
Each test that writes SOS data uses the `cleanup_sos_data` fixture to
ensure DB isolation — rows are deleted before and after.

The `device_token` fixture creates a real Device row and returns a valid
device JWT for use as `Authorization: Bearer <token>`.
"""

import pytest
import datetime
import uuid
from httpx import AsyncClient
from sqlalchemy import select

from app.db.engine import AsyncSessionLocal
from app.db._models.device import Device
from app.db._models.sos import SOSReport
from app.core.security import create_device_jwt

pytestmark = pytest.mark.asyncio


@pytest.fixture
async def device_token() -> str:
    """
    Create a real device row and return its JWT.
    Uses AsyncSessionLocal directly so it operates within the correct event loop.
    """
    dev_id = "gw-test-123"
    async with AsyncSessionLocal() as db:
        existing = await db.get(Device, dev_id)
        if not existing:
            new_device = Device(
                id=dev_id,
                device_model="TestModel",
                app_version="1.0.0"
            )
            db.add(new_device)
            await db.commit()
    return create_device_jwt(dev_id)


def make_batch_payload(sos_items: list) -> dict:
    return {
        "gateway_device_id": "gw-test-123",
        "gateway_location": {"lat": 10.0, "lng": 20.0},
        "uploaded_at": datetime.datetime.now(datetime.timezone.utc).isoformat(),
        "sos_batch": sos_items
    }


def make_sos_item(report_uuid: str = None, lat: float = 10.0, lng: float = 20.0,
                  created_at: str = None) -> dict:
    if report_uuid is None:
        report_uuid = str(uuid.uuid4())
    if created_at is None:
        created_at = datetime.datetime.now(datetime.timezone.utc).isoformat()
    return {
        "uuid": report_uuid,
        "device_id": "test-device-id",
        "created_at": created_at,
        "location": {
            "lat": lat,
            "lng": lng,
            "accuracy_m": 5.0
        },
        "is_quick_sos": True,
        "emergency_type": "medical",
        "severity_hint": "high",
        "people_count": 1,
        "medical_snapshot": {"blood_type": "O+"},
        "custom_message": "Need help!",
        "contact_number": "1234567890",
        "relay_hop_count": 0,
        "last_relayed_at": datetime.datetime.now(datetime.timezone.utc).isoformat(),
        "status": "pending_local"
    }


# ---------------------------------------------------------------------------
# Test: unauthenticated request must return 401
# ---------------------------------------------------------------------------

async def test_sos_batch_unauthorized(client: AsyncClient):
    payload = make_batch_payload([make_sos_item()])
    response = await client.post("/api/v1/sos/batch", json=payload)
    assert response.status_code == 401


# ---------------------------------------------------------------------------
# Test: valid batch upload is accepted
# ---------------------------------------------------------------------------

async def test_sos_batch_valid_upload(client: AsyncClient, device_token: str,
                                      cleanup_sos_data):
    report_uuid = str(uuid.uuid4())
    payload = make_batch_payload([make_sos_item(report_uuid)])
    headers = {"Authorization": f"Bearer {device_token}"}

    response = await client.post("/api/v1/sos/batch", json=payload, headers=headers)
    assert response.status_code == 202
    data = response.json()

    assert report_uuid in data["accepted_uuids"]
    assert len(data["duplicate_uuids"]) == 0

    # Verify DB row was created
    async with AsyncSessionLocal() as db:
        res = await db.execute(
            select(SOSReport).where(SOSReport.uuid == report_uuid)
        )
        report = res.scalar_one()
        assert report.uuid == report_uuid
        assert report.emergency_type == "medical"
        # cluster_id is set by ST_ClusterDBSCAN (DBSCAN_MINPOINTS=1 guarantees all points get one)
        assert report.cluster_id is not None


# ---------------------------------------------------------------------------
# Test: re-uploading the same UUID is identified as exact duplicate
# ---------------------------------------------------------------------------

async def test_sos_batch_exact_duplicate(client: AsyncClient, device_token: str,
                                         cleanup_sos_data):
    report_uuid = str(uuid.uuid4())
    payload = make_batch_payload([make_sos_item(report_uuid)])
    headers = {"Authorization": f"Bearer {device_token}"}

    # First upload — accepted
    res1 = await client.post("/api/v1/sos/batch", json=payload, headers=headers)
    assert res1.status_code == 202
    assert report_uuid in res1.json()["accepted_uuids"]

    # Second upload — exact duplicate
    res2 = await client.post("/api/v1/sos/batch", json=payload, headers=headers)
    assert res2.status_code == 202
    assert report_uuid in res2.json()["duplicate_uuids"]


# ---------------------------------------------------------------------------
# Test: different UUID but same location + time is identified as near-duplicate
# ---------------------------------------------------------------------------

async def test_sos_batch_spatial_time_duplicate(client: AsyncClient, device_token: str,
                                                cleanup_sos_data):
    base_uuid = str(uuid.uuid4())
    dup_uuid = str(uuid.uuid4())

    ts = datetime.datetime.now(datetime.timezone.utc).isoformat()
    item1 = make_sos_item(base_uuid, lat=10.0, lng=20.0, created_at=ts)
    # Same location and timestamp, different UUID — should be detected as near-dup
    item2 = make_sos_item(dup_uuid, lat=10.0, lng=20.0, created_at=ts)

    payload = make_batch_payload([item1, item2])
    headers = {"Authorization": f"Bearer {device_token}"}

    response = await client.post("/api/v1/sos/batch", json=payload, headers=headers)
    assert response.status_code == 202
    data = response.json()

    # Exactly one accepted, one near-duplicate
    assert len(data["accepted_uuids"]) == 1
    assert len(data["duplicate_uuids"]) == 1


# ---------------------------------------------------------------------------
# Test: GET /{uuid}/status returns the stored status
# ---------------------------------------------------------------------------

async def test_get_sos_status(client: AsyncClient, device_token: str,
                              cleanup_sos_data):
    report_uuid = str(uuid.uuid4())
    payload = make_batch_payload([make_sos_item(report_uuid)])
    headers = {"Authorization": f"Bearer {device_token}"}

    # Upload
    upload_resp = await client.post("/api/v1/sos/batch", json=payload, headers=headers)
    assert upload_resp.status_code == 202
    assert report_uuid in upload_resp.json()["accepted_uuids"], (
        f"Upload failed; response: {upload_resp.json()}"
    )

    # Status check
    status_resp = await client.get(f"/api/v1/sos/{report_uuid}/status", headers=headers)
    assert status_resp.status_code == 200
    assert status_resp.json()["status"] == "pending_local"


# ---------------------------------------------------------------------------
# Test: GET /{uuid}/status for unknown UUID returns 404
# ---------------------------------------------------------------------------

async def test_get_sos_status_not_found(client: AsyncClient, device_token: str):
    headers = {"Authorization": f"Bearer {device_token}"}
    response = await client.get(f"/api/v1/sos/{uuid.uuid4()}/status", headers=headers)
    assert response.status_code == 404
