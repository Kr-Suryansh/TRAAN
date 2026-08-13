package com.sih.android

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.navigation.compose.rememberNavController
import com.sih.android.navigation.AppNavigation
import com.sih.android.navigation.Screen
import com.sih.android.ui.theme.SihTheme
import com.sih.data.prefs.DevicePreferences
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/**
 * Single Activity — hosts the Compose NavHost.
 *
 * On launch:
 *  1. If a previously created SOS UUID is stored in prefs → start on Status screen.
 *     This survives process kills: the citizen picks up exactly where they left off.
 *  2. Otherwise → start on Home.
 *  3. Device registration is kicked off in the background (no-op if already registered).
 */
@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject
    lateinit var devicePreferences: DevicePreferences

    @Inject
    lateinit var workManager: androidx.work.WorkManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        if (!devicePreferences.isRegistered()) {
            com.sih.data.di.enqueueDeviceRegistration(workManager)
        }

        // If there's a stored SOS UUID from a previous session, resume its Status screen.
        val resumeUuid = devicePreferences.getLastSosUuid()
        val startDestination = if (resumeUuid != null) {
            Screen.Status.withUuid(resumeUuid)
        } else {
            Screen.Home.route
        }

        setContent {
            SihTheme {
                val navController = rememberNavController()

                AppNavigation(
                    navController    = navController,
                    startDestination = startDestination,
                    onClearLastSos   = { devicePreferences.clearLastSosUuid() }
                )
            }
        }
    }
}
