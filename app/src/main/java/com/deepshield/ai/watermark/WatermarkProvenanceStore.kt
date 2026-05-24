package com.deepshield.ai.watermark

import android.content.Context
import com.deepshield.ai.domain.model.WatermarkMode
import com.deepshield.ai.domain.model.WatermarkPayload
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import org.json.JSONObject

@Singleton
class WatermarkProvenanceStore @Inject constructor(
    @ApplicationContext private val context: Context
) {

    companion object {
        private const val PREFS_NAME = "deepshield_watermark_provenance"
    }

    data class ProvenanceRecord(
        val manifestToken: String,
        val manifestId: String,
        val ownerHash: String,
        val deviceId: String,
        val mode: WatermarkMode,
        val captureTimestamp: Long,
        val sourceContentHash: String,
        val watermarkedContentHash: String,
        val signature: String
    ) {
        fun toPayload(): WatermarkPayload = WatermarkPayload(
            deviceId = deviceId,
            ownerHash = ownerHash,
            captureTimestamp = captureTimestamp,
            c2paManifestId = manifestId,
            contentHash = watermarkedContentHash,
            signature = signature,
            mode = mode,
            integrityScore = 100f
        )

        fun toJson(): String = JSONObject().apply {
            put("manifestToken", manifestToken)
            put("manifestId", manifestId)
            put("ownerHash", ownerHash)
            put("deviceId", deviceId)
            put("mode", mode.name)
            put("captureTimestamp", captureTimestamp)
            put("sourceContentHash", sourceContentHash)
            put("watermarkedContentHash", watermarkedContentHash)
            put("signature", signature)
        }.toString()
    }

    fun save(record: ProvenanceRecord) {
        prefs().edit().putString(record.manifestToken, record.toJson()).apply()
    }

    fun findByManifestToken(manifestToken: String): ProvenanceRecord? {
        val json = prefs().getString(manifestToken, null) ?: return null
        return try {
            jsonToRecord(JSONObject(json))
        } catch (_: Exception) {
            null
        }
    }

    fun findByWatermarkedContentHash(contentHash: String): ProvenanceRecord? {
        if (contentHash.isBlank()) return null

        return prefs().all.values
            .asSequence()
            .mapNotNull { value -> (value as? String)?.takeIf { it.isNotBlank() } }
            .mapNotNull { json ->
                try {
                    jsonToRecord(JSONObject(json))
                } catch (_: Exception) {
                    null
                }
            }
            .firstOrNull { record ->
                record.watermarkedContentHash.equals(contentHash, ignoreCase = true)
            }
    }

    private fun prefs() = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private fun jsonToRecord(json: JSONObject): ProvenanceRecord = ProvenanceRecord(
        manifestToken = json.optString("manifestToken"),
        manifestId = json.optString("manifestId"),
        ownerHash = json.optString("ownerHash"),
        deviceId = json.optString("deviceId"),
        mode = json.optString("mode")
            .takeIf { it.isNotBlank() }
            ?.let(WatermarkMode::valueOf)
            ?: WatermarkMode.INVISIBLE,
        captureTimestamp = json.optLong("captureTimestamp"),
        sourceContentHash = json.optString("sourceContentHash"),
        watermarkedContentHash = json.optString("watermarkedContentHash"),
        signature = json.optString("signature")
    )
}
