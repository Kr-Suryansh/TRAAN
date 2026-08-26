from typing import Optional
from pydantic import BaseModel, ConfigDict, Field, model_validator
from datetime import datetime
from typing import Optional, Literal
from app.models.common import ResourceStatus

class Location(BaseModel):
    lat: float = Field(..., ge=-90.0, le=90.0)
    lng: float = Field(..., ge=-180.0, le=180.0)
    district: str

class ResourceBase(BaseModel):
    category: Literal["medical", "rescue", "shelter", "transport", "communication"]
    sub_type: str
    custodian_agency: str
    quantity_total: int = Field(..., ge=0)
    quantity_available: int = Field(..., ge=0)
    status: ResourceStatus
    contact: str
    
    @model_validator(mode='after')
    def check_quantities(self) -> 'ResourceBase':
        if self.quantity_available > self.quantity_total:
            raise ValueError("quantity_available cannot exceed quantity_total")
        return self
    
class ResourceCreate(ResourceBase):
    location: Location

class ResourceUpdate(BaseModel):
    quantity_available: Optional[int] = Field(None, ge=0)
    status: Optional[ResourceStatus] = None

class ResourceResponse(ResourceBase):
    resource_id: str
    location: Location
    last_updated_at: datetime
    
    model_config = ConfigDict(from_attributes=True)
