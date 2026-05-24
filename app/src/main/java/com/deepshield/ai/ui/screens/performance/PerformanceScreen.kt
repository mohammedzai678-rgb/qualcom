package com.deepshield.ai.ui.screens.performance

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.BatteryChargingFull
import androidx.compose.material.icons.rounded.DeveloperBoard
import androidx.compose.material.icons.rounded.Memory
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.rounded.Thermostat
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.deepshield.ai.ui.components.GlassCard
import com.deepshield.ai.ui.components.MetricCard
import com.deepshield.ai.ui.components.StatusChip
import com.deepshield.ai.ui.theme.CodeText
import com.deepshield.ai.ui.theme.CyberBlack
import com.deepshield.ai.ui.theme.DangerRed
import com.deepshield.ai.ui.theme.DeepPurple
import com.deepshield.ai.ui.theme.ElectricBlue
import com.deepshield.ai.ui.theme.MetricSmall
import com.deepshield.ai.ui.theme.NeonCyan
import com.deepshield.ai.ui.theme.NeonGreen
import com.deepshield.ai.ui.theme.TextPrimary
import com.deepshield.ai.ui.theme.TextSecondary
import com.deepshield.ai.ui.theme.TextTertiary
import com.deepshield.ai.ui.theme.WarningOrange

@Composable
fun PerformanceScreen(
    viewModel: PerformanceViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val delegateReady = state.activeDelegate.startsWith("NPU")
    var intFourMode by remember { mutableStateOf(false) }
    var batterySaver by remember { mutableStateOf(false) }
    var thermalProtection by remember { mutableStateOf(true) }
    var npuPriority by remember { mutableStateOf(true) }
    var adaptiveFrameSkip by remember { mutableStateOf(false) }

    LazyColumn(
        modifier = Modifier.fillMaxSize().background(CyberBlack),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Column(modifier = Modifier.padding(top = 8.dp)) {
                Text(
                    "NPU Performance Center",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )
                Text(
                    "Live profiler output from the current detection session",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextTertiary
                )
            }
        }

        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                MetricCard(Icons.Rounded.Speed, "FPS", state.fps.toInt().toString(), "fps", NeonGreen, Modifier.weight(1f))
                MetricCard(Icons.Rounded.Timer, "Latency", String.format("%.1f", state.latencyMs), "ms", NeonCyan, Modifier.weight(1f))
            }
        }
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                MetricCard(Icons.Rounded.Memory, "NPU", state.npuUtilizationPercent.toInt().toString(), "%", DeepPurple, Modifier.weight(1f))
                MetricCard(Icons.Rounded.Thermostat, "Temp", String.format("%.1f", state.thermalCelsius), "C", NeonGreen, Modifier.weight(1f))
            }
        }
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                MetricCard(Icons.Rounded.DeveloperBoard, "CPU", state.cpuUsagePercent.toInt().toString(), "%", ElectricBlue, Modifier.weight(1f))
                MetricCard(Icons.Rounded.BatteryChargingFull, "Power", String.format("%.1f", state.batteryDrainMw / 1000f), "W", WarningOrange, Modifier.weight(1f))
            }
        }

        item {
            GlassCard(modifier = Modifier.fillMaxWidth(), glowColor = NeonCyan.copy(alpha = 0.1f)) {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            "Active Inference Delegate",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = TextPrimary
                        )
                        Text(state.activeDelegate, style = MetricSmall, color = NeonCyan)
                    }
                    StatusChip(
                        text = if (delegateReady) "ACTIVE" else "FALLBACK",
                        color = if (delegateReady) NeonGreen else WarningOrange
                    )
                }
                Spacer(Modifier.height(8.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Quantization", style = MaterialTheme.typography.labelSmall, color = TextTertiary)
                    Text(
                        if (intFourMode) "INT4 (Experimental)" else "INT8 (Primary)",
                        style = CodeText,
                        color = if (intFourMode) WarningOrange else NeonCyan
                    )
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Fallback Chain", style = MaterialTheme.typography.labelSmall, color = TextTertiary)
                    Text("NNAPI -> CPU", style = CodeText, color = TextSecondary)
                }
            }
        }

        item {
            GlassCard(modifier = Modifier.fillMaxWidth()) {
                Text(
                    "Optimization Controls",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = TextPrimary
                )
                Spacer(Modifier.height(12.dp))

                OptToggle("NPU Priority Mode", "Route 100% ops to Hexagon HTP", NeonCyan, npuPriority) { npuPriority = it }
                OptToggle("INT4 Mode (Experimental)", "Higher throughput with accuracy tradeoff", WarningOrange, intFourMode) { intFourMode = it }
                OptToggle("Adaptive Frame Skip", "Analyze fewer frames when the scene is stable", DeepPurple, adaptiveFrameSkip) { adaptiveFrameSkip = it }
                OptToggle("Battery Saver", "Lower power usage for longer sessions", NeonGreen, batterySaver) { batterySaver = it }
                OptToggle("Thermal Protection", "Throttle when device temperature rises", DangerRed, thermalProtection) { thermalProtection = it }
            }
        }

        item {
            GlassCard(modifier = Modifier.fillMaxWidth()) {
                Text(
                    "Loaded Models",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = TextPrimary
                )
                Spacer(Modifier.height(12.dp))

                state.availableModels.forEach { model ->
                    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(model.name, style = MaterialTheme.typography.bodySmall, color = TextPrimary, modifier = Modifier.weight(1f))
                        Text(model.architecture, style = CodeText, color = TextTertiary, modifier = Modifier.weight(0.7f))
                        Text(model.quantization, style = CodeText, color = NeonCyan, modifier = Modifier.weight(0.4f))
                        Text("${model.latencyMs.toInt()}ms", style = CodeText, color = NeonGreen, modifier = Modifier.weight(0.4f))
                    }
                }
            }
        }

        item { Spacer(Modifier.height(80.dp)) }
    }
}

@Composable
private fun OptToggle(title: String, desc: String, color: Color, checked: Boolean, onToggle: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium, color = TextPrimary)
            Text(desc, style = MaterialTheme.typography.labelSmall, color = TextTertiary)
        }
        Switch(
            checked = checked,
            onCheckedChange = onToggle,
            colors = SwitchDefaults.colors(checkedThumbColor = color, checkedTrackColor = color.copy(alpha = 0.3f))
        )
    }
}
