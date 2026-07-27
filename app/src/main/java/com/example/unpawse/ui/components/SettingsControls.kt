package com.example.unpawse.ui.components

import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * The two stock trailing slots for a [SettingsRow]: a "leads somewhere" chevron and a "current
 * value" label. Both were private to `SettingsScreen` until sub-screens (photo storage, app picker)
 * needed the same affordances — keeping one copy is what makes those screens look native to Settings.
 */
@Composable
fun Chevron(modifier: Modifier = Modifier) {
    Icon(
        imageVector = Icons.Filled.ChevronRight,
        contentDescription = null,
        tint = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier.size(24.dp),
    )
}

/** The current value of a setting, shown in the trailing slot (e.g. "Every 30m", "1.0 (1)"). */
@Composable
fun ValueText(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = modifier,
    )
}
