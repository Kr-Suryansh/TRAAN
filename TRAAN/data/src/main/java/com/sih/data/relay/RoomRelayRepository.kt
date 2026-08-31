package com.sih.data.relay

import android.util.Log
import com.sih.data.db.dao.SosRequestDao
import com.sih.network.model.dto.LocationDto
import com.sih.network.model.dto.SosRequestDto
import com.sih.network.model.dto.UserMedicalProfileDto
import com.sih.relay.RelayRepository
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Room-backed implementation of [RelayRepository].
 *
 * Reads SOS records that arrived via the offline relay mesh (relay_hop_count > 0),
 * meaning they were received from a peer device — NOT created by this device's citizen.
 *
 * WHY this is safe and non-duplicating:
 *   - [GatewaySyncWorker] already calls [SosRequestDao.getAllSos()] which returns ALL
 *     Room records (own + relayed). Those results are deduplicated with the output of
 *     this method via `distinctBy { it.uuid }` before uploading.
 *   - This implementation filters to relay_hop_count > 0 so it correctly identifies
 *     records that truly came from the mesh, rather than locally created ones.
 *   - The [GatewaySyncWorker] distinctBy ensures zero double-counting even if the
 *     same UUID appears in both paths.
 *
 * Stage 6B-3: closes the documented integration gap. Previously [StubRelayRepository]
 * returned an empty list; relay-received SOS were still uploaded because they land in
 * Room via [RoomRelayDataSource]. This implementation makes that path explicit.
 */
@Singleton
class RoomRelayRepository @Inject constructor(
    private val sosRequestDao: SosRequestDao
) : RelayRepository {

    companion object {
        private const val TAG = "RoomRelayRepository"

        // Moshi instance for deserializing the JSON-encoded medical snapshot stored in Room
        private val moshi: Moshi = Moshi.Builder()
            .addLast(KotlinJsonAdapterFactory())
            .build()
        private val profileAdapter = moshi.adapter(UserMedicalProfileDto::class.java)
    }

    /**
     * Returns SOS records with relay_hop_count > 0 — i.e., records that arrived
     * from peer devices via Nearby Connections and were stored by [RoomRelayDataSource].
     *
     * Records with hop_count == 0 are locally created by this device's citizen
     * and are already captured by [GatewaySyncWorker] via [SosRequestDao.getAllSos()].
     */
    override suspend fun getRelayedSosEntries(): List<SosRequestDto> {
        return try {
            val all = sosRequestDao.getAllSos()
            val relayed = all.filter { it.relayHopCount > 0 }
            Log.d(TAG, "getRelayedSosEntries: ${relayed.size} relay-received records (of ${all.size} total)")

            relayed.map { entity ->
                val profileDto = entity.medicalSnapshot?.let { json ->
                    runCatching { profileAdapter.fromJson(json) }.getOrNull()
                }
                SosRequestDto(
                    uuid           = entity.uuid,
                    deviceId       = entity.deviceId,
                    createdAt      = entity.createdAt,
                    location       = LocationDto(
                        lat        = entity.locationLat,
                        lng        = entity.locationLng,
                        accuracyM  = entity.locationAccuracyM
                    ),
                    isQuickSos     = entity.isQuickSos,
                    emergencyType  = entity.emergencyType,
                    peopleCount    = entity.peopleCount,
                    medicalSnapshot = profileDto,
                    customMessage  = entity.customMessage,
                    contactNumber  = entity.contactNumber,
                    relayHopCount  = entity.relayHopCount,
                    lastRelayedAt  = entity.lastRelayedAt
                    // status intentionally omitted — device-side only (contract rule C)
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to read relay-received SOS from Room — returning empty", e)
            emptyList()
        }
    }
}
