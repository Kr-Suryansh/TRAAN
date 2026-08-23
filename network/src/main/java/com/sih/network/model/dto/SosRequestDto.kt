package com.sih.network.model.dto

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

/**
 * The SOS record as it travels over the wire — §1.2 shape.
 *
 * IMPORTANT: The [status] field from SOSRequest is device-side ONLY and is
 * intentionally ABSENT from this DTO. It must never be serialised or sent to
 * the backend. Any attempt to include it is a contract violation (§1.2 / rule C).
 *
 * emergency_type enum values (exact strings per §1.2):
 *   medical | trapped | structural_collapse | flood_rescue | fire | missing_person | unspecified
 *
 * severity_hint enum values (exact strings per §1.2):
 *   critical | high | medium | low | null
 */
@JsonClass(generateAdapter = true)
data class SosRequestDto(
    @Json(name = "uuid")             val uuid: String,
    @Json(name = "device_id")        val deviceId: String,
    @Json(name = "created_at")       val createdAt: String,
    @Json(name = "location")         val location: LocationDto,
    @Json(name = "is_quick_sos")     val isQuickSos: Boolean,
    @Json(name = "emergency_type")   val emergencyType: String,
    @Json(name = "severity_hint")    val severityHint: String? = null,
    @Json(name = "people_count")     val peopleCount: Int? = null,
    @Json(name = "medical_snapshot") val medicalSnapshot: UserMedicalProfileDto? = null,
    @Json(name = "custom_message")   val customMessage: String? = null,
    @Json(name = "contact_number")   val contactNumber: String? = null,
    @Json(name = "relay_hop_count")  val relayHopCount: Int = 0,
    @Json(name = "last_relayed_at")  val lastRelayedAt: String
    // NOTE: `status` is device-side only — it lives on SosRequestEntity (Room) and
    // SosStatus enum, NOT here. Rule C of the non-negotiable contract.
)
