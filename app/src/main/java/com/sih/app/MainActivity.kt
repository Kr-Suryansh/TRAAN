package com.sih.app

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
import com.sih.app.navigation.AppNavigation
import com.sih.app.navigation.Screen
import com.sih.app.ui.theme.SihTheme
import com.sih.data.di.enqueueDeviceRegistration
import com.sih.data.prefs.DevicePreferences
import com.sih.data.repository.UserMedicalProfileRepository
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

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

        devicePreferences.getOrCreateInstallationId()

        if (!devicePreferences.isRegistered()) {
            enqueueDeviceRegistration(workManager)
        }

        setContent {
            SihTheme {
                var startDestination by remember {
                    mutableStateOf<String?>(null)
                }

                LaunchedEffect(Unit) {
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

    private suspend fun resolveStartDestinationAsync(): String {
        val resumeUuid = devicePreferences.getLastSosUuid()
        if (resumeUuid != null) {
            return Screen.Status.withUuid(resumeUuid)
        }

        val hasProfile = userMedicalProfileRepository.getSnapshot() != null
        if (!hasProfile && !devicePreferences.isOnboardingSkipped()) {
            return Screen.Onboarding.route
        }

        return Screen.Home.route
    }
}
