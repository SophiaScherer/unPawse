package com.example.unpawse.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

/**
 * A labelled −/value/+ pill for an integer setting that moves in fixed increments. Promoted out of
 * `AppPickerScreen` (where it was the per-app daily-limit stepper) so Settings rows can use the same
 * control; [step]/[min]/[max] and [format] are what used to be hardcoded to 15-minute limits.
 *
 * Stateless: it renders [value] and reports the next value through [onChange], already clamped.
 */
@Composable
fun ValueStepper(
    label: String,
    value: Int,
    onChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
    step: Int = 1,
    min: Int = Int.MIN_VALUE,
    max: Int = Int.MAX_VALUE,
    format: (Int) -> String = { it.toString() },
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.primaryContainer)
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onPrimaryContainer,
            modifier = Modifier.weight(1f),
        )
        StepperButton(
            icon = Icons.Filled.Remove,
            contentDescription = "Decrease $label",
            enabled = value > min,
            onClick = { onChange(steppedValue(value, -1, step, min, max)) },
        )
        Text(
            text = format(value),
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onPrimaryContainer,
            modifier = Modifier.width(64.dp),
            textAlign = TextAlign.Center,
        )
        StepperButton(
            icon = Icons.Filled.Add,
            contentDescription = "Increase $label",
            enabled = value < max,
            onClick = { onChange(steppedValue(value, +1, step, min, max)) },
        )
    }
}

@Composable
private fun StepperButton(
    icon: ImageVector,
    contentDescription: String,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    IconButton(onClick = onClick, enabled = enabled, modifier = Modifier.size(32.dp)) {
        Icon(
            imageVector = icon,
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

/**
 * Moves [current] by [deltaSteps] increments of [step], clamped to `[min, max]`. Pure, so the
 * clamping rule is unit-tested without a device (see `LimitFormatTest`).
 */
fun steppedValue(current: Int, deltaSteps: Int, step: Int, min: Int, max: Int): Int =
    (current + deltaSteps * step).coerceIn(min, max)
