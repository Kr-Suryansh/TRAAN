package com.sih.relay.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * The core SOS message that originates on a victim's device and travels through the relay mesh.
 *
 * ═══════════════════════════════════════════════════════════════════════════════
 * SCHEMA CONTRACT — day1-contracts-and-repo-setup.md §1.2
 * Field names here MUST match the contract exactly.
 * Do NOT add, remove, or rename fields without updating the contract document
 * first and notifying the team. This schema is shared across Android, backend,
 * and dashboard components.
 * ═══════════════════════════════════════════════════════════════════════════════
 *
 * TEXT-ONLY: No photo or audio fields. This is a non-negotiable design constraint
 * (antigravity-build-prompts.md). Any such addition violates the contract.
 *
 * NOT a Room @Entity — Room persistence belongs to :data.
 * See Agent.md §Architecture Decisions — RelayDataSource boundary.
 */
@Serializable
data class SOSRequest(

    /**
     * UUIDv4, client-generated on the originating device. Primary identifier across
     * the entire system (Android, backend, dashboard). Never changed — even after relay hops.
     */
    val uuid: String,

    /** Installation ID of the phone that originally created this SOS. Not the relaying phone. */
    @SerialName("device_id")
    val deviceId: String,

    /** ISO 8601 timestamp when this SOS was created on the originating device. */
    @SerialName("created_at")
    val createdAt: String,

    /** Where the victim was when they sent the SOS. One-shot location capture — not polled. */
    val location: SosLocation,

    /**
     * true  = sent with a single tap — minimal info, quick to send.
     * false = user had time to fill in additional details.
     * A single tap alone is a complete, valid emergency signal.
     */
    @SerialName("is_quick_sos")
    val isQuickSos: Boolean,

    /** What kind of emergency this is. See [EmergencyType] for all values. */
    @SerialName("emergency_type")
    val emergencyType: EmergencyType,

    /**
     * Self-reported urgency. Null on quick SOS — the backend's AI assigns an
     * authoritative severity to the resulting Incident regardless of this hint.
     */
    @SerialName("severity_hint")
    val severityHint: SeverityHint? = null,

    /** How many people are affected at this location. Null if unknown. */
    @SerialName("people_count")
    val peopleCount: Int? = null,

    /**
     * Snapshot of the victim's medical profile captured at SOS creation time.
     * Auto-filled from the stored profile — never re-typed during an emergency.
     * Null if the user hasn't filled in a profile before the emergency.
     * See [UserMedicalProfile] for fields.
     */
    @SerialName("medical_snapshot")
    val medicalSnapshot: UserMedicalProfile? = null,

    /** Optional free-text note from the victim (e.g. "We are on the third floor"). */
    @SerialName("custom_message")
    val customMessage: String? = null,

    /** Optional phone number rescuers may try to call. */
    @SerialName("contact_number")
    val contactNumber: String? = null,

    /**
     * Number of relay hops this message has made. Starts at 0 on the originating phone.
     * The relay engine increments this by 1 each time it forwards the SOS to a new peer.
     * Helps authorities understand how many hops the victim is from the uploading gateway.
     */
    @SerialName("relay_hop_count")
    val relayHopCount: Int = 0,

    /**
     * ISO 8601 timestamp updated by the relay engine on every hop.
     * Used for TTL-based cleanup: purge SOS records where this timestamp
     * is older than ~48-72 hours (see antigravity-build-prompts.md).
     */
    @SerialName("last_relayed_at")
    val lastRelayedAt: String,

    /**
     * Device-side lifecycle status. Not used for server-side business logic.
     * Transitions: PENDING_LOCAL → IN_RELAY (when first relayed to a peer)
     *              IN_RELAY → UPLOADED (when a gateway confirms backend receipt)
     */
    val status: SOSStatus = SOSStatus.PENDING_LOCAL
)
