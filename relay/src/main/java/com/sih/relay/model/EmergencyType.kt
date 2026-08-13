package com.sih.relay.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Category of emergency declared in an SOS.
 *
 * Schema source: day1-contracts-and-repo-setup.md §1.2
 * Contract values: medical | trapped | structural_collapse | flood_rescue | fire | missing_person | unspecified
 *
 * @SerialName values match the contract exactly (lowercase snake_case).
 * Do NOT rename or remove values without updating the contract document first.
 */
@Serializable
enum class EmergencyType {
    @SerialName("medical")             MEDICAL,
    @SerialName("trapped")             TRAPPED,
    @SerialName("structural_collapse") STRUCTURAL_COLLAPSE,
    @SerialName("flood_rescue")        FLOOD_RESCUE,
    @SerialName("fire")                FIRE,
    @SerialName("missing_person")      MISSING_PERSON,
    @SerialName("unspecified")         UNSPECIFIED
}
