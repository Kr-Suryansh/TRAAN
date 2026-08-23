package com.sih.app.navigation

sealed class Screen(val route: String) {
    data object Onboarding : Screen("onboarding")
    data object Home       : Screen("home")
    data object Status     : Screen("status/{uuid}") {
        fun withUuid(uuid: String) = "status/$uuid"
    }
}
