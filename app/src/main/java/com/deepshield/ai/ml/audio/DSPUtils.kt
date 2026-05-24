package com.deepshield.ai.ml.audio

import kotlin.math.*

/**
 * Lightweight native Kotlin DSP implementation mirroring Librosa's default MFCC extraction.
 */
object DSPUtils {

    /**
     * Compute the Radix-2 Fast Fourier Transform (FFT).
     * Input array length must be a power of 2.
     */
    fun fft(xRe: FloatArray, xIm: FloatArray) {
        val n = xRe.size
        // Bit-reversed addressing
        var j = 0
        for (i in 0 until n - 1) {
            if (i < j) {
                val tempRe = xRe[i]
                xRe[i] = xRe[j]
                xRe[j] = tempRe

                val tempIm = xIm[i]
                xIm[i] = xIm[j]
                xIm[j] = tempIm
            }
            var k = n / 2
            while (k <= j) {
                j -= k
                k /= 2
            }
            j += k
        }

        // Cooley-Tukey decimation-in-time radix-2 FFT
        var step = 1
        while (step < n) {
            val jump = step * 2
            val delta = -Math.PI / step
            val sine = sin(delta / 2)
            val multiplierRe = -2 * sine * sine
            val multiplierIm = sin(delta)

            var factorRe = 1.0
            var factorIm = 0.0

            for (m in 0 until step) {
                for (i in m until n step jump) {
                    val match = i + step
                    val prodRe = factorRe * xRe[match] - factorIm * xIm[match]
                    val prodIm = factorRe * xIm[match] + factorIm * xRe[match]

                    xRe[match] = (xRe[i] - prodRe).toFloat()
                    xIm[match] = (xIm[i] - prodIm).toFloat()
                    xRe[i] = (xRe[i] + prodRe).toFloat()
                    xIm[i] = (xIm[i] + prodIm).toFloat()
                }
                val nextFactorRe = factorRe * multiplierRe - factorIm * multiplierIm + factorRe
                val nextFactorIm = factorRe * multiplierIm + factorIm * multiplierRe + factorIm
                factorRe = nextFactorRe
                factorIm = nextFactorIm
            }
            step = jump
        }
    }

    /**
     * Generates a Hann window.
     */
    fun hannWindow(length: Int): FloatArray {
        val window = FloatArray(length)
        for (i in 0 until length) {
            window[i] = (0.5 * (1.0 - cos(2.0 * Math.PI * i / (length - 1)))).toFloat()
        }
        return window
    }

    /**
     * Converts frequency (Hz) to Mel scale.
     */
    private fun hzToMel(hz: Float): Float {
        return (2595.0 * log10(1.0 + hz / 700.0)).toFloat()
    }

    /**
     * Converts Mel scale to frequency (Hz).
     */
    private fun melToHz(mel: Float): Float {
        return (700.0 * (10.0.pow(mel / 2595.0) - 1.0)).toFloat()
    }

    /**
     * Creates a Mel filterbank matrix mirroring librosa.filters.mel.
     */
    fun melFilterbank(sr: Int, nFft: Int, nMels: Int, fMin: Float, fMax: Float): Array<FloatArray> {
        val minMel = hzToMel(fMin)
        val maxMel = hzToMel(fMax)
        val melPoints = FloatArray(nMels + 2) { i ->
            minMel + i * (maxMel - minMel) / (nMels + 1)
        }
        val hzPoints = FloatArray(nMels + 2) { i -> melToHz(melPoints[i]) }
        val binPoints = IntArray(nMels + 2) { i ->
            floor((nFft + 1) * hzPoints[i] / sr).toInt()
        }

        val filters = Array(nMels) { FloatArray(nFft / 2 + 1) }

        for (m in 1..nMels) {
            val left = binPoints[m - 1]
            val center = binPoints[m]
            val right = binPoints[m + 1]

            for (k in left until center) {
                filters[m - 1][k] = (k - left).toFloat() / (center - left)
            }
            for (k in center until right) {
                filters[m - 1][k] = (right - k).toFloat() / (right - center)
            }
            
            // Librosa default: normalize the filters by area
            var enorm = 2.0f / (hzPoints[m + 1] - hzPoints[m - 1])
            for (k in filters[m - 1].indices) {
                filters[m - 1][k] *= enorm
            }
        }
        return filters
    }

    /**
     * Discrete Cosine Transform Type-II (DCT-II).
     */
    fun dct(input: FloatArray, nCoeffs: Int): FloatArray {
        val n = input.size
        val output = FloatArray(nCoeffs)
        val factor = Math.PI / n
        for (k in 0 until nCoeffs) {
            var sum = 0.0
            for (i in 0 until n) {
                sum += input[i] * cos(k * (i + 0.5) * factor)
            }
            // Orthogonal normalization scaling
            val scale = if (k == 0) sqrt(1.0 / n) else sqrt(2.0 / n)
            output[k] = (sum * scale).toFloat()
        }
        return output
    }
}
