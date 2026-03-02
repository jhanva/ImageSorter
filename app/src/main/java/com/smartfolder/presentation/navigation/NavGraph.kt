package com.smartfolder.presentation.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
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
    // Share AnalysisViewModel between Analysis and Results screens
    val analysisEntry = remember(navController) {
        navController.getBackStackEntry(Screen.Home.route)
    }

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
                        // Pass suggestions via the saved state handle
                        launchSingleTop = true
                    }
                }
            )
        }

        composable(Screen.Results.route) {
            val viewModel: ResultsViewModel = hiltViewModel()
            // Get suggestions from the analysis back stack entry
            val analysisViewModel: AnalysisViewModel = hiltViewModel(
                navController.previousBackStackEntry ?: it
            )
            val suggestions = analysisViewModel.uiState.value.suggestions
            viewModel.setSuggestions(suggestions)

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
