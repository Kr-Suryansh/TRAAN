from sqlalchemy import Column, String, DateTime, Float, Boolean, Integer
from sqlalchemy.dialects.postgresql import JSONB
from geoalchemy2 import Geometry

from app.db.base import Base

class SOSReport(Base):
    __tablename__ = "sos_report"

    # Core fields (from SOSRequest)
    uuid = Column(String, primary_key=True, index=True)
    device_id = Column(String, nullable=False, index=True)
    created_at = Column(DateTime(timezone=True), nullable=False)
    
    # We use Geometry(Point, 4326) for easy ST_ClusterDBSCAN compatibility, 
    # and we can cast to geography for ST_DWithin distance filtering.
    location = Column(Geometry(geometry_type='POINT', srid=4326), nullable=False)
    
    accuracy_m = Column(Float, nullable=True)
    is_quick_sos = Column(Boolean, nullable=False)
    emergency_type = Column(String, nullable=False)
    severity_hint = Column(String, nullable=True)
    people_count = Column(Integer, nullable=True)
    medical_snapshot = Column(JSONB, nullable=True)
    custom_message = Column(String, nullable=True)
    contact_number = Column(String, nullable=True)
    relay_hop_count = Column(Integer, nullable=False)
    last_relayed_at = Column(DateTime(timezone=True), nullable=False)
    status = Column(String, nullable=False)

    # Backend-only fields
    received_at = Column(DateTime(timezone=True), nullable=False)
    cluster_id = Column(String, nullable=True, index=True)
    received_via_gateway_device_id = Column(String, nullable=False)
