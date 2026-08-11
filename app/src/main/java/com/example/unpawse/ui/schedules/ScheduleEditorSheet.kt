package com.example.unpawse.ui.schedules

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.unpawse.ui.components.ConfirmDialog
import com.example.unpawse.ui.components.DayOfWeekChips
import com.example.unpawse.ui.components.DayPresetChips
import com.example.unpawse.ui.components.OptionPickerDialog
import com.example.unpawse.ui.components.SectionLabel
import com.example.unpawse.ui.components.TimeOfDayDialog
import com.example.unpawse.ui.format.formatMinuteOfDay
import com.example.unpawse.ui.theme.Dimens

/**
 * Create/edit sheet for one blocking window: name, start and end time, days, and which apps it
 * covers. Follows `CaptureActionsSheet`'s pattern — a [ModalBottomSheet] whose only local state is
 * the in-flight edit and the dialogs it opens; nothing is persisted until "Save".
 *
 * Editing a draft rather than the saved window means backing out changes nothing, which matters
 * here: a half-edited schedule that took effect mid-edit could block an app the user was using.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScheduleEditorSheet(
    initial: ScheduleDraft,
    appOptions: List<ScheduleAppOption>,
    onDismiss: () -> Unit,
    onSave: (ScheduleDraft) -> Unit,
    onDelete: (Long) -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    // Keyed on the window being edited so reopening the sheet on a different row starts fresh.
    var draft by remember(initial.id) { mutableStateOf(initial) }
    var showStartPicker by remember { mutableStateOf(false) }
    var showEndPicker by remember { mutableStateOf(false) }
    var showScopePicker by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }

    if (showStartPicker) {
        TimeOfDayDialog(
            title = "Starts at",
            minuteOfDay = draft.startMinuteOfDay,
            onConfirm = { draft = draft.copy(startMinuteOfDay = it) },
            onDismiss = { showStartPicker = false },
        )
    }

    if (showEndPicker) {
        TimeOfDayDialog(
            title = "Ends at",
            minuteOfDay = draft.endMinuteOfDay,
            onConfirm = { draft = draft.copy(endMinuteOfDay = it) },
            onDismiss = { showEndPicker = false },
        )
    }

    if (showScopePicker) {
        OptionPickerDialog(
            title = "Applies to",
            options = appOptions,
            selected = appOptions.firstOrNull { it.packageName == draft.packageName },
            onSelect = { draft = draft.copy(packageName = it.packageName) },
            onDismiss = { showScopePicker = false },
            label = { it.label },
            supporting = { option ->
                "Every app unPawse watches".takeIf { option.packageName == null }
            },
        )
    }

    if (confirmDelete) {
        ConfirmDialog(
            title = "Delete schedule?",
            message = "\"${draft.effectiveLabel}\" will stop blocking anything. This can't be undone.",
            confirmLabel = "Delete",
            destructive = true,
            onConfirm = {
                onDelete(draft.id)
                onDismiss()
            },
            onDismiss = { confirmDelete = false },
        )
    }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            Modifier
                .padding(horizontal = Dimens.ScreenHMargin)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = if (draft.isNew) "New schedule" else "Edit schedule",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
            )

            OutlinedTextField(
                value = draft.label,
                onValueChange = { draft = draft.copy(label = it) },
                label = { Text("Name") },
                placeholder = { Text(DEFAULT_LABEL) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                TimeField(
                    label = "Starts",
                    minuteOfDay = draft.startMinuteOfDay,
                    onClick = { showStartPicker = true },
                    modifier = Modifier.weight(1f),
                )
                TimeField(
                    label = "Ends",
                    minuteOfDay = draft.endMinuteOfDay,
                    onClick = { showEndPicker = true },
                    modifier = Modifier.weight(1f),
                )
            }

            if (draft.endMinuteOfDay <= draft.startMinuteOfDay) {
                // Without this the days below read wrongly: they name the night the window starts,
                // not both calendar days it touches.
                Text(
                    text = "Runs overnight into the next morning.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            SectionLabel(text = "Repeats", uppercase = true)
            DayPresetChips(
                daysMask = draft.daysMask,
                onMaskChange = { draft = draft.copy(daysMask = it) },
            )
            Spacer(Modifier.size(2.dp))
            DayOfWeekChips(
                daysMask = draft.daysMask,
                onMaskChange = { draft = draft.copy(daysMask = it) },
            )

            SectionLabel(text = "Applies to", uppercase = true)
            ScopeField(
                label = appOptions.firstOrNull { it.packageName == draft.packageName }?.label
                    ?: ALL_APPS_LABEL,
                onClick = { showScopePicker = true },
            )

            Spacer(Modifier.size(4.dp))
            Button(
                onClick = {
                    onSave(draft)
                    onDismiss()
                },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(50),
            ) {
                Text("Save", style = MaterialTheme.typography.labelLarge)
            }

            if (!draft.isNew) {
                TextButton(
                    onClick = { confirmDelete = true },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(
                        Icons.Filled.DeleteOutline,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error,
                    )
                    Spacer(Modifier.size(8.dp))
                    Text("Delete schedule", color = MaterialTheme.colorScheme.error)
                }
            }
        }
    }
}

/** A tappable field showing a wall-clock time; opens the picker rather than accepting typed input. */
@Composable
private fun TimeField(
    label: String,
    minuteOfDay: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = formatMinuteOfDay(minuteOfDay),
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.primary,
        )
    }
}

@Composable
private fun ScopeField(label: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = "Change",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
        )
    }
}
