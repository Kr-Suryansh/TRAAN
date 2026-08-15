from pydantic import BaseModel, Field
from typing import List, Optional
from datetime import datetime
from enum import Enum
import uuid

# Re-implementing schemas based on Day 1 Contracts
# Since this module operates independently, we define Pydantic models to mock DB models/schemas

class EmergencyType(str, Enum):
    medical = "medical"
    trapped = "trapped"
    structural_collapse = "structural_collapse"
    flood_rescue = "flood_rescue"
    fire = "fire"
    missing_person = "missing_person"
    unspecified = "unspecified"

class SeverityEnum(str, Enum):
    critical = "critical"
    high = "high"
    medium = "medium"
    low = "low"

class ResourceCategory(str, Enum):
    medical = "medical"
    rescue = "rescue"
    shelter = "shelter"
    transport = "transport"
    communication = "communication"

class ResourceStatus(str, Enum):
    available = "available"
    partially_deployed = "partially_deployed"
    deployed = "deployed"
    maintenance = "maintenance"

class Location(BaseModel):
    lat: float
    lng: float
    accuracy_m: Optional[float] = None
    district: Optional[str] = None # Added for resources

class UserMedicalProfile(BaseModel):
    name: Optional[str] = None
    age: Optional[int] = None
    blood_type: Optional[str] = None
    medical_conditions: List[str] = []
    medications: List[str] = []
    allergies: List[str] = []
    emergency_contact_name: Optional[str] = None
    emergency_contact_number: Optional[str] = None

class SOSRequest(BaseModel):
    uuid: str = Field(default_factory=lambda: str(uuid.uuid4()))
    device_id: str
    created_at: datetime
    location: Location
    is_quick_sos: bool
    emergency_type: EmergencyType = EmergencyType.unspecified
    severity_hint: Optional[SeverityEnum] = None
    people_count: Optional[int] = None
    medical_snapshot: Optional[UserMedicalProfile] = None
    custom_message: Optional[str] = None
    contact_number: Optional[str] = None
    relay_hop_count: int = 0
    last_relayed_at: datetime
    status: str = "pending_local"

class Flags(BaseModel):
    medical_emergency: bool = False
    trapped: bool = False
    elderly_or_children: bool = False
    structural_damage: bool = False

class RecommendedResource(BaseModel):
    resource_id: Optional[str] = None
    resource_type: str
    quantity: int
    reasoning: str

class Incident(BaseModel):
    incident_id: str = Field(default_factory=lambda: str(uuid.uuid4()))
    cluster_id: str
    source_sos_uuids: List[str] = []
    location: Location
    area_name: str = ""
    emergency_types: List[str] = []
    severity: SeverityEnum = SeverityEnum.medium
    ai_summary: str = ""
    report_count: int = 0
    estimated_people_affected: int = 0
    flags: Flags = Field(default_factory=Flags)
    first_reported_at: datetime
    last_updated_at: datetime
    status: str = "new"
    recommended_resources: List[RecommendedResource] = []
    assigned_resources: List[str] = []

class Resource(BaseModel):
    resource_id: str = Field(default_factory=lambda: str(uuid.uuid4()))
    category: ResourceCategory
    sub_type: str
    custodian_agency: str
    quantity_total: int
    quantity_available: int
    status: ResourceStatus = ResourceStatus.available
    location: Location
    contact: str
    last_updated_at: datetime
