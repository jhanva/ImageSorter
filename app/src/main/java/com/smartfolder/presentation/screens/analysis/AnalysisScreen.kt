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
import androidx.compose.ui.res.stringResource
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
            title = { Text(stringResource(R.string.analysis_cancel_title)) },
            text = { Text(stringResource(R.string.analysis_cancel_message)) },
            confirmButton = {
                TextButton(onClick = {
                    showCancelConfirmation = false
                    viewModel.cancelAnalysis()
                    onNavigateBack()
                }) {
                    Text(stringResource(R.string.analysis_cancel_confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = { showCancelConfirmation = false }) {
                    Text(stringResource(R.string.analysis_cancel_continue))
                }
            }
        )
    }

    LaunchedEffect(Unit) {
        viewModel.startAnalysis()
    }

    val phaseLabel = when (uiState.progress.phase) {
        AnalysisPhase.IDLE -> stringResource(R.string.analysis_phase_preparing)
        AnalysisPhase.INDEXING_DESTINATIONS -> stringResource(R.string.analysis_phase_indexing_destinations)
        AnalysisPhase.INDEXING_SOURCES -> stringResource(R.string.analysis_phase_indexing_sources)
        AnalysisPhase.CENTROID -> stringResource(R.string.analysis_phase_centroid)
        AnalysisPhase.COMPARING -> stringResource(R.string.analysis_phase_comparing)
        AnalysisPhase.COMPLETE -> stringResource(R.string.analysis_phase_complete)
        AnalysisPhase.ERROR -> stringResource(R.string.analysis_phase_stopped)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(stringResource(R.string.analysis_title))
                        Text(
                            text = stringResource(R.string.analysis_subtitle),
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
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.action_back)
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
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            uiState.error?.let { error ->
                ErrorBanner(message = error)
                Spacer(modifier = Modifier.height(16.dp))
                Button(onClick = { viewModel.startAnalysis() }) {
                    Text(stringResource(R.string.action_retry))
                }
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedButton(onClick = onNavigateBack) {
                    Text(stringResource(R.string.analysis_go_back))
                }
            } ?: run {
                if (uiState.progress.phase != AnalysisPhase.COMPLETE) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(24.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer
                        )
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(20.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = phaseLabel,
                                style = MaterialTheme.typography.headlineSmall,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                            Text(
                                text = stringResource(R.string.analysis_subtitle),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.85f)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    ProgressIndicator(
                        phaseLabel = phaseLabel,
                        current = uiState.progress.current,
                        total = uiState.progress.total,
                        currentFileName = uiState.progress.currentFileName
                    )

                    Spacer(modifier = Modifier.height(24.dp))

                    if (uiState.isAnalyzing) {
                        OutlinedButton(
                            onClick = { showCancelConfirmation = true }
                        ) {
                            Text(stringResource(R.string.action_cancel))
                        }
                    }
                } else {
                    EmptyState(
                        title = stringResource(R.string.analysis_done_title),
                        message = stringResource(R.string.analysis_done_message, uiState.suggestions.size),
                        illustrationRes = R.drawable.illus_all_clean,
                        actionLabel = stringResource(R.string.analysis_open_results),
                        onAction = onNavigateToResults
                    )
                }
            }
        }
    }
}
