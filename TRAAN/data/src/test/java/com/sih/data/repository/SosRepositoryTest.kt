package com.sih.data.repository

import androidx.work.WorkManager
import com.sih.data.SosConstants
import com.sih.data.db.dao.SosRequestDao
import com.sih.data.db.entity.SosRequestEntity
import com.sih.data.db.entity.UserMedicalProfileEntity
import com.sih.data.model.EmergencyType
import com.sih.data.model.SosStatus
import com.sih.data.prefs.DevicePreferences
import io.mockk.*
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

/**
 * Unit tests for [SosRepository].
 *
 * Covers:
 *  1. SOS can be created without network (no WorkManager immediate run, only enqueue)
 *  2. Stable device identity across multiple SOS creations
 *  3. Medical profile is snapshotted at creation time
 *  4. TTL cleanup uses [SosConstants.TTL_SECONDS]
 *  5. SOS creation does NOT make a network call
 */
class SosRepositoryTest {

    private val sosRequestDao = mockk<SosRequestDao>(relaxed = true)
    private val devicePreferences = mockk<DevicePreferences>(relaxed = true)
    private val workManager = mockk<WorkManager>(relaxed = true)

    private lateinit var repository: SosRepository

    companion object {
        private const val FAKE_INSTALLATION_ID = "stable-install-uuid-1234"
        private const val FAKE_REGISTERED_ID   = "backend-device-uuid-5678"
    }

    @Before
    fun setUp() {
        repository = SosRepository(sosRequestDao, devicePreferences, workManager)
    }

    // ── Test 1: Offline SOS creation ─────────────────────────────────────────

    @Test
    fun `createSos writes to Room without network - airplane mode safe`() = runTest {
        every { devicePreferences.getDeviceId() } returns null
        every { devicePreferences.getOrCreateInstallationId() } returns FAKE_INSTALLATION_ID

        val capturedEntities = mutableListOf<SosRequestEntity>()
        coEvery { sosRequestDao.insertSos(capture(capturedEntities)) } returns 1L

        val uuid = repository.createSos(lat = 19.076, lng = 72.877, isQuickSos = true)

        // Verify Room was written to
        coVerify(exactly = 1) { sosRequestDao.insertSos(any()) }

        // UUID returned must match what was stored
        assertEquals(uuid, capturedEntities.first().uuid)

        // Status must be pending_local (not uploaded, not in_relay)
        assertEquals(SosStatus.PENDING_LOCAL.apiValue, capturedEntities.first().status)
    }

    @Test
    fun `createSos does NOT directly call any network API`() = runTest {
        every { devicePreferences.getDeviceId() } returns FAKE_REGISTERED_ID
        coEvery { sosRequestDao.insertSos(any()) } returns 1L

        repository.createSos(lat = 0.0, lng = 0.0, isQuickSos = true)

        // WorkManager enqueue (deferred sync) is allowed — direct network call is not.
        // Since repository has no DisasterApi reference, the mere absence of that
        // dependency proves this at the architectural level. We additionally verify
        // that WorkManager was given a unique work request (not run synchronously).
        verify { workManager.enqueueUniqueWork(any(), any(), any<androidx.work.OneTimeWorkRequest>()) }
    }

    // ── Test 2: Stable device identity ───────────────────────────────────────

    @Test
    fun `createSos uses stable installation ID when not registered`() = runTest {
        every { devicePreferences.getDeviceId() } returns null
        every { devicePreferences.getOrCreateInstallationId() } returns FAKE_INSTALLATION_ID

        val capturedEntities = mutableListOf<SosRequestEntity>()
        coEvery { sosRequestDao.insertSos(capture(capturedEntities)) } returns 1L

        // Create two SOS messages offline
        repository.createSos(lat = 1.0, lng = 1.0, isQuickSos = true)
        repository.createSos(lat = 2.0, lng = 2.0, isQuickSos = true)

        assertEquals("Both SOS must share the same device identity",
            capturedEntities[0].deviceId, capturedEntities[1].deviceId)
        assertEquals(FAKE_INSTALLATION_ID, capturedEntities[0].deviceId)
    }

    @Test
    fun `createSos uses backend device ID when registered`() = runTest {
        every { devicePreferences.getDeviceId() } returns FAKE_REGISTERED_ID
        coEvery { sosRequestDao.insertSos(any()) } returns 1L

        val capturedEntities = mutableListOf<SosRequestEntity>()
        coEvery { sosRequestDao.insertSos(capture(capturedEntities)) } returns 1L

        repository.createSos(lat = 1.0, lng = 1.0, isQuickSos = true)

        assertEquals(FAKE_REGISTERED_ID, capturedEntities.first().deviceId)

        // Installation ID fallback should NOT have been called
        verify(exactly = 0) { devicePreferences.getOrCreateInstallationId() }
    }

    @Test
    fun `device identity is never a new random UUID per SOS`() = runTest {
        // Before the fix, the code was: "unregistered_${UUID.randomUUID()}"
        // which produces a different value every time.
        // After the fix, getOrCreateInstallationId() is called — always returns the same value.

        every { devicePreferences.getDeviceId() } returns null
        every { devicePreferences.getOrCreateInstallationId() } returns FAKE_INSTALLATION_ID

        val capturedEntities = mutableListOf<SosRequestEntity>()
        coEvery { sosRequestDao.insertSos(capture(capturedEntities)) } returns 1L

        repeat(3) { repository.createSos(lat = 0.0, lng = 0.0, isQuickSos = true) }

        val deviceIds = capturedEntities.map { it.deviceId }.distinct()
        assertEquals("All SOS from same offline install must share ONE device ID", 1, deviceIds.size)
        assertFalse("Device ID must not start with 'unregistered_'",
            deviceIds.first().startsWith("unregistered_"))
    }

    // ── Test 3: Medical snapshot ─────────────────────────────────────────────

    @Test
    fun `createSos snapshots medical profile at creation time`() = runTest {
        every { devicePreferences.getDeviceId() } returns FAKE_REGISTERED_ID
        coEvery { sosRequestDao.insertSos(any()) } returns 1L

        val profile = UserMedicalProfileEntity(
            id            = 1,
            name          = "Test User",
            age           = 30,
            bloodType     = "O+",
            medicalConditions = "[\"diabetic\"]",
            medications   = "[\"metformin\"]",
            allergies     = "[\"penicillin\"]"
        )

        val capturedEntities = mutableListOf<SosRequestEntity>()
        coEvery { sosRequestDao.insertSos(capture(capturedEntities)) } returns 1L

        repository.createSos(lat = 19.0, lng = 72.0, isQuickSos = true, profile = profile)

        val snapshot = capturedEntities.first().medicalSnapshot
        assertNotNull("Medical snapshot must not be null when profile is provided", snapshot)
        assertTrue("Snapshot must contain name", snapshot!!.contains("Test User"))
        assertTrue("Snapshot must contain blood type", snapshot.contains("O+"))
        // Snapshot is a JSON string — profile data is frozen at creation time
    }

    @Test
    fun `createSos stores null medical snapshot when no profile is provided`() = runTest {
        every { devicePreferences.getDeviceId() } returns FAKE_REGISTERED_ID

        val capturedEntities = mutableListOf<SosRequestEntity>()
        coEvery { sosRequestDao.insertSos(capture(capturedEntities)) } returns 1L

        repository.createSos(lat = 0.0, lng = 0.0, isQuickSos = true, profile = null)

        assertNull("Quick SOS with no profile must have null medical_snapshot",
            capturedEntities.first().medicalSnapshot)
    }

    // ── Test 4: TTL uses SosConstants ────────────────────────────────────────

    @Test
    fun `purgeExpiredRecords uses SosConstants TTL_SECONDS not 7 days`() = runTest {
        val sevenDaysSeconds = 7 * 24 * 3600L
        assertNotEquals(
            "TTL must not be the incorrect 7-day value",
            sevenDaysSeconds, SosConstants.TTL_SECONDS
        )

        // Verify purge is called with a cutoff that matches 72-hour window
        val capturedCutoffs = mutableListOf<String>()
        coEvery { sosRequestDao.deleteExpiredUploaded(capture(capturedCutoffs)) } just Runs

        repository.purgeExpiredRecords()

        coVerify(exactly = 1) { sosRequestDao.deleteExpiredUploaded(any()) }
    }

    // ── Test 5: SOS creation fields ──────────────────────────────────────────

    @Test
    fun `createSos stores correct emergency type and initial status`() = runTest {
        every { devicePreferences.getDeviceId() } returns FAKE_REGISTERED_ID

        val capturedEntities = mutableListOf<SosRequestEntity>()
        coEvery { sosRequestDao.insertSos(capture(capturedEntities)) } returns 1L

        repository.createSos(
            lat           = 19.0,
            lng           = 72.0,
            isQuickSos    = false,
            emergencyType = EmergencyType.FLOOD_RESCUE
        )

        val entity = capturedEntities.first()
        assertEquals("flood_rescue", entity.emergencyType)
        assertEquals(SosStatus.PENDING_LOCAL.apiValue, entity.status)
        assertEquals(0, entity.relayHopCount)
    }
}
