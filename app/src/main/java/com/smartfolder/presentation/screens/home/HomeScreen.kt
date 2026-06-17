package com.smartfolder.presentation.screens.home

import android.Manifest
import android.os.Build
import android.provider.DocumentsContract
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Source
import androidx.compose.material.icons.filled.TaskAlt
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.smartfolder.domain.model.Folder
import com.smartfolder.domain.model.ImageFolderOption
import com.smartfolder.presentation.components.ErrorBanner
import com.smartfolder.presentation.components.FolderCard
import com.smartfolder.presentation.components.FolderSelectionBottomSheet
import com.smartfolder.presentation.components.ModelSelector
import com.smartfolder.presentation.components.ProgressIndicator
import com.smartfolder.presentation.visual.HomeVisuals

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    viewModel: HomeViewModel,
    onNavigateToAnalysis: () -> Unit,
    onNavigateToSettings: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = androidx.compose.ui.platform.LocalContext.current
    var showFolderSheet by remember { mutableStateOf(false) }
    var pendingRole by remember { mutableStateOf<FolderSelectRole?>(null) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val hero = HomeVisuals.buildHeroContent(uiState)

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
            uiState.canAnalyze && !uiState.isIndexingDestinations && !uiState.isIndexingSources -> onNavigateToAnalysis()
            uiState.destinationFolders.isEmpty() -> openFolderPicker(FolderSelectRole.DESTINATION)
            uiState.sourceFolders.isEmpty() -> openFolderPicker(FolderSelectRole.SOURCE)
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

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Image Sorter")
                        Text(
                            text = "Offline anime and game art routing",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                actions = {
                    IconButton(onClick = onNavigateToSettings) {
                        Icon(Icons.Default.Settings, contentDescription = "Settings")
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
                hero = hero,
                destinationCount = uiState.destinationFolders.size,
                sourceCount = uiState.sourceFolders.size,
                indexedImageCount = uiState.destinationFolders.sumOf { it.indexedCount } + uiState.sourceFolders.sumOf { it.indexedCount },
                onPrimaryAction = ::handleHeroAction,
                primaryEnabled = !uiState.isIndexingDestinations && !uiState.isIndexingSources
            )

            EngineCard(
                modelChoice = uiState.modelChoice,
                availableFolderCount = uiState.availableImageFolders.size,
                onSelectModel = viewModel::setModelChoice
            )

            FolderGroupCard(
                title = "Destination folders",
                subtitle = "Reference folders the model compares against.",
                icon = Icons.Default.TaskAlt,
                folders = uiState.destinationFolders,
                addButtonLabel = "Add destination folder",
                indexButtonLabel = "Index destinations",
                emptyMessage = "Add the categories you want the app to route into.",
                isIndexing = uiState.isIndexingDestinations,
                progress = uiState.destinationIndexingProgress,
                onAddFolder = { openFolderPicker(FolderSelectRole.DESTINATION) },
                onIndex = viewModel::indexDestinationFolders,
                onRemoveFolder = viewModel::removeFolder
            )

            FolderGroupCard(
                title = "Source folders",
                subtitle = "Input folders that will be matched against your destinations.",
                icon = Icons.Default.Source,
                folders = uiState.sourceFolders,
                addButtonLabel = "Add source folder",
                indexButtonLabel = "Index sources",
                emptyMessage = "Add one or more source folders to start routing images.",
                isIndexing = uiState.isIndexingSources,
                progress = uiState.sourceIndexingProgress,
                onAddFolder = { openFolderPicker(FolderSelectRole.SOURCE) },
                onIndex = viewModel::indexSourceFolders,
                onRemoveFolder = viewModel::removeFolder
            )

            Button(
                onClick = onNavigateToAnalysis,
                enabled = uiState.canAnalyze && !uiState.isIndexingDestinations && !uiState.isIndexingSources,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.AutoAwesome, contentDescription = null)
                Spacer(modifier = Modifier.size(8.dp))
                Text("Analyze library")
            }
        }
    }
}

private enum class FolderSelectRole {
    DESTINATION,
    SOURCE
}

@Composable
private fun HeroCard(
    hero: com.smartfolder.presentation.visual.HomeHeroContent,
    destinationCount: Int,
    sourceCount: Int,
    indexedImageCount: Int,
    onPrimaryAction: () -> Unit,
    primaryEnabled: Boolean
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(30.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(22.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            Text(
                text = hero.title,
                style = MaterialTheme.typography.displaySmall,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
            Text(
                text = hero.subtitle,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.88f)
            )
            LinearProgressIndicator(
                progress = { hero.progress },
                modifier = Modifier.fillMaxWidth(),
                trackColor = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.15f),
                color = MaterialTheme.colorScheme.secondary
            )
            Text(
                text = hero.progressLabel,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                MetricChip(label = "Destinations", value = destinationCount.toString())
                MetricChip(label = "Sources", value = sourceCount.toString())
                MetricChip(label = "Indexed", value = indexedImageCount.toString())
            }
            Button(
                onClick = onPrimaryAction,
                enabled = primaryEnabled,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(hero.primaryActionLabel)
            }
        }
    }
}

@Composable
private fun MetricChip(
    label: String,
    value: String
) {
    Column(
        modifier = Modifier
            .background(
                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.08f),
                shape = RoundedCornerShape(18.dp)
            )
            .padding(horizontal = 14.dp, vertical = 10.dp)
    ) {
        Text(
            text = value,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onPrimaryContainer,
            fontWeight = FontWeight.SemiBold
        )
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.84f)
        )
    }
}

@Composable
private fun EngineCard(
    modelChoice: com.smartfolder.domain.model.ModelChoice,
    availableFolderCount: Int,
    onSelectModel: (com.smartfolder.domain.model.ModelChoice) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "Embedding engine",
                style = MaterialTheme.typography.headlineSmall
            )
            Text(
                text = "Everything runs locally from the bundled TFLite models. No uploads, no cloud dependency.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            ModelSelector(
                selected = modelChoice,
                onSelected = onSelectModel
            )
            Text(
                text = "$availableFolderCount MediaStore folders detected on this device.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun FolderGroupCard(
    title: String,
    subtitle: String,
    icon: ImageVector,
    folders: List<Folder>,
    addButtonLabel: String,
    indexButtonLabel: String,
    emptyMessage: String,
    isIndexing: Boolean,
    progress: com.smartfolder.domain.model.IndexingProgress,
    onAddFolder: () -> Unit,
    onIndex: () -> Unit,
    onRemoveFolder: (Folder) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .background(
                            color = MaterialTheme.colorScheme.secondaryContainer,
                            shape = CircleShape
                        )
                        .padding(12.dp)
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                }
                Spacer(modifier = Modifier.size(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.headlineSmall
                    )
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            if (folders.isEmpty()) {
                Text(
                    text = emptyMessage,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                folders.forEach { folder ->
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        FolderCard(folder = folder)
                        OutlinedButton(
                            onClick = { onRemoveFolder(folder) },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Remove from library")
                        }
                    }
                }
            }

            if (isIndexing) {
                ProgressIndicator(
                    phase = progress.phase.name,
                    current = progress.current,
                    total = progress.total,
                    currentFileName = progress.currentFileName
                )
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedButton(
                        onClick = onAddFolder,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(addButtonLabel)
                    }
                    Button(
                        onClick = onIndex,
                        enabled = folders.isNotEmpty(),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(indexButtonLabel)
                    }
                }
            }
        }
    }
}
