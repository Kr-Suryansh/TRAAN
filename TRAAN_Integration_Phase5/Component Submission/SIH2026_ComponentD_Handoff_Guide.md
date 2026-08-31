# SIH 2026 — Component D Handoff Guide

**To: Component E (AI/Optimizer), Component F (Dashboard), and Component C (Android) Teams**
**From: Component D (Backend Core) Team**

The backend core is now **100% complete and fully verified**. All infrastructure, authentication, database schemas, deduplication logic, spatial clustering, automated pipelines, and WebSocket endpoints are functioning.

Here is exactly how you need to interact with this repository to complete your parts.

---

## 📱 For Component A, B, & C (Android / Mesh Teams)

Your backend endpoints are fully ready. You do **not** need to modify this repository.

1. **Device Registration:** Call `POST /api/v1/auth/device/register` on app first-launch. Save the `device_jwt` returned.
2. **SOS Uploads:** When connectivity is established, send your clustered/relayed SOS payloads to `POST /api/v1/sos/batch`.
   - **Authentication:** Put the `device_jwt` in the `Authorization: Bearer <token>` header.
   - **Behavior:** The backend will automatically handle exact duplicates, spatial near-duplicates, and spatial clustering using PostGIS DBSCAN.
3. **Status Check:** Call `GET /api/v1/sos/{uuid}/status` to get real-time updates on a specific SOS report.

---

## 🧠 For Component E (AI & Resource Optimizer Team)

We have created the necessary database fields (e.g., `ai_summary`, `recommended_resources`, `flags` on the `Incident` model) and set up the REST API routing. 

However, **we have left temporary boundary stubs for you.** Your job is to replace these stubs with your actual Gemini API and Google OR-Tools logic.

### Where You Need to Write Code:
1. **`app/services/ai/stub.py`**:
   - Replace the `summarize_incident` function. It currently returns a hardcoded mock string. You need to pull the `Incident` and its `SOSReports` from the database, call the Gemini API, and return the generated insights and `IncidentFlags`.
   - Replace the `generate_situation_brief` function to aggregate all active incidents and query Gemini for a global summary.
2. **`app/services/optimizer/stub.py`**:
   - Replace the `allocate_resources` function. It currently logs a warning and returns an empty list. You need to take the incident data, available resources in the district, and run your Google OR-Tools optimization model to return an optimal `DispatchPlan`.
3. **`scripts/seed.py` (To be created by you)**:
   - You need to write a simple Python script to parse the IDRN (India Disaster Resource Network) dataset and populate the `resource` database table so your optimizer has data to work with.

*Note: You do not need to touch `app/routers/optimizer.py` — we already wired the REST endpoints to point to your stubs.*

---

## 💻 For Component F (Dashboard Frontend Team)

Your API endpoints and real-time WebSockets are ready. You do **not** need to modify this repository.

### How to Connect:
1. **Authentication:** Call `POST /api/v1/auth/authority/login` to get an `access_token`.
2. **REST APIs:** Use the `access_token` in the `Authorization: Bearer <token>` header to call:
   - `GET /api/v1/incidents` (Map data)
   - `GET /api/v1/resources` (Resource tables)
   - `GET /api/v1/stats/summary` (Top-level KPI widgets)
3. **WebSockets (Real-time Updates):**
   - Connect your WebSocket client to: `ws://<backend_url>/ws/incidents?token=<your_access_token>`
   - The backend will push the following events to you as JSON strings:
     - `incident_created`: A new SOS cluster was formed. Add it to the map.
     - `incident_updated`: The AI updated the summary, or the status changed.
     - `incident_dispatched`: A vehicle/resource was deployed.
     - `resource_updated`: Resource inventory dropped.

**Documentation:** Read the `ApiEndpoints.md` file in the root of the repository for exact JSON request/response schemas.
