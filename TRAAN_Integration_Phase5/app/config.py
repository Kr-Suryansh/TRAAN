"""
Application configuration.

All settings are read from environment variables (or the .env file).
Never hardcode secrets here — use .env.example as the reference.
"""

from typing import List
from pydantic_settings import BaseSettings, SettingsConfigDict
from pydantic import model_validator


class Settings(BaseSettings):
    model_config = SettingsConfigDict(
        env_file=".env",
        env_file_encoding="utf-8",
        extra="ignore",
    )

    # ── Database ────────────────────────────────────────────────────────────────
    DATABASE_URL: str = (
        "postgresql+asyncpg://sih:sih_dev_secret@localhost:5432/sih_db"
    )

    # ── Application ─────────────────────────────────────────────────────────────
    APP_NAME: str = "SIH Disaster Response API"
    APP_VERSION: str = "0.1.0"
    DEBUG: bool = False
    CORS_ORIGINS: List[str] = ["http://localhost:3000"]

    # ── JWT ─────────────────────────────────────────────────────────────────────
    # Used in Phase 2 (auth implementation). Stored here so the env var is
    # documented and the setting is available to security.py when it's wired in.
    JWT_SECRET_KEY: str = "dev-only-change-before-production"
    JWT_ALGORITHM: str = "HS256"
    DEVICE_JWT_EXPIRE_MINUTES: int = 43200   # 30 days
    AUTHORITY_JWT_EXPIRE_MINUTES: int = 60   # 1 hour

    # ── External APIs (Component E — AI + Resource Intelligence) ────────────────
    GEMINI_API_KEY: str = ""
    SITUATION_BRIEF_REFRESH_INTERVAL: int = 10800

    # ── Deduplication tuning (Phase 3) ──────────────────────────────────────────
    # Distance in meters for spatial deduplication.
    DEDUPLICATION_DISTANCE_M: float = 50.0
    # Time window (minutes) within which same-location reports are considered dupes.
    DEDUPLICATION_TIME_WINDOW_MINUTES: float = 15.0

    # ── Clustering tuning (Phase 3) ─────────────────────────────────────────────
    # ST_ClusterDBSCAN eps in degrees for EPSG:4326 geometry (~111 meters per 0.001 degree at equator)
    DBSCAN_EPS: float = 0.001
    DBSCAN_MINPOINTS: int = 1

    @model_validator(mode='after')
    def check_jwt_secret(self) -> 'Settings':
        if not self.DEBUG and self.JWT_SECRET_KEY == "dev-only-change-before-production":
            raise ValueError("JWT_SECRET_KEY must be changed from the default in production (DEBUG=False).")
        return self


settings = Settings()
