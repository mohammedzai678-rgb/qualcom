package com.deepshield.ai.ui.screens.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.deepshield.ai.data.DetectionSettingsRepository
import com.deepshield.ai.data.ScanRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

data class SettingsState(
    val detectionThreshold: Float = 80f,
    val hasScanHistory: Boolean = false
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val detectionSettingsRepository: DetectionSettingsRepository,
    private val scanRepository: ScanRepository
) : ViewModel() {

    val state: StateFlow<SettingsState> = combine(
        detectionSettingsRepository.settings,
        scanRepository.scanHistory
    ) { settings, scanHistory ->
        SettingsState(
            detectionThreshold = settings.alertThreshold,
            hasScanHistory = scanHistory.isNotEmpty()
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SettingsState())

    fun updateDetectionThreshold(threshold: Float) {
        detectionSettingsRepository.updateAlertThreshold(threshold)
    }

    fun clearScanHistory() {
        scanRepository.clearHistory()
    }
}
