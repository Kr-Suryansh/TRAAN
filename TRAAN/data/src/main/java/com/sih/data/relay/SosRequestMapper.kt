package com.sih.data.relay

import com.sih.data.db.entity.SosRequestEntity
import com.sih.data.model.EmergencyType
import com.sih.data.model.SosStatus
import com.sih.relay.model.SOSRequest
import com.sih.relay.model.SOSStatus
import com.sih.relay.model.SosLocation
import com.sih.relay.model.UserMedicalProfile
import kotlinx.serialization.json.Json

/**
 * Maps between the :relay model [SOSRequest] and the :data Room entity
 * [SosRequestEntity]. The two models are deliberately NOT merged — they exist
 * on opposite sides of the [com.sih.relay.api.RelayDataSource] boundary.
 *
 * Mapping rules (must hold on every round-trip):
 *  - `uuid` is the identity — preserved exactly (idempotent relay merges).
 *  - Location is flattened (Float → Double) on the way to Room and restored.
 *  - Enums are bridged by constant name because both sides share the same
 *    contract values (§1.2). Unknown API values degrade to the UNSPECIFIED /
 *    PENDING_LOCAL defaults on the entity side (matching Component C).
 *  - `medicalSnapshot` is serialised to its JSON string for storage. The
 *    encoded field names match the wire contract, so the string is also
 *    parseable by Component C's Moshi UserMedicalProfileDto adapter.
 *  - Relay-side `status` is DEVICE-side only and never serialised to the
 *    network; it is preserved in Room via the entity `status` column.
 *  - `relayHopCount` and `lastRelayedAt` survive the round-trip untouched.
 */
object SosRequestMapper {

    /**
     * JSON instance used for the medical_snapshot column. Same configuration as
     * the :relay payload codec: unknown keys are tolerated on decode and defaults
     * are encoded so an all-null profile still round-trips as a parseable JSON.
     */
    private val relayJson: Json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    fun toEntity(sos: SOSRequest): SosRequestEntity = SosRequestEntity(
        uuid = sos.uuid,
        deviceId = sos.deviceId,
        createdAt = sos.createdAt,
        locationLat = sos.location.lat.toDouble(),
        locationLng = sos.location.lng.toDouble(),
        locationAccuracyM = sos.location.accuracyMeters?.toDouble(),
        isQuickSos = sos.isQuickSos,
        emergencyType = EmergencyType.valueOf(sos.emergencyType.name).apiValue,
        peopleCount = sos.peopleCount,
        medicalSnapshot = sos.medicalSnapshot?.let {
            relayJson.encodeToString(UserMedicalProfile.serializer(), it)
        },
        customMessage = sos.customMessage,
        contactNumber = sos.contactNumber,
        relayHopCount = sos.relayHopCount,
        lastRelayedAt = sos.lastRelayedAt,
        status = sos.status.name.lowercase()
    )

    fun toSosRequest(entity: SosRequestEntity): SOSRequest = SOSRequest(
        uuid = entity.uuid,
        deviceId = entity.deviceId,
        createdAt = entity.createdAt,
        location = SosLocation(
            lat = entity.locationLat.toFloat(),
            lng = entity.locationLng.toFloat(),
            accuracyMeters = entity.locationAccuracyM?.toFloat()
        ),
        isQuickSos = entity.isQuickSos,
        emergencyType = emergencyTypeOf(entity.emergencyType),
        peopleCount = entity.peopleCount,
        medicalSnapshot = entity.medicalSnapshot?.let { medicalSnapshotOf(it) },
        customMessage = entity.customMessage,
        contactNumber = entity.contactNumber,
        relayHopCount = entity.relayHopCount,
        lastRelayedAt = entity.lastRelayedAt,
        status = sosStatusOf(entity.status)
    )

    // ── Enum + snapshot helpers ──────────────────────────────────────────────

    private fun emergencyTypeOf(apiValue: String): com.sih.relay.model.EmergencyType {
        val name = com.sih.data.model.EmergencyType.fromApiValue(apiValue).name
        return runCatching { com.sih.relay.model.EmergencyType.valueOf(name) }
            .getOrDefault(com.sih.relay.model.EmergencyType.UNSPECIFIED)
    }

    private fun sosStatusOf(apiValue: String): SOSStatus {
        val name = com.sih.data.model.SosStatus.fromApiValue(apiValue).name
        return runCatching { SOSStatus.valueOf(name) }
            .getOrDefault(SOSStatus.PENDING_LOCAL)
    }

    private fun medicalSnapshotOf(json: String): UserMedicalProfile? =
        runCatching { relayJson.decodeFromString(UserMedicalProfile.serializer(), json) }.getOrNull()
}