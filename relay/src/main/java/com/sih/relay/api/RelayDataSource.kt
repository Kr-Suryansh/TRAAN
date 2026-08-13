package com.sih.relay.api

import com.sih.relay.model.SOSRequest
import kotlinx.coroutines.flow.Flow

/**
 * Internal module-boundary abstraction between :relay and :data.
 *
 * ╔═══════════════════════════════════════════════════════════════════════════╗
 * ║  INTERNAL BOUNDARY — NOT A PUBLIC PROJECT API CONTRACT                  ║
 * ║                                                                         ║
 * ║  This interface is NOT listed in ApiEndpoints.md.                       ║
 * ║  This interface is NOT a shared contract between all components.        ║
 * ║  It is documented in Agent.md §Architecture Decisions — Decision 1.     ║
 * ║                                                                         ║
 * ║  It is public in Kotlin (default visibility) only because :data must    ║
 * ║  be able to implement it. "Public" here means "accessible to Gradle     ║
 * ║  modules that depend on :relay" — it does NOT mean "part of the         ║
 * ║  publicly agreed team API contract."                                    ║
 * ╚═══════════════════════════════════════════════════════════════════════════╝
 *
 * PURPOSE:
 *   :relay needs to read and write SOS messages for epidemic routing.
 *   Room (the actual database) is owned by :data (Component C).
 *   This interface lets :relay declare what storage operations it needs
 *   without importing Room or creating a circular dependency on :data.
 *
 * WIRING (done by :app at startup — see Agent.md for full instructions):
 *   1. :data implements this interface with a Room-backed class (e.g. RoomRelayDataSource).
 *   2. :app depends on both :relay and :data.
 *   3. :app creates the Room-backed instance and passes it into RelayManager's constructor.
 *   4. RelayManager uses this interface exclusively — never imports Room, never imports :data.
 *
 * Day 1: :app passes a no-op stub implementation. Replace with the real
 *        Room implementation when :data's DAO is ready (Day 2+).
 */
interface RelayDataSource {

    /**
     * Returns UUIDs of all SOS messages currently in the local store.
     * Called when building a [com.sih.relay.model.RelayManifest] before connecting to a peer.
     */
    suspend fun getAllSosUuids(): List<String>

    /**
     * Returns the full [SOSRequest] objects for all UUIDs that are NOT in [knownUuids].
     * Called after receiving a peer's manifest to compute the diff — what the peer is missing.
     *
     * @param knownUuids The list of UUIDs the peer already has (from their [com.sih.relay.model.RelayManifest]).
     * @return SOSRequest objects the peer doesn't have yet — to be sent to the peer.
     */
    suspend fun getMissingSos(knownUuids: List<String>): List<SOSRequest>

    /**
     * Saves a batch of SOS messages received from a peer into the local store.
     *
     * MUST be idempotent: if a UUID already exists in the store, the existing record
     * must not be corrupted. A failed call must not leave the store in a partial state.
     * See antigravity-build-prompts.md §Failure-Handling Requirements — Mesh/Relay.
     *
     * @param messages SOS records received from a peer during a relay sync.
     */
    suspend fun saveSosMessages(messages: List<SOSRequest>)

    /**
     * Returns a live stream of all SOS messages in the local store.
     * Emits a new list whenever the store changes (new records saved, TTL cleanup ran).
     * Backed by a Room Flow in the :data implementation.
     *
     * Used by [com.sih.relay.RelayManager.getRelayStore] to provide the Flow to :app.
     */
    fun observeAllSos(): Flow<List<SOSRequest>>
}
