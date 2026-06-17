package com.smartfolder.presentation.components

import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.smartfolder.domain.model.ExecutionProfile

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ExecutionProfileSelector(
    selected: ExecutionProfile,
    onSelected: (ExecutionProfile) -> Unit,
    modifier: Modifier = Modifier
) {
    FlowRow(modifier = modifier) {
        ExecutionProfile.entries.forEach { profile ->
            FilterChip(
                selected = selected == profile,
                onClick = { onSelected(profile) },
                label = { Text(profile.displayName) },
                modifier = Modifier.padding(end = 8.dp, bottom = 8.dp)
            )
        }
    }
}
