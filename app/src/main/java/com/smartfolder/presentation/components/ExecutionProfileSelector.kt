package com.smartfolder.presentation.components

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.smartfolder.domain.model.ExecutionProfile

@Composable
fun ExecutionProfileSelector(
    selected: ExecutionProfile,
    onSelected: (ExecutionProfile) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(modifier = modifier) {
        ExecutionProfile.entries.forEach { profile ->
            FilterChip(
                selected = selected == profile,
                onClick = { onSelected(profile) },
                label = { Text(profile.displayName) },
                modifier = Modifier.padding(end = 8.dp)
            )
        }
    }
}
