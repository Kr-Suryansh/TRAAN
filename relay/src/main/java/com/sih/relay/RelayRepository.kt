package com.sih.relay

import com.sih.network.model.dto.SosRequestDto
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Interface for the offline relay mesh — owned by Agent A+B.
 *
 * Agent C calls this to retrieve any SOS records that arrived via the
 * Nearby Connections epidemic-routing mesh (i.e., records NOT created by
 * this device's citizen, but carried here by a peer).
 *
 * The GatewaySyncWorker merges these with locally-created SOS records
 * before uploading the full batch when internet is available.
 *
 * Integration: Agent A+B will replace [StubRelayRepository] with a real
 * implementation backed by the foreground service + Nearby Connections state.
 * The interface itself must not be changed without coordination.
 */
interface RelayRepository {

    /**
     * Returns all SOS entries currently held by the relay engine that
     * are NOT already in the local Room database (to avoid double-counting).
     *
     * In the stub, returns an empty list.
     * In the real implementation, returns entries from the relay store.
     */
    suspend fun getRelayedSosEntries(): List<SosRequestDto>
}

// ─────────────────────────────────────────────────────────────────────────────
// STUB — temporary, clearly marked.
// TODO: Agent A+B replaces this with the real Nearby Connections-backed impl.
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Stub implementation of [RelayRepository].
 *
 * Always returns an empty list — no Nearby Connections logic is implemented here.
 * This allows :data and :app to compile and run independently of the relay engine.
 *
 * Replace this class (keep the interface) when Agent A+B's :relay module is ready.
 */
@Singleton
class StubRelayRepository @Inject constructor() : RelayRepository {
    override suspend fun getRelayedSosEntries(): List<SosRequestDto> = emptyList()
}
