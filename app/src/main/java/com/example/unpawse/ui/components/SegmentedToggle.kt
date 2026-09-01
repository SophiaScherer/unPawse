package com.example.unpawse.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.BiasAlignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.unpawse.ui.theme.UnPawseTheme

/**
 * A one-of-N switch drawn as a sliding thumb inside a pill track.
 *
 * Distinct from [FilterChip], and the distinction is what each says about its options. Chips are a
 * row of independent-looking pills that happen to be exclusive, which suits a filter the user may
 * not think about often. This reads as a single control with a current position — right for a
 * setting that changes what every figure below it means, where the alternative has to stay visible
 * rather than being something to go and look for.
 *
 * Full width by default: it is a page-level control, not something tucked into a card's corner.
 */
@Composable
fun SegmentedToggle(
    labels: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (labels.isEmpty()) return

    val count = labels.size
    // Animating the position rather than swapping two backgrounds is the whole point of the shape:
    // the thumb moving is what tells the user the two options are one setting.
    val position by animateFloatAsState(
        targetValue = selectedIndex.coerceIn(0, count - 1).toFloat(),
        label = "segmentedTogglePosition",
    )

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(TOGGLE_HEIGHT)
            .clip(RoundedCornerShape(percent = 50))
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .padding(TRACK_INSET),
    ) {
        // The thumb is one slot wide and slides across the remaining track. Bias runs -1..1 over
        // that travel, so with two options -1 is fully left and 1 fully right.
        val travel = (count - 1).coerceAtLeast(1)
        Box(
            modifier = Modifier
                .align(BiasAlignment(horizontalBias = 2f * position / travel - 1f, verticalBias = 0f))
                .fillMaxWidth(1f / count)
                .fillMaxHeight()
                .clip(RoundedCornerShape(percent = 50))
                .background(MaterialTheme.colorScheme.primaryContainer),
        )

        Row(modifier = Modifier.fillMaxSize()) {
            labels.forEachIndexed { index, label ->
                val selected = index == selectedIndex
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(percent = 50))
                        // `selectable` rather than `clickable`: it announces which option is
                        // currently chosen, which a row of plain buttons cannot.
                        .selectable(
                            selected = selected,
                            onClick = { onSelect(index) },
                            role = Role.RadioButton,
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = label,
                        style = MaterialTheme.typography.labelLarge,
                        color = if (selected) {
                            MaterialTheme.colorScheme.onPrimaryContainer
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                        maxLines = 1,
                        // A long label in a narrow slot must not wrap the track out of shape.
                        overflow = TextOverflow.Ellipsis,
                        textAlign = TextAlign.Center,
                    )
                }
            }
        }
    }
}

/** Tall enough for a comfortable touch target without reading as a button bar. */
private val TOGGLE_HEIGHT = 44.dp

/** Keeps the thumb clear of the track's edge so the pill reads as a groove. */
private val TRACK_INSET = 4.dp

@Preview(showBackground = true, backgroundColor = 0xFFFFF8F8)
@Composable
private fun SegmentedTogglePreview() {
    UnPawseTheme {
        SegmentedToggle(
            labels = listOf("Tracked apps", "All apps"),
            selectedIndex = 0,
            onSelect = {},
            modifier = Modifier.padding(16.dp),
        )
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF171213)
@Composable
private fun SegmentedToggleDarkPreview() {
    UnPawseTheme(darkTheme = true) {
        SegmentedToggle(
            labels = listOf("Tracked apps", "All apps"),
            selectedIndex = 1,
            onSelect = {},
            modifier = Modifier.padding(16.dp),
        )
    }
}
