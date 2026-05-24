package com.deepshield.ai.ui.screens.heatmap

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.deepshield.ai.domain.model.ArtifactType
import com.deepshield.ai.ui.components.GlassCard
import com.deepshield.ai.ui.components.HeatmapOverlay
import com.deepshield.ai.ui.components.ScanProgressBar
import com.deepshield.ai.ui.theme.BadgeShape
import com.deepshield.ai.ui.theme.CardShape
import com.deepshield.ai.ui.theme.CyberBlack
import com.deepshield.ai.ui.theme.DangerRed
import com.deepshield.ai.ui.theme.HeatmapHigh
import com.deepshield.ai.ui.theme.HeatmapLow
import com.deepshield.ai.ui.theme.HeatmapMedium
import com.deepshield.ai.ui.theme.HeatmapSafe
import com.deepshield.ai.ui.theme.NeonCyan
import com.deepshield.ai.ui.theme.NeonGreen
import com.deepshield.ai.ui.theme.SurfaceCard
import com.deepshield.ai.ui.theme.SurfaceDark
import com.deepshield.ai.ui.theme.TextPrimary
import com.deepshield.ai.ui.theme.TextSecondary
import com.deepshield.ai.ui.theme.TextTertiary
import com.deepshield.ai.ui.theme.WarningOrange

@Composable
fun HeatmapScreen(
    scanId: String = "",
    viewModel: HeatmapViewModel = hiltViewModel()
) {
    var overlayAlpha by remember { mutableFloatStateOf(0.45f) }
    var showGrid by remember { mutableStateOf(false) }
    val scanResult by viewModel.scanResult.collectAsStateWithLifecycle()
    val heatmapData = scanResult?.heatmapData
    val previewBitmap = remember(scanResult?.id, scanResult?.previewImageBytes) {
        scanResult?.previewImageBytes?.let { bytes ->
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
        }
    }

    val regionBreakdown = scanResult
        ?.detectedArtifacts
        ?.takeIf { it.isNotEmpty() }
        ?.map { artifact ->
            val label = when (artifact.type) {
                ArtifactType.FACE_SWAP -> "Face texture anomaly"
                ArtifactType.GAN_ARTIFACT -> "Patch texture anomaly"
                ArtifactType.FREQUENCY_ANOMALY -> "Frequency anomaly"
                ArtifactType.METADATA_MISMATCH -> "Metadata inconsistency"
                else -> artifact.type.name.replace('_', ' ')
            }
            label to artifact.confidence
        }
        ?: emptyList()

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
                    text = "Explainability Heatmap",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )
                Text(
                    text = scanResult?.fileName?.ifEmpty { "Scan ${scanId.take(8)}" }
                        ?: "No completed scan found for this heatmap",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextTertiary
                )
            }
        }

        item {
            GlassCard(modifier = Modifier.fillMaxWidth(), contentPadding = 0.dp) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(350.dp)
                        .clip(CardShape)
                        .background(SurfaceCard)
                ) {
                    if (previewBitmap != null) {
                        Image(
                            bitmap = previewBitmap.asImageBitmap(),
                            contentDescription = "Scanned media preview",
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Fit
                        )
                    } else {
                        Box(modifier = Modifier.fillMaxSize().background(SurfaceDark))
                    }

                    if (heatmapData != null) {
                        HeatmapOverlay(
                            heatmapData = heatmapData,
                            alpha = overlayAlpha,
                            showGrid = showGrid
                        )
                    }
                }
            }
        }

        if (scanResult == null || heatmapData == null) {
            item {
                GlassCard(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = "Run an image scan first to populate this screen with real model output.",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextTertiary
                    )
                }
            }
        }

        item {
            GlassCard(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = "Overlay Controls",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = TextPrimary
                )
                Spacer(Modifier.height(12.dp))

                Text(
                    text = "Opacity: ${(overlayAlpha * 100).toInt()}%",
                    style = MaterialTheme.typography.labelSmall,
                    color = TextTertiary
                )
                Slider(
                    value = overlayAlpha,
                    onValueChange = { overlayAlpha = it },
                    valueRange = 0f..1f,
                    colors = SliderDefaults.colors(
                        thumbColor = NeonCyan,
                        activeTrackColor = NeonCyan
                    )
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Show Grid",
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextSecondary
                    )
                    Switch(
                        checked = showGrid,
                        onCheckedChange = { showGrid = it },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = NeonCyan,
                            checkedTrackColor = NeonCyan.copy(alpha = 0.3f)
                        )
                    )
                }
            }
        }

        item {
            GlassCard(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = "Color Legend",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = TextPrimary
                )
                Spacer(Modifier.height(8.dp))
                LegendItem(color = HeatmapHigh, label = "Highly Suspicious", desc = "Strong local anomaly")
                LegendItem(color = HeatmapMedium, label = "Moderate Suspicion", desc = "Patch needs review")
                LegendItem(color = HeatmapLow, label = "Low Suspicion", desc = "Minor deviation")
                LegendItem(color = HeatmapSafe, label = "Verified Safe", desc = "No notable anomaly")
            }
        }

        item {
            GlassCard(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = "Region Analysis",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = TextPrimary
                )
                Spacer(Modifier.height(8.dp))

                if (regionBreakdown.isEmpty()) {
                    Text(
                        text = "No region-specific artifacts were recorded for this scan.",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextTertiary
                    )
                } else {
                    regionBreakdown.forEach { (region, score) ->
                        val color = when {
                            score > 70 -> DangerRed
                            score > 40 -> WarningOrange
                            else -> NeonGreen
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(region, style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                            Text(
                                text = "${score.toInt()}%",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = color
                            )
                        }
                        ScanProgressBar(progress = score / 100f, color = color)
                        Spacer(Modifier.height(4.dp))
                    }
                }
            }
        }

        item { Spacer(Modifier.height(80.dp)) }
    }
}

@Composable
private fun LegendItem(color: Color, label: String, desc: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.height(16.dp).fillMaxWidth(0.04f).clip(BadgeShape).background(color))
        Column {
            Text(label, style = MaterialTheme.typography.bodySmall, color = TextPrimary)
            Text(desc, style = MaterialTheme.typography.labelSmall, color = TextTertiary)
        }
    }
}
