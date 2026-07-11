package com.smartfolder.presentation.components

import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.smartfolder.R
import com.smartfolder.domain.model.ModelChoice

@Composable
fun modelChoiceLabel(choice: ModelChoice): String = when (choice) {
    ModelChoice.SEMANTIC -> stringResource(R.string.model_semantic)
    ModelChoice.ANIME -> stringResource(R.string.model_anime)
    ModelChoice.FAST -> stringResource(R.string.model_fast)
    ModelChoice.PRECISE -> stringResource(R.string.model_precise)
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ModelSelector(
    selected: ModelChoice,
    onSelected: (ModelChoice) -> Unit,
    modifier: Modifier = Modifier,
    availableModels: List<ModelChoice> = ModelChoice.entries
) {
    FlowRow(modifier = modifier) {
        availableModels.forEach { choice ->
            FilterChip(
                selected = selected == choice,
                onClick = { onSelected(choice) },
                label = { Text(modelChoiceLabel(choice)) },
                modifier = Modifier.padding(end = 8.dp, bottom = 8.dp)
            )
        }
    }
}
