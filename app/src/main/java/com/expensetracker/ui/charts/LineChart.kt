package com.expensetracker.ui.charts

import com.expensetracker.ui.theme.card
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.expensetracker.domain.ChartPoint
import kotlin.math.abs

/**
 * Single-series smoothed line with a light area wash, hairline gridlines, a labelled end point,
 * and a tap crosshair that snaps to the nearest point.
 */
@Composable
fun LineChart(
    points: List<ChartPoint>,
    valueFormatter: (Double) -> String,
    axisFormatter: (Double) -> String,
    modifier: Modifier = Modifier,
    height: Dp = 200.dp,
    lineColor: Color = MaterialTheme.colorScheme.primary,
) {
    val colors = MaterialTheme.colorScheme
    val surface = colors.card
    val textMeasurer = rememberTextMeasurer()
    val axisStyle = MaterialTheme.typography.labelSmall.copy(color = colors.onSurfaceVariant)
    val endLabelStyle = MaterialTheme.typography.labelMedium.copy(color = colors.onSurface, fontWeight = FontWeight.SemiBold)
    val tooltipValueStyle = MaterialTheme.typography.labelLarge.copy(color = colors.inverseOnSurface, fontWeight = FontWeight.Bold)
    val tooltipLabelStyle = MaterialTheme.typography.labelSmall.copy(color = colors.inverseOnSurface.copy(alpha = 0.8f))

    var selected by remember(points) { mutableStateOf<Int?>(null) }
    val reveal = remember { Animatable(0f) }
    LaunchedEffect(points) {
        reveal.snapTo(0f)
        reveal.animateTo(1f, tween(durationMillis = 900, easing = FastOutSlowInEasing))
    }

    val axisMax = niceAxisMax(points.maxOfOrNull { it.value } ?: 0.0)
    val ticks = listOf(0.0, axisMax / 2, axisMax)
    val description = points.joinToString(prefix = "Line chart. ") { "${it.label}: ${valueFormatter(it.value)}" }

    // Geometry shared by drawing and hit-testing.
    fun geometry(width: Float, heightPx: Float, density: androidx.compose.ui.unit.Density): LineGeometry = with(density) {
        val widestTick = ticks.maxOf { textMeasurer.measure(axisFormatter(it), axisStyle).size.width }
        val labelHeight = textMeasurer.measure("0", axisStyle).size.height
        val left = widestTick + 12.dp.toPx() + 6.dp.toPx()
        val right = width - 12.dp.toPx()
        val top = 36.dp.toPx()
        val bottom = heightPx - labelHeight - 10.dp.toPx()
        val stepX = if (points.size > 1) (right - left) / (points.size - 1) else 0f
        LineGeometry(
            left = left,
            right = right,
            top = top,
            bottom = bottom,
            xs = points.indices.map { left + stepX * it },
            ys = points.map { bottom - (it.value / axisMax).toFloat() * (bottom - top) },
        )
    }

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
            .semantics { contentDescription = description }
            .pointerInput(points) {
                detectTapGestures { offset ->
                    val g = geometry(size.width.toFloat(), size.height.toFloat(), this)
                    val nearest = g.xs.indices.minByOrNull { abs(g.xs[it] - offset.x) }
                    selected = if (nearest == selected) null else nearest
                }
            },
    ) {
        if (points.isEmpty()) return@Canvas
        val g = geometry(size.width, size.height, this)

        ticks.forEach { tick ->
            val y = g.bottom - (tick / axisMax).toFloat() * (g.bottom - g.top)
            drawLine(
                color = if (tick == 0.0) colors.outline.copy(alpha = 0.6f) else colors.outlineVariant.copy(alpha = 0.6f),
                start = Offset(g.left - 6.dp.toPx(), y),
                end = Offset(size.width, y),
                strokeWidth = 1.dp.toPx(),
            )
            val label = textMeasurer.measure(axisFormatter(tick), axisStyle)
            drawText(label, topLeft = Offset(g.left - 6.dp.toPx() - label.size.width - 8.dp.toPx(), y - label.size.height / 2f))
        }

        points.forEachIndexed { index, point ->
            val label = textMeasurer.measure(point.label, axisStyle)
            drawText(label, topLeft = Offset(g.xs[index] - label.size.width / 2f, g.bottom + 8.dp.toPx()))
        }

        val line = Path().apply {
            moveTo(g.xs[0], g.ys[0])
            for (i in 1 until points.size) {
                val midX = (g.xs[i - 1] + g.xs[i]) / 2
                cubicTo(midX, g.ys[i - 1], midX, g.ys[i], g.xs[i], g.ys[i])
            }
        }
        val area = Path().apply {
            addPath(line)
            lineTo(g.xs.last(), g.bottom)
            lineTo(g.xs.first(), g.bottom)
            close()
        }

        clipRect(right = g.left + (size.width - g.left) * reveal.value) {
            drawPath(
                area,
                Brush.verticalGradient(listOf(lineColor.copy(alpha = 0.14f), lineColor.copy(alpha = 0.02f)), startY = g.top, endY = g.bottom),
            )
            drawPath(line, lineColor, style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
        }

        val focus = selected ?: points.lastIndex
        if (selected != null) {
            drawLine(colors.outline.copy(alpha = 0.7f), Offset(g.xs[focus], g.top), Offset(g.xs[focus], g.bottom), strokeWidth = 1.dp.toPx())
        }
        if (reveal.value >= 0.99f) {
            // End/selected marker: filled dot with a 2dp surface ring.
            drawCircle(surface, radius = 6.dp.toPx(), center = Offset(g.xs[focus], g.ys[focus]))
            drawCircle(lineColor, radius = 4.dp.toPx(), center = Offset(g.xs[focus], g.ys[focus]))
        }

        if (selected != null) {
            drawTooltip(
                textMeasurer = textMeasurer,
                value = valueFormatter(points[focus].value),
                label = points[focus].label,
                anchorX = g.xs[focus],
                anchorY = g.ys[focus],
                minX = 0f,
                background = colors.inverseSurface,
                valueStyle = tooltipValueStyle,
                labelStyle = tooltipLabelStyle,
            )
        } else if (reveal.value >= 0.99f) {
            // Direct label on the end point only.
            val label = textMeasurer.measure(valueFormatter(points.last().value), endLabelStyle)
            val x = (g.xs.last() - label.size.width / 2f).coerceAtMost(size.width - label.size.width)
            drawText(label, topLeft = Offset(x, (g.ys.last() - label.size.height - 10.dp.toPx()).coerceAtLeast(0f)))
        }
    }
}

private class LineGeometry(
    val left: Float,
    val right: Float,
    val top: Float,
    val bottom: Float,
    val xs: List<Float>,
    val ys: List<Float>,
)
