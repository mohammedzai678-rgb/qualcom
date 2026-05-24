package com.deepshield.ai.ui.components

import androidx.compose.animation.core.*
import kotlin.math.pow
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.deepshield.ai.ui.theme.*

/**
 * Animated number counter with smooth spring interpolation.
 * Used for dashboard stats (Total Scans, Threats, etc.)
 */
@Composable
fun AnimatedCounter(
    targetValue: Int,
    modifier: Modifier = Modifier,
    color: Color = TextPrimary,
    prefix: String = "",
    suffix: String = ""
) {
    val animatedValue by animateIntAsState(
        targetValue = targetValue,
        animationSpec = tween(
            durationMillis = 1200,
            easing = EaseOutExpo
        ),
        label = "counter"
    )

    Text(
        text = "$prefix${animatedValue}$suffix",
        style = MetricLarge,
        color = color,
        modifier = modifier
    )
}

/**
 * Small animated counter for inline metrics.
 */
@Composable
fun AnimatedCounterSmall(
    targetValue: Int,
    modifier: Modifier = Modifier,
    color: Color = TextPrimary,
    prefix: String = "",
    suffix: String = ""
) {
    val animatedValue by animateIntAsState(
        targetValue = targetValue,
        animationSpec = tween(durationMillis = 800, easing = EaseOutExpo),
        label = "counterSmall"
    )

    Text(
        text = "$prefix${animatedValue}$suffix",
        style = MetricMedium,
        color = color,
        modifier = modifier
    )
}

/**
 * Neon CTA button with gradient background and press animation.
 */
@Composable
fun NeonButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    gradientColors: List<Color> = listOf(NeonCyan, DeepPurple),
    textColor: Color = CyberBlack
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.96f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
        label = "buttonScale"
    )

    val alpha = if (enabled) 1f else 0.4f

    Box(
        modifier = modifier
            .scale(scale)
            .clip(ButtonShape)
            .background(
                brush = Brush.horizontalGradient(
                    colors = gradientColors.map { it.copy(alpha = alpha) }
                ),
                shape = ButtonShape
            )
            .clickable(
                enabled = enabled,
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            )
            .padding(horizontal = 24.dp, vertical = 14.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
            color = textColor.copy(alpha = alpha)
        )
    }
}

/**
 * Outlined neon button variant.
 */
@Composable
fun NeonOutlineButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    borderColor: Color = NeonCyan,
    textColor: Color = NeonCyan
) {
    Box(
        modifier = modifier
            .clip(ButtonShape)
            .border(
                width = 1.dp,
                color = borderColor.copy(alpha = 0.6f),
                shape = ButtonShape
            )
            .background(borderColor.copy(alpha = 0.06f), ButtonShape)
            .clickable(onClick = onClick)
            .padding(horizontal = 24.dp, vertical = 14.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
            color = textColor
        )
    }
}

private val EaseOutExpo: Easing = Easing { t ->
    if (t == 1f) 1f else 1f - 2.0.pow(-10.0 * t).toFloat()
}
