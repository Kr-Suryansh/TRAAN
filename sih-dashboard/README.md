# Authority Dashboard — sih-dashboard
**React + Vite + TypeScript Platform (Component 5)**

This repository contains the authority-facing operations dashboard for the disaster response platform, implemented using React, Vite, Leaflet, and WebSockets.

---

## 1. Installation

Ensure Node.js v22+ is installed on your system.

```bash
# Clone the repository and navigate into it
cd sih-dashboard

# Install required dependencies
npm install
```

---

## 2. Configuration (`.env` setup)

Copy `.env.example` to `.env.local` to override configurations:
- `VITE_API_BASE_URL`: REST API endpoint (defaults to `http://localhost:8000/api/v1`)
- `VITE_WS_URL`: WebSocket updates stream (defaults to `ws://localhost:8000/ws/incidents`)
- `VITE_MOCK_MODE`: Set to `true` (default) to run using client-side simulated data matching the Day 1 contract. Set to `false` to connect to a live `sih-backend` server.

---

## 3. Development Commands

### Start Server
Run the local Vite development server on `http://localhost:5173`:
```bash
npm run dev
```

### Run Tests
Executes the Vitest test suite checking logic and human-in-the-loop safeguards:
```bash
npm run test
```

### Type Checking & Compilation
Performs static type analysis using the TypeScript compiler:
```bash
npm run build
```

---

## 4. Implementation Details

- **Strict Schema Compliance**: Every structure is strictly mapped from the `day1-contracts-and-repo-setup.md` specifications in `src/types/index.ts`.
- **Text-Only Design**: Optimized for extreme constraints during disaster settings. Payloads exclude images and audio files.
- **WebSocket Reconnection Resiliency**: The WebSocket connection automatically reattempts links using an exponential backoff strategy if connection disruptions occur.
- **Safety Safeguard**: Dispatch records are not created automatically. The system requires an explicit authority checkbox acknowledgment and manual button click inside the dispatch modal.
