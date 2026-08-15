package com.sih.relay.service

import com.sih.relay.api.RelayDataSource

/**
 * Simple process-wide holder for the [RelayDataSource] the relay service should use.
 *
 * Day 5 — the service lives in :relay but the temporary [InMemoryRelayStore] test
 * store lives in :app (owned by Component C). :app sets this before starting the
 * service so the service can construct its [RelayManager] with the same store the
 * UI shares.
 *
 * Component C will eventually replace this with real DI (Hilt) in :app; this tiny
 * static seam is Day 5 scaffolding only and is deliberately not a DI framework.
 */
object RelayDataSourceProvider {
    @Volatile
    var dataSource: RelayDataSource? = null
}