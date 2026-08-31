import asyncio
import httpx
import uuid
from datetime import datetime, timezone

async def verify():
    base_url = "http://localhost:8000"
    
    # 1. Register a device and get JWT
    print("Registering device...")
    async with httpx.AsyncClient() as client:
        # Model A: Android generates the canonical installation UUID and sends it
        installation_id = str(uuid.uuid4())
        reg_res = await client.post(f"{base_url}/api/v1/auth/device/register", json={
            "device_id": installation_id,
            "device_model": "test-model",
            "app_version": "1.0"
        })
        device_token = reg_res.json()["device_jwt"]
        device_id = reg_res.json()["device_id"]
        assert device_id == installation_id, f"Model A violated: sent {installation_id}, got {device_id}"
        
        # 2. Upload an SOS batch
        print("Uploading SOS batch...")
        sos_uuid = str(uuid.uuid4())
        batch_payload = {
            # gateway_device_id must match device_id (enforced by SOS batch endpoint)
            "gateway_device_id": device_id,
            "gateway_location": {"lat": 30.3165, "lng": 78.0322, "accuracy_m": 5.0},
            "uploaded_at": datetime.now(timezone.utc).isoformat(),
            "sos_batch": [
                {
                    "uuid": sos_uuid,
                    "device_id": device_id,
                    "created_at": datetime.now(timezone.utc).isoformat(),
                    "location": {"lat": 30.3165, "lng": 78.0322, "accuracy_m": 5.0},
                    "is_quick_sos": True,
                    "emergency_type": "medical",
                    "severity_hint": "high",
                    "people_count": 2,
                    "medical_snapshot": {},
                    "custom_message": "Help!",
                    "contact_number": "1234567890",
                    "relay_hop_count": 0,
                    "last_relayed_at": datetime.now(timezone.utc).isoformat(),
                    "status": "pending_local"
                }
            ]
        }
        await client.post(f"{base_url}/api/v1/sos/batch", json=batch_payload, headers={
            "Authorization": f"Bearer {device_token}"
        })
        
        # 3. Generate authority token directly (bypassing login since db may lack seeds)
        print("Logging in as authority (mocked)...")
        from app.core.security import create_authority_jwt
        access_token = create_authority_jwt("mock-admin-id", "admin")
        
        # 4. Fetch incidents and verify structure
        print("Fetching incidents...")
        inc_res = await client.get(f"{base_url}/api/v1/incidents", headers={
            "Authorization": f"Bearer {access_token}"
        })
        incidents = inc_res.json()
        
        if incidents:
            incident = incidents[0]
            print("\n✅ Incident Response verified! It contains the source SOS details:")
            print(f"Incident ID: {incident['incident_id']}")
            print(f"SOS Reports Embedded: {len(incident['source_sos_reports'])}")
            print("First SOS Report details:")
            
            sos_detail = incident['source_sos_reports'][0]
            for key, value in sos_detail.items():
                print(f"  {key}: {value}")
        else:
            print("No incidents found.")

if __name__ == "__main__":
    asyncio.run(verify())
