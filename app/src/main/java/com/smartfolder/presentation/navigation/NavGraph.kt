package com.smartfolder.presentation.navigation

import androidx.compose.runtime.Composable
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.smartfolder.presentation.screens.home.HomeScreen
import com.smartfolder.presentation.screens.home.HomeViewModel
import com.smartfolder.presentation.screens.settings.SettingsScreen
import com.smartfolder.presentation.screens.settings.SettingsViewModel
import com.smartfolder.presentation.screens.triage.TriageScreen
import com.smartfolder.presentation.screens.triage.TriageViewModel

@Composable
fun NavGraph(
    navController: NavHostController,
    homeViewModel: HomeViewModel
) {
    NavHost(
        navController = navController,
        startDestination = Screen.Home.route
    ) {
        composable(Screen.Home.route) {
            HomeScreen(
                viewModel = homeViewModel,
                onStartTriage = { folder ->
                    navController.navigate(Screen.Triage.createRoute(folder.id)) {
                        launchSingleTop = true
                    }
                },
                onNavigateToSettings = {
                    navController.navigate(Screen.Settings.route)
                }
            )
        }

        composable(
            route = Screen.Triage.route,
            arguments = listOf(navArgument("folderId") { type = NavType.LongType })
        ) {
            val viewModel: TriageViewModel = hiltViewModel()
            TriageScreen(
                viewModel = viewModel,
                onNavigateBack = { navController.popBackStack() }
            )
        }

        composable(Screen.Settings.route) {
            val viewModel: SettingsViewModel = hiltViewModel()
            SettingsScreen(
                viewModel = viewModel,
                onNavigateBack = { navController.popBackStack() }
            )
        }
    }
}
