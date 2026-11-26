package com.expensetracker.ui.charts

import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.log10
import kotlin.math.pow

/** Rounds [max] up to a "nice" axis maximum (1, 2, 2.5, 5 or 10 × 10ⁿ) so tick labels are clean. */
fun niceAxisMax(max: Double): Double {
    if (max <= 0.0) return 1.0
    val magnitude = 10.0.pow(floor(log10(max)))
    val normalized = max / magnitude
    val step = listOf(1.0, 2.0, 2.5, 5.0, 10.0).first { it >= normalized - 1e-9 }
    return step * magnitude
}

/** Show at most ~[maxLabels] x-axis labels, always including the first one. */
fun labelStep(count: Int, maxLabels: Int = 7): Int =
    if (count <= maxLabels) 1 else ceil(count / maxLabels.toDouble()).toInt()
