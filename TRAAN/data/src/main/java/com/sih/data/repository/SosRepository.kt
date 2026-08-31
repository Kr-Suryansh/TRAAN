package com.sih.data.repository

import androidx.work.WorkManager
import com.sih.data.SosConstants
import com.sih.data.db.dao.SosRequestDao
import com.sih.data.db.entity.SosRequestEntity
import com.sih.data.db.entity.UserMedicalProfileEntity
import com.sih.data.di.enqueueImmediateGatewaySync
import com.sih.data.model.EmergencyType
import com.sih.data.model.SosStatus
import com.sih.data.prefs.DevicePreferences
import com.sih.network.model.dto.UserMedicalProfileDto
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.flow.Flow
import java.time.Instant
import java.time.format.DateTimeFormatter
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Repository for SOS lifecycle — creation, status tracking, TTL expiry.
 *
 * Key invariant: the SOS button must work with the phone in airplane mode.
 * [createSos] never makes a network call — it only writes to Room.
 * WorkManager handles the upload asynchronously once connectivity is available.
 *
 * ## Device identity — Model A (resolved contract, P0.4.6)
 * The device_id in each SOS record is resolved as:
 *   1. Backend-confirmed device_id (pref_device_id) — preferred when available.
 *   2. Stable local installation UUID (pref_installation_id) — fallback during the
 *      registration window (no internet yet on first launch).
 * Under Model A: pref_device_id == pref_installation_id always after registration.
 * All offline SOS carry the same installationId regardless of network state.
 *
 * ## TTL (P0.4.4)
 * Expiry uses [SosConstants.TTL_HOURS] (72 h). Never 7 days, never magic numbers.
 */
@Singleton
class SosRepository @Inject constructor(
    private val sosRequestDao: SosRequestDao,
    private val devicePreferences: DevicePreferences,
    private val workManager: WorkManager
) {
    private val moshi = Moshi.Builder().addLast(KotlinJsonAdapterFactory()).build()
    private val profileAdapter = moshi.adapter(UserMedicalProfileDto::class.java)

    /**
     * Create and persist a new SOS record from this device.
     *
     * Called by the SOS button — MUST work offline (airplane mode).
     * No network call is made here.
     *
     * After the Room write succeeds:
     *  - UUID is stored in DevicePreferences (survives process kill → Status screen resumes)
     *  - An immediate GatewaySyncWorker is enqueued (CONNECTED constraint, KEEP policy)
     *    so that if internet is already available, the upload fires within seconds.
     *
     * [profile] is a snapshot of the current UserMedicalProfile.
     * If null (user skipped onboarding), medical_snapshot is null.
     *
     * Returns the UUID of the created SOS (used to navigate to StatusScreen).
     */
    suspend fun createSos(
        lat: Double,
        lng: Double,
        accuracyM: Double? = null,
        isQuickSos: Boolean = true,
        emergencyType: EmergencyType = EmergencyType.UNSPECIFIED,
        peopleCount: Int? = null,
        customMessage: String? = null,
        contactNumber: String? = null,
        profile: UserMedicalProfileEntity? = null
    ): String {
        val uuid     = UUID.randomUUID().toString()
        val now      = DateTimeFormatter.ISO_INSTANT.format(Instant.now())

        // P0.4.6 — stable device identity: prefer backend-registered id, fall back
        // to the stable installation UUID (never a new random UUID per SOS).
        val deviceId = devicePreferences.getDeviceId()
            ?: devicePreferences.getOrCreateInstallationId()

        val medicalSnapshotJson = profile?.let { p ->
            val dto = UserMedicalProfileDto(
                name                   = p.name,
                age                    = p.age,
                bloodType              = p.bloodType,
                medicalConditions      = parseJsonArray(p.medicalConditions),
                medications            = parseJsonArray(p.medications),
                allergies              = parseJsonArray(p.allergies),
                emergencyContactName   = p.emergencyContactName,
                emergencyContactNumber = p.emergencyContactNumber
            )
            profileAdapter.toJson(dto)
        }

        val entity = SosRequestEntity(
            uuid               = uuid,
            deviceId           = deviceId,
            createdAt          = now,
            locationLat        = lat,
            locationLng        = lng,
            locationAccuracyM  = accuracyM,
            isQuickSos         = isQuickSos,
            emergencyType      = emergencyType.apiValue,
            peopleCount        = peopleCount,
            medicalSnapshot    = medicalSnapshotJson,
            customMessage      = customMessage,
            contactNumber      = contactNumber,
            relayHopCount      = 0,
            lastRelayedAt      = now,
            status             = SosStatus.PENDING_LOCAL.apiValue
        )

        sosRequestDao.insertSos(entity)

        // Persist UUID → Status screen survives process kill + cold restart
        devicePreferences.saveLastSosUuid(uuid)

        // Fire an immediate upload attempt if connectivity is already available.
        // KEEP policy: no-op if a sync is already in flight.
        enqueueImmediateGatewaySync(workManager)

        return uuid
    }

    /** Observe a single SOS by UUID (for the Status screen). */
    fun observeSos(uuid: String): Flow<SosRequestEntity?> =
        sosRequestDao.observeSosByUuid(uuid)

    /** Get a single SOS by UUID (suspend, for one-shot reads). */
    suspend fun getSos(uuid: String): SosRequestEntity? =
        sosRequestDao.getSosByUuid(uuid)

    /** All SOS records created by this device. */
    fun observeOwnSos(): Flow<List<SosRequestEntity>> {
        val deviceId = devicePreferences.getDeviceId()
            ?: devicePreferences.getOrCreateInstallationId()
        return sosRequestDao.observeOwnSos(deviceId)
    }

    /** Count of all records (for debug / battery-mode display). */
    fun observeCount(): Flow<Int> = sosRequestDao.observeCount()

    /**
     * Delete uploaded records older than [SosConstants.TTL_HOURS].
     * Call periodically (e.g. daily) — never immediately after upload.
     *
     * P0.4.4: uses the centralized TTL constant — no magic numbers here.
     */
    suspend fun purgeExpiredRecords() {
        val cutoff = Instant.now().minusSeconds(SosConstants.TTL_SECONDS)
        val cutoffIso = DateTimeFormatter.ISO_INSTANT.format(cutoff)
        sosRequestDao.deleteExpiredUploaded(cutoffIso)
    }

    private fun parseJsonArray(json: String): List<String> =
        runCatching {
            moshi.adapter<List<String>>(
                com.squareup.moshi.Types.newParameterizedType(List::class.java, String::class.java)
            ).fromJson(json) ?: emptyList()
        }.getOrDefault(emptyList())
}
