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
import com.smartfolder.domain.model.ExecutionProfile

@Composable
fun executionProfileLabel(profile: ExecutionProfile): String = when (profile) {
    ExecutionProfile.BATTERY -> stringResource(R.string.profile_battery)
    ExecutionProfile.BALANCED -> stringResource(R.string.profile_balanced)
    ExecutionProfile.PERFORMANCE -> stringResource(R.string.profile_performance)
}

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
                label = { Text(executionProfileLabel(profile)) },
                modifier = Modifier.padding(end = 8.dp, bottom = 8.dp)
            )
        }
    }
}
