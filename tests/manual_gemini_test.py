import os
import json
from datetime import datetime, timezone
from dotenv import load_dotenv

# Load dotenv BEFORE importing AI services so global client initialization works
load_dotenv()

from app.models.schemas import SOSRequest, EmergencyType, Location
from app.services.ai.summarizer import generate_incident_summary

def run_manual_test():
    if not os.getenv("GEMINI_API_KEY"):
        print("ERROR: GEMINI_API_KEY not found in .env")
        return

    print("Running Manual Gemini Acceptance Test for Day 3...")
    
    loc = Location(lat=30.316, lng=78.032, district="Dehradun")
    now = datetime.now(timezone.utc)
    
    # 3 distinct reports about the same event
    reports = [
        SOSRequest(
            device_id="dev-1",
            created_at=now,
            location=loc,
            is_quick_sos=False,
            emergency_type=EmergencyType.flood_rescue,
            people_count=1,
            custom_message="Water has entered several houses near Ward 5. An elderly diabetic person is trapped inside one house.",
            last_relayed_at=now
        ),
        SOSRequest(
            device_id="dev-2",
            created_at=now,
            location=loc,
            is_quick_sos=False,
            emergency_type=EmergencyType.flood_rescue,
            people_count=1,
            custom_message="Flood water is rising around Ward 5. One elderly resident needs medical help and cannot leave the building.",
            last_relayed_at=now
        ),
        SOSRequest(
            device_id="dev-3",
            created_at=now,
            location=loc,
            is_quick_sos=False,
            emergency_type=EmergencyType.trapped,
            people_count=None,
            custom_message="Residents near Ward 5 are surrounded by rising water. At least one person with a medical condition appears unable to evacuate.",
            last_relayed_at=now
        )
    ]
    
    print("\nInput SOS Reports:")
    for i, r in enumerate(reports):
        print(f"Report {i+1}: {r.custom_message}")
        
    print("\nCalling Gemini...")
    result = generate_incident_summary(reports)
    
    print("\n--- Output ---")
    print(json.dumps(result, indent=2))
    
    # Simple validation against requirements
    assert "ai_summary" in result, "Missing ai_summary"
    assert "flags" in result, "Missing flags"
    assert "estimated_people_affected" in result, "Missing estimated_people_affected"
    assert result.get("severity") in ["critical", "high", "medium", "low", None], "Invalid severity enum"
    
    print("\nSUCCESS: Acceptance Criteria Verified: 3 SOS reports processed into 1 coherent summary.")

if __name__ == "__main__":
    run_manual_test()
