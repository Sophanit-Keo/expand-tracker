package com.expensetracker.ui.charts

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.hypot

data class DonutSlice(val value: Float, val color: Color)

/**
 * Part-to-whole ring. Keep it to ≤ 6 slices (fold the tail into "Other"); slices are separated by
 * a 2dp surface gap, and a tapped slice stays saturated while the others recede.
 */
@Composable
fun DonutChart(
    slices: List<DonutSlice>,
    modifier: Modifier = Modifier,
    thickness: Dp = 20.dp,
    selectedIndex: Int? = null,
    onSliceClick: (Int?) -> Unit = {},
    centerContent: @Composable () -> Unit = {},
) {
    val track = MaterialTheme.colorScheme.surfaceContainerHigh
    val sweep = remember { Animatable(0f) }
    LaunchedEffect(slices) {
        sweep.snapTo(0f)
        sweep.animateTo(1f, tween(durationMillis = 900, easing = FastOutSlowInEasing))
    }
    val total = slices.sumOf { it.value.toDouble() }.toFloat()

    Box(modifier, contentAlignment = Alignment.Center) {
        Canvas(
            Modifier
                .fillMaxSize()
                .pointerInput(slices, selectedIndex) {
                    detectTapGestures { offset ->
                        val center = Offset(size.width / 2f, size.height / 2f)
                        val outer = minOf(size.width, size.height) / 2f
                        val distance = hypot(offset.x - center.x, offset.y - center.y)
                        if (distance > outer || distance < outer - thickness.toPx() * 2.2f || total <= 0f) {
                            onSliceClick(null)
                            return@detectTapGestures
                        }
                        // Angle measured clockwise from 12 o'clock.
                        var angle = (atan2(offset.y - center.y, offset.x - center.x) * 180 / PI).toFloat() + 90f
                        if (angle < 0) angle += 360f
                        var start = 0f
                        val hit = slices.indexOfFirst { slice ->
                            val end = start + slice.value / total * 360f
                            (angle in start..end).also { start = end }
                        }
                        onSliceClick(if (hit == -1 || hit == selectedIndex) null else hit)
                    }
                },
        ) {
            val stroke = thickness.toPx()
            val diameter = size.minDimension - stroke
            val topLeft = Offset((size.width - diameter) / 2, (size.height - diameter) / 2)
            val arcSize = Size(diameter, diameter)
            drawArc(track, 0f, 360f, useCenter = false, topLeft = topLeft, size = arcSize, style = Stroke(stroke))
            if (total <= 0f) return@Canvas

            val visibleSlices = slices.count { it.value > 0f }
            // A 2dp gap at the ring's mid-radius, expressed in degrees.
            val gap = if (visibleSlices > 1) (2.dp.toPx() / (diameter / 2) * 180f / PI.toFloat()) else 0f
            val revealed = 360f * sweep.value
            var start = 0f
            slices.forEachIndexed { index, slice ->
                val angle = slice.value / total * 360f
                val visible = (revealed - start).coerceIn(0f, angle)
                if (visible > gap) {
                    val color = if (selectedIndex == null || selectedIndex == index) slice.color else slice.color.copy(alpha = 0.28f)
                    val width = if (selectedIndex == index) stroke * 1.25f else stroke
                    drawArc(
                        color = color,
                        startAngle = start - 90f + gap / 2,
                        sweepAngle = visible - gap,
                        useCenter = false,
                        topLeft = topLeft,
                        size = arcSize,
                        style = Stroke(width, cap = StrokeCap.Butt),
                    )
                }
                start += angle
            }
        }
        centerContent()
    }
}
