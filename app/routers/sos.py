from fastapi import APIRouter, Depends, HTTPException, status
from sqlalchemy.ext.asyncio import AsyncSession
from sqlalchemy import select, text
from sqlalchemy.exc import IntegrityError
import datetime

from app.db.engine import get_db
from app.models.sos import BatchUpload, BatchResponse, SOSStatusResponse
from app.db._models.sos import SOSReport
from app.core.security import require_device_jwt
from app.config import settings
from app.services.incident_pipeline import process_sos_clusters

router = APIRouter(
    prefix="/sos",
    tags=["sos"]
)


@router.post("/batch", response_model=BatchResponse, status_code=status.HTTP_202_ACCEPTED)
async def upload_sos_batch(
    payload: BatchUpload,
    db: AsyncSession = Depends(get_db),
    device_id: str = Depends(require_device_jwt)
):
    if device_id != payload.gateway_device_id:
        raise HTTPException(
            status_code=status.HTTP_403_FORBIDDEN,
            detail="Device identity mismatch"
        )
    # Prepare results
    accepted_uuids = []
    duplicate_uuids = []

    if not payload.sos_batch:
        return BatchResponse(accepted_uuids=[], duplicate_uuids=[])

    # 1. Exact UUID Match
    incoming_uuids = [report.uuid for report in payload.sos_batch]
    result = await db.execute(
        select(SOSReport.uuid).where(SOSReport.uuid.in_(incoming_uuids))
    )
    existing_uuids = set(result.scalars().all())

    # 2. Process non-exact duplicates for spatial/time near-duplicate detection
    reports_to_insert = []
    
    # We will use raw SQL for the near-duplicate ST_DWithin check to ensure we cast to geography correctly
    near_dup_query = text("""
        SELECT uuid 
        FROM sos_report 
        WHERE ST_DWithin(
            location::geography, 
            ST_SetSRID(ST_MakePoint(:lng, :lat), 4326)::geography, 
            :dist_m
        )
        AND uuid != :incoming_uuid
        AND ABS(EXTRACT(EPOCH FROM (created_at - :created_at))) <= :time_window_sec
        LIMIT 1
    """)

    for report in payload.sos_batch:
        if report.uuid in existing_uuids:
            duplicate_uuids.append(report.uuid)
            continue
            
        # Check near duplicate
        res = await db.execute(near_dup_query, {
            "lng": report.location.lng,
            "lat": report.location.lat,
            "dist_m": settings.DEDUPLICATION_DISTANCE_M,
            "incoming_uuid": report.uuid,
            "created_at": report.created_at,
            "time_window_sec": settings.DEDUPLICATION_TIME_WINDOW_MINUTES * 60
        })
        near_dup = res.scalar_one_or_none()
        
        if near_dup:
            # We consider this a duplicate
            duplicate_uuids.append(report.uuid)
            continue
            
        # Accept the report
        accepted_uuids.append(report.uuid)
        
        new_report = SOSReport(
            uuid=report.uuid,
            device_id=report.device_id,
            created_at=report.created_at,
            location=f"SRID=4326;POINT({report.location.lng} {report.location.lat})",
            accuracy_m=report.location.accuracy_m,
            is_quick_sos=report.is_quick_sos,
            emergency_type=report.emergency_type.value,
            severity_hint=report.severity_hint.value if report.severity_hint else None,
            people_count=report.people_count,
            medical_snapshot=report.medical_snapshot,
            custom_message=report.custom_message,
            contact_number=report.contact_number,
            relay_hop_count=report.relay_hop_count,
            last_relayed_at=report.last_relayed_at,
            status=report.status.value,
            received_at=datetime.datetime.now(datetime.timezone.utc),
            received_via_gateway_device_id=payload.gateway_device_id
        )
        db.add(new_report)
        await db.flush()

    if accepted_uuids:
        await db.commit()
        
        # 3. Clustering Execution
        # We execute ST_ClusterDBSCAN over the geometry. Since location is Geometry(Point, 4326),
        # ST_ClusterDBSCAN's eps unit is degrees.
        # We use the minimum UUID in each cluster as the stable cluster_id.
        cluster_query = text("""
            WITH clusters AS (
                SELECT uuid, ST_ClusterDBSCAN(location, eps := :eps, minpoints := :minpoints) OVER () as cid
                FROM sos_report
            ),
            cluster_uuids AS (
                SELECT cid, MIN(uuid) as stable_cluster_id
                FROM clusters
                WHERE cid IS NOT NULL
                GROUP BY cid
            )
            UPDATE sos_report 
            SET cluster_id = cu.stable_cluster_id
            FROM clusters c
            JOIN cluster_uuids cu ON c.cid = cu.cid
            WHERE sos_report.uuid = c.uuid;
        """)
        
        await db.execute(cluster_query, {
            "eps": settings.DBSCAN_EPS,
            "minpoints": settings.DBSCAN_MINPOINTS
        })
        await db.commit()
        
        # 4. SOS -> Incident Pipeline
        await process_sos_clusters(db, accepted_uuids)

    return BatchResponse(
        accepted_uuids=accepted_uuids,
        duplicate_uuids=duplicate_uuids
    )


@router.get("/{uuid}/status", response_model=SOSStatusResponse)
async def get_sos_status(
    uuid: str,
    db: AsyncSession = Depends(get_db),
    token_data: dict = Depends(require_device_jwt)
):
    result = await db.execute(
        select(SOSReport.status).where(SOSReport.uuid == uuid)
    )
    status_val = result.scalar_one_or_none()
    
    if not status_val:
        raise HTTPException(status_code=404, detail="SOS record not found")
        
    return SOSStatusResponse(status=status_val)
