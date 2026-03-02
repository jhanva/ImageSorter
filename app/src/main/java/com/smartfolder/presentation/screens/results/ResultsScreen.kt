package com.smartfolder.presentation.screens.results

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Snackbar
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.smartfolder.presentation.components.EmptyState
import com.smartfolder.presentation.components.ErrorBanner
import com.smartfolder.presentation.components.SuggestionCard
import com.smartfolder.presentation.components.ThresholdSlider

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ResultsScreen(
    viewModel: ResultsViewModel,
    onNavigateBack: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Results (${uiState.filteredSuggestions.size})") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
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

            // Threshold slider
            ThresholdSlider(
                value = uiState.threshold,
                onValueChange = { viewModel.setThreshold(it) },
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
            )

            // Selection controls
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "${uiState.selectedIds.size} selected",
                    style = MaterialTheme.typography.bodyMedium
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = { viewModel.selectAll() }) {
                        Text("Select All")
                    }
                    TextButton(onClick = { viewModel.deselectAll() }) {
                        Text("Deselect")
                    }
                }
            }

            if (uiState.filteredSuggestions.isEmpty()) {
                EmptyState(
                    title = "No Matches",
                    message = "No images match the current threshold. Try lowering it."
                )
            } else {
                LazyColumn(
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(
                        items = uiState.filteredSuggestions,
                        key = { it.image.id }
                    ) { suggestion ->
                        SuggestionCard(
                            suggestion = suggestion,
                            isSelected = suggestion.image.id in uiState.selectedIds,
                            onSelectionChanged = { viewModel.toggleSelection(suggestion.image.id) }
                        )
                    }
                }
            }

            // Move button
            if (uiState.selectedIds.isNotEmpty()) {
                Button(
                    onClick = { viewModel.moveSelected() },
                    enabled = !uiState.isMoving,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                ) {
                    if (uiState.isMoving) {
                        CircularProgressIndicator(
                            modifier = Modifier.padding(end = 8.dp),
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                    }
                    Text("Move ${uiState.selectedIds.size} Images to Reference Folder")
                }
            }
        }
    }
}
