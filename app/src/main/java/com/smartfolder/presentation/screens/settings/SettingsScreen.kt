package com.smartfolder.presentation.screens.settings

import android.os.Build
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Snackbar
import androidx.compose.material3.Switch
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.smartfolder.R
import com.smartfolder.domain.model.ModelChoice
import com.smartfolder.presentation.components.ExecutionProfileSelector
import com.smartfolder.presentation.components.ModelSelector
import com.smartfolder.presentation.components.ThresholdSlider

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel,
    onNavigateBack: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = androidx.compose.ui.platform.LocalContext.current
    val availableModels = remember { ModelChoice.availableIn(context) }
    var showClearConfirmation by remember { mutableStateOf(false) }

    if (showClearConfirmation) {
        AlertDialog(
            onDismissRequest = { showClearConfirmation = false },
            title = { Text(stringResource(R.string.settings_clear_cache_title)) },
            text = { Text(stringResource(R.string.settings_clear_cache_message)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        showClearConfirmation = false
                        viewModel.clearCache()
                    }
                ) {
                    Text(
                        text = stringResource(R.string.settings_clear_confirm),
                        color = MaterialTheme.colorScheme.error
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearConfirmation = false }) {
                    Text(stringResource(R.string.action_cancel))
                }
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings_title)) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.action_back)
                        )
                    }
                }
            )
        },
        snackbarHost = {
            uiState.message?.let { message ->
                Snackbar(
                    action = {
                        TextButton(onClick = { viewModel.dismissMessage() }) {
                            Text(stringResource(R.string.action_dismiss))
                        }
                    }
                ) {
                    Text(
                        text = when (message) {
                            SettingsMessage.CACHE_CLEARED -> stringResource(R.string.settings_cache_cleared)
                            SettingsMessage.CACHE_FAILED -> stringResource(R.string.settings_cache_failed)
                        }
                    )
                }
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            Text(
                text = stringResource(R.string.settings_threshold_title),
                style = MaterialTheme.typography.titleMedium
            )
            ThresholdSlider(
                value = uiState.threshold,
                onValueChange = { viewModel.setThreshold(it) }
            )

            Text(
                text = stringResource(R.string.settings_model_title),
                style = MaterialTheme.typography.titleMedium
            )
            Text(
                text = stringResource(R.string.settings_model_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            ModelSelector(
                selected = uiState.modelChoice,
                onSelected = { viewModel.setModelChoice(it) },
                availableModels = availableModels
            )

            Text(
                text = stringResource(R.string.settings_profile_title),
                style = MaterialTheme.typography.titleMedium
            )
            ExecutionProfileSelector(
                selected = uiState.executionProfile,
                onSelected = { viewModel.setExecutionProfile(it) }
            )

            Text(
                text = stringResource(R.string.settings_appearance_title),
                style = MaterialTheme.typography.titleMedium
            )
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = stringResource(R.string.settings_dynamic_color),
                        style = MaterialTheme.typography.bodyLarge
                    )
                    Switch(
                        checked = uiState.dynamicColor,
                        onCheckedChange = { viewModel.setDynamicColor(it) }
                    )
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(R.string.settings_dark_mode),
                    style = MaterialTheme.typography.bodyLarge
                )
                Switch(
                    checked = uiState.darkMode,
                    onCheckedChange = { viewModel.setDarkMode(it) }
                )
            }

            Button(
                onClick = { showClearConfirmation = true },
                enabled = !uiState.isClearingCache,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.error
                )
            ) {
                if (uiState.isClearingCache) {
                    CircularProgressIndicator(
                        modifier = Modifier.padding(end = 8.dp),
                        color = MaterialTheme.colorScheme.onError
                    )
                }
                Text(stringResource(R.string.settings_clear_cache))
            }
        }
    }
}
