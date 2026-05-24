package com.deepshield.ai.ml.audio

import android.content.Context
import android.net.Uri
import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.nio.FloatBuffer

class AudioDeepfakeDetector(private val context: Context) {

    private var ortEnv: OrtEnvironment? = null
    private var ortSession: OrtSession? = null
    private val mfccExtractor = MFCCExtractor()

    init {
        initModel()
    }

    private fun initModel() {
        try {
            ortEnv = OrtEnvironment.getEnvironment()
            val modelBytes = context.assets.open("my_model.onnx").readBytes()
            
            // Create a temp file since OrtSession prefers file paths
            val tempFile = File(context.cacheDir, "my_model.onnx")
            FileOutputStream(tempFile).use { it.write(modelBytes) }
            
            val options = OrtSession.SessionOptions().apply {
                addConfigEntry("session.intra_op.num_threads", "4")
            }
            ortSession = ortEnv?.createSession(tempFile.absolutePath, options)
        } catch (e: Exception) {
            e.printStackTrace()
            // Non-fatal if model not yet placed in assets
        }
    }

    suspend fun analyzeAudio(uri: Uri): AudioAnalysisResult = withContext(Dispatchers.Default) {
        val startTime = System.currentTimeMillis()
        
        try {
            // 1. Extract 40 MFCC bands over 500 frames natively
            val features = mfccExtractor.extractMfcc(context, uri)
            
            // Expected shape: [1, 40, 500, 1]
            val shape = longArrayOf(1, 40, 500, 1)
            
            // Flatten the nested arrays for OnnxTensor
            val flatBuffer = FloatBuffer.allocate(40 * 500)
            for (m in 0 until 40) {
                for (t in 0 until 500) {
                    flatBuffer.put(features[0][m][t][0])
                }
            }
            flatBuffer.rewind()

            ortEnv?.let { env ->
                ortSession?.let { session ->
                    val tensor = OnnxTensor.createTensor(env, flatBuffer, shape)
                    val inputs = mapOf("input" to tensor) // Match Keras converted input name
                    
                    val results = session.run(inputs)
                    val outputValues = results[0].value as Array<FloatArray>
                    
                    // CNN-BiLSTM outputs a single probability [0, 1] for binary classification
                    // Typically 1 = Deepfake/Spoof, 0 = Authentic/Bonafide
                    val deepfakeProb = outputValues[0][0]
                    val authenticProb = 1f - deepfakeProb
                    
                    tensor.close()
                    results.close()
                    
                    val processingTime = System.currentTimeMillis() - startTime
                    
                    return@withContext AudioAnalysisResult(
                        isAuthentic = authenticProb >= 0.5f,
                        confidenceScore = (maxOf(authenticProb, deepfakeProb) * 100f),
                        deepfakeProbability = deepfakeProb,
                        processingTimeMs = processingTime,
                        error = null
                    )
                }
            }
            
            return@withContext AudioAnalysisResult(
                isAuthentic = false,
                confidenceScore = 0f,
                deepfakeProbability = 0f,
                processingTimeMs = 0L,
                error = "Model not initialized"
            )

        } catch (e: Exception) {
            e.printStackTrace()
            return@withContext AudioAnalysisResult(
                isAuthentic = false,
                confidenceScore = 0f,
                deepfakeProbability = 0f,
                processingTimeMs = System.currentTimeMillis() - startTime,
                error = e.localizedMessage
            )
        }
    }

    fun close() {
        ortSession?.close()
        ortEnv?.close()
    }
}

data class AudioAnalysisResult(
    val isAuthentic: Boolean,
    val confidenceScore: Float,
    val deepfakeProbability: Float,
    val processingTimeMs: Long,
    val error: String?
)
