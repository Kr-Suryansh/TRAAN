# TRAAN — Tactical Relay for Alert and Aid Network

**SIH 2026 | Team Project | Status: Fully Integrated & Verified**

TRAAN is an end-to-end disaster management system that enables emergency communication when traditional networks (cell towers, internet) fail. It uses an offline mobile mesh network to propagate SOS signals from stranded victims through relay nodes to an internet-connected gateway device, which uploads the data to an AI-powered backend for spatial clustering, AI analysis, and automated resource dispatch.

---

## Architecture

```
[Device A — Victim/Relay] ──BLE/WiFi-Direct──> [Device C — Gateway]
        (offline, mesh)                               |
                                                      | HTTPS
                                                      v
                                            [Backend D+E — FastAPI]
                                                      |
                                            ┌─────────┴─────────┐
                                        [PostGIS DB]    [Gemini AI + OR-Tools]
                                                      |
                                                      v
                                            [Dashboard F — React/Vite]
```

### Components

| ID | Name | Tech | Location |
|---|---|---|---|
| A | Stranded Victim | Android (Kotlin) | `/TRAAN` |
| B | Relay Node | Android background service | `/TRAAN` |
| C | Gateway Node | Android (WorkManager) | `/TRAAN` |
| D+E | Backend + AI Pipeline | Python, FastAPI, PostGIS, Gemini | `/TRAAN_Integration_Phase5` |
| F | Authority Dashboard | React, Vite, Tailwind, Leaflet | `/sih-dashboard` |

---

## How to Run End-to-End

### 1. Backend (Components D+E)
```bash
cd TRAAN_Integration_Phase5
docker compose up -d
# Health check:
curl http://localhost:8000/api/v1/health
```
Backend available at `http://localhost:8000`. For LAN access from Android: `http://192.168.1.204:8000`.

### 2. Dashboard (Component F)
```bash
cd sih-dashboard
npm install
npm run dev
```
Dashboard at `http://localhost:5173` (LAN: `http://192.168.1.204:5173`).
Login: `demo@sih.gov.in` / `demo1234`

### 3. Android App (Components A, B, C)
1. Build APK via Android Studio → `Build > Build Bundle(s)/APK(s)`
2. Install on physical devices (emulators cannot test the mesh)
3. Grant Bluetooth, Location, and Notification permissions
4. **Gateway device:** Keep Wi-Fi on. Tap "Start Relay"
5. **Victim/Relay devices:** Disconnect Wi-Fi & Mobile Data (**do NOT use Airplane Mode**). Keep Bluetooth + Location ON. Tap "Start Relay"
6. Press SOS button on Device A

---

## Current Status — August 27, 2026

| Item | Status |
|---|---|
| Mesh networking (A→B→C) | ✅ Verified — physical hardware tested |
| Backend + AI pipeline (D+E) | ✅ Fully operational |
| Dashboard (F) | ✅ Live, real-time WebSocket working |
| `device_id` contract | ✅ **Fixed (Model A)** — Android-generated UUID is now canonical |
| LAN connectivity (192.168.1.204) | ✅ Configured |
| Severity feature | ✅ Removed from Android frontend (severity is now strictly backend/AI-derived) |

### Latest Fix — `device_id` Model A (2026-08-27)
The registration endpoint previously generated a new server-side UUID, creating a split identity between offline SOS (`installationId`) and post-registration SOS (backend UUID). This broke `GET /sos/{uuid}/status` ownership checks.

**Resolved:** Android now sends its stable `installationId` as `device_id` during registration. The backend validates, upserts, and echoes it back. One UUID for one device, always.

See [`device-id-contract-inconsistency-finding.md`](./device-id-contract-inconsistency-finding.md) for the original audit.

---

## Key Files

| File | Purpose |
|---|---|
| `Agent.md` | Architecture rules for AI agents working on this project |
| `Logs.md` | Physical testing logs |
| `Summary.md` | Full component architecture breakdown |
| `ApiEndpoints.md` | Quick reference for all API endpoints |
| `TRAAN_Integration_Phase5/ApiEndpoints.md` | Full backend API contract with request/response schemas |
| `device-id-contract-inconsistency-finding.md` | Audit report for the resolved device_id inconsistency |
