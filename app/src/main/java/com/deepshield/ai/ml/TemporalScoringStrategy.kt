package com.deepshield.ai.ml

object TemporalScoringStrategy {
    
    /**
     * Implements the "confident_strategy" from selimsef/dfdc_deepfake_challenge.
     * 
     * In the original repository:
     * - Prediction > 0.8 = FAKE
     * - Prediction < 0.2 = REAL
     * 
     * In DeepShieldAI, the Authenticity Score is inverted (0-100 scale):
     * - Score < 20 = FAKE (High probability)
     * - Score > 80 = REAL (High probability)
     */
    fun confidentStrategy(scores: FloatArray): Float {
        val sz = scores.size
        if (sz == 0) return 50f // Fallback to unknown/suspicious

        // Count highly probable fake frames (score < 20)
        val fakes = scores.count { it < 20f }

        return when {
            // If > 40% of frames AND > 11 frames are heavily fake, the video is a deepfake.
            // Return the average of only those confident fake scores.
            fakes > (sz / 2.5f) && fakes > 11 -> {
                scores.filter { it < 20f }.average().toFloat()
            }
            // If > 90% of frames are heavily real, the video is definitely real.
            // Return the average of only those confident real scores.
            scores.count { it > 80f } > 0.9f * sz -> {
                scores.filter { it > 80f }.average().toFloat()
            }
            // Otherwise, return the average of all frames
            else -> {
                scores.average().toFloat()
            }
        }
    }
}
