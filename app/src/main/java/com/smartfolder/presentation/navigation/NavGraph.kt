package com.smartfolder.presentation.navigation

import androidx.compose.runtime.Composable
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.smartfolder.presentation.screens.analysis.AnalysisScreen
import com.smartfolder.presentation.screens.analysis.AnalysisViewModel
import com.smartfolder.presentation.screens.home.HomeScreen
import com.smartfolder.presentation.screens.home.HomeViewModel
import com.smartfolder.presentation.screens.results.ResultsScreen
import com.smartfolder.presentation.screens.results.ResultsViewModel
import com.smartfolder.presentation.screens.settings.SettingsScreen
import com.smartfolder.presentation.screens.settings.SettingsViewModel

@Composable
fun NavGraph(
    navController: NavHostController,
    onSelectReferenceFolder: () -> Unit,
    onSelectUnsortedFolder: () -> Unit
) {
    NavHost(
        navController = navController,
        startDestination = Screen.Home.route
    ) {
        composable(Screen.Home.route) {
            val viewModel: HomeViewModel = hiltViewModel()
            HomeScreen(
                viewModel = viewModel,
                onSelectReferenceFolder = onSelectReferenceFolder,
                onSelectUnsortedFolder = onSelectUnsortedFolder,
                onNavigateToAnalysis = {
                    navController.navigate(Screen.Analysis.route)
                },
                onNavigateToSettings = {
                    navController.navigate(Screen.Settings.route)
                }
            )
        }

        composable(Screen.Analysis.route) {
            val viewModel: AnalysisViewModel = hiltViewModel()
            AnalysisScreen(
                viewModel = viewModel,
                onNavigateBack = { navController.popBackStack() },
                onNavigateToResults = {
                    navController.navigate(Screen.Results.route) {
                        launchSingleTop = true
                    }
                }
            )
        }

        composable(Screen.Results.route) {
            // Suggestions are loaded from DB in ResultsViewModel.init via
            // LoadSuggestionsUseCase. AnalyzeImagesUseCase persists them
            // before navigating here, so no back-stack dependency is needed.
            val viewModel: ResultsViewModel = hiltViewModel()
            ResultsScreen(
                viewModel = viewModel,
                onNavigateBack = {
                    navController.popBackStack(Screen.Home.route, inclusive = false)
                }
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
