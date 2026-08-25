import pytest
import datetime
import uuid
from httpx import AsyncClient
from sqlalchemy import select

from app.db.engine import AsyncSessionLocal
from app.db._models.incident import Incident
from app.db._models.sos import SOSReport

# Import the fixture from test_sos (it should really be in conftest, but we can import it or recreate it)
from tests.test_sos import device_token, make_batch_payload, make_sos_item

pytestmark = pytest.mark.asyncio

async def test_sos_pipeline_creates_incident(client: AsyncClient, device_token: str,
                                              cleanup_sos_data, cleanup_phase4_data):
    """
    Test that uploading an SOS batch triggers the pipeline, creating a new Incident
    in the database representing the clustered SOS reports.
    """
    # 1. Create a batch of two SOS reports near each other
    uuid1 = str(uuid.uuid4())
    uuid2 = str(uuid.uuid4())
    
    
    item1 = make_sos_item(uuid1, lat=10.0, lng=20.0)
    
    # Set item2 20 minutes later so it doesn't get dropped as a duplicate,
    # but keep it spatially close (0.0001 degrees ~ 11m) so DBSCAN clusters them together
    dt2 = datetime.datetime.now(datetime.timezone.utc) + datetime.timedelta(minutes=20)
    item2 = make_sos_item(uuid2, lat=10.0001, lng=20.0001, created_at=dt2.isoformat())
    
    # One has critical hint, the other high
    item1["severity_hint"] = "critical"
    item1["people_count"] = 2
    item2["severity_hint"] = "high"
    item2["people_count"] = 5
    
    payload = make_batch_payload([item1, item2])
    headers = {"Authorization": f"Bearer {device_token}"}
    
    # 2. Upload batch
    response = await client.post("/api/v1/sos/batch", json=payload, headers=headers)
    assert response.status_code == 202
    data = response.json()
    assert len(data["accepted_uuids"]) == 2
    assert len(data["duplicate_uuids"]) == 0
    
    # 3. Verify Incident was created via Pipeline
    async with AsyncSessionLocal() as db:
        # Check SOS Reports exist and have a cluster_id
        res_sos = await db.execute(select(SOSReport).where(SOSReport.uuid.in_([uuid1, uuid2])))
        sos_reports = res_sos.scalars().all()
        assert len(sos_reports) == 2
        
        cid1 = sos_reports[0].cluster_id
        cid2 = sos_reports[1].cluster_id
        assert cid1 is not None
        assert cid1 == cid2  # They should be clustered together
        
        # Check Incident exists for this cluster
        res_inc = await db.execute(select(Incident).where(Incident.cluster_id == cid1))
        incident = res_inc.scalar_one_or_none()
        
        assert incident is not None
        assert incident.report_count == 2
        assert len(incident.source_sos_uuids) == 2
        assert incident.severity == "high"  # Max severity hint is capped because objective facts don't support critical
        assert incident.estimated_people_affected == 5  # Max people count
        assert incident.status == "new"
        assert incident.first_reported_at is not None
        assert incident.last_updated_at is not None
