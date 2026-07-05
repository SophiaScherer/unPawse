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

/**
 * Compact bar chart used inside the small "Trend" card. Bars are normalized to the tallest value
 * and drawn as rounded rectangles along the bottom.
 */
@Composable
fun MiniBarChart(
    values: List<Float>,
    modifier: Modifier = Modifier,
    barColor: Color = MaterialTheme.colorScheme.primary,
) {
    Canvas(modifier = modifier) {
        if (values.isEmpty()) return@Canvas
        val maxV = values.max().takeIf { it > 0f } ?: 1f
        val gap = 4.dp.toPx()
        val barWidth = (size.width - gap * (values.size - 1)) / values.size
        val radius = CornerRadius(barWidth / 3f, barWidth / 3f)

        values.forEachIndexed { i, v ->
            val barHeight = size.height * (v / maxV)
            val x = i * (barWidth + gap)
            drawRoundRect(
                color = barColor,
                topLeft = Offset(x, size.height - barHeight),
                size = Size(barWidth, barHeight),
                cornerRadius = radius,
            )
        }
    }
}
