package com.sih.android.navigation

/** All nav destinations — add new screens here, never use raw strings elsewhere. */
sealed class Screen(val route: String) {
    data object Onboarding : Screen("onboarding")
    data object Home       : Screen("home")
    data object Status     : Screen("status/{uuid}") {
        fun withUuid(uuid: String) = "status/$uuid"
    }
}
