import pytest
from datetime import datetime
from app.models.schemas import Incident, Resource, SeverityEnum, ResourceCategory, Location, ResourceStatus, Flags
from app.services.optimizer.allocator import optimize_allocations

@pytest.fixture
def base_location():
    return Location(lat=30.0, lng=78.0)

@pytest.fixture
def now():
    return datetime.utcnow()

# --- Test 1: Valid assignment ---
def test_valid_assignment(base_location, now):
    incidents = [
        Incident(
            cluster_id="c1",
            location=base_location,
            severity=SeverityEnum.critical,
            estimated_people_affected=10,
            flags=Flags(medical_emergency=True),
            first_reported_at=now,
            last_updated_at=now
        )
    ]
    resources = [
        Resource(
            category=ResourceCategory.medical,
            sub_type="ambulance",
            custodian_agency="H1",
            quantity_total=5,
            quantity_available=5,
            location=base_location,
            contact="",
            last_updated_at=now
        )
    ]
    assignments = optimize_allocations(incidents, resources)
    assert len(assignments) > 0
    for a in assignments:
        assert "incident_id" in a
        assert "resource_id" in a
        assert "quantity" in a
        assert "reasoning" in a
        assert isinstance(a["quantity"], int)
        assert a["quantity"] > 0

# --- Test 2: Capacity constraint ---
def test_capacity_constraint(base_location, now):
    incidents = [
        Incident(
            cluster_id=f"c{i}",
            location=base_location,
            severity=SeverityEnum.critical,
            estimated_people_affected=50,
            flags=Flags(medical_emergency=True),
            first_reported_at=now,
            last_updated_at=now
        ) for i in range(5)
    ]
    # Only 3 ambulances available
    resources = [
        Resource(
            category=ResourceCategory.medical,
            sub_type="ambulance",
            custodian_agency="H1",
            quantity_total=5,
            quantity_available=3,
            location=base_location,
            contact="",
            last_updated_at=now
        )
    ]
    assignments = optimize_allocations(incidents, resources)
    total_assigned = sum(a["quantity"] for a in assignments if a["resource_id"] == resources[0].resource_id)
    assert total_assigned <= 3
    # Actually, it should be exactly 3 since demand is high
    assert total_assigned == 3

# --- Test 3: No unavailable resources ---
def test_no_unavailable_resources(base_location, now):
    incidents = [
        Incident(
            cluster_id="c1",
            location=base_location,
            severity=SeverityEnum.critical,
            flags=Flags(medical_emergency=True),
            first_reported_at=now,
            last_updated_at=now
        )
    ]
    resources = [
        Resource(
            category=ResourceCategory.medical,
            sub_type="ambulance",
            custodian_agency="H1",
            quantity_total=5,
            quantity_available=5,
            status=ResourceStatus.maintenance,
            location=base_location,
            contact="",
            last_updated_at=now
        ),
        Resource(
            category=ResourceCategory.medical,
            sub_type="stretcher",
            custodian_agency="H1",
            quantity_total=5,
            quantity_available=0, # 0 available
            status=ResourceStatus.available,
            location=base_location,
            contact="",
            last_updated_at=now
        )
    ]
    assignments = optimize_allocations(incidents, resources)
    assert len(assignments) == 0

# --- Test 4: Priority behavior ---
def test_priority_behavior(base_location, now):
    # Two incidents competing for 1 ambulance
    incidents = [
        Incident(
            cluster_id="c_medium",
            location=base_location,
            severity=SeverityEnum.medium,
            flags=Flags(medical_emergency=True),
            first_reported_at=now,
            last_updated_at=now
        ),
        Incident(
            cluster_id="c_critical",
            location=base_location,
            severity=SeverityEnum.critical,
            flags=Flags(medical_emergency=True),
            first_reported_at=now,
            last_updated_at=now
        )
    ]
    resources = [
        Resource(
            category=ResourceCategory.medical,
            sub_type="ambulance",
            custodian_agency="H1",
            quantity_total=1,
            quantity_available=1,
            location=base_location,
            contact="",
            last_updated_at=now
        )
    ]
    assignments = optimize_allocations(incidents, resources)
    assert len(assignments) == 1
    # Critical incident should get it
    assert assignments[0]["incident_id"] == incidents[1].incident_id

# --- Test 5: Resource suitability ---
def test_resource_suitability(base_location, now):
    incidents = [
        Incident(
            cluster_id="c_fire",
            location=base_location,
            severity=SeverityEnum.high,
            flags=Flags(trapped=True), # Needs rescue
            first_reported_at=now,
            last_updated_at=now
        )
    ]
    resources = [
        Resource(
            category=ResourceCategory.medical,
            sub_type="ambulance",
            custodian_agency="H1",
            quantity_total=2,
            quantity_available=2,
            location=base_location,
            contact="",
            last_updated_at=now
        ),
        Resource(
            category=ResourceCategory.rescue,
            sub_type="fire_engine",
            custodian_agency="F1",
            quantity_total=2,
            quantity_available=2,
            location=base_location,
            contact="",
            last_updated_at=now
        )
    ]
    assignments = optimize_allocations(incidents, resources)
    # The incident has trapped=True, so rescue demand is > 0, medical demand is 0 (since no medical flags or critical)
    # Wait, high severity gives medical += 2 and rescue += 1.
    # So it might assign both. But rescue is more suitable for trapped? Actually, the heuristics assign demand.
    # Let's check that rescue is assigned.
    rescue_assigned = any(a["resource_id"] == resources[1].resource_id for a in assignments)
    assert rescue_assigned

# --- Test 6: Location/cost behavior ---
def test_location_behavior(now):
    incident_loc = Location(lat=30.0, lng=78.0)
    incidents = [
        Incident(
            cluster_id="c1",
            location=incident_loc,
            severity=SeverityEnum.critical,
            flags=Flags(medical_emergency=True),
            first_reported_at=now,
            last_updated_at=now
        )
    ]
    # Resource 1 is far away (~111km per degree lat)
    loc_far = Location(lat=31.0, lng=78.0)
    # Resource 2 is exactly at the incident
    loc_near = Location(lat=30.0, lng=78.0)
    
    resources = [
        Resource(
            category=ResourceCategory.medical,
            sub_type="ambulance",
            custodian_agency="H_far",
            quantity_total=1,
            quantity_available=1,
            location=loc_far,
            contact="",
            last_updated_at=now
        ),
        Resource(
            category=ResourceCategory.medical,
            sub_type="ambulance",
            custodian_agency="H_near",
            quantity_total=1,
            quantity_available=1,
            location=loc_near,
            contact="",
            last_updated_at=now
        )
    ]
    # Limit incident demand so it only needs 1 ambulance. Critical + medical flag = 5 medical demand.
    # Let's change the incident to medium + medical = 3 demand, and available is 1 and 1. It will take both.
    # Let's change the heuristic demand by mocking it or just providing 1 incident and two resources, but 
    # we only have capacity for 1? No, we can just look at a scenario where demand is 1.
    # To force demand = 1, severity = low, no flags, but low severity has 0 medical demand...
    # Actually we can just run it, OR tools maximizes. If demand > available, it will try to assign all.
    # Let's limit demand by creating 2 incidents, 1 critical, 1 low.
    
    # Alternatively, just use an incident with demand 1.
    # The heuristic: severity=medium -> medical 1. flags=0. 
    incident_medium = Incident(
        cluster_id="c1",
        location=incident_loc,
        severity=SeverityEnum.medium,
        estimated_people_affected=0,
        first_reported_at=now,
        last_updated_at=now
    )
    # Demand for medium is: medical=1. So it will only allocate 1 medical resource.
    
    assignments = optimize_allocations([incident_medium], resources)
    assert len(assignments) == 1
    # Should choose the nearer resource
    assert assignments[0]["resource_id"] == resources[1].resource_id

# --- Test 7: No incidents ---
def test_no_incidents(base_location, now):
    resources = [
        Resource(
            category=ResourceCategory.medical,
            sub_type="ambulance",
            custodian_agency="H1",
            quantity_total=1,
            quantity_available=1,
            location=base_location,
            contact="",
            last_updated_at=now
        )
    ]
    assignments = optimize_allocations([], resources)
    assert assignments == []

# --- Test 8: No available resources ---
def test_no_resources(base_location, now):
    incidents = [
        Incident(
            cluster_id="c1",
            location=base_location,
            severity=SeverityEnum.critical,
            first_reported_at=now,
            last_updated_at=now
        )
    ]
    assignments = optimize_allocations(incidents, [])
    assert assignments == []

# --- Test 9: Partially deployed resources are eligible ---
def test_partially_deployed_resources_eligible(base_location, now):
    incidents = [
        Incident(
            cluster_id="c_partial",
            location=base_location,
            severity=SeverityEnum.critical,
            flags=Flags(medical_emergency=True),
            first_reported_at=now,
            last_updated_at=now
        )
    ]
    resources = [
        Resource(
            resource_id="r_partially_deployed",
            category=ResourceCategory.medical,
            sub_type="ambulance",
            custodian_agency="H1",
            quantity_total=10,
            quantity_available=3,
            status=ResourceStatus.partially_deployed, # Partially deployed!
            location=base_location,
            contact="108",
            last_updated_at=now
        )
    ]
    assignments = optimize_allocations(incidents, resources)
    assert len(assignments) > 0
    assert assignments[0]["resource_id"] == "r_partially_deployed"

# --- Test 10: Optimizer constraints behavior ---
def test_optimizer_constraints(now):
    inc_loc = Location(lat=30.0, lng=78.0)
    incidents = [
        Incident(
            incident_id="inc_constraint_1",
            cluster_id="c1",
            location=inc_loc,
            severity=SeverityEnum.critical,
            flags=Flags(medical_emergency=True, trapped=True),
            first_reported_at=now,
            last_updated_at=now
        )
    ]
    res_near = Resource(
        resource_id="r_near_amb",
        category=ResourceCategory.medical,
        sub_type="ambulance",
        custodian_agency="H1",
        quantity_total=5,
        quantity_available=5,
        status=ResourceStatus.available,
        location=Location(lat=30.01, lng=78.01), # ~1.5km
        contact="108",
        last_updated_at=now
    )
    res_far = Resource(
        resource_id="r_far_boat",
        category=ResourceCategory.rescue,
        sub_type="boat",
        custodian_agency="SDRF",
        quantity_total=5,
        quantity_available=5,
        status=ResourceStatus.available,
        location=Location(lat=31.0, lng=78.0), # ~111km
        contact="112",
        last_updated_at=now
    )
    all_resources = [res_near, res_far]

    # Subtest A: Excluded resource IDs
    assign_excl = optimize_allocations(incidents, all_resources, constraints={"excluded_resource_ids": ["r_near_amb"]})
    assigned_ids = [a["resource_id"] for a in assign_excl]
    assert "r_near_amb" not in assigned_ids

    # Subtest B: Maximum distance constraint (5km)
    assign_dist = optimize_allocations(incidents, all_resources, constraints={"maximum_distance": 5.0})
    assigned_ids_dist = [a["resource_id"] for a in assign_dist]
    assert "r_near_amb" in assigned_ids_dist
    assert "r_far_boat" not in assigned_ids_dist

    # Subtest C: Required categories constraint
    assign_cat = optimize_allocations(incidents, all_resources, constraints={"required_resource_categories": ["rescue"]})
    assigned_cats = [a["resource_id"] for a in assign_cat]
    assert "r_near_amb" not in assigned_cats

    # Subtest D: Maximum allocation cap
    assign_cap = optimize_allocations(incidents, all_resources, constraints={"maximum_allocation": 1})
    for a in assign_cap:
        assert a["quantity"] <= 1

