from typing import Optional, List, Any
from pydantic import BaseModel, ConfigDict, Field
from datetime import datetime
from app.models.sos import Location, SOSRequest
from app.models.common import IncidentStatus
from enum import Enum

class IncidentFlags(BaseModel):
    medical_emergency: bool
    trapped: bool
    elderly_or_children: bool
    structural_damage: bool

class IncidentSeverity(str, Enum):
    critical = "critical"
    high = "high"
    medium = "medium"
    low = "low"

class Recommendation(BaseModel):
    resource_type: str
    quantity: int
    reasoning: str
    agency: Optional[str] = None
    distance_km: Optional[float] = None

class IncidentBase(BaseModel):
    area_name: Optional[str] = None
    emergency_types: List[str]
    severity: IncidentSeverity
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
    source_sos_uuids: List[str]
    location: Location
    first_reported_at: datetime
    last_updated_at: datetime
    
    model_config = ConfigDict(from_attributes=True)

class IncidentDetailResponse(BaseModel):
    incident: IncidentResponse
    sos_reports: List[SOSRequest]

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

class DispatchStatus(str, Enum):
    dispatched = "dispatched"
    en_route = "en_route"
    arrived = "arrived"
    completed = "completed"

class DispatchResponse(BaseModel):
    dispatch_id: str
    incident_id: str
    resource_id: str
    quantity_dispatched: int
    dispatched_by: str
    dispatched_at: datetime
    eta_minutes: Optional[int] = None
    status: DispatchStatus
    
    model_config = ConfigDict(from_attributes=True)
