"""
app/db package.

Exports the async session dependency and declarative base
so other modules don't need to know the file structure.
"""

from app.db.engine import AsyncSessionLocal, engine, get_db
from app.db.base import Base

__all__ = ["engine", "AsyncSessionLocal", "get_db", "Base"]
