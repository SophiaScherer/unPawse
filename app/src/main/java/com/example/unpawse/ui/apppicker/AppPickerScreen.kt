package com.example.unpawse.ui.apppicker

import androidx.compose.foundation.background
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
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalTextStyle
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.unpawse.ui.components.PawCard
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
    onToggleMonitored: (AppLimitItem, Boolean) -> Unit = { _, _ -> },
    onLimitChange: (AppLimitItem, Int) -> Unit = { _, _ -> },
) {
    Column(modifier = modifier.fillMaxWidth()) {
        AppPickerHeader(monitoredCount = state.monitoredCount, onBack = onBack)

        SearchField(
            query = state.searchQuery,
            onQueryChange = onSearchChange,
            modifier = Modifier.padding(horizontal = Dimens.ScreenHMargin),
        )

        Spacer(Modifier.size(Dimens.StackGap))

        when {
            state.isLoading -> LoadingState()
            state.apps.isEmpty() -> EmptyState(hasQuery = state.searchQuery.isNotBlank())
            else -> LazyColumn(
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
                        onToggleMonitored = { onToggleMonitored(app, it) },
                        onLimitChange = { onLimitChange(app, it) },
                    )
                }
            }
        }
    }
}

@Composable
private fun AppPickerHeader(monitoredCount: Int, onBack: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Dimens.ScreenHMargin - 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onBack) {
            Icon(
                Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "Back",
                tint = MaterialTheme.colorScheme.onSurface,
            )
        }
        Spacer(Modifier.width(4.dp))
        Column(Modifier.weight(1f)) {
            Text(
                "App limits",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
            )
            Text(
                if (monitoredCount == 0) "No apps limited yet" else "$monitoredCount app${if (monitoredCount == 1) "" else "s"} limited",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** Functional search field styled to match the Gallery's (decorative) search bar. */
@Composable
private fun SearchField(
    query: String,
    onQueryChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            Icons.Filled.Search,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.size(12.dp))
        Box(Modifier.weight(1f)) {
            BasicTextField(
                value = query,
                onValueChange = onQueryChange,
                singleLine = true,
                textStyle = LocalTextStyle.current.merge(
                    MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurface),
                ),
                cursorBrush = androidx.compose.ui.graphics.SolidColor(MaterialTheme.colorScheme.primary),
                modifier = Modifier.fillMaxWidth(),
            )
            if (query.isEmpty()) {
                Text(
                    "Search apps...",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/** One app: icon, name, monitor switch, and (when monitored) a daily-limit stepper. */
@Composable
private fun AppLimitRow(
    item: AppLimitItem,
    onToggleMonitored: (Boolean) -> Unit,
    onLimitChange: (Int) -> Unit,
) {
    PawCard(contentPadding = 12.dp) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            AppIcon(packageName = item.packageName, label = item.label)
            Spacer(Modifier.width(16.dp))
            Text(
                text = item.label,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Spacer(Modifier.width(12.dp))
            Switch(checked = item.monitored, onCheckedChange = onToggleMonitored)
        }

        if (item.monitored) {
            Spacer(Modifier.size(8.dp))
            LimitStepper(
                minutes = item.dailyLimitMinutes,
                onChange = onLimitChange,
            )
        }
    }
}

@Composable
private fun LimitStepper(minutes: Int, onChange: (Int) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.primaryContainer)
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            "Daily limit",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onPrimaryContainer,
            modifier = Modifier.weight(1f),
        )
        StepperButton(
            icon = Icons.Filled.Remove,
            contentDescription = "Decrease limit",
            enabled = minutes > MIN_LIMIT_MINUTES,
            onClick = { onChange(adjustLimit(minutes, -1)) },
        )
        Text(
            text = formatMinutes(minutes),
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onPrimaryContainer,
            modifier = Modifier.width(64.dp),
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        )
        StepperButton(
            icon = Icons.Filled.Add,
            contentDescription = "Increase limit",
            enabled = minutes < MAX_LIMIT_MINUTES,
            onClick = { onChange(adjustLimit(minutes, +1)) },
        )
    }
}

@Composable
private fun StepperButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    contentDescription: String,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    IconButton(onClick = onClick, enabled = enabled, modifier = Modifier.size(32.dp)) {
        Icon(
            icon,
            contentDescription = contentDescription,
            tint = if (enabled) {
                MaterialTheme.colorScheme.onPrimaryContainer
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f)
            },
            modifier = Modifier.size(18.dp),
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
