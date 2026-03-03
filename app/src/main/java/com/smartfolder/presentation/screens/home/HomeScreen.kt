package com.smartfolder.presentation.screens.home

import android.Manifest
import android.os.Build
import android.provider.DocumentsContract
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.smartfolder.domain.model.IndexingPhase
import com.smartfolder.domain.model.ImageFolderOption
import com.smartfolder.presentation.components.ErrorBanner
import com.smartfolder.presentation.components.FolderCard
import com.smartfolder.presentation.components.ModelSelector
import com.smartfolder.presentation.components.ProgressIndicator

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    viewModel: HomeViewModel,
    onNavigateToAnalysis: () -> Unit,
    onNavigateToSettings: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = androidx.compose.ui.platform.LocalContext.current
    var showFolderDialog by remember { mutableStateOf(false) }
    var pendingRole by remember { mutableStateOf<FolderSelectRole?>(null) }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            viewModel.refreshAvailableImageFolders()
            showFolderDialog = true
        } else {
            viewModel.dismissError()
            pendingRole = null
        }
    }

    fun hasImagePermission(): Boolean {
        val permission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            Manifest.permission.READ_MEDIA_IMAGES
        } else {
            Manifest.permission.READ_EXTERNAL_STORAGE
        }
        return ContextCompat.checkSelfPermission(
            context,
            permission
        ) == android.content.pm.PackageManager.PERMISSION_GRANTED
    }

    fun requestImagePermission() {
        val permission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            Manifest.permission.READ_MEDIA_IMAGES
        } else {
            Manifest.permission.READ_EXTERNAL_STORAGE
        }
        permissionLauncher.launch(permission)
    }

    fun openFolderPicker(role: FolderSelectRole) {
        pendingRole = role
        if (!hasImagePermission()) {
            requestImagePermission()
            return
        }
        viewModel.refreshAvailableImageFolders()
        showFolderDialog = true
    }

    if (showFolderDialog) {
        FolderSelectionDialog(
            folders = uiState.availableImageFolders,
            isLoading = uiState.isLoadingImageFolders,
            onDismiss = {
                showFolderDialog = false
                pendingRole = null
            },
            onSelect = { selected ->
                showFolderDialog = false
                val folderUri = DocumentsContract.buildTreeDocumentUri(
                    "com.android.externalstorage.documents",
                    selected.documentId
                )
                when (pendingRole) {
                    FolderSelectRole.REFERENCE -> viewModel.selectReferenceFolder(folderUri)
                    FolderSelectRole.UNSORTED -> viewModel.selectUnsortedFolder(folderUri)
                    null -> Unit
                }
                pendingRole = null
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Image Sorter") },
                actions = {
                    OutlinedButton(
                        onClick = onNavigateToSettings,
                        modifier = Modifier.padding(end = 8.dp)
                    ) {
                        Text("Settings")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            uiState.error?.let { error ->
                ErrorBanner(message = error)
            }

            // Model selection
            Text(
                text = "Model",
                style = MaterialTheme.typography.titleMedium
            )
            ModelSelector(
                selected = uiState.modelChoice,
                onSelected = { viewModel.setModelChoice(it) }
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Reference folder (A)
            Text(
                text = "Reference Folder (A)",
                style = MaterialTheme.typography.titleMedium
            )
            if (uiState.referenceFolder != null) {
                FolderCard(folder = uiState.referenceFolder!!)
                OutlinedButton(
                    onClick = { openFolderPicker(FolderSelectRole.REFERENCE) },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Change Reference Folder")
                }
                if (uiState.isIndexingRef) {
                    ProgressIndicator(
                        phase = uiState.refIndexingProgress.phase.name,
                        current = uiState.refIndexingProgress.current,
                        total = uiState.refIndexingProgress.total,
                        currentFileName = uiState.refIndexingProgress.currentFileName
                    )
                } else {
                    Button(
                        onClick = { viewModel.indexReferenceFolder() },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Index Reference Folder")
                    }
                }
            } else {
                OutlinedButton(
                    onClick = { openFolderPicker(FolderSelectRole.REFERENCE) },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Select Reference Folder")
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Unsorted folder (B)
            Text(
                text = "Unsorted Folder (B)",
                style = MaterialTheme.typography.titleMedium
            )
            if (uiState.unsortedFolder != null) {
                FolderCard(folder = uiState.unsortedFolder!!)
                OutlinedButton(
                    onClick = { openFolderPicker(FolderSelectRole.UNSORTED) },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Change Unsorted Folder")
                }
                if (uiState.isIndexingUnsorted) {
                    ProgressIndicator(
                        phase = uiState.unsortedIndexingProgress.phase.name,
                        current = uiState.unsortedIndexingProgress.current,
                        total = uiState.unsortedIndexingProgress.total,
                        currentFileName = uiState.unsortedIndexingProgress.currentFileName
                    )
                } else {
                    Button(
                        onClick = { viewModel.indexUnsortedFolder() },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Index Unsorted Folder")
                    }
                }
            } else {
                OutlinedButton(
                    onClick = { openFolderPicker(FolderSelectRole.UNSORTED) },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Select Unsorted Folder")
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Analyze button
            Button(
                onClick = onNavigateToAnalysis,
                enabled = uiState.canAnalyze && !uiState.isIndexingRef && !uiState.isIndexingUnsorted,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Analyze Images")
            }
        }
    }
}

private enum class FolderSelectRole {
    REFERENCE,
    UNSORTED
}

@Composable
private fun FolderSelectionDialog(
    folders: List<ImageFolderOption>,
    isLoading: Boolean,
    onDismiss: () -> Unit,
    onSelect: (ImageFolderOption) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Select image folder") },
        text = {
            if (isLoading) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    CircularProgressIndicator()
                    Text("Loading folders...")
                }
            } else if (folders.isEmpty()) {
                Text("No image folders found.")
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(folders) { folder ->
                        OutlinedButton(
                            onClick = { onSelect(folder) },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(folder.displayName)
                                Text("${folder.imageCount}")
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            if (folders.isEmpty() && !isLoading) {
                TextButton(onClick = onDismiss) {
                    Text("Close")
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
