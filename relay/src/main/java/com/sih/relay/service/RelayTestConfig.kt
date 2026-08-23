package com.sih.relay.service

/**
 * TEST-ONLY configuration for restricting which Nearby Connections peers a phone
 * may connect to. Used by Day 7 Phase D physical testing to deterministically
 * force an A ↔ B ↔ C topology without relying on radio positioning.
 *
 * ═══════════════════════════════════════════════════════════════════════════════
 * TEST / DEBUG ONLY — NOT PRODUCTION CODE.
 * ═══════════════════════════════════════════════════════════════════════════════
 *
 * When set, [RelayManager] advertises under [localPeerName], uses that name in
 * `requestConnection` and the manifest `deviceId`, and only connects to peers
 * whose advertised name is in [allowedPeerNames]. When unset, behavior is
 * identical to the current production implementation (constant "SIH-Relay-Node"
 * name, connect to every discovered peer).
 *
 * The filter operates purely at the Nearby connection layer: it controls which
 * endpoints enter `connectedEndpoints`. It does NOT touch SOS propagation,
 * payload codec, hop accounting, duplicate handling, manifest diffing, the duty
 * cycler, the foreground service, or the Room schema.
 */
data class RelayTestConfig(
    /** The Nearby endpoint name this phone advertises while test mode is active. */
    val localPeerName: String,
    /** The only endpoint names this phone may connect to while test mode is active. */
    val allowedPeerNames: Set<String>
)

/**
 * Simple process-wide holder for the TEST-ONLY [RelayTestConfig], mirroring the
 * existing [RelayDataSourceProvider] seam pattern. `:app` sets this (from intent
 * extras) before starting the relay service.
 *
 * TEST / DEBUG ONLY — NOT PRODUCTION CODE. Remove or leave null in production.
 */
object RelayTestConfigProvider {
    @Volatile
    var config: RelayTestConfig? = null
}