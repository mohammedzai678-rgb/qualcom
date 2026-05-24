package com.deepshield.ai.ml

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.Process
import android.os.SystemClock
import com.deepshield.ai.domain.model.NpuMetrics
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.math.abs

/**
 * Tracks real runtime metrics gathered from the process and battery subsystem.
 *
 * CPU/GPU/NPU utilization cannot be measured precisely from the public Android
 * APIs used here, so those values are derived from observed inference duty
 * cycle and only attributed to the active delegate.
 */
@Singleton
class PerformanceProfiler @Inject constructor(
    @ApplicationContext private val context: Context
) {

    private val _metrics = MutableStateFlow(NpuMetrics(activeDelegate = "CPU"))
    val metrics: StateFlow<NpuMetrics> = _metrics.asStateFlow()

    private val _metricsHistory = MutableStateFlow<List<NpuMetrics>>(emptyList())
    val metricsHistory: StateFlow<List<NpuMetrics>> = _metricsHistory.asStateFlow()

    private var inferenceCount = 0
    private var totalInferenceTimeMs = 0L
    private var lastInferenceStartMs = 0L
    private var lastCpuTimeMs = Process.getElapsedCpuTime()
    private var lastWallTimeMs = SystemClock.elapsedRealtime()

    private val fpsWindow = mutableListOf<Long>()

    fun startInference() {
        lastInferenceStartMs = SystemClock.elapsedRealtime()
    }

    fun endInference(
        durationMs: Long = SystemClock.elapsedRealtime() - lastInferenceStartMs,
        activeDelegate: String = "CPU",
        quantizationMode: String = "INT8"
    ) {
        inferenceCount++
        totalInferenceTimeMs += durationMs

        val now = SystemClock.elapsedRealtime()
        fpsWindow.add(now)
        fpsWindow.removeAll { now - it > 1000L }

        val currentMetrics = computeCurrentMetrics(
            lastLatencyMs = durationMs,
            activeDelegate = activeDelegate,
            quantizationMode = quantizationMode
        )
        _metrics.value = currentMetrics

        val history = _metricsHistory.value.toMutableList()
        history.add(currentMetrics)
        if (history.size > 60) history.removeAt(0)
        _metricsHistory.value = history
    }

    fun getAverageLatency(): Float {
        return if (inferenceCount > 0) {
            totalInferenceTimeMs.toFloat() / inferenceCount
        } else {
            0f
        }
    }

    fun getCurrentFps(): Float = _metrics.value.fps

    fun resetStats() {
        inferenceCount = 0
        totalInferenceTimeMs = 0L
        fpsWindow.clear()
        _metricsHistory.value = emptyList()
    }

    private fun computeCurrentMetrics(
        lastLatencyMs: Long,
        activeDelegate: String,
        quantizationMode: String
    ): NpuMetrics {
        val runtime = Runtime.getRuntime()
        val usedMemoryMb = (runtime.totalMemory() - runtime.freeMemory()) / (1024f * 1024f)
        val fps = fpsWindow.size.toFloat()

        val nowWall = SystemClock.elapsedRealtime()
        val nowCpu = Process.getElapsedCpuTime()
        val wallDelta = (nowWall - lastWallTimeMs).coerceAtLeast(1L)
        val cpuDelta = (nowCpu - lastCpuTimeMs).coerceAtLeast(0L)
        val cpuPercent = (
            cpuDelta.toFloat() /
                wallDelta.toFloat() /
                runtime.availableProcessors().coerceAtLeast(1) *
                100f
            ).coerceIn(0f, 100f)
        lastWallTimeMs = nowWall
        lastCpuTimeMs = nowCpu

        val inferenceDutyCycle = ((lastLatencyMs * fps) / 10f).coerceIn(0f, 100f)
        val delegate = activeDelegate.lowercase()
        val gpuUsage = if ("gpu" in delegate) inferenceDutyCycle else 0f
        val npuUsage = if ("nnapi" in delegate || "npu" in delegate || "htp" in delegate) inferenceDutyCycle else 0f

        val batteryIntent = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val thermalCelsius = batteryIntent
            ?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, -1)
            ?.takeIf { it > 0 }
            ?.div(10f)
            ?: 0f
        val voltageMv = batteryIntent?.getIntExtra(BatteryManager.EXTRA_VOLTAGE, 0) ?: 0
        val batteryManager = context.getSystemService(BatteryManager::class.java)
        val currentUa = batteryManager?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_NOW) ?: 0
        val batteryDrainMw = if (voltageMv > 0 && currentUa != 0) {
            abs(currentUa / 1000f) * (voltageMv / 1000f)
        } else {
            0f
        }

        return NpuMetrics(
            fps = fps,
            inferenceLatencyMs = lastLatencyMs.toFloat(),
            cpuUsagePercent = cpuPercent,
            gpuUsagePercent = gpuUsage,
            npuUtilizationPercent = npuUsage,
            ramUsageMb = usedMemoryMb,
            thermalCelsius = thermalCelsius,
            batteryDrainMw = batteryDrainMw,
            activeDelegate = activeDelegate,
            quantizationMode = quantizationMode,
            timestamp = System.currentTimeMillis()
        )
    }
}
