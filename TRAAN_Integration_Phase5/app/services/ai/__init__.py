"""
app/services/ai — Gemini-based summarization service.

COMPONENT BOUNDARY: Implementation is owned by Component E (AI + Resource Intelligence).
"""

from .summarizer import generate_incident_summary
from .situation_brief import (
    get_cached_situation_brief,
    refresh_situation_brief,
    start_periodic_refresh,
    stop_periodic_refresh,
)
