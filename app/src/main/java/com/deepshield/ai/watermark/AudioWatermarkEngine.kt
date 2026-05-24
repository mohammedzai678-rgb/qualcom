package com.deepshield.ai.watermark

import android.content.Context
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMuxer
import android.net.Uri
import com.deepshield.ai.ml.audio.DSPUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Full Audio Watermarking Engine using Frequency-Domain DWT-DCT.
 *
 * Pipeline:
 *   1. MediaExtractor + MediaCodec   → decode compressed audio → PCM ShortArray (16-bit)
 *   2. Haar Wavelet Transform (1-D)  → split into Approximation (L) and Detail (H) bands
 *   3. DCT-II on Approximation band  → modify mid-frequency coefficients to embed payload bits
 *   4. Inverse DCT + Inverse Haar    → reconstruct modified PCM frame
 *   5. MediaCodec AAC encoder        → re-encode PCM ShortArray → AAC frames
 *   6. MediaMuxer                    → write AAC stream to .m4a output file
 *
 * The watermark is embedded by modulating mid-frequency DCT coefficients using
 * QIM (Quantization Index Modulation) with embedding strength α. The payload is
 * repeated across frames so extraction is robust to partial cropping.
 */
class AudioWatermarkEngine {

    // ──────────────────────────────────────────────────────────────────────
    // Constants
    // ──────────────────────────────────────────────────────────────────────
    companion object {
        private const val FRAME_SIZE = 2048          // samples per watermark frame (must be power of 2)
        private const val ALPHA = 0.35f              // QIM embedding strength (imperceptible but robust)
        private const val DCT_START = 256            // start of mid-frequency band for embedding
        private const val SAMPLE_RATE = 44100        // output AAC sample rate
        private const val AAC_BITRATE = 128_000      // 128 kbps AAC output
        private const val AAC_CHANNEL_COUNT = 1      // mono
        private const val BUFFER_TIMEOUT_US = 10_000L
    }

    // ──────────────────────────────────────────────────────────────────────
    // Public API
    // ──────────────────────────────────────────────────────────────────────

    suspend fun embedAudioWatermark(
        context: Context,
        inputUri: Uri,
        outputFile: File,
        payload: String,
        onProgress: (Float) -> Unit
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            onProgress(0.05f)

            // Step 1: Decode audio from URI to raw PCM (ShortArray, 16-bit)
            val (pcmShorts, originalSampleRate, channelCount) =
                decodeAudioToPcm(context, inputUri)
                    ?: return@withContext false

            onProgress(0.20f)

            // Step 2: Convert payload → binary bit sequence (BooleanArray)
            val payloadBytes = payload.toByteArray(Charsets.UTF_8)
            val binaryPayload = BooleanArray(payloadBytes.size * 8)
            for (i in payloadBytes.indices) {
                for (b in 0..7) {
                    binaryPayload[i * 8 + b] = (payloadBytes[i].toInt() shr (7 - b) and 1) == 1
                }
            }

            onProgress(0.30f)

            // Step 3: Embed watermark into PCM via DWT-DCT
            val watermarkedShorts = embedBitsIntoPcm(pcmShorts, binaryPayload, onProgress)

            onProgress(0.80f)

            // Step 4: Encode watermarked PCM → AAC → .m4a file
            val encoded = encodeToAac(watermarkedShorts, outputFile)

            onProgress(1.0f)
            return@withContext encoded
        } catch (e: Exception) {
            e.printStackTrace()
            return@withContext false
        }
    }

    // ──────────────────────────────────────────────────────────────────────
    // Step 1: Decode compressed audio → PCM (ShortArray)
    // Returns Triple(pcmShorts, sampleRate, channelCount) or null on failure
    // ──────────────────────────────────────────────────────────────────────

    private data class PcmData(
        val shorts: ShortArray,
        val sampleRate: Int,
        val channelCount: Int
    )

    private fun decodeAudioToPcm(context: Context, uri: Uri): PcmData? {
        val extractor = MediaExtractor()
        try {
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

            if (audioTrackIndex < 0 || format == null) {
                extractor.release()
                return null
            }

            val sampleRate = format.getInteger(MediaFormat.KEY_SAMPLE_RATE, SAMPLE_RATE)
            val channelCount = format.getInteger(MediaFormat.KEY_CHANNEL_COUNT, AAC_CHANNEL_COUNT)
            val mime = format.getString(MediaFormat.KEY_MIME)!!

            extractor.selectTrack(audioTrackIndex)

            val codec = MediaCodec.createDecoderByType(mime)
            codec.configure(format, null, null, 0)
            codec.start()

            val pcmList = ArrayList<Short>(sampleRate * 60) // Pre-allocate 60 seconds
            val bufInfo = MediaCodec.BufferInfo()
            var isEos = false

            while (!isEos) {
                // Feed compressed data into decoder
                val inIndex = codec.dequeueInputBuffer(BUFFER_TIMEOUT_US)
                if (inIndex >= 0) {
                    val inBuf = codec.getInputBuffer(inIndex)
                    if (inBuf != null) {
                        val sampleSize = extractor.readSampleData(inBuf, 0)
                        if (sampleSize < 0) {
                            codec.queueInputBuffer(inIndex, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                        } else {
                            codec.queueInputBuffer(inIndex, 0, sampleSize, extractor.sampleTime, 0)
                            extractor.advance()
                        }
                    }
                }

                // Read decoded PCM output
                val outIndex = codec.dequeueOutputBuffer(bufInfo, BUFFER_TIMEOUT_US)
                if (outIndex >= 0) {
                    val outBuf = codec.getOutputBuffer(outIndex)
                    if (outBuf != null && bufInfo.size > 0) {
                        outBuf.position(bufInfo.offset)
                        outBuf.limit(bufInfo.offset + bufInfo.size)
                        val shortBuf = outBuf.order(ByteOrder.LITTLE_ENDIAN).asShortBuffer()
                        val chunk = ShortArray(shortBuf.remaining())
                        shortBuf.get(chunk)
                        pcmList.ensureCapacity(pcmList.size + chunk.size)
                        for (s in chunk) pcmList.add(s)
                    }
                    codec.releaseOutputBuffer(outIndex, false)
                    if ((bufInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                        isEos = true
                    }
                }
            }

            codec.stop()
            codec.release()
            extractor.release()

            return PcmData(
                shorts = ShortArray(pcmList.size) { pcmList[it] },
                sampleRate = sampleRate,
                channelCount = channelCount
            )
        } catch (e: Exception) {
            e.printStackTrace()
            try { extractor.release() } catch (_: Exception) {}
            return null
        }
    }

    // ──────────────────────────────────────────────────────────────────────
    // Step 2+3: DWT-DCT watermark embedding on raw PCM ShortArray
    // ──────────────────────────────────────────────────────────────────────

    private fun embedBitsIntoPcm(
        pcm: ShortArray,
        bits: BooleanArray,
        onProgress: (Float) -> Unit
    ): ShortArray {
        val output = pcm.copyOf()
        val totalFrames = pcm.size / FRAME_SIZE
        var bitIndex = 0

        for (frameIdx in 0 until totalFrames) {
            val start = frameIdx * FRAME_SIZE

            // ── Extract float frame ──
            val chunk = FloatArray(FRAME_SIZE) { i ->
                output[start + i].toFloat() / 32768f  // normalize to [-1, 1]
            }

            // ── 1-D Haar Wavelet Transform ──
            val half = FRAME_SIZE / 2
            val approx = FloatArray(half) { i -> (chunk[2 * i] + chunk[2 * i + 1]) / 2f }
            val detail = FloatArray(half) { i -> (chunk[2 * i] - chunk[2 * i + 1]) / 2f }

            // ── Forward DCT on Approximation band ──
            val dctCoeffs = DSPUtils.dct(approx, half)

            // ── QIM Embedding: modify mid-frequency coefficients ──
            val embedCount = minOf(bits.size, dctCoeffs.size - DCT_START)
            for (i in 0 until embedCount) {
                val bit = if (bits[(bitIndex + i) % bits.size]) 1f else -1f
                dctCoeffs[DCT_START + i] += ALPHA * bit
            }
            bitIndex = (bitIndex + embedCount) % bits.size

            // ── Inverse DCT ──
            val modifiedApprox = idct(dctCoeffs, half)

            // ── Inverse Haar Wavelet Transform ──
            for (i in 0 until half) {
                val a = modifiedApprox[i]
                val d = detail[i]
                chunk[2 * i]     = a + d
                chunk[2 * i + 1] = a - d
            }

            // ── Write back as ShortArray (clamp to [-1, 1] before scaling) ──
            for (i in 0 until FRAME_SIZE) {
                output[start + i] = (chunk[i].coerceIn(-1f, 1f) * 32767f).toInt().toShort()
            }

            // Progress update every 64 frames
            if (frameIdx % 64 == 0) {
                val progress = 0.30f + (frameIdx.toFloat() / totalFrames) * 0.50f
                onProgress(progress.coerceIn(0f, 0.80f))
            }
        }

        return output
    }

    /**
     * Inverse DCT-II (DCT-III) using the exact inverse of DSPUtils.dct().
     * Since DSPUtils uses orthonormal DCT-II, the inverse is DCT-III with the same normalization.
     */
    private fun idct(input: FloatArray, nOut: Int): FloatArray {
        val n = input.size
        val output = FloatArray(nOut)
        val factor = Math.PI / n
        for (i in 0 until nOut) {
            var sum = 0.0
            for (k in 0 until n) {
                val scale = if (k == 0) Math.sqrt(1.0 / n) else Math.sqrt(2.0 / n)
                sum += scale * input[k] * Math.cos(k * (i + 0.5) * factor)
            }
            output[i] = sum.toFloat()
        }
        return output
    }

    // ──────────────────────────────────────────────────────────────────────
    // Step 4: Re-encode PCM ShortArray → AAC .m4a using MediaCodec + MediaMuxer
    // ──────────────────────────────────────────────────────────────────────

    private fun encodeToAac(pcm: ShortArray, outputFile: File): Boolean {
        outputFile.parentFile?.mkdirs()

        val mediaFormat = MediaFormat.createAudioFormat(
            MediaFormat.MIMETYPE_AUDIO_AAC,
            SAMPLE_RATE,
            AAC_CHANNEL_COUNT
        ).apply {
            setInteger(MediaFormat.KEY_BIT_RATE, AAC_BITRATE)
            setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectLC)
            setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, FRAME_SIZE * 2) // bytes = samples * 2
        }

        val encoder = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_AUDIO_AAC)
        encoder.configure(mediaFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
        encoder.start()

        val muxer = MediaMuxer(outputFile.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
        val bufInfo = MediaCodec.BufferInfo()

        var muxerTrack = -1
        var muxerStarted = false
        var inputOffset = 0
        var presentationTimeUs = 0L
        var isEncoderDone = false

        // How many PCM samples per AAC input frame (AAC-LC uses 1024 samples per frame)
        val samplesPerFrame = 1024
        val bytesPerFrame = samplesPerFrame * 2  // 16-bit = 2 bytes per sample

        try {
            while (!isEncoderDone) {
                // ── Feed PCM into encoder ──
                val inIndex = encoder.dequeueInputBuffer(BUFFER_TIMEOUT_US)
                if (inIndex >= 0) {
                    val inBuf = encoder.getInputBuffer(inIndex) ?: continue
                    inBuf.clear()

                    if (inputOffset >= pcm.size) {
                        // Signal EOS
                        encoder.queueInputBuffer(inIndex, 0, 0, presentationTimeUs, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                    } else {
                        val samplesAvailable = minOf(samplesPerFrame, pcm.size - inputOffset)
                        val byteBuffer = ByteBuffer.allocate(samplesAvailable * 2).order(ByteOrder.LITTLE_ENDIAN)
                        for (i in 0 until samplesAvailable) {
                            byteBuffer.putShort(pcm[inputOffset + i])
                        }
                        byteBuffer.flip()
                        inBuf.put(byteBuffer)
                        encoder.queueInputBuffer(inIndex, 0, samplesAvailable * 2, presentationTimeUs, 0)
                        inputOffset += samplesAvailable
                        presentationTimeUs += (samplesAvailable.toLong() * 1_000_000L) / SAMPLE_RATE
                    }
                }

                // ── Read encoded AAC output ──
                val outIndex = encoder.dequeueOutputBuffer(bufInfo, BUFFER_TIMEOUT_US)
                when {
                    outIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                        if (!muxerStarted) {
                            muxerTrack = muxer.addTrack(encoder.outputFormat)
                            muxer.start()
                            muxerStarted = true
                        }
                    }
                    outIndex >= 0 -> {
                        val outBuf = encoder.getOutputBuffer(outIndex)
                        if (outBuf != null && bufInfo.size > 0 && muxerStarted) {
                            outBuf.position(bufInfo.offset)
                            outBuf.limit(bufInfo.offset + bufInfo.size)
                            // Skip ADTS header config frames (codec config)
                            if ((bufInfo.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG) == 0) {
                                muxer.writeSampleData(muxerTrack, outBuf, bufInfo)
                            }
                        }
                        encoder.releaseOutputBuffer(outIndex, false)
                        if ((bufInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                            isEncoderDone = true
                        }
                    }
                }
            }

            encoder.stop()
            encoder.release()
            if (muxerStarted) muxer.stop()
            muxer.release()

            return outputFile.exists() && outputFile.length() > 0
        } catch (e: Exception) {
            e.printStackTrace()
            try { encoder.stop(); encoder.release() } catch (_: Exception) {}
            try { if (muxerStarted) muxer.stop(); muxer.release() } catch (_: Exception) {}
            return false
        }
    }
}
