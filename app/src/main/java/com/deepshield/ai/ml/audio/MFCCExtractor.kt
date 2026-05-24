package com.deepshield.ai.ml.audio

import android.content.Context
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import java.nio.ByteBuffer
import kotlin.math.max

class MFCCExtractor(
    private val sampleRate: Int = 16000,
    private val nFft: Int = 2048,
    private val hopLength: Int = 512,
    private val nMels: Int = 128,
    private val nMfcc: Int = 40,
    private val maxLength: Int = 500
) {
    private val window = DSPUtils.hannWindow(nFft)
    private val melFilters = DSPUtils.melFilterbank(sampleRate, nFft, nMels, 0f, sampleRate / 2f)

    /**
     * Decodes the audio track of the given URI into a flat FloatArray at 16kHz.
     */
    fun decodeAudioToFloatArray(context: Context, uri: Uri): FloatArray {
        val extractor = MediaExtractor()
        extractor.setDataSource(context, uri, null)
        
        var audioTrackIndex = -1
        var format: MediaFormat? = null
        for (i in 0 until extractor.trackCount) {
            val f = extractor.getTrackFormat(i)
            val mime = f.getString(MediaFormat.KEY_MIME) ?: continue
            if (mime.startsWith("audio/")) {
                audioTrackIndex = i
                format = f
                break
            }
        }
        
        if (audioTrackIndex < 0 || format == null) return FloatArray(0)
        extractor.selectTrack(audioTrackIndex)

        val mime = format.getString(MediaFormat.KEY_MIME)!!
        val codec = MediaCodec.createDecoderByType(mime)
        codec.configure(format, null, null, 0)
        codec.start()

        val pcmList = mutableListOf<Float>()
        val info = MediaCodec.BufferInfo()
        var isEOS = false

        while (!isEOS) {
            val inIndex = codec.dequeueInputBuffer(10000)
            if (inIndex >= 0) {
                val buffer = codec.getInputBuffer(inIndex)
                if (buffer != null) {
                    val sampleSize = extractor.readSampleData(buffer, 0)
                    if (sampleSize < 0) {
                        codec.queueInputBuffer(inIndex, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                    } else {
                        codec.queueInputBuffer(inIndex, 0, sampleSize, extractor.sampleTime, 0)
                        extractor.advance()
                    }
                }
            }

            var outIndex = codec.dequeueOutputBuffer(info, 10000)
            while (outIndex >= 0) {
                val outBuffer = codec.getOutputBuffer(outIndex)
                if (outBuffer != null && info.size > 0) {
                    // Audio is usually 16-bit PCM
                    val shortBuffer = outBuffer.asShortBuffer()
                    val floats = FloatArray(info.size / 2)
                    for (i in floats.indices) {
                        // Normalize 16-bit to [-1.0, 1.0]
                        floats[i] = shortBuffer.get(i).toFloat() / 32768f
                    }
                    pcmList.addAll(floats.toList())
                }
                codec.releaseOutputBuffer(outIndex, false)
                if ((info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                    isEOS = true
                    break
                }
                outIndex = codec.dequeueOutputBuffer(info, 10000)
            }
        }

        codec.stop()
        codec.release()
        extractor.release()

        return pcmList.toFloatArray()
    }

    /**
     * Extracts MFCCs from a given audio URI and formats it to shape [1, nMfcc, maxLength, 1].
     * Compatible with the Keras input shape.
     */
    fun extractMfcc(context: Context, uri: Uri): Array<Array<Array<FloatArray>>> {
        val y = decodeAudioToFloatArray(context, uri)
        if (y.isEmpty()) {
            return generateEmptyPaddedTensor()
        }
        
        val frames = (y.size - nFft) / hopLength + 1
        val numFrames = max(1, frames)
        
        val mfccs = Array(nMfcc) { FloatArray(numFrames) }
        
        for (f in 0 until numFrames) {
            val start = f * hopLength
            val frame = FloatArray(nFft)
            for (i in 0 until nFft) {
                if (start + i < y.size) {
                    frame[i] = y[start + i] * window[i]
                }
            }
            
            val fftRe = frame.copyOf()
            val fftIm = FloatArray(nFft)
            DSPUtils.fft(fftRe, fftIm)
            
            // Compute power spectrum
            val powerSpec = FloatArray(nFft / 2 + 1)
            for (i in powerSpec.indices) {
                powerSpec[i] = (fftRe[i] * fftRe[i] + fftIm[i] * fftIm[i]) / nFft
            }
            
            // Apply mel filters
            val melSpec = FloatArray(nMels)
            for (m in 0 until nMels) {
                var sum = 0f
                for (k in powerSpec.indices) {
                    sum += powerSpec[k] * melFilters[m][k]
                }
                melSpec[m] = Math.log((sum + 1e-6f).toDouble()).toFloat() // Log scaling
            }
            
            // Apply DCT to get MFCCs
            val mfccFrame = DSPUtils.dct(melSpec, nMfcc)
            for (m in 0 until nMfcc) {
                mfccs[m][f] = mfccFrame[m]
            }
        }
        
        // Pad or truncate to maxLength (500)
        val finalMfccs = Array(nMfcc) { FloatArray(maxLength) }
        for (m in 0 until nMfcc) {
            for (t in 0 until maxLength) {
                if (t < numFrames) {
                    finalMfccs[m][t] = mfccs[m][t]
                } else {
                    finalMfccs[m][t] = 0f
                }
            }
        }
        
        // Return shaped [1, 40, 500, 1] for ONNX/TFLite
        return arrayOf(Array(nMfcc) { m -> Array(maxLength) { t -> floatArrayOf(finalMfccs[m][t]) } })
    }

    private fun generateEmptyPaddedTensor(): Array<Array<Array<FloatArray>>> {
        return arrayOf(Array(nMfcc) { Array(maxLength) { floatArrayOf(0f) } })
    }
}
