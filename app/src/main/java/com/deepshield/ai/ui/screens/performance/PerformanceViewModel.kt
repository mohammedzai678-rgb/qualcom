package com.deepshield.ai.ui.screens.performance

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.deepshield.ai.ml.ModelManager
import com.deepshield.ai.ml.PerformanceProfiler
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

data class PerformanceState(
    val fps: Float = 0f,
    val latencyMs: Float = 0f,
    val npuUtilizationPercent: Float = 0f,
    val thermalCelsius: Float = 0f,
    val cpuUsagePercent: Float = 0f,
    val batteryDrainMw: Float = 0f,
    val activeDelegate: String = "CPU",
    val availableModels: List<ModelManager.ModelInfo> = emptyList()
)

@HiltViewModel
class PerformanceViewModel @Inject constructor(
    performanceProfiler: PerformanceProfiler,
    modelManager: ModelManager
) : ViewModel() {

    val state: StateFlow<PerformanceState> = performanceProfiler.metrics.map { metrics ->
        PerformanceState(
            fps = metrics.fps,
            latencyMs = metrics.inferenceLatencyMs,
            npuUtilizationPercent = metrics.npuUtilizationPercent,
            thermalCelsius = metrics.thermalCelsius,
            cpuUsagePercent = metrics.cpuUsagePercent,
            batteryDrainMw = metrics.batteryDrainMw,
            activeDelegate = modelManager.getActiveDelegateName(),
            availableModels = modelManager.availableModels
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), PerformanceState())
}
