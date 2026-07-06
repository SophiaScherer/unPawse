package com.example.unpawse.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Circular progress ring for the Home screen's screen-time card. Draws a full track plus a
 * rounded progress arc sweeping clockwise from the top. [content] is centered inside the ring
 * (the "2h 15m / Screen time" label).
 */
@Composable
fun ProgressRing(
    progress: Float,
    modifier: Modifier = Modifier,
    strokeWidth: Dp = 16.dp,
    trackColor: Color,
    progressColor: Color,
    content: @Composable () -> Unit,
) {
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Canvas(modifier = Modifier.matchParentSize()) {
            val stroke = Stroke(width = strokeWidth.toPx(), cap = StrokeCap.Round)
            val inset = strokeWidth.toPx() / 2f
            val arcSize = androidx.compose.ui.geometry.Size(
                width = size.width - strokeWidth.toPx(),
                height = size.height - strokeWidth.toPx(),
            )
            val topLeft = androidx.compose.ui.geometry.Offset(inset, inset)

            // Track (full circle).
            drawArc(
                color = trackColor,
                startAngle = 0f,
                sweepAngle = 360f,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = stroke,
            )
            // Progress arc from 12 o'clock.
            drawArc(
                color = progressColor,
                startAngle = -90f,
                sweepAngle = progress.coerceIn(0f, 1f) * 360f,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = stroke,
            )
        }
        content()
    }
}
