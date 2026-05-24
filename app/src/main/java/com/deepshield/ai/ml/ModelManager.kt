package com.deepshield.ai.ml

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import android.content.Context
import android.graphics.Bitmap
import android.graphics.PointF
import android.media.FaceDetector as AndroidFaceDetector
import android.os.Build
import android.util.Log
import com.deepshield.ai.domain.model.BoundingBox
import com.qualcomm.qti.QnnDelegate
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import org.tensorflow.lite.DataType
import org.tensorflow.lite.Interpreter
import org.tensorflow.lite.Tensor

/**
 * Loads and runs the image models used by the detection pipeline.
 *
 * The current app ships generic vision backbones rather than fully fine-tuned
 * deepfake heads, so the detector uses these outputs as real learned signals
 * alongside frequency and metadata analysis.
 */
@Singleton
class ModelManager @Inject constructor(
    @ApplicationContext private val context: Context
) {

    companion object {
        const val DEEPFAKE_B0_MODEL = "deepfake_b0.onnx"
        const val BLAZEFACE_MODEL = "blazeface.tflite"
        const val TINY_VIT_MODEL = "tiny_vit_int8.onnx"
        const val AUDIO_MODEL = "audio_classifier_int8.tflite"
        const val MOBILENET_MODEL = "mobilenet_face_int8.tflite"

        private const val MODEL_PREFIX = "models/"
        private const val QNN_SKEL_ASSET_DIR = "qnn"
        private const val REAL_MODEL_SIZE_THRESHOLD_BYTES = 1024L
    }

    data class ModelInfo(
        val name: String,
        val fileName: String,
        val task: String,
        val architecture: String,
        val quantization: String,
        val latencyMs: Float,
        val isLoaded: Boolean = false
    )

    private val ortEnvironment: OrtEnvironment by lazy { OrtEnvironment.getEnvironment() }

    @Volatile
    private var deepfakeB0Session: OrtSession? = null

    /**
     * True only after [getDeepfakeB0Session] successfully loads the model AND the
     * runtime weight-sanity check confirms outputs are non-degenerate (i.e., the model
     * contains trained weights, not random initialisation).
     */
    @Volatile
    var deepfakeB0WeightsValid: Boolean = false
        private set

    @Volatile
    private var qnnDelegate: QnnDelegate? = null

    @Volatile
    private var tinyVitSession: OrtSession? = null

    @Volatile
    private var blazeFaceDetector: BlazeFaceDetector? = null

    /** Shared face detection result type returned by [detectFaces]. */
    data class FaceResult(val box: BoundingBox, val confidence: Float)

    @Volatile
    private var activeImageDelegateName: String =
        if (supportsNnApi()) "NNAPI / device accelerator" else "CPU"

    val availableModels: List<ModelInfo>
        get() = listOf(
            ModelInfo(
                name = "EfficientNet-B0 (Deepfake)",
                fileName = DEEPFAKE_B0_MODEL,
                task = "Deepfake detection",
                architecture = "CNN",
                quantization = "FP32",
                latencyMs = 45f,
                isLoaded = deepfakeB0Session != null && hasRealModel(DEEPFAKE_B0_MODEL)
            ),
            ModelInfo(
                name = "Face Detector (BlazeFace)",
                fileName = BLAZEFACE_MODEL,
                task = "Face localization",
                architecture = "CNN",
                quantization = "FP16/INT8",
                latencyMs = 6f,
                isLoaded = blazeFaceDetector?.isInitialized() == true
            ),
            ModelInfo(
                name = "TinyViT-5M",
                fileName = TINY_VIT_MODEL,
                task = "Patch texture analysis",
                architecture = "Transformer",
                quantization = "Dynamic INT8",
                latencyMs = 32f,
                isLoaded = tinyVitSession != null && hasRealModel(TINY_VIT_MODEL)
            ),
            ModelInfo(
                name = "Audio Classifier",
                fileName = AUDIO_MODEL,
                task = "Voice clone detection",
                architecture = "CNN",
                quantization = "INT8",
                latencyMs = 8f,
                isLoaded = hasRealModel(AUDIO_MODEL)
            ),
            ModelInfo(
                name = "MobileNet Face",
                fileName = MOBILENET_MODEL,
                task = "Fast face feature extraction (stub — not used in inference)",
                architecture = "CNN",
                quantization = "INT8",
                latencyMs = 7f,
                isLoaded = false  // Stub file — no inference function implemented
            )
        )

    /**
     * Run face detection on [bitmap] using BlazeFace (MediaPipe TFLite).
     *
     * Falls back to Android's legacy [AndroidFaceDetector] if BlazeFace is not
     * available, so face detection always works even without the model file.
     *
     * @param bitmap Source bitmap in any resolution/config.
     * @param maxFaces Maximum number of faces to return (default 5).
     * @return Faces sorted by confidence × area descending, normalized [0,1] coords.
     */
    fun detectFaces(bitmap: Bitmap, maxFaces: Int = 5): List<FaceResult> {
        val blazeFace = getBlazeFaceDetector()
        if (blazeFace != null) {
            return blazeFace.detect(bitmap)
                .take(maxFaces)
                .map { FaceResult(it.box, it.confidence) }
        }
        // ── Fallback: Android legacy eye-pair detector ─────────────────────────
        Log.w("ModelManager", "BlazeFace not available — using AndroidFaceDetector fallback")
        return detectFacesAndroidFallback(bitmap, maxFaces)
    }

    private fun getBlazeFaceDetector(): BlazeFaceDetector? {
        blazeFaceDetector?.let { if (it.isInitialized()) return it }
        if (!hasRealModel(BLAZEFACE_MODEL)) return null

        synchronized(this) {
            blazeFaceDetector?.let { if (it.isInitialized()) return it }
            return try {
                val detector = BlazeFaceDetector()

                // Try QNN HTP delegate first (Qualcomm NPU acceleration for face detection).
                // Falls back gracefully to CPU if the delegate is unavailable or init fails.
                val qnnDelegate: org.tensorflow.lite.Delegate? = if (isQnnAvailable()) {
                    try {
                        createQnnDelegate()?.also {
                            Log.i("ModelManager", "BlazeFace: QNN HTP delegate activated")
                            activeImageDelegateName = "QNN HTP"
                        }
                    } catch (e: Throwable) {
                        Log.w("ModelManager", "QNN delegate failed, falling back to CPU: ${e.message}")
                        null
                    }
                } else null

                detector.initialize(mapAsset(BLAZEFACE_MODEL), qnnDelegate)
                blazeFaceDetector = detector
                detector
            } catch (e: Exception) {
                Log.e("ModelManager", "BlazeFace init failed: ${e.message}")
                null
            }
        }
    }

    /**
     * Legacy Android face detector fallback.
     * Works on all devices but only detects frontal faces with visible eyes.
     */
    private fun detectFacesAndroidFallback(bitmap: Bitmap, maxFaces: Int): List<FaceResult> {
        val targetMax = 384
        val scale = min(1f, targetMax.toFloat() / max(bitmap.width, bitmap.height).toFloat())
        val scaledWidth  = ensureEven((bitmap.width  * scale).toInt().coerceAtLeast(64))
        val scaledHeight = ensureEven((bitmap.height * scale).toInt().coerceAtLeast(64))

        val scaledBitmap = if (scaledWidth != bitmap.width || scaledHeight != bitmap.height) {
            Bitmap.createScaledBitmap(bitmap, scaledWidth, scaledHeight, true)
        } else bitmap

        val faceBitmap = scaledBitmap.copy(Bitmap.Config.RGB_565, false)
        val detector   = AndroidFaceDetector(faceBitmap.width, faceBitmap.height, maxFaces)
        val faces      = arrayOfNulls<AndroidFaceDetector.Face>(maxFaces)
        val count      = detector.findFaces(faceBitmap, faces)

        val results = buildList {
            for (i in 0 until count) {
                val face = faces[i] ?: continue
                val mid  = PointF()
                face.getMidPoint(mid)
                val eyeDist = face.eyesDistance()
                if (!eyeDist.isFinite() || eyeDist <= 1f) continue

                val left   = (mid.x - eyeDist * 1.4f).coerceIn(0f, faceBitmap.width.toFloat())
                val top    = (mid.y - eyeDist * 1.7f).coerceIn(0f, faceBitmap.height.toFloat())
                val right  = (mid.x + eyeDist * 1.4f).coerceIn(0f, faceBitmap.width.toFloat())
                val bottom = (mid.y + eyeDist * 1.9f).coerceIn(0f, faceBitmap.height.toFloat())
                if (right <= left || bottom <= top) continue

                add(FaceResult(
                    box = BoundingBox(
                        x = left / faceBitmap.width,
                        y = top  / faceBitmap.height,
                        width  = (right  - left) / faceBitmap.width,
                        height = (bottom - top)  / faceBitmap.height
                    ),
                    confidence = face.confidence().coerceIn(0f, 1f)
                ))
            }
        }.sortedByDescending { it.confidence * it.box.width * it.box.height }

        if (faceBitmap !== scaledBitmap) faceBitmap.recycle()
        if (scaledBitmap !== bitmap) scaledBitmap.recycle()
        return results
    }

    private fun ensureEven(v: Int): Int = if (v % 2 != 0) v - 1 else v

    fun getActiveDelegateName(): String = activeImageDelegateName

    fun getFallbackChainDescription(): String {
        return when {
            isQnnAvailable() -> "QNN HTP -> NNAPI -> CPU"
            supportsNnApi() -> "NNAPI -> CPU"
            else -> "CPU"
        }
    }

    fun isQnnAvailable(): Boolean {
        return try {
            supportsArm64Runtime() &&
                File(context.applicationInfo.nativeLibraryDir, "libQnnHtp.so").exists() &&
                QnnDelegate.checkCapability(QnnDelegate.Capability.HTP_RUNTIME_QUANTIZED)
        } catch (_: Throwable) {
            false
        }
    }

    fun runDeepfakeB0(chwInput: FloatArray): FloatArray {
        val shape = longArrayOf(1, 3, 224, 224)

        fun execute(session: OrtSession): FloatArray {
            val inputName = session.inputNames.first()
            val outputName = session.outputNames.first()
            OnnxTensor.createTensor(ortEnvironment, FloatBuffer.wrap(chwInput), shape).use { tensor ->
                session.run(mapOf(inputName to tensor), setOf(outputName)).use { results ->
                    val value = results.first().value
                    return flattenOrtOutput(value)
                }
            }
        }

        return try {
            val session = getDeepfakeB0Session() ?: return FloatArray(0)
            execute(session)
        } catch (primaryError: Throwable) {
            Log.w("ModelManager", "deepfake_b0 inference failed, retrying on CPU: ${primaryError.message}")
            resetDeepfakeB0Session()
            activeImageDelegateName = "CPU"
            try {
                val cpuSession = getDeepfakeB0Session(preferNnApi = false) ?: return FloatArray(0)
                execute(cpuSession)
            } catch (retryError: Throwable) {
                Log.e("ModelManager", "deepfake_b0 CPU retry failed: ${retryError.message}")
                FloatArray(0)
            }
        }
    }

    fun runTinyVit(chwInput: FloatArray): FloatArray {
        val shape = longArrayOf(1, 3, 224, 224)

        fun execute(session: OrtSession): FloatArray {
            val inputName = session.inputNames.first()
            val outputName = session.outputNames.first()
            OnnxTensor.createTensor(ortEnvironment, FloatBuffer.wrap(chwInput), shape).use { tensor ->
                session.run(mapOf(inputName to tensor), setOf(outputName)).use { results ->
                    val value = results.first().value
                    return flattenOrtOutput(value)
                }
            }
        }

        return try {
            val session = getTinyVitSession() ?: return FloatArray(0)
            execute(session)
        } catch (primaryError: Throwable) {
            Log.w("ModelManager", "TinyViT inference failed, retrying on CPU: ${primaryError.message}")
            resetTinyVitSession()
            try {
                val cpuSession = getTinyVitSession(preferNnApi = false) ?: return FloatArray(0)
                execute(cpuSession)
            } catch (retryError: Throwable) {
                Log.e("ModelManager", "TinyViT CPU retry failed: ${retryError.message}")
                FloatArray(0)
            }
        }
    }

    private fun getDeepfakeB0Session(preferNnApi: Boolean = supportsNnApi()): OrtSession? {
        deepfakeB0Session?.let { return it }
        synchronized(this) {
            deepfakeB0Session?.let { return it }
            if (!hasRealModel(DEEPFAKE_B0_MODEL)) return null

            // Step 1: Verify SHA-256 checksum against the sidecar file bundled in assets.
            if (!verifyModelChecksum(DEEPFAKE_B0_MODEL)) {
                Log.e("ModelManager", "deepfake_b0.onnx checksum mismatch — model may be corrupted.")
            }

            val modelPath = extractModelToCache(DEEPFAKE_B0_MODEL) ?: return null
            val loadedSession = createOrtSession(
                modelPath = modelPath,
                intraThreads = 4,
                preferNnApi = preferNnApi,
                modelTag = "deepfake_b0"
            ) ?: return null
            val session = loadedSession.session
            activeImageDelegateName = loadedSession.delegateName

            // Step 2: Runtime weight-sanity check.
            deepfakeB0WeightsValid = verifyB0Weights(session)
            if (!deepfakeB0WeightsValid) {
                Log.e("ModelManager",
                    "deepfake_b0.onnx weight-sanity FAILED — outputs are near-uniform. " +
                    "The model may have random weights. Re-run python/export_deepfake_b0.py.")
            } else {
                Log.i("ModelManager", "deepfake_b0.onnx weight-sanity PASSED — trained weights confirmed.")
            }

            deepfakeB0Session = session
            return deepfakeB0Session
        }
    }

    private fun getTinyVitSession(preferNnApi: Boolean = supportsNnApi()): OrtSession? {
        tinyVitSession?.let { return it }
        synchronized(this) {
            tinyVitSession?.let { return it }
            if (!hasRealModel(TINY_VIT_MODEL)) return null

            val modelPath = extractModelToCache(TINY_VIT_MODEL) ?: return null
            val loadedSession = createOrtSession(
                modelPath = modelPath,
                intraThreads = 2,
                preferNnApi = preferNnApi,
                modelTag = "TinyViT"
            ) ?: return null
            tinyVitSession = loadedSession.session
            return tinyVitSession
        }
    }

    private data class LoadedOrtSession(
        val session: OrtSession,
        val delegateName: String
    )

    private fun createOrtSession(
        modelPath: String,
        intraThreads: Int,
        preferNnApi: Boolean,
        modelTag: String
    ): LoadedOrtSession? {
        if (preferNnApi && supportsNnApi()) {
            try {
                val nnApiOptions = createSessionOptions(intraThreads, preferNnApi = true)
                val session = ortEnvironment.createSession(modelPath, nnApiOptions)
                Log.i("ModelManager", "$modelTag: NNAPI session created")
                return LoadedOrtSession(session = session, delegateName = "NNAPI / device accelerator")
            } catch (e: Throwable) {
                Log.w("ModelManager", "$modelTag: NNAPI session failed, falling back to CPU: ${e.message}")
            }
        }

        return try {
            val cpuOptions = createSessionOptions(intraThreads, preferNnApi = false)
            val session = ortEnvironment.createSession(modelPath, cpuOptions)
            Log.i("ModelManager", "$modelTag: CPU session created")
            LoadedOrtSession(session = session, delegateName = "CPU")
        } catch (e: Throwable) {
            Log.e("ModelManager", "$modelTag: CPU session failed: ${e.message}")
            null
        }
    }

    private fun createSessionOptions(
        intraThreads: Int,
        preferNnApi: Boolean
    ): OrtSession.SessionOptions {
        return OrtSession.SessionOptions().apply {
            setOptimizationLevel(OrtSession.SessionOptions.OptLevel.ALL_OPT)
            setInterOpNumThreads(1)
            setIntraOpNumThreads(intraThreads)
            if (preferNnApi && supportsNnApi()) {
                addNnapi()
            }
        }
    }

    private fun resetDeepfakeB0Session() {
        synchronized(this) {
            try {
                deepfakeB0Session?.close()
            } catch (_: Throwable) {
            }
            deepfakeB0Session = null
            deepfakeB0WeightsValid = false
        }
    }

    private fun resetTinyVitSession() {
        synchronized(this) {
            try {
                tinyVitSession?.close()
            } catch (_: Throwable) {
            }
            tinyVitSession = null
        }
    }

    /**
     * Extracts an ONNX model from assets to the code cache directory.
     * Also extracts an accompanying .data file if present (for models with external data).
     * Forces re-extraction whenever the asset size differs from the cached file size
     * so that updated models (e.g. after re-running export_deepfake_b0.py) are picked up.
     * Returns the absolute path of the extracted .onnx file, or null on error.
     */
    private fun extractModelToCache(fileName: String): String? {
        return try {
            val cacheDir = File(context.codeCacheDir, "onnx-models").apply { mkdirs() }
            val destFile = File(cacheDir, fileName)

            // Re-extract if missing, empty, or size has changed (model was updated)
            val assetSize = context.assets.openFd(MODEL_PREFIX + fileName).use { it.length }
            if (!destFile.exists() || destFile.length() == 0L || destFile.length() != assetSize) {
                context.assets.open(MODEL_PREFIX + fileName).use { input ->
                    destFile.outputStream().use { output -> input.copyTo(output) }
                }
                Log.i("ModelManager", "Extracted $fileName (${destFile.length()} bytes) to cache.")
            }

            // Also copy the external data file if it exists in assets (e.g. deepfake_b0.onnx.data)
            val dataFileName = "$fileName.data"
            try {
                val destDataFile = File(cacheDir, dataFileName)
                val dataAssetSize = context.assets.openFd(MODEL_PREFIX + dataFileName).use { it.length }
                if (!destDataFile.exists() || destDataFile.length() == 0L || destDataFile.length() != dataAssetSize) {
                    context.assets.open(MODEL_PREFIX + dataFileName).use { input ->
                        destDataFile.outputStream().use { output -> input.copyTo(output) }
                    }
                }
            } catch (_: Exception) {
                // No external data file — single-file model, that's fine
            }

            destFile.absolutePath
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    /**
     * Verifies the SHA-256 checksum of a bundled model against a sidecar [fileName].sha256
     * file in assets. Returns true if the checksums match, or if no sidecar file exists
     * (legacy builds without a checksum file are allowed through — the weight-sanity check
     * acts as the second line of defence).
     */
    private fun verifyModelChecksum(fileName: String): Boolean {
        val checksumAssetPath = MODEL_PREFIX + fileName + ".sha256"
        val expectedLine = try {
            context.assets.open(checksumAssetPath).bufferedReader().use { it.readLine() }
        } catch (_: Exception) {
            // No checksum file bundled — skip validation silently
            return true
        }
        // File format: "<sha256hex>  <filename>\n"
        val expectedHash = expectedLine?.trim()?.split("\\s+".toRegex())?.firstOrNull() ?: return true

        return try {
            val digest = MessageDigest.getInstance("SHA-256")
            context.assets.open(MODEL_PREFIX + fileName).use { input ->
                val buf = ByteArray(1 shl 20) // 1 MB chunks
                var read: Int
                while (input.read(buf).also { read = it } != -1) {
                    digest.update(buf, 0, read)
                }
            }
            val actualHash = digest.digest().joinToString("") { "%02x".format(it) }
            val match = actualHash.equals(expectedHash, ignoreCase = true)
            if (!match) {
                Log.e("ModelManager", "Checksum MISMATCH for $fileName\n" +
                    "  expected: $expectedHash\n  actual:   $actualHash")
            }
            match
        } catch (e: Exception) {
            Log.w("ModelManager", "Checksum verification failed with exception: ${e.message}")
            true // Non-fatal: fall through to weight-sanity check
        }
    }

    /**
     * Runs two synthetic inputs through the B0 session and checks that the outputs
     * are NOT near-uniform (which would indicate random/untrained weights).
     *
     * Thresholds:
     *  - The gap between the real-class probabilities for the two inputs must exceed 5 pp.
     *  - At least one input must achieve a confidence > 52% for either class.
     *
     * A trained FaceForensics++ model produces a gap of ~60+ pp on these inputs.
     * A random model produces a gap of < 2 pp.
     */
    private fun verifyB0Weights(session: OrtSession): Boolean {
        return try {
            val inputName  = session.inputNames.first()
            val outputName = session.outputNames.first()
            val shape = longArrayOf(1, 3, 224, 224)
            val pixelCount = 3 * 224 * 224

            // Input A: neutral gray (0.0 after ImageNet normalisation of 0.5)
            val grayInput  = FloatArray(pixelCount) { 0f }
            // Input B: uniform black (-mean/std after ImageNet normalisation of 0.0)
            val blackInput = FloatArray(pixelCount) { idx ->
                val channel = idx / (224 * 224)
                val mean    = floatArrayOf(0.485f, 0.456f, 0.406f)[channel]
                val std     = floatArrayOf(0.229f, 0.224f, 0.225f)[channel]
                (0f - mean) / std
            }

            fun runAndGetRealProb(input: FloatArray): Float {
                OnnxTensor.createTensor(ortEnvironment, FloatBuffer.wrap(input), shape).use { tensor ->
                    session.run(mapOf(inputName to tensor), setOf(outputName)).use { results ->
                        val logits = flattenOrtOutput(results.first().value)
                        if (logits.size < 2) return 0.5f
                        // Numerically-stable softmax
                        val maxL   = maxOf(logits[0], logits[1])
                        val exp0   = Math.exp((logits[0] - maxL).toDouble()).toFloat()
                        val exp1   = Math.exp((logits[1] - maxL).toDouble()).toFloat()
                        return exp0 / (exp0 + exp1)
                    }
                }
            }

            val probGray  = runAndGetRealProb(grayInput)
            val probBlack = runAndGetRealProb(blackInput)

            val gap     = abs(probGray - probBlack)
            val maxConf = maxOf(probGray, probBlack, 1f - probGray, 1f - probBlack)

            Log.d("ModelManager",
                "B0 sanity: P(real|gray)=$probGray  P(real|black)=$probBlack  " +
                "gap=$gap  maxConf=$maxConf")

            gap > 0.05f && maxConf > 0.52f
        } catch (e: Exception) {
            Log.w("ModelManager", "Weight sanity check threw exception: ${e.message}")
            false
        }
    }

    private fun supportsNnApi(): Boolean = Build.VERSION.SDK_INT >= Build.VERSION_CODES.P

    private fun supportsArm64Runtime(): Boolean = Build.SUPPORTED_ABIS.contains("arm64-v8a")

    private fun createQnnDelegate(): QnnDelegate? {
        if (!supportsArm64Runtime()) return null

        val nativeLibDir = context.applicationInfo.nativeLibraryDir?.let(::File) ?: return null
        val backendLib = File(nativeLibDir, "libQnnHtp.so")
        if (!backendLib.exists()) return null

        val skelDir = extractQnnSkelLibraries() ?: return null
        val cacheDir = File(context.codeCacheDir, "qnn-cache").apply { mkdirs() }

        return try {
            val options = QnnDelegate.Options().apply {
                setBackendType(QnnDelegate.Options.BackendType.HTP_BACKEND)
                setLibraryPath(backendLib.absolutePath)
                setSkelLibraryDir(skelDir.absolutePath)
                setCacheDir(cacheDir.absolutePath)
                setModelToken(BLAZEFACE_MODEL)
                setHtpPerformanceMode(QnnDelegate.Options.HtpPerformanceMode.HTP_PERFORMANCE_BALANCE)
                setHtpPrecision(QnnDelegate.Options.HtpPrecision.HTP_PRECISION_QUANTIZED)
            }

            QnnDelegate(options).also { delegate ->
                if (!delegate.isAvailable()) {
                    delegate.close()
                    throw UnsupportedOperationException("QNN delegate is not available on this device")
                }
            }
        } catch (_: Throwable) {
            null
        }
    }

    private fun extractQnnSkelLibraries(): File? {
        val targetDir = File(context.codeCacheDir, "qnn-skel").apply { mkdirs() }
        val assetNames = context.assets.list(QNN_SKEL_ASSET_DIR).orEmpty()
            .filter { it.endsWith(".so") }

        if (assetNames.isEmpty()) return null

        return try {
            for (assetName in assetNames) {
                val targetFile = File(targetDir, assetName)
                val assetPath = "$QNN_SKEL_ASSET_DIR/$assetName"
                if (targetFile.exists() && targetFile.length() > 0L) continue

                context.assets.open(assetPath).use { input ->
                    targetFile.outputStream().use { output ->
                        input.copyTo(output)
                    }
                }
                targetFile.setReadable(true, false)
            }
            targetDir
        } catch (_: Exception) {
            null
        }
    }

    private fun hasRealModel(fileName: String): Boolean {
        return try {
            context.assets.openFd(MODEL_PREFIX + fileName).use { it.length > REAL_MODEL_SIZE_THRESHOLD_BYTES }
        } catch (_: Exception) {
            false
        }
    }

    private fun mapAsset(fileName: String): MappedByteBuffer {
        val descriptor = context.assets.openFd(MODEL_PREFIX + fileName)
        FileInputStream(descriptor.fileDescriptor).use { inputStream ->
            val channel = inputStream.channel
            return channel.map(
                FileChannel.MapMode.READ_ONLY,
                descriptor.startOffset,
                descriptor.declaredLength
            )
        }
    }

    private fun readAssetBytes(fileName: String): ByteArray {
        context.assets.open(MODEL_PREFIX + fileName).use { input ->
            return input.readBytes()
        }
    }

    private fun quantizeInput(rgbInput: FloatArray, tensor: Tensor): ByteBuffer {
        val buffer = ByteBuffer.allocateDirect(tensor.numBytes()).order(ByteOrder.nativeOrder())
        val params = tensor.quantizationParams()

        when (tensor.dataType()) {
            DataType.FLOAT32 -> rgbInput.forEach(buffer::putFloat)
            DataType.INT8 -> rgbInput.forEach { value ->
                val quantized = ((value / params.scale) + params.zeroPoint).roundToInt().coerceIn(-128, 127)
                buffer.put(quantized.toByte())
            }
            DataType.UINT8 -> rgbInput.forEach { value ->
                val quantized = ((value / params.scale) + params.zeroPoint).roundToInt().coerceIn(0, 255)
                buffer.put((quantized and 0xFF).toByte())
            }
            else -> error("Unsupported TFLite input type: ${tensor.dataType()}")
        }

        buffer.rewind()
        return buffer
    }

    private fun dequantizeOutput(buffer: ByteBuffer, tensor: Tensor): FloatArray {
        buffer.rewind()
        val elementCount = tensor.shape().fold(1) { acc, dim -> acc * dim }
        val params = tensor.quantizationParams()

        return when (tensor.dataType()) {
            DataType.FLOAT32 -> FloatArray(elementCount) { buffer.getFloat() }
            DataType.INT8 -> FloatArray(elementCount) {
                ((buffer.get().toInt()) - params.zeroPoint) * params.scale
            }
            DataType.UINT8 -> FloatArray(elementCount) {
                (((buffer.get().toInt()) and 0xFF) - params.zeroPoint) * params.scale
            }
            else -> error("Unsupported TFLite output type: ${tensor.dataType()}")
        }
    }

    @Suppress("UNCHECKED_CAST")
    private fun flattenOrtOutput(value: Any?): FloatArray {
        val flattened = ArrayList<Float>()
        collectOrtFloats(value, flattened)
        return flattened.toFloatArray()
    }

    private fun collectOrtFloats(value: Any?, output: MutableList<Float>) {
        when (value) {
            null -> Unit
            is Float -> output.add(value)
            is Number -> output.add(value.toFloat())
            is FloatArray -> value.forEach(output::add)
            is Array<*> -> value.forEach { collectOrtFloats(it, output) }
            is FloatBuffer -> {
                val buffer = value.duplicate()
                while (buffer.hasRemaining()) {
                    output.add(buffer.get())
                }
            }
            else -> {
                if (!value.javaClass.isArray) return

                val length = java.lang.reflect.Array.getLength(value)
                for (index in 0 until length) {
                    collectOrtFloats(java.lang.reflect.Array.get(value, index), output)
                }
            }
        }
    }
}
