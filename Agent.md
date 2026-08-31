# TRAAN Architecture & Agent Instructions

This document provides the high-level architecture rules and current integration status for AI agents working on the TRAAN project.

## Current Project Status
- **Phase:** 5 (Final Delivery & Verification)
- **Status:** All components (A through F) are fully integrated and verified via physical hardware testing.
- **Mesh Network:** Store-and-Forward routing is confirmed working. Offline SOS signals successfully hop to Gateway devices.
- **Backend:** Component D (Orchestrator) and Component E (AI Logic) have been consolidated into a single FastAPI backend (`/TRAAN_Integration_Phase5`). PostGIS clustering and Gemini AI summarization are fully functional.
- **Dashboard:** The React/Vite dashboard (`/sih-dashboard`) successfully authenticates and displays real-time AI incident summaries and OR-Tools resource dispatch recommendations.
- **device_id Contract:** Fixed (Model A). Android generates a canonical `installationId` UUID at first launch and sends it during registration. The backend accepts and echoes it back, ensuring a single identity for pre-registration and post-registration SOS.

## Core Rules for Modification
1. **No Backend Separation:** Components D and E are consolidated. Do not attempt to split them into separate microservices.
2. **Database:** PostgreSQL with PostGIS is strictly required. No SQLite or MongoDB.
3. **Android Mesh:** Google Nearby Connections (`P2P_CLUSTER`) is the backbone. Do not change this to raw BLE or Wi-Fi Direct.
4. **Offline Testing:** Emulators cannot be used to test the mesh network. Physical devices must be used, and they must have Location services turned ON to permit background BLE scanning. Airplane mode should NOT be used for testing, as it kills required radios.

## Key Technologies
- **Android App:** Kotlin, Coroutines, Room DB, Retrofit, Nearby Connections API.
- **Backend:** Python 3.12, FastAPI, SQLAlchemy (async), asyncpg, GeoAlchemy2, Google Gemini API, Google OR-Tools.
- **Frontend:** React, Vite, Tailwind CSS, Leaflet Maps.
- **Infrastructure:** Docker Compose.
