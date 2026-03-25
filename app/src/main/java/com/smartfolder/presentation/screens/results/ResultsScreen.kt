package com.smartfolder.presentation.screens.results

import android.app.Activity
import android.os.Build
import android.provider.MediaStore
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Snackbar
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.smartfolder.domain.model.SuggestionItem
import com.smartfolder.presentation.components.EmptyState
import com.smartfolder.presentation.components.ErrorBanner
import com.smartfolder.presentation.components.SimilarImageRow
import com.smartfolder.presentation.components.ThresholdSlider

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ResultsScreen(
    viewModel: ResultsViewModel,
    onNavigateBack: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    var pendingReferenceMoveIds by remember { mutableStateOf<Set<Long>>(emptySet()) }

    val writeRequestLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            viewModel.moveImagesToReference(pendingReferenceMoveIds)
        } else {
            viewModel.setError("Write permission denied. Could not move selected images.")
        }
        pendingReferenceMoveIds = emptySet()
    }

    val requestMoveToReference: (Set<Long>) -> Unit = { imageIds ->
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val uris = viewModel.getImageUris(imageIds)
            if (uris.isEmpty()) {
                viewModel.moveImagesToReference(imageIds)
            } else {
                try {
                    pendingReferenceMoveIds = imageIds
                    val writeRequest = MediaStore.createWriteRequest(
                        context.contentResolver,
                        uris
                    )
                    writeRequestLauncher.launch(
                        IntentSenderRequest.Builder(writeRequest.intentSender).build()
                    )
                } catch (e: Exception) {
                    pendingReferenceMoveIds = emptySet()
                    viewModel.setError(
                        e.message ?: "Could not request write permission for selected images."
                    )
                }
            }
        } else {
            viewModel.moveImagesToReference(imageIds)
        }
    }
    val moveSelectedImages = { requestMoveToReference(uiState.moveCandidateIds) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    if (uiState.manualMode) {
                        Text("Assisted Review (${uiState.selectedCount}/${uiState.visibleSuggestionCount})")
                    } else if (uiState.isReviewing) {
                        Text("Review ${uiState.reviewProgress}")
                    } else {
                        Text("Results (${uiState.filteredSuggestions.size})")
                    }
                },
                navigationIcon = {
                    IconButton(onClick = {
                        if (uiState.isReviewing) {
                            viewModel.cancelReview()
                        } else {
                            onNavigateBack()
                        }
                    }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        },
        snackbarHost = {
            uiState.moveResultMessage?.let { message ->
                Snackbar(
                    action = {
                        TextButton(onClick = { viewModel.dismissMessage() }) {
                            Text("Dismiss")
                        }
                    }
                ) {
                    Text(message)
                }
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            uiState.error?.let { error ->
                ErrorBanner(
                    message = error,
                    modifier = Modifier.padding(16.dp)
                )
            }

            when {
                uiState.manualMode -> ManualSelectionContent(
                    uiState = uiState,
                    onToggleSelection = viewModel::toggleSelection,
                    onToggleSectionSelection = viewModel::toggleSectionSelection,
                    onSelectAll = viewModel::selectAllFiltered,
                    onClearSelection = viewModel::clearSelection,
                    onSelectBestInDuplicateGroups = viewModel::selectBestInVisibleDuplicateGroups,
                    onSelectBestInVisualGroups = viewModel::selectBestInVisibleVisualGroups,
                    onFilterChange = viewModel::setManualFilter,
                    onSortChange = viewModel::setManualSort,
                    onMoveToReference = requestMoveToReference
                )
                uiState.reviewComplete -> ReviewSummary(
                    acceptedCount = uiState.acceptedCount,
                    skippedCount = uiState.skippedCount,
                    isMoving = uiState.isMoving,
                    onMoveAccepted = moveSelectedImages,
                    onCancel = { viewModel.cancelReview() }
                )
                uiState.isReviewing -> ReviewCard(
                    uiState = uiState,
                    onAccept = { viewModel.acceptCurrent() },
                    onSkip = { viewModel.skipCurrent() },
                    onFinishEarly = { viewModel.finishReviewNow() }
                )
                else -> SuggestionsList(
                    uiState = uiState,
                    onThresholdChange = { viewModel.setThreshold(it) },
                    onStartReview = { viewModel.startReview() }
                )
            }
        }
    }
}

@Composable
private fun SuggestionsList(
    uiState: ResultsUiState,
    onThresholdChange: (Float) -> Unit,
    onStartReview: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxSize()
    ) {
        ThresholdSlider(
            value = uiState.threshold,
            onValueChange = onThresholdChange,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
        )

        if (uiState.filteredSuggestions.isEmpty()) {
            EmptyState(
                title = "No Matches",
                message = "No images match the current threshold. Try lowering it."
            )
        } else {
            if (uiState.isDebugTopFallback) {
                Text(
                    text = "DEBUG: showing Top ${uiState.filteredSuggestions.size} matches below threshold",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.tertiary,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                )
            } else {
                Text(
                    text = "${uiState.filteredSuggestions.size} images found above threshold",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                )
            }

            Spacer(modifier = Modifier.weight(1f))

            Button(
                onClick = onStartReview,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                Text("Start Review (one by one)")
            }
        }
    }
}

@Composable
private fun ManualSelectionContent(
    uiState: ResultsUiState,
    onToggleSelection: (Long) -> Unit,
    onToggleSectionSelection: (Set<Long>) -> Unit,
    onSelectAll: () -> Unit,
    onClearSelection: () -> Unit,
    onSelectBestInDuplicateGroups: () -> Unit,
    onSelectBestInVisualGroups: () -> Unit,
    onFilterChange: (ManualReviewFilter) -> Unit,
    onSortChange: (ManualReviewSort) -> Unit,
    onMoveToReference: (Set<Long>) -> Unit
) {
    var moveConfirmationIds by remember { mutableStateOf<Set<Long>>(emptySet()) }
    var showReviewTools by rememberSaveable { mutableStateOf(false) }

    if (moveConfirmationIds.isNotEmpty()) {
        AlertDialog(
            onDismissRequest = { moveConfirmationIds = emptySet() },
            title = { Text("Move Images") },
            text = {
                Text(
                    "Move ${moveConfirmationIds.size} image(s) to A? Files will be removed from folder B."
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val imageIds = moveConfirmationIds
                        moveConfirmationIds = emptySet()
                        onMoveToReference(imageIds)
                    }
                ) {
                    Text("Move")
                }
            },
            dismissButton = {
                TextButton(onClick = { moveConfirmationIds = emptySet() }) {
                    Text("Cancel")
                }
            }
        )
    }

    if (uiState.allSuggestions.isEmpty()) {
        EmptyState(
            title = "No Images Found",
            message = "No images from folder B are available for manual selection."
        )
        return
    }

    Column(
        modifier = Modifier.fillMaxSize()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = "${uiState.visibleSuggestionCount}/${uiState.allSuggestions.size} visible | ${uiState.selectedCount} selected",
                style = MaterialTheme.typography.titleSmall
            )
            Text(
                text = "${uiState.manualVisibleVisualGroupCount} visual group(s) | ${uiState.manualVisibleDuplicateGroupCount} duplicate set(s) | ${uiState.manualVisibleBatchCount} batch group(s)",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            if (uiState.isComputingManualVisualGroups) {
                Text(
                    text = "Analyzing visual similarity offline...",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedButton(
                    onClick = { showReviewTools = !showReviewTools },
                    modifier = Modifier.weight(1f)
                ) {
                    Text(if (showReviewTools) "Hide Tools" else "Show Tools")
                }

                OutlinedButton(
                    onClick = {
                        if (uiState.manualVisibleDuplicateGroupCount > 0) {
                            onSelectBestInDuplicateGroups()
                        } else {
                            onSelectBestInVisualGroups()
                        }
                    },
                    enabled = uiState.manualVisibleDuplicateGroupCount > 0 ||
                        uiState.manualVisibleVisualGroupCount > 0,
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        when {
                            uiState.manualVisibleDuplicateGroupCount > 0 -> "Duplicate Picks"
                            else -> "Visual Picks"
                        }
                    )
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = onSelectAll,
                    enabled = uiState.selectedCount < uiState.filteredSuggestions.size
                ) {
                    Text("Select All")
                }

                OutlinedButton(
                    onClick = onClearSelection,
                    enabled = uiState.selectedCount > 0
                ) {
                    Text("Clear")
                }
            }

            if (showReviewTools) {
                val availableFilters = buildList {
                    add(ManualReviewFilter.ALL)
                    if (uiState.manualVisualGroupCount > 0) add(ManualReviewFilter.VISUAL_GROUPS)
                    if (uiState.manualLargeFileCount > 0) add(ManualReviewFilter.LARGE_FILES)
                    if (uiState.manualDuplicateGroupCount > 0) add(ManualReviewFilter.DUPLICATES)
                    if (uiState.manualFilter !in this) add(uiState.manualFilter)
                }
                val availableSorts = buildList {
                    add(ManualReviewSort.NEWEST)
                    add(ManualReviewSort.NAME)
                    add(ManualReviewSort.LARGEST)
                    add(ManualReviewSort.BATCHES)
                    if (uiState.manualVisualGroupCount > 0) add(ManualReviewSort.VISUAL_GROUPS)
                    if (uiState.manualDuplicateGroupCount > 0) add(ManualReviewSort.DUPLICATES)
                    if (uiState.manualSort !in this) add(uiState.manualSort)
                }

                ManualChipRow(
                    title = null,
                    options = availableFilters.map { filter ->
                        Triple(filter.label, uiState.manualFilter == filter, { onFilterChange(filter) })
                    }
                )

                ManualChipRow(
                    title = null,
                    options = availableSorts.map { sort ->
                        Triple(sort.label, uiState.manualSort == sort, { onSortChange(sort) })
                    }
                )

            }
        }

        if (uiState.manualGridEntries.isEmpty()) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                contentAlignment = Alignment.Center
            ) {
                EmptyState(
                    title = "No Results",
                    message = "Try a different search or filter to keep reviewing."
                )
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 140.dp),
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(
                    items = uiState.manualGridEntries,
                    key = { entry ->
                        when (entry) {
                            is ManualReviewGridEntry.Header -> "header-${entry.section.key}"
                            is ManualReviewGridEntry.ImageItem -> entry.suggestion.image.id
                        }
                    },
                    span = { entry ->
                        when (entry) {
                            is ManualReviewGridEntry.Header -> GridItemSpan(maxLineSpan)
                            is ManualReviewGridEntry.ImageItem -> GridItemSpan(1)
                        }
                    }
                ) { entry ->
                    when (entry) {
                        is ManualReviewGridEntry.Header -> ManualSectionHeader(
                            section = entry.section,
                            selectedIds = uiState.selectedIds,
                            onToggleSectionSelection = onToggleSectionSelection,
                            onMoveSectionToReference = {
                                moveConfirmationIds = entry.section.suggestions
                                    .mapTo(linkedSetOf()) { it.image.id }
                            }
                        )
                        is ManualReviewGridEntry.ImageItem -> ManualSuggestionGridItem(
                            suggestion = entry.suggestion,
                            isSelected = entry.suggestion.image.id in uiState.selectedIds,
                            onClick = { onToggleSelection(entry.suggestion.image.id) }
                        )
                    }
                }
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Button(
                onClick = {
                    moveConfirmationIds = uiState.moveCandidateIds
                },
                enabled = uiState.moveCandidateCount > 0 && !uiState.isMoving,
                modifier = Modifier.weight(1f)
            ) {
                if (uiState.isMoving) {
                    CircularProgressIndicator(
                        modifier = Modifier
                            .size(20.dp)
                            .padding(end = 8.dp),
                        color = MaterialTheme.colorScheme.onPrimary,
                        strokeWidth = 2.dp
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                }
                Text("Move to A")
            }
        }
    }
}

@Composable
private fun ManualChipRow(
    title: String?,
    options: List<Triple<String, Boolean, () -> Unit>>
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        title?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            options.forEach { (label, selected, onClick) ->
                FilterChip(
                    selected = selected,
                    onClick = onClick,
                    label = { Text(label) }
                )
            }
        }
    }
}

@Composable
private fun ManualSectionHeader(
    section: ManualReviewSection,
    selectedIds: Set<Long>,
    onToggleSectionSelection: (Set<Long>) -> Unit,
    onMoveSectionToReference: () -> Unit
) {
    val sectionIds = section.suggestions.mapTo(linkedSetOf()) { it.image.id }
    val allSelected = sectionIds.isNotEmpty() && sectionIds.all { it in selectedIds }
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(
                    text = section.title,
                    style = MaterialTheme.typography.titleSmall
                )
                Text(
                    text = section.subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = { onToggleSectionSelection(sectionIds) },
                    modifier = Modifier.weight(1f)
                ) {
                    Text(if (allSelected) "Clear Section" else "Select Section")
                }

                Button(
                    onClick = onMoveSectionToReference,
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Move to A")
                }
            }
        }
    }
}

@Composable
private fun ManualSuggestionGridItem(
    suggestion: SuggestionItem,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val containerColor = if (isSelected) {
        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
    } else {
        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
    }
    val borderColor = if (isSelected) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.outlineVariant
    }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        color = containerColor,
        tonalElevation = if (isSelected) 2.dp else 0.dp
    ) {
        Column(
            modifier = Modifier.padding(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f)
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.surface)
                    .border(
                        width = if (isSelected) 2.dp else 1.dp,
                        color = borderColor,
                        shape = RoundedCornerShape(12.dp)
                    )
            ) {
                AsyncImage(
                    model = suggestion.image.uri,
                    contentDescription = suggestion.image.displayName,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(8.dp)
                )

                if (isSelected) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(8.dp)
                            .size(24.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primary),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }

            Text(
                text = suggestion.image.displayName,
                style = MaterialTheme.typography.bodySmall,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
private fun ReviewCard(
    uiState: ResultsUiState,
    onAccept: () -> Unit,
    onSkip: () -> Unit,
    onFinishEarly: () -> Unit
) {
    val suggestion = uiState.currentSuggestion ?: return

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            Text(
                text = "Accepted: ${uiState.acceptedCount}",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary
            )
            Text(
                text = "Skipped: ${uiState.skippedCount}",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        AsyncImage(
            model = suggestion.image.uri,
            contentDescription = suggestion.image.displayName,
            contentScale = ContentScale.Fit,
            modifier = Modifier
                .fillMaxWidth()
                .height(300.dp)
                .clip(RoundedCornerShape(12.dp))
        )

        Spacer(modifier = Modifier.height(12.dp))

        Text(
            text = suggestion.image.displayName,
            style = MaterialTheme.typography.titleMedium,
            textAlign = TextAlign.Center
        )

        Text(
            text = "Score: %.1f%%".format(suggestion.score * 100),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.primary
        )

        Spacer(modifier = Modifier.height(16.dp))

        if (suggestion.topSimilarFromA.isNotEmpty()) {
            Text(
                text = "Similar in reference folder:",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(8.dp))
            SimilarImageRow(matches = suggestion.topSimilarFromA)
        }

        Spacer(modifier = Modifier.height(24.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            OutlinedButton(
                onClick = onSkip,
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = MaterialTheme.colorScheme.error
                )
            ) {
                Icon(Icons.Default.Close, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Skip")
            }

            Button(
                onClick = onAccept,
                modifier = Modifier.weight(1f)
            ) {
                Icon(Icons.Default.Check, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Move")
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        OutlinedButton(
            onClick = onFinishEarly,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Stop review and continue to move")
        }
    }
}

@Composable
private fun ReviewSummary(
    acceptedCount: Int,
    skippedCount: Int,
    isMoving: Boolean,
    onMoveAccepted: () -> Unit,
    onCancel: () -> Unit
) {
    var showMoveConfirmation by remember { mutableStateOf(false) }

    if (showMoveConfirmation) {
        AlertDialog(
            onDismissRequest = { showMoveConfirmation = false },
            title = { Text("Move Images") },
            text = { Text("Move $acceptedCount image(s) to the reference folder? Files will be removed from the unsorted folder.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showMoveConfirmation = false
                        onMoveAccepted()
                    }
                ) {
                    Text("Move")
                }
            },
            dismissButton = {
                TextButton(onClick = { showMoveConfirmation = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "Review Complete",
            style = MaterialTheme.typography.headlineSmall
        )

        Spacer(modifier = Modifier.height(24.dp))

        Text(
            text = "$acceptedCount accepted",
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.primary
        )
        Text(
            text = "$skippedCount skipped",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(32.dp))

        if (acceptedCount > 0) {
            Button(
                onClick = { showMoveConfirmation = true },
                enabled = !isMoving,
                modifier = Modifier.fillMaxWidth()
            ) {
                if (isMoving) {
                    CircularProgressIndicator(
                        modifier = Modifier
                            .size(20.dp)
                            .padding(end = 8.dp),
                        color = MaterialTheme.colorScheme.onPrimary,
                        strokeWidth = 2.dp
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                }
                Text("Move $acceptedCount image(s) to Reference Folder")
            }

            Spacer(modifier = Modifier.height(12.dp))
        }

        OutlinedButton(
            onClick = onCancel,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(if (acceptedCount > 0) "Cancel" else "Go Back")
        }
    }
}


