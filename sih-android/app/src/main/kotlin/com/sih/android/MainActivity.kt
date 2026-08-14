package com.sih.android

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.navigation.compose.rememberNavController
import com.sih.android.navigation.AppNavigation
import com.sih.android.navigation.Screen
import com.sih.android.ui.theme.SihTheme
import com.sih.data.di.enqueueDeviceRegistration
import com.sih.data.prefs.DevicePreferences
import com.sih.data.repository.UserMedicalProfileRepository
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/**
 * Single Activity — hosts the Compose NavHost.
 *
 * Start destination resolution (in priority order):
 *  1. Status screen — if a previously created SOS UUID is stored (survives process kills).
 *  2. Onboarding — if no SOS UUID AND no medical profile exists (first-ever launch).
 *     Onboarding is SKIPPABLE and must never block emergency SOS creation (rule P1.6.2).
 *  3. Home — default for returning users.
 *
 * Device registration is always kicked off in the background (no-op if already registered).
 * The stable installation UUID is also initialised here so it's ready before any SOS
 * is created (P0.4.6).
 */
@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject
    lateinit var devicePreferences: DevicePreferences

    @Inject
    lateinit var workManager: androidx.work.WorkManager

    @Inject
    lateinit var userMedicalProfileRepository: UserMedicalProfileRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Ensure stable installation ID exists before any SOS creation (P0.4.6)
        devicePreferences.getOrCreateInstallationId()

        // Trigger device registration in the background if not yet registered
        if (!devicePreferences.isRegistered()) {
            enqueueDeviceRegistration(workManager)
        }

        setContent {
            SihTheme {
                var startDestination by androidx.compose.runtime.remember {
                    androidx.compose.runtime.mutableStateOf<String?>(null)
                }

                androidx.compose.runtime.LaunchedEffect(Unit) {
                    startDestination = resolveStartDestinationAsync()
                }

                val destination = startDestination
                if (destination != null) {
                    val navController = rememberNavController()

                    AppNavigation(
                        navController    = navController,
                        startDestination = destination,
                        onClearLastSos   = { devicePreferences.clearLastSosUuid() }
                    )
                }
            }
        }
    }

    /**
     * Determines the initial navigation destination asynchronously.
     */
    private suspend fun resolveStartDestinationAsync(): String {
        // 1. Resume Status screen if there is a pending/recent SOS
        val resumeUuid = devicePreferences.getLastSosUuid()
        if (resumeUuid != null) {
            return Screen.Status.withUuid(resumeUuid)
        }

        // 2. Onboarding — if the user has never set up their medical profile and hasn't skipped.
        val hasProfile = userMedicalProfileRepository.getSnapshot() != null
        if (!hasProfile && !devicePreferences.isOnboardingSkipped()) {
            return Screen.Onboarding.route
        }

        // 3. Default: Home screen
        return Screen.Home.route
    }
}
