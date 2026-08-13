from typing import Optional, List, Any
from pydantic import BaseModel, ConfigDict, Field
from datetime import datetime
from app.models.sos import Location
from app.models.common import IncidentStatus

class IncidentFlags(BaseModel):
    medical_emergency: bool
    trapped: bool
    elderly_or_children: bool
    structural_damage: bool

class Recommendation(BaseModel):
    resource_type: str
    quantity: int
    reasoning: str

class IncidentBase(BaseModel):
    area_name: Optional[str] = None
    emergency_types: List[str]
    severity: str
    ai_summary: Optional[str] = None
    report_count: int
    estimated_people_affected: Optional[int] = None
    flags: IncidentFlags
    status: IncidentStatus
    recommended_resources: Optional[List[Recommendation]] = None
    assigned_resources: Optional[List[str]] = None

class IncidentResponse(IncidentBase):
    incident_id: str
    cluster_id: str
    source_sos_reports: List[Any]
    location: Location
    first_reported_at: datetime
    last_updated_at: datetime
    
    model_config = ConfigDict(from_attributes=True)

class IncidentStatusUpdate(BaseModel):
    status: IncidentStatus

class PaginatedIncidentsResponse(BaseModel):
    items: List[IncidentResponse]
    total: int
    page: int
    size: int

class DispatchRequest(BaseModel):
    resource_id: str
    quantity: int

class DispatchResponse(BaseModel):
    dispatch_id: str
    incident_id: str
    resource_id: str
    quantity_dispatched: int
    dispatched_by: str
    dispatched_at: datetime
    eta_minutes: Optional[int] = None
    status: str
    
    model_config = ConfigDict(from_attributes=True)
