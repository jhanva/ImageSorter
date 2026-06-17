package com.smartfolder.presentation.screens.analysis

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.smartfolder.R
import com.smartfolder.domain.model.AnalysisPhase
import com.smartfolder.presentation.components.EmptyState
import com.smartfolder.presentation.components.ErrorBanner
import com.smartfolder.presentation.components.ProgressIndicator

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AnalysisScreen(
    viewModel: AnalysisViewModel,
    onNavigateBack: () -> Unit,
    onNavigateToResults: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    var showCancelConfirmation by remember { mutableStateOf(false) }

    if (showCancelConfirmation) {
        AlertDialog(
            onDismissRequest = { showCancelConfirmation = false },
            title = { Text("Cancel analysis") },
            text = { Text("Are you sure you want to cancel the current analysis? Progress will be lost.") },
            confirmButton = {
                TextButton(onClick = {
                    showCancelConfirmation = false
                    viewModel.cancelAnalysis()
                    onNavigateBack()
                }) {
                    Text("Cancel analysis")
                }
            },
            dismissButton = {
                TextButton(onClick = { showCancelConfirmation = false }) {
                    Text("Continue")
                }
            }
        )
    }

    LaunchedEffect(Unit) {
        viewModel.startAnalysis()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Analysis")
                        Text(
                            text = "Building destination suggestions locally",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = {
                        if (uiState.isAnalyzing) {
                            showCancelConfirmation = true
                        } else {
                            onNavigateBack()
                        }
                    }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            uiState.error?.let { error ->
                ErrorBanner(message = error)
                Spacer(modifier = Modifier.height(16.dp))
                Button(onClick = { viewModel.startAnalysis() }) {
                    Text("Retry analysis")
                }
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedButton(onClick = onNavigateBack) {
                    Text("Go back")
                }
            } ?: run {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(28.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer
                    )
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Text(
                            text = when (uiState.progress.phase) {
                                AnalysisPhase.IDLE -> "Preparing analysis"
                                AnalysisPhase.INDEXING_DESTINATIONS -> "Checking destination embeddings"
                                AnalysisPhase.INDEXING_SOURCES -> "Checking source embeddings"
                                AnalysisPhase.CENTROID -> "Preparing destination fingerprints"
                                AnalysisPhase.COMPARING -> "Comparing source images against your library"
                                AnalysisPhase.COMPLETE -> "Analysis complete"
                                AnalysisPhase.ERROR -> "Analysis stopped"
                            },
                            style = MaterialTheme.typography.headlineSmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                        Text(
                            text = "The app is matching source images against every indexed destination using local embeddings stored on the device.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.88f)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                ProgressIndicator(
                    phase = uiState.progress.phase.name,
                    current = uiState.progress.current,
                    total = uiState.progress.total,
                    currentFileName = uiState.progress.currentFileName
                )

                Spacer(modifier = Modifier.height(24.dp))

                if (uiState.isAnalyzing) {
                    OutlinedButton(
                        onClick = { showCancelConfirmation = true }
                    ) {
                        Text("Cancel")
                    }
                }

                if (uiState.progress.phase == AnalysisPhase.COMPLETE) {
                    Spacer(modifier = Modifier.height(20.dp))
                    EmptyState(
                        title = "Suggestions ready",
                        message = "ImageSorter produced ${uiState.suggestions.size} suggestions. Open Results to review and move images in batches.",
                        illustrationRes = R.drawable.illus_all_clean,
                        actionLabel = "Open results",
                        onAction = onNavigateToResults
                    )
                }
            }
        }
    }
}
