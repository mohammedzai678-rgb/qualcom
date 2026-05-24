package com.deepshield.ai.watermark

import android.content.Context
import android.graphics.Bitmap
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMuxer
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.nio.ByteBuffer

/**
 * Native Video Watermarking Engine using MediaCodec & MediaMuxer.
 *
 * Pipeline:
 *   1. MediaExtractor selects the video track.
 *   2. MediaCodec (decoder) produces raw YUV frames via getOutputImage().
 *   3. Y (luma) plane bytes are watermarked via InvisibleWatermarkProcessor (DWT-DCT).
 *   4. U and V (chroma) planes are correctly read using pixelStride/rowStride
 *      and packed into the encoder input buffer WITHOUT zeroing them out.
 *   5. MediaCodec (AVC encoder) compresses the watermarked YUV frame.
 *   6. MediaMuxer writes encoded video + pass-through audio into the output .mp4.
 */
class VideoWatermarkEngine(
    private val watermarkProcessor: InvisibleWatermarkProcessor = InvisibleWatermarkProcessor()
) {
    suspend fun embedVideoWatermark(
        context: Context,
        inputUri: Uri,
        outputFile: File,
        payload: String,
        onProgress: (Float) -> Unit
    ): Boolean = withContext(Dispatchers.IO) {
        val extractor = MediaExtractor()
        var encoder: MediaCodec? = null
        var decoder: MediaCodec? = null
        var muxer: MediaMuxer? = null

        try {
            extractor.setDataSource(context, inputUri, null)

            var videoTrackIndex = -1
            var audioTrackIndex = -1
            var videoFormat: MediaFormat? = null
            var audioFormat: MediaFormat? = null

            for (i in 0 until extractor.trackCount) {
                val format = extractor.getTrackFormat(i)
                val mime = format.getString(MediaFormat.KEY_MIME) ?: continue
                when {
                    mime.startsWith("video/") -> {
                        videoTrackIndex = i
                        videoFormat = format
                    }
                    mime.startsWith("audio/") -> {
                        audioTrackIndex = i
                        audioFormat = format
                    }
                }
            }

            if (videoTrackIndex == -1 || videoFormat == null) return@withContext false

            val width = videoFormat.getInteger(MediaFormat.KEY_WIDTH)
            val height = videoFormat.getInteger(MediaFormat.KEY_HEIGHT)
            val frameRate = try { videoFormat.getInteger(MediaFormat.KEY_FRAME_RATE) } catch (_: Exception) { 30 }
            val bitRate = try { videoFormat.getInteger(MediaFormat.KEY_BIT_RATE) } catch (_: Exception) { 5_000_000 }
            val durationUs = try { videoFormat.getLong(MediaFormat.KEY_DURATION) } catch (_: Exception) { 0L }

            // ── Configure Encoder ──
            val outputVideoFormat = MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_AVC, width, height).apply {
                setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Flexible)
                setInteger(MediaFormat.KEY_BIT_RATE, bitRate)
                setInteger(MediaFormat.KEY_FRAME_RATE, frameRate)
                setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1)
            }

            encoder = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_VIDEO_AVC)
            encoder.configure(outputVideoFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)

            // ── Configure Decoder ──
            decoder = MediaCodec.createDecoderByType(videoFormat.getString(MediaFormat.KEY_MIME)!!)
            decoder.configure(videoFormat, null, null, 0)

            muxer = MediaMuxer(outputFile.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)

            val muxerAudioTrack = if (audioTrackIndex != -1 && audioFormat != null) {
                muxer.addTrack(audioFormat)
            } else -1

            encoder.start()
            decoder.start()
            extractor.selectTrack(videoTrackIndex)

            val bufferInfo = MediaCodec.BufferInfo()
            var isExtractorEOS = false
            var isDecoderEOS = false
            var isEncoderEOS = false
            var muxerVideoTrack = -1
            var muxerStarted = false

            // Pre-calculate Y plane size for this resolution
            val ySize = width * height
            // Semi-planar NV12/NV21: U+V interleaved = ySize/2
            // Planar YUV420: U = ySize/4, V = ySize/4
            val uvSize = ySize / 2  // for NV12 / flexible format

            while (!isEncoderEOS) {
                // ── 1. Feed Extractor → Decoder ──
                if (!isExtractorEOS) {
                    val inIndex = decoder.dequeueInputBuffer(10_000L)
                    if (inIndex >= 0) {
                        val buffer = decoder.getInputBuffer(inIndex)
                        val sampleSize = if (buffer != null) extractor.readSampleData(buffer, 0) else -1
                        if (sampleSize < 0) {
                            decoder.queueInputBuffer(inIndex, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                            isExtractorEOS = true
                        } else {
                            decoder.queueInputBuffer(inIndex, 0, sampleSize, extractor.sampleTime, extractor.sampleFlags)
                            extractor.advance()
                        }
                    }
                }

                // ── 2. Read Decoder output → Watermark → Feed Encoder ──
                if (!isDecoderEOS) {
                    val outIndex = decoder.dequeueOutputBuffer(bufferInfo, 10_000L)
                    if (outIndex >= 0) {
                        if ((bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                            isDecoderEOS = true
                        }

                        if (bufferInfo.size > 0) {
                            val image = decoder.getOutputImage(outIndex)
                            if (image != null) {
                                val yPlane = image.planes[0]
                                val uPlane = image.planes[1]
                                val vPlane = image.planes[2]

                                // ── Read Y plane (luma) correctly using rowStride ──
                                val yBytes = readPlane(yPlane.buffer, yPlane.rowStride, yPlane.pixelStride, width, height)

                                // ── Watermark the Y plane using DWT-DCT ──
                                val watermarkedY = applyWatermarkToLuma(yBytes, width, height, payload)

                                // ── Read U and V planes correctly using pixelStride/rowStride ──
                                // Android MediaCodec typically uses COLOR_FormatYUV420Flexible which maps to
                                // NV12 (semi-planar: U and V interleaved) or YUV420P (planar).
                                // We detect by checking pixelStride of the U plane:
                                //   pixelStride == 1 → planar (YUV420P)
                                //   pixelStride == 2 → semi-planar (NV12/NV21)
                                val uvPixelStride = uPlane.pixelStride
                                val uvRowStride = uPlane.rowStride
                                val chromaWidth = width / 2
                                val chromaHeight = height / 2

                                val encoderInIndex = encoder.dequeueInputBuffer(10_000L)
                                if (encoderInIndex >= 0) {
                                    val encoderInBuf = encoder.getInputBuffer(encoderInIndex)
                                    if (encoderInBuf != null) {
                                        encoderInBuf.clear()

                                        // Write watermarked Y plane (luma)
                                        encoderInBuf.put(watermarkedY, 0, minOf(watermarkedY.size, ySize))

                                        // ── Write U and V planes correctly ──
                                        if (uvPixelStride == 1) {
                                            // Planar YUV420P: U then V, each chromaWidth * chromaHeight bytes
                                            val uBytes = readPlane(uPlane.buffer, uvRowStride, 1, chromaWidth, chromaHeight)
                                            val vBytes = readPlane(vPlane.buffer, uvRowStride, 1, chromaWidth, chromaHeight)
                                            encoderInBuf.put(uBytes)
                                            encoderInBuf.put(vBytes)
                                        } else {
                                            // Semi-planar NV12 (U,V interleaved): read interleaved UV
                                            // The encoder COLOR_FormatYUV420Flexible accepts either layout.
                                            // We'll write as NV12: interleaved U,V for chromaWidth*chromaHeight pairs.
                                            val uvInterleaved = readInterleavedUV(
                                                uPlane.buffer, vPlane.buffer,
                                                uvRowStride, uvPixelStride,
                                                chromaWidth, chromaHeight
                                            )
                                            encoderInBuf.put(uvInterleaved)
                                        }

                                        val totalSize = encoderInBuf.position()
                                        encoder.queueInputBuffer(
                                            encoderInIndex, 0, totalSize,
                                            bufferInfo.presentationTimeUs, bufferInfo.flags
                                        )
                                    } else {
                                        encoder.queueInputBuffer(encoderInIndex, 0, 0, 0, 0)
                                    }
                                }

                                image.close()

                                if (durationUs > 0) {
                                    onProgress((bufferInfo.presentationTimeUs.toFloat() / durationUs).coerceIn(0f, 0.95f))
                                }
                            }
                        }
                        decoder.releaseOutputBuffer(outIndex, false)
                    }
                }

                // ── 3. Read Encoder output → Muxer ──
                val encOutIndex = encoder.dequeueOutputBuffer(bufferInfo, 10_000L)
                when {
                    encOutIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                        if (!muxerStarted) {
                            muxerVideoTrack = muxer.addTrack(encoder.outputFormat)
                            muxer.start()
                            muxerStarted = true
                        }
                    }
                    encOutIndex >= 0 -> {
                        val encodedData = encoder.getOutputBuffer(encOutIndex)
                        if (encodedData != null && bufferInfo.size > 0 && muxerStarted) {
                            encodedData.position(bufferInfo.offset)
                            encodedData.limit(bufferInfo.offset + bufferInfo.size)
                            muxer.writeSampleData(muxerVideoTrack, encodedData, bufferInfo)
                        }
                        encoder.releaseOutputBuffer(encOutIndex, false)
                        if ((bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                            isEncoderEOS = true
                        }
                    }
                }
            }

            encoder.stop()
            encoder.release()
            encoder = null
            decoder.stop()
            decoder.release()
            decoder = null

            // ── 4. Mux pass-through audio track ──
            if (audioTrackIndex != -1 && muxerStarted && muxerAudioTrack >= 0) {
                extractor.selectTrack(audioTrackIndex)
                extractor.seekTo(0, MediaExtractor.SEEK_TO_CLOSEST_SYNC)

                val audioBufferInfo = MediaCodec.BufferInfo()
                val byteBuffer = ByteBuffer.allocate(1024 * 1024)

                while (true) {
                    byteBuffer.clear()
                    val sampleSize = extractor.readSampleData(byteBuffer, 0)
                    if (sampleSize < 0) break

                    audioBufferInfo.offset = 0
                    audioBufferInfo.size = sampleSize
                    audioBufferInfo.presentationTimeUs = extractor.sampleTime
                    audioBufferInfo.flags = extractor.sampleFlags

                    muxer.writeSampleData(muxerAudioTrack, byteBuffer, audioBufferInfo)
                    extractor.advance()
                }
            }

            if (muxerStarted) muxer.stop()
            muxer.release()
            muxer = null
            extractor.release()

            onProgress(1.0f)
            return@withContext true
        } catch (e: Exception) {
            e.printStackTrace()
            return@withContext false
        } finally {
            try { encoder?.stop(); encoder?.release() } catch (_: Exception) {}
            try { decoder?.stop(); decoder?.release() } catch (_: Exception) {}
            try { muxer?.release() } catch (_: Exception) {}
        }
    }

    // ──────────────────────────────────────────────────────────────────────
    // Read a single image plane into a compact ByteArray,
    // correctly handling rowStride and pixelStride padding.
    // ──────────────────────────────────────────────────────────────────────
    private fun readPlane(
        buffer: ByteBuffer,
        rowStride: Int,
        pixelStride: Int,
        width: Int,
        height: Int
    ): ByteArray {
        val output = ByteArray(width * height)
        val rowData = ByteArray(rowStride)
        buffer.rewind()
        var outputIdx = 0
        for (row in 0 until height) {
            buffer.get(rowData, 0, minOf(rowStride, buffer.remaining()))
            if (pixelStride == 1) {
                // Packed row — copy width bytes directly
                System.arraycopy(rowData, 0, output, outputIdx, width)
                outputIdx += width
            } else {
                // Strided row — copy every pixelStride-th byte
                for (col in 0 until width) {
                    output[outputIdx++] = rowData[col * pixelStride]
                }
            }
        }
        return output
    }

    // ──────────────────────────────────────────────────────────────────────
    // Read interleaved UV planes (NV12 layout: U0,V0, U1,V1, ...)
    // for semi-planar formats (pixelStride == 2).
    // ──────────────────────────────────────────────────────────────────────
    private fun readInterleavedUV(
        uBuffer: ByteBuffer,
        vBuffer: ByteBuffer,
        rowStride: Int,
        pixelStride: Int,
        chromaWidth: Int,
        chromaHeight: Int
    ): ByteArray {
        val output = ByteArray(chromaWidth * chromaHeight * 2)
        uBuffer.rewind()
        vBuffer.rewind()
        var outputIdx = 0
        for (row in 0 until chromaHeight) {
            val uRowStart = row * rowStride
            val vRowStart = row * rowStride
            for (col in 0 until chromaWidth) {
                val uIdx = uRowStart + col * pixelStride
                val vIdx = vRowStart + col * pixelStride
                output[outputIdx++] = if (uIdx < uBuffer.capacity()) uBuffer.get(uIdx) else 0
                output[outputIdx++] = if (vIdx < vBuffer.capacity()) vBuffer.get(vIdx) else 0
            }
        }
        return output
    }

    // ──────────────────────────────────────────────────────────────────────
    // Apply DWT-DCT watermark to the Y (luma) plane.
    // Operates on raw luma bytes via InvisibleWatermarkProcessor by mapping
    // the Y array into a grayscale ARGB_8888 bitmap (Y → all RGB channels).
    // ──────────────────────────────────────────────────────────────────────
    private fun applyWatermarkToLuma(yBytes: ByteArray, width: Int, height: Int, payload: String): ByteArray {
        // Build an ARGB_8888 bitmap where R=G=B=Y for each pixel (grayscale representation)
        val pixels = IntArray(width * height) { i ->
            val y = yBytes[i].toInt() and 0xFF
            0xFF000000.toInt() or (y shl 16) or (y shl 8) or y
        }
        val luminaBitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        luminaBitmap.setPixels(pixels, 0, width, 0, 0, width, height)

        // Embed watermark using InvisibleWatermarkProcessor (DWT-DCT on U/V channels)
        val watermarkedBitmap = watermarkProcessor.embed(luminaBitmap, payload)
        luminaBitmap.recycle()

        // Extract the R channel back as luma bytes (R=G=B in the grayscale bitmap)
        val outPixels = IntArray(width * height)
        watermarkedBitmap.getPixels(outPixels, 0, width, 0, 0, width, height)
        watermarkedBitmap.recycle()

        return ByteArray(width * height) { i ->
            // Take the R channel as the reconstructed luma value
            ((outPixels[i] shr 16) and 0xFF).toByte()
        }
    }
}
