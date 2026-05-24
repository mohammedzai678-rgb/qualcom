package com.deepshield.ai.ui.screens.dashboard

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.deepshield.ai.domain.model.*
import com.deepshield.ai.ui.components.*
import com.deepshield.ai.ui.theme.*

@Composable
fun DashboardScreen(
    onNavigateToScanner: () -> Unit = {},
    onNavigateToLiveCamera: () -> Unit = {},
    onNavigateToAudio: () -> Unit = {},
    onNavigateToWatermark: () -> Unit = {},
    onNavigateToHeatmap: (String) -> Unit = {},
    onNavigateToForensics: (String) -> Unit = {},
    viewModel: DashboardViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(CyberBlack),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // ============================================================
        // Header
        // ============================================================
        item {
            Column(
                modifier = Modifier.padding(top = 8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "DeepShield AI",
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary
                        )
                        Text(
                            text = "Detect · Expose · Protect",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextTertiary
                        )
                    }

                    StatusChip(
                        text = "Offline Mode",
                        color = NeonGreen,
                        isActive = state.isOffline
                    )
                }

                Spacer(modifier = Modifier.height(4.dp))

                // NPU Status Bar
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(ChipShape)
                        .background(SurfaceCard.copy(alpha = 0.6f), ChipShape)
                        .border(1.dp, GlassBorder, ChipShape)
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    NpuStatusItem(
                        label = "NPU",
                        value = "${state.npuMetrics.npuUtilizationPercent.toInt()}%",
                        color = NeonCyan
                    )
                    NpuStatusItem(
                        label = "FPS",
                        value = "${state.npuMetrics.fps.toInt()}",
                        color = NeonGreen
                    )
                    NpuStatusItem(
                        label = "Latency",
                        value = "${state.npuMetrics.inferenceLatencyMs}ms",
                        color = DeepPurple
                    )
                    NpuStatusItem(
                        label = "Thermal",
                        value = "${state.npuMetrics.thermalCelsius}°C",
                        color = if (state.npuMetrics.thermalCelsius > 42) WarningOrange else NeonGreen
                    )
                }
            }
        }

        // ============================================================
        // Trust Score Gauge
        // ============================================================
        item {
            GradientGlassCard(
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "Media Trust Score",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = TextSecondary
                    )

                    AuthenticityGauge(
                        score = state.mediaTrustScore,
                        size = 160.dp,
                        strokeWidth = 14.dp,
                        label = "Overall Trust"
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        StatItem(
                            value = "${state.totalScans}",
                            label = "Total Scans",
                            color = NeonCyan
                        )
                        StatItem(
                            value = "${state.threatsDetected}",
                            label = "Threats",
                            color = DangerRed
                        )
                        StatItem(
                            value = "${state.mediaAuthenticated}",
                            label = "Verified",
                            color = NeonGreen
                        )
                    }
                }
            }
        }

        // ============================================================
        // Quick Actions
        // ============================================================
        item {
            Text(
                text = "Quick Actions",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = TextPrimary,
                modifier = Modifier.padding(top = 8.dp)
            )
        }

        item {
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                item {
                    QuickActionCard(
                        icon = Icons.Rounded.ImageSearch,
                        title = "Scan Media",
                        subtitle = "Still Images",
                        color = NeonCyan,
                        onClick = onNavigateToScanner
                    )
                }
                item {
                    QuickActionCard(
                        icon = Icons.Rounded.CameraAlt,
                        title = "Live Camera",
                        subtitle = "Real-time 30 FPS",
                        color = NeonGreen,
                        onClick = onNavigateToLiveCamera
                    )
                }
                item {
                    QuickActionCard(
                        icon = Icons.Rounded.VerifiedUser,
                        title = "Watermark",
                        subtitle = "Embed / Verify",
                        color = DeepPurple,
                        onClick = onNavigateToWatermark
                    )
                }
                item {
                    QuickActionCard(
                        icon = Icons.Rounded.Mic,
                        title = "Audio Scan",
                        subtitle = "Voice Clone",
                        color = WarningOrange,
                        onClick = onNavigateToAudio
                    )
                }
            }
        }

        // ============================================================
        // Threat Alerts
        // ============================================================
        if (state.threatAlerts.isNotEmpty()) {
            item {
                Text(
                    text = "Active Threats",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = TextPrimary,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }

            items(state.threatAlerts) { alert ->
                ThreatAlertCard(alert = alert)
            }
        }

        // ============================================================
        // Recent Scans
        // ============================================================
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Recent Scans",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = TextPrimary
                )
                Text(
                    text = "View All",
                    style = MaterialTheme.typography.labelMedium,
                    color = NeonCyan,
                    modifier = Modifier.clickable { onNavigateToScanner() }
                )
            }
        }

        items(state.recentScans) { scan ->
            RecentScanCard(
                scan = scan,
                onTap = { onNavigateToForensics(scan.id) }
            )
        }

        if (state.recentScans.isEmpty()) {
            item {
                GlassCard(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = "No completed scans yet. Run an image scan or start Live Shield to populate the dashboard.",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextTertiary
                    )
                }
            }
        }

        // Bottom spacer for navigation bar
        item {
            Spacer(modifier = Modifier.height(80.dp))
        }
    }
}

// ============================================================
// Sub-components
// ============================================================

@Composable
private fun NpuStatusItem(label: String, value: String, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = value,
            style = MetricSmall,
            fontWeight = FontWeight.Bold,
            color = color
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = TextTertiary,
            fontSize = MaterialTheme.typography.labelSmall.fontSize
        )
    }
}

@Composable
private fun StatItem(value: String, label: String, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = value,
            style = MetricMedium,
            color = color,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = TextTertiary
        )
    }
}

@Composable
private fun QuickActionCard(
    icon: ImageVector,
    title: String,
    subtitle: String,
    color: Color,
    onClick: () -> Unit
) {
    GlassCard(
        modifier = Modifier
            .width(140.dp)
            .clickable(onClick = onClick),
        glowColor = color.copy(alpha = 0.2f),
        contentPadding = 14.dp
    ) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(ChipShape)
                .background(color.copy(alpha = 0.12f), ChipShape)
                .border(1.dp, color.copy(alpha = 0.25f), ChipShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = title,
                tint = color,
                modifier = Modifier.size(22.dp)
            )
        }

        Spacer(modifier = Modifier.height(10.dp))

        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            color = TextPrimary
        )

        Text(
            text = subtitle,
            style = MaterialTheme.typography.labelSmall,
            color = TextTertiary
        )
    }
}

@Composable
private fun ThreatAlertCard(alert: ThreatAlert) {
    val severityColor = when (alert.severity) {
        ThreatSeverity.CRITICAL -> DangerRed
        ThreatSeverity.HIGH -> WarningOrange
        ThreatSeverity.MEDIUM -> WarningAmber
        ThreatSeverity.LOW -> TextTertiary
    }

    GlassCard(
        modifier = Modifier.fillMaxWidth(),
        glowColor = severityColor.copy(alpha = 0.15f)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.Top
        ) {
            // Threat pulse indicator
            Box(
                modifier = Modifier
                    .size(10.dp)
                    .padding(top = 5.dp)
                    .clip(PillShape)
                    .background(severityColor)
            )

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = alert.title,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = TextPrimary,
                        modifier = Modifier.weight(1f)
                    )
                    ConfidenceChip(
                        score = when (alert.severity) {
                            ThreatSeverity.CRITICAL -> 95f
                            ThreatSeverity.HIGH -> 80f
                            ThreatSeverity.MEDIUM -> 55f
                            ThreatSeverity.LOW -> 30f
                        }
                    )
                }

                Text(
                    text = alert.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = TextTertiary
                )

                Text(
                    text = alert.category,
                    style = MaterialTheme.typography.labelSmall,
                    color = severityColor,
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }
}

@Composable
private fun RecentScanCard(
    scan: ScanResult,
    onTap: () -> Unit
) {
    val typeIcon = when (scan.mediaType) {
        MediaType.IMAGE -> Icons.Rounded.Image
        MediaType.VIDEO -> Icons.Rounded.VideoFile
        MediaType.AUDIO -> Icons.Rounded.AudioFile
        MediaType.DOCUMENT -> Icons.Rounded.Description
    }

    val verdictColor = when (scan.verdict) {
        ScanVerdict.AUTHENTIC -> NeonGreen
        ScanVerdict.SUSPICIOUS -> WarningOrange
        ScanVerdict.DEEPFAKE -> DangerRed
        ScanVerdict.UNKNOWN -> TextTertiary
    }

    GlassCard(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onTap)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Media type icon
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(ChipShape)
                    .background(verdictColor.copy(alpha = 0.1f), ChipShape)
                    .border(1.dp, verdictColor.copy(alpha = 0.25f), ChipShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = typeIcon,
                    contentDescription = scan.mediaType.name,
                    tint = verdictColor,
                    modifier = Modifier.size(24.dp)
                )
            }

            // File info
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(
                    text = scan.fileName.ifEmpty { "Unknown File" },
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Medium,
                    color = TextPrimary
                )

                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = scan.mediaType.name,
                        style = MaterialTheme.typography.labelSmall,
                        color = TextTertiary
                    )
                    Text(
                        text = "·",
                        color = TextTertiary
                    )
                    Text(
                        text = "${scan.processingTimeMs}ms",
                        style = MaterialTheme.typography.labelSmall,
                        color = TextTertiary
                    )
                }

                ConfidenceBadge(
                    verdict = when (scan.verdict) {
                        ScanVerdict.AUTHENTIC -> Verdict.AUTHENTIC
                        ScanVerdict.SUSPICIOUS -> Verdict.SUSPICIOUS
                        ScanVerdict.DEEPFAKE -> Verdict.DEEPFAKE
                        ScanVerdict.UNKNOWN -> Verdict.UNKNOWN
                    },
                    showPulse = false
                )
            }

            // Score gauge
            MiniGauge(score = scan.authenticityScore)
        }
    }
}
