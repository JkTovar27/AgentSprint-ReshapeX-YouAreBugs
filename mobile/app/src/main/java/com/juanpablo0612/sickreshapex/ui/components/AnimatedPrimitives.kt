package com.juanpablo0612.sickreshapex.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.juanpablo0612.sickreshapex.R
import com.juanpablo0612.sickreshapex.ui.theme.Motion
import com.juanpablo0612.sickreshapex.ui.theme.ReadoutType
import com.juanpablo0612.sickreshapex.ui.theme.extendedColors
import kotlin.math.min

/**
 * Fades + slides a single item in, delayed by [index] * [Motion.STAGGER_STEP]ms.
 * Wrap list/column children with this to get a staggered entrance without
 * hand-rolling AnimatedVisibility state everywhere.
 */
@Composable
fun StaggeredAppearance(
    index: Int,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    var visible by remember(index) { mutableStateOf(false) }
    LaunchedEffect(index) {
        kotlinx.coroutines.delay((index * Motion.STAGGER_STEP).toLong())
        visible = true
    }
    AnimatedVisibility(
        visible = visible,
        modifier = modifier,
        enter = fadeIn(tween(Motion.DURATION_MEDIUM, easing = Motion.Standard)) +
                slideInVertically(
                    animationSpec = tween(
                        Motion.DURATION_MEDIUM,
                        easing = Motion.EmphasizedDecelerate
                    ),
                    initialOffsetY = { it / 4 }
                )
    ) {
        content()
    }
}

/** Expand/collapse wrapper for detail sections revealing progressively (e.g. pipeline stage cards). */
@Composable
fun ExpandableAppearance(
    visible: Boolean,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    AnimatedVisibility(
        visible = visible,
        modifier = modifier,
        enter = fadeIn(tween(Motion.DURATION_MEDIUM)) +
                expandVertically(
                    animationSpec = tween(
                        Motion.DURATION_MEDIUM,
                        easing = Motion.Emphasized
                    )
                ),
        exit = shrinkVertically(animationSpec = tween(Motion.DURATION_FAST))
    ) {
        content()
    }
}

/** Shimmering skeleton placeholder — use while content is loading. No images involved. */
@Composable
fun ShimmerBox(
    modifier: Modifier = Modifier,
    shape: androidx.compose.ui.graphics.Shape = RoundedCornerShape(12.dp)
) {
    val base = MaterialTheme.extendedColors.shimmerBase
    val highlight = MaterialTheme.extendedColors.shimmerHighlight
    val transition = rememberInfiniteTransition(label = "shimmer")
    val translate by transition.animateFloat(
        initialValue = -1000f,
        targetValue = 1000f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = LinearEasing)
        ),
        label = "shimmerTranslate"
    )
    val brush = Brush.linearGradient(
        colors = listOf(base, highlight, base),
        start = Offset(translate, 0f),
        end = Offset(translate + 400f, 400f)
    )
    Box(
        modifier = modifier
            .clip(shape)
            .background(brush)
    )
}

/**
 * Concentric rings pulsing outward from the center — the "scanning" motif used on the
 * splash screen and behind the active pipeline stage.
 */
@Composable
fun RadarPulse(
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.primary,
    ringCount: Int = 3
) {
    val transition = rememberInfiniteTransition(label = "radar")
    val progress by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(2400, easing = LinearEasing)
        ),
        label = "radarProgress"
    )
    Canvas(modifier = modifier) {
        val maxRadius = min(size.width, size.height) / 2f
        repeat(ringCount) { i ->
            val ringProgress = (progress + i.toFloat() / ringCount) % 1f
            val radius = maxRadius * ringProgress
            val alpha = (1f - ringProgress).coerceIn(0f, 1f)
            drawCircle(
                color = color.copy(alpha = alpha * 0.5f),
                radius = radius,
                center = Offset(size.width / 2f, size.height / 2f),
                style = Stroke(width = 2.dp.toPx())
            )
        }
        drawCircle(
            color = color,
            radius = maxRadius * 0.12f,
            center = Offset(size.width / 2f, size.height / 2f)
        )
    }
}

/**
 * Circular progress ring that eases from 0 to [progress] and shows a monospace
 * percentage readout in the center. Used for confidence scores.
 */
@Composable
fun ConfidenceRing(
    progress: Float,
    modifier: Modifier = Modifier,
    strokeWidth: androidx.compose.ui.unit.Dp = 10.dp,
    trackColor: Color = MaterialTheme.colorScheme.surfaceContainerHighest,
    label: String? = null
) {
    val animatedProgress by animateFloatAsState(
        targetValue = progress.coerceIn(0f, 1f),
        animationSpec = tween(Motion.DURATION_XSLOW, easing = Motion.EmphasizedDecelerate),
        label = "confidenceProgress"
    )
    val ringBrush = MaterialTheme.extendedColors.scanGradient
    Box(modifier = modifier, contentAlignment = androidx.compose.ui.Alignment.Center) {
        Canvas(modifier = Modifier.size(120.dp)) {
            val stroke = Stroke(width = strokeWidth.toPx(), cap = StrokeCap.Round)
            drawArc(
                color = trackColor,
                startAngle = -90f,
                sweepAngle = 360f,
                useCenter = false,
                style = stroke
            )
            drawArc(
                brush = ringBrush,
                startAngle = -90f,
                sweepAngle = 360f * animatedProgress,
                useCenter = false,
                style = stroke
            )
        }
        Box(contentAlignment = androidx.compose.ui.Alignment.Center) {
            androidx.compose.foundation.layout.Column(
                horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally
            ) {
                Text(
                    text = stringResource(R.string.percent_format, (animatedProgress * 100).toInt()),
                    style = ReadoutType.medium,
                    color = MaterialTheme.colorScheme.onSurface,
                    textAlign = TextAlign.Center
                )
                if (label != null) {
                    Text(
                        text = label,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }
    }
}

/** A small dot that gently pulses in size/alpha — for "live"/active indicators. */
@Composable
fun PulsingDot(
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.primary,
    size: androidx.compose.ui.unit.Dp = 8.dp
) {
    val transition = rememberInfiniteTransition(label = "pulseDot")
    val scale by transition.animateFloat(
        initialValue = 0.6f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(700, easing = Motion.Standard),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseDotScale"
    )
    Box(
        modifier = modifier
            .size(size)
            .scale(scale)
            .clip(CircleShape)
            .background(color)
    )
}

/** Scales content down slightly while pressed. Attach the same [interactionSource] used by clickable(). */
fun Modifier.pressScale(interactionSource: InteractionSource): Modifier = composed {
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.96f else 1f,
        animationSpec = Motion.snappySpring(),
        label = "pressScale"
    )
    this.scale(scale)
}

/** Slowly drifting diagonal gradient — subtle animated backdrop, no static image needed. */
@Composable
fun AnimatedGradientBackdrop(
    modifier: Modifier = Modifier,
    colors: List<Color> = listOf(
        MaterialTheme.colorScheme.background,
        MaterialTheme.colorScheme.surface
    )
) {
    val transition = rememberInfiniteTransition(label = "gradientDrift")
    val offset by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(6000, easing = Motion.Standard),
            repeatMode = RepeatMode.Reverse
        ),
        label = "gradientOffset"
    )
    Box(
        modifier = modifier.background(
            Brush.linearGradient(
                colors = colors,
                start = Offset(0f, 0f),
                end = Offset(800f + offset * 400f, 1600f - offset * 400f)
            )
        )
    )
}
