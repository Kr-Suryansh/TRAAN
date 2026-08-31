import uuid  # noqa: F401 — kept for any future use; not used for id generation under Model A
from datetime import datetime

from sqlalchemy import String, DateTime
from sqlalchemy.orm import Mapped, mapped_column

from app.db.base import Base


class Device(Base):
    __tablename__ = "device"

    # Under Model A (resolved device_id contract), the Android installation UUID
    # is the canonical primary key. The backend accepts and persists it verbatim.
    # No server-side default — the client MUST supply a valid UUID4.
    id: Mapped[str] = mapped_column(String, primary_key=True)
    device_model: Mapped[str] = mapped_column(String, nullable=False)
    app_version: Mapped[str] = mapped_column(String, nullable=False)
    created_at: Mapped[datetime] = mapped_column(
        DateTime(timezone=True), default=datetime.utcnow
    )
    last_seen_at: Mapped[datetime] = mapped_column(
        DateTime(timezone=True), nullable=True
    )
