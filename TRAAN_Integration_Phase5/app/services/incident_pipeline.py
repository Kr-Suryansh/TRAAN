import uuid
import datetime
import logging
from typing import List

from sqlalchemy.ext.asyncio import AsyncSession
from sqlalchemy import select, text, func

from app.db._models.sos import SOSReport
from app.db._models.incident import Incident
from app.db._models.resource import Resource as DBResource
from app.models.incidents import IncidentResponse
from app.ws.manager import manager as ws_manager
from app.services.ai.summarizer import generate_incident_summary
from app.services.optimizer.allocator import optimize_allocations
from app.models.schemas import (
    SOSRequest, 
    Location as SOSLocation,
    Resource as PydanticResource,
    ResourceCategory,
    ResourceStatus,
    Incident as PydanticIncident,
    SeverityEnum,
    Flags
)
import asyncio

logger = logging.getLogger(__name__)

# Severity ordering: lower index = more severe.
# Component D's deterministic severity is the protected floor.
# Gemini may escalate (move left) but must NEVER downgrade (move right).
_SEVERITY_ORDER: dict[str, int] = {
    "critical": 0,
    "high": 1,
    "medium": 2,
    "low": 3,
}

def _max_severity(sev_a: str, sev_b: str | None) -> str:
    """Return the more severe of two severity strings.

    Uses the project's existing severity enum values.
    If sev_b is None or unrecognised, sev_a is returned unchanged.
    Neither argument is mutated.
    """
    if not sev_b or sev_b not in _SEVERITY_ORDER:
        return sev_a
    if not sev_a or sev_a not in _SEVERITY_ORDER:
        return sev_b
    # The severity with the LOWER index number is MORE severe.
    return sev_a if _SEVERITY_ORDER[sev_a] <= _SEVERITY_ORDER[sev_b] else sev_b

async def process_sos_clusters(db: AsyncSession, modified_uuids: List[str]):
    """
    Given a list of SOSReport UUIDs that were just inserted/updated and clustered,
    find their distinct cluster_ids, aggregate the data, and UPSERT the Incident table.
    """
    if not modified_uuids:
        return

    # 1. Find all distinct cluster_ids for these UUIDs
    cluster_query = select(SOSReport.cluster_id).where(
        SOSReport.uuid.in_(modified_uuids),
        SOSReport.cluster_id.isnot(None)
    ).distinct()
    
    result = await db.execute(cluster_query)
    cluster_ids = result.scalars().all()

    if not cluster_ids:
        return

    # 2. Process each cluster
    for cid in cluster_ids:
        # Get all SOSReports for this cluster
        reports_query = select(
            SOSReport,
            func.ST_Y(SOSReport.location).label("lat"),
            func.ST_X(SOSReport.location).label("lng")
        ).where(SOSReport.cluster_id == cid)
        
        reports_result = await db.execute(reports_query)
        reports_rows = reports_result.all()
        
        if not reports_rows:
            continue
            
        reports = [row[0] for row in reports_rows]
        
        source_sos_uuids = [r.uuid for r in reports]
        emergency_types = list(set(r.emergency_type for r in reports if r.emergency_type))
        first_reported_at = min(r.created_at for r in reports)
        last_updated_at = max(r.created_at for r in reports)
        report_count = len(reports)
        
        people_counts = [r.people_count for r in reports if r.people_count is not None]
        estimated_people_affected = max(people_counts) if people_counts else None
        
        # Derive severity from structured characteristics
        has_structural_collapse = any(r.emergency_type == 'structural_collapse' for r in reports)
        is_trapped = any(r.emergency_type == 'trapped' for r in reports)
        
        has_vulnerable_medical = False
        for r in reports:
            if r.medical_snapshot and isinstance(r.medical_snapshot, dict):
                age = r.medical_snapshot.get('age')
                if age is not None and (age < 10 or age > 65):
                    has_vulnerable_medical = True

        objective_severity = "low"
        if estimated_people_affected and estimated_people_affected > 0:
            objective_severity = "medium"
        if estimated_people_affected and estimated_people_affected > 20:
            objective_severity = "high"
            
        if has_structural_collapse:
            objective_severity = _max_severity(objective_severity, "high")
            if is_trapped or (estimated_people_affected and estimated_people_affected > 5):
                objective_severity = _max_severity(objective_severity, "critical")
                
        if has_vulnerable_medical:
            objective_severity = _max_severity(objective_severity, "high")
            
        hints = [r.severity_hint for r in reports if r.severity_hint]
        hint_severity = "low"
        if "critical" in hints:
            hint_severity = "critical"
        elif "high" in hints:
            hint_severity = "high"
        elif "medium" in hints:
            hint_severity = "medium"
            
        # Ensure severity_hint is not the sole determinant for 'critical'
        if hint_severity == "critical" and _SEVERITY_ORDER[objective_severity] > _SEVERITY_ORDER["high"]:
            hint_severity = "high"
            
        severity = _max_severity(objective_severity, hint_severity)
            
        # Get centroid
        centroid_query = text("""
            SELECT ST_Y(ST_Centroid(ST_Collect(location))) as lat, 
                   ST_X(ST_Centroid(ST_Collect(location))) as lng 
            FROM sos_report 
            WHERE cluster_id = :cid
        """)
        centroid_res = await db.execute(centroid_query, {"cid": cid})
        centroid = centroid_res.first()
        lat, lng = centroid.lat, centroid.lng

        # 3. Check if Incident already exists
        incident_query = select(Incident).where(Incident.cluster_id == cid)
        incident_result = await db.execute(incident_query)
        existing_incident = incident_result.scalar_one_or_none()

        is_new = False
        if existing_incident:
            # Update existing
            existing_incident.source_sos_uuids = source_sos_uuids
            existing_incident.location = f"SRID=4326;POINT({lng} {lat})"
            existing_incident.emergency_types = emergency_types
            existing_incident.severity = severity
            existing_incident.report_count = report_count
            existing_incident.estimated_people_affected = estimated_people_affected
            existing_incident.first_reported_at = first_reported_at
            existing_incident.last_updated_at = last_updated_at
            incident_to_broadcast = existing_incident
        else:
            # Create new
            is_new = True
            new_incident = Incident(
                incident_id=str(uuid.uuid4()),
                cluster_id=cid,
                source_sos_uuids=source_sos_uuids,
                location=f"SRID=4326;POINT({lng} {lat})",
                area_name=None,
                emergency_types=emergency_types,
                severity=severity,
                ai_summary=None,
                report_count=report_count,
                estimated_people_affected=estimated_people_affected,
                flags={
                    "medical_emergency": False,
                    "trapped": False,
                    "elderly_or_children": False,
                    "structural_damage": False
                },
                first_reported_at=first_reported_at,
                last_updated_at=last_updated_at,
                status="new",
                recommended_resources=[],
                assigned_resources=[]
            )
            db.add(new_incident)
            incident_to_broadcast = new_incident

        await db.commit()
        await db.refresh(incident_to_broadcast)

        # --- AI ENRICHMENT STEP (POST-COMMIT) ---
        # The incident is successfully persisted. Now we attempt AI enrichment.
        # IMPORTANT: Capture D's deterministic severity BEFORE calling Gemini.
        # This is the protected floor — Gemini may escalate it but must never downgrade it.
        d_severity = incident_to_broadcast.severity
        try:
            # Map reports to Pydantic SOSRequest schema for AI
            pydantic_reports = []
            for r in reports:
                r_row = next((row for row in reports_rows if row[0].uuid == r.uuid), None)
                r_lat = r_row[1] if r_row else 0.0
                r_lng = r_row[2] if r_row else 0.0
                req = SOSRequest(
                    uuid=str(r.uuid),
                    device_id=r.device_id,
                    created_at=r.created_at,
                    location=SOSLocation(lat=r_lat, lng=r_lng, accuracy_m=r.accuracy_m),
                    is_quick_sos=r.is_quick_sos,
                    emergency_type=r.emergency_type,
                    severity_hint=r.severity_hint,
                    people_count=r.people_count,
                    medical_snapshot=r.medical_snapshot,
                    custom_message=r.custom_message,
                    contact_number=r.contact_number,
                    relay_hop_count=r.relay_hop_count,
                    last_relayed_at=r.last_relayed_at,
                    status=r.status
                )
                pydantic_reports.append(req)
                
            loop = asyncio.get_event_loop()
            ai_result = await loop.run_in_executor(
                None,
                lambda: generate_incident_summary(pydantic_reports)
            )
            
            if ai_result.get("ai_success", False):
                incident_to_broadcast.ai_summary = ai_result.get("ai_summary")
                incident_to_broadcast.flags = ai_result.get("flags", incident_to_broadcast.flags)
                incident_to_broadcast.estimated_people_affected = ai_result.get("estimated_people_affected")
                
                # OPTION A — Max-severity preservation (approved 2026-08-21):
                # Gemini may escalate D's severity but must NEVER downgrade it.
                # _max_severity returns the more severe of the two values.
                gemini_sev = ai_result.get("severity")
                incident_to_broadcast.severity = _max_severity(d_severity, gemini_sev)
                    
                await db.commit()
                await db.refresh(incident_to_broadcast)
        except Exception as e:
            logger.error(f"AI enrichment failed for incident {incident_to_broadcast.incident_id}: {e}")
            # D's severity (d_severity) is already committed and remains unchanged.

        # --- OPTIMIZER STEP (RECOMMENDATIONS) ---
        try:
            # Fetch all resources to pass to optimizer
            res_stmt = select(DBResource, func.ST_Y(DBResource.location).label("lat"), func.ST_X(DBResource.location).label("lng"))
            res_result = await db.execute(res_stmt)
            db_resources = res_result.all()
            
            p_resources = []
            for row in db_resources:
                r = row[0]
                r_lat = row.lat if row.lat is not None else 0.0
                r_lng = row.lng if row.lng is not None else 0.0
                        
                p_res = PydanticResource(
                    resource_id=r.resource_id,
                    category=ResourceCategory(r.category),
                    sub_type=r.sub_type,
                    custodian_agency=r.custodian_agency,
                    quantity_total=r.quantity_total,
                    quantity_available=r.quantity_available,
                    status=ResourceStatus(r.status),
                    location=SOSLocation(lat=r_lat, lng=r_lng, district=r.district),
                    contact=r.contact,
                    last_updated_at=r.last_updated_at
                )
                p_resources.append(p_res)
                
            # Create PydanticIncident representation
            p_inc_sev = SeverityEnum(incident_to_broadcast.severity) if incident_to_broadcast.severity else None
            p_inc_flags = Flags(**incident_to_broadcast.flags) if isinstance(incident_to_broadcast.flags, dict) else Flags()
            
            p_inc = PydanticIncident(
                incident_id=incident_to_broadcast.incident_id,
                cluster_id=incident_to_broadcast.cluster_id,
                location=SOSLocation(lat=lat, lng=lng),
                area_name=incident_to_broadcast.area_name or "",
                emergency_types=incident_to_broadcast.emergency_types or [],
                severity=p_inc_sev,
                ai_summary=incident_to_broadcast.ai_summary or "",
                report_count=incident_to_broadcast.report_count or 0,
                estimated_people_affected=incident_to_broadcast.estimated_people_affected or 0,
                flags=p_inc_flags,
                first_reported_at=incident_to_broadcast.first_reported_at,
                last_updated_at=incident_to_broadcast.last_updated_at,
                status=incident_to_broadcast.status
            )
            
            loop = asyncio.get_event_loop()
            assignments = await loop.run_in_executor(
                None,
                lambda: optimize_allocations([p_inc], p_resources)
            )
            
            if assignments:
                recs = []
                for a in assignments:
                    if a.get("incident_id") == incident_to_broadcast.incident_id:
                        r_type = "resource"
                        for pr in p_resources:
                            if pr.resource_id == a.get("resource_id"):
                                r_type = pr.sub_type
                                break
                        recs.append({
                            "resource_id": a.get("resource_id"),
                            "resource_type": r_type,
                            "quantity": a.get("quantity"),
                            "reasoning": a.get("reasoning"),
                            "agency": a.get("agency"),
                            "distance_km": a.get("distance_km")
                        })
                
                incident_to_broadcast.recommended_resources = recs
                await db.commit()
                await db.refresh(incident_to_broadcast)
                
        except Exception as e:
            logger.error(f"Optimizer failed for incident {incident_to_broadcast.incident_id}: {e}")

        # 4. Broadcast WebSocket event
        event_name = "incident_created" if is_new else "incident_updated"
        
        sos_reports_list = []
        for r in reports:
            # We need lat/lng for each report.
            # However, the initial query already fetched lat/lng for these reports!
            # Let's find the matching row.
            r_row = next((row for row in reports_rows if row[0].uuid == r.uuid), None)
            r_lat = r_row[1] if r_row else 0.0
            r_lng = r_row[2] if r_row else 0.0
            
            s_dict = {
                "uuid": r.uuid,
                "device_id": r.device_id,
                "created_at": r.created_at,
                "location": {"lat": r_lat, "lng": r_lng, "accuracy_m": r.accuracy_m},
                "is_quick_sos": r.is_quick_sos,
                "emergency_type": r.emergency_type,
                "severity_hint": r.severity_hint,
                "people_count": r.people_count,
                "medical_snapshot": r.medical_snapshot,
                "custom_message": r.custom_message,
                "contact_number": r.contact_number,
                "relay_hop_count": r.relay_hop_count,
                "last_relayed_at": r.last_relayed_at,
                "status": r.status
            }
            sos_reports_list.append(s_dict)
            
        response_model = IncidentResponse(
            incident_id=incident_to_broadcast.incident_id,
            cluster_id=incident_to_broadcast.cluster_id,
            source_sos_uuids=source_sos_uuids,
            location={"lat": lat, "lng": lng, "accuracy_m": None},
            area_name=incident_to_broadcast.area_name,
            emergency_types=incident_to_broadcast.emergency_types,
            severity=incident_to_broadcast.severity,
            ai_summary=incident_to_broadcast.ai_summary,
            report_count=incident_to_broadcast.report_count,
            estimated_people_affected=incident_to_broadcast.estimated_people_affected,
            flags=incident_to_broadcast.flags,
            first_reported_at=incident_to_broadcast.first_reported_at,
            last_updated_at=incident_to_broadcast.last_updated_at,
            status=incident_to_broadcast.status,
            recommended_resources=incident_to_broadcast.recommended_resources,
            assigned_resources=incident_to_broadcast.assigned_resources
        )
        
        
        await ws_manager.broadcast_event(event_name, response_model)

    # 5. Clean up orphaned Incidents (clusters that merged/disappeared)
    # This ensures that if ST_ClusterDBSCAN merges clusters, the old abandoned incident records are removed.
    cleanup_query = text("""
        DELETE FROM incident
        WHERE cluster_id NOT IN (
            SELECT DISTINCT cluster_id FROM sos_report WHERE cluster_id IS NOT NULL
        )
    """)
    await db.execute(cleanup_query)
    await db.commit()
