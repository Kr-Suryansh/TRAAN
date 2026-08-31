from sqlalchemy import Column, String, DateTime, Integer
from sqlalchemy.dialects.postgresql import JSONB
from geoalchemy2 import Geometry

from app.db.base import Base

class Incident(Base):
    __tablename__ = "incident"

    incident_id = Column(String, primary_key=True, index=True)
    cluster_id = Column(String, unique=True, index=True, nullable=False)
    source_sos_uuids = Column(JSONB, nullable=False)
    
    # We use Geometry(Point, 4326)
    location = Column(Geometry(geometry_type='POINT', srid=4326), nullable=False)
    area_name = Column(String, nullable=True)
    
    emergency_types = Column(JSONB, nullable=False)
    severity = Column(String, nullable=False)
    ai_summary = Column(String, nullable=True)
    report_count = Column(Integer, nullable=False)
    estimated_people_affected = Column(Integer, nullable=True)
    
    flags = Column(JSONB, nullable=False)
    
    first_reported_at = Column(DateTime(timezone=True), nullable=False)
    last_updated_at = Column(DateTime(timezone=True), nullable=False)
    
    status = Column(String, nullable=False)
    
    recommended_resources = Column(JSONB, nullable=True)
    assigned_resources = Column(JSONB, nullable=True)
