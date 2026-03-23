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
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
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
    val writeRequestLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            viewModel.moveAccepted()
        } else {
            viewModel.setError("Write permission denied. Could not move selected images.")
        }
    }
    val moveSelectedImages = {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val uris = viewModel.getAcceptedImageUris()
            if (uris.isEmpty()) {
                viewModel.moveAccepted()
            } else {
                try {
                    val writeRequest = MediaStore.createWriteRequest(
                        context.contentResolver,
                        uris
                    )
                    writeRequestLauncher.launch(
                        IntentSenderRequest.Builder(writeRequest.intentSender).build()
                    )
                } catch (e: Exception) {
                    viewModel.setError(
                        e.message ?: "Could not request write permission for selected images."
                    )
                }
            }
        } else {
            viewModel.moveAccepted()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    if (uiState.manualMode) {
                        Text("Select Images (${uiState.selectedCount}/${uiState.filteredSuggestions.size})")
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
                    onSelectAll = viewModel::selectAllFiltered,
                    onClearSelection = viewModel::clearSelection,
                    onMoveSelected = moveSelectedImages
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
    onSelectAll: () -> Unit,
    onClearSelection: () -> Unit,
    onMoveSelected: () -> Unit
) {
    var showMoveConfirmation by remember { mutableStateOf(false) }

    if (showMoveConfirmation) {
        AlertDialog(
            onDismissRequest = { showMoveConfirmation = false },
            title = { Text("Move Images") },
            text = {
                Text(
                    "Move ${uiState.moveCandidateCount} selected image(s) to the reference folder? Files will be removed from the unsorted folder."
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showMoveConfirmation = false
                        onMoveSelected()
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

    if (uiState.filteredSuggestions.isEmpty()) {
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
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "${uiState.filteredSuggestions.size} images loaded from folder B",
                style = MaterialTheme.typography.titleMedium
            )
            Text(
                text = "Tap a thumbnail to select it. ${uiState.selectedCount} image(s) selected.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedButton(
                    onClick = onSelectAll,
                    enabled = uiState.selectedCount < uiState.filteredSuggestions.size,
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Select All")
                }

                OutlinedButton(
                    onClick = onClearSelection,
                    enabled = uiState.selectedCount > 0,
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Clear")
                }
            }
        }

        LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = 140.dp),
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(
                items = uiState.filteredSuggestions,
                key = { it.image.id }
            ) { suggestion ->
                ManualSuggestionGridItem(
                    suggestion = suggestion,
                    isSelected = suggestion.image.id in uiState.selectedIds,
                    onClick = { onToggleSelection(suggestion.image.id) }
                )
            }
        }

        Button(
            onClick = { showMoveConfirmation = true },
            enabled = uiState.moveCandidateCount > 0 && !uiState.isMoving,
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
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
            Text("Move ${uiState.moveCandidateCount} Selected Image(s)")
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
        // Progress counters
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

        // Image preview
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

        // Image name
        Text(
            text = suggestion.image.displayName,
            style = MaterialTheme.typography.titleMedium,
            textAlign = TextAlign.Center
        )

        // Score
        Text(
            text = "Score: %.1f%%".format(suggestion.score * 100),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.primary
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Similar images from reference folder
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

        // Accept / Skip buttons
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
