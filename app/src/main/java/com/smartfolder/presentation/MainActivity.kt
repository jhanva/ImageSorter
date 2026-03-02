package com.smartfolder.presentation

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
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

    private var pendingFolderRole: String? = null

    private val folderPickerLauncher = registerForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        uri ?: return@registerForActivityResult
        // Take persistable permission
        val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
        try {
            contentResolver.takePersistableUriPermission(uri, flags)
        } catch (e: SecurityException) {
            try {
                contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            } catch (ignored: SecurityException) {
                // Could not persist permission
            }
        }

        when (pendingFolderRole) {
            "REFERENCE" -> homeViewModel?.selectReferenceFolder(uri)
            "UNSORTED" -> homeViewModel?.selectUnsortedFolder(uri)
        }
        pendingFolderRole = null
    }

    private var homeViewModel: HomeViewModel? = null

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
                    homeViewModel = viewModel

                    NavGraph(
                        navController = navController,
                        onSelectReferenceFolder = {
                            pendingFolderRole = "REFERENCE"
                            folderPickerLauncher.launch(null)
                        },
                        onSelectUnsortedFolder = {
                            pendingFolderRole = "UNSORTED"
                            folderPickerLauncher.launch(null)
                        }
                    )
                }
            }
        }
    }
}
