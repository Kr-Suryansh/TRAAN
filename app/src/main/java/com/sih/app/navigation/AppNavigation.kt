package com.sih.app.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.sih.app.ui.home.HomeScreen
import com.sih.app.ui.onboarding.OnboardingScreen
import com.sih.app.ui.status.StatusScreen

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
                    onClearLastSos()
                    navController.popBackStack()
                }
            )
        }
    }
}
