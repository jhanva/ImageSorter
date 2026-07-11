package com.smartfolder.presentation.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.smartfolder.R

@Composable
fun ThresholdSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = stringResource(R.string.results_threshold_label, (value * 100).toInt()),
            style = MaterialTheme.typography.bodyMedium
        )
        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = 0.30f..0.95f,
            steps = 65,
            modifier = Modifier.fillMaxWidth()
        )
    }
}
