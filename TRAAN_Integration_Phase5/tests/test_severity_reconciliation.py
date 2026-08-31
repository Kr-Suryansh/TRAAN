# tests/test_severity_reconciliation.py
#
# Focused unit tests for the Option A max-severity preservation rule.
# These tests are PURE UNIT TESTS — they do NOT call the Gemini API,
# do NOT touch the database, and do NOT consume any API quota.
#
# They verify only the _max_severity() helper and the reconciliation logic
# that was approved on 2026-08-21.

import pytest
from app.services.incident_pipeline import _max_severity, _SEVERITY_ORDER


class TestSeverityOrder:
    """Verify the severity ordering constant is correct."""

    def test_critical_is_most_severe(self):
        assert _SEVERITY_ORDER["critical"] < _SEVERITY_ORDER["high"]

    def test_high_more_severe_than_medium(self):
        assert _SEVERITY_ORDER["high"] < _SEVERITY_ORDER["medium"]

    def test_medium_more_severe_than_low(self):
        assert _SEVERITY_ORDER["medium"] < _SEVERITY_ORDER["low"]

    def test_all_four_levels_present(self):
        assert set(_SEVERITY_ORDER.keys()) == {"critical", "high", "medium", "low"}


class TestMaxSeverity:
    """
    Verify Option A — D's severity is the protected floor.
    Gemini may escalate but must NEVER downgrade.

    All cases approved in the user request of 2026-08-21.
    """

    # --- Gemini tries to downgrade → D's value must be preserved ---

    def test_d_critical_gemini_high_returns_critical(self):
        assert _max_severity("critical", "high") == "critical"

    def test_d_critical_gemini_medium_returns_critical(self):
        assert _max_severity("critical", "medium") == "critical"

    def test_d_critical_gemini_low_returns_critical(self):
        assert _max_severity("critical", "low") == "critical"

    def test_d_high_gemini_medium_returns_high(self):
        assert _max_severity("high", "medium") == "high"

    def test_d_high_gemini_low_returns_high(self):
        assert _max_severity("high", "low") == "high"

    def test_d_medium_gemini_low_returns_medium(self):
        assert _max_severity("medium", "low") == "medium"

    # --- Gemini escalates → higher severity must be adopted ---

    def test_d_medium_gemini_high_returns_high(self):
        assert _max_severity("medium", "high") == "high"

    def test_d_medium_gemini_critical_returns_critical(self):
        assert _max_severity("medium", "critical") == "critical"

    def test_d_high_gemini_critical_returns_critical(self):
        assert _max_severity("high", "critical") == "critical"

    def test_d_low_gemini_critical_returns_critical(self):
        assert _max_severity("low", "critical") == "critical"

    # --- Same severity on both sides ---

    def test_d_critical_gemini_critical_returns_critical(self):
        assert _max_severity("critical", "critical") == "critical"

    def test_d_high_gemini_high_returns_high(self):
        assert _max_severity("high", "high") == "high"

    def test_d_medium_gemini_medium_returns_medium(self):
        assert _max_severity("medium", "medium") == "medium"

    def test_d_low_gemini_low_returns_low(self):
        assert _max_severity("low", "low") == "low"

    # --- Gemini failure / AI unavailable → D's original must be preserved ---

    def test_gemini_returns_none_preserves_d_severity(self):
        assert _max_severity("critical", None) == "critical"

    def test_gemini_returns_empty_string_preserves_d_severity(self):
        assert _max_severity("high", "") == "high"

    def test_gemini_returns_unrecognised_value_preserves_d_severity(self):
        # An unexpected/invalid enum value from Gemini should never corrupt D's severity
        assert _max_severity("high", "extreme") == "high"
        assert _max_severity("critical", "unknown_value") == "critical"

    def test_gemini_returns_none_low_d_severity_preserved(self):
        assert _max_severity("low", None) == "low"
