"""
ORM model registry.

All SQLAlchemy models must be imported here so that:
  1. Base.metadata is fully populated when Alembic runs autogenerate.
  2. A single `from app.db.models import *` import in alembic/env.py
     is sufficient to pick up every table.

Phase 1: No models yet — this file exists as a required import target.
         Models are added in Phase 2 onwards.

When adding a model:
  1. Create it in app/db/_models/<name>.py inheriting from Base.
  2. Add an import line below in the "Active models" section.
"""

# ── Active models ──────────────────────────────────────────────────────────────
# Phase 2+: uncomment / add as each model is created
#
from app.db._models.device import Device          # noqa: F401
from app.db._models.authority import Authority    # noqa: F401
from app.db._models.sos import SOSReport   # noqa: F401
from app.db._models.incident import Incident      # noqa: F401
from app.db._models.resource import Resource      # noqa: F401
from app.db._models.dispatch import DispatchRecord # noqa: F401
# from app.db._models.situation_brief import SituationBrief  # noqa: F401
