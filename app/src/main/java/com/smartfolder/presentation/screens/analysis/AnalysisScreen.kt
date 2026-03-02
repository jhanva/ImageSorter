package com.smartfolder.presentation.screens.analysis

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.smartfolder.domain.model.AnalysisPhase
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

    LaunchedEffect(Unit) {
        viewModel.startAnalysis()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Analysis") },
                navigationIcon = {
                    IconButton(onClick = {
                        viewModel.cancelAnalysis()
                        onNavigateBack()
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
                    Text("Retry")
                }
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedButton(onClick = onNavigateBack) {
                    Text("Go Back")
                }
            } ?: run {
                ProgressIndicator(
                    phase = uiState.progress.phase.name.replace('_', ' '),
                    current = uiState.progress.current,
                    total = uiState.progress.total,
                    currentFileName = uiState.progress.currentFileName
                )

                Spacer(modifier = Modifier.height(24.dp))

                if (uiState.isAnalyzing) {
                    OutlinedButton(
                        onClick = {
                            viewModel.cancelAnalysis()
                            onNavigateBack()
                        }
                    ) {
                        Text("Cancel")
                    }
                }

                if (uiState.progress.phase == AnalysisPhase.COMPLETE) {
                    Text("Found ${uiState.suggestions.size} suggestions")
                    Spacer(modifier = Modifier.height(16.dp))
                    Button(
                        onClick = onNavigateToResults,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("View Results")
                    }
                }
            }
        }
    }
}
