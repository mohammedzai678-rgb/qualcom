package com.deepshield.ai.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.deepshield.ai.ui.theme.*

/**
 * Glassmorphism card — the signature visual element of DeepShield.
 * Semi-transparent background with subtle border and optional glow.
 */
@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    glowColor: Color? = null,
    borderColor: Color = GlassBorder,
    contentPadding: Dp = 16.dp,
    content: @Composable ColumnScope.() -> Unit
) {
    val backgroundBrush = Brush.verticalGradient(
        colors = listOf(
            GlassHighlight,
            GlassWhite
        )
    )

    Box(
        modifier = modifier
            .clip(CardShape)
            .then(
                if (glowColor != null) {
                    Modifier.border(
                        width = 1.dp,
                        brush = Brush.linearGradient(
                            colors = listOf(glowColor.copy(alpha = 0.5f), glowColor.copy(alpha = 0.1f))
                        ),
                        shape = CardShape
                    )
                } else {
                    Modifier.border(
                        width = 1.dp,
                        color = borderColor,
                        shape = CardShape
                    )
                }
            )
            .background(brush = backgroundBrush, shape = CardShape)
    ) {
        Column(
            modifier = Modifier.padding(contentPadding),
            content = content
        )
    }
}

/**
 * Elevated glass card with stronger background for primary content areas.
 */
@Composable
fun ElevatedGlassCard(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    Box(
        modifier = modifier
            .clip(CardShape)
            .border(1.dp, GlassBorder, CardShape)
            .background(SurfaceCard, CardShape)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            content = content
        )
    }
}

/**
 * Gradient-bordered glass card for highlighted/featured content.
 */
@Composable
fun GradientGlassCard(
    modifier: Modifier = Modifier,
    gradientColors: List<Color> = listOf(NeonCyan, DeepPurple),
    content: @Composable ColumnScope.() -> Unit
) {
    Box(
        modifier = modifier
            .clip(CardShape)
            .border(
                width = 1.dp,
                brush = Brush.linearGradient(gradientColors),
                shape = CardShape
            )
            .background(
                brush = Brush.verticalGradient(
                    colors = listOf(
                        gradientColors.first().copy(alpha = 0.08f),
                        GlassWhite
                    )
                ),
                shape = CardShape
            )
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            content = content
        )
    }
}
