import json
import logging
from typing import List, Dict, Any
from pydantic import BaseModel
from google.genai import types
from app.models.schemas import SOSRequest, Incident, SeverityEnum, Flags
from app.services.ai.client import get_client

logger = logging.getLogger(__name__)

# Define the expected structured output schema using Pydantic
class IncidentSummaryOutput(BaseModel):
    ai_summary: str
    flags: Flags
    estimated_people_affected: int
    severity: SeverityEnum

def generate_incident_summary(sos_reports: List[SOSRequest]) -> Dict[str, Any]:
    """
    Takes a list of SOS reports belonging to the same cluster and uses Gemini to generate
    a single coherent summary, severity, flags, and estimated people affected.
    
    Returns a dictionary matching the Incident fields:
    {
        "ai_summary": str,
        "flags": dict,
        "estimated_people_affected": int,
        "severity": str
    }
    """
    if not sos_reports:
        return _get_fallback_summary()

    client = get_client()
    if not client:
        logger.error("Gemini client not initialized. Returning fallback summary.")
        return _get_fallback_summary(sos_reports)

    # Prepare the input text by merging relevant fields from the SOS reports
    prompt_lines = [
        "You are an AI assistant for a disaster response coordination system.",
        "You will be given multiple SOS reports that describe the SAME real-world event/incident.",
        "Your task is to synthesize these multiple reports into ONE incident-level understanding.",
        "",
        "Rules:",
        "1. Do not summarize each report separately. Merge them into one coherent 'ai_summary' (one sentence preferred).",
        "2. Do not repeatedly count duplicate information. If two reports mention '4 people trapped', they are likely talking about the same 4 people unless stated otherwise.",
        "3. Contradictory or uncertain information should not be presented as certain fact.",
        "4. Do not invent information not supported by the reports.",
        "5. The 'severity' field MUST be exactly one of: 'critical', 'high', 'medium', 'low'.",
        "6. 'flags' must contain actual booleans for the given keys.",
        "7. 'estimated_people_affected' must be an integer (your best reasoned estimate of total unique people affected).",
        "\n--- SOS Reports ---"
    ]

    for i, report in enumerate(sos_reports):
        prompt_lines.append(f"Report {i+1}:")
        prompt_lines.append(f"  Emergency Type: {report.emergency_type.value if report.emergency_type else 'N/A'}")
        prompt_lines.append(f"  People Count: {report.people_count if report.people_count is not None else 'Unknown'}")
        if report.medical_snapshot:
            med_cond = ", ".join(report.medical_snapshot.medical_conditions) if report.medical_snapshot.medical_conditions else "None"
            prompt_lines.append(f"  Medical Info: Age {report.medical_snapshot.age}, Conditions: {med_cond}")
        if report.custom_message:
            prompt_lines.append(f"  Message: {report.custom_message}")
        prompt_lines.append("")

    prompt_text = "\n".join(prompt_lines)

    try:
        # Use structured output to guarantee the format
        response = client.models.generate_content(
            model='gemini-3.6-flash',
            contents=prompt_text,
            config=types.GenerateContentConfig(
                response_mime_type="application/json",
                response_schema=IncidentSummaryOutput,
            ),
        )
        
        # Parse the JSON string back into a dict
        result = json.loads(response.text)
        
        # Validate through Pydantic to ensure enums and types are perfectly correct
        validated_result = IncidentSummaryOutput(**result)
        
        return {
            "ai_summary": validated_result.ai_summary,
            "flags": validated_result.flags.model_dump(),
            "estimated_people_affected": validated_result.estimated_people_affected,
            "severity": validated_result.severity.value
        }
        
    except Exception as e:
        logger.error(f"Gemini API call failed: {e}")
        return _get_fallback_summary(sos_reports)

def _get_fallback_summary(sos_reports: List[SOSRequest] = None) -> Dict[str, Any]:
    """
    Fallback logic to preserve the incident if the AI processing fails.
    Fulfills the requirement: 'If Gemini fails, preserve the raw SOS reports... Do not fabricate an AI summary.'
    """
    if not sos_reports:
        return {
            "ai_summary": "AI summarization unavailable. No reports provided.",
            "flags": {"medical_emergency": False, "trapped": False, "elderly_or_children": False, "structural_damage": False},
            "estimated_people_affected": 0,
            "severity": SeverityEnum.medium.value
        }
        
    # Basic deterministic fallback
    medical = any(r.emergency_type == 'medical' or r.medical_snapshot for r in sos_reports)
    trapped = any(r.emergency_type == 'trapped' for r in sos_reports)
    structural = any(r.emergency_type == 'structural_collapse' for r in sos_reports)
    
    # Sum people count safely
    people_counts = [r.people_count for r in sos_reports if r.people_count is not None]
    total_people = sum(people_counts) if people_counts else len(sos_reports)
    
    severity = SeverityEnum.medium
    if trapped or structural or (medical and total_people > 5):
        severity = SeverityEnum.high
        
    return {
        "ai_summary": f"AI summarization failed. Cluster contains {len(sos_reports)} raw reports.",
        "flags": {
            "medical_emergency": medical,
            "trapped": trapped,
            "elderly_or_children": False, # Cannot deterministically infer without AI
            "structural_damage": structural
        },
        "estimated_people_affected": total_people,
        "severity": severity.value
    }
