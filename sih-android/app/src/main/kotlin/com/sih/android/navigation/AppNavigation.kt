package com.sih.android.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.sih.android.ui.home.HomeScreen
import com.sih.android.ui.onboarding.OnboardingScreen
import com.sih.android.ui.status.StatusScreen

/**
 * Root navigation graph.
 *
 * Start destination: [Screen.Home] on first launch, or [Screen.Status] if a
 * previously created SOS UUID is stored in [DevicePreferences].
 *
 * [onClearLastSos] is called when the citizen navigates back from Status → Home,
 * clearing the persisted UUID so a new SOS can be created fresh next launch.
 */
@Composable
fun AppNavigation(
    navController: NavHostController,
    startDestination: String = Screen.Home.route,
    onClearLastSos: () -> Unit = {}
) {
    NavHost(
        navController    = navController,
        startDestination = startDestination
    ) {
        composable(Screen.Onboarding.route) {
            OnboardingScreen(
                onDone = { navController.navigate(Screen.Home.route) {
                    popUpTo(Screen.Onboarding.route) { inclusive = true }
                }}
            )
        }

        composable(Screen.Home.route) {
            HomeScreen(
                onNavigateToOnboarding = {
                    navController.navigate(Screen.Onboarding.route)
                },
                onNavigateToStatus = { uuid ->
                    navController.navigate(Screen.Status.withUuid(uuid))
                }
            )
        }

        composable(
            route     = Screen.Status.route,
            arguments = listOf(navArgument("uuid") { type = NavType.StringType })
        ) { backStack ->
            val uuid = backStack.arguments?.getString("uuid") ?: return@composable
            StatusScreen(
                uuid   = uuid,
                onBack = {
                    // Clear the persisted UUID — citizen is starting fresh
                    onClearLastSos()
                    navController.popBackStack()
                }
            )
        }
    }
}
