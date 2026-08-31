# TRAAN Physical Testing Logs

## August 30, 2026 — Removal of Severity Hint from Frontend
- **Action:** Completely removed the user-facing severity hint selection from the Android app (HomeScreen, HomeViewModel).
- **Action:** Stripped `severityHint` and `SeverityHint` enum from all Android layers (Room entity, SosRepository, Relay models, Network DTOs, and test suites).
- **Action:** Added a Room database migration (version 1 -> 2) to drop the `severity_hint` column from the `sos_request` table.
- **Conclusion:** The Android app no longer generates or sends `severity_hint`. The dashboard and backend retain their severity types, as severity is now purely determined backend-side by AI/objective rules.

---

## August 27, 2026 — device_id Contract Fix Verification (Model A)

### Test 1: Idempotent Registration & Echo
- **Setup:** Backend API running with Model A fixes.
- **Action:** Sent `POST /api/v1/auth/device/register` with a client-generated UUID as `device_id`.
- **Result:** API returned 200 OK and echoed the exact same `device_id`. A second identical request returned 200 OK and a fresh JWT, confirming idempotency. Invalid UUIDs returned 422.
- **Conclusion:** SUCCESS. The backend now respects the Android-generated canonical UUID.

### Test 2: End-to-End SOS Identity Alignment
- **Setup:** Uploading SOS batch via `/api/v1/sos/batch`.
- **Action:** Gateway uploaded an SOS batch containing the registered `device_id`.
- **Result:** Batch accepted. A subsequent `GET /api/v1/sos/{uuid}/status` check using the device's JWT succeeded (200 OK), proving ownership mismatch is resolved.
- **Conclusion:** SUCCESS. The split-identity issue is fully resolved.

---

## August 26, 2026 — End-to-End Mesh Verification

### Test 1: Mesh Transfer Verification (A -> C)
- **Setup:** Two physical devices. Device A (Offline, Mobile Data OFF, Wi-Fi disconnected, Bluetooth/Location ON). Device C (Gateway, Wi-Fi connected to Internet).
- **Action:** SOS triggered from Device A.
- **Local Result:** ADB logs confirmed Device A successfully discovered endpoint `C1JV` and broadcasted SOS payload.
- **Backend Result:** PostgreSQL database confirmed receipt of SOS payload.
  - `relay_hop_count` = 1
  - `device_id` != `received_via_gateway_device_id`
- **Conclusion:** SUCCESS. The Nearby Connections mesh successfully bypassed the internet and routed the SOS to the gateway node.

### Test 2: AI Pipeline & Dashboard Verification
- **Setup:** Backend Docker cluster running with Gemini API enabled. React dashboard open on localhost:5173.
- **Action:** Gateway device uploaded the mesh payload.
- **Result:** 
  - FastAPI accepted the payload (`POST /api/v1/sos/batch`).
  - Spatial clustering (ST_ClusterDBSCAN) correctly grouped the new SOS.
  - Gemini AI generated a situation brief and incident summary.
  - OR-Tools allocated SDRF units and ambulances based on incident severity.
  - Dashboard instantly displayed the new data for the DDMA authority.
- **Conclusion:** SUCCESS. End-to-end flow from offline victim -> offline mesh -> internet gateway -> AI backend -> React dashboard is 100% operational.

### Important Notes on Environment Setup
- Testing the mesh with "Airplane Mode" turned on will fail, because Airplane Mode kills the radios required by Nearby Connections and often blocks background BLE scanning even if manually re-enabled. 
- Location Services MUST be turned on for Android devices to allow BLE discovery.
