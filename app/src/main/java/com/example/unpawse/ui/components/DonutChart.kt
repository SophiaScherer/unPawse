package com.example.unpawse.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
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
    content: @Composable () -> Unit,
) {
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Canvas(modifier = Modifier.matchParentSize()) {
            val total = segments.sumOf { it.value.toDouble() }.toFloat().takeIf { it > 0f } ?: return@Canvas
            val stroke = Stroke(width = strokeWidth.toPx(), cap = StrokeCap.Round)
            val inset = strokeWidth.toPx() / 2f
            val arcSize = Size(size.width - strokeWidth.toPx(), size.height - strokeWidth.toPx())
            val topLeft = Offset(inset, inset)

            var startAngle = -90f
            segments.forEach { segment ->
                val fullSweep = segment.value / total * 360f
                val sweep = (fullSweep - gapDegrees).coerceAtLeast(0f)
                drawArc(
                    color = segment.color,
                    startAngle = startAngle + gapDegrees / 2f,
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
