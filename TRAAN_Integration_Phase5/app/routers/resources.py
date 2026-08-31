from typing import List, Optional, Any
from datetime import datetime
import uuid
import logging

from fastapi import APIRouter, Depends, HTTPException, Query, status
from sqlalchemy.ext.asyncio import AsyncSession
from sqlalchemy import select

from app.db.engine import get_db
from app.db._models.resource import Resource
from app.models.resources import ResourceCreate, ResourceUpdate, ResourceResponse
from app.core.security import require_authority_jwt
from app.ws.manager import manager as ws_manager

logger = logging.getLogger(__name__)
router = APIRouter()

@router.get("", response_model=List[ResourceResponse])
async def get_resources(
    category: Optional[str] = None,
    status: Optional[str] = None,
    district: Optional[str] = None,
    db: AsyncSession = Depends(get_db),
    auth: dict[str, Any] = Depends(require_authority_jwt)
):
    """
    List resources with optional filtering.
    """
    stmt = select(Resource)
    if category:
        stmt = stmt.where(Resource.category == category)
    if status:
        stmt = stmt.where(Resource.status == status)
    if district:
        stmt = stmt.where(Resource.district == district)
        
    result = await db.execute(stmt)
    resources = result.scalars().all()
    
    # We must transform the geometry to lat/lng for output.
    # We can do this efficiently here or rely on properties if we add them,
    # but for simplicity, we map it manually or query ST_X/ST_Y.
    # Since PostGIS is involved, doing it in the query is better.
    # For now, let's just do a DB-side cast to get lat/lng.
    from sqlalchemy import func
    stmt_geom = select(
        Resource,
        func.ST_Y(Resource.location).label("lat"),
        func.ST_X(Resource.location).label("lng")
    )
    if category:
        stmt_geom = stmt_geom.where(Resource.category == category)
    if status:
        stmt_geom = stmt_geom.where(Resource.status == status)
    if district:
        stmt_geom = stmt_geom.where(Resource.district == district)
        
    result = await db.execute(stmt_geom)
    
    res_out = []
    for row in result:
        res_obj = row[0]
        lat = row[1]
        lng = row[2]
        r = ResourceResponse(
            resource_id=res_obj.resource_id,
            category=res_obj.category,
            sub_type=res_obj.sub_type,
            custodian_agency=res_obj.custodian_agency,
            quantity_total=res_obj.quantity_total,
            quantity_available=res_obj.quantity_available,
            status=res_obj.status,
            contact=res_obj.contact,
            last_updated_at=res_obj.last_updated_at,
            location={"lat": lat, "lng": lng, "district": res_obj.district}
        )
        res_out.append(r)
        
    return res_out

@router.get("/{resource_id}", response_model=ResourceResponse)
async def get_resource(
    resource_id: str,
    db: AsyncSession = Depends(get_db),
    auth: dict[str, Any] = Depends(require_authority_jwt)
):
    from sqlalchemy import func
    stmt_geom = select(
        Resource,
        func.ST_Y(Resource.location).label("lat"),
        func.ST_X(Resource.location).label("lng")
    ).where(Resource.resource_id == resource_id)
    
    result = await db.execute(stmt_geom)
    row = result.first()
    if not row:
        raise HTTPException(status_code=404, detail="Resource not found")
        
    res_obj = row[0]
    lat = row[1]
    lng = row[2]
    
    return ResourceResponse(
        resource_id=res_obj.resource_id,
        category=res_obj.category,
        sub_type=res_obj.sub_type,
        custodian_agency=res_obj.custodian_agency,
        quantity_total=res_obj.quantity_total,
        quantity_available=res_obj.quantity_available,
        status=res_obj.status,
        contact=res_obj.contact,
        last_updated_at=res_obj.last_updated_at,
        location={"lat": lat, "lng": lng, "district": res_obj.district}
    )

@router.post("", response_model=ResourceResponse, status_code=status.HTTP_201_CREATED)
async def create_resource(
    resource: ResourceCreate,
    db: AsyncSession = Depends(get_db),
    auth: dict[str, Any] = Depends(require_authority_jwt)
):
    """
    Create a new mock IDRN resource. Admin only.
    """
    if auth.get("role") != "admin":
        raise HTTPException(status_code=403, detail="Admin role required to create resources")
        
    resource_id = str(uuid.uuid4())
    
    db_resource = Resource(
        resource_id=resource_id,
        category=resource.category,
        sub_type=resource.sub_type,
        custodian_agency=resource.custodian_agency,
        quantity_total=resource.quantity_total,
        quantity_available=resource.quantity_available,
        status=resource.status,
        contact=resource.contact,
        location=f"SRID=4326;POINT({resource.location.lng} {resource.location.lat})",
        district=resource.location.district,
        last_updated_at=datetime.utcnow()
    )
    db.add(db_resource)
    await db.commit()
    
    # Broadcast event
    r = ResourceResponse(
        resource_id=resource_id,
        category=resource.category,
        sub_type=resource.sub_type,
        custodian_agency=resource.custodian_agency,
        quantity_total=resource.quantity_total,
        quantity_available=resource.quantity_available,
        status=resource.status,
        contact=resource.contact,
        last_updated_at=db_resource.last_updated_at,
        location=resource.location
    )
    await ws_manager.broadcast_event("resource_updated", r)
    return r

@router.patch("/{resource_id}", response_model=ResourceResponse)
async def update_resource(
    resource_id: str,
    update_data: ResourceUpdate,
    db: AsyncSession = Depends(get_db),
    auth: dict[str, Any] = Depends(require_authority_jwt)
):
    """
    Update a resource (e.g. quantity or status). Authority only.
    """
    stmt = select(Resource).where(Resource.resource_id == resource_id).with_for_update()
    result = await db.execute(stmt)
    db_resource = result.scalars().first()
    
    if not db_resource:
        raise HTTPException(status_code=404, detail="Resource not found")
        
    if update_data.quantity_available is not None:
        if update_data.quantity_available < 0:
            raise HTTPException(status_code=400, detail="quantity_available cannot be negative")
        if update_data.quantity_available > db_resource.quantity_total:
            raise HTTPException(status_code=400, detail="quantity_available cannot exceed quantity_total")
        db_resource.quantity_available = update_data.quantity_available
    if update_data.status is not None:
        db_resource.status = update_data.status
        
    db_resource.last_updated_at = datetime.utcnow()
    await db.commit()
    
    # Refetch with geometry
    from sqlalchemy import func
    stmt_geom = select(
        Resource,
        func.ST_Y(Resource.location).label("lat"),
        func.ST_X(Resource.location).label("lng")
    ).where(Resource.resource_id == resource_id)
    
    result = await db.execute(stmt_geom)
    row = result.first()
    res_obj = row[0]
    lat = row[1]
    lng = row[2]
    
    r = ResourceResponse(
        resource_id=res_obj.resource_id,
        category=res_obj.category,
        sub_type=res_obj.sub_type,
        custodian_agency=res_obj.custodian_agency,
        quantity_total=res_obj.quantity_total,
        quantity_available=res_obj.quantity_available,
        status=res_obj.status,
        contact=res_obj.contact,
        last_updated_at=res_obj.last_updated_at,
        location={"lat": lat, "lng": lng, "district": res_obj.district}
    )
    
    await ws_manager.broadcast_event("resource_updated", r)
    
    return r
