package com.sih.data.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import com.sih.data.model.EmergencyType
import com.sih.data.model.SeverityHint
import com.sih.data.model.SosStatus

/**
 * Persisted SOS request — §1.2 SOSRequest (all fields).
 *
 * This entity stores EVERY SOS this device holds, whether:
 *  - Created by this device's citizen
 *  - Received via the offline mesh relay
 *
 * This phone may act as a gateway and upload ALL stored entries.
 *
 * Design decisions:
 *  - Location stored as flat columns (lat/lng/accuracy) — no nested objects in Room
 *  - [emergencyType] and [severityHint] stored as their apiValue strings
 *  - [medicalSnapshot] stored as a JSON string (full serialised UserMedicalProfileDto)
 *  - [status] is DEVICE-SIDE ONLY; never serialised to the network
 *  - [relayHopCount] incremented by the :relay module at each hop
 */
@Entity(tableName = "sos_request")
data class SosRequestEntity(

    /** UUIDv4, client-generated, permanent primary key */
    @PrimaryKey
    @ColumnInfo(name = "uuid")
    val uuid: String,

    /** Installation ID of the originating phone (not necessarily this phone) */
    @ColumnInfo(name = "device_id")
    val deviceId: String,

    /** ISO8601 timestamp from originating device's clock */
    @ColumnInfo(name = "created_at")
    val createdAt: String,

    // ── Location ─────────────────────────────────────────────────────────
    @ColumnInfo(name = "location_lat")
    val locationLat: Double,

    @ColumnInfo(name = "location_lng")
    val locationLng: Double,

    @ColumnInfo(name = "location_accuracy_m")
    val locationAccuracyM: Double? = null,

    // ── SOS metadata ─────────────────────────────────────────────────────
    /** true = sent via single tap with no typing required */
    @ColumnInfo(name = "is_quick_sos")
    val isQuickSos: Boolean,

    /** Stored as apiValue string, e.g. "flood_rescue" */
    @ColumnInfo(name = "emergency_type")
    val emergencyType: String = EmergencyType.UNSPECIFIED.apiValue,

    /** Null for quick SOS; stored as apiValue string when present */
    @ColumnInfo(name = "severity_hint")
    val severityHint: String? = null,

    @ColumnInfo(name = "people_count")
    val peopleCount: Int? = null,

    /**
     * JSON-serialised UserMedicalProfileDto snapshot at time of SOS creation.
     * Null if the citizen skipped onboarding or had no profile.
     */
    @ColumnInfo(name = "medical_snapshot")
    val medicalSnapshot: String? = null,

    @ColumnInfo(name = "custom_message")
    val customMessage: String? = null,

    @ColumnInfo(name = "contact_number")
    val contactNumber: String? = null,

    // ── Relay metadata ────────────────────────────────────────────────────
    /** Number of peer hops this SOS has traversed; 0 for origin device */
    @ColumnInfo(name = "relay_hop_count")
    val relayHopCount: Int = 0,

    /** ISO8601 — updated at each relay hop; used for TTL-based expiry */
    @ColumnInfo(name = "last_relayed_at")
    val lastRelayedAt: String,

    // ── Device-side status (NEVER sent to backend) ────────────────────────
    @ColumnInfo(name = "status")
    val status: String = SosStatus.PENDING_LOCAL.apiValue
)
