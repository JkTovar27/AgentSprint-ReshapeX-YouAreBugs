package com.juanpablo0612.sickreshapex.ui.theme

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring

/**
 * Shared motion tokens so every screen animates with the same rhythm.
 * Durations are in milliseconds, meant for tween()/AnimatedVisibility.
 */
object Motion {
    const val DURATION_INSTANT = 100
    const val DURATION_FAST = 180
    const val DURATION_MEDIUM = 320
    const val DURATION_SLOW = 520
    const val DURATION_XSLOW = 900

    /** Stagger delay between successive items in an entrance list animation. */
    const val STAGGER_STEP = 45

    val Standard: Easing = FastOutSlowInEasing
    val Emphasized: Easing = CubicBezierEasing(0.2f, 0.0f, 0f, 1.0f)
    val EmphasizedDecelerate: Easing = CubicBezierEasing(0.05f, 0.7f, 0.1f, 1.0f)

    fun <T> bouncySpring() = spring<T>(
        dampingRatio = Spring.DampingRatioMediumBouncy,
        stiffness = Spring.StiffnessLow
    )

    fun <T> snappySpring() = spring<T>(
        dampingRatio = Spring.DampingRatioNoBouncy,
        stiffness = Spring.StiffnessMediumLow
    )
}
