package com.smartfolder.presentation.components

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.smartfolder.domain.model.ModelChoice

@Composable
fun ModelSelector(
    selected: ModelChoice,
    onSelected: (ModelChoice) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(modifier = modifier) {
        ModelChoice.entries.forEach { choice ->
            FilterChip(
                selected = selected == choice,
                onClick = { onSelected(choice) },
                label = { Text(choice.displayName) },
                modifier = Modifier.padding(end = 8.dp)
            )
        }
    }
}
