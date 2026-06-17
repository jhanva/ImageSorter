package com.smartfolder.presentation.screens.results

import android.app.Activity
import android.os.Build
import android.provider.MediaStore
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Route
import androidx.compose.material3.AlertDialog
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
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
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
import com.smartfolder.presentation.components.SimilarImageRow
import com.smartfolder.presentation.components.ThresholdSlider
import com.smartfolder.presentation.visual.ResultsVisuals

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ResultsScreen(
    viewModel: ResultsViewModel,
    onNavigateBack: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    var destinationPickerImageId by remember { mutableLongStateOf(0L) }
    val overview = ResultsVisuals.buildOverview(
        filteredSuggestions = uiState.filteredSuggestions,
        destinationGroupCount = uiState.destinationSections.size,
        selectedCount = uiState.selectedCount
    )

    val writeRequestLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            viewModel.moveSelected()
        } else {
            viewModel.setError("Write permission denied. Could not move selected images.")
        }
    }

    fun requestSelectedMove() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val uris = viewModel.getSelectedImageUris()
            if (uris.isEmpty()) {
                viewModel.moveSelected()
                return
            }
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
        } else {
            viewModel.moveSelected()
        }
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

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Results")
                        Text(
                            text = "${uiState.filteredSuggestions.size} visible suggestions",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
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
                        androidx.compose.foundation.layout.Spacer(modifier = Modifier.size(8.dp))
                        Text(overview.actionLabel)
                    }
                }
            }
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            verticalArrangement = Arrangement.spacedBy(14.dp)
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
                        title = overview.title,
                        summary = overview.summary,
                        threshold = uiState.threshold,
                        onThresholdChange = viewModel::setThreshold,
                        selectedCount = uiState.selectedCount
                    )
                }
            }

            if (uiState.allSuggestions.isEmpty()) {
                item {
                    EmptyState(
                        title = "No suggestions yet",
                        message = "Run an analysis first to build local destination matches for your source images.",
                        illustrationRes = R.drawable.illus_no_photos
                    )
                }
            } else if (uiState.filteredSuggestions.isEmpty()) {
                item {
                    EmptyState(
                        title = "Nothing above the current threshold",
                        message = "Lower the threshold to review weaker matches or keep it high to move only the safest groups.",
                        illustrationRes = R.drawable.illus_all_clean
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
                        onToggleSelection = viewModel::toggleSelection,
                        onChangeDestination = { imageId -> destinationPickerImageId = imageId }
                    )
                }
            }
        }
    }
}

@Composable
private fun OverviewCard(
    title: String,
    summary: String,
    threshold: Float,
    onThresholdChange: (Float) -> Unit,
    selectedCount: Int
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(26.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .background(
                            color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.12f),
                            shape = CircleShape
                        )
                        .padding(12.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Route,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                }
                androidx.compose.foundation.layout.Spacer(modifier = Modifier.size(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.headlineSmall,
                        color = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                    Text(
                        text = summary,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.88f)
                    )
                }
                if (selectedCount > 0) {
                    Text(
                        text = "$selectedCount selected",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                }
            }
            ThresholdSlider(
                value = threshold,
                onValueChange = onThresholdChange
            )
        }
    }
}

@Composable
private fun DestinationSection(
    section: DestinationSuggestionSection,
    destinationFolders: List<Folder>,
    selectedIds: Set<Long>,
    destinationOverrides: Map<Long, Long>,
    onToggleSelection: (Long) -> Unit,
    onChangeDestination: (Long) -> Unit
) {
    val isManualRouting = section.destination.id == 0L
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isManualRouting) {
                MaterialTheme.colorScheme.tertiaryContainer
            } else {
                MaterialTheme.colorScheme.surface
            }
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = if (isManualRouting) Icons.Default.AutoAwesome else Icons.AutoMirrored.Filled.DriveFileMove,
                    contentDescription = null,
                    tint = if (isManualRouting) {
                        MaterialTheme.colorScheme.onTertiaryContainer
                    } else {
                        MaterialTheme.colorScheme.primary
                    }
                )
                androidx.compose.foundation.layout.Spacer(modifier = Modifier.size(10.dp))
                Column {
                    Text(
                        text = section.destination.displayName,
                        style = MaterialTheme.typography.titleLarge
                    )
                    Text(
                        text = "${section.suggestions.size} images in this review group",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            section.suggestions.forEach { suggestion ->
                SuggestionDestinationCard(
                    suggestion = suggestion,
                    assignedDestinationName = resolveDestinationName(
                        suggestion = suggestion,
                        destinationFolders = destinationFolders,
                        destinationOverrides = destinationOverrides
                    ),
                    isSelected = suggestion.image.id in selectedIds,
                    onToggleSelection = { onToggleSelection(suggestion.image.id) },
                    onChangeDestination = { onChangeDestination(suggestion.image.id) }
                )
            }
        }
    }
}

@Composable
private fun SuggestionDestinationCard(
    suggestion: SuggestionItem,
    assignedDestinationName: String,
    isSelected: Boolean,
    onToggleSelection: () -> Unit,
    onChangeDestination: () -> Unit
) {
    val isUnassigned = suggestion.suggestedDestinationId == 0L
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f)
        )
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Checkbox(
                checked = isSelected,
                onCheckedChange = { onToggleSelection() }
            )
            AsyncImage(
                model = suggestion.image.uri,
                contentDescription = suggestion.image.displayName,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(96.dp)
                    .clip(RoundedCornerShape(16.dp))
            )
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = suggestion.image.displayName,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = assignedDestinationName,
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (isUnassigned) {
                        MaterialTheme.colorScheme.tertiary
                    } else {
                        MaterialTheme.colorScheme.primary
                    },
                    fontWeight = FontWeight.SemiBold
                )
                ScoreRow(
                    score = suggestion.score,
                    margin = suggestion.confidenceMargin
                )
                if (suggestion.topSimilarImages.isNotEmpty()) {
                    SimilarImageRow(
                        matches = suggestion.topSimilarImages,
                        modifier = Modifier.horizontalScroll(rememberScrollState())
                    )
                }
                OutlinedButton(onClick = onChangeDestination) {
                    Text(if (isUnassigned) "Choose destination" else "Change destination")
                }
            }
        }
    }
}

@Composable
private fun ScoreRow(
    score: Float,
    margin: Float
) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        ScoreChip(
            label = "Match",
            value = "${(score * 100).toInt()}%"
        )
        ScoreChip(
            label = "Margin",
            value = "${(margin * 100).toInt()}%"
        )
    }
}

@Composable
private fun ScoreChip(
    label: String,
    value: String
) {
    Row(
        modifier = Modifier
            .background(
                color = MaterialTheme.colorScheme.primaryContainer,
                shape = CircleShape
            )
            .padding(horizontal = 10.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onPrimaryContainer
        )
        Text(
            text = value,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onPrimaryContainer
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
        title = { Text("Choose destination") },
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
                Text("Cancel")
            }
        }
    )
}

private fun resolveDestinationName(
    suggestion: SuggestionItem,
    destinationFolders: List<Folder>,
    destinationOverrides: Map<Long, Long>
): String {
    val destinationId = destinationOverrides[suggestion.image.id] ?: suggestion.suggestedDestinationId
    return when {
        destinationId == 0L -> "Needs manual routing"
        else -> destinationFolders.firstOrNull { it.id == destinationId }?.displayName
            ?: "Missing destination $destinationId"
    }
}
