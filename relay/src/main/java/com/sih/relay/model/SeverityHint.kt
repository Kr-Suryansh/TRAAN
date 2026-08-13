package com.sih.relay.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Self-reported urgency level from the victim.
 *
 * Schema source: day1-contracts-and-repo-setup.md §1.2
 * Contract values: critical | high | medium | low
 * Nullable in SOSRequest — omitted on quick SOS (is_quick_sos = true).
 *
 * @SerialName values match the contract exactly (lowercase).
 * Do NOT rename or remove values without updating the contract document first.
 */
@Serializable
enum class SeverityHint {
    @SerialName("critical") CRITICAL,
    @SerialName("high")     HIGH,
    @SerialName("medium")   MEDIUM,
    @SerialName("low")      LOW
}
