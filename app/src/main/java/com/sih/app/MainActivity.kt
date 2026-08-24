package com.sih.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.lifecycle.Lifecycle
import androidx.navigation.compose.rememberNavController
import com.sih.app.navigation.AppNavigation
import com.sih.app.navigation.Screen
import com.sih.app.ui.theme.SihTheme
import com.sih.data.di.enqueueDeviceRegistration
import com.sih.data.prefs.DevicePreferences
import com.sih.data.repository.UserMedicalProfileRepository
import com.sih.relay.RelayPermissionRequirements
import com.sih.relay.service.RelayForegroundService
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

    private var relayServiceStarted = false

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

                // ── Relay permission + service startup ───────────────────
                var relayPermissionsNeeded by remember { mutableStateOf(false) }
                var shouldRequestPermissions by remember { mutableStateOf(false) }

                val relayLauncher = rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.RequestMultiplePermissions()
                ) { results ->
                    val anyDenied = results.values.any { !it }
                    if (!anyDenied && !relayServiceStarted) {
                        relayServiceStarted = true
                        RelayForegroundService.start(this@MainActivity)
                    }
                }

                LaunchedEffect(Unit) {
                    if (RelayPermissionRequirements.hasRequiredPermissions(this@MainActivity)) {
                        if (!relayServiceStarted) {
                            relayServiceStarted = true
                            RelayForegroundService.start(this@MainActivity)
                        }
                    } else {
                        relayPermissionsNeeded = true
                    }
                }

                if (relayPermissionsNeeded) {
                    LaunchedEffect(shouldRequestPermissions) {
                        if (shouldRequestPermissions) {
                            shouldRequestPermissions = false
                            val missing = RelayPermissionRequirements
                                .missingPermissions(this@MainActivity)
                            if (missing.isNotEmpty()) {
                                relayLauncher.launch(missing.toTypedArray())
                            } else {
                                if (!relayServiceStarted) {
                                    relayServiceStarted = true
                                    RelayForegroundService.start(this@MainActivity)
                                }
                            }
                        }
                    }

                    LaunchedEffect(lifecycle.currentState) {
                        snapshotFlow { lifecycle.currentState }
                            .collect { state ->
                                if (state == Lifecycle.State.RESUMED &&
                                    !relayServiceStarted &&
                                    RelayPermissionRequirements.hasRequiredPermissions(
                                        this@MainActivity
                                    )
                                ) {
                                    relayServiceStarted = true
                                    RelayForegroundService.start(this@MainActivity)
                                }
                            }
                    }

                    shouldRequestPermissions = true
                }
                // ── End relay ────────────────────────────────────────────

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
