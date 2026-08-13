"""
Miscellaneous endpoints.

  GET /api/v1/health       — liveness + DB connectivity check
  GET /api/v1/stats/summary — (Phase 4) aggregated incident/resource counts
"""

import logging

from fastapi import APIRouter, Depends
from sqlalchemy import text
from sqlalchemy.ext.asyncio import AsyncSession
from sqlalchemy import select, func
from datetime import datetime, timedelta

from app.db.engine import get_db
from app.db._models.incident import Incident
from app.db._models.resource import Resource
from app.models.stats import StatsSummaryResponse, ActiveIncidents, ResourceStats

logger = logging.getLogger(__name__)

router = APIRouter()


# ── Health ─────────────────────────────────────────────────────────────────────

@router.get(
    "/health",
    summary="Health check",
    tags=["misc"],
    response_description="Service liveness and DB connectivity status",
)
async def health(db: AsyncSession = Depends(get_db)) -> dict:
    """
    Returns HTTP 200 when the API is running.
    Also verifies the database connection and reports PostGIS availability.

    Used by Docker healthchecks and monitoring tools.
    """
    db_status = "ok"
    postgis_version: str | None = None

    try:
        result = await db.execute(text("SELECT PostGIS_Version()"))
        postgis_version = result.scalar_one()
    except Exception as exc:
        logger.error("Health check DB error: %s", exc)
        db_status = "error"

    return {
        "status": "ok",
        "database": db_status,
        "postgis_version": postgis_version,
        "api_version": "0.1.0",
    }


# ── Stats summary (Phase 4) ───────────────────────────────────────

@router.get(
    "/stats/summary",
    response_model=StatsSummaryResponse,
    summary="Aggregated incident and resource statistics",
    tags=["misc"],
)
async def stats_summary(db: AsyncSession = Depends(get_db)):
    # Incidents by severity
    active_statuses = ["new", "acknowledged", "dispatched"]
    stmt_inc_sev = select(Incident.severity, func.count()).where(Incident.status.in_(active_statuses)).group_by(Incident.severity)
    res_inc_sev = await db.execute(stmt_inc_sev)
    severity_counts = {row[0]: row[1] for row in res_inc_sev}
    
    # Total affected
    stmt_affected = select(func.sum(Incident.estimated_people_affected)).where(Incident.status.in_(active_statuses))
    res_affected = await db.execute(stmt_affected)
    total_affected = res_affected.scalar() or 0
    
    # Resources
    stmt_res_avail = select(func.sum(Resource.quantity_available))
    res_avail = await db.execute(stmt_res_avail)
    total_available = res_avail.scalar() or 0
    
    stmt_res_dep = select(func.sum(Resource.quantity_total - Resource.quantity_available))
    res_dep = await db.execute(stmt_res_dep)
    total_deployed = res_dep.scalar() or 0
    
    # Recent incidents
    fifteen_mins_ago = datetime.utcnow() - timedelta(minutes=15)
    stmt_recent = select(func.count()).where(Incident.first_reported_at >= fifteen_mins_ago)
    res_recent = await db.execute(stmt_recent)
    recent_incidents = res_recent.scalar() or 0
    
    return StatsSummaryResponse(
        active_incidents=ActiveIncidents(
            critical=severity_counts.get("critical", 0),
            high=severity_counts.get("high", 0),
            medium=severity_counts.get("medium", 0),
            low=severity_counts.get("low", 0),
        ),
        total_estimated_people_affected=total_affected,
        resources=ResourceStats(
            available=total_available,
            deployed=total_deployed
        ),
        new_incidents_last_15min=recent_incidents
    )
