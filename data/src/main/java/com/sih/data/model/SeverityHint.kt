package com.sih.data.model

/**
 * Self-reported severity hint — §1.2 severity_hint enum.
 *
 * Contract: "critical | high | medium | low | null (self-reported, omitted on quick SOS)"
 *
 * Nullable — when quick SOS is sent, no severity hint is attached.
 * [apiValue] is what gets serialised.
 */
enum class SeverityHint(val apiValue: String) {
    CRITICAL("critical"),
    HIGH("high"),
    MEDIUM("medium"),
    LOW("low");

    companion object {
        fun fromApiValue(value: String?): SeverityHint? =
            if (value == null) null
            else entries.find { it.apiValue == value }
    }
}