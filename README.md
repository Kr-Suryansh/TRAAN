# SIH Backend — Disaster Response Coordination Platform

Backend services for Component E (AI + Resource Intelligence) of the SIH 2026 Disaster Response Coordination Platform. This repository contains the AI processing services (Gemini API), resource allocation optimizer (Google OR-Tools), and mock IDRN resource registry.

## Project Structure

```
sih-backend/
├── app/
│   ├── db/
│   │   ├── database.py           # SQLAlchemy Engine, SessionLocal, init_db, get_db
│   │   └── models.py             # ResourceModel and IncidentModel DB tables
│   ├── models/
│   │   └── schemas.py            # Shared Pydantic models & enums (Incident, Resource, SOS, etc.)
│   └── services/
│       ├── ai/
│       │   ├── client.py         # Gemini API client initialization (google-genai==1.5.0)
│       │   ├── summarizer.py     # AI-powered incident summarization & fallback safety
│       │   └── situation_brief.py # Situation brief generation, cache & idempotent worker
│       ├── optimizer/
│       │   ├── allocator.py      # OR-Tools CP-SAT resource allocation engine
│       │   ├── incident_service.py # Incident lifecycle & DB-backed optimization logic
│       │   └── resource_registry.py # DB-backed resource query helper functions
│       └── mock_idrn/
│           ├── seed.py           # Database seed script for mock resources
│           └── data.json         # Mock IDRN resource data (Dehradun district)
├── tests/
│   ├── test_optimizer.py         # OR-Tools optimizer unit tests
│   ├── test_ai_summarizer.py     # AI summarizer unit tests
│   ├── test_db_seed.py           # Database seed & persistence tests
│   ├── test_incident_integration.py # Incident lifecycle integration tests
│   ├── test_p0_remediation.py    # Remediation tests (null severity, restart recovery)
│   ├── test_situation_brief.py   # Situation brief unit & worker idempotency tests
│   └── manual_gemini_test.py     # Manual Gemini integration test
├── demo_optimizer.py             # Standalone optimizer demo script
├── requirements.txt              # Python dependencies (pinned)
├── .env.example                  # Environment variable template
├── Agent.md                      # Component E integration documentation
├── ApiEndpoints.md               # API endpoint contract reference
└── Logs.md                       # Development & remediation log
```

## Prerequisites

- **Python 3.10–3.12**
- **pip** (comes with Python)

## Setup

### 1. Clone the repository

```bash
git clone <repository-url>
cd TRAAN
```

### 2. Create a virtual environment

```bash
python -m venv .venv
```

Activate it:

- **Windows (PowerShell):** `.venv\Scripts\Activate.ps1`
- **Windows (CMD):** `.venv\Scripts\activate.bat`
- **macOS/Linux:** `source .venv/bin/activate`

### 3. Install dependencies

```bash
pip install -r requirements.txt
```

### 4. Configure environment variables

```bash
cp .env.example .env
```

Edit `.env` and fill in your real values:

| Variable | Required | Description |
|---|---|---|
| `GEMINI_API_KEY` | Yes (for AI features) | Google Gemini API key from [AI Studio](https://aistudio.google.com/apikey) |
| `DATABASE_URL` | Optional | PostgreSQL connection string (defaults to local `traan.db` SQLite if unset for dev/testing) |

### 5. Run the optimizer demo

```bash
python demo_optimizer.py
```

### 6. Run the mock IDRN seed

```bash
python -m app.services.mock_idrn.seed
```

### 7. Run test suite (52 tests)

```bash
pytest tests/ -v
```

## Current Implementation Status

| Component | Status | Notes |
|---|---|---|
| OR-Tools Optimizer | ✅ Complete | CP-SAT solver with priority, capacity, distance, suitability & custom constraint validation |
| Mock IDRN Registry | ✅ Complete | Database seed script with Dehradun district public data |
| Gemini Summarizer | ✅ Complete | Structured Pydantic output (`google-genai==1.5.0`) with failure safety & injection defense |
| Situation Brief | ✅ Complete | Cached brief generation, thread-safe lock & idempotent worker lifecycle |
| Database Layer | ✅ Complete | Database-backed persistence for incidents & resources; Component E acts as a service layer consuming Component D's canonical DB architecture |

## API Contract Reference

See [ApiEndpoints.md](ApiEndpoints.md) for the full endpoint specification.  
See [day1-contracts-and-repo-setup.md](../day1-contracts-and-repo-setup.md) for the canonical schema definitions.

## Team Ownership

- **Component D** — Backend Core (PostgreSQL/PostGIS DB architecture, auth, REST routing, WebSocket)
- **Component E** — AI + Resource Intelligence (Gemini summarization/briefs, OR-Tools optimizer, mock IDRN seed logic)
