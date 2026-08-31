package com.sih.relay

import com.sih.network.model.dto.SosRequestDto

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
 * The interface itself must not be changed without coordination across teams.
 */
interface RelayRepository {

    /**
     * Returns all SOS entries currently held by the relay engine that
     * originated from peer devices (relay_hop_count > 0).
     *
     * In practice these records are already stored in Room by [RoomRelayDataSource],
     * so [GatewaySyncWorker.sosRequestDao.getAllSos()] already captures them.
     * This path provides an explicit, deduplicated view of only relay-received entries.
     */
    suspend fun getRelayedSosEntries(): List<SosRequestDto>
}
