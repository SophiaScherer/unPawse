package com.example.unpawse.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp

/** A single row in an [ActivityTimeline]. Icon + tint let each event read at a glance. */
data class TimelineEntry(
    val icon: ImageVector,
    val iconTint: Color,
    val iconBackground: Color,
    val title: String,
    val subtitle: String,
    val time: String,
)

/**
 * Vertical event feed (Recent Activity card). Each entry has a tinted leading icon; a thin
 * connector line joins consecutive icons, drawn behind them so the timeline reads as continuous.
 */
@Composable
fun ActivityTimeline(
    entries: List<TimelineEntry>,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        entries.forEachIndexed { index, entry ->
            Row(modifier = Modifier.fillMaxWidth()) {
                // Leading icon column with connector.
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(entry.iconBackground),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = entry.icon,
                            contentDescription = null,
                            tint = entry.iconTint,
                            modifier = Modifier.size(20.dp),
                        )
                    }
                    if (index < entries.lastIndex) {
                        Box(
                            modifier = Modifier
                                .width(1.dp)
                                .height(28.dp)
                                .background(MaterialTheme.colorScheme.outlineVariant),
                        )
                    }
                }
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.padding(bottom = if (index < entries.lastIndex) 12.dp else 0.dp)) {
                    Text(
                        text = entry.title,
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        text = entry.subtitle,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        text = entry.time,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.outline,
                    )
                }
            }
        }
    }
}
