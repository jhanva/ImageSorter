package com.smartfolder.presentation.navigation

sealed class Screen(val route: String) {
    data object Home : Screen("home")
    data object Analysis : Screen("analysis")
    data object Results : Screen("results")
    data object Settings : Screen("settings")
}
