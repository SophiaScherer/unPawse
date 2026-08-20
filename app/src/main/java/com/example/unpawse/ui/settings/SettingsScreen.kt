package com.example.unpawse.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.LocalCafe
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Summarize
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.unpawse.data.settings.SettingsRepository
import com.example.unpawse.service.REMINDER_OFF
import com.example.unpawse.service.UsageTracker
import com.example.unpawse.ui.components.BackHeader
import com.example.unpawse.ui.components.Chevron
import com.example.unpawse.ui.components.ConfirmDialog
import com.example.unpawse.ui.components.InitialsAvatar
import com.example.unpawse.ui.components.OptionPickerDialog
import com.example.unpawse.ui.components.SectionLabel
import com.example.unpawse.ui.components.SettingsGroup
import com.example.unpawse.ui.components.SettingsRow
import com.example.unpawse.ui.theme.unPawseColors
import com.example.unpawse.ui.components.ValueText
import com.example.unpawse.ui.format.avatarInitialFor
import com.example.unpawse.ui.navigation.SettingsRowIds
import com.example.unpawse.ui.theme.Dimens
import com.example.unpawse.ui.theme.ThemeMode
import com.example.unpawse.ui.theme.UnPawseTheme

@Composable
fun SettingsScreen(
    state: SettingsUiState,
    modifier: Modifier = Modifier,
    onBack: () -> Unit = {},
    onSensitivityChange: (Float) -> Unit = {},
    onEarnedMinutesChange: (Int) -> Unit = {},
    onWarningMinutesChange: (Int) -> Unit = {},
    onReminderMinutesChange: (Int) -> Unit = {},
    onToggleDailySummary: (Boolean) -> Unit = {},
    onThemeModeChange: (ThemeMode) -> Unit = {},
    onEraseEverything: () -> Unit = {},
    onNameChange: (String) -> Unit = {},
    onRowClick: (String) -> Unit = {},
) {
    // Ephemeral UI state for the dialogs; the values themselves are persisted via the callbacks.
    var showNameDialog by remember { mutableStateOf(false) }
    if (showNameDialog) {
        NameEditDialog(
            currentName = state.userName,
            onConfirm = {
                onNameChange(it)
                showNameDialog = false
            },
            onDismiss = { showNameDialog = false },
        )
    }

    // The one action in the app that cannot be undone; the message enumerates what goes rather than
    // asking a bare "are you sure?".
    var showEraseDialog by remember { mutableStateOf(false) }
    if (showEraseDialog) {
        ConfirmDialog(
            title = "Delete everything?",
            message = "This removes your screen-time history, your app limits, every cat photo " +
                "(favourites included) and all your settings. unPawse goes back to how it was on " +
                "the day you installed it.\n\n" +
                "Export your data first if you want to keep a copy. This cannot be undone.",
            confirmLabel = "Delete everything",
            destructive = true,
            onConfirm = onEraseEverything,
            onDismiss = { showEraseDialog = false },
        )
    }

    // Confirmed before the picker opens, so the warning is read while backing out is still free.
    var showImportDialog by remember { mutableStateOf(false) }
    if (showImportDialog) {
        ConfirmDialog(
            title = "Replace everything?",
            message = "Importing overwrites your screen-time history, app limits, schedules, " +
                "photos and settings with whatever the file holds. Anything currently on this " +
                "device is deleted first.\n\n" +
                "Export your data first if you want to keep a copy. This cannot be undone.",
            confirmLabel = "Choose a file",
            destructive = true,
            onConfirm = { onRowClick(SettingsRowIds.IMPORT) },
            onDismiss = { showImportDialog = false },
        )
    }

    var showReminderDialog by remember { mutableStateOf(false) }
    if (showReminderDialog) {
        OptionPickerDialog(
            title = "Reminder frequency",
            options = SettingsRepository.REMINDER_MINUTE_CHOICES,
            selected = state.reminderMinutes,
            onSelect = onReminderMinutesChange,
            onDismiss = { showReminderDialog = false },
            label = { reminderLabel(it) },
            supporting = { minutes ->
                if (minutes <= REMINDER_OFF) {
                    "No check-ins"
                } else {
                    "Only while a limited app is open"
                }
            },
        )
    }

    var showWarningDialog by remember { mutableStateOf(false) }
    if (showWarningDialog) {
        OptionPickerDialog(
            title = "Warning before lock",
            options = SettingsRepository.WARNING_MINUTE_CHOICES,
            selected = state.warningMinutes,
            onSelect = onWarningMinutesChange,
            onDismiss = { showWarningDialog = false },
            label = { warningLabel(it) },
            supporting = { minutes ->
                "No warning — the block is the first you hear of it"
                    .takeIf { minutes <= UsageTracker.WARNING_OFF }
            },
        )
    }

    var showThemeDialog by remember { mutableStateOf(false) }
    if (showThemeDialog) {
        OptionPickerDialog(
            title = "Theme",
            options = ThemeMode.entries,
            selected = state.themeMode,
            onSelect = onThemeModeChange,
            onDismiss = { showThemeDialog = false },
            label = { it.label },
            supporting = { mode ->
                "Matches your device setting".takeIf { mode == ThemeMode.SYSTEM }
            },
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
        item { SettingsHeader(state.userName, onBack) }

        item {
            SectionLabel(text = "Profile", uppercase = true)
            SettingsGroup {
                SettingsRow(
                    title = "Your name",
                    subtitle = state.userName.ifBlank { "Not set — tap to add" },
                    leadingIcon = Icons.Filled.Person,
                    iconTint = MaterialTheme.colorScheme.primary,
                    iconBackground = MaterialTheme.colorScheme.primaryContainer,
                    onClick = { showNameDialog = true },
                    trailing = { Chevron() },
                )
            }
        }

        item {
            SectionLabel(text = "Screen Time", uppercase = true)
            SettingsGroup {
                // Nothing can be monitored without usage access, so surface it first and loudly
                // when it's missing.
                SettingsRow(
                    title = "Screen time access",
                    subtitle = if (state.usageAccessGranted) {
                        "Granted — limits are being watched"
                    } else {
                        "Required — tap to allow unPawse to see your app usage"
                    },
                    leadingIcon = if (state.usageAccessGranted) Icons.Filled.Shield else Icons.Filled.Warning,
                    iconTint = if (state.usageAccessGranted) {
                        MaterialTheme.unPawseColors.success
                    } else {
                        MaterialTheme.colorScheme.error
                    },
                    iconBackground = if (state.usageAccessGranted) {
                        MaterialTheme.unPawseColors.successContainer
                    } else {
                        MaterialTheme.colorScheme.errorContainer
                    },
                    onClick = { onRowClick(SettingsRowIds.USAGE_ACCESS) },
                    trailing = { Chevron() },
                )
                SettingsRow(
                    title = "Display over other apps",
                    subtitle = if (state.overlayAccessGranted) {
                        "Granted — breaks can interrupt you"
                    } else {
                        "Required — without it a reached limit can't block"
                    },
                    leadingIcon = if (state.overlayAccessGranted) Icons.Filled.Shield else Icons.Filled.Warning,
                    iconTint = if (state.overlayAccessGranted) {
                        MaterialTheme.unPawseColors.success
                    } else {
                        MaterialTheme.colorScheme.error
                    },
                    iconBackground = if (state.overlayAccessGranted) {
                        MaterialTheme.unPawseColors.successContainer
                    } else {
                        MaterialTheme.colorScheme.errorContainer
                    },
                    onClick = { onRowClick(SettingsRowIds.OVERLAY_ACCESS) },
                    trailing = { Chevron() },
                )
                // Informational: the total is derived from the per-app limits below, so there is
                // nothing to edit here — no onClick, no chevron.
                SettingsRow(
                    title = "Total daily limit", subtitle = state.dailyLimitLabel,
                    leadingIcon = Icons.Filled.Timer,
                    iconTint = MaterialTheme.colorScheme.primary,
                    iconBackground = MaterialTheme.colorScheme.primaryContainer,
                )
                SettingsRow(
                    title = "Individual app limits", subtitle = state.appLimitsSummary,
                    leadingIcon = Icons.Filled.Apps,
                    iconTint = MaterialTheme.colorScheme.secondary,
                    iconBackground = MaterialTheme.colorScheme.secondaryContainer,
                    onClick = { onRowClick(SettingsRowIds.APP_LIMITS) }, trailing = { Chevron() },
                )
                // The other half of a limit: not how much, but when. Sits beside the per-app
                // budgets because that's the row a user compares it against.
                SettingsRow(
                    title = "Blocking schedules", subtitle = state.schedulesSummary,
                    leadingIcon = Icons.Filled.Bedtime,
                    iconTint = MaterialTheme.colorScheme.tertiary,
                    iconBackground = MaterialTheme.colorScheme.tertiaryContainer,
                    onClick = { onRowClick(SettingsRowIds.SCHEDULES) }, trailing = { Chevron() },
                )
                EarnedTimeControl(
                    minutesPerCat = state.earnedMinutesPerCat,
                    onMinutesChange = onEarnedMinutesChange,
                )
            }
        }

        item {
            SectionLabel(text = "Cat Detection", uppercase = true)
            SettingsGroup {
                SensitivityControl(
                    sensitivity = state.sensitivity,
                    onSensitivityChange = onSensitivityChange,
                )
            }
        }

        item {
            SectionLabel(text = "Notifications", uppercase = true)
            SettingsGroup {
                // Nothing below can reach the user without this, so it leads the group — the same
                // treatment the two screen-time permissions get.
                SettingsRow(
                    title = "Notification access",
                    subtitle = if (state.notificationsGranted) {
                        "Granted — unPawse can reach you"
                    } else {
                        "Required — tap to let unPawse send notifications"
                    },
                    leadingIcon = if (state.notificationsGranted) Icons.Filled.Shield else Icons.Filled.Warning,
                    iconTint = if (state.notificationsGranted) {
                        MaterialTheme.unPawseColors.success
                    } else {
                        MaterialTheme.colorScheme.error
                    },
                    iconBackground = if (state.notificationsGranted) {
                        MaterialTheme.unPawseColors.successContainer
                    } else {
                        MaterialTheme.colorScheme.errorContainer
                    },
                    onClick = { onRowClick(SettingsRowIds.NOTIFICATION_ACCESS) },
                    trailing = { Chevron() },
                )
                SettingsRow(
                    title = "Reminder frequency",
                    subtitle = "Check in while you're using a limited app",
                    leadingIcon = Icons.Filled.NotificationsActive,
                    enabled = state.notificationsGranted,
                    onClick = { showReminderDialog = true },
                    trailing = { ValueText(state.reminderFrequency) },
                )
                SettingsRow(
                    title = "Warning before lock",
                    subtitle = "A heads-up before an app runs out",
                    leadingIcon = Icons.Filled.Warning,
                    enabled = state.notificationsGranted,
                    onClick = { showWarningDialog = true },
                    trailing = { ValueText(state.warningBeforeLock) },
                )
                SettingsRow(
                    title = "Daily summary",
                    subtitle = "A recap of your screen time each evening",
                    leadingIcon = Icons.Filled.Summarize,
                    enabled = state.notificationsGranted,
                    trailing = {
                        Switch(
                            checked = state.dailySummaryEnabled,
                            onCheckedChange = onToggleDailySummary,
                            enabled = state.notificationsGranted,
                        )
                    },
                )
            }
        }

        item {
            SectionLabel(text = "Privacy", uppercase = true)
            SettingsGroup {
                SettingsRow(
                    title = "Manage photos", subtitle = state.photosSummary,
                    leadingIcon = Icons.Filled.PhotoLibrary,
                    onClick = { onRowClick(SettingsRowIds.MANAGE_PHOTOS) }, trailing = { Chevron() },
                )
                SettingsRow(
                    title = "Export data",
                    subtitle = "Save your settings, limits, history and photos to a file",
                    leadingIcon = Icons.Filled.Download,
                    onClick = { onRowClick(SettingsRowIds.EXPORT) }, trailing = { Chevron() },
                )
                SettingsRow(
                    title = "Import data",
                    subtitle = "Restore from an unPawse export — replaces everything",
                    leadingIcon = Icons.Filled.Upload,
                    onClick = { showImportDialog = true }, trailing = { Chevron() },
                )
                SettingsRow(
                    title = "Delete all data",
                    subtitle = "History, limits, photos and settings",
                    leadingIcon = Icons.Filled.DeleteOutline,
                    iconTint = MaterialTheme.colorScheme.error,
                    iconBackground = MaterialTheme.colorScheme.errorContainer,
                    titleColor = MaterialTheme.colorScheme.error,
                    onClick = { showEraseDialog = true },
                )
            }
        }

        item {
            SectionLabel(text = "Appearance", uppercase = true)
            SettingsGroup {
                SettingsRow(
                    title = "Theme", leadingIcon = Icons.Filled.LightMode,
                    onClick = { showThemeDialog = true },
                    trailing = { ValueText(state.themeMode.label) },
                )
            }
        }

        item {
            SectionLabel(text = "About", uppercase = true)
            SettingsGroup {
                SettingsRow(title = "Version", trailing = { ValueText(state.versionLabel) })
                // Chevron, not "open in new": the policy is a screen in this app, not a web link.
                SettingsRow(
                    title = "Privacy Policy",
                    onClick = { onRowClick(SettingsRowIds.PRIVACY_POLICY) },
                    trailing = { Chevron() },
                )
            }
        }
    }
}

@Composable
private fun SettingsHeader(userName: String, onBack: () -> Unit) {
    BackHeader(
        title = "Settings",
        onBack = onBack,
        trailing = { InitialsAvatar(initial = avatarInitialFor(userName), size = 40.dp) },
    )
}

/** Simple single-field dialog for the display name; seeds the field with the current value. */
@Composable
private fun NameEditDialog(
    currentName: String,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var text by remember { mutableStateOf(currentName) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Your name") },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                singleLine = true,
                label = { Text("Name") },
            )
        },
        confirmButton = { TextButton(onClick = { onConfirm(text) }) { Text("Save") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

/** Interactive preview body: the toggles/slider actually respond so the controls can be eyeballed. */
@Composable
private fun SettingsScreenPreviewContent(startMode: ThemeMode) {
    var theme by remember { mutableStateOf(startMode) }
    var summary by remember { mutableStateOf(false) }
    var sensitivity by remember { mutableFloatStateOf(0.65f) }
    var earnedMinutes by remember { mutableIntStateOf(15) }
    SettingsScreen(
        state = SettingsUiState.sample(themeMode = theme).copy(
            dailySummaryEnabled = summary,
            sensitivity = sensitivity,
            earnedMinutesPerCat = earnedMinutes,
        ),
        onThemeModeChange = { theme = it },
        onToggleDailySummary = { summary = it },
        onSensitivityChange = { sensitivity = it },
        onEarnedMinutesChange = { earnedMinutes = it },
    )
}

@Preview(name = "Settings", showBackground = true, backgroundColor = 0xFFFFF8F8, heightDp = 1800)
@Composable
private fun SettingsScreenPreview() {
    UnPawseTheme { SettingsScreenPreviewContent(startMode = ThemeMode.LIGHT) }
}

@Preview(name = "Settings · dark", showBackground = true, backgroundColor = 0xFF171213, heightDp = 1800)
@Composable
private fun SettingsScreenDarkPreview() {
    UnPawseTheme(darkTheme = true) { SettingsScreenPreviewContent(startMode = ThemeMode.DARK) }
}
