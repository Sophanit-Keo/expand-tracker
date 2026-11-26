package com.expensetracker.ui.charts

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
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
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.expensetracker.domain.CashPoint

/**
 * Diverging columns on one shared scale: money in grows up from the zero line, money out grows down.
 * Tap a slot to see in, out and net for it. Two series, so it always comes with a [ChartLegend].
 */
@Composable
fun CashFlowChart(
    points: List<CashPoint>,
    inColor: Color,
    outColor: Color,
    valueFormatter: (Double) -> String,
    axisFormatter: (Double) -> String,
    modifier: Modifier = Modifier,
    height: Dp = 220.dp,
    tooltipLabel: (Int) -> String = { points[it].label },
) {
    val colors = MaterialTheme.colorScheme
    val textMeasurer = rememberTextMeasurer()
    val axisStyle = MaterialTheme.typography.labelSmall.copy(color = colors.onSurfaceVariant)
    val tipStrong = MaterialTheme.typography.labelLarge.copy(color = colors.inverseOnSurface, fontWeight = FontWeight.Bold)
    val tipText = MaterialTheme.typography.labelMedium.copy(color = colors.inverseOnSurface)
    val tipMuted = MaterialTheme.typography.labelSmall.copy(color = colors.inverseOnSurface.copy(alpha = 0.8f))

    var selected by remember(points) { mutableStateOf<Int?>(null) }
    val growth = remember { Animatable(0f) }
    LaunchedEffect(points) {
        growth.snapTo(0f)
        growth.animateTo(1f, tween(durationMillis = 700, easing = FastOutSlowInEasing))
    }

    val axisMax = niceAxisMax(points.maxOfOrNull { maxOf(it.cashIn, it.cashOut) } ?: 0.0)
    val tickLabels = listOf("+" + axisFormatter(axisMax), "0", "−" + axisFormatter(axisMax))
    val description = points.joinToString(prefix = "Cash in and out. ") {
        "${it.label}: in ${valueFormatter(it.cashIn)}, out ${valueFormatter(it.cashOut)}"
    }

    fun layout(width: Float, heightPx: Float, density: Density) = with(density) {
        val widestTick = tickLabels.maxOf { textMeasurer.measure(it, axisStyle).size.width }
        val labelHeight = textMeasurer.measure("0", axisStyle).size.height
        val left = widestTick + 12.dp.toPx()
        val top = 8.dp.toPx()
        val bottom = heightPx - labelHeight - 8.dp.toPx()
        val slot = (width - left) / points.size.coerceAtLeast(1)
        CashLayout(left, top, bottom, slot, (slot * 0.62f).coerceIn(2.dp.toPx(), 24.dp.toPx()))
    }

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
            .semantics { contentDescription = description }
            .pointerInput(points) {
                detectTapGestures { offset ->
                    val l = layout(size.width.toFloat(), size.height.toFloat(), this)
                    val index = if (offset.x < l.left) null else ((offset.x - l.left) / l.slot).toInt().takeIf { it in points.indices }
                    selected = if (index == null || index == selected) null else index
                }
            },
    ) {
        if (points.isEmpty()) return@Canvas
        val l = layout(size.width, size.height, this)
        val zero = l.zero
        val half = zero - l.top

        // Gridlines: +max, zero (stronger), −max.
        listOf(l.top to tickLabels[0], zero to tickLabels[1], l.bottom to tickLabels[2]).forEach { (y, text) ->
            drawLine(
                color = if (y == zero) colors.outline.copy(alpha = 0.7f) else colors.outlineVariant.copy(alpha = 0.6f),
                start = Offset(l.left, y),
                end = Offset(size.width, y),
                strokeWidth = 1.dp.toPx(),
            )
            val label = textMeasurer.measure(text, axisStyle)
            drawText(label, topLeft = Offset(l.left - label.size.width - 8.dp.toPx(), (y - label.size.height / 2f).coerceIn(0f, size.height - label.size.height)))
        }

        val radius = minOf(4.dp.toPx(), l.bar / 2)
        val gap = 1.dp.toPx() // keeps the two bars from touching the zero line and each other
        points.forEachIndexed { index, p ->
            val center = l.left + l.slot * index + l.slot / 2
            val x0 = center - l.bar / 2
            val dim = selected != null && selected != index
            if (p.cashIn > 0) {
                val h = (p.cashIn / axisMax).toFloat() * half * growth.value
                drawColumn(Rect(x0, zero - gap - h, x0 + l.bar, zero - gap), radius, roundTop = true, color = if (dim) inColor.copy(alpha = 0.3f) else inColor)
            }
            if (p.cashOut > 0) {
                val h = (p.cashOut / axisMax).toFloat() * half * growth.value
                drawColumn(Rect(x0, zero + gap, x0 + l.bar, zero + gap + h), radius, roundTop = false, color = if (dim) outColor.copy(alpha = 0.3f) else outColor)
            }
        }

        val step = labelStep(points.size)
        points.forEachIndexed { index, p ->
            if (index % step != 0) return@forEachIndexed
            val label = textMeasurer.measure(p.label, axisStyle)
            val x = (l.left + l.slot * index + l.slot / 2 - label.size.width / 2f).coerceIn(l.left, size.width - label.size.width)
            drawText(label, topLeft = Offset(x, l.bottom + 6.dp.toPx()))
        }

        selected?.let { index ->
            val p = points.getOrNull(index) ?: return@let
            val net = p.net
            drawLinesTooltip(
                textMeasurer,
                listOf(
                    tooltipLabel(index) to tipMuted,
                    "In  +${valueFormatter(p.cashIn)}" to tipText,
                    "Out  −${valueFormatter(p.cashOut)}" to tipText,
                    "Net  ${if (net >= 0) "+" else "−"}${valueFormatter(kotlin.math.abs(net))}" to tipStrong,
                ),
                anchorX = l.left + l.slot * index + l.slot / 2,
                minX = l.left,
                background = colors.inverseSurface,
            )
        }
    }
}

private class CashLayout(val left: Float, val top: Float, val bottom: Float, val slot: Float, val bar: Float) {
    val zero: Float get() = (top + bottom) / 2
}

/** A column with 4dp rounding on its data end and a square end at the zero line. */
private fun DrawScope.drawColumn(rect: Rect, radius: Float, roundTop: Boolean, color: Color) {
    if (rect.height <= 0f) return
    val r = CornerRadius(minOf(radius, rect.height))
    val path = Path().apply {
        addRoundRect(
            RoundRect(
                rect = rect,
                topLeft = if (roundTop) r else CornerRadius.Zero,
                topRight = if (roundTop) r else CornerRadius.Zero,
                bottomLeft = if (roundTop) CornerRadius.Zero else r,
                bottomRight = if (roundTop) CornerRadius.Zero else r,
            ),
        )
    }
    drawPath(path, color)
}

/** A multi-line tooltip pinned to the top of the plot, centred on [anchorX]. */
internal fun DrawScope.drawLinesTooltip(
    textMeasurer: TextMeasurer,
    lines: List<Pair<String, TextStyle>>,
    anchorX: Float,
    minX: Float,
    background: Color,
) {
    val layouts = lines.map { (text, style) -> textMeasurer.measure(text, style) }
    val padH = 10.dp.toPx()
    val padV = 7.dp.toPx()
    val width = layouts.maxOf { it.size.width } + padH * 2
    val height = layouts.sumOf { it.size.height } + padV * 2
    val left = (anchorX - width / 2).coerceIn(minX, size.width - width)
    drawRoundRect(background, topLeft = Offset(left, 0f), size = Size(width, height), cornerRadius = CornerRadius(10.dp.toPx()))
    var y = padV
    layouts.forEach {
        drawText(it, topLeft = Offset(left + padH, y))
        y += it.size.height
    }
}

/** Legend for multi-series charts: a swatch shaped like the mark (bars → square) and a label. */
@Composable
fun ChartLegend(items: List<Pair<String, Color>>, modifier: Modifier = Modifier) {
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
        items.forEach { (label, color) ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier
                        .size(10.dp)
                        .background(color, RoundedCornerShape(2.dp)),
                )
                Spacer(Modifier.width(6.dp))
                Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
