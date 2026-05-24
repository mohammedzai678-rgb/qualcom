package com.deepshield.ai.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.deepshield.ai.ui.theme.*

/**
 * Animated concentric ring pulse effect for threat alerts.
 * Radiating rings expand outward and fade — used for threat indicators.
 */
@Composable
fun ThreatPulse(
    modifier: Modifier = Modifier,
    color: Color = DangerRed,
    size: Dp = 80.dp,
    rings: Int = 3,
    isActive: Boolean = true
) {
    if (!isActive) return

    val infiniteTransition = rememberInfiniteTransition(label = "threatPulse")

    val ringAnimations = (0 until rings).map { index ->
        val delay = index * 400

        val scale by infiniteTransition.animateFloat(
            initialValue = 0.3f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(
                    durationMillis = 1200,
                    delayMillis = delay,
                    easing = EaseOutCubic
                ),
                repeatMode = RepeatMode.Restart
            ),
            label = "pulseScale$index"
        )

        val alpha by infiniteTransition.animateFloat(
            initialValue = 0.6f,
            targetValue = 0f,
            animationSpec = infiniteRepeatable(
                animation = tween(
                    durationMillis = 1200,
                    delayMillis = delay,
                    easing = EaseOutCubic
                ),
                repeatMode = RepeatMode.Restart
            ),
            label = "pulseAlpha$index"
        )

        Pair(scale, alpha)
    }

    Canvas(modifier = modifier.size(size)) {
        val center = this.center
        val maxRadius = this.size.minDimension / 2f

        // Draw pulsing rings
        ringAnimations.forEach { (scale, alpha) ->
            drawCircle(
                color = color.copy(alpha = alpha),
                radius = maxRadius * scale,
                center = center,
                style = Stroke(width = 2.dp.toPx())
            )
        }

        // Center dot
        drawCircle(
            color = color,
            radius = 6.dp.toPx(),
            center = center
        )
    }
}

/**
 * Scanning animation — a radial sweep effect.
 */
@Composable
fun ScanPulse(
    modifier: Modifier = Modifier,
    color: Color = NeonCyan,
    size: Dp = 120.dp,
    isScanning: Boolean = true
) {
    if (!isScanning) return

    val infiniteTransition = rememberInfiniteTransition(label = "scanPulse")

    val sweepAngle by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "sweep"
    )

    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 0.8f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = EaseInOutCubic),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse"
    )

    Canvas(modifier = modifier.size(size)) {
        val center = this.center
        val radius = this.size.minDimension / 2f * pulseScale

        // Outer ring
        drawCircle(
            color = color.copy(alpha = 0.15f),
            radius = radius,
            center = center,
            style = Stroke(width = 2.dp.toPx())
        )

        // Sweep arc
        drawArc(
            color = color.copy(alpha = 0.4f),
            startAngle = sweepAngle,
            sweepAngle = 60f,
            useCenter = true,
            topLeft = center.copy(
                x = center.x - radius,
                y = center.y - radius
            ),
            size = androidx.compose.ui.geometry.Size(radius * 2, radius * 2)
        )

        // Center dot
        drawCircle(
            color = color,
            radius = 4.dp.toPx(),
            center = center
        )
    }
}

private val EaseOutCubic: Easing = Easing { t ->
    val f = t - 1f
    f * f * f + 1f
}
