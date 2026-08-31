from sqlalchemy import Column, String, DateTime, Integer, CheckConstraint
from geoalchemy2 import Geometry

from app.db.base import Base

class Resource(Base):
    __tablename__ = "resource"
    __table_args__ = (
        CheckConstraint('quantity_available >= 0', name='check_quantity_available_positive'),
    )

    resource_id = Column(String, primary_key=True, index=True)
    category = Column(String, nullable=False, index=True)
    sub_type = Column(String, nullable=False)
    custodian_agency = Column(String, nullable=False)
    quantity_total = Column(Integer, nullable=False)
    quantity_available = Column(Integer, nullable=False)
    status = Column(String, nullable=False, index=True)
    
    location = Column(Geometry(geometry_type='POINT', srid=4326), nullable=False)
    district = Column(String, nullable=False, index=True)
    
    contact = Column(String, nullable=False)
    last_updated_at = Column(DateTime(timezone=True), nullable=False)
