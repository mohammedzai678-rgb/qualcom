package com.deepshield.ai.watermark

import android.graphics.Bitmap
import android.graphics.Color
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.abs
import kotlin.math.max

@Singleton
class InvisibleWatermarkProcessor @Inject constructor() {

    // Default configuration from ShieldMnt/invisible-watermark
    private val block = 4

    /**
     * Convenience overload: embed a String payload into a bitmap.
     * Converts the payload to a UTF-8 BooleanArray and delegates to [encode].
     */
    fun embed(bitmap: Bitmap, payload: String, scale: Float = 36f): Bitmap {
        val bytes = payload.toByteArray(Charsets.UTF_8)
        val bits = BooleanArray(bytes.size * 8)
        for (i in bytes.indices) {
            for (b in 0..7) {
                bits[i * 8 + b] = (bytes[i].toInt() shr (7 - b) and 1) == 1
            }
        }
        return encode(bitmap, bits, scale)
    }

    /**
     * Embeds a bit array watermark using the dwtDCT (MaxDct) logic.
     * The algorithm modifies the Approximation (LL) band's maximum AC coefficient.
     */
    fun encode(bitmap: Bitmap, watermarkBits: BooleanArray, scale: Float = 36f): Bitmap {
        val wmLen = watermarkBits.size
        val width = bitmap.width
        val height = bitmap.height
        val pixels = IntArray(width * height)
        bitmap.getPixels(pixels, 0, width, 0, 0, width, height)

        // Convert RGB to YUV (using exact OpenCV BGR2YUV constants)
        val yArray = FloatArray(width * height)
        val uArray = FloatArray(width * height)
        val vArray = FloatArray(width * height)

        for (i in pixels.indices) {
            val r = Color.red(pixels[i]).toFloat()
            val g = Color.green(pixels[i]).toFloat()
            val b = Color.blue(pixels[i]).toFloat()

            // OpenCV BGR2YUV constants
            yArray[i] = 0.299f * r + 0.587f * g + 0.114f * b
            uArray[i] = -0.14713f * r - 0.28886f * g + 0.436f * b + 128f
            vArray[i] = 0.615f * r - 0.51499f * g - 0.10001f * b + 128f
        }

        // The repo encodes in U and V channels (scales = [0, 36, 36])
        processChannelEncode(uArray, width, height, watermarkBits, scale, wmLen)
        processChannelEncode(vArray, width, height, watermarkBits, scale, wmLen)

        // Convert YUV back to RGB
        val outPixels = IntArray(width * height)
        for (i in outPixels.indices) {
            val y = yArray[i]
            val u = uArray[i] - 128f
            val v = vArray[i] - 128f

            var r = (y + 1.13983f * v).toInt()
            var g = (y - 0.39465f * u - 0.58060f * v).toInt()
            var b = (y + 2.03211f * u).toInt()

            r = r.coerceIn(0, 255)
            g = g.coerceIn(0, 255)
            b = b.coerceIn(0, 255)

            outPixels[i] = Color.rgb(r, g, b)
        }

        val outBitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        outBitmap.setPixels(outPixels, 0, width, 0, 0, width, height)
        return outBitmap
    }

    /**
     * Extracts a bit array watermark using the dwtDCT (MaxDct) logic.
     */
    fun decode(bitmap: Bitmap, wmLen: Int = 256, scale: Float = 36f): BooleanArray {
        val width = bitmap.width
        val height = bitmap.height
        val pixels = IntArray(width * height)
        bitmap.getPixels(pixels, 0, width, 0, 0, width, height)

        val uArray = FloatArray(width * height)
        val vArray = FloatArray(width * height)

        for (i in pixels.indices) {
            val r = Color.red(pixels[i]).toFloat()
            val g = Color.green(pixels[i]).toFloat()
            val b = Color.blue(pixels[i]).toFloat()
            uArray[i] = -0.14713f * r - 0.28886f * g + 0.436f * b + 128f
            vArray[i] = 0.615f * r - 0.51499f * g - 0.10001f * b + 128f
        }

        val scores = Array(wmLen) { mutableListOf<Float>() }

        processChannelDecode(uArray, width, height, scale, scores, wmLen)
        processChannelDecode(vArray, width, height, scale, scores, wmLen)

        val decodedBits = BooleanArray(wmLen)
        for (i in 0 until wmLen) {
            val avgScore = if (scores[i].isNotEmpty()) scores[i].average().toFloat() else 0f
            decodedBits[i] = (avgScore * 255f > 127f)
        }
        return decodedBits
    }

    private fun processChannelEncode(channel: FloatArray, width: Int, height: Int, watermarkBits: BooleanArray, scale: Float, wmLen: Int) {
        val validWidth = (width / 4) * 4
        val validHeight = (height / 4) * 4
        
        val halfW = validWidth / 2
        val halfH = validHeight / 2
        
        val cA = FloatArray(halfW * halfH)
        val cH = FloatArray(halfW * halfH)
        val cV = FloatArray(halfW * halfH)
        val cD = FloatArray(halfW * halfH)

        // Forward Haar DWT 2D
        for (i in 0 until halfH) {
            for (j in 0 until halfW) {
                val a = channel[(2 * i) * width + (2 * j)]
                val b = channel[(2 * i) * width + (2 * j + 1)]
                val c = channel[(2 * i + 1) * width + (2 * j)]
                val d = channel[(2 * i + 1) * width + (2 * j + 1)]

                val idx = i * halfW + j
                cA[idx] = (a + b + c + d) / 2f
                cH[idx] = (a - b + c - d) / 2f
                cV[idx] = (a + b - c - d) / 2f
                cD[idx] = (a - b - c + d) / 2f
            }
        }

        // Encode watermark in cA blocks
        var bitNum = 0
        for (i in 0 until halfH / block) {
            for (j in 0 until halfW / block) {
                val wmBit = if (watermarkBits[bitNum % wmLen]) 1 else 0
                
                // Find pos of max absolute AC coefficient in the block
                var maxVal = -1f
                var maxPosI = 0
                var maxPosJ = 0
                
                for (bi in 0 until block) {
                    for (bj in 0 until block) {
                        if (bi == 0 && bj == 0) continue // Skip DC coefficient
                        val idx = (i * block + bi) * halfW + (j * block + bj)
                        val v = abs(cA[idx])
                        if (v > maxVal) {
                            maxVal = v
                            maxPosI = bi
                            maxPosJ = bj
                        }
                    }
                }
                
                // Diffuse DCT Matrix logic (from maxDct.py)
                val idx = (i * block + maxPosI) * halfW + (j * block + maxPosJ)
                val v = cA[idx]
                if (v >= 0.0f) {
                    cA[idx] = (v / scale).toInt() * scale + 0.25f * scale + 0.5f * wmBit * scale
                } else {
                    cA[idx] = -1.0f * ((abs(v) / scale).toInt() * scale + 0.25f * scale + 0.5f * wmBit * scale)
                }
                
                bitNum++
            }
        }

        // Inverse Haar DWT 2D
        for (i in 0 until halfH) {
            for (j in 0 until halfW) {
                val idx = i * halfW + j
                val a = cA[idx]
                val h = cH[idx]
                val v = cV[idx]
                val d = cD[idx]

                channel[(2 * i) * width + (2 * j)]     = (a + h + v + d) / 2f
                channel[(2 * i) * width + (2 * j + 1)] = (a - h + v - d) / 2f
                channel[(2 * i + 1) * width + (2 * j)] = (a + h - v - d) / 2f
                channel[(2 * i + 1) * width + (2 * j + 1)] = (a - h - v + d) / 2f
            }
        }
    }

    private fun processChannelDecode(channel: FloatArray, width: Int, height: Int, scale: Float, scores: Array<MutableList<Float>>, wmLen: Int) {
        val validWidth = (width / 4) * 4
        val validHeight = (height / 4) * 4
        
        val halfW = validWidth / 2
        val halfH = validHeight / 2
        
        val cA = FloatArray(halfW * halfH)

        // Forward Haar DWT 2D (Only need cA)
        for (i in 0 until halfH) {
            for (j in 0 until halfW) {
                val a = channel[(2 * i) * width + (2 * j)]
                val b = channel[(2 * i) * width + (2 * j + 1)]
                val c = channel[(2 * i + 1) * width + (2 * j)]
                val d = channel[(2 * i + 1) * width + (2 * j + 1)]

                val idx = i * halfW + j
                cA[idx] = (a + b + c + d) / 2f
            }
        }

        // Decode watermark from cA blocks
        var bitNum = 0
        for (i in 0 until halfH / block) {
            for (j in 0 until halfW / block) {
                var maxVal = -1f
                var maxPosI = 0
                var maxPosJ = 0
                
                for (bi in 0 until block) {
                    for (bj in 0 until block) {
                        if (bi == 0 && bj == 0) continue
                        val idx = (i * block + bi) * halfW + (j * block + bj)
                        val v = abs(cA[idx])
                        if (v > maxVal) {
                            maxVal = v
                            maxPosI = bi
                            maxPosJ = bj
                        }
                    }
                }
                
                val idx = (i * block + maxPosI) * halfW + (j * block + maxPosJ)
                val v = abs(cA[idx])
                
                val score = if ((v % scale) > 0.5f * scale) 1f else 0f
                scores[bitNum % wmLen].add(score)
                bitNum++
            }
        }
    }
}
