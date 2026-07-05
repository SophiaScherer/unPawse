package com.example.unpawse.ui.stats

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.TrendingDown
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.Assessment
import androidx.compose.material.icons.filled.Celebration
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.MilitaryTech
import androidx.compose.material.icons.filled.Nightlight
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.unpawse.ui.components.DonutChart
import com.example.unpawse.ui.components.DonutSegment
import com.example.unpawse.ui.components.LineChart
import com.example.unpawse.ui.components.MiniBarChart
import com.example.unpawse.ui.components.PawCard
import com.example.unpawse.ui.components.ScreenHeader
import com.example.unpawse.ui.components.SectionLabel
import com.example.unpawse.ui.theme.Dimens
import com.example.unpawse.ui.theme.UnPawseTheme

@Composable
fun StatsScreen(
    state: StatsUiState,
    modifier: Modifier = Modifier,
    onDetails: () -> Unit = {},
) {
    LazyColumn(
        modifier = modifier.fillMaxWidth(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(
            start = Dimens.ScreenHMargin,
            end = Dimens.ScreenHMargin,
            top = 8.dp,
            bottom = 24.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(Dimens.StackGap),
    ) {
        item { ScreenHeader(title = "unPawse") }
        item { DailyScreenTimeCard(state) }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(Dimens.Gutter)) {
                PreventedCard(state.preventedCount, Modifier.weight(1f))
                TrendCard(state.trendLabel, state.trendBars, Modifier.weight(1f))
            }
        }
        item { UsageBreakdownCard(state, onDetails) }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(Dimens.Gutter)) {
                MiniStatCard("Longest Streak", state.longestStreak, Icons.Filled.LocalFireDepartment,
                    MaterialTheme.colorScheme.surfaceContainerHigh, Modifier.weight(1f))
                MiniStatCard("Unlocks", state.unlocks, Icons.Filled.PhoneAndroid,
                    MaterialTheme.colorScheme.surfaceContainerLowest, Modifier.weight(1f))
            }
        }
        item { CapturedPhotosBanner(state.capturedPhotos) }
        item {
            SectionLabel(text = "Recent Achievements")
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(Dimens.Gutter)) {
                state.achievements.forEach { achievement ->
                    AchievementCard(achievement, Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun DailyScreenTimeCard(state: StatsUiState) {
    PawCard(modifier = Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.Top) {
            Column(modifier = Modifier.weight(1f)) {
                Text("Daily Screen Time", style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(state.dailyTotal, style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                Spacer(Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Filled.ArrowDownward,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.secondary,
                        modifier = Modifier.size(16.dp),
                    )
                    Text(
                        state.deltaText,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.secondary,
                    )
                }
            }
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Filled.Assessment, contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer, modifier = Modifier.size(22.dp))
            }
        }
        Spacer(Modifier.height(16.dp))
        LineChart(
            points = state.weeklyPoints,
            labels = state.weekdayLabels,
            highlightIndex = state.highlightDayIndex,
        )
    }
}

@Composable
private fun PreventedCard(count: Int, modifier: Modifier = Modifier) {
    PawCard(modifier = modifier) {
        Text("Prevented", style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(count.toString(), style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
        Text("INTERRUPTIONS", style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun TrendCard(label: String, bars: List<Float>, modifier: Modifier = Modifier) {
    PawCard(modifier = modifier, containerColor = MaterialTheme.colorScheme.primaryContainer, shadowElevation = 0.dp) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Trend", style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer, modifier = Modifier.weight(1f))
            Icon(Icons.AutoMirrored.Filled.TrendingDown, contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimaryContainer, modifier = Modifier.size(18.dp))
        }
        Text(label, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onPrimaryContainer)
        Spacer(Modifier.height(8.dp))
        MiniBarChart(
            values = bars,
            barColor = MaterialTheme.colorScheme.primary,
            modifier = Modifier
                .fillMaxWidth()
                .height(32.dp),
        )
    }
}

@Composable
private fun UsageBreakdownCard(state: StatsUiState, onDetails: () -> Unit) {
    PawCard(modifier = Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Usage Breakdown", style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            TextButton(onClick = onDetails) { Text("Details") }
        }
        Spacer(Modifier.height(8.dp))
        Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            DonutChart(
                segments = state.breakdown.map { DonutSegment(it.durationWeight(), it.color.toColor()) },
                modifier = Modifier.size(180.dp),
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("${state.productivePercent}%", style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                    Text("Productive", style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        Spacer(Modifier.height(16.dp))
        state.breakdown.forEach { category ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .clip(CircleShape)
                        .background(category.color.toColor()),
                )
                Spacer(Modifier.width(12.dp))
                Text(category.label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                Text(category.duration, style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun MiniStatCard(label: String, value: String, icon: ImageVector, container: Color, modifier: Modifier = Modifier) {
    PawCard(modifier = modifier, containerColor = container) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(22.dp))
        Spacer(Modifier.height(12.dp))
        Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface)
    }
}

@Composable
private fun CapturedPhotosBanner(photos: String) {
    PawCard(
        modifier = Modifier.fillMaxWidth(),
        containerColor = MaterialTheme.colorScheme.secondaryContainer,
        shadowElevation = 0.dp,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainerLowest.copy(alpha = 0.5f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Filled.PhotoLibrary, contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSecondaryContainer)
            }
            Spacer(Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text("Captured Cat Photos", style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSecondaryContainer)
                Text(photos, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSecondaryContainer)
            }
            Icon(Icons.Filled.Celebration, contentDescription = null,
                tint = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.5f), modifier = Modifier.size(28.dp))
        }
    }
}

@Composable
private fun AchievementCard(achievement: Achievement, modifier: Modifier = Modifier) {
    PawCard(modifier = modifier) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(CircleShape)
                    .background(achievement.color.toColor()),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = if (achievement.color == AchievementColor.CORAL) Icons.Filled.MilitaryTech else Icons.Filled.Nightlight,
                    contentDescription = null,
                    tint = Color.White,
                )
            }
            Spacer(Modifier.height(12.dp))
            Text(achievement.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface)
            Text(achievement.subtitle, style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center)
        }
    }
}

@Composable
private fun UsageColor.toColor(): Color = when (this) {
    UsageColor.SOCIAL -> MaterialTheme.colorScheme.primary
    UsageColor.PRODUCTIVITY -> MaterialTheme.colorScheme.secondary
    UsageColor.ENTERTAINMENT -> MaterialTheme.colorScheme.primaryContainer
}

@Composable
private fun AchievementColor.toColor(): Color = when (this) {
    AchievementColor.CORAL -> MaterialTheme.colorScheme.tertiaryContainer
    AchievementColor.SAGE -> MaterialTheme.colorScheme.secondaryContainer
}

/** Rough weight for donut proportions derived from the displayed duration (Social>Prod>Ent). */
private fun UsageCategory.durationWeight(): Float = when (color) {
    UsageColor.SOCIAL -> 72f
    UsageColor.PRODUCTIVITY -> 45f
    UsageColor.ENTERTAINMENT -> 32f
}

@Preview(showBackground = true, backgroundColor = 0xFFFFF8F8, heightDp = 1600)
@Composable
private fun StatsScreenPreview() {
    UnPawseTheme {
        StatsScreen(state = StatsUiState.sample())
    }
}
