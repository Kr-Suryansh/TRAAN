import pytest
from datetime import datetime
from app.models.schemas import SOSRequest, EmergencyType, Location
from app.services.ai.summarizer import _get_fallback_summary, generate_incident_summary
from unittest.mock import patch, MagicMock

@pytest.fixture
def sample_flooding_reports():
    loc = Location(lat=30.316, lng=78.032)
    now = datetime.utcnow()
    return [
        SOSRequest(
            device_id="device_1",
            created_at=now,
            location=loc,
            is_quick_sos=False,
            emergency_type=EmergencyType.flood_rescue,
            people_count=4,
            custom_message="Water entered the ground floor, we are on the roof. 4 people.",
            last_relayed_at=now
        ),
        SOSRequest(
            device_id="device_2",
            created_at=now,
            location=loc,
            is_quick_sos=False,
            emergency_type=EmergencyType.flood_rescue,
            people_count=None,
            custom_message="Severe flooding here. Send boats please.",
            last_relayed_at=now
        ),
        SOSRequest(
            device_id="device_3",
            created_at=now,
            location=loc,
            is_quick_sos=False,
            emergency_type=EmergencyType.trapped,
            people_count=4,
            custom_message="Family of 4 trapped by flood waters in Ward 5.",
            last_relayed_at=now
        )
    ]

@patch("app.services.ai.summarizer.get_client")
def test_ai_summarizer_coherent_output(mock_get_client, sample_flooding_reports):
    """
    Acceptance Criteria: Given three sample SOS reports describing the same flooding event 
    in different words, the summarizer produces one coherent ai_summary, not three.
    """
    # Mock the Gemini client response
    mock_client = MagicMock()
    mock_response = MagicMock()
    mock_response.text = '{"ai_summary": "Severe flooding in Ward 5 with 4 people trapped on a roof.", "flags": {"medical_emergency": false, "trapped": true, "elderly_or_children": false, "structural_damage": false}, "estimated_people_affected": 4, "severity": "high"}'
    
    mock_client.models.generate_content.return_value = mock_response
    mock_get_client.return_value = mock_client

    result = generate_incident_summary(sample_flooding_reports)

    assert result["ai_summary"] == "Severe flooding in Ward 5 with 4 people trapped on a roof."
    assert result["flags"]["trapped"] is True
    assert result["estimated_people_affected"] == 4
    assert result["severity"] == "high"

def test_ai_summarizer_fallback(sample_flooding_reports):
    """Test fallback logic when AI fails."""
    result = _get_fallback_summary(sample_flooding_reports)
    assert result["flags"]["trapped"] is True # At least one report has 'trapped'
    assert result["estimated_people_affected"] == 8 # 4 + 4

@patch("app.services.ai.summarizer.get_client")
def test_missing_fields_causes_fallback(mock_get_client, sample_flooding_reports):
    """Test that missing required fields trigger Pydantic validation error and safe fallback."""
    mock_client = MagicMock()
    mock_response = MagicMock()
    # Missing 'severity' and 'flags'
    mock_response.text = '{"ai_summary": "Some summary", "estimated_people_affected": 5}'
    mock_client.models.generate_content.return_value = mock_response
    mock_get_client.return_value = mock_client

    result = generate_incident_summary(sample_flooding_reports)
    
    # Should use fallback safely
    assert result["ai_summary"] == "AI summarization failed. Cluster contains 3 raw reports."
    assert result["flags"]["trapped"] is True

@patch("app.services.ai.summarizer.get_client")
def test_invalid_severity_causes_fallback(mock_get_client, sample_flooding_reports):
    """Test that invalid severity triggers validation error and safe fallback."""
    mock_client = MagicMock()
    mock_response = MagicMock()
    # 'extreme' is not in SeverityEnum
    mock_response.text = '{"ai_summary": "Summary", "flags": {"medical_emergency": false, "trapped": false, "elderly_or_children": false, "structural_damage": false}, "estimated_people_affected": 2, "severity": "extreme"}'
    mock_client.models.generate_content.return_value = mock_response
    mock_get_client.return_value = mock_client

    result = generate_incident_summary(sample_flooding_reports)
    assert "failed" in result["ai_summary"]

@patch("app.services.ai.summarizer.get_client")
def test_negative_affected_population_causes_fallback(mock_get_client, sample_flooding_reports):
    """Test that negative affected population triggers validation error and safe fallback."""
    mock_client = MagicMock()
    mock_response = MagicMock()
    # negative estimated_people_affected
    mock_response.text = '{"ai_summary": "Summary", "flags": {"medical_emergency": false, "trapped": false, "elderly_or_children": false, "structural_damage": false}, "estimated_people_affected": -5, "severity": "medium"}'
    mock_client.models.generate_content.return_value = mock_response
    mock_get_client.return_value = mock_client

    result = generate_incident_summary(sample_flooding_reports)
    assert "failed" in result["ai_summary"]

@patch("app.services.ai.summarizer.get_client")
def test_malformed_json_causes_fallback(mock_get_client, sample_flooding_reports):
    """Test that malformed JSON triggers JSONDecodeError and safe fallback."""
    mock_client = MagicMock()
    mock_response = MagicMock()
    # Malformed JSON
    mock_response.text = '{"ai_summary": "Summary", "flags": {"medical_emergency"'
    mock_client.models.generate_content.return_value = mock_response
    mock_get_client.return_value = mock_client

    result = generate_incident_summary(sample_flooding_reports)
    assert "failed" in result["ai_summary"]

@patch("app.services.ai.summarizer.get_client")
def test_api_exception_causes_fallback(mock_get_client, sample_flooding_reports):
    """Test that an exception from the Gemini client triggers safe fallback."""
    mock_client = MagicMock()
    mock_client.models.generate_content.side_effect = Exception("API Timeout")
    mock_get_client.return_value = mock_client

    result = generate_incident_summary(sample_flooding_reports)
    assert "failed" in result["ai_summary"]

import os
@pytest.mark.skipif(not os.environ.get("GEMINI_API_KEY"), reason="Requires GEMINI_API_KEY")
def test_real_gemini_api(sample_flooding_reports):
    """Manual test to verify real Gemini integration if API key is present."""
    result = generate_incident_summary(sample_flooding_reports)
    assert isinstance(result, dict)
    assert "ai_summary" in result
    assert result["estimated_people_affected"] >= 0
    assert result["severity"] in ["critical", "high", "medium", "low"]
