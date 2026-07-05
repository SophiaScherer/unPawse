package com.example.unpawse.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

/**
 * Small stat chip used in the Home progress card (e.g. "45m / Remaining"). The [highlighted]
 * variant is the outlined blush-pink treatment the mockup gives the "Streak" pill.
 */
@Composable
fun StatPill(
    value: String,
    label: String,
    modifier: Modifier = Modifier,
    highlighted: Boolean = false,
) {
    val shape = RoundedCornerShape(16.dp)
    val container = if (highlighted) {
        MaterialTheme.colorScheme.surfaceContainerLowest
    } else {
        MaterialTheme.colorScheme.surfaceContainerLow
    }
    val borderModifier = if (highlighted) {
        Modifier.border(
            BorderStroke(1.5.dp, MaterialTheme.colorScheme.primaryContainer),
            shape,
        )
    } else {
        Modifier
    }

    Column(
        modifier = modifier
            .clip(shape)
            .background(container)
            .then(borderModifier)
            .padding(vertical = 14.dp, horizontal = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = value,
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.primary,
            textAlign = TextAlign.Center,
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}
