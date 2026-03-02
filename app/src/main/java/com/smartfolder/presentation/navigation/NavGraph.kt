package com.smartfolder.presentation.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
            val viewModel: ResultsViewModel = hiltViewModel()

            // Safely get AnalysisViewModel from previous back stack entry
            val previousEntry = navController.previousBackStackEntry
            val analysisViewModel: AnalysisViewModel? = previousEntry?.let { entry ->
                hiltViewModel(entry)
            }
            val suggestions = analysisViewModel?.uiState?.value?.suggestions ?: emptyList()

            // Set suggestions only once to avoid resetting on recomposition
            LaunchedEffect(Unit) {
                viewModel.setSuggestions(suggestions)
            }

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
