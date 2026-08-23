package com.sih.app.ui.home

import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.android.gms.tasks.Tasks
import com.sih.data.repository.SosRepository
import io.mockk.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private val context = mockk<android.content.Context>(relaxed = true)
    private val sosRepository = mockk<SosRepository>(relaxed = true)
    private val profileRepository = mockk<com.sih.data.repository.UserMedicalProfileRepository>(relaxed = true)
    private val locationProvider = mockk<FusedLocationProviderClient>(relaxed = true)

    private lateinit var viewModel: HomeViewModel

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        mockkStatic(LocationServices::class)
        every { LocationServices.getFusedLocationProviderClient(any<android.content.Context>()) } returns locationProvider
        every { locationProvider.lastLocation } returns Tasks.forResult(null)

        coEvery { sosRepository.createSos(any(), any(), any(), any(), any(), any(), any(), any(), any(), any()) } returns "sos_uuid_123"
        coEvery { profileRepository.getSnapshot() } returns null
        viewModel = HomeViewModel(context, sosRepository, profileRepository)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        unmockkAll()
    }

    @Test
    fun `triggerSosWithPermissionResult with granted permission resolves location`() = runTest {
        viewModel.triggerSosWithPermissionResult(isQuickSos = true, isGranted = true)

        val state = viewModel.uiState.value
        assertTrue(state.isCreatingSos)
        assertEquals(LocationStatus.FETCHING, state.locationStatus)
    }

    @Test
    fun `triggerSosWithPermissionResult with denied permission uses fallback 0,0`() = runTest {
        viewModel.triggerSosWithPermissionResult(isQuickSos = true, isGranted = false)

        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertNotNull(state.createdSosUuid)

        coVerify {
            sosRepository.createSos(
                lat = 0.0,
                lng = 0.0,
                accuracyM = null,
                isQuickSos = true,
                profile = any()
            )
        }
    }
}
