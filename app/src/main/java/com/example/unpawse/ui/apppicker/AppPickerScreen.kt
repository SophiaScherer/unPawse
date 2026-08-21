package com.example.unpawse.ui.apppicker

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.unpawse.data.apps.RECENT_DAYS
import com.example.unpawse.data.usage.AppCategory
import com.example.unpawse.ui.components.BackHeader
import com.example.unpawse.ui.components.EmptyStateCard
import com.example.unpawse.ui.components.FilterChip
import com.example.unpawse.ui.components.clearFocusOnScroll
import com.example.unpawse.ui.components.PawCard
import com.example.unpawse.ui.components.SearchField
import com.example.unpawse.ui.components.ValueStepper
import com.example.unpawse.ui.format.countLabel
import com.example.unpawse.ui.format.formatMinutes
import com.example.unpawse.ui.theme.Dimens
import com.example.unpawse.ui.theme.UnPawseTheme

/**
 * Stateless app picker: choose which apps unPawse watches and set each one's daily budget.
 * Replaces the Settings screen's hardcoded "Instagram, TikTok, 3 others" summary with real choices.
 * Callbacks default to no-ops so the @Previews render standalone.
 */
@Composable
fun AppPickerScreen(
    state: AppPickerUiState,
    modifier: Modifier = Modifier,
    onBack: () -> Unit = {},
    onSearchChange: (String) -> Unit = {},
    onSortChange: (AppSort) -> Unit = {},
    onGrantUsageAccess: () -> Unit = {},
    onToggleMonitored: (AppLimitItem, Boolean) -> Unit = { _, _ -> },
    onLimitChange: (AppLimitItem, Int) -> Unit = { _, _ -> },
    onWeekendLimitChange: (AppLimitItem, Int?) -> Unit = { _, _ -> },
    onCategoryChange: (AppLimitItem, AppCategory) -> Unit = { _, _ -> },
    onOpenSchedules: () -> Unit = {},
) {
    val focusManager = LocalFocusManager.current
    // Reaching for any row control means the user is done typing, and the keyboard is sitting over
    // the rows they are aiming at — so every one of them dismisses it before doing its own job.
    val dismissKeyboard = { focusManager.clearFocus() }

    Column(modifier = modifier.fillMaxWidth()) {
        AppPickerHeader(monitoredCount = state.monitoredCount, onBack = onBack)

        SearchField(
            query = state.searchQuery,
            placeholder = SEARCH_PLACEHOLDER,
            onQueryChange = onSearchChange,
            modifier = Modifier.padding(horizontal = Dimens.ScreenHMargin),
        )

        Spacer(Modifier.size(Dimens.StackGap))

        SortControls(
            sort = state.sort,
            // No figures means the caption would be describing something the rows don't show.
            showAverageCaption = state.usageAccessGranted,
            onSortChange = { dismissKeyboard(); onSortChange(it) },
            modifier = Modifier.padding(horizontal = Dimens.ScreenHMargin),
        )

        if (!state.usageAccessGranted) {
            Spacer(Modifier.size(Dimens.StackGap))
            UsageAccessNotice(
                onClick = { dismissKeyboard(); onGrantUsageAccess() },
                modifier = Modifier.padding(horizontal = Dimens.ScreenHMargin),
            )
        }

        Spacer(Modifier.size(Dimens.StackGap))

        when {
            state.isLoading -> LoadingState()
            state.apps.isEmpty() -> EmptyState(hasQuery = state.searchQuery.isNotBlank())
            else -> LazyColumn(
                modifier = Modifier.clearFocusOnScroll(),
                contentPadding = PaddingValues(
                    start = Dimens.ScreenHMargin,
                    end = Dimens.ScreenHMargin,
                    bottom = 24.dp,
                ),
                verticalArrangement = Arrangement.spacedBy(Dimens.StackGap),
            ) {
                items(state.apps, key = { it.packageName }) { app ->
                    AppLimitRow(
                        item = app,
                        onToggleMonitored = { dismissKeyboard(); onToggleMonitored(app, it) },
                        onLimitChange = { dismissKeyboard(); onLimitChange(app, it) },
                        onWeekendLimitChange = { dismissKeyboard(); onWeekendLimitChange(app, it) },
                        onCategoryChange = { dismissKeyboard(); onCategoryChange(app, it) },
                        onOpenSchedules = { dismissKeyboard(); onOpenSchedules() },
                    )
                }
            }
        }
    }
}

@Composable
private fun AppPickerHeader(monitoredCount: Int, onBack: () -> Unit) {
    BackHeader(
        title = "App limits",
        subtitle = if (monitoredCount == 0) {
            "No apps limited yet"
        } else {
            "${countLabel(monitoredCount, "app")} limited"
        },
        onBack = onBack,
        // This header sits outside the list, so it pads itself; the 12dp back-off aligns the arrow
        // glyph (not the IconButton's touch target) with the rows below.
        modifier = Modifier.padding(horizontal = Dimens.ScreenHMargin - 12.dp),
    )
}

/**
 * How the list is ordered, plus what the figures under each name mean.
 *
 * The caption is the period the averages cover — a figure that doesn't state its window is the trap
 * AGENTS.md records for the Stats cards, and it is stated once here rather than on every row.
 */
@Composable
private fun SortControls(
    sort: AppSort,
    showAverageCaption: Boolean,
    onSortChange: (AppSort) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            AppSort.entries.forEach { option ->
                FilterChip(
                    label = option.label,
                    selected = option == sort,
                    onClick = { onSortChange(option) },
                )
            }
        }
        if (showAverageCaption) {
            Spacer(Modifier.size(6.dp))
            Text(
                text = "Daily average, last $RECENT_DAYS days",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/**
 * Shown whenever usage access is missing, in either sort — both the ranking and every row's figure
 * are degraded, so it is one state rather than two. Tapping hands off to the system screen: a card
 * that reports a problem gets a way to act on it, like Home's Pause Protection card.
 */
@Composable
private fun UsageAccessNotice(onClick: () -> Unit, modifier: Modifier = Modifier) {
    EmptyStateCard(
        title = "Usage figures need usage access",
        body = "Grant it to sort by most used and see what each app costs you per day. " +
            "Tap here to open the setting.",
        modifier = modifier.clickable(onClick = onClick),
    )
}

/**
 * One app: icon, name, monitor switch, and — when monitored — its everyday limit, its weekend
 * override, and which blocking schedules cover it.
 */
@Composable
private fun AppLimitRow(
    item: AppLimitItem,
    onToggleMonitored: (Boolean) -> Unit,
    onLimitChange: (Int) -> Unit,
    onWeekendLimitChange: (Int?) -> Unit,
    onCategoryChange: (AppCategory) -> Unit,
    onOpenSchedules: () -> Unit,
) {
    PawCard(contentPadding = 12.dp) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            AppIcon(packageName = item.packageName, label = item.label)
            Spacer(Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.label,
                    style = MaterialTheme.typography.bodyLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                // Absent rather than zeroed when there is no figure — see AppLimitItem.
                dailyAverageLabel(item.dailyAverageSeconds)?.let { average ->
                    Text(
                        text = average,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Spacer(Modifier.width(12.dp))
            Switch(checked = item.monitored, onCheckedChange = onToggleMonitored)
        }

        if (item.monitored) {
            Spacer(Modifier.size(8.dp))
            ValueStepper(
                label = "Daily limit",
                value = item.dailyLimitMinutes,
                onChange = onLimitChange,
                step = LIMIT_STEP_MINUTES,
                min = MIN_LIMIT_MINUTES,
                max = MAX_LIMIT_MINUTES,
                format = ::formatMinutes,
            )
            Spacer(Modifier.size(4.dp))
            CategoryControl(item = item, onCategoryChange = onCategoryChange)
            WeekendLimitControl(item = item, onWeekendLimitChange = onWeekendLimitChange)
            ScheduleSummaryRow(summary = item.scheduleSummary, onClick = onOpenSchedules)
        }
    }
}

/**
 * The "when" half of this app's limit, surfaced here so windows are discoverable from where budgets
 * are set. Tapping it hands over to the Schedules screen, which owns the editing.
 */
@Composable
private fun ScheduleSummaryRow(summary: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "Schedules",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = summary,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f, fill = false),
        )
    }
}

@Composable
private fun LoadingState() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 48.dp),
        contentAlignment = Alignment.Center,
    ) {
        CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
    }
}

@Composable
private fun EmptyState(hasQuery: Boolean) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Dimens.ScreenHMargin, vertical = 48.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = if (hasQuery) "No apps match that search." else "No apps found on this device.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** Interactive preview body so the switches/stepper actually respond. */
@Composable
private fun AppPickerPreviewContent() {
    var state by remember { mutableStateOf(AppPickerUiState.sample()) }
    AppPickerScreen(
        state = state,
        onSearchChange = { query -> state = state.copy(searchQuery = query) },
        onSortChange = { sort ->
            state = state.copy(
                sort = sort,
                apps = when (sort) {
                    AppSort.ALPHABETICAL -> state.apps.sortedBy { it.label.lowercase() }
                    AppSort.MOST_USED -> state.apps.sortedByDescending { it.dailyAverageSeconds ?: 0L }
                },
            )
        },
        onToggleMonitored = { item, on ->
            state = state.copy(
                apps = state.apps.map { if (it.packageName == item.packageName) it.copy(monitored = on) else it },
            )
        },
        onLimitChange = { item, minutes ->
            state = state.copy(
                apps = state.apps.map {
                    if (it.packageName == item.packageName) it.copy(dailyLimitMinutes = minutes) else it
                },
            )
        },
        onWeekendLimitChange = { item, minutes ->
            state = state.copy(
                apps = state.apps.map {
                    if (it.packageName == item.packageName) it.copy(weekendLimitMinutes = minutes) else it
                },
            )
        },
        onCategoryChange = { item, category ->
            state = state.copy(
                apps = state.apps.map {
                    if (it.packageName == item.packageName) it.copy(category = category) else it
                },
            )
        },
    )
}

@Preview(name = "App picker", showBackground = true, backgroundColor = 0xFFFFF8F8, heightDp = 900)
@Composable
private fun AppPickerScreenPreview() {
    UnPawseTheme { AppPickerPreviewContent() }
}

@Preview(name = "App picker · dark", showBackground = true, backgroundColor = 0xFF171213, heightDp = 900)
@Composable
private fun AppPickerScreenDarkPreview() {
    UnPawseTheme(darkTheme = true) { AppPickerPreviewContent() }
}
