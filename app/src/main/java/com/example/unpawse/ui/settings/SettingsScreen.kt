package com.example.unpawse.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.LiveTv
import androidx.compose.material.icons.filled.LocalCafe
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Pets
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.Summarize
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.unpawse.ui.components.InitialsAvatar
import com.example.unpawse.ui.components.SectionLabel
import com.example.unpawse.ui.components.SettingsGroup
import com.example.unpawse.ui.components.SettingsRow
import com.example.unpawse.ui.theme.Dimens
import com.example.unpawse.ui.theme.UnPawseTheme

@Composable
fun SettingsScreen(
    state: SettingsUiState,
    modifier: Modifier = Modifier,
    onBack: () -> Unit = {},
    onSensitivityChange: (Float) -> Unit = {},
    onToggleLivePhoto: (Boolean) -> Unit = {},
    onToggleDailySummary: (Boolean) -> Unit = {},
    onToggleDarkMode: (Boolean) -> Unit = {},
    onRowClick: (String) -> Unit = {},
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
        item { SettingsHeader(onBack) }

        item {
            SectionLabel(text = "Screen Time", uppercase = true)
            SettingsGroup {
                SettingsRow(
                    title = "Daily limit", subtitle = state.dailyLimitLabel,
                    leadingIcon = Icons.Filled.Timer,
                    iconTint = MaterialTheme.colorScheme.primary,
                    iconBackground = MaterialTheme.colorScheme.primaryContainer,
                    onClick = { onRowClick("daily_limit") }, trailing = { Chevron() },
                )
                SettingsRow(
                    title = "Individual app limits", subtitle = state.appLimitsSummary,
                    leadingIcon = Icons.Filled.Apps,
                    iconTint = MaterialTheme.colorScheme.secondary,
                    iconBackground = MaterialTheme.colorScheme.secondaryContainer,
                    onClick = { onRowClick("app_limits") }, trailing = { Chevron() },
                )
                SettingsRow(
                    title = "Break duration", subtitle = state.breakDurationLabel,
                    leadingIcon = Icons.Filled.LocalCafe,
                    iconTint = MaterialTheme.colorScheme.tertiary,
                    iconBackground = MaterialTheme.colorScheme.tertiaryContainer,
                    onClick = { onRowClick("break_duration") }, trailing = { Chevron() },
                )
            }
        }

        item {
            SectionLabel(text = "Cat Detection", uppercase = true)
            SettingsGroup {
                Column(Modifier.padding(horizontal = 12.dp, vertical = 12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconTile(Icons.Filled.Pets)
                        Spacer(Modifier.width(16.dp))
                        Text("Sensitivity", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
                    }
                    Slider(
                        value = state.sensitivity,
                        onValueChange = onSensitivityChange,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Low", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("Medium", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("High", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                SettingsRow(
                    title = "Require live photo",
                    subtitle = "Increases security, uses more battery",
                    leadingIcon = Icons.Filled.LiveTv,
                    trailing = { Switch(checked = state.requireLivePhoto, onCheckedChange = onToggleLivePhoto) },
                )
                SettingsRow(
                    title = "Confidence threshold", subtitle = state.confidenceLabel,
                    leadingIcon = Icons.Filled.Psychology,
                    onClick = { onRowClick("confidence") }, trailing = { Chevron() },
                )
            }
        }

        item {
            SectionLabel(text = "Notifications", uppercase = true)
            SettingsGroup {
                SettingsRow(
                    title = "Reminder frequency", leadingIcon = Icons.Filled.NotificationsActive,
                    onClick = { onRowClick("reminder") }, trailing = { ValueText(state.reminderFrequency) },
                )
                SettingsRow(
                    title = "Warning before lock", leadingIcon = Icons.Filled.Warning,
                    onClick = { onRowClick("warning") }, trailing = { ValueText(state.warningBeforeLock) },
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
                    onClick = { onRowClick("manage_photos") }, trailing = { Chevron() },
                )
                SettingsRow(
                    title = "Export data", leadingIcon = Icons.Filled.Download,
                    onClick = { onRowClick("export") }, trailing = { Chevron() },
                )
                SettingsRow(
                    title = "Delete history", leadingIcon = Icons.Filled.DeleteOutline,
                    iconTint = MaterialTheme.colorScheme.error,
                    titleColor = MaterialTheme.colorScheme.error,
                    onClick = { onRowClick("delete_history") },
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
                    onClick = { onRowClick("privacy_policy") },
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
private fun SettingsHeader(onBack: () -> Unit) {
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
        InitialsAvatar(initial = 'S', size = 40.dp)
    }
}

@Composable
private fun Chevron() {
    Icon(Icons.Filled.ChevronRight, contentDescription = null,
        tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(24.dp))
}

@Composable
private fun ValueText(text: String) {
    Text(text, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
}

@Composable
private fun IconTile(icon: ImageVector) {
    Box(
        modifier = Modifier
            .size(40.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHigh),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(22.dp))
    }
}

/** Interactive preview body: the toggles/slider actually respond so the controls can be eyeballed. */
@Composable
private fun SettingsScreenPreviewContent(startDark: Boolean) {
    var dark by remember { mutableStateOf(startDark) }
    var live by remember { mutableStateOf(false) }
    var summary by remember { mutableStateOf(false) }
    var sensitivity by remember { mutableFloatStateOf(0.65f) }
    SettingsScreen(
        state = SettingsUiState.sample(darkMode = dark).copy(
            requireLivePhoto = live, dailySummaryEnabled = summary, sensitivity = sensitivity,
        ),
        onToggleDarkMode = { dark = it },
        onToggleLivePhoto = { live = it },
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
