from typing import Optional
from pydantic import BaseModel

class ActiveIncidents(BaseModel):
    critical: int
    high: int
    medium: int
    low: int

class ResourceStats(BaseModel):
    available: int
    deployed: int

class StatsSummaryResponse(BaseModel):
    active_incidents: ActiveIncidents
    total_estimated_people_affected: int
    resources: ResourceStats
    new_incidents_last_15min: int
