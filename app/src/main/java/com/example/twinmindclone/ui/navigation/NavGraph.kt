package com.example.twinmindclone.ui.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.twinmindclone.ui.screens.DashboardScreen
import com.example.twinmindclone.ui.screens.RecordingScreen
import com.example.twinmindclone.ui.screens.SummaryScreen

@Composable
fun NavGraph() {
    val navController = rememberNavController()

    NavHost(
        navController = navController,
        startDestination = Screen.Dashboard.route
    ) {
        composable(Screen.Dashboard.route) {
            DashboardScreen(
                onNavigateToRecording = { meetingId ->
                    navController.navigate(Screen.Recording.createRoute(meetingId))
                },
                onNavigateToSummary = { meetingId ->
                    navController.navigate(Screen.Summary.createRoute(meetingId))
                }
            )
        }

        composable(
            route = Screen.Recording.route,
            arguments = listOf(navArgument(Screen.Recording.ARG) { type = NavType.LongType })
        ) { backStackEntry ->
            val meetingId = backStackEntry.arguments?.getLong(Screen.Recording.ARG) ?: -1L
            RecordingScreen(
                meetingId = meetingId,
                onRecordingComplete = { id ->
                    navController.navigate(Screen.Summary.createRoute(id)) {
                        popUpTo(Screen.Dashboard.route)
                    }
                }
            )
        }

        composable(
            route = Screen.Summary.route,
            arguments = listOf(navArgument(Screen.Summary.ARG) { type = NavType.LongType })
        ) { backStackEntry ->
            val meetingId = backStackEntry.arguments?.getLong(Screen.Summary.ARG) ?: -1L
            SummaryScreen(
                meetingId = meetingId,
                onNavigateBack = { navController.popBackStack() }
            )
        }
    }
}
