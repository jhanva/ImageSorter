package com.smartfolder.presentation.screens.home

import android.Manifest
import android.os.Build
import android.provider.DocumentsContract
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.smartfolder.R
import com.smartfolder.domain.model.Folder
import com.smartfolder.presentation.components.ErrorBanner
import com.smartfolder.presentation.components.FolderSelectionBottomSheet
import com.smartfolder.presentation.visual.HomeHeroStage
import com.smartfolder.presentation.visual.HomeVisuals

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    viewModel: HomeViewModel,
    onStartTriage: (Folder) -> Unit,
    onNavigateToSettings: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = androidx.compose.ui.platform.LocalContext.current
    var showFolderSheet by remember { mutableStateOf(false) }
    var pendingRole by remember { mutableStateOf<FolderSelectRole?>(null) }
    var folderPendingRemoval by remember { mutableStateOf<Folder?>(null) }
    var showSourcePicker by remember { mutableStateOf(false) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val hero = HomeVisuals.buildHeroContent(uiState)

    // Batch selection queues the remaining document ids; the system dialog is
    // re-launched for each one in turn because persistable permission is only
    // granted per confirmed tree. Cancelling one dialog aborts the rest.
    var pendingBatchIds by remember { mutableStateOf<List<String>>(emptyList()) }
    var nextPickerDocumentId by remember { mutableStateOf<String?>(null) }

    // The image-folder sheet is only a browsing aid: the actual grant comes
    // from the system OpenDocumentTree dialog, pre-positioned at the chosen
    // folder so the user just confirms it. Constructed tree uris cannot get
    // persistable write permission.
    val folderPickerLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        val role = pendingRole
        if (uri != null && role != null) {
            when (role) {
                FolderSelectRole.DESTINATION -> viewModel.addDestinationFolder(uri)
                FolderSelectRole.SOURCE -> viewModel.addSourceFolder(uri)
            }
        }
        if (uri != null && role != null && pendingBatchIds.isNotEmpty()) {
            nextPickerDocumentId = pendingBatchIds.first()
            pendingBatchIds = pendingBatchIds.drop(1)
        } else {
            pendingBatchIds = emptyList()
            pendingRole = null
        }
    }

    LaunchedEffect(nextPickerDocumentId) {
        val documentId = nextPickerDocumentId ?: return@LaunchedEffect
        nextPickerDocumentId = null
        folderPickerLauncher.launch(
            DocumentsContract.buildDocumentUri(
                "com.android.externalstorage.documents",
                documentId
            )
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            viewModel.refreshAvailableImageFolders()
            showFolderSheet = true
        } else {
            pendingRole = null
        }
    }

    fun openFolderPicker(role: FolderSelectRole) {
        pendingRole = role
        val permission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            Manifest.permission.READ_MEDIA_IMAGES
        } else {
            Manifest.permission.READ_EXTERNAL_STORAGE
        }
        val granted = ContextCompat.checkSelfPermission(
            context,
            permission
        ) == android.content.pm.PackageManager.PERMISSION_GRANTED
        if (!granted) {
            permissionLauncher.launch(permission)
            return
        }
        viewModel.refreshAvailableImageFolders()
        showFolderSheet = true
    }

    fun handleHeroAction() {
        when {
            uiState.canStartTriage -> {
                val sources = uiState.sourceFolders
                if (sources.size == 1) {
                    onStartTriage(sources.first())
                } else {
                    showSourcePicker = true
                }
            }
            uiState.destinationFolders.isEmpty() -> openFolderPicker(FolderSelectRole.DESTINATION)
            else -> openFolderPicker(FolderSelectRole.SOURCE)
        }
    }

    if (showFolderSheet) {
        FolderSelectionBottomSheet(
            sheetState = sheetState,
            folders = uiState.availableImageFolders,
            isLoading = uiState.isLoadingImageFolders,
            onDismiss = {
                showFolderSheet = false
                pendingRole = null
            },
            onSelect = { selected ->
                showFolderSheet = false
                val initialUri = DocumentsContract.buildDocumentUri(
                    "com.android.externalstorage.documents",
                    selected.documentId
                )
                folderPickerLauncher.launch(initialUri)
            },
            onSelectMultiple = { selected ->
                showFolderSheet = false
                pendingBatchIds = selected.drop(1).map { it.documentId }
                nextPickerDocumentId = selected.firstOrNull()?.documentId
            }
        )
    }

    if (showSourcePicker) {
        AlertDialog(
            onDismissRequest = { showSourcePicker = false },
            title = { Text(stringResource(R.string.triage_pick_source_title)) },
            text = {
                Column {
                    uiState.sourceFolders.forEach { folder ->
                        TextButton(
                            onClick = {
                                showSourcePicker = false
                                onStartTriage(folder)
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = folder.displayName,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showSourcePicker = false }) {
                    Text(stringResource(R.string.action_cancel))
                }
            }
        )
    }

    folderPendingRemoval?.let { folder ->
        AlertDialog(
            onDismissRequest = { folderPendingRemoval = null },
            title = { Text(stringResource(R.string.remove_folder_title)) },
            text = { Text(stringResource(R.string.remove_folder_message, folder.displayName)) },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.removeFolder(folder)
                    folderPendingRemoval = null
                }) {
                    Text(
                        text = stringResource(R.string.action_remove),
                        color = MaterialTheme.colorScheme.error
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { folderPendingRemoval = null }) {
                    Text(stringResource(R.string.action_cancel))
                }
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.app_name)) },
                actions = {
                    IconButton(onClick = onNavigateToSettings) {
                        Icon(
                            imageVector = Icons.Outlined.Settings,
                            contentDescription = stringResource(R.string.action_settings)
                        )
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            uiState.error?.let { error ->
                ErrorBanner(message = error)
            }

            HeroCard(
                stage = hero.stage,
                progress = hero.progress,
                stepsLabel = stringResource(R.string.hero_steps, hero.completedSteps, hero.totalSteps),
                onPrimaryAction = ::handleHeroAction
            )

            FolderSection(
                title = stringResource(R.string.home_destinations_title),
                emptyMessage = stringResource(R.string.home_destinations_empty),
                folders = uiState.destinationFolders,
                onAddFolder = { openFolderPicker(FolderSelectRole.DESTINATION) },
                onRemoveFolder = { folderPendingRemoval = it }
            )

            FolderSection(
                title = stringResource(R.string.home_sources_title),
                emptyMessage = stringResource(R.string.home_sources_empty),
                folders = uiState.sourceFolders,
                onAddFolder = { openFolderPicker(FolderSelectRole.SOURCE) },
                onRemoveFolder = { folderPendingRemoval = it }
            )
        }
    }
}

private enum class FolderSelectRole {
    DESTINATION,
    SOURCE
}

@Composable
private fun HeroCard(
    stage: HomeHeroStage,
    progress: Float,
    stepsLabel: String,
    onPrimaryAction: () -> Unit
) {
    val title = when (stage) {
        HomeHeroStage.SETUP -> stringResource(R.string.hero_setup_title)
        HomeHeroStage.READY -> stringResource(R.string.hero_ready_title)
    }
    val subtitle = when (stage) {
        HomeHeroStage.SETUP -> stringResource(R.string.hero_setup_subtitle)
        HomeHeroStage.READY -> stringResource(R.string.hero_ready_subtitle)
    }
    val actionLabel = when (stage) {
        HomeHeroStage.SETUP -> stringResource(R.string.action_choose_folders)
        HomeHeroStage.READY -> stringResource(R.string.action_start_triage)
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .animateContentSize(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.85f)
            )
            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier.fillMaxWidth(),
                trackColor = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.12f)
            )
            Text(
                text = stepsLabel,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.85f)
            )
            Button(
                onClick = onPrimaryAction,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(actionLabel)
            }
        }
    }
}

@Composable
private fun FolderSection(
    title: String,
    emptyMessage: String,
    folders: List<Folder>,
    onAddFolder: () -> Unit,
    onRemoveFolder: (Folder) -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .animateContentSize(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f)
                )
                TextButton(onClick = onAddFolder) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.size(4.dp))
                    Text(stringResource(R.string.home_add_folder))
                }
            }

            if (folders.isEmpty()) {
                Text(
                    text = emptyMessage,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 8.dp)
                )
            } else {
                folders.forEachIndexed { index, folder ->
                    if (index > 0) {
                        HorizontalDivider(
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
                        )
                    }
                    FolderRow(
                        folder = folder,
                        onRemove = { onRemoveFolder(folder) }
                    )
                }
            }
        }
    }
}

@Composable
private fun FolderRow(
    folder: Folder,
    onRemove: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Box(
            modifier = Modifier
                .background(
                    color = MaterialTheme.colorScheme.secondaryContainer,
                    shape = CircleShape
                )
                .padding(8.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Folder,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSecondaryContainer,
                modifier = Modifier.size(18.dp)
            )
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = folder.displayName,
                style = MaterialTheme.typography.titleSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = stringResource(R.string.folder_image_count, folder.imageCount),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        IconButton(onClick = onRemove) {
            Icon(
                imageVector = Icons.Default.Delete,
                contentDescription = stringResource(R.string.menu_remove),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
