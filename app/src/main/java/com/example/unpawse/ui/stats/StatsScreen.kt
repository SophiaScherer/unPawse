package com.example.unpawse.ui.stats

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
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
import com.example.unpawse.data.usage.UsageScope
import com.example.unpawse.ui.components.DonutChart
import com.example.unpawse.ui.components.DonutSegment
import com.example.unpawse.ui.components.EmptyStateCard
import com.example.unpawse.ui.components.LineChart
import com.example.unpawse.ui.components.MiniBarChart
import com.example.unpawse.ui.components.PawCard
import com.example.unpawse.ui.theme.unPawseColors
import com.example.unpawse.ui.components.ScreenHeader
import com.example.unpawse.ui.components.SectionLabel
import com.example.unpawse.ui.components.SegmentedToggle
import com.example.unpawse.ui.theme.Dimens
import com.example.unpawse.ui.theme.UnPawseTheme

@Composable
fun StatsScreen(
    state: StatsUiState,
    modifier: Modifier = Modifier,
    onDetails: () -> Unit = {},
    onScopeChange: (UsageScope) -> Unit = {},
    onGrantUsageAccess: () -> Unit = {},
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
        // Above every card, because it governs all of them — the chart, the trend and the donut all
        // change meaning with it. It sat inside the first card and read as though it belonged to
        // that card's figure alone.
        item { ScopeToggle(state.usageScope, onScopeChange) }
        item { DailyScreenTimeCard(state, onGrantUsageAccess) }
        // Paired cards share a height, so neither row ends in a ragged edge however their captions
        // wrap (audit VIS-03).
        item {
            Row(
                modifier = Modifier.height(IntrinsicSize.Min),
                horizontalArrangement = Arrangement.spacedBy(Dimens.Gutter),
            ) {
                PreventedCard(state.preventedCount, Modifier.weight(1f).fillMaxHeight())
                TrendCard(state, Modifier.weight(1f).fillMaxHeight())
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
            Row(
                modifier = Modifier.height(IntrinsicSize.Min),
                horizontalArrangement = Arrangement.spacedBy(Dimens.Gutter),
            ) {
                MiniStatCard("Longest Streak", state.longestStreak, Icons.Filled.LocalFireDepartment,
                    MaterialTheme.colorScheme.surfaceContainerHigh, Modifier.weight(1f).fillMaxHeight())
                // The caption is part of the claim: unlocks are only seen while the monitor service
                // is alive, so an uncaptioned number would imply a complete tally it isn't.
                MiniStatCard("Unlocks", state.unlocks, Icons.Filled.PhoneAndroid,
                    MaterialTheme.unPawseColors.cardSurface, Modifier.weight(1f).fillMaxHeight(),
                    caption = "TODAY, WHILE MONITORING")
            }
        }
        item { CapturedPhotosBanner(state.capturedPhotos, state.hasCapturedPhotos) }
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

/**
 * Which apps every figure on this screen counts.
 *
 * A sliding two-position control rather than a chip row: it is the page's setting, not one card's
 * filter, and the position of the thumb is what makes the alternative visible without hunting for
 * it. Its own labels carry the scope, so the cards beneath it need no caption of their own — except
 * the breakdown, which scrolls far enough away to need repeating.
 */
@Composable
private fun ScopeToggle(scope: UsageScope, onScopeChange: (UsageScope) -> Unit) {
    val options = UsageScope.entries
    SegmentedToggle(
        labels = options.map { it.label },
        selectedIndex = options.indexOf(scope),
        onSelect = { onScopeChange(options[it]) },
    )
}

@Composable
private fun DailyScreenTimeCard(
    state: StatsUiState,
    onGrantUsageAccess: () -> Unit,
) {
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
                    //
                    // Only a rise gets an arrow. Today is still running, so being under yesterday
                    // is "so far" rather than an improvement, and a green down arrow claimed one
                    // every morning.
                    val rose = state.deltaHasBaseline && state.deltaIsPositive
                    val deltaTint = if (rose) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    }
                    if (rose) {
                        Icon(
                            Icons.Filled.ArrowUpward,
                            contentDescription = "Up from yesterday",
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
        if (state.scopeUnavailable) {
            // The figures aren't merely empty, they're unmeasurable, and a card that reports a
            // problem gets a way to act on it — same hand-off as the App Picker's notice.
            UsageAccessNotice(onGrantUsageAccess)
        } else {
            LineChart(
                points = state.weeklyPoints,
                labels = state.weekdayLabels,
                highlightIndex = state.highlightDayIndex,
            )
        }
    }
}

/**
 * Shown when all-apps is selected without usage access. The tracked scope needs no such notice: it
 * reads what unPawse recorded itself, which is why it stays the default.
 */
@Composable
private fun UsageAccessNotice(onClick: () -> Unit) {
    EmptyStateCard(
        title = "All-apps figures need usage access",
        body = "Grant it to see time across every app on your phone. Tap here to open the setting, " +
            "or switch back to tracked apps.",
        modifier = Modifier.clickable(onClick = onClick),
    )
}

@Composable
private fun PreventedCard(count: Int, modifier: Modifier = Modifier) {
    PawCard(modifier = modifier) {
        // Mirrors the Trend card's header beside it, so the paired cards read as a set.
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Prevented", style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
            Icon(Icons.Filled.Shield, contentDescription = null,
                tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
        }
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
            if (state.trendHasBaseline && !state.trendIsLevel) {
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
            Column(modifier = Modifier.weight(1f)) {
                Text("Usage Breakdown", style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold)
                // Under the title, not in the donut's hole: the ring's centre is 124dp across and
                // this caption is wider than that, so it used to overlap the arcs it describes.
                // Repeated here at all because the toggle has scrolled off by this point.
                if (state.scopeCaption.isNotEmpty()) {
                    Text(state.scopeCaption, style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
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

/**
 * The photo tally. It celebrates a collection, so it only *looks* like a celebration once there is
 * one: an empty library got the tinted fill and a party popper over the words "0 Photos". Zero is
 * not an achievement, and the glyph and the fill are claims as much as the number is.
 */
@Composable
private fun CapturedPhotosBanner(photos: String, hasPhotos: Boolean) {
    // Ink follows the fill: onPrimaryContainer is only the right contrast on the tinted branch.
    val onCard = if (hasPhotos) {
        MaterialTheme.colorScheme.onPrimaryContainer
    } else {
        MaterialTheme.colorScheme.onSurface
    }
    val onCardVariant = if (hasPhotos) onCard else MaterialTheme.colorScheme.onSurfaceVariant

    PawCard(
        modifier = Modifier.fillMaxWidth(),
        containerColor = if (hasPhotos) {
            MaterialTheme.colorScheme.primaryContainer
        } else {
            MaterialTheme.unPawseColors.cardSurface
        },
        shadowElevation = if (hasPhotos) 0.dp else 2.dp,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(
                        if (hasPhotos) {
                            MaterialTheme.colorScheme.surfaceContainerLowest.copy(alpha = 0.5f)
                        } else {
                            MaterialTheme.colorScheme.surfaceContainerHigh
                        },
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Filled.PhotoLibrary, contentDescription = null, tint = onCard)
            }
            Spacer(Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text("Captured Cat Photos", style = MaterialTheme.typography.bodyMedium,
                    color = onCardVariant)
                Text(photos, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold,
                    color = onCard)
                if (!hasPhotos) {
                    Text("Photograph your cat to start your collection.",
                        style = MaterialTheme.typography.bodySmall, color = onCardVariant)
                }
            }
            if (hasPhotos) {
                Icon(Icons.Filled.Celebration, contentDescription = null,
                    tint = onCard.copy(alpha = 0.5f), modifier = Modifier.size(28.dp))
            }
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
    // The icon was a hardcoded white, which measured 1.29:1 on the light-theme sage circle — both
    // states need ink chosen against the fill underneath it.
    val circleColor: Color
    val iconColor: Color
    if (achievement.unlocked) {
        circleColor = achievement.color.toColor()
        iconColor = MaterialTheme.unPawseColors.onCategory
    } else {
        circleColor = MaterialTheme.colorScheme.primaryContainer
        iconColor = MaterialTheme.colorScheme.onPrimaryContainer
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
                    tint = iconColor,
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

// Fixed tokens rather than scheme slots: Social was `primary` and Entertainment `primaryContainer`,
// one hue at two lightnesses, so the pair swapped appearance whenever the theme flipped. Other stays
// neutral so it doesn't read as a fourth brand category competing with the three the user chose.
@Composable
private fun UsageColor.toColor(): Color = when (this) {
    UsageColor.SOCIAL -> MaterialTheme.unPawseColors.categorySocial
    UsageColor.PRODUCTIVITY -> MaterialTheme.unPawseColors.categoryProductivity
    UsageColor.ENTERTAINMENT -> MaterialTheme.unPawseColors.categoryEntertainment
    UsageColor.OTHER -> MaterialTheme.unPawseColors.categoryOther
}

// Borrows the category hues so the app has one coral and one green rather than two of each. Same
// inversion bug as above: these were container slots, which are pale in light and dark in dark.
@Composable
private fun AchievementColor.toColor(): Color = when (this) {
    AchievementColor.CORAL -> MaterialTheme.unPawseColors.categoryEntertainment
    AchievementColor.SAGE -> MaterialTheme.unPawseColors.categoryProductivity
}

@Preview(name = "Stats", showBackground = true, backgroundColor = 0xFFFFF8F8, heightDp = 1600)
@Composable
private fun StatsScreenPreview() {
    UnPawseTheme {
        StatsScreen(state = StatsUiState.sample())
    }
}

// The donut and the achievement rail are the two places a theme flip used to change what a colour
// meant, so this screen is the one that most needs both previews side by side.
@Preview(name = "Stats · dark", showBackground = true, backgroundColor = 0xFF171213, heightDp = 1600)
@Composable
private fun StatsScreenDarkPreview() {
    UnPawseTheme(darkTheme = true) {
        StatsScreen(state = StatsUiState.sample())
    }
}

// All-apps without usage access: the one state where the card reports a problem rather than a
// figure, and the only place the chips sit over a notice instead of a chart.
@Preview(name = "Stats · all apps, no access", showBackground = true, backgroundColor = 0xFFFFF8F8, heightDp = 1600)
@Composable
private fun StatsScreenScopeUnavailablePreview() {
    UnPawseTheme {
        StatsScreen(
            state = StatsUiState.empty().copy(
                usageScope = UsageScope.ALL,
                scopeCaption = "ALL APPS ON THIS PHONE",
                scopeUnavailable = true,
            ),
        )
    }
}
