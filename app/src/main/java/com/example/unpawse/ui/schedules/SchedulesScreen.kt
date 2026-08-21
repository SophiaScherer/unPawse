package com.example.unpawse.ui.schedules

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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.unpawse.ui.components.BackHeader
import com.example.unpawse.ui.components.EmptyStateCard
import com.example.unpawse.ui.components.IconTile
import com.example.unpawse.ui.components.PawCard
import com.example.unpawse.ui.format.countLabel
import com.example.unpawse.ui.theme.Dimens
import com.example.unpawse.ui.theme.UnPawseTheme

/**
 * Stateless Schedules screen: recurring windows during which apps are blocked outright, whatever
 * budget is left. Complements the App Picker, which answers "how much"; this answers "when".
 *
 * The editor is a bottom sheet opened from here, so which window is being edited is local state —
 * the values themselves round-trip through the callbacks. Callbacks default to no-ops so the
 * @Previews render standalone.
 */
@Composable
fun SchedulesScreen(
    state: SchedulesUiState,
    modifier: Modifier = Modifier,
    onBack: () -> Unit = {},
    onSave: (ScheduleDraft) -> Unit = {},
    onDelete: (Long) -> Unit = {},
    onToggleEnabled: (Long, Boolean) -> Unit = { _, _ -> },
) {
    var editing by remember { mutableStateOf<ScheduleDraft?>(null) }

    editing?.let { draft ->
        ScheduleEditorSheet(
            initial = draft,
            appOptions = state.appOptions,
            onDismiss = { editing = null },
            onSave = onSave,
            onDelete = onDelete,
        )
    }

    Column(modifier = modifier.fillMaxWidth()) {
        BackHeader(
            title = "Schedules",
            subtitle = summaryFor(state),
            onBack = onBack,
            modifier = Modifier.padding(horizontal = Dimens.ScreenHMargin - 12.dp),
        )

        when {
            state.isLoading -> LoadingState()
            else -> LazyColumn(
                contentPadding = PaddingValues(
                    start = Dimens.ScreenHMargin,
                    end = Dimens.ScreenHMargin,
                    bottom = 24.dp,
                ),
                verticalArrangement = Arrangement.spacedBy(Dimens.StackGap),
            ) {
                if (state.windows.isEmpty()) {
                    item { EmptyState() }
                }

                items(state.windows, key = { it.id }) { item ->
                    ScheduleRow(
                        item = item,
                        onClick = { editing = item.draft },
                        onToggleEnabled = { onToggleEnabled(item.id, it) },
                    )
                }

                item {
                    Spacer(Modifier.size(4.dp))
                    Button(
                        onClick = { editing = ScheduleDraft() },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(50),
                    ) {
                        Icon(Icons.Filled.Add, contentDescription = null)
                        Spacer(Modifier.size(8.dp))
                        Text("Add schedule", style = MaterialTheme.typography.labelLarge)
                    }
                }
            }
        }
    }
}

private fun summaryFor(state: SchedulesUiState): String = when {
    state.isLoading -> "Loading…"
    state.windows.isEmpty() -> "No schedules yet"
    state.activeCount == 0 -> "All paused"
    else -> "${countLabel(state.activeCount, "schedule")} active"
}

/** One window: name, hours, days and scope, with a switch to pause it without deleting it. */
@Composable
private fun ScheduleRow(
    item: ScheduleItem,
    onClick: () -> Unit,
    onToggleEnabled: (Boolean) -> Unit,
) {
    PawCard(contentPadding = 12.dp, onClick = onClick) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconTile(
                icon = Icons.Filled.Bedtime,
            )
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    text = item.label,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = if (item.isOvernight) "${item.timeRange} (overnight)" else item.timeRange,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = "${item.daysLabel} · ${item.scopeLabel}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.outline,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(Modifier.width(12.dp))
            Switch(checked = item.enabled, onCheckedChange = onToggleEnabled)
        }
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
private fun EmptyState() {
    EmptyStateCard(
        title = "Nothing scheduled yet",
        body = "A schedule blocks apps at set times — bedtime, school hours, dinner — no matter " +
            "how much time is left in their daily budget. There's no cat-photo escape from one.",
    )
}

/** Interactive preview body so the switches and the Add button actually respond. */
@Composable
private fun SchedulesPreviewContent() {
    var state by remember { mutableStateOf(SchedulesUiState.sample()) }
    SchedulesScreen(
        state = state,
        onToggleEnabled = { id, on ->
            state = state.copy(
                windows = state.windows.map {
                    if (it.id == id) it.copy(draft = it.draft.copy(enabled = on)) else it
                },
            )
        },
    )
}

@Preview(name = "Schedules", showBackground = true, backgroundColor = 0xFFFFF8F8, heightDp = 900)
@Composable
private fun SchedulesScreenPreview() {
    UnPawseTheme { SchedulesPreviewContent() }
}

@Preview(name = "Schedules · dark", showBackground = true, backgroundColor = 0xFF171213, heightDp = 900)
@Composable
private fun SchedulesScreenDarkPreview() {
    UnPawseTheme(darkTheme = true) { SchedulesPreviewContent() }
}

@Preview(name = "Schedules · empty", showBackground = true, backgroundColor = 0xFFFFF8F8, heightDp = 500)
@Composable
private fun SchedulesEmptyPreview() {
    UnPawseTheme {
        SchedulesScreen(state = SchedulesUiState(isLoading = false))
    }
}
