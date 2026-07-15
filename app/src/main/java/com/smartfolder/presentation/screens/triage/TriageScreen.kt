package com.smartfolder.presentation.screens.triage

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
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
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.smartfolder.R
import com.smartfolder.domain.model.ImageInfo
import com.smartfolder.presentation.components.EmptyState
import com.smartfolder.presentation.components.ErrorBanner
import com.smartfolder.presentation.components.ImagePreviewDialog

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TriageScreen(
    viewModel: TriageViewModel,
    onNavigateBack: () -> Unit,
    onOpenTrash: () -> Unit = {}
) {
    val uiState by viewModel.uiState.collectAsState()
    val haptics = LocalHapticFeedback.current
    val view = LocalView.current
    var previewImage by remember { mutableStateOf<ImageInfo?>(null) }

    // Keep the screen on during a triage session.
    androidx.compose.runtime.DisposableEffect(Unit) {
        view.keepScreenOn = true
        onDispose { view.keepScreenOn = false }
    }

    // Preload the next images so each decision shows the next photo instantly.
    val context = LocalContext.current
    androidx.compose.runtime.LaunchedEffect(uiState.currentIndex, uiState.queue) {
        val next = uiState.queue.drop(uiState.currentIndex + 1).take(3)
        next.forEach { image ->
            val request = ImageRequest.Builder(context)
                .data(image.uri)
                .build()
            coil.Coil.imageLoader(context).enqueue(request)
        }
    }

    previewImage?.let { image ->
        ImagePreviewDialog(
            uri = image.uri,
            displayName = image.displayName,
            onDismiss = { previewImage = null }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = uiState.sourceFolder?.displayName
                                ?: stringResource(R.string.triage_title)
                        )
                        if (!uiState.isLoading && !uiState.isComplete) {
                            Text(
                                text = stringResource(
                                    R.string.triage_progress,
                                    uiState.remainingCount,
                                    uiState.movedCount
                                ),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.action_back)
                        )
                    }
                },
                actions = {
                    IconButton(onClick = onOpenTrash) {
                        Icon(
                            imageVector = Icons.Filled.Delete,
                            contentDescription = stringResource(R.string.trash_title)
                        )
                    }
                    IconButton(
                        onClick = { viewModel.undoLast() },
                        enabled = uiState.canUndo && !uiState.isBusy
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.Undo,
                            contentDescription = stringResource(R.string.triage_undo)
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
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            uiState.error?.let { error ->
                ErrorBanner(message = error)
            }

            when {
                uiState.isLoading -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }
                uiState.isComplete && uiState.totalCount == 0 -> {
                    EmptyState(
                        title = stringResource(R.string.triage_empty_title),
                        message = stringResource(R.string.triage_empty_message)
                    )
                }
                uiState.isComplete -> {
                    TriageSummary(
                        uiState = uiState,
                        onDone = onNavigateBack
                    )
                }
                else -> {
                    val current = uiState.current
                    if (current != null) {
                        LinearProgressIndicator(
                            progress = {
                                if (uiState.totalCount == 0) 0f
                                else uiState.currentIndex.toFloat() / uiState.totalCount
                            },
                            modifier = Modifier.fillMaxWidth()
                        )

                        AsyncImage(
                            model = current.uri,
                            contentDescription = current.displayName,
                            contentScale = ContentScale.Fit,
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f)
                                .clickable { previewImage = current }
                        )

                        Text(
                            text = current.displayName,
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.fillMaxWidth(),
                            textAlign = TextAlign.Center
                        )

                        DestinationButtons(
                            uiState = uiState,
                            onMove = { destinationId ->
                                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                viewModel.moveTo(destinationId)
                            },
                            onSkip = { viewModel.skip() },
                            onDelete = {
                                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                viewModel.deleteCurrent()
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DestinationButtons(
    uiState: TriageUiState,
    onMove: (Long) -> Unit,
    onSkip: () -> Unit,
    onDelete: () -> Unit
) {
    // Density adapts to the folder count so up to 16 destinations stay
    // visible without scrolling; positions stay fixed for muscle memory.
    val count = uiState.destinations.size
    val columns = when {
        count <= 4 -> 2
        count <= 9 -> 3
        else -> 4
    }
    val buttonHeight = if (count <= 6) 52.dp else 48.dp
    val visibleRows = ((count + columns - 1) / columns).coerceIn(1, 4)
    val gridHeight = buttonHeight * visibleRows + 8.dp * (visibleRows - 1)

    Column(
        modifier = Modifier.padding(bottom = 12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        LazyVerticalGrid(
            columns = GridCells.Fixed(columns),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.heightIn(max = gridHeight)
        ) {
            items(uiState.destinations, key = { it.id }) { destination ->
                Button(
                    onClick = { onMove(destination.id) },
                    enabled = !uiState.isBusy,
                    contentPadding = PaddingValues(horizontal = 6.dp, vertical = 4.dp),
                    modifier = Modifier.heightIn(min = buttonHeight)
                ) {
                    Text(
                        text = destination.displayName,
                        style = if (columns == 4) {
                            MaterialTheme.typography.labelMedium
                        } else {
                            MaterialTheme.typography.labelLarge
                        },
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            OutlinedButton(
                onClick = onSkip,
                enabled = !uiState.isBusy,
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = 48.dp)
            ) {
                Text(
                    text = stringResource(R.string.triage_skip),
                    maxLines = 1
                )
            }
            OutlinedButton(
                onClick = onDelete,
                enabled = !uiState.isBusy,
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = MaterialTheme.colorScheme.error
                ),
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = 48.dp)
            ) {
                Text(
                    text = stringResource(R.string.triage_delete),
                    maxLines = 1
                )
            }
        }
    }
}

@Composable
private fun TriageSummary(
    uiState: TriageUiState,
    onDone: () -> Unit
) {
    val foldersById = uiState.destinations.associateBy { it.id }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = stringResource(R.string.triage_complete_title),
            style = MaterialTheme.typography.headlineSmall,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 24.dp)
        )
        Text(
            text = stringResource(
                R.string.triage_complete_message,
                uiState.movedCount,
                uiState.skippedCount
            ),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        if (uiState.deletedCount > 0) {
            Text(
                text = stringResource(R.string.triage_deleted_summary, uiState.deletedCount),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error,
                textAlign = TextAlign.Center
            )
        }

        uiState.movedByDestination.forEach { (destinationId, count) ->
            val name = foldersById[destinationId]?.displayName ?: return@forEach
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(text = name, style = MaterialTheme.typography.bodyMedium)
                Text(
                    text = stringResource(R.string.triage_summary_count, count),
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }

        TextButton(onClick = onDone) {
            Text(stringResource(R.string.triage_done))
        }
    }
}
