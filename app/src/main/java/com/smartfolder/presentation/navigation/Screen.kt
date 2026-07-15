package com.smartfolder.presentation.navigation

sealed class Screen(val route: String) {
    data object Home : Screen("home")
    data object Triage : Screen("triage/{folderId}") {
        fun createRoute(folderId: Long): String = "triage/$folderId"
    }
    data object Trash : Screen("trash/{folderId}") {
        fun createRoute(folderId: Long): String = "trash/$folderId"
    }
    data object Settings : Screen("settings")
}
