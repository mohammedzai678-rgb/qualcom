package com.deepshield.ai.watermark

import android.graphics.Bitmap
import android.graphics.Color
import com.deepshield.ai.domain.model.WatermarkMode
import com.deepshield.ai.domain.model.WatermarkPayload
import java.security.MessageDigest
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.cos
import kotlin.math.sqrt

/**
 * DCT-domain steganographic watermark engine.
 *
 * Uses a compact 256-bit payload so the app can recover a stable manifest token
 * from saved files and resolve the full provenance record locally.
 */
@Singleton
class DctWatermarkEngine @Inject constructor(
    private val invisibleProcessor: InvisibleWatermarkProcessor
) {

    companion object {
        private const val BLOCK_SIZE = 8
        private const val PI = Math.PI
        private const val PAYLOAD_BYTES = 32
        private const val PAYLOAD_BITS = PAYLOAD_BYTES * 8
        private const val PAYLOAD_VERSION: Byte = 1
        private const val MAGIC_0: Byte = 0x44
        private const val MAGIC_1: Byte = 0x53
    }

    data class WatermarkInspection(
        val mode: WatermarkMode,
        val captureTimestamp: Long,
        val manifestToken: String,
        val ownerToken: String,
        val deviceToken: String,
        val checksumValid: Boolean
    )

    fun embedWatermark(
        original: Bitmap,
        mode: WatermarkMode = WatermarkMode.INVISIBLE,
        deviceId: String = "SNAPDRAGON_8GEN3",
        ownerHash: String = ""
    ): Pair<Bitmap, WatermarkPayload> {
        val mutableBitmap = original.copy(Bitmap.Config.ARGB_8888, true)
        val payload = WatermarkPayload(
            deviceId = deviceId,
            ownerHash = ownerHash.ifEmpty { computeHash(deviceId + System.currentTimeMillis()) },
            captureTimestamp = System.currentTimeMillis(),
            c2paManifestId = UUID.randomUUID().toString(),
            mode = mode,
            integrityScore = 100f
        )

        val payloadBits = payloadToBits(payload)
        val strength = when (mode) {
            WatermarkMode.INVISIBLE -> 3
            WatermarkMode.ROBUST -> 8
            WatermarkMode.FRAGILE -> 1
        }

        return if (mode == WatermarkMode.INVISIBLE) {
            val finalBitmap = invisibleProcessor.encode(mutableBitmap, payloadBits)
            if (finalBitmap !== mutableBitmap && !mutableBitmap.isRecycled) {
                mutableBitmap.recycle()
            }
            Pair(
                finalBitmap,
                payload.copy(contentHash = computeContentHash(finalBitmap))
            )
        } else {
            embedBitsIntoDCT(mutableBitmap, payloadBits, strength)
            Pair(
                mutableBitmap,
                payload.copy(contentHash = computeContentHash(mutableBitmap))
            )
        }
    }

    fun verifyWatermark(bitmap: Bitmap, originalPayload: WatermarkPayload?): WatermarkPayload {
        val inspection = inspectWatermark(bitmap)
        val integrity = if (inspection == null) {
            0f
        } else if (originalPayload != null) {
            val expectedToken = manifestToken(originalPayload.c2paManifestId)
            if (expectedToken.equals(inspection.manifestToken, ignoreCase = true)) 82f else 18f
        } else {
            70f
        }

        return WatermarkPayload(
            deviceId = originalPayload?.deviceId.orEmpty(),
            ownerHash = originalPayload?.ownerHash.orEmpty(),
            captureTimestamp = inspection?.captureTimestamp ?: (originalPayload?.captureTimestamp ?: 0L),
            c2paManifestId = originalPayload?.c2paManifestId.orEmpty(),
            contentHash = originalPayload?.contentHash.orEmpty(),
            signature = originalPayload?.signature.orEmpty(),
            mode = inspection?.mode ?: (originalPayload?.mode ?: WatermarkMode.INVISIBLE),
            integrityScore = integrity
        )
    }

    fun inspectWatermark(bitmap: Bitmap): WatermarkInspection? {
        val invisibleInspection = bitsToInspection(invisibleProcessor.decode(bitmap, wmLen = PAYLOAD_BITS))
        if (invisibleInspection?.checksumValid == true) {
            return invisibleInspection
        }

        val dctInspection = bitsToInspection(extractBitsFromDCT(bitmap, PAYLOAD_BITS))
        return dctInspection?.takeIf { it.checksumValid }
    }

    fun manifestToken(manifestId: String): String = tokenHex(manifestId, 8)

    private fun embedBitsIntoDCT(bitmap: Bitmap, bits: BooleanArray, strength: Int) {
        val width = bitmap.width
        val height = bitmap.height
        var bitIndex = 0

        val blocksX = width / BLOCK_SIZE
        val blocksY = height / BLOCK_SIZE

        for (by in 0 until blocksY) {
            for (bx in 0 until blocksX) {
                if (bitIndex >= bits.size) return

                val block = Array(BLOCK_SIZE) { FloatArray(BLOCK_SIZE) }
                for (y in 0 until BLOCK_SIZE) {
                    for (x in 0 until BLOCK_SIZE) {
                        val px = bx * BLOCK_SIZE + x
                        val py = by * BLOCK_SIZE + y
                        if (px < width && py < height) {
                            val pixel = bitmap.getPixel(px, py)
                            block[y][x] = Color.blue(pixel).toFloat()
                        }
                    }
                }

                val dctBlock = forwardDCT(block)
                val bit = bits[bitIndex]
                val coeff = dctBlock[4][3]

                dctBlock[4][3] = if (bit) {
                    (kotlin.math.floor(coeff / strength.toDouble()) * strength + strength / 2.0).toFloat()
                } else {
                    (kotlin.math.floor(coeff / strength.toDouble()) * strength).toFloat()
                }

                val reconstructed = inverseDCT(dctBlock)

                for (y in 0 until BLOCK_SIZE) {
                    for (x in 0 until BLOCK_SIZE) {
                        val px = bx * BLOCK_SIZE + x
                        val py = by * BLOCK_SIZE + y
                        if (px < width && py < height) {
                            val originalPixel = bitmap.getPixel(px, py)
                            val newBlue = reconstructed[y][x].toInt().coerceIn(0, 255)
                            val newPixel = Color.argb(
                                Color.alpha(originalPixel),
                                Color.red(originalPixel),
                                Color.green(originalPixel),
                                newBlue
                            )
                            bitmap.setPixel(px, py, newPixel)
                        }
                    }
                }

                bitIndex++
            }
        }
    }

    private fun extractBitsFromDCT(bitmap: Bitmap, bitCount: Int): BooleanArray {
        val width = bitmap.width
        val height = bitmap.height
        val bits = mutableListOf<Boolean>()
        val blocksX = width / BLOCK_SIZE
        val blocksY = height / BLOCK_SIZE

        loop@ for (by in 0 until blocksY) {
            for (bx in 0 until blocksX) {
                if (bits.size >= bitCount) break@loop

                val block = Array(BLOCK_SIZE) { FloatArray(BLOCK_SIZE) }
                for (y in 0 until BLOCK_SIZE) {
                    for (x in 0 until BLOCK_SIZE) {
                        val px = bx * BLOCK_SIZE + x
                        val py = by * BLOCK_SIZE + y
                        if (px < width && py < height) {
                            block[y][x] = Color.blue(bitmap.getPixel(px, py)).toFloat()
                        }
                    }
                }

                val dctBlock = forwardDCT(block)
                val coeff = dctBlock[4][3]
                bits.add(coeff % 2 >= 0.5f)
            }
        }

        return bits.toBooleanArray()
    }

    private fun forwardDCT(block: Array<FloatArray>): Array<FloatArray> {
        val result = Array(BLOCK_SIZE) { FloatArray(BLOCK_SIZE) }
        for (u in 0 until BLOCK_SIZE) {
            for (v in 0 until BLOCK_SIZE) {
                var sum = 0.0
                for (x in 0 until BLOCK_SIZE) {
                    for (y in 0 until BLOCK_SIZE) {
                        sum += block[x][y] *
                            cos((2 * x + 1) * u * PI / (2 * BLOCK_SIZE)) *
                            cos((2 * y + 1) * v * PI / (2 * BLOCK_SIZE))
                    }
                }
                val cu = if (u == 0) 1.0 / sqrt(2.0) else 1.0
                val cv = if (v == 0) 1.0 / sqrt(2.0) else 1.0
                result[u][v] = (0.25 * cu * cv * sum).toFloat()
            }
        }
        return result
    }

    private fun inverseDCT(dctBlock: Array<FloatArray>): Array<FloatArray> {
        val result = Array(BLOCK_SIZE) { FloatArray(BLOCK_SIZE) }
        for (x in 0 until BLOCK_SIZE) {
            for (y in 0 until BLOCK_SIZE) {
                var sum = 0.0
                for (u in 0 until BLOCK_SIZE) {
                    for (v in 0 until BLOCK_SIZE) {
                        val cu = if (u == 0) 1.0 / sqrt(2.0) else 1.0
                        val cv = if (v == 0) 1.0 / sqrt(2.0) else 1.0
                        sum += cu * cv * dctBlock[u][v] *
                            cos((2 * x + 1) * u * PI / (2 * BLOCK_SIZE)) *
                            cos((2 * y + 1) * v * PI / (2 * BLOCK_SIZE))
                    }
                }
                result[x][y] = (0.25 * sum).toFloat()
            }
        }
        return result
    }

    fun computeContentHash(bitmap: Bitmap): String {
        val pixels = IntArray(bitmap.width * bitmap.height)
        bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
        val bytes = pixels.flatMap { pixel ->
            listOf(
                (pixel shr 16 and 0xFF).toByte(),
                (pixel shr 8 and 0xFF).toByte(),
                (pixel and 0xFF).toByte()
            )
        }.toByteArray()
        return computeHash(bytes)
    }

    private fun computeHash(data: String): String = computeHash(data.toByteArray())

    private fun computeHash(data: ByteArray): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val hashBytes = digest.digest(data)
        return hashBytes.joinToString("") { "%02x".format(it) }
    }

    private fun payloadToBits(payload: WatermarkPayload): BooleanArray {
        val bytes = ByteArray(PAYLOAD_BYTES)
        bytes[0] = MAGIC_0
        bytes[1] = MAGIC_1
        bytes[2] = PAYLOAD_VERSION
        bytes[3] = payload.mode.ordinal.toByte()
        write48Bit(bytes, offset = 4, value = payload.captureTimestamp)
        writeToken(bytes, offset = 10, length = 8, seed = payload.c2paManifestId)
        writeToken(bytes, offset = 18, length = 6, seed = payload.ownerHash.ifBlank { payload.deviceId })
        writeToken(bytes, offset = 24, length = 6, seed = payload.deviceId)
        val checksum = crc16(bytes, endExclusive = 30)
        bytes[30] = ((checksum shr 8) and 0xFF).toByte()
        bytes[31] = (checksum and 0xFF).toByte()
        return bytesToBits(bytes)
    }

    private fun bitsToInspection(bits: BooleanArray): WatermarkInspection? {
        if (bits.size < PAYLOAD_BITS) return null

        val bytes = bitsToBytes(bits.copyOf(PAYLOAD_BITS))
        if (bytes[0] != MAGIC_0 || bytes[1] != MAGIC_1 || bytes[2] != PAYLOAD_VERSION) {
            return null
        }

        val expectedChecksum = ((bytes[30].toInt() and 0xFF) shl 8) or (bytes[31].toInt() and 0xFF)
        val actualChecksum = crc16(bytes, endExclusive = 30)
        if (expectedChecksum != actualChecksum) {
            return null
        }

        val modeOrdinal = bytes[3].toInt() and 0xFF
        val mode = WatermarkMode.entries.getOrElse(modeOrdinal) { WatermarkMode.INVISIBLE }

        return WatermarkInspection(
            mode = mode,
            captureTimestamp = read48Bit(bytes, offset = 4),
            manifestToken = bytes.sliceArray(10 until 18).toHex(),
            ownerToken = bytes.sliceArray(18 until 24).toHex(),
            deviceToken = bytes.sliceArray(24 until 30).toHex(),
            checksumValid = true
        )
    }

    private fun write48Bit(bytes: ByteArray, offset: Int, value: Long) {
        for (index in 0 until 6) {
            val shift = (5 - index) * 8
            bytes[offset + index] = ((value shr shift) and 0xFF).toByte()
        }
    }

    private fun read48Bit(bytes: ByteArray, offset: Int): Long {
        var result = 0L
        for (index in 0 until 6) {
            result = (result shl 8) or (bytes[offset + index].toLong() and 0xFF)
        }
        return result
    }

    private fun writeToken(bytes: ByteArray, offset: Int, length: Int, seed: String) {
        val digest = MessageDigest.getInstance("SHA-256")
            .digest(seed.ifBlank { "deepshield" }.toByteArray(Charsets.UTF_8))
        for (index in 0 until length) {
            bytes[offset + index] = digest[index]
        }
    }

    private fun tokenHex(seed: String, byteLength: Int): String {
        return MessageDigest.getInstance("SHA-256")
            .digest(seed.toByteArray(Charsets.UTF_8))
            .copyOf(byteLength)
            .toHex()
    }

    private fun bytesToBits(bytes: ByteArray): BooleanArray {
        val bits = BooleanArray(bytes.size * 8)
        for (index in bytes.indices) {
            for (bit in 0..7) {
                bits[index * 8 + bit] = ((bytes[index].toInt() shr (7 - bit)) and 1) == 1
            }
        }
        return bits
    }

    private fun bitsToBytes(bits: BooleanArray): ByteArray {
        val bytes = ByteArray(bits.size / 8)
        for (index in bytes.indices) {
            var value = 0
            for (bit in 0..7) {
                if (bits[index * 8 + bit]) {
                    value = value or (1 shl (7 - bit))
                }
            }
            bytes[index] = value.toByte()
        }
        return bytes
    }

    private fun crc16(bytes: ByteArray, endExclusive: Int): Int {
        var crc = 0xFFFF
        for (index in 0 until endExclusive) {
            crc = crc xor ((bytes[index].toInt() and 0xFF) shl 8)
            repeat(8) {
                crc = if ((crc and 0x8000) != 0) {
                    (crc shl 1) xor 0x1021
                } else {
                    crc shl 1
                }
                crc = crc and 0xFFFF
            }
        }
        return crc and 0xFFFF
    }

    private fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it) }
}
