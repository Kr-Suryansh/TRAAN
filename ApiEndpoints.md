# ApiEndpoints.md — sih-android (Agent C)
> Disaster Response Coordination Platform · SIH 2026  
> Component: Android App Shell  
> **Master Source of Truth:** `day1-contracts-and-repo-setup.md` § 2. REST API Endpoints  
> **This file follows the master blueprint exactly. Any proposed deviations are flagged explicitly.**  
> **Last Updated:** 2026-08-13 (Day 8 — integration hardening complete)

---

## Base URL
```
/api/v1
```
Full URL in dev: `http://<backend-host>:8000/api/v1`

---

## Endpoints Consumed by Agent C

### 1. Device Registration
**`POST /api/v1/auth/device/register`**

| Field | Value |
|-------|-------|
| Auth | None |
| When called | First app launch only (checks stored `device_jwt` first) |
| Called from | `DeviceRegistrationRepository` → triggered from `MainActivity` / `Application.onCreate()` |

**Request Body:**
```json
{
  "device_model": "string (Build.MODEL)",
  "app_version": "string (BuildConfig.VERSION_NAME)"
}
```

**Response (200 OK):**
```json
{
  "device_id": "string (UUID)",
  "device_jwt": "string (JWT)"
}
```

**Storage:** Both fields stored in `EncryptedSharedPreferences` under keys `pref_device_id` and `pref_device_jwt`.

**Error Handling:**
- Network failure → retry with exponential backoff (WorkManager one-time request)
- 4xx → log, do not crash; app operates in offline-only mode until registration succeeds
- On 401 from any subsequent call → re-call this endpoint to refresh credentials

---

### 2. SOS Batch Upload
**`POST /api/v1/sos/batch`**

| Field | Value |
|-------|-------|
| Auth | `Authorization: Bearer <device_jwt>` |
| When called | `GatewaySyncWorker` (WorkManager, constrained to `NetworkType.CONNECTED`) **AND** immediately after `SosRepository.createSos()` via `enqueueImmediateGatewaySync()` (one-shot, KEEP policy) |
| Called from | `:data` module `GatewaySyncWorker` |

**Request Body (`GatewayUploadBatch`):**
```json
{
  "gateway_device_id": "string (stored device_id)",
  "gateway_location": {
    "lat": "float (last known location, or 0.0 if unavailable)",
    "lng": "float (last known location, or 0.0 if unavailable)"
  },
  "uploaded_at": "string (ISO8601, current time)",
  "sos_batch": [
    {
      "uuid": "string (UUIDv4)",
      "device_id": "string",
      "created_at": "string (ISO8601)",
      "location": {
        "lat": "float",
        "lng": "float",
        "accuracy_m": "float | null"
      },
      "is_quick_sos": "boolean",
      "emergency_type": "medical | trapped | structural_collapse | flood_rescue | fire | missing_person | unspecified",
      "severity_hint": "critical | high | medium | low | null",
      "people_count": "integer | null",
      "medical_snapshot": "UserMedicalProfile | null",
      "custom_message": "string | null",
      "contact_number": "string | null",
      "relay_hop_count": "integer",
      "last_relayed_at": "string (ISO8601)",
      "status": "string"
    }
  ]
}
```

**Response (202 Accepted):**
```json
{
  "accepted_uuids": ["string", "..."],
  "duplicate_uuids": ["string", "..."]
}
```

**Post-upload behaviour:**
- All UUIDs in `accepted_uuids` → update local `SosRequestEntity.status = uploaded`
- All UUIDs in `duplicate_uuids` → also mark as `uploaded` (backend already has them)
- Records are **NOT deleted** after upload — kept until TTL expiry for relay purposes

**Error Handling:**
- 401 → refresh device_jwt, retry
- 4xx (other) → log per-batch failure, WorkManager retry with backoff
- 5xx → WorkManager exponential backoff
- Network timeout → WorkManager retry

---

### 3. SOS Status Check
**`GET /api/v1/sos/{uuid}/status`**

| Field | Value |
|-------|-------|
| Auth | `Authorization: Bearer <device_jwt>` |
| When called | `StatusScreen` on load **AND** automatically when device connectivity returns (via `ConnectivityManager.NetworkCallback` in `StatusViewModel`) — only when `NetworkType.CONNECTED` |
| Called from | `:app` `StatusViewModel` |

**Path Parameter:** `uuid` — the UUIDv4 of the SOS to check.

**Response (200 OK):**
```json
{
  "status": "string (backend-defined status)"
}
```

> [!NOTE]
> **Open Question (flagged for Agent D):** The contract defines `{status}` but does not enumerate backend-side status values. Agent C will treat any non-null `status` value as "delivered to backend" and display it verbatim. If Agent D defines specific values (e.g. `received`, `clustered`, `dispatched`), Agent C will map them to user-friendly display strings without changing the API.

**Error Handling:**
- 401 → refresh device_jwt, retry
- 404 → SOS not yet received by backend → display "Not yet received — still in relay"
- Network failure → display cached local status only
- Show "pending_local" or "in_relay" from local Room DB if offline

---

## Endpoints NOT Consumed by Agent C

These are defined in the master contract but belong to other components:

| Endpoint | Owner |
|----------|-------|
| `POST /api/v1/auth/authority/login` | Agent F (Dashboard) |
| `POST /api/v1/auth/authority/refresh` | Agent F (Dashboard) |
| `GET /api/v1/incidents/*` | Agent F (Dashboard) |
| `PATCH /api/v1/incidents/*` | Agent F (Dashboard) |
| `POST /api/v1/incidents/*/dispatch` | Agent F (Dashboard) |
| `GET /api/v1/resources/*` | Agent F (Dashboard) |
| `GET /api/v1/situation-brief` | Agent F (Dashboard) |
| `POST /api/v1/optimize/allocate` | Agent F (Dashboard) |
| `GET /api/v1/health` | DevOps / all |
| `WS /ws/incidents` | Agent F (Dashboard) |

---

## Proposed Contract Deviations

**None.** All endpoints used by Agent C are consumed exactly as specified in `day1-contracts-and-repo-setup.md`.

The only open questions (flagged above) are **ambiguities to clarify with Agent D**, not proposed changes.

---

## Notes on `gateway_location` (Potential Ambiguity)

The `GatewayUploadBatch.gateway_location` schema defines `lat: float, lng: float` without a null/optional marker. However, the gateway device may not always have a fresh location when the upload triggers (user indoors, no GPS fix).

**Agent C's approach:**
- Use `FusedLocationProviderClient.lastLocation` to get last known location
- If null → send `{ "lat": 0.0, "lng": 0.0 }` and log a warning
- **Never block the upload** for a missing gateway location
- If Agent D adds optional handling for this, no API change is needed — the field is still sent, just with sentinel values

**Flagged to Agent D** for awareness. No contract change proposed.
