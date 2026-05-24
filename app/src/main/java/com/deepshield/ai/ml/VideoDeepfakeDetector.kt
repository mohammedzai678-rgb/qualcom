package com.deepshield.ai.ml

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.media.MediaMetadataRetriever
import android.net.Uri
import com.deepshield.ai.domain.model.AIAttribution
import com.deepshield.ai.domain.model.BoundingBox
import com.deepshield.ai.domain.model.MediaType
import com.deepshield.ai.domain.model.ScanResult
import com.deepshield.ai.domain.model.ScanVerdict
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min

@Singleton
class VideoDeepfakeDetector @Inject constructor(
    @ApplicationContext private val context: Context,
    private val modelManager: ModelManager,
    private val performanceProfiler: PerformanceProfiler
) {
    private data class FaceCandidate(
        val box: BoundingBox,
        val confidence: Float
    )

    private data class ClassifierScore(
        val score: Float,
        val entropyNorm: Float,
        val top1: Float,
        val margin: Float
    )

    suspend fun analyzeVideo(
        uri: Uri,
        fileName: String = "",
        fileSize: Long = 0L,
        onProgress: ((Float, String) -> Unit)? = null
    ): ScanResult = withContext(Dispatchers.Default) {
        val startTime = System.currentTimeMillis()
        performanceProfiler.startInference()
        
        val retriever = MediaMetadataRetriever()
        var previewBytes: ByteArray? = null
        
        try {
            retriever.setDataSource(context, uri)
            val durationStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
            val durationMs = durationStr?.toLongOrNull() ?: 0L
            val durationUs = durationMs * 1000L
            val videoWidth = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)
                ?.toIntOrNull()
                ?.coerceAtLeast(1)
                ?: 1280
            val videoHeight = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)
                ?.toIntOrNull()
                ?.coerceAtLeast(1)
                ?: 720

            val sampleTimestampsUs = buildFrameTimeline(durationUs)
            val frameScores = mutableListOf<Float>()
            val totalFrames = sampleTimestampsUs.size.coerceAtLeast(1)

            sampleTimestampsUs.forEachIndexed { index, timeUs ->
                val bitmap = loadAnalysisFrame(
                    retriever = retriever,
                    timeUs = timeUs,
                    videoWidth = videoWidth,
                    videoHeight = videoHeight
                )
                if (bitmap != null) {
                    if (previewBytes == null) {
                        previewBytes = createPreviewBytes(bitmap)
                    }

                    runCatching { processFrame(bitmap) }
                        .onSuccess(frameScores::add)

                    val progress = (index + 1).toFloat() / totalFrames.toFloat()
                    reportProgress(
                        onProgress,
                        progress * 0.9f,
                        "Scanning frame ${index + 1} of $totalFrames"
                    )

                    bitmap.recycle()
                }
            }

            if (frameScores.isEmpty()) {
                val fallbackFrame = loadAnalysisFrame(
                    retriever = retriever,
                    timeUs = 0L,
                    videoWidth = videoWidth,
                    videoHeight = videoHeight
                )
                if (fallbackFrame != null) {
                    if (previewBytes == null) {
                        previewBytes = createPreviewBytes(fallbackFrame)
                    }
                    frameScores += processFrame(fallbackFrame)
                    fallbackFrame.recycle()
                }
            }

            if (frameScores.isEmpty()) {
                throw IllegalStateException("Failed to extract usable frames from video")
            }

            reportProgress(onProgress, 0.95f, "Applying DFDC Temporal Strategy")
            val finalAuthenticity = TemporalScoringStrategy.confidentStrategy(frameScores.toFloatArray())

            val verdict = when {
                finalAuthenticity >= 65f -> ScanVerdict.AUTHENTIC
                finalAuthenticity >= 40f -> ScanVerdict.SUSPICIOUS
                else -> ScanVerdict.DEEPFAKE
            }

            val processingTime = System.currentTimeMillis() - startTime
            performanceProfiler.endInference(
                durationMs = processingTime,
                activeDelegate = modelManager.getActiveDelegateName(),
                quantizationMode = "FP32"
            )

            reportProgress(onProgress, 1.0f, "Complete")

            ScanResult(
                mediaType = MediaType.VIDEO,
                authenticityScore = finalAuthenticity,
                confidenceScore = 95f, // High confidence for video due to temporal strategy
                verdict = verdict,
                modelScores = mapOf(
                    "EfficientNet-B0 (DFDC)" to finalAuthenticity,
                    "Temporal Analysis" to finalAuthenticity
                ),
                detectedArtifacts = emptyList(),
                aiAttribution = if (verdict == ScanVerdict.AUTHENTIC) null else AIAttribution(confidence = 90f),
                processingTimeMs = processingTime,
                fileName = fileName,
                fileSize = fileSize,
                previewImageBytes = previewBytes
            )
        } catch (e: Throwable) {
            val processingTime = System.currentTimeMillis() - startTime
            performanceProfiler.endInference(
                durationMs = processingTime,
                activeDelegate = modelManager.getActiveDelegateName(),
                quantizationMode = "FP32"
            )
            
            ScanResult(
                mediaType = MediaType.VIDEO,
                authenticityScore = 50f,
                confidenceScore = 25f,
                verdict = ScanVerdict.UNKNOWN,
                processingTimeMs = processingTime,
                fileName = fileName,
                fileSize = fileSize,
                previewImageBytes = previewBytes,
                modelScores = mapOf(
                    "EfficientNet-B0 (DFDC)" to 50f,
                    "Temporal Analysis" to 50f
                )
            )
        } finally {
            try {
                retriever.release()
            } catch (e: Exception) {
                // Ignore release errors
            }
        }
    }

    private fun processFrame(bitmap: Bitmap): Float {
        val faces = runCatching { detectFaces(bitmap) }.getOrDefault(emptyList())
        val primaryFace = faces.maxByOrNull { it.confidence * it.box.width * it.box.height }?.box 
            ?: BoundingBox(0f, 0f, 1f, 1f)

        val analysisCrop = runCatching {
            cropBitmap(bitmap, primaryFace, extraPadding = 0.08f)
        }.getOrElse { bitmap }
        return try {
            val deepfakeInput = runCatching {
                preprocessChwNormalized(analysisCrop, 224, 224)
            }.getOrElse {
                preprocessChwNormalized(bitmap, 224, 224)
            }
            val logits = modelManager.runDeepfakeB0(deepfakeInput)
            val frequencyScore = quickFrequencyAuthenticity(analysisCrop)
            if (logits.size >= 2) {
                val score = analyzeDeepfakeB0Output(logits)
                (score.score * 0.85f + frequencyScore * 0.15f).coerceIn(0f, 100f)
            } else {
                frequencyScore
            }
        } finally {
            if (analysisCrop !== bitmap && !analysisCrop.isRecycled) {
                analysisCrop.recycle()
            }
        }
    }

    private fun reportProgress(onProgress: ((Float, String) -> Unit)?, progress: Float, label: String) {
        onProgress?.invoke(progress.coerceIn(0f, 1f), label)
    }

    /**
     * Detect faces using BlazeFace (via ModelManager), falling back to
     * Android's legacy API. Returns up to 5 face candidates.
     */
    private fun detectFaces(bitmap: Bitmap): List<FaceCandidate> =
        modelManager.detectFaces(bitmap, maxFaces = 5).map {
            FaceCandidate(box = it.box, confidence = it.confidence)
        }

    private fun buildFrameTimeline(durationUs: Long): List<Long> {
        if (durationUs <= 0L) return listOf(0L)
        val maxFrames = 24
        val frameCount = min(maxFrames, max(1, (durationUs / 500_000L).toInt() + 1))
        val lastTimestamp = durationUs.coerceAtLeast(1L) - 1L
        return List(frameCount) { index ->
            if (frameCount == 1) {
                0L
            } else {
                (index.toLong() * lastTimestamp / (frameCount - 1)).coerceIn(0L, lastTimestamp)
            }
        }
    }

    private fun loadAnalysisFrame(
        retriever: MediaMetadataRetriever,
        timeUs: Long,
        videoWidth: Int,
        videoHeight: Int
    ): Bitmap? {
        val maxEdge = 640
        val scale = min(1f, maxEdge.toFloat() / max(videoWidth, videoHeight).toFloat())
        val targetWidth = (videoWidth * scale).toInt().coerceAtLeast(224)
        val targetHeight = (videoHeight * scale).toInt().coerceAtLeast(224)

        return try {
            retriever.getScaledFrameAtTime(
                timeUs,
                MediaMetadataRetriever.OPTION_CLOSEST,
                targetWidth,
                targetHeight
            ) ?: retriever.getFrameAtTime(timeUs, MediaMetadataRetriever.OPTION_CLOSEST)
        } catch (_: Throwable) {
            try {
                retriever.getFrameAtTime(timeUs, MediaMetadataRetriever.OPTION_CLOSEST)
            } catch (_: Throwable) {
                null
            }
        }
    }

    private fun cropBitmap(bitmap: Bitmap, normalizedBox: BoundingBox, extraPadding: Float): Bitmap {
        val left = ((normalizedBox.x - extraPadding) * bitmap.width).toInt().coerceIn(0, bitmap.width - 1)
        val top = ((normalizedBox.y - extraPadding) * bitmap.height).toInt().coerceIn(0, bitmap.height - 1)
        val right = ceil((normalizedBox.x + normalizedBox.width + extraPadding) * bitmap.width)
            .toInt()
            .coerceIn(left + 1, bitmap.width)
        val bottom = ceil((normalizedBox.y + normalizedBox.height + extraPadding) * bitmap.height)
            .toInt()
            .coerceIn(top + 1, bitmap.height)
        return Bitmap.createBitmap(bitmap, left, top, right - left, bottom - top)
    }

    private fun preprocessChwNormalized(bitmap: Bitmap, width: Int, height: Int): FloatArray {
        val scaled = Bitmap.createScaledBitmap(bitmap, width, height, true)
        val pixels = IntArray(width * height)
        scaled.getPixels(pixels, 0, width, 0, 0, width, height)

        val values = FloatArray(width * height * 3)
        val mean = floatArrayOf(0.485f, 0.456f, 0.406f)
        val std = floatArrayOf(0.229f, 0.224f, 0.225f)

        for (y in 0 until height) {
            for (x in 0 until width) {
                val pixel = pixels[y * width + x]
                val r = (Color.red(pixel) / 255f - mean[0]) / std[0]
                val g = (Color.green(pixel) / 255f - mean[1]) / std[1]
                val b = (Color.blue(pixel) / 255f - mean[2]) / std[2]
                val index = y * width + x
                values[index] = r
                values[width * height + index] = g
                values[2 * width * height + index] = b
            }
        }

        if (scaled !== bitmap) scaled.recycle()
        return values
    }

    private fun analyzeDeepfakeB0Output(logits: FloatArray): ClassifierScore {
        if (logits.size < 2) {
            return neutralClassifierScore()
        }
        
        val maxLogit = max(logits[0], logits[1])
        val exp0 = exp(logits[0] - maxLogit)
        val exp1 = exp(logits[1] - maxLogit)
        val sumExp = exp0 + exp1
        val probReal = exp0 / sumExp
        val probFake = exp1 / sumExp
        
        return ClassifierScore(
            score = (probReal * 100f).coerceIn(0f, 100f),
            entropyNorm = 0.5f,
            top1 = max(probReal, probFake),
            margin = abs(probReal - probFake)
        )
    }

    private fun createPreviewBytes(bitmap: Bitmap): ByteArray {
        val stream = java.io.ByteArrayOutputStream()
        val scaled = Bitmap.createScaledBitmap(bitmap, 256, 256, true)
        scaled.compress(Bitmap.CompressFormat.JPEG, 70, stream)
        if (scaled !== bitmap) scaled.recycle()
        return stream.toByteArray()
    }

    private fun quickFrequencyAuthenticity(bitmap: Bitmap): Float {
        val scaled = Bitmap.createScaledBitmap(bitmap, 32, 32, true)
        val pixels = IntArray(32 * 32)
        val luminance = FloatArray(32 * 32)
        scaled.getPixels(pixels, 0, 32, 0, 0, 32, 32)
        for (i in pixels.indices) {
            val pixel = pixels[i]
            luminance[i] =
                (0.299f * Color.red(pixel) + 0.587f * Color.green(pixel) + 0.114f * Color.blue(pixel)) / 255f
        }
        if (scaled !== bitmap) scaled.recycle()

        var laplacianEnergy = 0f
        var count = 0
        for (y in 1 until 31) {
            for (x in 1 until 31) {
                val center = luminance[y * 32 + x]
                val left = luminance[y * 32 + x - 1]
                val right = luminance[y * 32 + x + 1]
                val top = luminance[(y - 1) * 32 + x]
                val bottom = luminance[(y + 1) * 32 + x]
                laplacianEnergy += abs(4f * center - left - right - top - bottom)
                count++
            }
        }

        val normalizedEnergy = (laplacianEnergy / count.coerceAtLeast(1)).coerceIn(0f, 1f)
        return when {
            normalizedEnergy in 0.04f..0.50f -> {
                val deviation = abs(normalizedEnergy - 0.20f) / 0.30f
                (80f + (1f - deviation) * 15f).coerceIn(78f, 95f)
            }
            normalizedEnergy < 0.04f -> (35f + normalizedEnergy * 1000f).coerceIn(35f, 75f)
            else -> (75f - (normalizedEnergy - 0.50f) * 90f).coerceIn(30f, 75f)
        }
    }

    private fun neutralClassifierScore(score: Float = 50f): ClassifierScore =
        ClassifierScore(
            score = score.coerceIn(0f, 100f),
            entropyNorm = 0.5f,
            top1 = 0.5f,
            margin = 0f
        )
}
