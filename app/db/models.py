import json
from datetime import datetime
from sqlalchemy import Column, String, Integer, Float, DateTime, JSON, Text
from app.db.database import Base
from app.models.schemas import Resource, Incident, Location, ResourceCategory, ResourceStatus, SeverityEnum, Flags, RecommendedResource

class ResourceModel(Base):
    __tablename__ = "resources"

    resource_id = Column(String, primary_key=True, index=True)
    category = Column(String, nullable=False)
    sub_type = Column(String, nullable=False)
    custodian_agency = Column(String, nullable=False)
    quantity_total = Column(Integer, nullable=False)
    quantity_available = Column(Integer, nullable=False)
    status = Column(String, nullable=False, default="available")
    lat = Column(Float, nullable=False)
    lng = Column(Float, nullable=False)
    district = Column(String, nullable=True)
    contact = Column(String, nullable=False)
    last_updated_at = Column(DateTime, default=datetime.utcnow)

    def to_pydantic(self) -> Resource:
        return Resource(
            resource_id=self.resource_id,
            category=ResourceCategory(self.category),
            sub_type=self.sub_type,
            custodian_agency=self.custodian_agency,
            quantity_total=self.quantity_total,
            quantity_available=self.quantity_available,
            status=ResourceStatus(self.status),
            location=Location(lat=self.lat, lng=self.lng, district=self.district),
            contact=self.contact,
            last_updated_at=self.last_updated_at or datetime.utcnow()
        )

    @classmethod
    def from_pydantic(cls, res: Resource) -> "ResourceModel":
        return cls(
            resource_id=res.resource_id,
            category=res.category.value if hasattr(res.category, "value") else str(res.category),
            sub_type=res.sub_type,
            custodian_agency=res.custodian_agency,
            quantity_total=res.quantity_total,
            quantity_available=res.quantity_available,
            status=res.status.value if hasattr(res.status, "value") else str(res.status),
            lat=res.location.lat,
            lng=res.location.lng,
            district=res.location.district,
            contact=res.contact,
            last_updated_at=res.last_updated_at or datetime.utcnow()
        )

class IncidentModel(Base):
    __tablename__ = "incidents"

    incident_id = Column(String, primary_key=True, index=True)
    cluster_id = Column(String, nullable=False)
    source_sos_uuids = Column(JSON, nullable=False, default=list)
    lat = Column(Float, nullable=False)
    lng = Column(Float, nullable=False)
    area_name = Column(String, nullable=True, default="")
    emergency_types = Column(JSON, nullable=False, default=list)
    severity = Column(String, nullable=True)  # Can be None if Gemini fails
    ai_summary = Column(String, nullable=True, default="")
    report_count = Column(Integer, nullable=False, default=0)
    estimated_people_affected = Column(Integer, nullable=False, default=0)
    flags = Column(JSON, nullable=False, default=dict)
    first_reported_at = Column(DateTime, default=datetime.utcnow)
    last_updated_at = Column(DateTime, default=datetime.utcnow)
    status = Column(String, nullable=False, default="new")
    recommended_resources = Column(JSON, nullable=False, default=list)
    assigned_resources = Column(JSON, nullable=False, default=list)

    def to_pydantic(self) -> Incident:
        sev = SeverityEnum(self.severity) if self.severity else None
        flags_obj = Flags(**self.flags) if isinstance(self.flags, dict) else Flags()
        
        recs = []
        if isinstance(self.recommended_resources, list):
            for r in self.recommended_resources:
                if isinstance(r, dict):
                    recs.append(RecommendedResource(**r))
                elif isinstance(r, RecommendedResource):
                    recs.append(r)

        return Incident(
            incident_id=self.incident_id,
            cluster_id=self.cluster_id,
            source_sos_uuids=self.source_sos_uuids or [],
            location=Location(lat=self.lat, lng=self.lng),
            area_name=self.area_name or "",
            emergency_types=self.emergency_types or [],
            severity=sev,
            ai_summary=self.ai_summary or "",
            report_count=self.report_count or 0,
            estimated_people_affected=self.estimated_people_affected or 0,
            flags=flags_obj,
            first_reported_at=self.first_reported_at or datetime.utcnow(),
            last_updated_at=self.last_updated_at or datetime.utcnow(),
            status=self.status or "new",
            recommended_resources=recs,
            assigned_resources=self.assigned_resources or []
        )

    @classmethod
    def from_pydantic(cls, inc: Incident) -> "IncidentModel":
        recs_data = [r.model_dump() for r in inc.recommended_resources] if inc.recommended_resources else []
        return cls(
            incident_id=inc.incident_id,
            cluster_id=inc.cluster_id,
            source_sos_uuids=inc.source_sos_uuids,
            lat=inc.location.lat,
            lng=inc.location.lng,
            area_name=inc.area_name,
            emergency_types=inc.emergency_types,
            severity=inc.severity.value if inc.severity and hasattr(inc.severity, "value") else str(inc.severity) if inc.severity else None,
            ai_summary=inc.ai_summary,
            report_count=inc.report_count,
            estimated_people_affected=inc.estimated_people_affected,
            flags=inc.flags.model_dump() if inc.flags else {},
            first_reported_at=inc.first_reported_at or datetime.utcnow(),
            last_updated_at=inc.last_updated_at or datetime.utcnow(),
            status=inc.status,
            recommended_resources=recs_data,
            assigned_resources=inc.assigned_resources or []
        )
