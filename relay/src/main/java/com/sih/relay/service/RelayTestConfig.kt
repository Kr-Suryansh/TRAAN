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
 * existing [RelayDataSourceProvider] seam pattern.
 *
 * ═══════════════════════════════════════════════════════════════════════════════
 * TEST / DEBUG ONLY — NOT PRODUCTION CODE.
 * ═══════════════════════════════════════════════════════════════════════════════
 *
 * ## How it is set
 *
 * `:app`'s `MainActivity.onCreate()` reads two intent extras and assigns this
 * holder before `setContent` (and therefore before the relay foreground service
 * starts its first advertising/discovery cycle):
 *
 *   - `relay_test_peer` — the Nearby endpoint name this device advertises as.
 *   - `relay_test_allowed` — comma-separated list of endpoint names this device
 *     may connect to.
 *
 * Example ADB launch:
 * ```
 * adb shell am start -n com.sih.android/.MainActivity \
 *   --es relay_test_peer Device-A \
 *   --es relay_test_allowed Device-B
 * ```
 *
 * ## Lifecycle
 *
 * This is a **process-local in-memory** `@Volatile var`. It is NOT persisted.
 * - **Force-stop / process death** destroys it. A subsequent normal launch
 *   (without extras) leaves `config` as `null`, restoring unrestricted
 *   production behavior.
 * - **START_STICKY redelivery** after process death does NOT restore the config;
 *   `MainActivity.onCreate()` must be called again with the extras to re-set it.
 *
 * ## Production behavior when null
 *
 * When `config` is `null` (normal launch, no extras):
 * - `RelayManager.effectiveEndpointName()` returns `"SIH-Relay-Node"`.
 * - `RelayManager.isPeerAllowed()` returns `true` for every peer.
 * - Identical to unrestricted production behavior.
 *
 * ## Important: test endpoint names vs. SOSRequest.deviceId
 *
 * The test endpoint names (`Device-A`, `Device-B`, `Device-C`) are used ONLY
 * for Nearby Connections advertising and peer filtering. They are NOT the same
 * as `SOSRequest.deviceId`, which is the device's stable installation UUID
 * (from `DevicePreferences.getOrCreateInstallationId()`). The installation UUID
 * travels unchanged through the relay chain (A → B → C). A valid multi-hop
 * test is confirmed by: same `uuid` + same `deviceId` + `hopCount` 0 → 1 → 2
 * across three devices.
 */
object RelayTestConfigProvider {
    @Volatile
    var config: RelayTestConfig? = null
}