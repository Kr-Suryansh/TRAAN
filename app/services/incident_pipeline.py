import uuid
import datetime
import logging
from typing import List

from sqlalchemy.ext.asyncio import AsyncSession
from sqlalchemy import select, text, func

from app.db._models.sos import SOSReport
from app.db._models.incident import Incident
from app.models.incidents import IncidentResponse
from app.ws.manager import manager as ws_manager

logger = logging.getLogger(__name__)

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
        
        # Derive severity
        hints = [r.severity_hint for r in reports if r.severity_hint]
        if "critical" in hints:
            severity = "critical"
        elif "high" in hints:
            severity = "high"
        elif "medium" in hints:
            severity = "medium"
        elif "low" in hints:
            severity = "low"
        else:
            severity = "medium"
            
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
            source_sos_reports=sos_reports_list,
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
