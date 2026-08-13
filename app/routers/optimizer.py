from typing import List, Optional, Any, Dict
import logging
from fastapi import APIRouter, Depends, HTTPException, status
from pydantic import BaseModel
from sqlalchemy.ext.asyncio import AsyncSession
from app.db.engine import get_db
from app.core.security import require_authority_jwt
from app.ws.manager import manager as ws_manager
from datetime import datetime

logger = logging.getLogger(__name__)
router = APIRouter()

class AllocateRequest(BaseModel):
    incident_ids: Optional[List[str]] = None
    constraints: Optional[Dict[str, Any]] = None

@router.get("/situation-brief")
async def get_situation_brief(
    db: AsyncSession = Depends(get_db),
    auth: dict[str, Any] = Depends(require_authority_jwt)
):
    """
    STUB: Returns the current situation brief.
    Component E is responsible for the actual Gemini implementation.
    """
    return {"situation_brief": "AI Situation Brief Stub: 0 active incidents."}

@router.post("/situation-brief/refresh")
async def refresh_situation_brief(
    db: AsyncSession = Depends(get_db),
    auth: dict[str, Any] = Depends(require_authority_jwt)
):
    """
    STUB: Refreshes the situation brief.
    Component E is responsible for the actual Gemini implementation.
    """
    if auth.get("role") != "admin":
        raise HTTPException(
            status_code=status.HTTP_403_FORBIDDEN,
            detail="Admin privileges required"
        )
        
    brief_text = "AI Situation Brief Stub: 0 active incidents."
    updated_at = datetime.utcnow().isoformat()
    await ws_manager.broadcast_event("situation_brief_updated", {"text": brief_text, "updated_at": updated_at})
    
    return {"status": "refresh_queued"}

@router.post("/optimize/allocate")
async def allocate_resources(
    req: AllocateRequest,
    db: AsyncSession = Depends(get_db),
    auth: dict[str, Any] = Depends(require_authority_jwt)
):
    """
    STUB: Requests resource allocation.
    Component E is responsible for the actual OR-Tools implementation.
    """
    return []

