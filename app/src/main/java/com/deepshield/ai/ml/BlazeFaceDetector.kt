package com.deepshield.ai.ml

import android.graphics.Bitmap
import android.graphics.Color
import android.util.Log
import com.deepshield.ai.domain.model.BoundingBox
import org.tensorflow.lite.Interpreter
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min

/**
 * BlazeFace face detector using the MediaPipe face_detection_short_range TFLite model.
 *
 * Model: blazeface.tflite (~230KB, MediaPipe float16 short-range model)
 * Input:  [1, 128, 128, 3], float32, normalized to [-1, 1]
 * Output 0 (regressors):    [1, 896, 16] — bounding box deltas (dy,dx,dh,dw) + 12 keypoint coords
 * Output 1 (classificators):[1, 896, 1]  — score logits
 *
 * Anchor layout (total 896 anchors):
 *   - Layer 0 (stride=8,  feature map 16×16): 2 anchors/cell × 256 cells = 512
 *   - Layer 1 (stride=16, feature map  8×8):  2 anchors/cell ×  64 cells = 128
 *   - Layer 2 (stride=16, feature map  8×8):  2 anchors/cell ×  64 cells = 128
 *   - Layer 3 (stride=16, feature map  8×8):  2 anchors/cell ×  64 cells = 128
 *                                                                 Total = 896
 *
 * Reference: MediaPipe face_detection_short_range.tflite + SsdAnchorsCalculator
 */
class BlazeFaceDetector {

    companion object {
        private const val TAG = "BlazeFaceDetector"

        /** Model input resolution */
        const val INPUT_SIZE = 128

        /** Number of SSD anchors */
        const val NUM_ANCHORS = 896

        /** Number of coordinates per anchor (4 box + 12 keypoint) */
        const val NUM_COORDS = 16

        /** Score sigmoid threshold for candidate selection */
        const val SCORE_THRESHOLD = 0.5f

        /** IoU threshold for non-maximum suppression */
        const val IOU_THRESHOLD = 0.3f

        /** MediaPipe TensorsToDetectionsCalculator scale factors */
        const val X_SCALE = 128f
        const val Y_SCALE = 128f
        const val W_SCALE = 128f
        const val H_SCALE = 128f

        /**
         * Pre-computed SSD anchors as (cy, cx) pairs, normalized to [0, 1].
         * Generated at class-load time so there is zero per-inference overhead.
         */
        val ANCHORS: Array<FloatArray> by lazy { generateAnchors() }

        private fun generateAnchors(): Array<FloatArray> {
            val anchors = ArrayList<FloatArray>(NUM_ANCHORS)

            // Layer 0: stride=8 → 16×16 feature map, 2 anchors per cell
            for (y in 0 until 16) {
                for (x in 0 until 16) {
                    val cy = (y + 0.5f) / 16f
                    val cx = (x + 0.5f) / 16f
                    anchors.add(floatArrayOf(cy, cx))
                    anchors.add(floatArrayOf(cy, cx))
                }
            }
            // Layers 1–3: stride=16 → 8×8 feature map, 2 anchors per cell (×3 layers)
            repeat(3) {
                for (y in 0 until 8) {
                    for (x in 0 until 8) {
                        val cy = (y + 0.5f) / 8f
                        val cx = (x + 0.5f) / 8f
                        anchors.add(floatArrayOf(cy, cx))
                        anchors.add(floatArrayOf(cy, cx))
                    }
                }
            }

            check(anchors.size == NUM_ANCHORS) {
                "Anchor count mismatch: expected $NUM_ANCHORS, got ${anchors.size}"
            }
            return anchors.toTypedArray()
        }
    }

    /** Detected face with a normalized bounding box and confidence. */
    data class FaceBox(
        val box: BoundingBox,
        val confidence: Float
    )

    private var interpreter: Interpreter? = null

    /**
     * Initialize the detector from a TFLite flat-file buffer.
     *
     * @param modelBuffer Memory-mapped TFLite flatbuffer from assets.
     * @param acceleratorDelegate Optional hardware delegate (QnnDelegate, NnApiDelegate, etc.).
     *   If null, runs on CPU with 2 threads.
     */
    fun initialize(
        modelBuffer: java.nio.MappedByteBuffer,
        acceleratorDelegate: org.tensorflow.lite.Delegate? = null
    ) {
        val options = Interpreter.Options().apply {
            if (acceleratorDelegate != null) {
                addDelegate(acceleratorDelegate)
                // Delegate manages threading internally
                numThreads = 1
            } else {
                numThreads = 2
                useNNAPI = false
            }
        }
        interpreter = Interpreter(modelBuffer, options)
        val delegateName = if (acceleratorDelegate != null) "HW delegate" else "CPU"
        Log.i(TAG, "BlazeFace initialized [$delegateName] (${NUM_ANCHORS} anchors)")
    }

    fun isInitialized(): Boolean = interpreter != null

    /**
     * Run face detection on [bitmap].
     *
     * Returns bounding boxes in normalized [0,1] coordinates (x, y, width, height),
     * sorted by confidence descending. Returns an empty list if not initialized.
     */
    fun detect(bitmap: Bitmap): List<FaceBox> {
        val interp = interpreter ?: return emptyList()

        val inputBuffer = preprocessBitmap(bitmap)

        // Allocate output buffers — shape [1, NUM_ANCHORS, NUM_COORDS] and [1, NUM_ANCHORS, 1]
        val regressorsOut = Array(1) { Array(NUM_ANCHORS) { FloatArray(NUM_COORDS) } }
        val scoresOut     = Array(1) { Array(NUM_ANCHORS) { FloatArray(1) } }

        // Detect tensor ordering by inspecting output shapes
        val out0Shape = interp.getOutputTensor(0).shape()
        val outputMap = HashMap<Int, Any>(2)
        if (out0Shape.last() == NUM_COORDS) {
            // Standard: output0 = regressors [1,896,16], output1 = scores [1,896,1]
            outputMap[0] = regressorsOut
            outputMap[1] = scoresOut
        } else {
            // Reversed: output0 = scores [1,896,1], output1 = regressors [1,896,16]
            outputMap[0] = scoresOut
            outputMap[1] = regressorsOut
        }

        interp.runForMultipleInputsOutputs(arrayOf(inputBuffer), outputMap)

        return decodeAndNms(regressorsOut[0], scoresOut[0])
    }

    // ─── Pre-processing ────────────────────────────────────────────────────────

    private fun preprocessBitmap(bitmap: Bitmap): ByteBuffer {
        val scaled = Bitmap.createScaledBitmap(bitmap, INPUT_SIZE, INPUT_SIZE, true)
        // 128 × 128 × 3 channels × 4 bytes per float
        val buffer = ByteBuffer.allocateDirect(INPUT_SIZE * INPUT_SIZE * 3 * 4)
            .order(ByteOrder.nativeOrder())

        val pixels = IntArray(INPUT_SIZE * INPUT_SIZE)
        scaled.getPixels(pixels, 0, INPUT_SIZE, 0, 0, INPUT_SIZE, INPUT_SIZE)

        for (pixel in pixels) {
            // MediaPipe normalization: [0,255] → [-1, 1]
            buffer.putFloat((Color.red(pixel)   - 128f) / 128f)
            buffer.putFloat((Color.green(pixel) - 128f) / 128f)
            buffer.putFloat((Color.blue(pixel)  - 128f) / 128f)
        }

        if (scaled !== bitmap) scaled.recycle()
        buffer.rewind()
        return buffer
    }

    // ─── Post-processing ───────────────────────────────────────────────────────

    private fun decodeAndNms(
        regressors: Array<FloatArray>,
        scores: Array<FloatArray>
    ): List<FaceBox> {
        val candidates = ArrayList<FaceBox>(64)

        for (i in 0 until NUM_ANCHORS) {
            val rawScore = scores[i][0]
            // Stable sigmoid — clamp to avoid overflow on large logits
            val score = 1f / (1f + exp(-rawScore.toDouble().coerceIn(-60.0, 60.0))).toFloat()
            if (score < SCORE_THRESHOLD) continue

            val anchorCy = ANCHORS[i][0]
            val anchorCx = ANCHORS[i][1]

            // MediaPipe box decoding (TensorsToDetectionsCalculator convention):
            //   cy = raw[0] / Y_SCALE + anchor_cy
            //   cx = raw[1] / X_SCALE + anchor_cx
            //   h  = exp(raw[2] / H_SCALE)
            //   w  = exp(raw[3] / W_SCALE)
            val cy = regressors[i][0] / Y_SCALE + anchorCy
            val cx = regressors[i][1] / X_SCALE + anchorCx
            val h  = exp(regressors[i][2] / H_SCALE.toDouble()).toFloat()
            val w  = exp(regressors[i][3] / W_SCALE.toDouble()).toFloat()

            val xmin = (cx - w / 2f).coerceIn(0f, 1f)
            val ymin = (cy - h / 2f).coerceIn(0f, 1f)
            val xmax = (cx + w / 2f).coerceIn(0f, 1f)
            val ymax = (cy + h / 2f).coerceIn(0f, 1f)

            if (xmax <= xmin || ymax <= ymin) continue

            candidates.add(
                FaceBox(
                    box = BoundingBox(
                        x = xmin,
                        y = ymin,
                        width  = xmax - xmin,
                        height = ymax - ymin
                    ),
                    confidence = score
                )
            )
        }

        return weightedNms(candidates)
    }

    /**
     * Greedy NMS: keep the highest-confidence box, suppress all overlapping
     * boxes (IoU > threshold), repeat.
     */
    private fun weightedNms(boxes: List<FaceBox>): List<FaceBox> {
        if (boxes.isEmpty()) return emptyList()

        val sorted = boxes.sortedByDescending { it.confidence }.toMutableList()
        val kept   = ArrayList<FaceBox>(sorted.size)

        while (sorted.isNotEmpty()) {
            val best = sorted.removeAt(0)
            kept.add(best)
            sorted.removeAll { iou(best.box, it.box) > IOU_THRESHOLD }
        }

        return kept
    }

    private fun iou(a: BoundingBox, b: BoundingBox): Float {
        val interLeft   = max(a.x, b.x)
        val interTop    = max(a.y, b.y)
        val interRight  = min(a.x + a.width,  b.x + b.width)
        val interBottom = min(a.y + a.height, b.y + b.height)

        if (interRight <= interLeft || interBottom <= interTop) return 0f

        val interArea = (interRight - interLeft) * (interBottom - interTop)
        val aArea = a.width * a.height
        val bArea = b.width * b.height
        return interArea / (aArea + bArea - interArea)
    }

    fun close() {
        interpreter?.close()
        interpreter = null
    }
}
