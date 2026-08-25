"""
Alembic environment — async SQLAlchemy with asyncpg.

This file runs in two modes:
  offline — generates SQL without a live DB connection (for review/CI)
  online  — connects to Postgres and applies migrations

The DATABASE_URL is read from app.config.settings so it comes from the
environment variable or .env file — never hardcoded here.

All ORM models are imported via `app.db.models` so Base.metadata picks them
up for autogenerate. When adding a new model, add its import to
`app/db/models.py`, not here.
"""

import asyncio
import logging
from logging.config import fileConfig

from alembic import context
from sqlalchemy import pool
from sqlalchemy.ext.asyncio import async_engine_from_config

# Load app settings and models
from app.config import settings
from app.db.base import Base
import app.db.models  # noqa: F401 — registers all models with Base.metadata

# ── Alembic Config object ──────────────────────────────────────────────────────
config = context.config

# Override the sqlalchemy.url from settings (keeps credentials out of alembic.ini)
config.set_main_option("sqlalchemy.url", settings.DATABASE_URL)

# Set up logging from alembic.ini
if config.config_file_name is not None:
    fileConfig(config.config_file_name)

logger = logging.getLogger("alembic.env")

# The metadata object Alembic will diff against
target_metadata = Base.metadata


# ── Offline mode ──────────────────────────────────────────────────────────────

def run_migrations_offline() -> None:
    """
    Run migrations without a live DB connection.
    Outputs SQL statements to stdout for review.
    """
    url = config.get_main_option("sqlalchemy.url")
    context.configure(
        url=url,
        target_metadata=target_metadata,
        literal_binds=True,
        dialect_opts={"paramstyle": "named"},
        compare_type=True,
    )
    with context.begin_transaction():
        context.run_migrations()


def include_object(object, name, type_, reflected, compare_to):
    # Ignore any tables in the DB that aren't defined in our ORM metadata
    if type_ == "table" and reflected and compare_to is None:
        return False
    return True


# ── Online mode ───────────────────────────────────────────────────────────────

def do_run_migrations(connection) -> None:
    context.configure(
        connection=connection,
        target_metadata=target_metadata,
        compare_type=True,
        include_object=include_object,
    )
    with context.begin_transaction():
        context.run_migrations()


async def run_async_migrations() -> None:
    """Connect with asyncpg and apply migrations."""
    connectable = async_engine_from_config(
        config.get_section(config.config_ini_section, {}),
        prefix="sqlalchemy.",
        poolclass=pool.NullPool,
    )
    async with connectable.connect() as connection:
        await connection.run_sync(do_run_migrations)
    await connectable.dispose()


def run_migrations_online() -> None:
    asyncio.run(run_async_migrations())


# ── Dispatch ──────────────────────────────────────────────────────────────────

if context.is_offline_mode():
    run_migrations_offline()
else:
    run_migrations_online()
