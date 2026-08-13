"""
FastAPI application entry point.

Routers are registered here with the `/api/v1` prefix.
WebSocket endpoint is registered in Phase 5.

Startup: verifies database connectivity and PostGIS availability.
Shutdown: disposes the async engine connection pool cleanly.
"""

import logging
from contextlib import asynccontextmanager

from fastapi import FastAPI
from fastapi.middleware.cors import CORSMiddleware
from sqlalchemy import text

from app.config import settings
from app.db.engine import engine

# ── Routers (Phase 1: misc only, others added per phase) ──────────────────────
from app.routers import misc

# Phase 2+: uncomment as each router is implemented
from app.routers import auth, sos
from app.routers import incidents
from app.routers import resources
from app.routers import optimizer

logger = logging.getLogger(__name__)


# ── Lifespan (startup / shutdown) ─────────────────────────────────────────────

@asynccontextmanager
async def lifespan(app: FastAPI):
    # ── Startup ────────────────────────────────────────────────────────────────
    logger.info("Starting %s v%s", settings.APP_NAME, settings.APP_VERSION)
    try:
        async with engine.connect() as conn:
            postgis_ver = await conn.execute(text("SELECT PostGIS_Version()"))
            logger.info("PostGIS connected: %s", postgis_ver.scalar_one())
    except Exception as exc:
        logger.error(
            "STARTUP WARNING: cannot reach database — %s. "
            "Endpoints will return DB errors until connectivity is restored.",
            exc,
        )
        # Do NOT crash the app — some endpoints (e.g. /health) still respond
        # and report the DB failure, which is more useful than a dead API.

    yield  # application runs here

    # ── Shutdown ───────────────────────────────────────────────────────────────
    await engine.dispose()
    logger.info("Database connection pool disposed. Shutdown complete.")


# ── Application factory ────────────────────────────────────────────────────────

app = FastAPI(
    title=settings.APP_NAME,
    version=settings.APP_VERSION,
    description=(
        "Disaster Response Coordination Platform — Backend API\n\n"
        "**SIH 2026** · Component D — Backend Core\n\n"
        "This Swagger UI is the live contract that Android, Dashboard, "
        "and AI/Optimizer teams build against. "
        "Do not change endpoint paths, request fields, or response fields "
        "without coordinating with all affected teams."
    ),
    docs_url="/docs",
    redoc_url="/redoc",
    lifespan=lifespan,
)

# ── CORS ──────────────────────────────────────────────────────────────────────
# Allow all origins in development. Tighten to the dashboard origin in production.
app.add_middleware(
    CORSMiddleware,
    allow_origins=settings.CORS_ORIGINS,
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)

# ── Router registration ────────────────────────────────────────────────────────
API_PREFIX = "/api/v1"

app.include_router(misc.router, prefix=API_PREFIX)

# Phase 2+: register additional routers as they are implemented
app.include_router(auth.router,      prefix=API_PREFIX, tags=["auth"])
app.include_router(sos.router,       prefix=API_PREFIX, tags=["sos"])
app.include_router(incidents.router, prefix=f"{API_PREFIX}/incidents", tags=["incidents"])
app.include_router(resources.router, prefix=f"{API_PREFIX}/resources", tags=["resources"])
app.include_router(optimizer.router, prefix=API_PREFIX, tags=["optimizer"])

# Phase 5: WebSocket endpoint (and Phase 4 implementation)
from app.ws.manager import router as ws_router
app.include_router(ws_router)
