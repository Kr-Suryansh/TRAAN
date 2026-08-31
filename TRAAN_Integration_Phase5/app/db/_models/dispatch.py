from sqlalchemy import Column, String, DateTime, Integer, ForeignKey
from app.db.base import Base

class DispatchRecord(Base):
    __tablename__ = "dispatch_record"

    dispatch_id = Column(String, primary_key=True, index=True)
    incident_id = Column(String, ForeignKey("incident.incident_id"), nullable=False, index=True)
    resource_id = Column(String, ForeignKey("resource.resource_id"), nullable=False, index=True)
    quantity_dispatched = Column(Integer, nullable=False)
    dispatched_by = Column(String, nullable=False)
    dispatched_at = Column(DateTime(timezone=True), nullable=False)
    eta_minutes = Column(Integer, nullable=True)
    status = Column(String, nullable=False)
