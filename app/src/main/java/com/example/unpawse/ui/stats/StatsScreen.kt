package com.example.unpawse.ui.stats

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Assessment
import androidx.compose.material.icons.filled.Celebration
import androidx.compose.material.icons.filled.HourglassBottom
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MilitaryTech
import androidx.compose.material.icons.filled.Nightlight
import androidx.compose.material.icons.filled.Pets
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
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
        item { ScreenHeader(title = "unPawse", avatarInitial = state.avatarInitial) }
        item { DailyScreenTimeCard(state) }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(Dimens.Gutter)) {
                PreventedCard(state.preventedCount, Modifier.weight(1f))
                TrendCard(state, Modifier.weight(1f))
            }
        }
        item { UsageBreakdownCard(state, onDetails) }
        item {
            // Its own tile now that the donut reports screen time. The caption is part of the claim:
            // uncapped apps are excluded, so a bare percentage would imply a whole-device figure.
            MiniStatCard("Budget Left", state.budgetLeftLabel, Icons.Filled.HourglassBottom,
                MaterialTheme.colorScheme.surfaceContainerHigh, Modifier.fillMaxWidth(),
                caption = "TODAY, ACROSS CAPPED APPS")
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(Dimens.Gutter)) {
                MiniStatCard("Longest Streak", state.longestStreak, Icons.Filled.LocalFireDepartment,
                    MaterialTheme.colorScheme.surfaceContainerHigh, Modifier.weight(1f))
                // The caption is part of the claim: unlocks are only seen while the monitor service
                // is alive, so an uncaptioned number would imply a complete tally it isn't.
                MiniStatCard("Unlocks", state.unlocks, Icons.Filled.PhoneAndroid,
                    MaterialTheme.colorScheme.surfaceContainerLowest, Modifier.weight(1f),
                    caption = "TODAY, WHILE MONITORING")
            }
        }
        item { CapturedPhotosBanner(state.capturedPhotos) }
        // No emptiness guard any more: the catalogue is fixed, so a fresh install legitimately shows
        // every badge locked. That is content — it says what there is to earn — unlike the bare
        // heading over blank space this used to guard against.
        item {
            SectionLabel(text = "Recent Achievements")
            Spacer(Modifier.height(8.dp))
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(Dimens.Gutter),
            ) {
                state.achievements.forEach { achievement ->
                    AchievementCard(achievement, Modifier.width(ACHIEVEMENT_CARD_WIDTH))
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
                    // Follows deltaIsPositive, same rule as the trend arrow. This was pinned to a
                    // green ArrowDownward, so a day where usage doubled rendered "100% from
                    // yesterday" as though it were an improvement. The arrow and its colour both
                    // encode the claim, so both are state-driven, and it is no longer decorative.
                    //
                    // With no yesterday to compare against there is no direction to report, so the
                    // arrow is omitted entirely rather than defaulted: any usage at all beats zero,
                    // so a default would put a red "went up" arrow beside "No data for yesterday".
                    val deltaTint = when {
                        !state.deltaHasBaseline -> MaterialTheme.colorScheme.onSurfaceVariant
                        state.deltaIsPositive -> MaterialTheme.colorScheme.error
                        else -> MaterialTheme.colorScheme.secondary
                    }
                    if (state.deltaHasBaseline) {
                        Icon(
                            if (state.deltaIsPositive) Icons.Filled.ArrowUpward else Icons.Filled.ArrowDownward,
                            contentDescription = if (state.deltaIsPositive) "Up from yesterday" else "Down from yesterday",
                            tint = deltaTint,
                            modifier = Modifier.size(16.dp),
                        )
                    }
                    Text(
                        state.deltaText,
                        style = MaterialTheme.typography.bodyMedium,
                        color = deltaTint,
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
        // The period is part of the claim: the mockup's bare "42" said nothing about what it
        // counted. This is the same Mon–Sun week the chart draws and the trend compares.
        Text("INTERRUPTIONS", style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text("THIS WEEK", style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun TrendCard(state: StatsUiState, modifier: Modifier = Modifier) {
    PawCard(modifier = modifier, containerColor = MaterialTheme.colorScheme.primaryContainer, shadowElevation = 0.dp) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Trend", style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer, modifier = Modifier.weight(1f))
            // Follows the sign in the label; this was pinned to TrendingDown, so a week where usage
            // rose showed "+0.6h" beside a downward arrow. With no last week behind it there is no
            // direction to report, so the arrow goes rather than defaulting — same rule as the
            // vs-yesterday arrow above.
            if (state.trendHasBaseline) {
                Icon(
                    if (state.trendIsUp) Icons.AutoMirrored.Filled.TrendingUp else Icons.AutoMirrored.Filled.TrendingDown,
                    contentDescription = if (state.trendIsUp) "Usage up week over week" else "Usage down week over week",
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.size(18.dp),
                )
            }
        }
        Text(state.trendLabel, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onPrimaryContainer)
        // The period is part of the claim, as on the Prevented card beside it.
        Text(state.trendCaption, style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onPrimaryContainer)
        Spacer(Modifier.height(8.dp))
        MiniBarChart(
            values = state.trendBars,
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
                // Sized from the real durations. DonutChart normalises raw values itself, so the
                // arcs track the legend beside them instead of a fixed palette-keyed weight table.
                segments = state.breakdown.map { DonutSegment(it.seconds.toFloat(), it.color.toColor()) },
                modifier = Modifier.size(180.dp),
            ) {
                // The total of the slices around it. The centre used to show budget left, an
                // unrelated measurement, so the ring and the number it framed disagreed by design.
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(state.breakdownTotal, style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                    Text("Screen time", style = MaterialTheme.typography.bodyMedium,
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
private fun MiniStatCard(
    label: String,
    value: String,
    icon: ImageVector,
    container: Color,
    modifier: Modifier = Modifier,
    /** Optional scope line under the value, for a number that doesn't speak for itself. */
    caption: String? = null,
) {
    PawCard(modifier = modifier, containerColor = container) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(22.dp))
        Spacer(Modifier.height(12.dp))
        Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface)
        if (caption != null) {
            Text(caption, style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
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

/** Fixed width so locked and earned cards line up in the scrolling rail. */
private val ACHIEVEMENT_CARD_WIDTH = 148.dp

@Composable
private fun AchievementCard(achievement: Achievement, modifier: Modifier = Modifier) {
    // The mockup greys locked badges with `opacity-50 grayscale`. Only the opacity half is
    // reproduced: true desaturation needs a saturation ColorMatrix via RenderEffect, which is
    // API 31+ against minSdk 26. Alpha plus the neutral container reads the same at every level, so
    // this is finished rather than pending — don't "complete" it with a RenderEffect.
    val alpha = if (achievement.unlocked) 1f else 0.5f
    val circleColor = if (achievement.unlocked) {
        achievement.color.toColor()
    } else {
        MaterialTheme.colorScheme.primaryContainer
    }

    PawCard(modifier = modifier.alpha(alpha)) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(CircleShape)
                    .background(circleColor),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = achievement.icon.toIcon(),
                    contentDescription = if (achievement.unlocked) null else "Not earned yet",
                    tint = Color.White,
                )
            }
            Spacer(Modifier.height(12.dp))
            Text(achievement.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface, textAlign = TextAlign.Center)
            Text(achievement.subtitle, style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center)
        }
    }
}

private fun AchievementIcon.toIcon(): ImageVector = when (this) {
    AchievementIcon.TROPHY -> Icons.Filled.MilitaryTech
    AchievementIcon.CATS -> Icons.Filled.Pets
    AchievementIcon.STREAK -> Icons.Filled.LocalFireDepartment
    AchievementIcon.BUDGET -> Icons.Filled.Nightlight
    AchievementIcon.SHIELD -> Icons.Filled.Shield
    AchievementIcon.LOCKED -> Icons.Filled.Lock
}

@Composable
private fun UsageColor.toColor(): Color = when (this) {
    UsageColor.SOCIAL -> MaterialTheme.colorScheme.primary
    UsageColor.PRODUCTIVITY -> MaterialTheme.colorScheme.secondary
    UsageColor.ENTERTAINMENT -> MaterialTheme.colorScheme.primaryContainer
    // The mockup's "everything else" legend dot; neutral so it doesn't read as a fourth brand
    // category competing with the three the user actually chose between.
    UsageColor.OTHER -> MaterialTheme.colorScheme.outlineVariant
}

@Composable
private fun AchievementColor.toColor(): Color = when (this) {
    AchievementColor.CORAL -> MaterialTheme.colorScheme.tertiaryContainer
    AchievementColor.SAGE -> MaterialTheme.colorScheme.secondaryContainer
}

@Preview(showBackground = true, backgroundColor = 0xFFFFF8F8, heightDp = 1600)
@Composable
private fun StatsScreenPreview() {
    UnPawseTheme {
        StatsScreen(state = StatsUiState.sample())
    }
}
