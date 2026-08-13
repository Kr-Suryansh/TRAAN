"""
app/services/ai — Gemini-based summarization service.

COMPONENT BOUNDARY: Implementation is owned by Component E (AI + Resource Intelligence).

This package exposes the interface that Backend Core calls.
The stub below returns null values and must be replaced by Component E.

Contract (do not change without team coordination):
  summarize_incident(sos_reports: list[dict]) -> IncidentAISummary
  generate_situation_brief(incidents: list[dict]) -> str

Where IncidentAISummary = {
    "ai_summary": str | None,
    "flags": {
        "medical_emergency": bool,
        "trapped": bool,
        "elderly_or_children": bool,
        "structural_damage": bool,
    },
    "estimated_people_affected": int | None,
    "severity": Literal["critical", "high", "medium", "low"] | None,
}
"""

# TODO (Component E): replace with real Gemini implementation
# See app/services/ai/gemini.py when Component E is ready
