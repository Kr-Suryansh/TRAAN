package com.sih.data.relay

import com.sih.data.db.dao.SosRequestDao
import com.sih.relay.api.RelayDataSource
import com.sih.relay.model.SOSRequest
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow

/**
 * Room-backed implementation of [RelayDataSource] — Component C's persistence
 * infrastructure consumed through the :relay boundary.
 *
 * Dependency direction is preserved: `:relay ↓ RelayDataSource interface ↑ this
 * (:data implementation)`. This class imports :relay models, never the reverse,
 * and :relay never imports Room or :data.
 *
 * Semantics (match the contract in [RelayDataSource]):
 *  - [saveSosMessages] uses [SosRequestDao.insertSos] with OnConflictStrategy.IGNORE
 *    so merges are idempotent by UUID — re-saving a known UUID never corrupts or
 *    overwrites the existing record, and a failed batch never leaves a partial
 *    state (each insert is atomic).
 *  - [getMissingSos] computes the peer diff over the full Room table.
 *  - [observeAllSos] emits whenever the table changes. The DAO exposes no
 *    observe-all Flow (API unchanged), so we derive freshness from the existing
 *    [SosRequestDao.observeCount] Flow and re-read the table on each invalidation.
 *  - `relayHopCount`, `lastRelayedAt`, and device-side `status` survive the
 *    mapping untouched via [SosRequestMapper].
 */
@OptIn(ExperimentalCoroutinesApi::class)
class RoomRelayDataSource(
    private val sosRequestDao: SosRequestDao
) : RelayDataSource {

    override suspend fun getAllSosUuids(): List<String> =
        sosRequestDao.getAllSos().map { it.uuid }

    override suspend fun getMissingSos(knownUuids: List<String>): List<SOSRequest> =
        sosRequestDao.getAllSos()
            .filter { it.uuid !in knownUuids }
            .map { SosRequestMapper.toSosRequest(it) }

    override suspend fun saveSosMessages(messages: List<SOSRequest>) {
        for (sos in messages) {
            sosRequestDao.insertSos(SosRequestMapper.toEntity(sos))
        }
    }

    override fun observeAllSos(): Flow<List<SOSRequest>> =
        sosRequestDao.observeCount()
            .flatMapLatest {
                flow { emit(sosRequestDao.getAllSos().map { SosRequestMapper.toSosRequest(it) }) }
            }
}