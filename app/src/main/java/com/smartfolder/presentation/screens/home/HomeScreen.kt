package com.smartfolder.presentation.screens.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.smartfolder.domain.model.IndexingPhase
import com.smartfolder.presentation.components.ErrorBanner
import com.smartfolder.presentation.components.FolderCard
import com.smartfolder.presentation.components.ModelSelector
import com.smartfolder.presentation.components.ProgressIndicator

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    viewModel: HomeViewModel,
    onSelectReferenceFolder: () -> Unit,
    onSelectUnsortedFolder: () -> Unit,
    onNavigateToAnalysis: () -> Unit,
    onNavigateToSettings: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("SmartFolder") },
                actions = {
                    OutlinedButton(
                        onClick = onNavigateToSettings,
                        modifier = Modifier.padding(end = 8.dp)
                    ) {
                        Text("Settings")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            uiState.error?.let { error ->
                ErrorBanner(message = error)
            }

            // Model selection
            Text(
                text = "Model",
                style = MaterialTheme.typography.titleMedium
            )
            ModelSelector(
                selected = uiState.modelChoice,
                onSelected = { viewModel.setModelChoice(it) }
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Reference folder (A)
            Text(
                text = "Reference Folder (A)",
                style = MaterialTheme.typography.titleMedium
            )
            if (uiState.referenceFolder != null) {
                FolderCard(folder = uiState.referenceFolder!!)
                if (uiState.isIndexingRef) {
                    ProgressIndicator(
                        phase = uiState.refIndexingProgress.phase.name,
                        current = uiState.refIndexingProgress.current,
                        total = uiState.refIndexingProgress.total,
                        currentFileName = uiState.refIndexingProgress.currentFileName
                    )
                } else {
                    Button(
                        onClick = { viewModel.indexReferenceFolder() },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Index Reference Folder")
                    }
                }
            } else {
                OutlinedButton(
                    onClick = onSelectReferenceFolder,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Select Reference Folder")
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Unsorted folder (B)
            Text(
                text = "Unsorted Folder (B)",
                style = MaterialTheme.typography.titleMedium
            )
            if (uiState.unsortedFolder != null) {
                FolderCard(folder = uiState.unsortedFolder!!)
                if (uiState.isIndexingUnsorted) {
                    ProgressIndicator(
                        phase = uiState.unsortedIndexingProgress.phase.name,
                        current = uiState.unsortedIndexingProgress.current,
                        total = uiState.unsortedIndexingProgress.total,
                        currentFileName = uiState.unsortedIndexingProgress.currentFileName
                    )
                } else {
                    Button(
                        onClick = { viewModel.indexUnsortedFolder() },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Index Unsorted Folder")
                    }
                }
            } else {
                OutlinedButton(
                    onClick = onSelectUnsortedFolder,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Select Unsorted Folder")
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Analyze button
            Button(
                onClick = onNavigateToAnalysis,
                enabled = uiState.canAnalyze && !uiState.isIndexingRef && !uiState.isIndexingUnsorted,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Analyze Images")
            }
        }
    }
}
