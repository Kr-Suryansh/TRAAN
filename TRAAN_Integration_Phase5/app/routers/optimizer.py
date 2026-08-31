from typing import List, Optional, Any, Dict
import logging
from fastapi import APIRouter, Depends, HTTPException, status
from pydantic import BaseModel
from sqlalchemy.ext.asyncio import AsyncSession
from sqlalchemy import select
from datetime import datetime

from app.db.engine import get_db
from app.core.security import require_authority_jwt
from app.ws.manager import manager as ws_manager

from app.db._models.incident import Incident as DBIncident
from app.db._models.resource import Resource as DBResource
from app.models.schemas import (
    Incident as PydanticIncident, 
    Resource as PydanticResource,
    Location, 
    SeverityEnum, 
    Flags, 
    RecommendedResource,
    ResourceCategory, 
    ResourceStatus
)
from app.services.ai.situation_brief import get_cached_situation_brief, refresh_situation_brief
from app.services.optimizer.allocator import optimize_allocations
import asyncio

logger = logging.getLogger(__name__)
router = APIRouter()

class AllocateRequest(BaseModel):
    incident_ids: Optional[List[str]] = None
    constraints: Optional[Dict[str, Any]] = None

async def _fetch_data_for_ai(db: AsyncSession, incident_ids: Optional[List[str]] = None):
    # Fetch incidents
    if incident_ids:
        inc_stmt = select(DBIncident).where(DBIncident.incident_id.in_(incident_ids), DBIncident.status != 'resolved')
    else:
        inc_stmt = select(DBIncident).where(DBIncident.status != 'resolved')
        
    inc_res = await db.execute(inc_stmt)
    db_incidents = inc_res.scalars().all()
    
    incidents = []
    for i in db_incidents:
        sev = SeverityEnum(i.severity) if i.severity else None
        flags_obj = Flags(**i.flags) if isinstance(i.flags, dict) else Flags()
        
        recs = []
        if isinstance(i.recommended_resources, list):
            for r in i.recommended_resources:
                if isinstance(r, dict):
                    recs.append(RecommendedResource(**r))
                    
        # Extract lat/lng from WKT for optimizer if possible, else 0.0
        # PostGIS ST_X/ST_Y is ideal but since we are doing this in Python and WKT looks like SRID=4326;POINT(lng lat)
        lat, lng = 0.0, 0.0
        if i.location and isinstance(i.location, str) and "POINT" in i.location:
            try:
                coords = i.location.split("POINT(")[1].split(")")[0].split()
                lng, lat = float(coords[0]), float(coords[1])
            except Exception:
                pass
        
        p_inc = PydanticIncident(
            incident_id=i.incident_id,
            cluster_id=i.cluster_id,
            source_sos_uuids=i.source_sos_uuids or [],
            location=Location(lat=lat, lng=lng),
            area_name=i.area_name or "",
            emergency_types=i.emergency_types or [],
            severity=sev,
            ai_summary=i.ai_summary or "",
            report_count=i.report_count or 0,
            estimated_people_affected=i.estimated_people_affected or 0,
            flags=flags_obj,
            first_reported_at=i.first_reported_at or datetime.utcnow(),
            last_updated_at=i.last_updated_at or datetime.utcnow(),
            status=i.status,
            recommended_resources=recs,
            assigned_resources=i.assigned_resources or []
        )
        incidents.append(p_inc)
        
    # Fetch resources
    res_stmt = select(DBResource)
    res_result = await db.execute(res_stmt)
    db_resources = res_result.scalars().all()
    
    resources = []
    for r in db_resources:
        lat, lng = 0.0, 0.0
        if r.location and isinstance(r.location, str) and "POINT" in r.location:
            try:
                coords = r.location.split("POINT(")[1].split(")")[0].split()
                lng, lat = float(coords[0]), float(coords[1])
            except Exception:
                pass
                
        p_res = PydanticResource(
            resource_id=r.resource_id,
            category=ResourceCategory(r.category),
            sub_type=r.sub_type,
            custodian_agency=r.custodian_agency,
            quantity_total=r.quantity_total,
            quantity_available=r.quantity_available,
            status=ResourceStatus(r.status),
            location=Location(lat=lat, lng=lng, district=r.district),
            contact=r.contact,
            last_updated_at=r.last_updated_at
        )
        resources.append(p_res)
        
    return incidents, resources

@router.get("/situation-brief")
async def get_situation_brief(
    db: AsyncSession = Depends(get_db),
    auth: dict[str, Any] = Depends(require_authority_jwt)
):
    cache = get_cached_situation_brief()
    return {
        "text": cache.brief_text,
        "updated_at": cache.generated_at.isoformat()
    }

@router.post("/situation-brief/refresh")
async def refresh_situation_brief_endpoint(
    db: AsyncSession = Depends(get_db),
    auth: dict[str, Any] = Depends(require_authority_jwt)
):
    if auth.get("role") != "admin":
        raise HTTPException(
            status_code=status.HTTP_403_FORBIDDEN,
            detail="Admin privileges required"
        )
        
    incidents, resources = await _fetch_data_for_ai(db)
    
    # Run sync AI refresh in a thread to avoid blocking the event loop
    loop = asyncio.get_event_loop()
    cache = await loop.run_in_executor(
        None, 
        lambda: refresh_situation_brief(incidents=incidents, resources=resources)
    )
    
    await ws_manager.broadcast_event(
        "situation_brief_updated", 
        {"text": cache.brief_text, "updated_at": cache.generated_at.isoformat()}
    )
    
    return {"status": "refresh_queued"}

@router.post("/optimize/allocate")
async def allocate_resources(
    req: AllocateRequest,
    db: AsyncSession = Depends(get_db),
    auth: dict[str, Any] = Depends(require_authority_jwt)
):
    incidents, resources = await _fetch_data_for_ai(db, req.incident_ids)
    
    # Run sync optimization in a thread
    loop = asyncio.get_event_loop()
    assignments = await loop.run_in_executor(
        None,
        lambda: optimize_allocations(incidents, resources, req.constraints)
    )
    
    return assignments


