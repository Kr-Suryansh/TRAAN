package com.sih.data.model

/**
 * Allowed emergency types — §1.2 emergency_type enum.
 *
 * Exact contract values (lowercase with underscores):
 *   medical | trapped | structural_collapse | flood_rescue |
 *   fire | missing_person | unspecified
 *
 * [apiValue] is what gets serialised into Room and sent over the wire.
 */
enum class EmergencyType(val apiValue: String) {
    MEDICAL("medical"),
    TRAPPED("trapped"),
    STRUCTURAL_COLLAPSE("structural_collapse"),
    FLOOD_RESCUE("flood_rescue"),
    FIRE("fire"),
    MISSING_PERSON("missing_person"),
    UNSPECIFIED("unspecified");

    companion object {
        fun fromApiValue(value: String): EmergencyType =
            entries.find { it.apiValue == value } ?: UNSPECIFIED
    }
}