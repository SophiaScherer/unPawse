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
 *
 * A `null` point is a slot with nothing to measure — a day that hasn't happened yet. It keeps its
 * place on the x axis, so the labels stay under the days they name, but the line stops rather than
 * running through it: drawn as zero, the weekend of a Wednesday read as four days of abstinence.
 */
@Composable
fun LineChart(
    points: List<Float?>,
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
            val plotted = points.filterNotNull()
            if (plotted.isEmpty()) return@Canvas

            val maxV = plotted.max()
            val minV = plotted.min()
            val range = (maxV - minV).takeIf { it > 0f } ?: 1f
            val vPad = size.height * 0.12f
            val usableH = size.height - vPad * 2
            // Spans every slot, plotted or not, so the axis doesn't stretch as the week fills in.
            val spans = (points.size - 1).coerceAtLeast(1)

            val coords = points.mapIndexed { i, v ->
                v?.let {
                    val x = size.width * (i / spans.toFloat())
                    Offset(x, vPad + usableH * (1f - (it - minV) / range))
                }
            }

            coords.unbrokenRuns().forEach { run ->
                // A lone point has no segment to draw, but it is still a measured day — on a Monday
                // it is the whole chart, and the old size-2 guard rendered it as an empty card.
                if (run.size == 1) {
                    drawCircle(color = lineColor, radius = 3.dp.toPx(), center = run.first())
                    return@forEach
                }
                val path = Path().apply {
                    moveTo(run.first().x, run.first().y)
                    for (i in 0 until run.size - 1) {
                        val p0 = run[i]
                        val p1 = run[i + 1]
                        val midX = (p0.x + p1.x) / 2f
                        cubicTo(midX, p0.y, midX, p1.y, p1.x, p1.y)
                    }
                }
                drawPath(
                    path = path,
                    color = lineColor,
                    style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round),
                )
            }

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

/**
 * Contiguous stretches of plotted points. A gap breaks the line in two rather than being joined
 * across, because a segment spanning it would assert a value for a day that has none.
 */
private fun List<Offset?>.unbrokenRuns(): List<List<Offset>> {
    val runs = mutableListOf<List<Offset>>()
    var run = mutableListOf<Offset>()
    forEach { point ->
        if (point == null) {
            if (run.isNotEmpty()) runs += run
            run = mutableListOf()
        } else {
            run += point
        }
    }
    if (run.isNotEmpty()) runs += run
    return runs
}
