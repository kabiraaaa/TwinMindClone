package com.example.twinmindclone.ui.navigation

sealed class Screen(val route: String) {
    object Dashboard : Screen("dashboard")

    object Recording : Screen("recording/{meetingId}") {
        fun createRoute(meetingId: Long) = "recording/$meetingId"
        const val ARG = "meetingId"
    }

    object Summary : Screen("summary/{meetingId}") {
        fun createRoute(meetingId: Long) = "summary/$meetingId"
        const val ARG = "meetingId"
    }
}
