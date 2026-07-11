package com.smartfolder.presentation.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.smartfolder.R

@Composable
fun ProgressIndicator(
    phaseLabel: String,
    current: Int,
    total: Int,
    currentFileName: String,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                BrandLottie(
                    resId = R.raw.scan_loader,
                    modifier = Modifier.size(56.dp)
                )
                Spacer(modifier = Modifier.size(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = phaseLabel,
                        style = MaterialTheme.typography.titleMedium
                    )
                    Text(
                        text = if (total > 0) {
                            stringResource(R.string.analysis_progress_count, current, total)
                        } else {
                            stringResource(R.string.analysis_progress_preparing)
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (total > 0) {
                    Text(
                        text = "${((current.toFloat() / total.toFloat()) * 100f).toInt()}%",
                        style = MaterialTheme.typography.labelLarge,
                        modifier = Modifier
                            .background(
                                color = MaterialTheme.colorScheme.primaryContainer,
                                shape = CircleShape
                            )
                            .padding(horizontal = 12.dp, vertical = 8.dp)
                    )
                }
            }
            LinearProgressIndicator(
                progress = { if (total > 0) current.toFloat() / total.toFloat() else 0f },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 16.dp),
                trackColor = MaterialTheme.colorScheme.surfaceVariant
            )
            if (currentFileName.isNotEmpty()) {
                Text(
                    text = currentFileName,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 12.dp),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}
