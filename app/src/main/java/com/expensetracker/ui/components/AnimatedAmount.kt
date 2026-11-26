package com.expensetracker.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember

/**
 * A number that glides to its new value when it changes (e.g. the month total after adding an
 * expense) instead of jumping. Shows the exact value whenever it isn't moving.
 */
@Composable
fun animatedAmount(target: Double, durationMillis: Int = 650): Double {
    val animatable = remember { Animatable(target.toFloat()) }
    LaunchedEffect(target) {
        animatable.animateTo(target.toFloat(), tween(durationMillis, easing = FastOutSlowInEasing))
    }
    return if (animatable.isRunning) animatable.value.toDouble() else target
}
