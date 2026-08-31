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


# Stable UUID used as the gateway/device identity throughout test_sos.py.
# Under Model A this equals the Android installationId.
_GATEWAY_DEVICE_ID = "a1b2c3d4-e5f6-4789-abcd-ef1234567890"


@pytest.fixture
async def device_token() -> str:
    """
    Create a real device row using a valid UUID4 and return its JWT.
    Uses AsyncSessionLocal directly so it operates within the correct event loop.
    """
    dev_id = _GATEWAY_DEVICE_ID
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
        "gateway_device_id": _GATEWAY_DEVICE_ID,
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
        # Model A: device_id == the gateway's installationId so GET /status ownership passes
        "device_id": _GATEWAY_DEVICE_ID,
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

    # Status check — use device_token whose sub == _GATEWAY_DEVICE_ID == sos.device_id
    status_resp = await client.get(f"/api/v1/sos/{report_uuid}/status", headers=headers)
    print(f"DEBUG TEST: {status_resp.text}")
    assert status_resp.status_code == 200
    assert status_resp.json()["status"] == "pending_local"


# ---------------------------------------------------------------------------
# Test: GET /{uuid}/status for unknown UUID returns 404
# ---------------------------------------------------------------------------

async def test_get_sos_status_not_found(client: AsyncClient, device_token: str):
    headers = {"Authorization": f"Bearer {device_token}"}
    response = await client.get(f"/api/v1/sos/{uuid.uuid4()}/status", headers=headers)
    assert response.status_code == 404


# ---------------------------------------------------------------------------
# Regression Tests: Input Validation
# ---------------------------------------------------------------------------

async def test_sos_batch_invalid_uuid(client: AsyncClient, device_token: str):
    payload = make_batch_payload([make_sos_item("not-a-uuid")])
    headers = {"Authorization": f"Bearer {device_token}"}
    response = await client.post("/api/v1/sos/batch", json=payload, headers=headers)
    assert response.status_code == 422


async def test_sos_batch_invalid_location(client: AsyncClient, device_token: str):
    item = make_sos_item()
    item["location"]["lat"] = 100.0  # Invalid, max is 90
    payload = make_batch_payload([item])
    headers = {"Authorization": f"Bearer {device_token}"}
    response = await client.post("/api/v1/sos/batch", json=payload, headers=headers)
    assert response.status_code == 422


async def test_sos_batch_negative_people_count(client: AsyncClient, device_token: str):
    item = make_sos_item()
    item["people_count"] = -1
    payload = make_batch_payload([item])
    headers = {"Authorization": f"Bearer {device_token}"}
    response = await client.post("/api/v1/sos/batch", json=payload, headers=headers)
    assert response.status_code == 422


@pytest.mark.asyncio
async def test_sos_same_batch_deduplication(client: AsyncClient, device_token: str, cleanup_sos_data):
    # Test A: Two different UUIDs in SAME batch, same location, within time window -> one accepted, one duplicate
    uuid1 = str(uuid.uuid4())
    uuid2 = str(uuid.uuid4())
    item1 = make_sos_item(uuid1, lat=10.0, lng=20.0)
    item2 = make_sos_item(uuid2, lat=10.0, lng=20.0)
    payload_a = make_batch_payload([item1, item2])
    
    headers = {"Authorization": f"Bearer {device_token}"}
    res_a = await client.post("/api/v1/sos/batch", json=payload_a, headers=headers)
    assert res_a.status_code == 202
    assert len(res_a.json()["accepted_uuids"]) == 1
    assert len(res_a.json()["duplicate_uuids"]) == 1

    # Test B: Two different UUIDs in SAME batch, same location, outside time window -> both accepted
    uuid3 = str(uuid.uuid4())
    uuid4 = str(uuid.uuid4())
    item3 = make_sos_item(uuid3, lat=11.0, lng=21.0)
    dt_future = datetime.datetime.now(datetime.timezone.utc) + datetime.timedelta(minutes=30)
    item4 = make_sos_item(uuid4, lat=11.0, lng=21.0, created_at=dt_future.isoformat())
    payload_b = make_batch_payload([item3, item4])
    
    res_b = await client.post("/api/v1/sos/batch", json=payload_b, headers=headers)
    assert len(res_b.json()["accepted_uuids"]) == 2
    assert len(res_b.json()["duplicate_uuids"]) == 0

    # Test C: Two different UUIDs in SAME batch, outside spatial distance -> both accepted
    uuid5 = str(uuid.uuid4())
    uuid6 = str(uuid.uuid4())
    item5 = make_sos_item(uuid5, lat=12.0, lng=22.0)
    item6 = make_sos_item(uuid6, lat=12.1, lng=22.1) # far away
    payload_c = make_batch_payload([item5, item6])
    
    res_c = await client.post("/api/v1/sos/batch", json=payload_c, headers=headers)
    assert len(res_c.json()["accepted_uuids"]) == 2
    assert len(res_c.json()["duplicate_uuids"]) == 0

    # Test D: Exact UUID duplicate in SAME batch -> one accepted, one duplicate
    uuid7 = str(uuid.uuid4())
    item7a = make_sos_item(uuid7, lat=13.0, lng=23.0)
    item7b = make_sos_item(uuid7, lat=13.0, lng=23.0)
    payload_d = make_batch_payload([item7a, item7b])
    
    res_d = await client.post("/api/v1/sos/batch", json=payload_d, headers=headers)
    assert len(res_d.json()["accepted_uuids"]) == 1
    assert len(res_d.json()["duplicate_uuids"]) == 1
