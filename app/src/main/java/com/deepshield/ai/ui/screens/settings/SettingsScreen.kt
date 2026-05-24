package com.deepshield.ai.ui.screens.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.deepshield.ai.ui.components.*
import com.deepshield.ai.ui.theme.*

@Composable
fun SettingsScreen(
    onNavigateToAudio: () -> Unit = {},
    onNavigateToPrivacy: () -> Unit = {},
    onNavigateToPerformance: () -> Unit = {},
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    LazyColumn(
        modifier = Modifier.fillMaxSize().background(CyberBlack),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Column(modifier = Modifier.padding(top = 8.dp)) {
                Text("Settings", style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold, color = TextPrimary)
                Text("Configure DeepShield AI", style = MaterialTheme.typography.bodySmall, color = TextTertiary)
            }
        }

        // More Modules (accessible from settings)
        item {
            Text("More Modules", style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold, color = TextSecondary)
        }
        item {
            SettingsNavItem(Icons.Rounded.Mic, "Audio Detection", "Voice clone & synthetic speech",
                WarningOrange, onNavigateToAudio)
        }
        item {
            SettingsNavItem(Icons.Rounded.Security, "Privacy Auditor", "EXIF & sensitive content",
                DeepPurple, onNavigateToPrivacy)
        }
        item {
            SettingsNavItem(Icons.Rounded.Speed, "NPU Performance", "Optimization controls",
                NeonCyan, onNavigateToPerformance)
        }

        // Detection Settings
        item {
            Spacer(Modifier.height(4.dp))
            Text("Detection Configuration", style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold, color = TextSecondary)
        }
        item {
            GlassCard(modifier = Modifier.fillMaxWidth()) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Alert Threshold", style = MaterialTheme.typography.bodyMedium, color = TextPrimary)
                    Text("${state.detectionThreshold.toInt()}%", style = MetricSmall, color = NeonCyan)
                }
                Spacer(Modifier.height(4.dp))
                Text("Trigger deepfake alert when confidence exceeds this threshold",
                    style = MaterialTheme.typography.labelSmall, color = TextTertiary)
                Slider(value = state.detectionThreshold, onValueChange = viewModel::updateDetectionThreshold,
                    valueRange = 50f..99f,
                    colors = SliderDefaults.colors(thumbColor = NeonCyan, activeTrackColor = NeonCyan))
            }
        }

        // App Info
        item {
            Spacer(Modifier.height(4.dp))
            Text("About", style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold, color = TextSecondary)
        }
        item {
            GlassCard(modifier = Modifier.fillMaxWidth()) {
                InfoRow("App Version", "1.0.0")
                InfoRow("Build", "Hackathon MVP")
                InfoRow("Target Device", "Snapdragon 8 Gen 3")
                InfoRow("Inference Engine", "QNN SDK + TFLite")
                InfoRow("Quantization", "INT8 (Primary)")
                InfoRow("Privacy", "Zero Cloud · Air-Gapped")
                InfoRow("Standard", "C2PA v2.1 Compliant")
            }
        }

        // Architecture
        item {
            GlassCard(modifier = Modifier.fillMaxWidth(), glowColor = DeepPurple.copy(alpha = 0.1f)) {
                Text("Architecture", style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold, color = DeepPurple)
                Spacer(Modifier.height(8.dp))
                Text("MVVM + Clean Architecture", style = MaterialTheme.typography.bodySmall, color = TextPrimary)
                Text("Kotlin · Jetpack Compose · Material 3", style = CodeText, color = TextTertiary)
                Text("TFLite · ONNX Runtime · QNN SDK", style = CodeText, color = TextTertiary)
                Text("Room + SQLCipher · Hilt · CameraX", style = CodeText, color = TextTertiary)
                Spacer(Modifier.height(8.dp))
                StatusChip(text = "Privacy First — No Internet Permission", color = NeonGreen)
            }
        }

        item {
            NeonOutlineButton(
                text = if (state.hasScanHistory) "Clear Scan History" else "No Scan History Yet",
                onClick = {
                    if (state.hasScanHistory) {
                        viewModel.clearScanHistory()
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                borderColor = DangerRed,
                textColor = DangerRed
            )
        }

        item { Spacer(Modifier.height(80.dp)) }
    }
}

@Composable
private fun SettingsNavItem(icon: ImageVector, title: String, desc: String, color: Color, onClick: () -> Unit) {
    GlassCard(modifier = Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(40.dp).clip(ChipShape).background(color.copy(alpha = 0.1f), ChipShape)
                .border(1.dp, color.copy(alpha = 0.25f), ChipShape),
                contentAlignment = Alignment.Center) {
                Icon(icon, null, tint = color, modifier = Modifier.size(20.dp))
            }
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Medium, color = TextPrimary)
                Text(desc, style = MaterialTheme.typography.labelSmall, color = TextTertiary)
            }
            Icon(Icons.Rounded.ChevronRight, null, tint = TextTertiary, modifier = Modifier.size(20.dp))
        }
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, style = MaterialTheme.typography.bodySmall, color = TextTertiary)
        Text(value, style = CodeText, color = NeonCyan)
    }
}
