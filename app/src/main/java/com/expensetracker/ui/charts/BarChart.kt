package com.expensetracker.ui.charts

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
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.expensetracker.domain.ChartPoint

/**
 * Single-series column chart: thin columns (≤ 24dp) with 4dp rounded tops grown from one baseline,
 * hairline gridlines with compact y-axis ticks, and a tap-to-inspect tooltip on each column.
 */
@Composable
fun BarChart(
    points: List<ChartPoint>,
    valueFormatter: (Double) -> String,
    axisFormatter: (Double) -> String,
    modifier: Modifier = Modifier,
    height: Dp = 200.dp,
    barColor: Color = MaterialTheme.colorScheme.primary,
    /** Optional column drawn as "current" (e.g. today) when nothing is selected. */
    highlightIndex: Int? = null,
    tooltipLabel: (Int) -> String = { points[it].label },
) {
    val colors = MaterialTheme.colorScheme
    val textMeasurer = rememberTextMeasurer()
    val axisStyle = MaterialTheme.typography.labelSmall.copy(color = colors.onSurfaceVariant)
    val tooltipValueStyle = MaterialTheme.typography.labelLarge.copy(color = colors.inverseOnSurface, fontWeight = FontWeight.Bold)
    val tooltipLabelStyle = MaterialTheme.typography.labelSmall.copy(color = colors.inverseOnSurface.copy(alpha = 0.8f))

    var selected by remember(points) { mutableStateOf<Int?>(null) }
    val growth = remember { Animatable(0f) }
    LaunchedEffect(points) {
        growth.snapTo(0f)
        growth.animateTo(1f, tween(durationMillis = 700, easing = FastOutSlowInEasing))
    }

    val axisMax = niceAxisMax(points.maxOfOrNull { it.value } ?: 0.0)
    val ticks = listOf(0.0, axisMax / 2, axisMax)
    val description = points.joinToString(prefix = "Bar chart. ") { "${it.label}: ${valueFormatter(it.value)}" }

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
            .semantics { contentDescription = description }
            .pointerInput(points) {
                detectTapGestures { offset ->
                    val layout = BarLayout.of(this, points.size, textMeasurer, ticks.map(axisFormatter), axisStyle)
                    val index = layout.indexAt(offset.x)
                    selected = if (index == null || index == selected) null else index
                }
            },
    ) {
        if (points.isEmpty()) return@Canvas
        val layout = BarLayout.of(this, points.size, textMeasurer, ticks.map(axisFormatter), axisStyle)

        // Gridlines + y ticks (hairline, recessive).
        ticks.forEach { tick ->
            val y = layout.yFor(tick, axisMax)
            drawLine(
                color = if (tick == 0.0) colors.outline.copy(alpha = 0.6f) else colors.outlineVariant.copy(alpha = 0.6f),
                start = Offset(layout.plotLeft, y),
                end = Offset(size.width, y),
                strokeWidth = 1.dp.toPx(),
            )
            val label = textMeasurer.measure(axisFormatter(tick), axisStyle)
            drawText(label, topLeft = Offset(layout.plotLeft - label.size.width - 8.dp.toPx(), y - label.size.height / 2f))
        }

        // Columns.
        val radius = minOf(4.dp.toPx(), layout.barWidth / 2)
        points.forEachIndexed { index, point ->
            if (point.value <= 0.0) return@forEachIndexed
            val top = layout.yFor(point.value * growth.value, axisMax)
            val left = layout.centerX(index) - layout.barWidth / 2
            val emphasized = when (val s = selected) {
                null -> highlightIndex == null || highlightIndex == index
                else -> s == index
            }
            val color = if (emphasized) barColor else barColor.copy(alpha = 0.38f)
            val path = Path().apply {
                addRoundRect(
                    RoundRect(
                        rect = Rect(left, minOf(top, layout.baseline - 2.dp.toPx()), left + layout.barWidth, layout.baseline),
                        topLeft = CornerRadius(radius),
                        topRight = CornerRadius(radius),
                        bottomLeft = CornerRadius.Zero,
                        bottomRight = CornerRadius.Zero,
                    ),
                )
            }
            drawPath(path, color)
        }

        // X labels.
        val step = labelStep(points.size)
        points.forEachIndexed { index, point ->
            if (index % step != 0 && !(index == points.lastIndex && points.size <= 12)) return@forEachIndexed
            val label = textMeasurer.measure(point.label, axisStyle)
            val x = (layout.centerX(index) - label.size.width / 2f).coerceIn(layout.plotLeft, size.width - label.size.width)
            drawText(label, topLeft = Offset(x, layout.baseline + 6.dp.toPx()))
        }

        // Tooltip for the selected column: value leads, label follows.
        selected?.let { index ->
            val point = points.getOrNull(index) ?: return@let
            val barTop = layout.yFor(point.value, axisMax)
            drawTooltip(
                textMeasurer = textMeasurer,
                value = valueFormatter(point.value),
                label = tooltipLabel(index),
                anchorX = layout.centerX(index),
                anchorY = barTop,
                minX = layout.plotLeft,
                background = colors.inverseSurface,
                valueStyle = tooltipValueStyle,
                labelStyle = tooltipLabelStyle,
            )
        }
    }
}

private class BarLayout(
    val plotLeft: Float,
    val plotTop: Float,
    val baseline: Float,
    val slotWidth: Float,
    val barWidth: Float,
) {
    fun centerX(index: Int) = plotLeft + slotWidth * index + slotWidth / 2
    fun yFor(value: Double, max: Double) = baseline - ((value / max).toFloat() * (baseline - plotTop))
    fun indexAt(x: Float): Int? {
        if (x < plotLeft) return null
        return ((x - plotLeft) / slotWidth).toInt()
    }

    companion object {
        fun of(
            density: androidx.compose.ui.unit.Density,
            count: Int,
            sizePx: Size,
            measurer: TextMeasurer,
            tickLabels: List<String>,
            style: TextStyle,
        ): BarLayout = with(density) {
            val widestTick = tickLabels.maxOf { measurer.measure(it, style).size.width }
            val labelHeight = measurer.measure("0", style).size.height
            val plotLeft = widestTick + 12.dp.toPx()
            val plotTop = 36.dp.toPx() // room for the tooltip above the tallest column
            val baseline = sizePx.height - labelHeight - 8.dp.toPx()
            val slot = (sizePx.width - plotLeft) / count.coerceAtLeast(1)
            val bar = (slot * 0.62f).coerceIn(2.dp.toPx(), 24.dp.toPx())
            BarLayout(plotLeft, plotTop, baseline, slot, bar)
        }

        fun of(scope: DrawScope, count: Int, measurer: TextMeasurer, tickLabels: List<String>, style: TextStyle) =
            of(scope, count, scope.size, measurer, tickLabels, style)

        fun of(
            scope: androidx.compose.ui.input.pointer.PointerInputScope,
            count: Int,
            measurer: TextMeasurer,
            tickLabels: List<String>,
            style: TextStyle,
        ) = of(scope, count, Size(scope.size.width.toFloat(), scope.size.height.toFloat()), measurer, tickLabels, style)
    }
}

/** Dark rounded bubble above a mark; clamped so it never leaves the canvas. */
internal fun DrawScope.drawTooltip(
    textMeasurer: TextMeasurer,
    value: String,
    label: String,
    anchorX: Float,
    anchorY: Float,
    minX: Float,
    background: Color,
    valueStyle: TextStyle,
    labelStyle: TextStyle,
) {
    val valueLayout = textMeasurer.measure(value, valueStyle)
    val labelLayout = textMeasurer.measure(label, labelStyle)
    val padH = 10.dp.toPx()
    val padV = 6.dp.toPx()
    val width = maxOf(valueLayout.size.width, labelLayout.size.width) + padH * 2
    val height = valueLayout.size.height + labelLayout.size.height + padV * 2
    val left = (anchorX - width / 2).coerceIn(minX, size.width - width)
    val top = (anchorY - height - 8.dp.toPx()).coerceAtLeast(0f)
    drawRoundRect(
        color = background,
        topLeft = Offset(left, top),
        size = Size(width, height),
        cornerRadius = CornerRadius(10.dp.toPx()),
    )
    drawText(valueLayout, topLeft = Offset(left + padH, top + padV))
    drawText(labelLayout, topLeft = Offset(left + padH, top + padV + valueLayout.size.height))
}
