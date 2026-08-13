from typing import List, Optional, Any
from datetime import datetime
import uuid
import logging

from fastapi import APIRouter, Depends, HTTPException, Query, status
from sqlalchemy.ext.asyncio import AsyncSession
from sqlalchemy import select, func

from app.db.engine import get_db
from app.db._models.incident import Incident
from app.db._models.resource import Resource
from app.db._models.dispatch import DispatchRecord
from app.models.incidents import (
    IncidentResponse, 
    IncidentStatusUpdate, 
    DispatchRequest, 
    DispatchResponse, 
    Recommendation,
    PaginatedIncidentsResponse,
)
from app.core.security import require_authority_jwt
from app.ws.manager import manager as ws_manager

logger = logging.getLogger(__name__)
router = APIRouter()

async def _build_incident_response(row, db: AsyncSession) -> IncidentResponse:
    inc_obj = row[0]
    lat = row[1]
    lng = row[2]
    
    from app.db._models.sos import SOSReport
    stmt = select(
        SOSReport,
        func.ST_Y(SOSReport.location).label("lat"),
        func.ST_X(SOSReport.location).label("lng")
    ).where(SOSReport.uuid.in_(inc_obj.source_sos_uuids))
    
    res = await db.execute(stmt)
    sos_rows = res.all()
    
    sos_reports = []
    for s_row in sos_rows:
        s_obj = s_row[0]
        s_dict = {
            "uuid": s_obj.uuid,
            "device_id": s_obj.device_id,
            "created_at": s_obj.created_at,
            "location": {"lat": s_row[1], "lng": s_row[2], "accuracy_m": s_obj.accuracy_m},
            "is_quick_sos": s_obj.is_quick_sos,
            "emergency_type": s_obj.emergency_type,
            "severity_hint": s_obj.severity_hint,
            "people_count": s_obj.people_count,
            "medical_snapshot": s_obj.medical_snapshot,
            "custom_message": s_obj.custom_message,
            "contact_number": s_obj.contact_number,
            "relay_hop_count": s_obj.relay_hop_count,
            "last_relayed_at": s_obj.last_relayed_at,
            "status": s_obj.status
        }
        sos_reports.append(s_dict)
        
    return IncidentResponse(
        incident_id=inc_obj.incident_id,
        cluster_id=inc_obj.cluster_id,
        source_sos_reports=sos_reports,
        location={"lat": lat, "lng": lng, "accuracy_m": None},
        area_name=inc_obj.area_name,
        emergency_types=inc_obj.emergency_types,
        severity=inc_obj.severity,
        ai_summary=inc_obj.ai_summary,
        report_count=inc_obj.report_count,
        estimated_people_affected=inc_obj.estimated_people_affected,
        flags=inc_obj.flags,
        first_reported_at=inc_obj.first_reported_at,
        last_updated_at=inc_obj.last_updated_at,
        status=inc_obj.status,
        recommended_resources=inc_obj.recommended_resources,
        assigned_resources=inc_obj.assigned_resources
    )

@router.get("", response_model=PaginatedIncidentsResponse)
async def list_incidents(
    bbox: Optional[str] = None,
    severity: Optional[str] = None,
    status: Optional[str] = None,
    since: Optional[datetime] = None,
    page: int = 1,
    size: int = 10,
    db: AsyncSession = Depends(get_db),
    auth: dict[str, Any] = Depends(require_authority_jwt)
):
    stmt = select(
        Incident,
        func.ST_Y(Incident.location).label("lat"),
        func.ST_X(Incident.location).label("lng")
    )
    
    if severity:
        stmt = stmt.where(Incident.severity == severity)
    if status:
        stmt = stmt.where(Incident.status == status)
    if since:
        stmt = stmt.where(Incident.last_updated_at >= since)
    if bbox:
        # Simplistic bbox parsing: min_lng,min_lat,max_lng,max_lat
        try:
            parts = [float(p) for p in bbox.split(',')]
            if len(parts) == 4:
                minx, miny, maxx, maxy = parts
                bbox_geom = f"SRID=4326;POLYGON(({minx} {miny}, {minx} {maxy}, {maxx} {maxy}, {maxx} {miny}, {minx} {miny}))"
                stmt = stmt.where(func.ST_Intersects(Incident.location, bbox_geom))
        except ValueError:
            pass
            
    total_result = await db.execute(select(func.count()).select_from(stmt.subquery()))
    total = total_result.scalar() or 0
    
    stmt = stmt.offset((page - 1) * size).limit(size)
    result = await db.execute(stmt)
    items = [await _build_incident_response(row, db) for row in result.all()]
    
    return PaginatedIncidentsResponse(
        items=items,
        total=total,
        page=page,
        size=size
    )

@router.get("/{incident_id}", response_model=IncidentResponse)
async def get_incident(
    incident_id: str,
    db: AsyncSession = Depends(get_db),
    auth: dict[str, Any] = Depends(require_authority_jwt)
):
    stmt = select(
        Incident,
        func.ST_Y(Incident.location).label("lat"),
        func.ST_X(Incident.location).label("lng")
    ).where(Incident.incident_id == incident_id)
    
    result = await db.execute(stmt)
    row = result.first()
    if not row:
        raise HTTPException(status_code=404, detail="Incident not found")
    return await _build_incident_response(row, db)

@router.patch("/{incident_id}/status", response_model=IncidentResponse)
async def update_incident(
    incident_id: str,
    update_data: IncidentStatusUpdate,
    db: AsyncSession = Depends(get_db),
    auth: dict[str, Any] = Depends(require_authority_jwt)
):
    stmt = select(Incident).where(Incident.incident_id == incident_id)
    result = await db.execute(stmt)
    inc = result.scalars().first()
    if not inc:
        raise HTTPException(status_code=404, detail="Incident not found")
        
    allowed_statuses = {"new", "acknowledged", "dispatched", "resolved"}
    if update_data.status not in allowed_statuses:
        raise HTTPException(status_code=400, detail="Invalid incident status")
        
    inc.status = update_data.status
    inc.last_updated_at = datetime.utcnow()
    await db.commit()
    
    # Broadcast
    stmt_geom = select(
        Incident,
        func.ST_Y(Incident.location).label("lat"),
        func.ST_X(Incident.location).label("lng")
    ).where(Incident.incident_id == incident_id)
    row = (await db.execute(stmt_geom)).first()
    resp = await _build_incident_response(row, db)
    await ws_manager.broadcast_event("incident_updated", resp)
    return resp

@router.get("/{incident_id}/recommendations")
async def get_recommendations(
    incident_id: str,
    db: AsyncSession = Depends(get_db),
    auth: dict[str, Any] = Depends(require_authority_jwt)
):
    # This is an AI/Optimizer integration boundary.
    # We return what's in the DB if present, or an empty list.
    stmt = select(Incident).where(Incident.incident_id == incident_id)
    result = await db.execute(stmt)
    inc = result.scalars().first()
    if not inc:
        raise HTTPException(status_code=404, detail="Incident not found")
        
    return inc.recommended_resources or []

@router.post("/{incident_id}/dispatch", response_model=DispatchResponse, status_code=status.HTTP_201_CREATED)
async def create_dispatch(
    incident_id: str,
    dispatch_req: DispatchRequest,
    db: AsyncSession = Depends(get_db),
    auth: dict[str, Any] = Depends(require_authority_jwt)
):
    # Verify Incident
    stmt_inc = select(Incident).where(Incident.incident_id == incident_id)
    inc = (await db.execute(stmt_inc)).scalars().first()
    if not inc:
        raise HTTPException(status_code=404, detail="Incident not found")
        
    # Verify Resource
    stmt_res = select(Resource).where(Resource.resource_id == dispatch_req.resource_id).with_for_update()
    res = (await db.execute(stmt_res)).scalars().first()
    if not res:
        raise HTTPException(status_code=404, detail="Resource not found")
        
    if dispatch_req.quantity <= 0:
        raise HTTPException(status_code=400, detail="Quantity must be greater than zero")
        
    if res.quantity_available < dispatch_req.quantity:
        raise HTTPException(status_code=400, detail="Not enough resource quantity available")
        
    # Update Resource
    res.quantity_available -= dispatch_req.quantity
    res.status = "partially_deployed" if res.quantity_available > 0 else "deployed"
    res.last_updated_at = datetime.utcnow()
    
    # Create DispatchRecord
    dispatch_id = str(uuid.uuid4())
    dr = DispatchRecord(
        dispatch_id=dispatch_id,
        incident_id=incident_id,
        resource_id=res.resource_id,
        quantity_dispatched=dispatch_req.quantity,
        dispatched_by=auth.get("id"),
        dispatched_at=datetime.utcnow(),
        status="dispatched"
    )
    db.add(dr)
    
    # Update Incident
    assigned = list(inc.assigned_resources) if inc.assigned_resources else []
    if res.resource_id not in assigned:
        assigned.append(res.resource_id)
    inc.assigned_resources = assigned
    if inc.status == "new" or inc.status == "acknowledged":
        inc.status = "dispatched"
    inc.last_updated_at = datetime.utcnow()
    
    await db.commit()
    
    resp = DispatchResponse(
        dispatch_id=dr.dispatch_id,
        incident_id=dr.incident_id,
        resource_id=dr.resource_id,
        quantity_dispatched=dr.quantity_dispatched,
        dispatched_by=dr.dispatched_by,
        dispatched_at=dr.dispatched_at,
        eta_minutes=dr.eta_minutes,
        status=dr.status
    )
    
    await ws_manager.broadcast_event("incident_dispatched", resp)
    
    # Broadcast updated resource and incident
    # We do this asynchronously but wait for completion
    stmt_res_geom = select(Resource, func.ST_Y(Resource.location), func.ST_X(Resource.location)).where(Resource.resource_id == res.resource_id)
    r_row = (await db.execute(stmt_res_geom)).first()
    from app.models.resources import ResourceResponse
    r_resp = ResourceResponse(
        resource_id=r_row[0].resource_id,
        category=r_row[0].category,
        sub_type=r_row[0].sub_type,
        custodian_agency=r_row[0].custodian_agency,
        quantity_total=r_row[0].quantity_total,
        quantity_available=r_row[0].quantity_available,
        status=r_row[0].status,
        contact=r_row[0].contact,
        last_updated_at=r_row[0].last_updated_at,
        location={"lat": r_row[1], "lng": r_row[2], "district": r_row[0].district}
    )
    await ws_manager.broadcast_event("resource_updated", r_resp)
    
    stmt_inc_geom = select(Incident, func.ST_Y(Incident.location), func.ST_X(Incident.location)).where(Incident.incident_id == incident_id)
    i_row = (await db.execute(stmt_inc_geom)).first()
    i_resp = await _build_incident_response(i_row, db)
    await ws_manager.broadcast_event("incident_updated", i_resp)
    
    return resp

@router.post("/{incident_id}/refresh-summary")
async def refresh_summary(
    incident_id: str,
    db: AsyncSession = Depends(get_db),
    auth: dict[str, Any] = Depends(require_authority_jwt)
):
    """
    Integration boundary stub for AI-based summary refresh.
    Component E is responsible for the actual implementation.
    """
    stmt = select(Incident).where(Incident.incident_id == incident_id)
    inc = (await db.execute(stmt)).scalars().first()
    if not inc:
        raise HTTPException(status_code=404, detail="Incident not found")
        
    # STUB: Ideally we would queue a task or trigger Component E logic here.
    return {"status": "refresh_queued"}
