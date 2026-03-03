package com.smartfolder.presentation

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.compose.rememberNavController
import com.smartfolder.domain.repository.SettingsRepository
import com.smartfolder.presentation.navigation.NavGraph
import com.smartfolder.presentation.screens.home.HomeViewModel
import com.smartfolder.presentation.theme.SmartFolderTheme
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject lateinit var settingsRepository: SettingsRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val darkMode by settingsRepository.darkMode.collectAsState(initial = false)
            SmartFolderTheme(darkTheme = darkMode) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    val navController = rememberNavController()
                    val viewModel: HomeViewModel = hiltViewModel()

                    NavGraph(
                        navController = navController,
                        homeViewModel = viewModel
                    )
                }
            }
        }
    }
}
