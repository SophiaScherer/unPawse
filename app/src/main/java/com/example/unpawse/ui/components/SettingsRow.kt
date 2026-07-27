package com.example.unpawse.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp

/** Material's standard dimming for an unavailable control. */
private const val DISABLED_ALPHA = 0.38f

/**
 * Groups related [SettingsRow]s inside a single [PawCard], matching the mockup's Settings screen.
 * Rows are separated by whitespace, not dividers (per DESIGN.md's "minimal, clean" list guidance).
 */
@Composable
fun SettingsGroup(
    modifier: Modifier = Modifier,
    content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit,
) {
    PawCard(modifier = modifier, contentPadding = 8.dp) {
        content()
    }
}

/**
 * One settings entry: an optional tinted icon tile, a title with optional subtitle, and a trailing
 * slot (chevron, [androidx.compose.material3.Switch], value text, …). Supply [onClick] for navigable
 * rows; leave it null for rows whose only control is in the trailing slot.
 */
@Composable
fun SettingsRow(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    leadingIcon: ImageVector? = null,
    iconTint: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    iconBackground: Color = MaterialTheme.colorScheme.surfaceContainerHigh,
    titleColor: Color = MaterialTheme.colorScheme.onSurface,
    enabled: Boolean = true,
    onClick: (() -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
) {
    val rowModifier = if (onClick != null && enabled) {
        modifier
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
    } else {
        modifier
    }

    // Dimmed rather than hidden: a setting that vanishes when unavailable is harder to find again
    // than one that is visibly waiting on something.
    val contentAlpha = if (enabled) 1f else DISABLED_ALPHA

    Row(
        modifier = rowModifier
            .alpha(contentAlpha)
            .padding(horizontal = 12.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (leadingIcon != null) {
            IconTile(icon = leadingIcon, tint = iconTint, background = iconBackground)
            Spacer(Modifier.width(16.dp))
        }

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                color = titleColor,
            )
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        if (trailing != null) {
            Spacer(Modifier.width(12.dp))
            trailing()
        }
    }
}
