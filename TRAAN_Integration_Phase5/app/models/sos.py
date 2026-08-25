from pydantic import BaseModel, Field, UUID4
from datetime import datetime
from typing import List, Optional, Dict, Any

from app.models.common import EmergencyType, SeverityHint, SOSStatus


class Location(BaseModel):
    lat: float = Field(..., ge=-90.0, le=90.0)
    lng: float = Field(..., ge=-180.0, le=180.0)
    accuracy_m: Optional[float] = None


class SOSRequest(BaseModel):
    uuid: UUID4
    device_id: str
    created_at: datetime
    location: Location
    is_quick_sos: bool
    emergency_type: EmergencyType
    severity_hint: Optional[SeverityHint] = None
    people_count: Optional[int] = Field(None, ge=0)
    medical_snapshot: Optional[Dict[str, Any]] = None
    custom_message: Optional[str] = None
    contact_number: Optional[str] = None
    relay_hop_count: int = Field(..., ge=0)
    last_relayed_at: datetime
    status: Optional[SOSStatus] = None

class BatchUpload(BaseModel):
    gateway_device_id: str
    gateway_location: Location
    uploaded_at: datetime
    sos_batch: List[SOSRequest]


class BatchResponse(BaseModel):
    accepted_uuids: List[UUID4]
    duplicate_uuids: List[UUID4]


class SOSStatusResponse(BaseModel):
    status: str
