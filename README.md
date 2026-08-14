# SIH Backend — Disaster Response Coordination Platform

Backend services for the SIH 2026 Disaster Response Coordination Platform. This repository contains the FastAPI backend, AI processing (Gemini), resource optimization (OR-Tools), and mock IDRN resource registry.

## Project Structure

```
sih-backend/
├── app/
│   ├── models/
│   │   └── schemas.py            # Pydantic models (Incident, Resource, SOS, etc.)
│   └── services/
│       ├── ai/
│       │   ├── client.py         # Gemini API client initialization
│       │   ├── summarizer.py     # AI-powered incident summarization
│       │   └── situation_brief.py # Situation brief generation
│       ├── optimizer/
│       │   └── allocator.py      # OR-Tools resource allocation engine
│       └── mock_idrn/
│           ├── seed.py           # Database seed script for mock resources
│           └── data.json         # Mock IDRN resource data (Dehradun district)
├── tests/
│   ├── test_optimizer.py         # OR-Tools optimizer unit tests
│   ├── test_ai_summarizer.py     # AI summarizer unit tests
│   └── manual_gemini_test.py     # Manual Gemini integration test
├── demo_optimizer.py             # Standalone optimizer demo script
├── requirements.txt              # Python dependencies
├── .env.example                  # Environment variable template
├── Agent.md                      # Component E implementation notes
├── ApiEndpoints.md               # API endpoint contract reference
└── Logs.md                       # Development log
```

## Prerequisites

- **Python 3.10–3.12** (recommended; 3.13+ may have wheel compatibility issues)
- **pip** (comes with Python)

## Setup

### 1. Clone the repository

```bash
git clone <repository-url>
cd sih-backend
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
| `DATABASE_URL` | Later (Day 5+) | PostgreSQL connection string |
| `JWT_SECRET` | Later (Day 5+) | Secret for authority JWT authentication |

> **Note:** The optimizer and mock IDRN modules work without any environment variables. Only the AI summarization features require `GEMINI_API_KEY`.

### 5. Run the optimizer demo

```bash
python demo_optimizer.py
```

### 6. Run the mock IDRN seed (validation only, no DB yet)

```bash
python -m app.services.mock_idrn.seed
```

### 7. Run tests

```bash
pytest tests/ -v
```

For optimizer tests only:

```bash
pytest tests/test_optimizer.py -v
```

## Current Implementation Status (Day 4)

| Component | Status | Notes |
|---|---|---|
| OR-Tools Optimizer | ✅ Complete | Standalone allocation engine with priority, capacity, and distance constraints |
| Mock IDRN Registry | ✅ Complete | Seed script with real Dehradun district data from IDRN/DDMP |
| Gemini Summarizer | ✅ Complete | Merges multiple SOS reports into one incident summary |
| Situation Brief | ✅ Complete | Generates dashboard situation paragraph |
| API Endpoints | ⏳ Day 5–6 | Not yet wired to FastAPI routers |
| Database Layer | ⏳ Day 5–6 | PostgreSQL/PostGIS integration pending |

## API Contract Reference

See [ApiEndpoints.md](ApiEndpoints.md) for the full endpoint specification.  
See [day1-contracts-and-repo-setup.md](../day1-contracts-and-repo-setup.md) for the canonical schema definitions.

## Team

- **Component D** — Backend Core (auth, routing, DB, WebSocket)
- **Component E** — AI + Resource Intelligence (this repo's services)
