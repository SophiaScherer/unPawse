package com.example.unpawse.ui.components

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/**
 * Group/section heading. Used two ways in the mockup:
 *  - Bold sentence-case section titles ("Quick Actions", "Recent Activity", "Today").
 *  - Uppercase muted group labels in Settings ("SCREEN TIME", "PRIVACY") — pass [uppercase] = true.
 */
@Composable
fun SectionLabel(
    text: String,
    modifier: Modifier = Modifier,
    uppercase: Boolean = false,
    color: Color = if (uppercase) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.onSurface
    },
) {
    if (uppercase) {
        Text(
            text = text.uppercase(),
            style = MaterialTheme.typography.labelMedium,
            color = color,
            modifier = modifier.padding(start = 4.dp, bottom = 8.dp),
        )
    } else {
        Text(
            text = text,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = color,
            modifier = modifier.padding(vertical = 4.dp),
        )
    }
}
