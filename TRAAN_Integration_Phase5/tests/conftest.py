"""
Test suite — SIH Backend Core.

conftest.py provides shared fixtures for all tests.

Event loop architecture:
  pytest.ini uses asyncio_mode = auto and asyncio_default_fixture_loop_scope = session.
  A single event loop is shared across all tests in the session.

  SQLAlchemy engine is created with NullPool for tests to prevent asyncpg
  connection pool conflicts between tests. With NullPool, every `async with
  AsyncSessionLocal()` opens a brand-new connection and closes it on exit —
  no pooling, no cross-loop state.

Fixtures:
  client            — async HTTPX test client (function-scoped)
  cleanup_sos_data  — deletes sos_report rows before+after each test that writes SOS data
"""

import sys
import pytest
from httpx import AsyncClient, ASGITransport
from sqlalchemy import text
from sqlalchemy.ext.asyncio import create_async_engine, async_sessionmaker, AsyncSession
from sqlalchemy.pool import NullPool

from app.core.security import hash_password
from app.db._models.authority import Authority

from app.config import settings
import app.db.engine  # Ensures the module is loaded into sys.modules

# ── Override engine with NullPool for tests ────────────────────────────────────
# This prevents asyncpg connection pool state from leaking between tests.
# Must happen before any test imports app.main (which transitively uses the engine).

# Force isolated test database
if "sih_test_db" not in settings.DATABASE_URL:
    settings.DATABASE_URL = settings.DATABASE_URL.replace("sih_db", "sih_test_db")

_test_engine = create_async_engine(
    settings.DATABASE_URL,
    echo=False,
    poolclass=NullPool,
)
_TestSessionLocal = async_sessionmaker(
    _test_engine,
    class_=AsyncSession,
    expire_on_commit=False,
)

# Retrieve the actual module from sys.modules to avoid the naming collision 
# with the 'engine' variable exported in app/db/__init__.py
db_engine_module = sys.modules["app.db.engine"]

# Patch the module-level engine and session factory used by the app
db_engine_module.engine = _test_engine
db_engine_module.AsyncSessionLocal = _TestSessionLocal


# Now import the app AFTER patching the engine so all routers use the NullPool engine
from app.main import app  # noqa: E402


# ── Per-test HTTP client ──────────────────────────────────────────────────────

@pytest.fixture
async def client() -> AsyncClient:
    """
    Async HTTP test client backed by the FastAPI ASGI app.
    Routers use get_db() which calls AsyncSessionLocal() — our patched NullPool version.
    """
    async with AsyncClient(
        transport=ASGITransport(app=app),
        base_url="http://testserver",
    ) as ac:
        yield ac


# ── SOS data isolation ────────────────────────────────────────────────────────

@pytest.fixture
async def cleanup_sos_data():
    """
    Deletes all sos_report rows before and after each test.
    Tests that write SOS data must declare this fixture to prevent
    spatial near-duplicate false positives from previous test runs.
    """
    async with _TestSessionLocal() as db:
        await db.execute(text("DELETE FROM sos_report"))
        await db.commit()

    yield

    async with _TestSessionLocal() as db:
        await db.execute(text("DELETE FROM sos_report"))
        await db.commit()

# ── Authority & Auth ──────────────────────────────────────────────────────────

@pytest.fixture
async def setup_authority():
    from sqlalchemy import delete
    async with _TestSessionLocal() as db:
        await db.execute(delete(Authority).where(Authority.email == "test@sih.gov.in"))
        
        auth = Authority(
            email="test@sih.gov.in",
            hashed_password=hash_password("password123"),
            role="admin",
        )
        db.add(auth)
        await db.commit()
        await db.refresh(auth)
        
        try:
            yield auth
        finally:
            await db.execute(delete(Authority).where(Authority.email == "test@sih.gov.in"))
            await db.commit()

# ── Phase 4 Data Isolation ────────────────────────────────────────────────────

@pytest.fixture
async def cleanup_phase4_data():
    """
    Deletes all records from incident, resource, and dispatch_record before/after each test.
    This ensures tests have a clean slate for Phase 4 logic.
    """
    async with _TestSessionLocal() as db:
        await db.execute(text("DELETE FROM dispatch_record"))
        await db.execute(text("DELETE FROM incident"))
        await db.execute(text("DELETE FROM resource"))
        await db.commit()

    yield

    async with _TestSessionLocal() as db:
        await db.execute(text("DELETE FROM dispatch_record"))
        await db.execute(text("DELETE FROM incident"))
        await db.execute(text("DELETE FROM resource"))
        await db.commit()

