package com.example.unpawse.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.unit.dp

/** The rail is a background mark, not a reading — faint enough that a real bar reads as the data. */
private const val RAIL_ALPHA = 0.25f
private val RAIL_HEIGHT = 3.dp

/**
 * Compact bar chart used inside the small "Trend" card. Bars are normalized to the tallest value
 * and drawn as rounded rectangles along the bottom.
 *
 * Every slot draws a faint rail, and a bar is never shorter than it. Without that, a week with one
 * busy day rendered a lone full-height rectangle beside blank card — a stray mark rather than a
 * chart. A `null` value draws the rail alone: it is a slot with nothing to measure, such as a day
 * that hasn't happened, which a zero-height bar would have claimed was a day of no usage.
 */
@Composable
fun MiniBarChart(
    values: List<Float?>,
    modifier: Modifier = Modifier,
    barColor: Color = MaterialTheme.colorScheme.primary,
) {
    Canvas(modifier = modifier) {
        if (values.isEmpty()) return@Canvas
        val maxV = values.filterNotNull().maxOrNull()?.takeIf { it > 0f } ?: 1f
        val gap = 4.dp.toPx()
        val barWidth = (size.width - gap * (values.size - 1)) / values.size
        val radius = CornerRadius(barWidth / 3f, barWidth / 3f)
        val rail = RAIL_HEIGHT.toPx().coerceAtMost(size.height)

        values.forEachIndexed { i, v ->
            val x = i * (barWidth + gap)
            drawRoundRect(
                color = barColor.copy(alpha = RAIL_ALPHA),
                topLeft = Offset(x, size.height - rail),
                size = Size(barWidth, rail),
                cornerRadius = radius,
            )
            if (v == null) return@forEachIndexed
            val barHeight = (size.height * (v / maxV)).coerceAtLeast(rail)
            drawRoundRect(
                color = barColor,
                topLeft = Offset(x, size.height - barHeight),
                size = Size(barWidth, barHeight),
                cornerRadius = radius,
            )
        }
    }
}
