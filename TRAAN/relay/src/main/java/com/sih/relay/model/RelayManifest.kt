package com.sih.relay.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * The "what do you already have?" handshake exchanged between two phones when they connect.
 *
 * ═══════════════════════════════════════════════════════════════════════════════
 * SCHEMA CONTRACT — day1-contracts-and-repo-setup.md §1.3
 * Field names here MUST match the contract exactly.
 * ═══════════════════════════════════════════════════════════════════════════════
 *
 * PROTOCOL (Day 3 implementation):
 *   1. Phone A connects to Phone B.
 *   2. Both phones immediately send their RelayManifest (before any SOS data moves).
 *   3. Each phone computes the diff: which UUIDs does the other phone NOT have?
 *   4. Each phone sends only the missing [SOSRequest] objects to the other.
 *
 * This prevents re-sending SOS messages the peer already holds, which would waste
 * battery and bandwidth — the key constraint for a trapped victim with a nearly-dead phone.
 */
@Serializable
data class RelayManifest(

    /** The device_id of the phone sending this manifest. */
    @SerialName("device_id")
    val deviceId: String,

    /**
     * Complete list of SOS UUIDs currently held in this phone's relay store.
     * Can be empty on a brand-new device that hasn't encountered any SOS messages yet.
     */
    @SerialName("known_uuids")
    val knownUuids: List<String>,

    /** ISO 8601 timestamp when this manifest was generated. */
    val timestamp: String
)
