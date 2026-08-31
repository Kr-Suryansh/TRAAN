# TRAAN Project Summary

## Project Overview
TRAAN is an end-to-end disaster management system designed to facilitate emergency communication when traditional networks (cell towers, internet) fail. It uses an offline mobile mesh network to propagate SOS signals from stranded victims to an internet-connected gateway, which then routes the data to an AI-powered backend for analysis, clustering, and resource allocation.

## Components Architecture

The system is composed of six distinct functional components (A through F):

### 1. Component A (Stranded Victim)
**Role:** The offline user device.
**Implementation:** Android App (`/TRAAN`).
**Functionality:** Broadcasts SOS signals over Google Nearby Connections using a combination of Bluetooth LE and Wi-Fi Direct. Does not require an internet connection.

### 2. Component B (Relay Node)
**Role:** The offline mesh router.
**Implementation:** Android App (`/TRAAN` background service).
**Functionality:** Receives SOS signals from Component A devices and stores them locally. It actively scans for other devices to propagate the SOS further across the mesh (Store and Forward routing).

### 3. Component C (Gateway Node)
**Role:** The internet bridge.
**Implementation:** Android App (`/TRAAN`).
**Functionality:** A device on the periphery of the disaster zone that has regained internet access. It receives payloads from the offline mesh (via Nearby Connections) and uses a WorkManager task (`GatewaySyncWorker`) to upload the batched SOS data to the backend.

### 4. Component D & E (Unified Backend & AI Pipeline)
**Role:** Data ingestion, spatial clustering, AI analysis, and resource optimization.
**Implementation:** FastAPI, PostgreSQL/PostGIS, Docker (`/TRAAN_Integration_Phase5`).
*Note: Originally planned as separate components, D and E were successfully consolidated into a single highly-efficient backend.*
**Functionality:** 
- Ingests SOS batches and drops spatial/temporal duplicates.
- Uses PostGIS ST_ClusterDBSCAN to group nearby SOS reports into `Incidents`.
- Uses Google Gemini AI to analyze incidents, assign severity, and generate human-readable summaries.
- Uses Google OR-Tools to solve the constraint programming problem of allocating limited resources (ambulances, helicopters, SDRF units) to high-severity incidents based on distance and capacity.

### 5. Component F (Dashboard)
**Role:** The Authority Control Center.
**Implementation:** React, Vite, TailwindCSS (`/sih-dashboard`).
**Functionality:** A real-time web interface for Disaster Management Authorities. Displays AI-generated incident reports, a map of the disaster zone, situation briefings, and recommended resource dispatches. 

## Integration & Testing Verification

The entire `A -> B -> C -> Backend -> Dashboard` pipeline has been successfully integrated and verified with physical hardware testing.

**Mesh Network Verification:**
- Physical testing confirmed that Device A successfully generates an SOS which stays entirely local.
- Device A successfully discovers and transfers the payload to an internet-connected Device C.
- The `relay_hop_count` was confirmed as `1` in the database, proving the mesh successfully bypassed the internet for the first leg of the journey.

**Backend & Dashboard Verification:**
- The FastAPI backend successfully processes the incoming batches, hydrates the PostgreSQL database, and correctly triggers the Gemini AI.
- The React Dashboard accurately fetches this data and visualizes the incident details, severity, and OR-Tools recommendations.
- **device_id Contract:** Successfully fixed using Model A. The backend accepts the Android-generated `installationId` as the canonical `device_id`, completely resolving the offline vs online split-identity issue.

## How to Run End-to-End

### 1. Start the Backend (D+E)
```bash
cd TRAAN_Integration_Phase5
docker compose up -d
```
*Requires Docker. The backend runs on `http://localhost:8000`.*

### 2. Start the Dashboard (F)
```bash
cd sih-dashboard
npm install
npm run dev
```
*The dashboard will be available at `http://localhost:5173`. Use `demo@sih.gov.in` / `demo1234`.*

### 3. Run the Android App (A, B, C)
1. Install `app-debug.apk` on physical Android devices.
2. Grant Bluetooth, Location, and Notification permissions.
3. **Gateway (C):** Keep connected to the internet. Enable "Start Relay".
4. **Victim/Relay (A & B):** Disconnect from Wi-Fi and Mobile Data (Do NOT use Airplane Mode). Ensure Bluetooth and Location are ON. Enable "Start Relay".
5. Press the SOS button on Device A to begin the mesh propagation.
