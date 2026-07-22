package com.example.unpawse.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.EditCalendar
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.unpawse.ui.components.ActivityTimeline
import com.example.unpawse.ui.components.CatPhotoPlaceholder
import com.example.unpawse.ui.components.PawCard
import com.example.unpawse.ui.components.ProgressRing
import com.example.unpawse.ui.components.ScreenHeader
import com.example.unpawse.ui.components.SectionLabel
import com.example.unpawse.ui.components.StatPill
import com.example.unpawse.ui.components.TimelineEntry
import com.example.unpawse.ui.theme.Dimens
import com.example.unpawse.ui.theme.UnPawseTheme

/** Focus-session lengths offered by the duration picker, in minutes. */
private val FOCUS_DURATION_OPTIONS = listOf(15, 30, 60)

/**
 * Home dashboard. Stateless: it renders [state] and reports intents through the callbacks. The
 * callbacks default to no-ops so previews and not-yet-wired hosts compile cleanly.
 */
@Composable
fun HomeScreen(
    state: HomeUiState,
    focus: FocusCardState = FocusCardState.Inactive,
    modifier: Modifier = Modifier,
    onEditLimits: () -> Unit = {},
    onManageApps: () -> Unit = {},
    onStartFocus: (Int) -> Unit = {},
    onStopFocus: () -> Unit = {},
) {
    // Ephemeral: the duration picker shown by both "Start Focus" and the Focus card.
    var showFocusPicker by remember { mutableStateOf(false) }
    if (showFocusPicker) {
        FocusDurationDialog(
            onSelect = { minutes ->
                onStartFocus(minutes)
                showFocusPicker = false
            },
            onDismiss = { showFocusPicker = false },
        )
    }

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
        item {
            ScreenHeader(
                avatarInitial = state.avatarInitial,
                greeting = state.greeting,
                title = state.userName,
            )
        }

        item { ScreenTimeCard(state) }

        item {
            Row(horizontalArrangement = Arrangement.spacedBy(Dimens.Gutter)) {
                FocusCard(focus, onStart = { showFocusPicker = true }, onStop = onStopFocus, Modifier.weight(1f))
                PauseProtectionCard(state.pausedAppsCount, Modifier.weight(1f))
            }
        }

        item {
            SectionLabel(text = "Quick Actions")
            Spacer(Modifier.height(8.dp))
            QuickActionsRow(onEditLimits, onManageApps, onStartFocus = { showFocusPicker = true })
        }

        item { RecentActivityCard(state.activities) }

        item { PromoBanner(state.bannerTitle, state.bannerBody) }
    }
}

@Composable
private fun ScreenTimeCard(state: HomeUiState) {
    PawCard(modifier = Modifier.fillMaxWidth()) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp),
            contentAlignment = Alignment.Center,
        ) {
            ProgressRing(
                progress = state.progressFraction,
                modifier = Modifier.size(200.dp),
                trackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                progressColor = MaterialTheme.colorScheme.primary,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = state.screenTimeUsedLabel,
                        style = MaterialTheme.typography.displaySmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        text = "Screen time",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        Spacer(Modifier.height(16.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            StatPill(state.remainingLabel, "Remaining", Modifier.weight(1f))
            StatPill(state.streakDays.toString(), "Streak", Modifier.weight(1f), highlighted = true)
            StatPill(state.catCount.toString(), "Cats", Modifier.weight(1f))
        }
    }
}

@Composable
private fun FocusCard(
    focus: FocusCardState,
    onStart: () -> Unit,
    onStop: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // Inactive: tapping the card starts a session. Active: it shows the countdown + a Stop button.
    PawCard(modifier = modifier, onClick = if (focus.active) null else onStart) {
        IconTile(Icons.Filled.Timer, MaterialTheme.colorScheme.secondary, MaterialTheme.colorScheme.secondaryContainer)
        Spacer(Modifier.height(12.dp))
        Text("Focus", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (focus.active) {
            Text(
                focus.remainingLabel,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.secondary,
            )
            TextButton(onClick = onStop, contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp)) {
                Text("Stop", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
            }
        } else {
            Text(
                "Start a session",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}

/** Duration picker for a new focus session. */
@Composable
private fun FocusDurationDialog(onSelect: (Int) -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Start a focus session") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "Every monitored app is paused until the timer ends.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FOCUS_DURATION_OPTIONS.forEach { minutes ->
                        Button(
                            onClick = { onSelect(minutes) },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(50),
                        ) {
                            Text("${minutes}m")
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun PauseProtectionCard(appsActive: Int, modifier: Modifier = Modifier) {
    // Informational only: it reports how many apps are actively protected; it is not a control.
    PawCard(modifier = modifier) {
        IconTile(Icons.Filled.Shield, MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.primaryContainer)
        Spacer(Modifier.height(12.dp))
        Text("Pause Protection", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(4.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.secondary),
            )
            Spacer(Modifier.width(6.dp))
            Text(
                "$appsActive Apps Active",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}

@Composable
private fun IconTile(icon: ImageVector, tint: Color, background: Color) {
    Box(
        modifier = Modifier
            .size(40.dp)
            .clip(CircleShape)
            .background(background),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(22.dp))
    }
}

@Composable
private fun QuickActionsRow(
    onEditLimits: () -> Unit,
    onManageApps: () -> Unit,
    onStartFocus: () -> Unit,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        QuickAction(Icons.Filled.EditCalendar, "Edit Limits", onEditLimits, Modifier.weight(1f))
        QuickAction(Icons.Filled.Apps, "Manage Apps", onManageApps, Modifier.weight(1f))
        QuickAction(
            Icons.Filled.Bolt,
            "Start Focus",
            onStartFocus,
            Modifier.weight(1f),
            filled = true,
        )
    }
}

@Composable
private fun QuickAction(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    filled: Boolean = false,
) {
    val container = if (filled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerLowest
    val content = if (filled) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.primary
    PawCard(
        modifier = modifier,
        onClick = onClick,
        containerColor = container,
        contentPadding = 16.dp,
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(icon, contentDescription = null, tint = content, modifier = Modifier.size(24.dp))
            Text(
                label,
                style = MaterialTheme.typography.labelLarge,
                color = content,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            )
        }
    }
}

@Composable
private fun RecentActivityCard(activities: List<ActivityItem>) {
    PawCard(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "Recent Activity",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f),
            )
            Icon(Icons.Filled.History, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.height(16.dp))
        ActivityTimeline(entries = activities.map { it.toTimelineEntry() })
    }
}

@Composable
private fun PromoBanner(title: String, body: String) {
    PawCard(
        modifier = Modifier.fillMaxWidth(),
        containerColor = MaterialTheme.colorScheme.primaryContainer,
        shadowElevation = 0.dp,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    title,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    body,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
            }
            Spacer(Modifier.width(16.dp))
            CatPhotoPlaceholder(
                seed = 0,
                modifier = Modifier
                    .size(96.dp)
                    .clip(RoundedCornerShape(16.dp)),
            )
        }
    }
}

@Composable
private fun ActivityItem.toTimelineEntry(): TimelineEntry = when (kind) {
    ActivityKind.VERIFIED -> TimelineEntry(
        icon = Icons.Filled.Verified,
        iconTint = MaterialTheme.colorScheme.secondary,
        iconBackground = MaterialTheme.colorScheme.secondaryContainer,
        title = title, subtitle = subtitle, time = time,
    )
    ActivityKind.BLOCKED -> TimelineEntry(
        icon = Icons.Filled.Block,
        iconTint = MaterialTheme.colorScheme.onSurfaceVariant,
        iconBackground = MaterialTheme.colorScheme.surfaceContainerHigh,
        title = title, subtitle = subtitle, time = time,
    )
    ActivityKind.GOAL -> TimelineEntry(
        icon = Icons.Filled.WbSunny,
        iconTint = MaterialTheme.colorScheme.primary,
        iconBackground = MaterialTheme.colorScheme.primaryContainer,
        title = title, subtitle = subtitle, time = time,
    )
}

@Preview(name = "Home", showBackground = true, backgroundColor = 0xFFFFF8F8, heightDp = 1400)
@Composable
private fun HomeScreenPreview() {
    UnPawseTheme {
        HomeScreen(state = HomeUiState.sample())
    }
}

@Preview(name = "Home · dark", showBackground = true, backgroundColor = 0xFF171213, heightDp = 1400)
@Composable
private fun HomeScreenDarkPreview() {
    UnPawseTheme(darkTheme = true) {
        HomeScreen(state = HomeUiState.sample())
    }
}
