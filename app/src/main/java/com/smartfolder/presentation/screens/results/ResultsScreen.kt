package com.smartfolder.presentation.screens.results

import android.app.Activity
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.DriveFileMove
import androidx.compose.material.icons.filled.Checklist
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.HelpOutline
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Snackbar
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.smartfolder.R
import com.smartfolder.domain.model.Folder
import com.smartfolder.domain.model.SuggestionItem
import com.smartfolder.domain.model.confidenceMargin
import com.smartfolder.presentation.components.EmptyState
import com.smartfolder.presentation.components.ErrorBanner
import com.smartfolder.presentation.components.ImagePreviewDialog
import com.smartfolder.presentation.components.SimilarImageRow
import com.smartfolder.presentation.components.ThresholdSlider

private const val DUPLICATE_SIMILARITY = 0.98f

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ResultsScreen(
    viewModel: ResultsViewModel,
    onNavigateBack: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    var destinationPickerImageId by remember { mutableLongStateOf(0L) }
    var previewImage by remember { mutableStateOf<SuggestionItem?>(null) }
    var pendingWriteAction by remember { mutableStateOf(WriteAction.NONE) }

    val writeRequestLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        val action = pendingWriteAction
        pendingWriteAction = WriteAction.NONE
        if (result.resultCode == Activity.RESULT_OK) {
            when (action) {
                WriteAction.MOVE -> viewModel.moveSelected()
                WriteAction.UNDO -> viewModel.undoLastMove()
                WriteAction.NONE -> Unit
            }
        } else {
            viewModel.setError(context.getString(R.string.results_write_denied))
        }
    }

    fun launchWithWritePermission(uris: List<Uri>, action: WriteAction, fallback: () -> Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && uris.isNotEmpty()) {
            try {
                pendingWriteAction = action
                val writeRequest = MediaStore.createWriteRequest(context.contentResolver, uris)
                writeRequestLauncher.launch(
                    IntentSenderRequest.Builder(writeRequest.intentSender).build()
                )
            } catch (e: Exception) {
                pendingWriteAction = WriteAction.NONE
                viewModel.setError(
                    e.message ?: context.getString(R.string.results_write_request_failed)
                )
            }
        } else {
            fallback()
        }
    }

    fun requestSelectedMove() {
        launchWithWritePermission(
            uris = viewModel.getSelectedImageUris(),
            action = WriteAction.MOVE,
            fallback = { viewModel.moveSelected() }
        )
    }

    fun requestUndo() {
        launchWithWritePermission(
            uris = viewModel.getUndoImageUris(),
            action = WriteAction.UNDO,
            fallback = { viewModel.undoLastMove() }
        )
    }

    if (destinationPickerImageId != 0L) {
        DestinationPickerDialog(
            destinations = uiState.destinationFolders,
            onDismiss = { destinationPickerImageId = 0L },
            onSelect = { destinationId ->
                viewModel.setDestinationOverride(destinationPickerImageId, destinationId)
                destinationPickerImageId = 0L
            }
        )
    }

    previewImage?.let { suggestion ->
        ImagePreviewDialog(
            uri = suggestion.image.uri,
            displayName = suggestion.image.displayName,
            onDismiss = { previewImage = null }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(stringResource(R.string.results_title))
                        Text(
                            text = stringResource(R.string.results_subtitle, uiState.filteredSuggestions.size),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.action_back)
                        )
                    }
                }
            )
        },
        snackbarHost = {
            uiState.moveSummary?.let { summary ->
                Snackbar(
                    action = {
                        Row {
                            if (uiState.canUndo) {
                                TextButton(onClick = { requestUndo() }) {
                                    Text(stringResource(R.string.action_undo))
                                }
                            }
                            TextButton(onClick = { viewModel.dismissMessage() }) {
                                Text(stringResource(R.string.action_dismiss))
                            }
                        }
                    }
                ) {
                    Text(moveSummaryText(summary))
                }
            }
        },
        bottomBar = {
            if (uiState.selectedCount > 0) {
                Button(
                    onClick = { requestSelectedMove() },
                    enabled = !uiState.isMoving,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                ) {
                    if (uiState.isMoving) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp
                        )
                    } else {
                        Icon(Icons.AutoMirrored.Filled.DriveFileMove, contentDescription = null)
                        Spacer(modifier = Modifier.size(8.dp))
                        Text(stringResource(R.string.results_move_selected, uiState.selectedCount))
                    }
                }
            }
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Column(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    uiState.error?.let { error ->
                        ErrorBanner(
                            message = error,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                    OverviewCard(
                        groupCount = uiState.destinationSections.size,
                        unassignedCount = uiState.filteredSuggestions.count { it.suggestedDestinationId == 0L },
                        selectedCount = uiState.selectedCount,
                        threshold = uiState.threshold,
                        onThresholdChange = viewModel::setThreshold,
                        onSelectHighConfidence = viewModel::selectHighConfidence,
                        onClearSelection = viewModel::clearSelection
                    )
                }
            }

            if (uiState.allSuggestions.isEmpty()) {
                item {
                    EmptyState(
                        title = stringResource(R.string.results_empty_title),
                        message = stringResource(R.string.results_empty_message),
                        illustrationRes = R.drawable.illus_no_photos,
                        modifier = Modifier.padding(horizontal = 16.dp)
                    )
                }
            } else if (uiState.filteredSuggestions.isEmpty()) {
                item {
                    EmptyState(
                        title = stringResource(R.string.results_empty_filtered_title),
                        message = stringResource(R.string.results_empty_filtered_message),
                        illustrationRes = R.drawable.illus_all_clean,
                        modifier = Modifier.padding(horizontal = 16.dp)
                    )
                }
            } else {
                items(
                    items = uiState.destinationSections,
                    key = { section -> section.destination.id }
                ) { section ->
                    DestinationSection(
                        section = section,
                        destinationFolders = uiState.destinationFolders,
                        selectedIds = uiState.selectedIds,
                        destinationOverrides = uiState.destinationOverrides,
                        isCollapsed = section.destination.id in uiState.collapsedSectionIds,
                        onToggleCollapse = { viewModel.toggleSection(section.destination.id) },
                        onSelectAll = { viewModel.selectAllInSection(section.destination.id) },
                        onToggleSelection = viewModel::toggleSelection,
                        onChangeDestination = { imageId -> destinationPickerImageId = imageId },
                        onQuickAssign = viewModel::setDestinationOverride,
                        onPreview = { previewImage = it }
                    )
                }
            }
        }
    }
}

private enum class WriteAction { NONE, MOVE, UNDO }

@Composable
private fun moveSummaryText(summary: MoveSummary): String {
    return when {
        summary.restored > 0 -> stringResource(R.string.results_restored_summary, summary.restored)
        summary.copiedOnly == 0 && summary.failed == 0 ->
            stringResource(R.string.results_moved_summary, summary.moved)
        else -> stringResource(
            R.string.results_moved_summary_detailed,
            summary.moved,
            summary.copiedOnly,
            summary.failed
        )
    }
}

@Composable
private fun OverviewCard(
    groupCount: Int,
    unassignedCount: Int,
    selectedCount: Int,
    threshold: Float,
    onThresholdChange: (Float) -> Unit,
    onSelectHighConfidence: () -> Unit,
    onClearSelection: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(R.string.results_groups_count, groupCount),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSecondaryContainer
                )
                if (unassignedCount > 0) {
                    Text(
                        text = stringResource(R.string.results_unassigned_count, unassignedCount),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.tertiary
                    )
                }
                if (selectedCount > 0) {
                    Text(
                        text = stringResource(R.string.results_selected_count, selectedCount),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
            ThresholdSlider(
                value = threshold,
                onValueChange = onThresholdChange
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = onSelectHighConfidence) {
                    Icon(
                        imageVector = Icons.Default.Checklist,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.size(6.dp))
                    Text(stringResource(R.string.results_select_confident))
                }
                if (selectedCount > 0) {
                    TextButton(onClick = onClearSelection) {
                        Text(stringResource(R.string.results_clear_selection))
                    }
                }
            }
        }
    }
}

@Composable
private fun DestinationSection(
    section: DestinationSuggestionSection,
    destinationFolders: List<Folder>,
    selectedIds: Set<Long>,
    destinationOverrides: Map<Long, Long>,
    isCollapsed: Boolean,
    onToggleCollapse: () -> Unit,
    onSelectAll: () -> Unit,
    onToggleSelection: (Long) -> Unit,
    onChangeDestination: (Long) -> Unit,
    onQuickAssign: (Long, Long) -> Unit,
    onPreview: (SuggestionItem) -> Unit
) {
    val isManualRouting = section.destination.id == 0L
    val sectionTitle = if (isManualRouting) {
        stringResource(R.string.results_unassigned_section)
    } else {
        section.destination.displayName
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isManualRouting) {
                MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.55f)
            } else {
                MaterialTheme.colorScheme.surface
            }
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onToggleCollapse() },
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (isManualRouting) {
                    Icon(
                        imageVector = Icons.Default.HelpOutline,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onTertiaryContainer
                    )
                    Spacer(modifier = Modifier.size(10.dp))
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = sectionTitle,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = stringResource(R.string.results_section_images, section.suggestions.size),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                TextButton(onClick = onSelectAll) {
                    Text(stringResource(R.string.results_section_select_all))
                }
                Icon(
                    imageVector = if (isCollapsed) Icons.Default.ExpandMore else Icons.Default.ExpandLess,
                    contentDescription = stringResource(
                        if (isCollapsed) R.string.results_expand_section else R.string.results_collapse_section
                    )
                )
            }

            AnimatedVisibility(visible = !isCollapsed) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    section.suggestions.forEach { suggestion ->
                        SuggestionCard(
                            suggestion = suggestion,
                            destinationFolders = destinationFolders,
                            assignedDestinationName = resolveDestinationName(
                                suggestion = suggestion,
                                destinationFolders = destinationFolders,
                                destinationOverrides = destinationOverrides
                            ),
                            isSelected = suggestion.image.id in selectedIds,
                            showCandidates = isManualRouting,
                            onToggleSelection = { onToggleSelection(suggestion.image.id) },
                            onChangeDestination = { onChangeDestination(suggestion.image.id) },
                            onQuickAssign = { destinationId ->
                                onQuickAssign(suggestion.image.id, destinationId)
                            },
                            onPreview = { onPreview(suggestion) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SuggestionCard(
    suggestion: SuggestionItem,
    destinationFolders: List<Folder>,
    assignedDestinationName: String?,
    isSelected: Boolean,
    showCandidates: Boolean,
    onToggleSelection: () -> Unit,
    onChangeDestination: () -> Unit,
    onQuickAssign: (Long) -> Unit,
    onPreview: () -> Unit
) {
    val isUnassigned = assignedDestinationName == null
    val isLikelyDuplicate = suggestion.topSimilarImages.firstOrNull()
        ?.let { it.score >= DUPLICATE_SIMILARITY } == true

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f)
        )
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Checkbox(
                checked = isSelected,
                onCheckedChange = { onToggleSelection() }
            )
            AsyncImage(
                model = suggestion.image.uri,
                contentDescription = stringResource(R.string.results_preview),
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(96.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .clickable { onPreview() }
            )
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text = suggestion.image.displayName,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                assignedDestinationName?.let { name ->
                    Text(
                        text = name,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    ScoreChip(
                        label = stringResource(R.string.results_match_label),
                        value = "${(suggestion.score * 100).toInt()}%"
                    )
                    ScoreChip(
                        label = stringResource(R.string.results_margin_label),
                        value = "${(suggestion.confidenceMargin * 100).toInt()}%"
                    )
                }
                if (isLikelyDuplicate) {
                    Text(
                        text = stringResource(R.string.results_possible_duplicate),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.tertiary
                    )
                }
                if (showCandidates && suggestion.candidateIds.isNotEmpty()) {
                    val foldersById = destinationFolders.associateBy { it.id }
                    Row(
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        suggestion.candidateIds
                            .zip(suggestion.candidateScores)
                            .mapNotNull { (id, score) ->
                                foldersById[id]?.let { Triple(id, it.displayName, score) }
                            }
                            .forEach { (id, name, score) ->
                                AssistChip(
                                    onClick = { onQuickAssign(id) },
                                    label = { Text("$name ${(score * 100).toInt()}%") }
                                )
                            }
                    }
                }
                if (suggestion.topSimilarImages.isNotEmpty()) {
                    SimilarImageRow(
                        matches = suggestion.topSimilarImages,
                        modifier = Modifier.horizontalScroll(rememberScrollState())
                    )
                }
                TextButton(onClick = onChangeDestination) {
                    Text(
                        text = if (isUnassigned) {
                            stringResource(R.string.results_choose_destination)
                        } else {
                            stringResource(R.string.results_change_destination)
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun ScoreChip(
    label: String,
    value: String
) {
    Row(
        modifier = Modifier
            .clip(CircleShape)
            .padding(0.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

@Composable
private fun DestinationPickerDialog(
    destinations: List<Folder>,
    onDismiss: () -> Unit,
    onSelect: (Long) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.results_choose_destination)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                destinations.forEach { destination ->
                    OutlinedButton(
                        onClick = { onSelect(destination.id) },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(destination.displayName)
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.action_cancel))
            }
        }
    )
}

private fun resolveDestinationName(
    suggestion: SuggestionItem,
    destinationFolders: List<Folder>,
    destinationOverrides: Map<Long, Long>
): String? {
    val destinationId = destinationOverrides[suggestion.image.id] ?: suggestion.suggestedDestinationId
    if (destinationId == 0L) return null
    return destinationFolders.firstOrNull { it.id == destinationId }?.displayName
}
