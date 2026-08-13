package com.sih.data.worker

import android.content.Context
import android.util.Log
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.google.android.gms.location.LocationServices
import com.sih.data.db.dao.SosRequestDao
import com.sih.data.db.entity.SosRequestEntity
import com.sih.data.model.SosStatus
import com.sih.data.prefs.DevicePreferences
import com.sih.network.api.DisasterApi
import com.sih.network.model.dto.LocationDto
import com.sih.network.model.dto.SosRequestDto
import com.sih.network.model.dto.UserMedicalProfileDto
import com.sih.network.model.request.GatewayUploadBatch
import com.sih.relay.RelayRepository
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.time.Instant
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/**
 * WorkManager worker that uploads all locally stored SOS records to the backend.
 *
 * Constraints (set by caller in [com.sih.data.di.DataModule]):
 *   - NetworkType.CONNECTED — only runs when internet is available
 *
 * Gateway mode: uploads EVERY record in the local store, not only the device's
 * own SOS. Any phone with internet is a potential gateway for records it received
 * via the relay mesh.
 *
 * Idempotency: safe to re-run. The backend deduplicates by UUID. Already-uploaded
 * UUIDs returned in [duplicate_uuids] are also marked as uploaded.
 *
 * Post-upload: records are marked [SosStatus.UPLOADED] but NOT deleted.
 * Other phones may still need them from the relay mesh. TTL-based deletion
 * is handled separately in [com.sih.data.repository.SosRepository].
 *
 * On failure: WorkManager retries with exponential backoff (default policy).
 */
@HiltWorker
class GatewaySyncWorker @AssistedInject constructor(
    @Assisted private val context: Context,
    @Assisted workerParams: WorkerParameters,
    private val sosRequestDao: SosRequestDao,
    private val disasterApi: DisasterApi,
    private val devicePreferences: DevicePreferences,
    private val relayRepository: RelayRepository
) : CoroutineWorker(context, workerParams) {

    companion object {
        const val TAG = "GatewaySyncWorker"
        const val WORK_NAME_PERIODIC = "gateway_sync_periodic"
        const val WORK_NAME_IMMEDIATE = "gateway_sync_immediate"
    }

    private val moshi = Moshi.Builder().addLast(KotlinJsonAdapterFactory()).build()
    private val profileAdapter = moshi.adapter(UserMedicalProfileDto::class.java)

    override suspend fun doWork(): Result {
        Log.d(TAG, "Starting gateway sync")

        // 1. Guard: must be registered before uploading
        if (!devicePreferences.isRegistered()) {
            Log.w(TAG, "Device not registered — skipping sync")
            return Result.retry()
        }

        val deviceId  = devicePreferences.getDeviceId()!!
        val deviceJwt = devicePreferences.getDeviceJwt()!!

        // 2. Read all locally stored SOS records (own + relayed from mesh)
        val localSos = sosRequestDao.getAllSos()

        // 3. Merge with any relay-received records the :relay module knows about
        //    (stub returns empty list; real implementation from Agent A+B fills this)
        val relayedSos = relayRepository.getRelayedSosEntries()

        val localSosDtos = localSos.map { it.toDto() }

        val allSos = (localSosDtos + relayedSos)
            .distinctBy { it.uuid } // deduplicate across sources
            .ifEmpty {
                Log.d(TAG, "No SOS records to upload")
                return Result.success()
            }

        Log.d(TAG, "Uploading ${allSos.size} SOS record(s) as gateway $deviceId")

        // 4. Build the batch payload
        val gatewayLocation = getGatewayLocation()
        val batch = GatewayUploadBatch(
            gatewayDeviceId = deviceId,
            gatewayLocation = gatewayLocation,
            uploadedAt      = Instant.now().let { DateTimeFormatter.ISO_INSTANT.format(it) },
            sosBatch        = allSos
        )

        // 5. POST to /api/v1/sos/batch
        return try {
            val response = disasterApi.uploadBatch(batch)

            when {
                response.isSuccessful -> {
                    val body = response.body()
                    if (body != null) {
                        val uploaded = body.acceptedUuids + body.duplicateUuids
                        if (uploaded.isNotEmpty()) {
                            sosRequestDao.markUploaded(uploaded)
                        }
                        Log.i(TAG, "Sync success: ${body.acceptedUuids.size} accepted, " +
                                "${body.duplicateUuids.size} duplicates")
                    }

                    // Enforce the TTL: purge records older than 7 days
                    val cutoff = Instant.now().minusSeconds(7 * 24 * 3600)
                    val cutoffIso = DateTimeFormatter.ISO_INSTANT.format(cutoff)
                    sosRequestDao.deleteExpiredUploaded(cutoffIso)

                    Result.success()
                }

                response.code() == 401 -> {
                    // JWT expired — clear credentials and trigger re-registration
                    Log.w(TAG, "401 on batch upload — credentials expired, clearing")
                    devicePreferences.clearCredentials()
                    Result.retry()
                }

                response.code() in 500..599 -> {
                    Log.w(TAG, "Server error ${response.code()} — will retry")
                    Result.retry()
                }

                else -> {
                    Log.e(TAG, "Unrecoverable error ${response.code()} — giving up")
                    Result.failure()
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Network exception during sync", e)
            Result.retry() // WorkManager will apply exponential backoff
        }
    }

    /**
     * Returns the last known device location for the gateway_location field.
     *
     * Uses [FusedLocationProviderClient.lastLocation] — a cached, non-blocking read.
     * Never delays or blocks the upload. Falls back to 0.0 / 0.0 if:
     *  - No cached location available (GPS off, cold start)
     *  - Location permission not granted
     */
    @android.annotation.SuppressLint("MissingPermission")
    private suspend fun getGatewayLocation(): LocationDto {
        return try {
            val fusedClient = LocationServices.getFusedLocationProviderClient(context)
            suspendCancellableCoroutine { cont ->
                fusedClient.lastLocation
                    .addOnSuccessListener { location ->
                        if (location != null) {
                            cont.resume(LocationDto(lat = location.latitude, lng = location.longitude))
                        } else {
                            Log.w(TAG, "No cached location — using 0.0/0.0 sentinel")
                            cont.resume(LocationDto(lat = 0.0, lng = 0.0))
                        }
                    }
                    .addOnFailureListener { e ->
                        Log.w(TAG, "Location fetch failed — using 0.0/0.0 sentinel", e)
                        cont.resume(LocationDto(lat = 0.0, lng = 0.0))
                    }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Location exception — using 0.0/0.0 sentinel", e)
            LocationDto(lat = 0.0, lng = 0.0)
        }
    }

    /** Map Room entity → network DTO, stripping the device-side [status] field. */
    private fun SosRequestEntity.toDto(): SosRequestDto {
        val profileDto = medicalSnapshot?.let {
            runCatching { profileAdapter.fromJson(it) }.getOrNull()
        }

        return SosRequestDto(
            uuid           = uuid,
            deviceId       = deviceId,
            createdAt      = createdAt,
            location       = LocationDto(
                lat        = locationLat,
                lng        = locationLng,
                accuracyM  = locationAccuracyM
            ),
            isQuickSos     = isQuickSos,
            emergencyType  = emergencyType,
            severityHint   = severityHint,
            peopleCount    = peopleCount,
            medicalSnapshot= profileDto,
            customMessage  = customMessage,
            contactNumber  = contactNumber,
            relayHopCount  = relayHopCount,
            lastRelayedAt  = lastRelayedAt,
            status         = status
        )
    }
}
