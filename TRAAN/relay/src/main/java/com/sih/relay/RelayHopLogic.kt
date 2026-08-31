package com.sih.relay

import com.sih.relay.model.SOSRequest
import com.sih.relay.model.SOSStatus

/**
 * Pure hop-accounting logic for the Day 4 multi-hop relay protocol.
 *
 * Contract semantics (day1-contracts-and-repo-setup.md §1.2):
 *   relay_hop_count : "integer (incremented at each hop)"
 *   last_relayed_at : "ISO8601 (updated at each hop, used for TTL)"
 *   status          : "pending_local | in_relay | uploaded (device-side only)"
 *
 * Hop counting happens on RECEIVE:
 *   Phone A creates the SOS            → relayHopCount = 0, status = PENDING_LOCAL
 *   Phone B receives it from A          → relayHopCount = 1, status = IN_RELAY
 *   Phone C receives it from B          → relayHopCount = 2, status = IN_RELAY
 *
 * Every hop ALWAYS increments relayHopCount and updates lastRelayedAt —
 * even when the message is already IN_RELAY. The PENDING_LOCAL → IN_RELAY
 * status transition happens only once, on the first relay hop.
 *
 * Extracted as a pure object so it can be unit-tested on the JVM without any
 * Android / Nearby Connections dependencies. Internal to :relay.
 */
internal object RelayHopLogic {

    /**
     * Produces the copy of [sos] that the receiving phone should persist.
     *
     * @param sos        the SOSRequest received over the wire
     * @param relayedAt  ISO 8601 UTC timestamp for this hop (from RelayManager.isoNow())
     */
    fun onRelayReceive(sos: SOSRequest, relayedAt: String): SOSRequest = sos.copy(
        relayHopCount = sos.relayHopCount + 1,
        lastRelayedAt = relayedAt,
        status = if (sos.status == SOSStatus.PENDING_LOCAL) SOSStatus.IN_RELAY else sos.status
    )
}