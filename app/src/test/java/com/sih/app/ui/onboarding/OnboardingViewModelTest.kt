package com.sih.app.ui.onboarding

import com.sih.data.prefs.DevicePreferences
import com.sih.data.repository.UserMedicalProfileRepository
import io.mockk.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class OnboardingViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private val profileRepository = mockk<UserMedicalProfileRepository>(relaxed = true)
    private val devicePreferences = mockk<DevicePreferences>(relaxed = true)

    private lateinit var viewModel: OnboardingViewModel

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        viewModel = OnboardingViewModel(profileRepository, devicePreferences)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `skipOnboarding sets preference to true and updates state`() = runTest {
        viewModel.skipOnboarding()

        verify { devicePreferences.setOnboardingSkipped(true) }

        val state = viewModel.state.value
        assertTrue(state.savedSuccessfully)
    }

    @Test
    fun `saveProfile validates blood type successfully`() = runTest {
        viewModel.onBloodTypeChange("O+")
        viewModel.saveProfile()

        advanceUntilIdle()

        val state = viewModel.state.value
        assertFalse(state.validationErrors.containsKey("bloodType"))
    }

    @Test
    fun `saveProfile invalidates wrong blood type`() = runTest {
        viewModel.onBloodTypeChange("C+")
        viewModel.saveProfile()

        advanceUntilIdle()

        val state = viewModel.state.value
        assertTrue(state.validationErrors.containsKey("bloodType"))
    }
}
