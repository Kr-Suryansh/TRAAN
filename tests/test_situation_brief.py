import os
import pytest
from datetime import datetime
from unittest.mock import patch, MagicMock

from app.models.schemas import Incident, Resource, SeverityEnum, ResourceCategory, ResourceStatus, Location, Flags, RecommendedResource
from app.services.ai.situation_brief import (
    generate_situation_brief,
    refresh_situation_brief,
    get_cached_situation_brief,
    clear_situation_brief_cache,
    start_periodic_refresh,
    stop_periodic_refresh,
    SituationBriefCache,
    _build_situation_prompt
)
from app.services.optimizer.incident_service import clear_incident_store, INCIDENT_STORE

@pytest.fixture(autouse=True)
def reset_state():
    clear_situation_brief_cache()
    clear_incident_store()
    yield
    clear_situation_brief_cache()
    clear_incident_store()
    stop_periodic_refresh()

@pytest.fixture
def base_location():
    return Location(lat=30.0, lng=78.0)

@pytest.fixture
def now():
    return datetime.utcnow()

@pytest.fixture
def sample_incidents(base_location, now):
    inc1 = Incident(
        incident_id="inc_critical_01",
        cluster_id="cluster_1",
        location=base_location,
        area_name="Ward 5",
        severity=SeverityEnum.critical,
        estimated_people_affected=12,
        ai_summary="Severe flooding with 12 people trapped on roof.",
        flags=Flags(medical_emergency=True, trapped=True),
        recommended_resources=[
            RecommendedResource(resource_id="res_amb_01", resource_type="ambulance", quantity=2, reasoning="Urgent medical transport")
        ],
        first_reported_at=now,
        last_updated_at=now
    )
    inc2 = Incident(
        incident_id="inc_high_02",
        cluster_id="cluster_2",
        location=base_location,
        area_name="Ward 7",
        severity=SeverityEnum.high,
        estimated_people_affected=8,
        ai_summary="Landslide damaging structural wall.",
        flags=Flags(structural_damage=True, elderly_or_children=True),
        recommended_resources=[
            RecommendedResource(resource_id="res_boat_01", resource_type="motorboat", quantity=1, reasoning="Rescue watercraft")
        ],
        first_reported_at=now,
        last_updated_at=now
    )
    return [inc1, inc2]

@pytest.fixture
def sample_resources(base_location, now):
    return [
        Resource(
            resource_id="res_amb_01",
            category=ResourceCategory.medical,
            sub_type="ambulance",
            custodian_agency="District Hospital",
            quantity_total=5,
            quantity_available=2,
            status=ResourceStatus.available,
            location=base_location,
            contact="108",
            last_updated_at=now
        ),
        Resource(
            resource_id="res_boat_01",
            category=ResourceCategory.rescue,
            sub_type="motorboat",
            custodian_agency="SDRF",
            quantity_total=4,
            quantity_available=3,
            status=ResourceStatus.available,
            location=base_location,
            contact="112",
            last_updated_at=now
        )
    ]

# --- Test 1: Generates brief from active incidents ---
@patch("app.services.ai.situation_brief.get_client")
def test_situation_brief_aggregated_incidents(mock_get_client, sample_incidents, sample_resources):
    mock_client = MagicMock()
    mock_response = MagicMock()
    mock_response.text = (
        "Current response operations cover 2 active incidents in Ward 5 and Ward 7, affecting an estimated 20 people. "
        "Critical situations include 12 trapped residents and severe structural damage. "
        "Two ambulances and three motorboats remain available, with OR-Tools recommending immediate deployment to Ward 5."
    )
    mock_client.models.generate_content.return_value = mock_response
    mock_get_client.return_value = mock_client

    cache = refresh_situation_brief(incidents=sample_incidents, resources=sample_resources)
    
    assert cache.is_fallback is False
    assert cache.active_incidents_count == 2
    assert cache.total_people_affected == 20
    assert "Ward 5" in cache.brief_text
    assert mock_client.models.generate_content.called

# --- Test 2: Prompt includes critical cases and flags ---
def test_situation_brief_includes_critical_cases_and_flags(sample_incidents, sample_resources):
    prompt = _build_situation_prompt(sample_incidents, sample_resources)
    
    assert "Critical: 1" in prompt
    assert "High: 1" in prompt
    assert "Medical Emergencies: 1" in prompt
    assert "Trapped Persons: 1" in prompt
    assert "Vulnerable (Elderly/Children): 1" in prompt
    assert "Structural Damage: 1" in prompt

# --- Test 3: Prompt includes resource availability ---
def test_situation_brief_includes_resource_availability(sample_incidents, sample_resources):
    prompt = _build_situation_prompt(sample_incidents, sample_resources)
    
    assert "medical" in prompt
    assert "rescue" in prompt
    assert "Resource Inventory" in prompt

# --- Test 4: Prompt includes OR-Tools recommendations ---
def test_situation_brief_includes_ortools_recommendations(sample_incidents, sample_resources):
    prompt = _build_situation_prompt(sample_incidents, sample_resources)
    
    assert "Recommend 2x ambulance" in prompt
    assert "Recommend 1x motorboat" in prompt

# --- Test 5: Empty active incidents state ---
def test_situation_brief_zero_active_incidents(sample_resources):
    cache = refresh_situation_brief(incidents=[], resources=sample_resources)
    
    assert cache.active_incidents_count == 0
    assert "No active disaster incidents recorded" in cache.brief_text

# --- Test 6: Gemini failure preserves previous valid cache ---
@patch("app.services.ai.situation_brief.get_client")
def test_situation_brief_gemini_failure_preserves_previous_cache(mock_get_client, sample_incidents, sample_resources):
    # Step 1: Successful initial generation
    mock_client = MagicMock()
    mock_response = MagicMock()
    mock_response.text = "Initial valid cached situation brief for active emergency response."
    mock_client.models.generate_content.return_value = mock_response
    mock_get_client.return_value = mock_client

    cache1 = refresh_situation_brief(incidents=sample_incidents, resources=sample_resources)
    assert cache1.brief_text == "Initial valid cached situation brief for active emergency response."
    assert cache1.is_fallback is False

    # Step 2: Gemini failure on subsequent refresh
    mock_client.models.generate_content.side_effect = Exception("API Timeout")
    
    cache2 = refresh_situation_brief(incidents=sample_incidents, resources=sample_resources)
    
    # Previous valid cache is preserved
    assert cache2.brief_text == "Initial valid cached situation brief for active emergency response."
    assert cache2.is_fallback is False

# --- Test 7: Gemini failure with no cache uses deterministic fallback ---
@patch("app.services.ai.situation_brief.get_client")
def test_situation_brief_gemini_failure_no_cache_uses_fallback(mock_get_client, sample_incidents, sample_resources):
    mock_client = MagicMock()
    mock_client.models.generate_content.side_effect = Exception("API Connection Failed")
    mock_get_client.return_value = mock_client

    cache = refresh_situation_brief(incidents=sample_incidents, resources=sample_resources)
    
    assert cache.is_fallback is True
    assert "AI situation synthesis currently unavailable" in cache.brief_text
    assert cache.active_incidents_count == 2

# --- Test 8: Invalid Gemini output not cached ---
@patch("app.services.ai.situation_brief.get_client")
def test_situation_brief_invalid_gemini_output_not_cached(mock_get_client, sample_incidents, sample_resources):
    mock_client = MagicMock()
    mock_response = MagicMock()
    # Invalid output: Raw JSON instead of text paragraph
    mock_response.text = '{"brief": "This is raw JSON"}'
    mock_client.models.generate_content.return_value = mock_response
    mock_get_client.return_value = mock_client

    cache = refresh_situation_brief(incidents=sample_incidents, resources=sample_resources)
    
    # Should reject invalid output and use fallback
    assert cache.is_fallback is True
    assert "AI situation synthesis currently unavailable" in cache.brief_text

# --- Test 9: Untrusted input protection in prompt ---
def test_situation_brief_untrusted_input_sanitization(sample_incidents, sample_resources):
    prompt = _build_situation_prompt(sample_incidents, sample_resources)
    
    assert "UNTRUSTED INPUT PROTECTION" in prompt
    assert "HUMAN-IN-THE-LOOP RULE" in prompt
    assert "Do NOT claim or imply that recommended resources have been 'dispatched'" in prompt

# --- Test 10: No dispatch side effects ---
@patch("app.services.ai.situation_brief.get_client")
def test_situation_brief_no_dispatch_side_effects(mock_get_client, sample_incidents, sample_resources):
    initial_avail = sample_resources[0].quantity_available
    initial_status = sample_resources[0].status
    
    mock_client = MagicMock()
    mock_response = MagicMock()
    mock_response.text = "Operational brief text."
    mock_client.models.generate_content.return_value = mock_response
    mock_get_client.return_value = mock_client

    refresh_situation_brief(incidents=sample_incidents, resources=sample_resources)
    
    # Resources must remain unmutated
    assert sample_resources[0].quantity_available == initial_avail
    assert sample_resources[0].status == initial_status
    assert sample_incidents[0].assigned_resources == []

# --- Test 11: Periodic background refresh ---
def test_situation_brief_background_refresh():
    start_periodic_refresh(interval_seconds=1)
    
    cached = get_cached_situation_brief()
    assert isinstance(cached, SituationBriefCache)
    
    stop_periodic_refresh()

# --- Test 12: Real Gemini API integration test ---
@pytest.mark.skipif(not os.environ.get("GEMINI_API_KEY"), reason="Requires GEMINI_API_KEY")
def test_real_gemini_situation_brief_api(sample_incidents, sample_resources):
    cache = refresh_situation_brief(incidents=sample_incidents, resources=sample_resources)
    
    assert isinstance(cache, SituationBriefCache)
    assert len(cache.brief_text) > 20
    assert len(cache.brief_text) < 2000
    assert cache.is_fallback is False
