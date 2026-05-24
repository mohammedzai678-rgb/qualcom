package com.deepshield.ai.ui.screens.forensics

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.deepshield.ai.domain.model.DetectedArtifact
import com.deepshield.ai.domain.model.ScanResult
import com.deepshield.ai.domain.model.ScanVerdict
import com.deepshield.ai.ui.components.AuthenticityGauge
import com.deepshield.ai.ui.components.ConfidenceBadge
import com.deepshield.ai.ui.components.ConfidenceChip
import com.deepshield.ai.ui.components.GlassCard
import com.deepshield.ai.ui.components.GradientGlassCard
import com.deepshield.ai.ui.components.ScanProgressBar
import com.deepshield.ai.ui.components.StatusChip
import com.deepshield.ai.ui.components.Verdict
import com.deepshield.ai.ui.theme.CodeText
import com.deepshield.ai.ui.theme.CyberBlack
import com.deepshield.ai.ui.theme.DangerRed
import com.deepshield.ai.ui.theme.DeepPurple
import com.deepshield.ai.ui.theme.NeonCyan
import com.deepshield.ai.ui.theme.NeonGreen
import com.deepshield.ai.ui.theme.TextPrimary
import com.deepshield.ai.ui.theme.TextSecondary
import com.deepshield.ai.ui.theme.TextTertiary
import com.deepshield.ai.ui.theme.WarningOrange
import com.deepshield.ai.ui.theme.WarningAmber
import com.deepshield.ai.ui.theme.MetricSmall

@Composable
fun ForensicsScreen(
    scanId: String = "",
    viewModel: ForensicsViewModel = hiltViewModel()
) {
    val scanResult = viewModel.scanResult.collectAsStateWithLifecycle().value

    LazyColumn(
        modifier = Modifier.fillMaxSize().background(CyberBlack),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Column(modifier = Modifier.padding(top = 8.dp)) {
                Text(
                    text = "Forensic Report",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )
                Text(
                    text = scanResult?.fileName?.ifEmpty { "Scan ${scanId.take(8)}" } ?: "No completed scan found",
                    style = CodeText,
                    color = TextTertiary
                )
            }
        }

        if (scanResult == null) {
            item {
                GlassCard(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = "Run an image scan before opening this screen so the report can be generated from a real result.",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextTertiary
                    )
                }
            }
        } else {
            item {
                SummaryCard(scanResult)
            }

            item {
                GlassCard(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = "Detection Pipeline Results",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = TextPrimary
                    )
                    Spacer(Modifier.height(12.dp))

                    scanResult.modelScores.forEach { (model, score) ->
                        val color = when {
                            score < 30f -> DangerRed
                            score < 60f -> WarningOrange
                            else -> NeonGreen
                        }
                        Row(
                            Modifier.fillMaxWidth().padding(vertical = 2.dp),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(model, style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                            Text(
                                text = "${score.toInt()}%",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = color
                            )
                        }
                        ScanProgressBar(progress = score / 100f, color = color)
                        Spacer(Modifier.height(6.dp))
                    }
                }
            }

            scanResult.aiAttribution?.let { attribution ->
                item {
                    GlassCard(modifier = Modifier.fillMaxWidth(), glowColor = DangerRed.copy(alpha = 0.1f)) {
                        Text(
                            text = "AI Source Attribution",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = DangerRed
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = "Architecture: ${attribution.architectureType}",
                            style = MaterialTheme.typography.labelSmall,
                            color = TextTertiary
                        )
                        Spacer(Modifier.height(12.dp))

                        attribution.topModels.forEachIndexed { index, candidate ->
                            Row(
                                Modifier.fillMaxWidth().padding(vertical = 4.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "#${index + 1} ${candidate.name}",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = TextPrimary
                                )
                                ConfidenceChip(score = candidate.confidence)
                            }
                        }
                    }
                }
            }

            item {
                ArtifactCard(scanResult)
            }

            item {
                GlassCard(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Watermark Status",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = TextPrimary
                        )
                        StatusChip(text = "NOT VERIFIED", color = WarningOrange, isActive = false)
                    }
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = "Use the Shield screen to embed or verify provenance for media related to this report.",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextTertiary
                    )
                }
            }
        }

        item { Spacer(Modifier.height(80.dp)) }
    }
}

@Composable
private fun SummaryCard(scanResult: ScanResult) {
    val verdict = when (scanResult.verdict) {
        ScanVerdict.AUTHENTIC -> Verdict.AUTHENTIC
        ScanVerdict.SUSPICIOUS -> Verdict.SUSPICIOUS
        ScanVerdict.DEEPFAKE -> Verdict.DEEPFAKE
        ScanVerdict.UNKNOWN -> Verdict.UNKNOWN
    }
    val gradientColors = when (scanResult.verdict) {
        ScanVerdict.AUTHENTIC -> listOf(NeonGreen, NeonCyan)
        ScanVerdict.SUSPICIOUS -> listOf(WarningOrange, WarningAmber)
        ScanVerdict.DEEPFAKE -> listOf(DangerRed, WarningOrange)
        ScanVerdict.UNKNOWN -> listOf(DeepPurple, NeonCyan)
    }

    GradientGlassCard(modifier = Modifier.fillMaxWidth(), gradientColors = gradientColors) {
        Column(
            Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "Executive Summary",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = TextSecondary
            )
            ConfidenceBadge(verdict = verdict)
            AuthenticityGauge(score = scanResult.authenticityScore, size = 120.dp, label = "Authenticity")

            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                SummaryStat("${scanResult.confidenceScore.toInt()}%", "Confidence", NeonCyan)
                SummaryStat("${scanResult.processingTimeMs}ms", "Processing", DeepPurple)
                SummaryStat("${scanResult.detectedArtifacts.size}", "Artifacts", DangerRed)
            }
        }
    }
}

@Composable
private fun SummaryStat(value: String, label: String, color: androidx.compose.ui.graphics.Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, style = MetricSmall, color = color)
        Text(label, style = MaterialTheme.typography.labelSmall, color = TextTertiary)
    }
}

@Composable
private fun ArtifactCard(scanResult: ScanResult) {
    GlassCard(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = "Detected Artifacts",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            color = TextPrimary
        )
        Spacer(Modifier.height(12.dp))

        if (scanResult.detectedArtifacts.isEmpty()) {
            Text(
                text = "No suspicious artifacts were recorded for this scan.",
                style = MaterialTheme.typography.bodySmall,
                color = TextTertiary
            )
        } else {
            scanResult.detectedArtifacts.forEach { artifact ->
                ArtifactRow(artifact)
            }
        }
    }
}

@Composable
private fun ArtifactRow(artifact: DetectedArtifact) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 6.dp),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        androidx.compose.material3.Icon(
            imageVector = Icons.Rounded.Warning,
            contentDescription = null,
            tint = DangerRed,
            modifier = Modifier.size(18.dp)
        )
        Column(Modifier.weight(1f)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(
                    text = artifact.type.name.replace('_', ' '),
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.SemiBold,
                    color = TextPrimary
                )
                ConfidenceChip(score = artifact.confidence)
            }
            Text(
                text = artifact.description.ifEmpty { "Detector flagged this artifact during analysis." },
                style = MaterialTheme.typography.labelSmall,
                color = TextTertiary
            )
        }
    }
}
