package com.example.unpawse.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.unpawse.ui.theme.UnPawseTheme

/**
 * A pill that selects one option out of a visible few, checked when chosen.
 *
 * Lifted out of GalleryScreen so the App Picker's sort chips read as the same control rather than a
 * second pill with its own idea of the shape. Selection is the plum accent, never green — see the
 * `success` note in AGENTS.md.
 */
@Composable
fun FilterChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val container = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh
    val content = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(50))
            // Role.RadioButton, because these are one-of-N: it is what makes a screen reader say
            // which chip is currently selected rather than reading four unrelated buttons.
            .clickable(onClick = onClick, role = Role.RadioButton)
            .background(container)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (selected) {
            Icon(Icons.Filled.Check, contentDescription = null, tint = content, modifier = Modifier.size(16.dp))
            Spacer(Modifier.size(6.dp))
        }
        Text(
            label,
            style = MaterialTheme.typography.labelLarge,
            color = content,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
        )
    }
}

@Preview(showBackground = true, backgroundColor = 0xFFFFF8F8)
@Composable
private fun FilterChipPreview() {
    UnPawseTheme {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(label = "A–Z", selected = true, onClick = {})
            FilterChip(label = "Most used", selected = false, onClick = {})
        }
    }
}
