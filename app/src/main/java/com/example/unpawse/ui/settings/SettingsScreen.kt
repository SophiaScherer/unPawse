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
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Download
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.unpawse.ui.components.Chevron
import com.example.unpawse.ui.components.InitialsAvatar
import com.example.unpawse.ui.components.SectionLabel
import com.example.unpawse.ui.components.SettingsGroup
import com.example.unpawse.ui.components.SettingsRow
import com.example.unpawse.ui.components.ValueText
import com.example.unpawse.ui.navigation.SettingsRowIds
import com.example.unpawse.ui.theme.Dimens
import com.example.unpawse.ui.theme.UnPawseTheme

@Composable
fun SettingsScreen(
    state: SettingsUiState,
    modifier: Modifier = Modifier,
    onBack: () -> Unit = {},
    onSensitivityChange: (Float) -> Unit = {},
    onToggleDailySummary: (Boolean) -> Unit = {},
    onToggleDarkMode: (Boolean) -> Unit = {},
    onNameChange: (String) -> Unit = {},
    onRowClick: (String) -> Unit = {},
) {
    // Ephemeral UI state for the name-edit dialog; the value itself is persisted via onNameChange.
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
                        MaterialTheme.colorScheme.secondary
                    } else {
                        MaterialTheme.colorScheme.error
                    },
                    iconBackground = if (state.usageAccessGranted) {
                        MaterialTheme.colorScheme.secondaryContainer
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
                        MaterialTheme.colorScheme.secondary
                    } else {
                        MaterialTheme.colorScheme.error
                    },
                    iconBackground = if (state.overlayAccessGranted) {
                        MaterialTheme.colorScheme.secondaryContainer
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
                SettingsRow(
                    title = "Break duration", subtitle = state.breakDurationLabel,
                    leadingIcon = Icons.Filled.LocalCafe,
                    iconTint = MaterialTheme.colorScheme.tertiary,
                    iconBackground = MaterialTheme.colorScheme.tertiaryContainer,
                    onClick = { onRowClick(SettingsRowIds.BREAK_DURATION) }, trailing = { Chevron() },
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
                SettingsRow(
                    title = "Reminder frequency", leadingIcon = Icons.Filled.NotificationsActive,
                    onClick = { onRowClick(SettingsRowIds.REMINDER) },
                    trailing = { ValueText(state.reminderFrequency) },
                )
                SettingsRow(
                    title = "Warning before lock", leadingIcon = Icons.Filled.Warning,
                    onClick = { onRowClick(SettingsRowIds.WARNING) },
                    trailing = { ValueText(state.warningBeforeLock) },
                )
                SettingsRow(
                    title = "Daily summary", leadingIcon = Icons.Filled.Summarize,
                    trailing = { Switch(checked = state.dailySummaryEnabled, onCheckedChange = onToggleDailySummary) },
                )
            }
        }

        item {
            SectionLabel(text = "Privacy", uppercase = true)
            SettingsGroup {
                SettingsRow(
                    title = "Manage photos", leadingIcon = Icons.Filled.PhotoLibrary,
                    onClick = { onRowClick(SettingsRowIds.MANAGE_PHOTOS) }, trailing = { Chevron() },
                )
                SettingsRow(
                    title = "Export data", leadingIcon = Icons.Filled.Download,
                    onClick = { onRowClick(SettingsRowIds.EXPORT) }, trailing = { Chevron() },
                )
                SettingsRow(
                    title = "Delete history", leadingIcon = Icons.Filled.DeleteOutline,
                    iconTint = MaterialTheme.colorScheme.error,
                    titleColor = MaterialTheme.colorScheme.error,
                    onClick = { onRowClick(SettingsRowIds.DELETE_HISTORY) },
                )
            }
        }

        item {
            SectionLabel(text = "Appearance", uppercase = true)
            SettingsGroup {
                SettingsRow(
                    title = "Dark mode", leadingIcon = Icons.Filled.LightMode,
                    trailing = { Switch(checked = state.darkMode, onCheckedChange = onToggleDarkMode) },
                )
            }
        }

        item {
            SectionLabel(text = "About", uppercase = true)
            SettingsGroup {
                SettingsRow(title = "Version", trailing = { ValueText(state.versionLabel) })
                SettingsRow(
                    title = "Privacy Policy",
                    onClick = { onRowClick(SettingsRowIds.PRIVACY_POLICY) },
                    trailing = {
                        Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
                    },
                )
            }
        }
    }
}

@Composable
private fun SettingsHeader(userName: String, onBack: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onBack) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back",
                tint = MaterialTheme.colorScheme.onSurface)
        }
        Spacer(Modifier.width(4.dp))
        Text("Settings", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary, modifier = Modifier.weight(1f))
        InitialsAvatar(initial = avatarInitialFor(userName), size = 40.dp)
    }
}

/** First letter of the name, or a paw-friendly fallback when no name is set. Matches Home's avatar. */
private fun avatarInitialFor(userName: String): Char =
    userName.ifBlank { "friend" }.first().uppercaseChar()

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
private fun SettingsScreenPreviewContent(startDark: Boolean) {
    var dark by remember { mutableStateOf(startDark) }
    var summary by remember { mutableStateOf(false) }
    var sensitivity by remember { mutableFloatStateOf(0.65f) }
    SettingsScreen(
        state = SettingsUiState.sample(darkMode = dark).copy(
            dailySummaryEnabled = summary, sensitivity = sensitivity,
        ),
        onToggleDarkMode = { dark = it },
        onToggleDailySummary = { summary = it },
        onSensitivityChange = { sensitivity = it },
    )
}

@Preview(name = "Settings", showBackground = true, backgroundColor = 0xFFFFF8F8, heightDp = 1800)
@Composable
private fun SettingsScreenPreview() {
    UnPawseTheme { SettingsScreenPreviewContent(startDark = false) }
}

@Preview(name = "Settings · dark", showBackground = true, backgroundColor = 0xFF171213, heightDp = 1800)
@Composable
private fun SettingsScreenDarkPreview() {
    UnPawseTheme(darkTheme = true) { SettingsScreenPreviewContent(startDark = true) }
}
