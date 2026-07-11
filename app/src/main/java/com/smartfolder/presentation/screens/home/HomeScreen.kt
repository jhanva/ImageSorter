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
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
import com.smartfolder.domain.model.IndexingProgress
import com.smartfolder.presentation.components.ErrorBanner
import com.smartfolder.presentation.components.FolderSelectionBottomSheet
import com.smartfolder.presentation.components.ProgressIndicator
import com.smartfolder.presentation.visual.HomeHeroStage
import com.smartfolder.presentation.visual.HomeVisuals

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    viewModel: HomeViewModel,
    onNavigateToAnalysis: () -> Unit,
    onNavigateToResults: () -> Unit,
    onNavigateToSettings: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = androidx.compose.ui.platform.LocalContext.current
    var showFolderSheet by remember { mutableStateOf(false) }
    var pendingRole by remember { mutableStateOf<FolderSelectRole?>(null) }
    var folderPendingRemoval by remember { mutableStateOf<Folder?>(null) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val hero = HomeVisuals.buildHeroContent(uiState)
    val isIndexing = uiState.isIndexingDestinations || uiState.isIndexingSources

    androidx.compose.runtime.LaunchedEffect(Unit) {
        viewModel.refreshPendingReview()
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            viewModel.refreshAvailableImageFolders()
            showFolderSheet = true
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
        showFolderSheet = true
    }

    fun handleHeroAction() {
        when {
            uiState.canAnalyze && !isIndexing -> onNavigateToAnalysis()
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
                val folderUri = DocumentsContract.buildTreeDocumentUri(
                    "com.android.externalstorage.documents",
                    selected.documentId
                )
                when (pendingRole) {
                    FolderSelectRole.DESTINATION -> viewModel.addDestinationFolder(folderUri)
                    FolderSelectRole.SOURCE -> viewModel.addSourceFolder(folderUri)
                    null -> Unit
                }
                pendingRole = null
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
                title = {
                    Column {
                        Text(stringResource(R.string.app_name))
                        Text(
                            text = stringResource(R.string.home_subtitle),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
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
                primaryEnabled = !isIndexing,
                pendingReviewCount = uiState.pendingReviewCount,
                onPrimaryAction = ::handleHeroAction,
                onContinueReview = onNavigateToResults
            )

            FolderSection(
                title = stringResource(R.string.home_destinations_title),
                emptyMessage = stringResource(R.string.home_destinations_empty),
                folders = uiState.destinationFolders,
                isIndexing = uiState.isIndexingDestinations,
                progress = uiState.destinationIndexingProgress,
                onAddFolder = { openFolderPicker(FolderSelectRole.DESTINATION) },
                onReindexFolder = viewModel::reindexFolder,
                onRemoveFolder = { folderPendingRemoval = it }
            )

            FolderSection(
                title = stringResource(R.string.home_sources_title),
                emptyMessage = stringResource(R.string.home_sources_empty),
                folders = uiState.sourceFolders,
                isIndexing = uiState.isIndexingSources,
                progress = uiState.sourceIndexingProgress,
                onAddFolder = { openFolderPicker(FolderSelectRole.SOURCE) },
                onReindexFolder = viewModel::reindexFolder,
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
    primaryEnabled: Boolean,
    pendingReviewCount: Int,
    onPrimaryAction: () -> Unit,
    onContinueReview: () -> Unit
) {
    val title = when (stage) {
        HomeHeroStage.SETUP -> stringResource(R.string.hero_setup_title)
        HomeHeroStage.INDEXING -> stringResource(R.string.hero_indexing_title)
        HomeHeroStage.READY -> stringResource(R.string.hero_ready_title)
    }
    val subtitle = when (stage) {
        HomeHeroStage.SETUP -> stringResource(R.string.hero_setup_subtitle)
        HomeHeroStage.INDEXING -> stringResource(R.string.hero_indexing_subtitle)
        HomeHeroStage.READY -> stringResource(R.string.hero_ready_subtitle)
    }
    val actionLabel = when (stage) {
        HomeHeroStage.SETUP -> stringResource(R.string.action_choose_folders)
        HomeHeroStage.INDEXING -> stringResource(R.string.action_indexing)
        HomeHeroStage.READY -> stringResource(R.string.action_analyze)
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
                enabled = primaryEnabled,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(actionLabel)
            }
            if (pendingReviewCount > 0) {
                TextButton(
                    onClick = onContinueReview,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(stringResource(R.string.action_continue_review, pendingReviewCount))
                }
            }
        }
    }
}

@Composable
private fun FolderSection(
    title: String,
    emptyMessage: String,
    folders: List<Folder>,
    isIndexing: Boolean,
    progress: IndexingProgress,
    onAddFolder: () -> Unit,
    onReindexFolder: (Folder) -> Unit,
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
                        enabled = !isIndexing,
                        onReindex = { onReindexFolder(folder) },
                        onRemove = { onRemoveFolder(folder) }
                    )
                }
            }

            if (isIndexing) {
                Spacer(modifier = Modifier.size(6.dp))
                ProgressIndicator(
                    phaseLabel = stringResource(R.string.action_indexing),
                    current = progress.current,
                    total = progress.total,
                    currentFileName = progress.currentFileName
                )
            }
        }
    }
}

@Composable
private fun FolderRow(
    folder: Folder,
    enabled: Boolean,
    onReindex: () -> Unit,
    onRemove: () -> Unit
) {
    var menuExpanded by remember { mutableStateOf(false) }

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
                text = if (folder.imageCount > 0) {
                    stringResource(R.string.folder_indexed_count, folder.indexedCount, folder.imageCount)
                } else {
                    stringResource(R.string.folder_image_count, folder.imageCount)
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Box {
            IconButton(onClick = { menuExpanded = true }, enabled = enabled) {
                Icon(
                    imageVector = Icons.Default.MoreVert,
                    contentDescription = stringResource(R.string.folder_options)
                )
            }
            DropdownMenu(
                expanded = menuExpanded,
                onDismissRequest = { menuExpanded = false }
            ) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.menu_reindex)) },
                    onClick = {
                        menuExpanded = false
                        onReindex()
                    }
                )
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.menu_remove)) },
                    onClick = {
                        menuExpanded = false
                        onRemove()
                    }
                )
            }
        }
    }
}
