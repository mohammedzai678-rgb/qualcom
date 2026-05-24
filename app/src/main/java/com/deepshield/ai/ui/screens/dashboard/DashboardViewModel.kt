package com.deepshield.ai.ui.screens.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.deepshield.ai.data.ScanRepository
import com.deepshield.ai.domain.model.*
import com.deepshield.ai.ml.PerformanceProfiler
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import javax.inject.Inject

data class DashboardState(
    val totalScans: Int = 0,
    val threatsDetected: Int = 0,
    val mediaAuthenticated: Int = 0,
    val mediaTrustScore: Float = 0f,
    val recentScans: List<ScanResult> = emptyList(),
    val threatAlerts: List<ThreatAlert> = emptyList(),
    val npuMetrics: NpuMetrics = NpuMetrics(activeDelegate = "CPU"),
    val isOffline: Boolean = true
)

@HiltViewModel
class DashboardViewModel @Inject constructor(
    private val scanRepository: ScanRepository,
    private val performanceProfiler: PerformanceProfiler
) : ViewModel() {

    private val _state = MutableStateFlow(DashboardState())
    val state: StateFlow<DashboardState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            combine(
                scanRepository.scanHistory,
                performanceProfiler.metrics
            ) { history, metrics ->
                val authenticated = history.count { it.verdict == ScanVerdict.AUTHENTIC }
                val flagged = history.count { it.verdict != ScanVerdict.AUTHENTIC && it.verdict != ScanVerdict.UNKNOWN }
                val trustScore = if (history.isEmpty()) 0f else history.map { it.authenticityScore }.average().toFloat()

                DashboardState(
                    totalScans = history.size,
                    threatsDetected = flagged,
                    mediaAuthenticated = authenticated,
                    mediaTrustScore = trustScore,
                    recentScans = history.take(5),
                    threatAlerts = history
                        .filter { it.verdict == ScanVerdict.DEEPFAKE || it.verdict == ScanVerdict.SUSPICIOUS }
                        .take(3)
                        .map { scan -> scan.toThreatAlert() },
                    npuMetrics = metrics,
                    isOffline = true
                )
            }.collect { dashboardState ->
                _state.value = dashboardState
            }
        }
    }

    private fun ScanResult.toThreatAlert(): ThreatAlert {
        val severity = when (verdict) {
            ScanVerdict.DEEPFAKE -> ThreatSeverity.HIGH
            ScanVerdict.SUSPICIOUS -> ThreatSeverity.MEDIUM
            ScanVerdict.AUTHENTIC, ScanVerdict.UNKNOWN -> ThreatSeverity.LOW
        }
        val title = when (verdict) {
            ScanVerdict.DEEPFAKE -> "Deepfake detected"
            ScanVerdict.SUSPICIOUS -> "Review recommended"
            ScanVerdict.AUTHENTIC -> "Verified media"
            ScanVerdict.UNKNOWN -> "Scan incomplete"
        }

        return ThreatAlert(
            title = title,
            description = fileName.ifEmpty { "Unnamed media" },
            severity = severity,
            category = mediaType.name,
            relatedScanId = id
        )
    }
}
