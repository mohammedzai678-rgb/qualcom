package com.deepshield.ai.ui.components

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.deepshield.ai.ui.theme.*

/**
 * Verdict badge with animated glow effect.
 * Shows AUTHENTIC (green), SUSPICIOUS (orange), or DEEPFAKE (red).
 */
enum class Verdict {
    AUTHENTIC, SUSPICIOUS, DEEPFAKE, UNKNOWN
}

@Composable
fun ConfidenceBadge(
    verdict: Verdict,
    modifier: Modifier = Modifier,
    showPulse: Boolean = true
) {
    val (text, baseColor, bgColor) = when (verdict) {
        Verdict.AUTHENTIC -> Triple("AUTHENTIC", NeonGreen, NeonGreen.copy(alpha = 0.12f))
        Verdict.SUSPICIOUS -> Triple("SUSPICIOUS", WarningOrange, WarningOrange.copy(alpha = 0.12f))
        Verdict.DEEPFAKE -> Triple("DEEPFAKE DETECTED", DangerRed, DangerRed.copy(alpha = 0.12f))
        Verdict.UNKNOWN -> Triple("UNVERIFIED", TextTertiary, GlassWhite)
    }

    // Pulse animation for non-authentic verdicts
    val infiniteTransition = rememberInfiniteTransition(label = "badgePulse")
    val borderAlpha by infiniteTransition.animateFloat(
        initialValue = 0.4f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = if (verdict == Verdict.DEEPFAKE) 600 else 1200,
                easing = EaseInOutCubic
            ),
            repeatMode = RepeatMode.Reverse
        ),
        label = "borderAlpha"
    )

    val actualBorderAlpha = if (showPulse && verdict != Verdict.AUTHENTIC && verdict != Verdict.UNKNOWN) {
        borderAlpha
    } else {
        0.6f
    }

    Box(
        modifier = modifier
            .clip(PillShape)
            .border(
                width = 1.dp,
                color = baseColor.copy(alpha = actualBorderAlpha),
                shape = PillShape
            )
            .background(bgColor, PillShape)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            color = baseColor,
            letterSpacing = MaterialTheme.typography.labelMedium.letterSpacing
        )
    }
}

/**
 * Small inline confidence chip for list items.
 */
@Composable
fun ConfidenceChip(
    score: Float, // 0-100
    modifier: Modifier = Modifier
) {
    val color = when {
        score < 30f -> DangerRed
        score < 60f -> WarningOrange
        else -> NeonGreen
    }

    Box(
        modifier = modifier
            .clip(ChipShape)
            .background(color.copy(alpha = 0.12f), ChipShape)
            .border(1.dp, color.copy(alpha = 0.3f), ChipShape)
            .padding(horizontal = 8.dp, vertical = 4.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = "${score.toInt()}%",
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = color
        )
    }
}
