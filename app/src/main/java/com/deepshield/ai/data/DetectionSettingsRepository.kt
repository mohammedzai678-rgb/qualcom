package com.deepshield.ai.data

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import javax.inject.Inject
import javax.inject.Singleton

data class DetectionSettings(
    val alertThreshold: Float = 80f
)

@Singleton
class DetectionSettingsRepository @Inject constructor(
    @ApplicationContext context: Context
) {

    companion object {
        private const val PREFS_NAME = "deepshield_settings"
        private const val KEY_ALERT_THRESHOLD = "alert_threshold"
    }

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val _settings = MutableStateFlow(
        DetectionSettings(
            alertThreshold = prefs.getFloat(KEY_ALERT_THRESHOLD, DetectionSettings().alertThreshold)
        )
    )
    val settings: StateFlow<DetectionSettings> = _settings.asStateFlow()

    fun updateAlertThreshold(threshold: Float) {
        _settings.update { current ->
            current.copy(alertThreshold = threshold.coerceIn(50f, 99f)).also { updated ->
                prefs.edit().putFloat(KEY_ALERT_THRESHOLD, updated.alertThreshold).apply()
            }
        }
    }
}
