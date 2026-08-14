import logging
from typing import List
from app.models.schemas import Incident, Resource
from app.services.ai.client import get_client

logger = logging.getLogger(__name__)

def generate_situation_brief(incidents: List[Incident], resources: List[Resource]) -> str:
    """
    Synthesizes current active incidents by severity, resource availability, 
    and any critical cases into one short skimmable paragraph.
    """
    client = get_client()
    if not client:
        logger.error("Gemini client not initialized. Returning fallback situation brief.")
        return _get_fallback_brief(incidents, resources)

    # Gather data for the prompt
    active_incidents = [i for i in incidents if i.status not in ["resolved"]]
    
    if not active_incidents:
        return "No active incidents at this time. The situation is stable."

    critical_count = sum(1 for i in active_incidents if i.severity == 'critical')
    high_count = sum(1 for i in active_incidents if i.severity == 'high')
    total_affected = sum(i.estimated_people_affected for i in active_incidents)
    
    available_ambulances = sum(r.quantity_available for r in resources if r.sub_type.lower() == 'ambulance')
    available_boats = sum(r.quantity_available for r in resources if 'boat' in r.sub_type.lower())
    
    prompt = (
        "You are the central commander AI for a disaster response dashboard. "
        "Write a single, short, highly readable paragraph summarizing the overall situation. "
        "Include counts of critical/high incidents, total estimated people affected, and key resource availability (e.g., ambulances, boats). "
        "Keep it professional, urgent but calm, and no longer than 4 sentences.\n\n"
        f"Data:\n"
        f"- Active Incidents: {len(active_incidents)} (Critical: {critical_count}, High: {high_count})\n"
        f"- Total Estimated Affected: {total_affected}\n"
        f"- Key Resources Available: Ambulances ({available_ambulances}), Boats ({available_boats})\n"
    )

    try:
        response = client.models.generate_content(
            model='gemini-2.5-flash',
            contents=prompt,
        )
        return response.text.strip()
    except Exception as e:
        logger.error(f"Gemini API call failed for situation brief: {e}")
        return _get_fallback_brief(incidents, resources)

def _get_fallback_brief(incidents: List[Incident], resources: List[Resource]) -> str:
    """Fallback if AI fails"""
    active = len([i for i in incidents if i.status != "resolved"])
    if active == 0:
        return "System running on fallback mode. No active incidents."
    return f"System running on fallback mode. Currently tracking {active} active incidents."
