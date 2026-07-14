package com.smartfolder.presentation.screens.review

import android.app.Activity
import android.os.Build
import android.provider.MediaStore
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material3.Button
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.smartfolder.R
import com.smartfolder.presentation.components.EmptyState
import com.smartfolder.presentation.components.ErrorBanner
import com.smartfolder.presentation.components.SimilarImageRow

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReviewScreen(
    viewModel: ReviewViewModel,
    onNavigateBack: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    var pendingApply by remember { mutableStateOf(false) }

    val writePermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        if (pendingApply && result.resultCode == Activity.RESULT_OK) {
            viewModel.applyAcceptedMoves()
        }
        pendingApply = false
    }

    fun applyMovesWithPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val uris = viewModel.getAcceptedImageUris()
                .filter { it.authority == MediaStore.AUTHORITY }
            if (uris.isNotEmpty()) {
                runCatching {
                    val intent = MediaStore.createWriteRequest(context.contentResolver, uris)
                    pendingApply = true
                    writePermissionLauncher.launch(
                        androidx.activity.result.IntentSenderRequest.Builder(intent.intentSender).build()
                    )
                }.onFailure {
                    viewModel.applyAcceptedMoves()
                }
                return
            }
        }
        viewModel.applyAcceptedMoves()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(stringResource(R.string.review_title))
                        if (!uiState.isLoading && !uiState.isComplete) {
                            Text(
                                text = stringResource(
                                    R.string.review_progress,
                                    uiState.reviewedCount + 1,
                                    uiState.totalCount
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
                    IconButton(
                        onClick = { viewModel.undoLast() },
                        enabled = uiState.reviewedCount > 0
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.Undo,
                            contentDescription = stringResource(R.string.review_undo)
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
            verticalArrangement = Arrangement.spacedBy(12.dp)
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
                        title = stringResource(R.string.review_empty_title),
                        message = stringResource(R.string.review_empty_message)
                    )
                }
                uiState.isComplete -> {
                    ReviewSummary(
                        uiState = uiState,
                        onApplyMoves = { applyMovesWithPermission() },
                        onDone = onNavigateBack
                    )
                }
                else -> {
                    ReviewCurrentItem(
                        uiState = uiState,
                        onAccept = { viewModel.accept(it) },
                        onSkip = { viewModel.skip() }
                    )
                }
            }
        }
    }
}

@Composable
private fun ColumnScope.ReviewCurrentItem(
    uiState: ReviewUiState,
    onAccept: (Long) -> Unit,
    onSkip: () -> Unit
) {
    val item = uiState.current ?: return

    LinearProgressIndicator(
        progress = {
            if (uiState.totalCount == 0) 0f
            else uiState.reviewedCount.toFloat() / uiState.totalCount
        },
        modifier = Modifier.fillMaxWidth()
    )

    AsyncImage(
        model = item.image.uri,
        contentDescription = item.image.displayName,
        contentScale = ContentScale.Fit,
        modifier = Modifier
            .fillMaxWidth()
            .weight(1f)
    )

    Text(
        text = item.image.displayName,
        style = MaterialTheme.typography.titleSmall,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis
    )

    if (item.topSimilarImages.isNotEmpty()) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                text = stringResource(R.string.review_similar_label),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            SimilarImageRow(
                matches = item.topSimilarImages,
                modifier = Modifier.horizontalScroll(rememberScrollState())
            )
        }
    }

    Column(
        modifier = Modifier.padding(bottom = 16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        uiState.currentCandidates.take(3).forEach { candidate ->
            Button(
                onClick = { onAccept(candidate.folder.id) },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = stringResource(
                        R.string.review_accept_candidate,
                        candidate.folder.displayName,
                        (candidate.score * 100).toInt()
                    )
                )
            }
        }
        OutlinedButton(
            onClick = onSkip,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(stringResource(R.string.review_skip))
        }
    }
}

@Composable
private fun ReviewSummary(
    uiState: ReviewUiState,
    onApplyMoves: () -> Unit,
    onDone: () -> Unit
) {
    val foldersById = uiState.destinationFolders.associateBy { it.id }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = stringResource(R.string.review_complete_title),
            style = MaterialTheme.typography.headlineSmall,
            textAlign = TextAlign.Center
        )
        Text(
            text = stringResource(
                R.string.review_complete_message,
                uiState.acceptedCount,
                uiState.skippedCount
            ),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )

        uiState.acceptedByDestination.forEach { (destinationId, count) ->
            val name = foldersById[destinationId]?.displayName ?: return@forEach
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(text = name, style = MaterialTheme.typography.bodyMedium)
                Text(
                    text = stringResource(R.string.review_summary_count, count),
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }

        uiState.moveSummary?.let { summary ->
            Text(
                text = stringResource(
                    R.string.results_moved_summary_detailed,
                    summary.moved,
                    summary.copiedOnly,
                    summary.failed
                ),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary
            )
        }

        if (uiState.acceptedCount > 0 && uiState.moveSummary == null) {
            Button(
                onClick = onApplyMoves,
                enabled = !uiState.isMoving,
                modifier = Modifier.fillMaxWidth()
            ) {
                if (uiState.isMoving) {
                    CircularProgressIndicator(modifier = Modifier.padding(4.dp))
                } else {
                    Text(stringResource(R.string.review_apply_moves, uiState.acceptedCount))
                }
            }
        }

        TextButton(onClick = onDone) {
            Text(stringResource(R.string.review_done))
        }
    }
}
