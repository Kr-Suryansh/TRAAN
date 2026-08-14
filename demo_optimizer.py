import json
from datetime import datetime
from app.models.schemas import Incident, Resource, SeverityEnum, ResourceCategory, Location, Flags
from app.services.optimizer.allocator import optimize_allocations

def run_demo():
    print("--- Day 4 OR-Tools Optimizer Demo ---")
    now = datetime.utcnow()
    
    # 1. Mock Incidents
    incidents = [
        Incident(
            incident_id="INC-001",
            cluster_id="c_critical",
            location=Location(lat=30.0, lng=78.0), # Near R1 and R2
            severity=SeverityEnum.critical,
            estimated_people_affected=40,
            flags=Flags(medical_emergency=True), # High medical demand
            first_reported_at=now,
            last_updated_at=now
        ),
        Incident(
            incident_id="INC-002",
            cluster_id="c_high",
            location=Location(lat=30.1, lng=78.1), # Further away
            severity=SeverityEnum.high,
            estimated_people_affected=20,
            flags=Flags(trapped=True), # Rescue demand
            first_reported_at=now,
            last_updated_at=now
        )
    ]
    
    # 2. Mock Resources
    resources = [
        Resource(
            resource_id="RES-AMB-001",
            category=ResourceCategory.medical,
            sub_type="ambulance",
            custodian_agency="City Hospital",
            quantity_total=3,
            quantity_available=2, # Only 2 available
            location=Location(lat=30.0, lng=78.0), # Exact same location as INC-001
            contact="",
            last_updated_at=now
        ),
        Resource(
            resource_id="RES-RESCUE-001",
            category=ResourceCategory.rescue,
            sub_type="motorboat",
            custodian_agency="NDRF",
            quantity_total=2,
            quantity_available=2,
            location=Location(lat=30.1, lng=78.1), # Exact same location as INC-002
            contact="",
            last_updated_at=now
        ),
        Resource(
            resource_id="RES-AMB-002",
            category=ResourceCategory.medical,
            sub_type="ambulance",
            custodian_agency="District Hospital",
            quantity_total=1,
            quantity_available=1,
            location=Location(lat=31.0, lng=78.0), # Very far away
            contact="",
            last_updated_at=now
        )
    ]
    
    print("\n[Input Incidents]")
    for i in incidents:
        print(f" - {i.incident_id}: {i.severity.value}, People: {i.estimated_people_affected}, Loc: ({i.location.lat}, {i.location.lng})")
        
    print("\n[Input Resources]")
    for r in resources:
        print(f" - {r.resource_id}: {r.category.value} ({r.sub_type}), Available: {r.quantity_available}, Loc: ({r.location.lat}, {r.location.lng})")
        
    # 3. Optimize
    print("\n[Running OR-Tools Optimizer...]")
    assignments = optimize_allocations(incidents, resources)
    
    print("\n[Optimizer Assignments]")
    if not assignments:
        print(" - No assignments made.")
    for a in assignments:
        print(f" - Incident {a['incident_id']} <- Resource {a['resource_id']} (Qty: {a['quantity']})")
        print(f"   Reasoning: {a['reasoning']}")

if __name__ == "__main__":
    run_demo()
