package com.sih.relay.api

import com.sih.relay.model.SOSRequest
import kotlinx.coroutines.flow.Flow

/**
 * The public API exposed by the :relay module to :app.
 *
 * ═══════════════════════════════════════════════════════════════════════════════
 * THIS IS THE ONLY :relay INTERFACE THAT :app NEEDS TO KNOW ABOUT.
 * No other classes from :relay are part of the public contract.
 * See ApiEndpoints.md for the full contract record.
 * ═══════════════════════════════════════════════════════════════════════════════
 *
 * Contract source: antigravity-build-prompts -.md — Component Prompt 1
 * Implementation:  [com.sih.relay.RelayManager]
 *
 * Do not add methods without a confirmed implementation requirement.
 * If a new method becomes necessary, document why in Agent.md before adding it.
 */
interface RelayApi {

    /**
     * Start the relay engine.
     *
     * Day 1 : stub — no-op.
     * Day 2+: initializes Nearby Connections (advertise + discover, P2P_CLUSTER strategy).
     * Day 5+: also starts the foreground service with duty-cycled scanning
     *         (10s scan window every 45-60s — the primary battery protection mechanism).
     */
    fun startRelay()

    /**
     * Stop the relay engine cleanly.
     *
     * Day 1 : stub — no-op.
     * Day 2+: stops Nearby Connections advertising and discovery, disconnects all peers.
     * Day 5+: also stops and removes the foreground service notification.
     */
    fun stopRelay()

    /**
     * Observe all SOS messages currently held in the relay store.
     *
     * Emits a new [List] whenever the store changes — for example, when a peer sync
     * delivers new SOS records, when a local SOS is created, or when TTL cleanup runs.
     * The list always reflects the complete current store, not just the delta.
     *
     * Day 1 : returns an empty [Flow] (no backing store yet).
     * Day 2+: backed by the [RelayDataSource] implementation provided by :data.
     *
     * :app collects this Flow to drive its "relay status" UI indicator.
     */
    fun getRelayStore(): Flow<List<SOSRequest>>
}
