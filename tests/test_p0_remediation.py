import pytest
from datetime import datetime
from unittest.mock import patch, MagicMock
from sqlalchemy import create_engine
from sqlalchemy.orm import sessionmaker

from app.db.database import Base
from app.db.models import IncidentModel, ResourceModel
from app.models.schemas import (
    Incident, Location, SeverityEnum, Resource, ResourceCategory, ResourceStatus, Flags, SOSRequest, EmergencyType
)
from app.services.optimizer.incident_service import (
    create_incident, update_incident, get_active_incidents,
    run_automatic_optimization_for_store, run_on_demand_optimization, clear_incident_store
)
from app.services.ai.summarizer import generate_incident_summary, _get_fallback_summary
from app.services.ai.situation_brief import refresh_situation_brief, clear_situation_brief_cache
from app.services.mock_idrn.seed import seed_database

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

@pytest.fixture(autouse=True)
def cleanup():
    clear_incident_store()
    clear_situation_brief_cache()
    yield
    clear_incident_store()
    clear_situation_brief_cache()

# ============================================================
# P0-1: GEMINI FAILURE SAFETY & NULL SEVERITY TESTS
# ============================================================

def test_null_severity_db_roundtrip(test_db):
    """
    P0-1 Fix Verification:
    Incident with severity=None persists as NULL in DB and reads back as None (NOT medium).
    """
    now = datetime.utcnow()
    inc = Incident(
        incident_id="inc_null_sev_01",
        cluster_id="cluster_null",
        location=Location(lat=30.316, lng=78.032),
        severity=None, # Explicit None
        first_reported_at=now,
        last_updated_at=now
    )
    
    created = create_incident(inc, db=test_db)
    assert created.severity is None

    # Query DB directly via IncidentModel
    db_model = test_db.query(IncidentModel).filter(IncidentModel.incident_id == "inc_null_sev_01").first()
    assert db_model is not None
    assert db_model.severity is None # Raw DB value is NULL

    # Convert back to Pydantic
    pydantic_read = db_model.to_pydantic()
    assert pydantic_read.severity is None # Must NOT be SeverityEnum.medium!

def test_gemini_failure_persistence_path(test_db):
    """
    P0-1 End-to-End Failure Path Verification:
    Gemini failure -> fallback summary with severity=None -> DB persistence -> DB read returns severity=None.
    """
    now = datetime.utcnow()
    sos_reports = [
        SOSRequest(
            device_id="dev_1",
            created_at=now,
            location=Location(lat=30.316, lng=78.032),
            is_quick_sos=False,
            emergency_type=EmergencyType.flood_rescue,
            people_count=5,
            custom_message="Flood waters rising fast",
            last_relayed_at=now
        )
    ]
    
    # 1. Trigger summary with no Gemini client (forces fallback)
    summary_result = _get_fallback_summary(sos_reports)
    assert summary_result["severity"] is None
    assert summary_result["ai_success"] is False

    # 2. Create Incident using fallback summary
    inc = Incident(
        incident_id="inc_gemini_fail_01",
        cluster_id="cluster_fail",
        location=Location(lat=30.316, lng=78.032),
        severity=summary_result["severity"],
        ai_summary=summary_result["ai_summary"],
        estimated_people_affected=summary_result["estimated_people_affected"],
        flags=Flags(**summary_result["flags"]),
        first_reported_at=now,
        last_updated_at=now
    )

    created = create_incident(inc, db=test_db)
    assert created.severity is None

    # 3. Simulate process restart & read from DB
    clear_incident_store()
    loaded_incidents = get_active_incidents(db=test_db)
    target = next((i for i in loaded_incidents if i.incident_id == "inc_gemini_fail_01"), None)
    
    assert target is not None
    assert target.severity is None # Proves NO fabricated medium severity!

def test_existing_valid_severity_preserved_on_failure(test_db):
    """
    P0-1 CASE B Verification:
    An incident with an existing valid severity (e.g. 'high') must NOT be overwritten with None
    if Gemini fails during a subsequent update/refresh.
    """
    now = datetime.utcnow()
    inc = Incident(
        incident_id="inc_case_b_01",
        cluster_id="cluster_b",
        location=Location(lat=30.316, lng=78.032),
        severity=SeverityEnum.high, # Existing valid severity
        ai_summary="Initial summary",
        first_reported_at=now,
        last_updated_at=now
    )
    
    created = create_incident(inc, db=test_db)
    assert created.severity == SeverityEnum.high

    # Update incident with Gemini failure simulation (updates dictionary has severity=None)
    updates = {
        "ai_summary": "AI summarization failed. Cluster updated.",
        "severity": None, # Gemini failed on refresh
        "estimated_people_affected": 8
    }

    updated = update_incident("inc_case_b_01", updates, db=test_db)
    assert updated.severity == SeverityEnum.high # Existing high severity MUST be preserved!

    # Verify DB read
    clear_incident_store()
    loaded = get_active_incidents(db=test_db)
    target = next((i for i in loaded if i.incident_id == "inc_case_b_01"), None)
    assert target is not None
    assert target.severity == SeverityEnum.high

# ============================================================
# P0-4: INCIDENT DATABASE PERSISTENCE & RESTART TESTS
# ============================================================

def test_incident_persistence_after_restart(test_db):
    """
    P0-4 Restart Verification:
    Create incident, clear all in-memory store, query active incidents from DB.
    Verifies PostgreSQL/SQLite is the true source of truth.
    """
    now = datetime.utcnow()
    inc = Incident(
        incident_id="inc_restart_01",
        cluster_id="cluster_restart",
        location=Location(lat=30.316, lng=78.032),
        severity=SeverityEnum.critical,
        estimated_people_affected=15,
        flags=Flags(medical_emergency=True),
        first_reported_at=now,
        last_updated_at=now
    )

    create_incident(inc, db=test_db)

    # Reset in-memory store completely (simulates application restart)
    clear_incident_store()

    # Query active incidents
    active_incidents = get_active_incidents(db=test_db)
    assert len(active_incidents) == 1
    assert active_incidents[0].incident_id == "inc_restart_01"
    assert active_incidents[0].severity == SeverityEnum.critical

def test_optimizer_sees_incidents_after_restart(test_db):
    """
    P0-4 Optimizer Restart Verification:
    Create incident in DB, clear in-memory store, run automatic & on-demand optimization.
    Verifies optimizer loads active incidents from DB rather than in-memory store.
    """
    # Seed DB resources first
    seed_database(db=test_db)
    now = datetime.utcnow()

    inc = Incident(
        incident_id="inc_opt_restart_01",
        cluster_id="cluster_opt",
        location=Location(lat=30.316, lng=78.032),
        severity=SeverityEnum.critical,
        estimated_people_affected=10,
        flags=Flags(medical_emergency=True),
        first_reported_at=now,
        last_updated_at=now
    )

    create_incident(inc, db=test_db)

    # Simulate restart
    clear_incident_store()

    # Run on-demand optimization without passing in-memory incidents
    assignments = run_on_demand_optimization(db=test_db)
    assert len(assignments) > 0
    assert assignments[0]["incident_id"] == "inc_opt_restart_01"

    # Run automatic optimization & check DB recommendations update
    run_automatic_optimization_for_store(db=test_db)
    clear_incident_store()

    active_incidents = get_active_incidents(db=test_db)
    assert len(active_incidents[0].recommended_resources) > 0

@patch("app.services.ai.situation_brief.get_client")
def test_situation_brief_sees_incidents_after_restart(mock_get_client, test_db):
    """
    P0-4 Situation Brief Restart Verification:
    Create incidents in DB, clear in-memory store, refresh situation brief.
    Verifies situation brief aggregates active incidents from DB.
    """
    mock_client = MagicMock()
    mock_response = MagicMock()
    mock_response.text = "Operational brief synthesizing 1 active incident from database after backend restart."
    mock_client.models.generate_content.return_value = mock_response
    mock_get_client.return_value = mock_client

    seed_database(db=test_db)
    now = datetime.utcnow()

    inc = Incident(
        incident_id="inc_sb_restart_01",
        cluster_id="cluster_sb",
        location=Location(lat=30.316, lng=78.032),
        severity=SeverityEnum.high,
        estimated_people_affected=12,
        area_name="Dehradun Ward 5",
        first_reported_at=now,
        last_updated_at=now
    )

    create_incident(inc, db=test_db)

    # Simulate restart: clear in-memory store & clear cache
    clear_incident_store()
    clear_situation_brief_cache()

    # Refresh situation brief using DB session
    cache = refresh_situation_brief(db=test_db)

    assert cache.active_incidents_count == 1
    assert cache.total_people_affected == 12
    assert cache.is_fallback is False
