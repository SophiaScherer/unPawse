package com.example.unpawse.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** One slice of a [DonutChart]: a raw [value] (proportions are computed) and its [color]. */
data class DonutSegment(val value: Float, val color: Color)

/**
 * Ring-style breakdown chart (Usage Breakdown card). Each segment is a rounded arc separated by a
 * small gap. [content] is centered in the hole (the "75% / Productive" label).
 */
@Composable
fun DonutChart(
    segments: List<DonutSegment>,
    modifier: Modifier = Modifier,
    strokeWidth: Dp = 28.dp,
    gapDegrees: Float = 4f,
    trackColor: Color = MaterialTheme.colorScheme.surfaceContainerHigh,
    content: @Composable () -> Unit,
) {
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Canvas(modifier = Modifier.matchParentSize()) {
            val inset = strokeWidth.toPx() / 2f
            val arcSize = Size(size.width - strokeWidth.toPx(), size.height - strokeWidth.toPx())
            val topLeft = Offset(inset, inset)
            val total = segments.sumOf { it.value.toDouble() }.toFloat()
            if (total <= 0f) {
                // An empty ring rather than nothing: a bare "0m" floating in a blank card read as a
                // chart that failed to draw.
                drawArc(trackColor, -90f, 360f, false, topLeft, arcSize, style = Stroke(width = strokeWidth.toPx()))
                return@Canvas
            }
            val stroke = Stroke(width = strokeWidth.toPx(), cap = StrokeCap.Round)

            val drawn = segments.filter { it.value > 0f }
            if (drawn.size == 1) {
                // A whole ring has no ends, so no caps and no gap to leave.
                drawArc(drawn.single().color, -90f, 360f, false, topLeft, arcSize,
                    style = Stroke(width = strokeWidth.toPx()))
                return@Canvas
            }
            // A round cap reaches half the stroke past the arc's end, which at this ring size is
            // wider than the gap; without trimming it, each cap was drawn over its neighbor's start.
            val capDegrees = Math.toDegrees((inset / (arcSize.width / 2f)).toDouble()).toFloat()

            var startAngle = -90f
            drawn.forEach { segment ->
                val fullSweep = segment.value / total * 360f
                // A sliver too short for its own caps still draws as a dot rather than vanishing.
                val sweep = (fullSweep - gapDegrees - 2 * capDegrees).coerceAtLeast(0.1f)
                drawArc(
                    color = segment.color,
                    startAngle = startAngle + (fullSweep - sweep) / 2f,
                    sweepAngle = sweep,
                    useCenter = false,
                    topLeft = topLeft,
                    size = arcSize,
                    style = stroke,
                )
                startAngle += fullSweep
            }
        }
        content()
    }
}
