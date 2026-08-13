"""
SQLAlchemy declarative base.

Every ORM model must inherit from Base so that:
  - Base.metadata is populated for Alembic autogenerate
  - All tables are created/tracked together

Usage:
    from app.db.base import Base

    class MyModel(Base):
        __tablename__ = "my_table"
        ...
"""

from sqlalchemy.orm import DeclarativeBase


class Base(DeclarativeBase):
    pass
