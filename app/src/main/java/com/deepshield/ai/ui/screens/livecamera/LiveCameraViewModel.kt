package com.deepshield.ai.ui.screens.livecamera

import android.graphics.Bitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.deepshield.ai.data.DetectionSettingsRepository
import com.deepshield.ai.domain.model.BoundingBox
import com.deepshield.ai.domain.model.NpuMetrics
import com.deepshield.ai.ml.DeepfakeDetector
import com.deepshield.ai.ml.PerformanceProfiler
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import javax.inject.Inject

data class LiveCameraState(
    val isDetectionActive: Boolean = false,
    val isFrontCamera: Boolean = true,
    val currentScore: Float = 100f,
    val averageScore: Float = 100f,
    val fps: Float = 0f,
    val latencyMs: Float = 0f,
    val detectedFaces: List<BoundingBox> = emptyList(),
    val frameCount: Int = 0,
    val suspiciousFrames: Int = 0,
    val alertThreshold: Float = 80f,
    val isAlertShowing: Boolean = false,
    val npuMetrics: NpuMetrics = NpuMetrics()
)

@HiltViewModel
class LiveCameraViewModel @Inject constructor(
    private val deepfakeDetector: DeepfakeDetector,
    private val performanceProfiler: PerformanceProfiler,
    private val detectionSettingsRepository: DetectionSettingsRepository
) : ViewModel() {

    private val _state = MutableStateFlow(LiveCameraState())
    val state: StateFlow<LiveCameraState> = _state.asStateFlow()

    private val scoreHistory = mutableListOf<Float>()
    @Volatile private var isAnalyzingFrame = false

    init {
        viewModelScope.launch {
            detectionSettingsRepository.settings.collectLatest { settings ->
                _state.value = _state.value.copy(alertThreshold = settings.alertThreshold)
            }
        }
    }

    fun startDetection() {
        scoreHistory.clear()
        deepfakeDetector.resetLiveState()
        performanceProfiler.resetStats()
        _state.value = _state.value.copy(
            isDetectionActive = true,
            currentScore = 100f,
            averageScore = 100f,
            fps = 0f,
            latencyMs = 0f,
            detectedFaces = emptyList(),
            frameCount = 0,
            suspiciousFrames = 0,
            isAlertShowing = false
        )
    }

    fun stopDetection() {
        deepfakeDetector.resetLiveState()
        _state.value = _state.value.copy(
            isDetectionActive = false,
            detectedFaces = emptyList(),
            isAlertShowing = false
        )
    }

    /**
     * Toggle between front and back camera.
     * Resets score history and detector live state to avoid stale
     * EMA / face cache from the other camera's perspective.
     */
    fun toggleCamera() {
        scoreHistory.clear()
        deepfakeDetector.resetLiveState()
        _state.value = _state.value.copy(
            isFrontCamera = !_state.value.isFrontCamera,
            currentScore = 100f,
            averageScore = 100f,
            detectedFaces = emptyList(),
            frameCount = 0,
            suspiciousFrames = 0,
            isAlertShowing = false
        )
    }

    fun analyzeFrame(bitmap: Bitmap) {
        if (!_state.value.isDetectionActive || isAnalyzingFrame) {
            if (!bitmap.isRecycled) {
                bitmap.recycle()
            }
            return
        }
        isAnalyzingFrame = true

        viewModelScope.launch {
            try {
                val (score, faces) = deepfakeDetector.analyzeFrame(bitmap)

                scoreHistory.add(score)
                if (scoreHistory.size > 300) scoreHistory.removeAt(0) // Keep last 10 seconds at 30fps

                val average = scoreHistory.average().toFloat()
                val suspicious = scoreHistory.count { it < _state.value.alertThreshold }
                val metrics = performanceProfiler.metrics.value

                _state.value = _state.value.copy(
                    currentScore = score,
                    averageScore = average,
                    fps = metrics.fps,
                    latencyMs = metrics.inferenceLatencyMs,
                    detectedFaces = faces,
                    frameCount = _state.value.frameCount + 1,
                    suspiciousFrames = suspicious,
                    isAlertShowing = score < _state.value.alertThreshold,
                    npuMetrics = metrics
                )
            } catch (_: Throwable) {
                _state.value = _state.value.copy(
                    detectedFaces = emptyList(),
                    isAlertShowing = false
                )
            } finally {
                if (!bitmap.isRecycled) {
                    bitmap.recycle()
                }
                isAnalyzingFrame = false
            }
        }
    }

    fun setAlertThreshold(threshold: Float) {
        _state.value = _state.value.copy(alertThreshold = threshold)
    }
}
