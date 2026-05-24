package com.deepshield.ai.ui.screens.audio

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.deepshield.ai.domain.model.ModelCandidate
import com.deepshield.ai.domain.model.ScanResult
import com.deepshield.ai.domain.model.ScanVerdict
import com.deepshield.ai.ui.components.AuthenticityGauge
import com.deepshield.ai.ui.components.ConfidenceBadge
import com.deepshield.ai.ui.components.ConfidenceChip
import com.deepshield.ai.ui.components.FileUploadZone
import com.deepshield.ai.ui.components.GlassCard
import com.deepshield.ai.ui.components.GradientGlassCard
import com.deepshield.ai.ui.components.ScanProgressBar
import com.deepshield.ai.ui.components.Verdict
import com.deepshield.ai.ui.theme.CyberBlack
import com.deepshield.ai.ui.theme.DangerRed
import com.deepshield.ai.ui.theme.MetricSmall
import com.deepshield.ai.ui.theme.NeonCyan
import com.deepshield.ai.ui.theme.NeonGreen
import com.deepshield.ai.ui.theme.TextPrimary
import com.deepshield.ai.ui.theme.TextSecondary
import com.deepshield.ai.ui.theme.TextTertiary
import com.deepshield.ai.ui.theme.WarningAmber
import com.deepshield.ai.ui.theme.WarningOrange
import com.deepshield.ai.util.readByteSample
import com.deepshield.ai.util.readFileInfo

@Composable
fun AudioScreen(
    viewModel: AudioViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = androidx.compose.ui.platform.LocalContext.current

    val audioPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        val fileInfo = context.contentResolver.readFileInfo(uri)
        val sample = context.contentResolver.readByteSample(uri)
        if (sample != null && sample.isNotEmpty()) {
            viewModel.analyzeAudio(
                uri = uri,
                fileName = fileInfo.name,
                fileSize = fileInfo.size
            )
        }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(CyberBlack),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Column(modifier = Modifier.padding(top = 8.dp)) {
                Text(
                    text = "Audio Deepfake Detection",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )
                Text(
                    text = "Offline voice-clone and synthetic-speech heuristics",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextTertiary
                )
            }
        }

        item {
            FileUploadZone(
                onTap = { audioPickerLauncher.launch("audio/*") },
                title = "Upload Audio",
                subtitle = "Pick a local WAV, MP3, OGG, or FLAC clip",
                icon = Icons.Rounded.Mic,
                accentColor = WarningOrange
            )
        }

        item {
            GlassCard(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = state.selectedFileName ?: "No clip selected yet",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = TextPrimary
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = state.statusMessage,
                    style = MaterialTheme.typography.bodySmall,
                    color = TextTertiary
                )
            }
        }

        if (state.isAnalyzing) {
            item {
                GlassCard(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(22.dp),
                            strokeWidth = 2.dp,
                            color = WarningOrange
                        )
                        Text(
                            text = "Inspecting spectral, cadence, and speaker-consistency features...",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextSecondary
                        )
                    }
                }
            }
        }

        state.currentResult?.let { result ->
            item {
                GradientGlassCard(
                    modifier = Modifier.fillMaxWidth(),
                    gradientColors = when (result.verdict) {
                        ScanVerdict.AUTHENTIC -> listOf(NeonGreen, NeonCyan)
                        ScanVerdict.SUSPICIOUS -> listOf(WarningOrange, WarningAmber)
                        ScanVerdict.DEEPFAKE -> listOf(DangerRed, WarningOrange)
                        ScanVerdict.UNKNOWN -> listOf(NeonCyan, WarningOrange)
                    }
                ) {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        ConfidenceBadge(verdict = result.verdict.toUiVerdict())
                        AuthenticityGauge(
                            score = result.authenticityScore,
                            size = 130.dp,
                            label = "Voice Authenticity"
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceEvenly
                        ) {
                            MetricColumn(
                                value = "${(100f - result.authenticityScore).toInt()}%",
                                label = "Synthetic"
                            )
                            MetricColumn(
                                value = "${result.confidenceScore.toInt()}%",
                                label = "Confidence"
                            )
                            MetricColumn(
                                value = "${result.processingTimeMs}ms",
                                label = "Latency"
                            )
                        }
                    }
                }
            }

            item {
                GlassCard(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = "Analysis Breakdown",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = TextPrimary
                    )
                    Spacer(modifier = Modifier.height(12.dp))

                    result.modelScores.forEach { (name, score) ->
                        val color = when {
                            score < 30f -> DangerRed
                            score < 60f -> WarningOrange
                            else -> NeonGreen
                        }
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = name,
                                style = MaterialTheme.typography.bodySmall,
                                color = TextSecondary
                            )
                            Text(
                                text = "${score.toInt()}%",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = color
                            )
                        }
                        ScanProgressBar(progress = score / 100f, color = color)
                        Spacer(modifier = Modifier.height(4.dp))
                    }
                }
            }

            result.aiAttribution?.let { attribution ->
                item {
                    GlassCard(modifier = Modifier.fillMaxWidth(), glowColor = DangerRed.copy(alpha = 0.1f)) {
                        Text(
                            text = "Engine Attribution",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = DangerRed
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Most likely family: ${attribution.architectureType}",
                            style = MaterialTheme.typography.labelSmall,
                            color = TextTertiary
                        )
                        Spacer(modifier = Modifier.height(12.dp))

                        attribution.topModels.forEach { candidate ->
                            AttributionRow(candidate)
                        }
                    }
                }
            }
        }

        if (state.recentAudioScans.isNotEmpty()) {
            item {
                Text(
                    text = "Recent Audio Scans",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = TextPrimary
                )
            }

            items(state.recentAudioScans) { result ->
                AudioHistoryCard(result = result)
            }
        }

        item { Spacer(modifier = Modifier.height(80.dp)) }
    }
}

@Composable
private fun MetricColumn(value: String, label: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = value,
            style = MetricSmall,
            color = TextPrimary
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = TextTertiary
        )
    }
}

@Composable
private fun AttributionRow(candidate: ModelCandidate) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column {
            Text(
                text = candidate.name,
                style = MaterialTheme.typography.bodyMedium,
                color = TextPrimary
            )
            Text(
                text = candidate.architecture,
                style = MaterialTheme.typography.labelSmall,
                color = TextTertiary
            )
        }
        ConfidenceChip(score = candidate.confidence)
    }
}

@Composable
private fun AudioHistoryCard(result: ScanResult) {
    GlassCard(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = result.fileName.ifEmpty { "Audio Clip" },
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextPrimary
                )
                Text(
                    text = "${result.processingTimeMs}ms local pass",
                    style = MaterialTheme.typography.labelSmall,
                    color = TextTertiary
                )
            }
            ConfidenceChip(score = result.authenticityScore)
        }
    }
}

private fun ScanVerdict.toUiVerdict(): Verdict = when (this) {
    ScanVerdict.AUTHENTIC -> Verdict.AUTHENTIC
    ScanVerdict.SUSPICIOUS -> Verdict.SUSPICIOUS
    ScanVerdict.DEEPFAKE -> Verdict.DEEPFAKE
    ScanVerdict.UNKNOWN -> Verdict.UNKNOWN
}
