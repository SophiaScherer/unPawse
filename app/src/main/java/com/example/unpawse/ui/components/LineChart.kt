package com.example.unpawse.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Smooth weekly line chart (Daily Screen Time card). Normalizes [points] to the canvas height and
 * connects them with cubic segments (control points at horizontal midpoints) for the soft curve in
 * the mockup. Optionally highlights a single point with a filled dot.
 *
 * [labels] are drawn as an evenly-spaced row below the curve (e.g. MON..SUN).
 */
@Composable
fun LineChart(
    points: List<Float>,
    modifier: Modifier = Modifier,
    labels: List<String> = emptyList(),
    lineColor: Color = MaterialTheme.colorScheme.primary,
    highlightIndex: Int? = null,
    chartHeight: Dp = 120.dp,
) {
    Column(modifier = modifier) {
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(chartHeight),
        ) {
            if (points.size < 2) return@Canvas

            val maxV = points.max()
            val minV = points.min()
            val range = (maxV - minV).takeIf { it > 0f } ?: 1f
            val vPad = size.height * 0.12f
            val usableH = size.height - vPad * 2

            val coords = points.mapIndexed { i, v ->
                val x = size.width * (i / (points.size - 1).toFloat())
                val y = vPad + usableH * (1f - (v - minV) / range)
                Offset(x, y)
            }

            val path = Path().apply {
                moveTo(coords.first().x, coords.first().y)
                for (i in 0 until coords.size - 1) {
                    val p0 = coords[i]
                    val p1 = coords[i + 1]
                    val midX = (p0.x + p1.x) / 2f
                    cubicTo(midX, p0.y, midX, p1.y, p1.x, p1.y)
                }
            }
            drawPath(
                path = path,
                color = lineColor,
                style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round),
            )

            highlightIndex?.let { idx ->
                coords.getOrNull(idx)?.let { p ->
                    drawCircle(color = lineColor, radius = 5.dp.toPx(), center = p)
                }
            }
        }

        if (labels.isNotEmpty()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                labels.forEach { label ->
                    Text(
                        text = label,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                    )
                }
            }
        }
    }
}
