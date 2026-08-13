"""
app/services/ai/stub.py — TEMPORARY STUB for Gemini summarization.

REPLACE WITH REAL IMPLEMENTATION from Component E.

This stub allows Backend Core to wire up the full request/response flow
without a live Gemini key. It returns clearly-null/placeholder values so
authorities can see that AI processing has not run yet, rather than seeing
fabricated data.

Replace by:
1. Component E implementing app/services/ai/gemini.py
2. Importing from gemini.py instead of stub.py in any caller
3. Deleting this file once real implementation is verified
"""

import logging
from typing import Any

logger = logging.getLogger(__name__)


async def summarize_incident(sos_reports: list[dict[str, Any]]) -> dict[str, Any]:
    """
    STUB — returns null AI fields.
    Real implementation calls Gemini with merged SOS text fields.
    """
    logger.warning(
        "AI stub: summarize_incident called with %d reports — "
        "returning null summary. Replace with Component E implementation.",
        len(sos_reports),
    )
    return {
        "ai_summary": None,
        "flags": {
            "medical_emergency": False,
            "trapped": False,
            "elderly_or_children": False,
            "structural_damage": False,
        },
        "estimated_people_affected": None,
        "severity": None,
    }


async def generate_situation_brief(incidents: list[dict[str, Any]]) -> str | None:
    """
    STUB — returns None.
    Real implementation calls Gemini and returns a one-paragraph brief.
    """
    logger.warning(
        "AI stub: generate_situation_brief called — "
        "returning None. Replace with Component E implementation."
    )
    return None
