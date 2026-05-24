package com.deepshield.ai

import android.app.Application
import dagger.hilt.android.HiltAndroidApp

/**
 * DeepShield AI Application class.
 * Entry point for Hilt dependency injection.
 * 
 * Privacy-first: No analytics, no crash reporting, no telemetry.
 * All processing happens on-device using Snapdragon NPU.
 */
@HiltAndroidApp
class DeepShieldApp : Application() {

    override fun onCreate() {
        super.onCreate()
        // No cloud services initialized — fully offline architecture
        // ML models are loaded lazily when first needed via ModelManager
    }
}
