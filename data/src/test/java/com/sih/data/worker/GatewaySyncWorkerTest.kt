package com.sih.data.worker

import android.content.Context
import androidx.work.ListenableWorker.Result
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.sih.data.db.dao.SosRequestDao
import com.sih.data.db.entity.SosRequestEntity
import com.sih.data.model.SosStatus
import com.sih.data.prefs.DevicePreferences
import com.sih.network.api.DisasterApi
import com.sih.network.model.response.BatchUploadResponse
import com.sih.relay.RelayRepository
import io.mockk.*
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import retrofit2.Response
import java.time.Instant
import java.time.format.DateTimeFormatter

import android.location.Location
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.android.gms.tasks.OnFailureListener
import com.google.android.gms.tasks.OnSuccessListener
import com.google.android.gms.tasks.Task

@OptIn(ExperimentalCoroutinesApi::class)
class GatewaySyncWorkerTest {

    private val context = mockk<Context>(relaxed = true)
    private val sosRequestDao = mockk<SosRequestDao>(relaxed = true)
    private val disasterApi = mockk<DisasterApi>()
    private val devicePreferences = mockk<DevicePreferences>(relaxed = true)
    private val relayRepository = mockk<RelayRepository>(relaxed = true)
    private val workManager = mockk<WorkManager>(relaxed = true)
    private val fusedLocationProviderClient = mockk<FusedLocationProviderClient>(relaxed = true)
    private val locationTask = mockk<Task<Location?>>()

    @Before
    fun setUp() {
        mockkStatic(android.util.Log::class)
        every { android.util.Log.d(any(), any()) } returns 0
        every { android.util.Log.i(any(), any()) } returns 0
        every { android.util.Log.w(any(), any<String>()) } returns 0
        every { android.util.Log.w(any(), any<Throwable>()) } returns 0
        every { android.util.Log.e(any(), any<String>()) } returns 0
        every { android.util.Log.e(any(), any<String>(), any()) } returns 0

        mockkStatic(LocationServices::class)
        every { LocationServices.getFusedLocationProviderClient(any<Context>()) } returns fusedLocationProviderClient
        every { fusedLocationProviderClient.lastLocation } returns locationTask
        every { locationTask.addOnSuccessListener(any<OnSuccessListener<Location?>>()) } answers {
            val listener = firstArg<OnSuccessListener<Location?>>()
            listener.onSuccess(null)
            locationTask
        }
        every { locationTask.addOnFailureListener(any<OnFailureListener>()) } returns locationTask

        every { devicePreferences.isRegistered() } returns true
        every { devicePreferences.getDeviceId() } returns "device_123"
        every { devicePreferences.getDeviceJwt() } returns "jwt_123"
        coEvery { relayRepository.getRelayedSosEntries() } returns emptyList()
    }

    private fun createWorker(): GatewaySyncWorker {
        val params = mockk<WorkerParameters>(relaxed = true)
        return GatewaySyncWorker(
            context = context,
            workerParams = params,
            sosRequestDao = sosRequestDao,
            disasterApi = disasterApi,
            devicePreferences = devicePreferences,
            relayRepository = relayRepository,
            workManager = workManager
        )
    }

    @Test
    fun `doWork returns retry if device is not registered`() = runTest {
        every { devicePreferences.isRegistered() } returns false

        val worker = createWorker()
        val result = worker.doWork()

        assertEquals(Result.retry(), result)
        verify { workManager.enqueueUniqueWork(any<String>(), any<androidx.work.ExistingWorkPolicy>(), any<androidx.work.OneTimeWorkRequest>()) }
    }

    @Test
    fun `doWork returns success when no SOS records to upload`() = runTest {
        coEvery { sosRequestDao.getAllSos() } returns emptyList()

        val worker = createWorker()
        val result = worker.doWork()

        assertEquals(Result.success(), result)
        coVerify(exactly = 0) { disasterApi.uploadBatch(any()) }
    }

    @Test
    fun `doWork uploads records and marks them as uploaded on success`() = runTest {
        val nowIso = Instant.now().let { DateTimeFormatter.ISO_INSTANT.format(it) }
        val sosEntity = SosRequestEntity(
            uuid = "uuid_1",
            deviceId = "device_1",
            createdAt = nowIso,
            locationLat = 1.0,
            locationLng = 2.0,
            locationAccuracyM = null,
            isQuickSos = true,
            status = SosStatus.PENDING_LOCAL.apiValue,
            peopleCount = 1,
            lastRelayedAt = nowIso
        )
        coEvery { sosRequestDao.getAllSos() } returns listOf(sosEntity)

        val response = BatchUploadResponse(
            acceptedUuids = listOf("uuid_1"),
            duplicateUuids = emptyList()
        )
        coEvery { disasterApi.uploadBatch(any()) } returns Response.success(response)

        val worker = createWorker()
        val result = worker.doWork()

        assertEquals(Result.success(), result)
        coVerify { sosRequestDao.markUploaded(listOf("uuid_1")) }
        coVerify { sosRequestDao.deleteExpiredUploaded(any(), any()) }
    }

    @Test
    fun `doWork handles duplicates properly`() = runTest {
        val nowIso = Instant.now().let { DateTimeFormatter.ISO_INSTANT.format(it) }
        val sosEntity = SosRequestEntity(
            uuid = "uuid_1",
            deviceId = "device_1",
            createdAt = nowIso,
            locationLat = 1.0,
            locationLng = 2.0,
            locationAccuracyM = null,
            isQuickSos = true,
            status = SosStatus.PENDING_LOCAL.apiValue,
            peopleCount = 1,
            lastRelayedAt = nowIso
        )
        coEvery { sosRequestDao.getAllSos() } returns listOf(sosEntity)

        val response = BatchUploadResponse(
            acceptedUuids = emptyList(),
            duplicateUuids = listOf("uuid_1")
        )
        coEvery { disasterApi.uploadBatch(any()) } returns Response.success(response)

        val worker = createWorker()
        val result = worker.doWork()

        assertEquals(Result.success(), result)
        // Duplicates are also marked as uploaded locally
        coVerify { sosRequestDao.markUploaded(listOf("uuid_1")) }
    }

    @Test
    fun `doWork returns retry on 401 and clears credentials`() = runTest {
        val nowIso = Instant.now().let { DateTimeFormatter.ISO_INSTANT.format(it) }
        val sosEntity = SosRequestEntity(
            uuid = "uuid_1",
            deviceId = "device_1",
            createdAt = nowIso,
            locationLat = 1.0,
            locationLng = 2.0,
            locationAccuracyM = null,
            isQuickSos = true,
            status = SosStatus.PENDING_LOCAL.apiValue,
            peopleCount = 1,
            lastRelayedAt = nowIso
        )
        coEvery { sosRequestDao.getAllSos() } returns listOf(sosEntity)
        coEvery { disasterApi.uploadBatch(any()) } returns Response.error(401, mockk(relaxed = true))

        val worker = createWorker()
        val result = worker.doWork()

        assertEquals(Result.retry(), result)
        verify { devicePreferences.clearCredentials() }
        verify { workManager.enqueueUniqueWork(any<String>(), any<androidx.work.ExistingWorkPolicy>(), any<androidx.work.OneTimeWorkRequest>()) }
    }

    @Test
    fun `doWork returns retry on 500 error`() = runTest {
        val nowIso = Instant.now().let { DateTimeFormatter.ISO_INSTANT.format(it) }
        val sosEntity = SosRequestEntity(
            uuid = "uuid_1",
            deviceId = "device_1",
            createdAt = nowIso,
            locationLat = 1.0,
            locationLng = 2.0,
            locationAccuracyM = null,
            isQuickSos = true,
            status = SosStatus.PENDING_LOCAL.apiValue,
            peopleCount = 1,
            lastRelayedAt = nowIso
        )
        coEvery { sosRequestDao.getAllSos() } returns listOf(sosEntity)
        coEvery { disasterApi.uploadBatch(any()) } returns Response.error(500, mockk(relaxed = true))

        val worker = createWorker()
        val result = worker.doWork()

        assertEquals(Result.retry(), result)
    }
}
