package com.deepshield.ai.ui.components

import androidx.compose.animation.core.EaseInOutCubic
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CloudUpload
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.deepshield.ai.ui.theme.CardShape
import com.deepshield.ai.ui.theme.ChipShape
import com.deepshield.ai.ui.theme.GlassWhite
import com.deepshield.ai.ui.theme.MetricMedium
import com.deepshield.ai.ui.theme.NeonCyan
import com.deepshield.ai.ui.theme.NeonGreen
import com.deepshield.ai.ui.theme.PillShape
import com.deepshield.ai.ui.theme.TextDisabled
import com.deepshield.ai.ui.theme.TextPrimary
import com.deepshield.ai.ui.theme.TextTertiary

@Composable
fun MetricCard(
    icon: ImageVector,
    label: String,
    value: String,
    unit: String = "",
    color: Color = NeonCyan,
    modifier: Modifier = Modifier
) {
    GlassCard(
        modifier = modifier,
        contentPadding = 12.dp
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(ChipShape)
                    .background(color.copy(alpha = 0.1f), ChipShape)
                    .border(1.dp, color.copy(alpha = 0.2f), ChipShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = label,
                    tint = color,
                    modifier = Modifier.size(20.dp)
                )
            }

            Column {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelSmall,
                    color = TextTertiary
                )
                Row(
                    verticalAlignment = Alignment.Bottom,
                    horizontalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    Text(
                        text = value,
                        style = MetricMedium,
                        color = color
                    )
                    if (unit.isNotEmpty()) {
                        Text(
                            text = unit,
                            style = MaterialTheme.typography.labelSmall,
                            color = TextTertiary,
                            modifier = Modifier.padding(bottom = 2.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun StatusChip(
    text: String,
    color: Color = NeonGreen,
    modifier: Modifier = Modifier,
    isActive: Boolean = true
) {
    val dotColor = if (isActive) color else TextDisabled
    val infiniteTransition = rememberInfiniteTransition(label = "statusDot")
    val dotAlpha by infiniteTransition.animateFloat(
        initialValue = 0.5f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = EaseInOutCubic),
            repeatMode = androidx.compose.animation.core.RepeatMode.Reverse
        ),
        label = "dotAlpha"
    )

    Row(
        modifier = modifier
            .clip(PillShape)
            .background(color.copy(alpha = 0.08f), PillShape)
            .border(1.dp, color.copy(alpha = 0.2f), PillShape)
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(PillShape)
                .background(dotColor.copy(alpha = if (isActive) dotAlpha else 0.3f))
        )

        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Medium,
            color = color
        )
    }
}

@Composable
fun FileUploadZone(
    onTap: () -> Unit,
    modifier: Modifier = Modifier,
    title: String = "Upload Media",
    subtitle: String = "Tap to select image, video, or audio",
    icon: ImageVector = Icons.Rounded.CloudUpload,
    accentColor: Color = NeonCyan
) {
    val interactionSource = remember { MutableInteractionSource() }

    GlassCard(
        modifier = modifier.fillMaxWidth(),
        glowColor = accentColor.copy(alpha = 0.15f),
        contentPadding = 0.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(CardShape)
                .background(accentColor.copy(alpha = 0.03f))
                .clickable(
                    interactionSource = interactionSource,
                    indication = null,
                    onClick = onTap
                )
                .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .clip(CardShape)
                    .background(accentColor.copy(alpha = 0.1f), CardShape)
                    .border(1.dp, accentColor.copy(alpha = 0.3f), CardShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = title,
                    tint = accentColor,
                    modifier = Modifier.size(32.dp)
                )
            }

            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = TextPrimary
            )

            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = TextTertiary
            )
        }
    }
}

@Composable
fun ScanProgressBar(
    progress: Float,
    modifier: Modifier = Modifier,
    color: Color = NeonCyan,
    label: String? = null
) {
    val animatedProgress by animateFloatAsState(
        targetValue = progress.coerceIn(0f, 1f),
        animationSpec = tween(600, easing = EaseInOutCubic),
        label = "progress"
    )

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        if (label != null) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelSmall,
                    color = TextTertiary
                )
                Text(
                    text = "${(animatedProgress * 100).toInt()}%",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = color
                )
            }
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(6.dp)
                .clip(PillShape)
                .background(GlassWhite, PillShape)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(animatedProgress)
                    .clip(PillShape)
                    .background(
                        brush = Brush.horizontalGradient(
                            colors = listOf(color.copy(alpha = 0.6f), color)
                        ),
                        shape = PillShape
                    )
            )
        }
    }
}
