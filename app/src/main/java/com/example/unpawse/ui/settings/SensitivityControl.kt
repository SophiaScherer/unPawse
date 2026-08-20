package com.example.unpawse.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pets
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.unpawse.ui.components.IconTile
import com.example.unpawse.ui.components.ValueText

/**
 * Discrete values between the slider's endpoints, giving five stops (0, ¼, ½, ¾, 1) that map to a
 * round 90/80/70/60/50% confidence gate. A continuous slider made the setting unreproducible — two
 * users saying "I set it to medium" could be a percent apart.
 */
private const val SENSITIVITY_STEPS = 3

/**
 * The cat-detection sensitivity slider, with the confidence gate it produces spelled out.
 *
 * The gate used to be advertised by a separate "Confidence threshold" row showing a fabricated
 * "85% minimum match" — a number nothing computed, sitting directly beneath the control that
 * actually sets it. The readout here is derived from the live slider position instead.
 *
 * Stateless from the caller's point of view, but it holds the in-flight drag locally and only
 * reports on release: [onSensitivityChange] writes to DataStore, and the previous wiring called it
 * on every drag frame.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SensitivityControl(
    sensitivity: Float,
    onSensitivityChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
) {
    // Re-keyed on the persisted value so an external change is picked up, but left alone mid-drag.
    var dragged by remember(sensitivity) { mutableFloatStateOf(sensitivity) }

    Column(modifier.padding(horizontal = 12.dp, vertical = 12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconTile(Icons.Filled.Pets)
            Spacer(Modifier.width(16.dp))
            Text(
                text = "Sensitivity",
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f),
            )
            ValueText(minConfidenceLabel(dragged))
        }
        Text(
            text = "How sure unPawse must be before a photo counts as a cat",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        // M3 defaults paint the unfilled track `secondaryContainer` — bright green — and cap it with
        // a stop indicator in the *active* colour, so the remainder read as a second value with its
        // own marker rather than as the empty part of one bar.
        val sliderColors = SliderDefaults.colors(
            inactiveTrackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
        )
        Slider(
            value = dragged,
            onValueChange = { dragged = it },
            onValueChangeFinished = { onSensitivityChange(dragged) },
            colors = sliderColors,
            steps = SENSITIVITY_STEPS,
            modifier = Modifier.padding(top = 4.dp),
            track = { state ->
                SliderDefaults.Track(sliderState = state, colors = sliderColors, drawStopIndicator = null)
            },
        )
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("Low", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("Medium", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("High", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
