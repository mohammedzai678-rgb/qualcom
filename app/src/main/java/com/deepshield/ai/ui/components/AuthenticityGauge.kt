package com.deepshield.ai.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.deepshield.ai.ui.theme.*

/**
 * Animated circular gauge showing authenticity score (0-100%).
 * Color transitions: red (0-30) → orange (30-60) → green (60-100).
 * Features animated arc fill with spring physics.
 */
@Composable
fun AuthenticityGauge(
    score: Float, // 0f to 100f
    modifier: Modifier = Modifier,
    size: Dp = 180.dp,
    strokeWidth: Dp = 12.dp,
    showLabel: Boolean = true,
    label: String = "Authenticity"
) {
    val animatedScore by animateFloatAsState(
        targetValue = score,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "gaugeScore"
    )

    val scoreColor = when {
        animatedScore < 30f -> DangerRed
        animatedScore < 60f -> WarningOrange
        else -> NeonGreen
    }

    val glowColor = scoreColor.copy(alpha = 0.3f)
    val sweepAngle = (animatedScore / 100f) * 270f

    // Pulsing glow animation
    val infiniteTransition = rememberInfiniteTransition(label = "gaugeGlow")
    val glowAlpha by infiniteTransition.animateFloat(
        initialValue = 0.2f,
        targetValue = 0.5f,
        animationSpec = infiniteRepeatable(
            animation = tween(1500, easing = EaseInOutCubic),
            repeatMode = RepeatMode.Reverse
        ),
        label = "glowAlpha"
    )

    Box(
        modifier = modifier.size(size),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val canvasSize = this.size
            val strokePx = strokeWidth.toPx()
            val arcSize = Size(
                canvasSize.width - strokePx,
                canvasSize.height - strokePx
            )
            val topLeft = Offset(strokePx / 2f, strokePx / 2f)

            // Background track
            drawArc(
                color = GlassBorder,
                startAngle = 135f,
                sweepAngle = 270f,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(width = strokePx, cap = StrokeCap.Round)
            )

            // Glow layer
            drawArc(
                color = glowColor.copy(alpha = glowAlpha),
                startAngle = 135f,
                sweepAngle = sweepAngle,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(width = strokePx + 8.dp.toPx(), cap = StrokeCap.Round)
            )

            // Active arc with gradient
            drawArc(
                brush = Brush.sweepGradient(
                    colors = listOf(
                        scoreColor.copy(alpha = 0.6f),
                        scoreColor
                    )
                ),
                startAngle = 135f,
                sweepAngle = sweepAngle,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(width = strokePx, cap = StrokeCap.Round)
            )
        }

        // Center text
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = "${animatedScore.toInt()}%",
                style = MetricLarge,
                color = scoreColor
            )
            if (showLabel) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelSmall,
                    color = TextTertiary
                )
            }
        }
    }
}

/**
 * Mini version of the gauge for dashboard cards and list items.
 */
@Composable
fun MiniGauge(
    score: Float,
    modifier: Modifier = Modifier,
    size: Dp = 48.dp
) {
    AuthenticityGauge(
        score = score,
        modifier = modifier,
        size = size,
        strokeWidth = 4.dp,
        showLabel = false
    )
}
