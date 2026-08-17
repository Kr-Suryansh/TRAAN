import pytest
from sqlalchemy import create_engine
from sqlalchemy.orm import sessionmaker
from app.db.database import Base
from app.db.models import ResourceModel, IncidentModel
from app.services.mock_idrn.seed import seed_database
from app.services.optimizer.resource_registry import get_all_resources, get_available_resources
from app.services.optimizer.incident_service import create_incident
from app.models.schemas import Incident, Location, SeverityEnum, ResourceStatus, ResourceCategory, Flags
from datetime import datetime

@pytest.fixture
def test_db():
    engine = create_engine("sqlite:///:memory:", connect_args={"check_same_thread": False})
    TestingSessionLocal = sessionmaker(autocommit=False, autoflush=False, bind=engine)
    Base.metadata.create_all(bind=engine)
    db = TestingSessionLocal()
    try:
        yield db
    finally:
        db.close()
        Base.metadata.drop_all(bind=engine)

def test_seed_database_persists_records(test_db):
    """Test that seed_database writes ResourceModel rows into database."""
    resources = seed_database(db=test_db)
    assert len(resources) > 0

    count = test_db.query(ResourceModel).count()
    assert count == len(resources)
    
    first_db_res = test_db.query(ResourceModel).first()
    assert first_db_res.resource_id is not None
    assert first_db_res.district == "Dehradun"

def test_seed_database_is_idempotent(test_db):
    """Test that running seed_database multiple times upserts without duplicating rows."""
    res1 = seed_database(db=test_db)
    count1 = test_db.query(ResourceModel).count()

    res2 = seed_database(db=test_db)
    count2 = test_db.query(ResourceModel).count()

    assert count1 == count2 == len(res1)

def test_incident_persistence_in_db(test_db):
    """Test that create_incident persists IncidentModel into database session."""
    now = datetime.utcnow()
    inc = Incident(
        incident_id="inc_db_test_01",
        cluster_id="cluster_db_1",
        location=Location(lat=30.316, lng=78.032),
        area_name="Dehradun Ward 5",
        severity=SeverityEnum.critical,
        estimated_people_affected=15,
        flags=Flags(medical_emergency=True),
        first_reported_at=now,
        last_updated_at=now
    )
    
    # Seed DB resources first
    seed_database(db=test_db)
    
    created = create_incident(inc, db=test_db)
    
    assert created.incident_id == "inc_db_test_01"
    
    # Query database directly
    db_inc = test_db.query(IncidentModel).filter(IncidentModel.incident_id == "inc_db_test_01").first()
    assert db_inc is not None
    assert db_inc.cluster_id == "cluster_db_1"
    assert db_inc.severity == "critical"
    assert len(db_inc.recommended_resources) > 0
