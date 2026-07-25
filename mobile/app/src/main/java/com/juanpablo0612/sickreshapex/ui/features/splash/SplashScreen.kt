package com.juanpablo0612.sickreshapex.ui.features.splash

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.unit.dp
import com.juanpablo0612.sickreshapex.ui.components.PulsingDot
import com.juanpablo0612.sickreshapex.ui.components.RadarPulse
import com.juanpablo0612.sickreshapex.ui.theme.Motion
import com.juanpablo0612.sickreshapex.ui.theme.ReadoutType
import com.juanpablo0612.sickreshapex.ui.theme.extendedColors
import kotlinx.coroutines.delay

private const val DELAY_TO_WORDMARK = 550L
private const val DELAY_TO_TAGLINE = 250L
private const val DELAY_TO_READOUT = 350L
private const val DELAY_TO_EXIT = 750L
private const val EXIT_DURATION = 320

@Composable
fun SplashScreen(onNext: () -> Unit) {
    var showMark by remember { mutableStateOf(false) }
    var showWordmark by remember { mutableStateOf(false) }
    var showTagline by remember { mutableStateOf(false) }
    var showReadout by remember { mutableStateOf(false) }
    var exiting by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        showMark = true
        delay(DELAY_TO_WORDMARK)
        showWordmark = true
        delay(DELAY_TO_TAGLINE)
        showTagline = true
        delay(DELAY_TO_READOUT)
        showReadout = true
        delay(DELAY_TO_EXIT)
        exiting = true
        delay(EXIT_DURATION.toLong())
        onNext()
    }

    val exitAlpha by animateFloatAsState(
        targetValue = if (exiting) 0f else 1f,
        animationSpec = tween(EXIT_DURATION, easing = Motion.Emphasized),
        label = "splashExitAlpha"
    )
    val exitScale by animateFloatAsState(
        targetValue = if (exiting) 1.1f else 1f,
        animationSpec = tween(EXIT_DURATION, easing = Motion.Emphasized),
        label = "splashExitScale"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.extendedColors.heroGradient),
        contentAlignment = Alignment.Center
    ) {
        BreathingGlow(
            color = MaterialTheme.extendedColors.scanCyan,
            modifier = Modifier.size(420.dp)
        )

        Column(
            modifier = Modifier
                .alpha(exitAlpha)
                .scale(exitScale),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            AnimatedVisibility(
                visible = showMark,
                enter = fadeIn(tween(Motion.DURATION_SLOW, easing = Motion.EmphasizedDecelerate)) +
                    scaleIn(
                        initialScale = 0.6f,
                        animationSpec = tween(Motion.DURATION_SLOW, easing = Motion.EmphasizedDecelerate)
                    )
            ) {
                Box(contentAlignment = Alignment.Center) {
                    RadarPulse(
                        modifier = Modifier.size(230.dp),
                        color = MaterialTheme.extendedColors.scanCyan
                    )
                    ScanMark(modifier = Modifier.size(150.dp))
                }
            }

            Spacer(modifier = Modifier.height(28.dp))

            AnimatedVisibility(
                visible = showWordmark,
                enter = fadeIn(tween(Motion.DURATION_MEDIUM, easing = Motion.Standard)) +
                    slideInVertically(
                        animationSpec = tween(Motion.DURATION_MEDIUM, easing = Motion.EmphasizedDecelerate),
                        initialOffsetY = { it / 3 }
                    )
            ) {
                Text(
                    text = "SICK",
                    style = MaterialTheme.typography.displayLarge,
                    color = MaterialTheme.colorScheme.onBackground
                )
            }

            Spacer(modifier = Modifier.height(6.dp))

            AnimatedVisibility(
                visible = showTagline,
                enter = fadeIn(tween(Motion.DURATION_MEDIUM, easing = Motion.Standard)) +
                    slideInVertically(
                        animationSpec = tween(Motion.DURATION_MEDIUM, easing = Motion.EmphasizedDecelerate),
                        initialOffsetY = { it / 3 }
                    )
            ) {
                Text(
                    text = "Select Copilot",
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.height(20.dp))

            AnimatedVisibility(
                visible = showReadout,
                enter = fadeIn(tween(Motion.DURATION_MEDIUM, easing = Motion.Standard))
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    PulsingDot(color = MaterialTheme.extendedColors.scanCyan, size = 6.dp)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "INITIALIZING AI PIPELINE",
                        style = ReadoutType.small,
                        color = MaterialTheme.extendedColors.scanCyan
                    )
                }
            }
        }
    }
}

@Composable
private fun BreathingGlow(color: Color, modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "splashGlow")
    val glowAlpha by transition.animateFloat(
        initialValue = 0.12f,
        targetValue = 0.32f,
        animationSpec = infiniteRepeatable(
            animation = tween(1800, easing = Motion.Standard),
            repeatMode = RepeatMode.Reverse
        ),
        label = "splashGlowAlpha"
    )
    Canvas(modifier = modifier) {
        drawCircle(
            brush = Brush.radialGradient(listOf(color.copy(alpha = glowAlpha), Color.Transparent))
        )
    }
}

@Composable
private fun ScanMark(modifier: Modifier = Modifier) {
    val outerColor = MaterialTheme.extendedColors.scanCyan
    val innerColor = MaterialTheme.colorScheme.primary
    val coreHighlight = MaterialTheme.colorScheme.onPrimaryContainer
    val coreBase = MaterialTheme.colorScheme.primary

    val transition = rememberInfiniteTransition(label = "scanMarkSweep")
    val outerRotation by transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(animation = tween(5200, easing = LinearEasing)),
        label = "scanMarkOuterRotation"
    )
    val innerRotation by transition.animateFloat(
        initialValue = 360f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(animation = tween(3400, easing = LinearEasing)),
        label = "scanMarkInnerRotation"
    )

    Canvas(modifier = modifier) {
        val strokeWidth = 5.dp.toPx()
        val innerInset = size.minDimension * 0.2f

        rotate(outerRotation) {
            drawArc(
                color = outerColor,
                startAngle = -90f,
                sweepAngle = 250f,
                useCenter = false,
                style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
            )
        }
        rotate(innerRotation) {
            drawArc(
                color = innerColor,
                startAngle = 20f,
                sweepAngle = 150f,
                useCenter = false,
                topLeft = Offset(innerInset, innerInset),
                size = Size(size.width - innerInset * 2f, size.height - innerInset * 2f),
                style = Stroke(width = strokeWidth * 0.8f, cap = StrokeCap.Round)
            )
        }
        drawCircle(
            brush = Brush.radialGradient(listOf(coreHighlight, coreBase)),
            radius = size.minDimension * 0.16f
        )
        drawCircle(
            color = Color.White.copy(alpha = 0.9f),
            radius = size.minDimension * 0.05f
        )
    }
}
