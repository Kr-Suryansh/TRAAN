import pytest
from datetime import datetime
from unittest.mock import patch
from app.models.schemas import Incident, Resource, SeverityEnum, ResourceCategory, Location, ResourceStatus, Flags
from app.services.optimizer.incident_service import (
    create_incident,
    update_incident,
    run_on_demand_optimization,
    clear_incident_store,
    INCIDENT_STORE
)

from app.db.database import SessionLocal, init_db
from app.db.models import IncidentModel

@pytest.fixture(autouse=True)
def reset_store():
    clear_incident_store()
    init_db()
    db = SessionLocal()
    try:
        db.query(IncidentModel).delete()
        db.commit()
    finally:
        db.close()
    yield
    clear_incident_store()
    db = SessionLocal()
    try:
        db.query(IncidentModel).delete()
        db.commit()
    finally:
        db.close()

@pytest.fixture
def base_location():
    return Location(lat=30.0, lng=78.0)

@pytest.fixture
def now():
    return datetime.utcnow()

@pytest.fixture
def sample_resources(base_location, now):
    return [
        Resource(
            resource_id="r1_ambulance",
            category=ResourceCategory.medical,
            sub_type="ambulance",
            custodian_agency="Hospital Alpha",
            quantity_total=5,
            quantity_available=3,
            status=ResourceStatus.available,
            location=base_location,
            contact="108",
            last_updated_at=now
        ),
        Resource(
            resource_id="r2_boat",
            category=ResourceCategory.rescue,
            sub_type="motorboat",
            custodian_agency="SDRF",
            quantity_total=2,
            quantity_available=2,
            status=ResourceStatus.available,
            location=base_location,
            contact="112",
            last_updated_at=now
        ),
        Resource(
            resource_id="r3_camp",
            category=ResourceCategory.shelter,
            sub_type="relief_camp",
            custodian_agency="District Admin",
            quantity_total=50,
            quantity_available=50,
            status=ResourceStatus.available,
            location=base_location,
            contact="1077",
            last_updated_at=now
        )
    ]

# --- Test 1: New incident automatically gets recommendation ---
def test_new_incident_automatically_gets_recommendation(base_location, now, sample_resources):
    inc = Incident(
        incident_id="inc_001",
        cluster_id="cluster_1",
        location=base_location,
        severity=SeverityEnum.critical,
        estimated_people_affected=10,
        flags=Flags(medical_emergency=True),
        first_reported_at=now,
        last_updated_at=now
    )
    created = create_incident(inc, available_resources=sample_resources)
    
    assert created.incident_id in INCIDENT_STORE
    assert len(created.recommended_resources) > 0
    rec = created.recommended_resources[0]
    assert rec.resource_id is not None
    assert rec.quantity > 0
    assert rec.reasoning != ""

# --- Test 2: No suitable resource ---
def test_no_suitable_resource_preserves_incident(base_location, now):
    inc = Incident(
        incident_id="inc_002",
        cluster_id="cluster_2",
        location=base_location,
        severity=SeverityEnum.medium,
        first_reported_at=now,
        last_updated_at=now
    )
    # Pass empty resources
    created = create_incident(inc, available_resources=[])
    
    assert created.incident_id in INCIDENT_STORE
    assert len(created.recommended_resources) == 0

# --- Test 3: Capacity constraint ---
def test_capacity_constraint_across_incidents(base_location, now):
    # Resource with quantity_available = 3
    limited_resource = Resource(
        resource_id="r_limited_amb",
        category=ResourceCategory.medical,
        sub_type="ambulance",
        custodian_agency="Hospital",
        quantity_total=5,
        quantity_available=3,
        status=ResourceStatus.available,
        location=base_location,
        contact="108",
        last_updated_at=now
    )
    
    # Create 4 incidents that demand ambulances
    incidents = []
    for i in range(4):
        inc = Incident(
            incident_id=f"inc_cap_{i}",
            cluster_id=f"cluster_{i}",
            location=base_location,
            severity=SeverityEnum.critical,
            estimated_people_affected=20,
            flags=Flags(medical_emergency=True),
            first_reported_at=now,
            last_updated_at=now
        )
        create_incident(inc, available_resources=[limited_resource])

    total_recommended = 0
    for inc_id, stored_inc in INCIDENT_STORE.items():
        for rec in stored_inc.recommended_resources:
            if rec.resource_id == "r_limited_amb":
                total_recommended += rec.quantity

    assert total_recommended <= 3

# --- Test 4: Unavailable resources ignored ---
def test_unavailable_resources_ignored(base_location, now):
    resources = [
        Resource(
            resource_id="r_maint",
            category=ResourceCategory.medical,
            sub_type="ambulance",
            custodian_agency="H1",
            quantity_total=5,
            quantity_available=5,
            status=ResourceStatus.maintenance, # Maintenance
            location=base_location,
            contact="",
            last_updated_at=now
        ),
        Resource(
            resource_id="r_zero",
            category=ResourceCategory.rescue,
            sub_type="boat",
            custodian_agency="R1",
            quantity_total=5,
            quantity_available=0, # 0 available
            status=ResourceStatus.available,
            location=base_location,
            contact="",
            last_updated_at=now
        )
    ]
    inc = Incident(
        incident_id="inc_unavail",
        cluster_id="c_unavail",
        location=base_location,
        severity=SeverityEnum.critical,
        flags=Flags(medical_emergency=True, trapped=True),
        first_reported_at=now,
        last_updated_at=now
    )
    created = create_incident(inc, available_resources=resources)
    assert len(created.recommended_resources) == 0

# --- Test 5: Critical incident priority ---
def test_critical_incident_priority(base_location, now):
    res = Resource(
        resource_id="r_single_amb",
        category=ResourceCategory.medical,
        sub_type="ambulance",
        custodian_agency="H1",
        quantity_total=1,
        quantity_available=1,
        status=ResourceStatus.available,
        location=base_location,
        contact="",
        last_updated_at=now
    )
    
    inc_med = Incident(
        incident_id="inc_med",
        cluster_id="c_med",
        location=base_location,
        severity=SeverityEnum.medium,
        flags=Flags(medical_emergency=True),
        first_reported_at=now,
        last_updated_at=now
    )
    create_incident(inc_med, available_resources=[res])
    
    inc_crit = Incident(
        incident_id="inc_crit",
        cluster_id="c_crit",
        location=base_location,
        severity=SeverityEnum.critical,
        flags=Flags(medical_emergency=True),
        first_reported_at=now,
        last_updated_at=now
    )
    create_incident(inc_crit, available_resources=[res])
    
    # Critical incident should get the 1 available ambulance
    crit_recs = INCIDENT_STORE["inc_crit"].recommended_resources
    assert len(crit_recs) == 1
    assert crit_recs[0].resource_id == "r_single_amb"

# --- Test 6: Optimizer failure does not delete incident ---
@patch("app.services.optimizer.incident_service.optimize_allocations")
def test_optimizer_failure_preserves_incident(mock_optimize, base_location, now, sample_resources):
    mock_optimize.side_effect = Exception("OR-Tools Solver Crash")
    
    inc = Incident(
        incident_id="inc_fail_test",
        cluster_id="c_fail",
        location=base_location,
        severity=SeverityEnum.high,
        first_reported_at=now,
        last_updated_at=now
    )
    
    created = create_incident(inc, available_resources=sample_resources)
    assert created.incident_id in INCIDENT_STORE
    assert created.recommended_resources == []

# --- Test 7: Recommendation is not dispatch ---
def test_recommendation_does_not_mutate_quantity_or_dispatch(base_location, now, sample_resources):
    initial_avail = sample_resources[0].quantity_available
    initial_status = sample_resources[0].status
    
    inc = Incident(
        incident_id="inc_nodispatch",
        cluster_id="c_nodispatch",
        location=base_location,
        severity=SeverityEnum.critical,
        flags=Flags(medical_emergency=True),
        first_reported_at=now,
        last_updated_at=now
    )
    created = create_incident(inc, available_resources=sample_resources)
    
    assert len(created.recommended_resources) > 0
    # Resource quantity_available & status must NOT be changed
    assert sample_resources[0].quantity_available == initial_avail
    assert sample_resources[0].status == initial_status
    assert created.assigned_resources == []

# --- Test 8: On-demand endpoint still works ---
def test_on_demand_optimization_path(base_location, now, sample_resources):
    inc1 = Incident(
        incident_id="inc_ondemand_1",
        cluster_id="c1",
        location=base_location,
        severity=SeverityEnum.high,
        first_reported_at=now,
        last_updated_at=now
    )
    inc2 = Incident(
        incident_id="inc_ondemand_2",
        cluster_id="c2",
        location=base_location,
        severity=SeverityEnum.low,
        first_reported_at=now,
        last_updated_at=now
    )
    create_incident(inc1, available_resources=sample_resources)
    create_incident(inc2, available_resources=sample_resources)
    
    # Run on-demand optimization for only inc1
    assignments = run_on_demand_optimization(
        incident_ids=["inc_ondemand_1"],
        resources=sample_resources
    )
    
    assert isinstance(assignments, list)
    for a in assignments:
        assert a["incident_id"] == "inc_ondemand_1"

# --- Test 9: End-to-end realistic multi-incident multi-resource integration ---
def test_e2e_automatic_resource_recommendation_integration(now):
    loc_ward5 = Location(lat=30.316, lng=78.032)
    loc_ward7 = Location(lat=30.325, lng=78.040)
    
    r1 = Resource(
        resource_id="r1_amb",
        category=ResourceCategory.medical,
        sub_type="ambulance",
        custodian_agency="Dehradun Hospital",
        quantity_total=5,
        quantity_available=3,
        status=ResourceStatus.available,
        location=loc_ward5,
        contact="108",
        last_updated_at=now
    )
    r2 = Resource(
        resource_id="r2_boat",
        category=ResourceCategory.rescue,
        sub_type="motorboat",
        custodian_agency="SDRF Uttarakhand",
        quantity_total=2,
        quantity_available=2,
        status=ResourceStatus.available,
        location=loc_ward5,
        contact="112",
        last_updated_at=now
    )
    r3 = Resource(
        resource_id="r3_camp",
        category=ResourceCategory.shelter,
        sub_type="relief_camp",
        custodian_agency="District Admin",
        quantity_total=50,
        quantity_available=50,
        status=ResourceStatus.available,
        location=loc_ward5,
        contact="1077",
        last_updated_at=now
    )
    resources = [r1, r2, r3]

    inc1 = Incident(
        incident_id="inc_ward5",
        cluster_id="cluster_w5",
        location=loc_ward5,
        severity=SeverityEnum.critical,
        estimated_people_affected=12,
        flags=Flags(medical_emergency=True, trapped=True),
        first_reported_at=now,
        last_updated_at=now
    )
    inc2 = Incident(
        incident_id="inc_ward7",
        cluster_id="cluster_w7",
        location=loc_ward7,
        severity=SeverityEnum.high,
        estimated_people_affected=6,
        flags=Flags(medical_emergency=True),
        first_reported_at=now,
        last_updated_at=now
    )

    # 1. Create Incident 1 through application flow
    res1 = create_incident(inc1, available_resources=resources)
    assert len(res1.recommended_resources) > 0

    # 2. Create Incident 2 through application flow
    res2 = create_incident(inc2, available_resources=resources)
    assert len(res2.recommended_resources) > 0

    # 3. Verify total recommended quantities across all incidents <= available
    total_r1 = 0
    total_r2 = 0
    total_r3 = 0

    for stored_inc in INCIDENT_STORE.values():
        for rec in stored_inc.recommended_resources:
            if rec.resource_id == "r1_amb":
                total_r1 += rec.quantity
            elif rec.resource_id == "r2_boat":
                total_r2 += rec.quantity
            elif rec.resource_id == "r3_camp":
                total_r3 += rec.quantity

    assert total_r1 <= 3
    assert total_r2 <= 2
    assert total_r3 <= 50

    # 4. Verify resources are unmutated and no dispatch records exist
    assert r1.quantity_available == 3
    assert r2.quantity_available == 2
    assert r3.quantity_available == 50
    assert res1.assigned_resources == []
    assert res2.assigned_resources == []
