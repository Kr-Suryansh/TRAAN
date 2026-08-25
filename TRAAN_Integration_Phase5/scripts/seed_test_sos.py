import asyncio
import httpx
import uuid
from datetime import datetime, timezone

async def verify():
    base_url = "http://localhost:8000"
    
    print("Registering device...")
    async with httpx.AsyncClient() as client:
        reg_res = await client.post(f"{base_url}/api/v1/auth/device/register", json={
            "device_model": "end-to-end-test-model",
            "app_version": "1.0"
        })
        device_token = reg_res.json()["device_jwt"]
        device_id = reg_res.json()["device_id"]
        
        print("Uploading seeded test SOS batch (Flood Scenarios)...")
        
        # Scenario A — Flood + Medical Emergency
        sos_a_uuid = str(uuid.uuid4())
        
        # Scenario B — Flood + Food/Water Shortage
        sos_b_uuid = str(uuid.uuid4())
        
        # Scenario C — Flood + Rescue/Trapped
        sos_c_uuid = str(uuid.uuid4())
        
        # Ensure we place these somewhat geographically apart to test clustering/distance
        batch_payload = {
            "gateway_device_id": device_id,
            "gateway_location": {"lat": 30.3400, "lng": 78.0600, "accuracy_m": 5.0},
            "uploaded_at": datetime.now(timezone.utc).isoformat(),
            "sos_batch": [
                {
                    "uuid": sos_a_uuid,
                    "device_id": device_id,
                    "created_at": datetime.now(timezone.utc).isoformat(),
                    "location": {"lat": 30.3300, "lng": 78.0500, "accuracy_m": 5.0},
                    "is_quick_sos": False,
                    "emergency_type": "medical",
                    "severity_hint": "high",
                    "people_count": 1,
                    "medical_snapshot": {"vulnerable": True},
                    "custom_message": "Water entered the house. Elderly person needs urgent medical help.",
                    "contact_number": "9999999901",
                    "relay_hop_count": 0,
                    "last_relayed_at": datetime.now(timezone.utc).isoformat(),
                    "status": "pending_local"
                },
                {
                    "uuid": sos_b_uuid,
                    "device_id": device_id,
                    "created_at": datetime.now(timezone.utc).isoformat(),
                    "location": {"lat": 30.3600, "lng": 78.0800, "accuracy_m": 5.0}, # Different cluster
                    "is_quick_sos": False,
                    "emergency_type": "flood_rescue",
                    "severity_hint": "medium",
                    "people_count": 4,
                    "medical_snapshot": {},
                    "custom_message": "Family stranded on second floor. No food or drinking water.",
                    "contact_number": "9999999902",
                    "relay_hop_count": 0,
                    "last_relayed_at": datetime.now(timezone.utc).isoformat(),
                    "status": "pending_local"
                },
                {
                    "uuid": sos_c_uuid,
                    "device_id": device_id,
                    "created_at": datetime.now(timezone.utc).isoformat(),
                    "location": {"lat": 30.3800, "lng": 78.1000, "accuracy_m": 5.0}, # Different cluster
                    "is_quick_sos": False,
                    "emergency_type": "trapped",
                    "severity_hint": "critical",
                    "people_count": 3,
                    "medical_snapshot": {},
                    "custom_message": "Flood water rising rapidly. Three people trapped inside the house.",
                    "contact_number": "9999999903",
                    "relay_hop_count": 0,
                    "last_relayed_at": datetime.now(timezone.utc).isoformat(),
                    "status": "pending_local"
                }
            ]
        }
        
        # Because we are processing 3 AI incidents and the optimizer sequentially in the backend
        # the client might timeout if we use default timeout. We set a long timeout.
        timeout = httpx.Timeout(120.0, connect=60.0)
        res = await client.post(
            f"{base_url}/api/v1/sos/batch", 
            json=batch_payload, 
            headers={"Authorization": f"Bearer {device_token}"},
            timeout=timeout
        )
        print(f"SOS Upload Response: {res.status_code}")
        print(res.text)

if __name__ == "__main__":
    asyncio.run(verify())
