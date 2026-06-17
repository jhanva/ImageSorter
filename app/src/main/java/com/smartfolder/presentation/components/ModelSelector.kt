package com.smartfolder.presentation.components

import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.smartfolder.domain.model.ModelChoice

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ModelSelector(
    selected: ModelChoice,
    onSelected: (ModelChoice) -> Unit,
    modifier: Modifier = Modifier
) {
    FlowRow(modifier = modifier) {
        ModelChoice.entries.forEach { choice ->
            FilterChip(
                selected = selected == choice,
                onClick = { onSelected(choice) },
                label = { Text(choice.displayName) },
                modifier = Modifier.padding(end = 8.dp, bottom = 8.dp)
            )
        }
    }
}
