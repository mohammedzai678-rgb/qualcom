package com.deepshield.ai.domain.model

import java.util.UUID

/**
 * Core detection result from any scan operation.
 */
data class ScanResult(
    val id: String = UUID.randomUUID().toString(),
    val mediaType: MediaType = MediaType.IMAGE,
    val authenticityScore: Float = 0f,       // 0-100
    val confidenceScore: Float = 0f,         // 0-100
    val verdict: ScanVerdict = ScanVerdict.UNKNOWN,
    val modelScores: Map<String, Float> = emptyMap(), // per-model scores
    val detectedArtifacts: List<DetectedArtifact> = emptyList(),
    val aiAttribution: AIAttribution? = null,
    val processingTimeMs: Long = 0,
    val timestamp: Long = System.currentTimeMillis(),
    val fileName: String = "",
    val fileSize: Long = 0,
    val previewImageBytes: ByteArray? = null,
    val heatmapData: Array<FloatArray>? = null,
    val frequencyAnomalyScore: Float = 0f,
    val metadataIntegrity: Float = 100f
)

enum class MediaType { IMAGE, VIDEO, AUDIO, DOCUMENT }

enum class ScanVerdict {
    AUTHENTIC, SUSPICIOUS, DEEPFAKE, UNKNOWN;

    fun displayName(): String = when (this) {
        AUTHENTIC -> "Authentic"
        SUSPICIOUS -> "Suspicious"
        DEEPFAKE -> "Deepfake Detected"
        UNKNOWN -> "Unverified"
    }
}

data class DetectedArtifact(
    val type: ArtifactType,
    val region: BoundingBox? = null,
    val confidence: Float = 0f,
    val description: String = ""
)

enum class ArtifactType {
    FACE_SWAP, GAN_ARTIFACT, FREQUENCY_ANOMALY, METADATA_MISMATCH,
    LIP_SYNC_MISMATCH, TEMPORAL_INCONSISTENCY, VOICE_CLONE,
    SYNTHETIC_SPEECH, IDENTITY_DRIFT, TEXTURE_ANOMALY
}

data class BoundingBox(
    val x: Float, val y: Float,
    val width: Float, val height: Float
)

data class AIAttribution(
    val topModels: List<ModelCandidate> = emptyList(),
    val architectureType: String = "",  // GAN, Diffusion, Flow-based, Autoregressive
    val confidence: Float = 0f
)

data class ModelCandidate(
    val name: String,          // e.g., "Stable Diffusion XL", "Midjourney v6"
    val confidence: Float,     // 0-100
    val architecture: String   // GAN, Diffusion, etc.
)

/**
 * Media item for tracking scanned files.
 */
data class MediaItem(
    val id: String = UUID.randomUUID().toString(),
    val uri: String = "",
    val fileName: String = "",
    val fileSize: Long = 0,
    val mediaType: MediaType = MediaType.IMAGE,
    val mimeType: String = "",
    val timestamp: Long = System.currentTimeMillis(),
    val scanResult: ScanResult? = null,
    val isWatermarked: Boolean = false
)

/**
 * Threat alert model.
 */
data class ThreatAlert(
    val id: String = UUID.randomUUID().toString(),
    val title: String = "",
    val description: String = "",
    val severity: ThreatSeverity = ThreatSeverity.LOW,
    val category: String = "",
    val timestamp: Long = System.currentTimeMillis(),
    val relatedScanId: String? = null
)

enum class ThreatSeverity { LOW, MEDIUM, HIGH, CRITICAL }

/**
 * Watermark payload embedded in media.
 */
data class WatermarkPayload(
    val deviceId: String = "",
    val ownerHash: String = "",
    val captureTimestamp: Long = System.currentTimeMillis(),
    val c2paManifestId: String = UUID.randomUUID().toString(),
    val contentHash: String = "",
    val signature: String = "",
    val mode: WatermarkMode = WatermarkMode.INVISIBLE,
    val integrityScore: Float = 100f  // 100 = untampered
)

enum class WatermarkMode {
    INVISIBLE, ROBUST, FRAGILE;

    fun displayName(): String = when (this) {
        INVISIBLE -> "Invisible (Recommended)"
        ROBUST -> "Robust"
        FRAGILE -> "Fragile (Tamper-Evident)"
    }
}

/**
 * Forensic report data.
 */
data class ForensicReport(
    val id: String = UUID.randomUUID().toString(),
    val scanResult: ScanResult,
    val watermarkStatus: WatermarkPayload? = null,
    val privacyRisks: List<PrivacyRisk> = emptyList(),
    val chainOfCustody: List<CustodyEntry> = emptyList(),
    val generatedAt: Long = System.currentTimeMillis(),
    val exportPath: String? = null
)

/**
 * NPU performance metrics snapshot.
 */
data class NpuMetrics(
    val fps: Float = 0f,
    val inferenceLatencyMs: Float = 0f,
    val cpuUsagePercent: Float = 0f,
    val gpuUsagePercent: Float = 0f,
    val npuUtilizationPercent: Float = 0f,
    val ramUsageMb: Float = 0f,
    val thermalCelsius: Float = 0f,
    val batteryDrainMw: Float = 0f,
    val activeDelegate: String = "CPU",  // CPU, GPU, NPU, HTP
    val quantizationMode: String = "INT8",
    val timestamp: Long = System.currentTimeMillis()
)

/**
 * Privacy risk assessment for a media file.
 */
data class PrivacyRisk(
    val type: PrivacyRiskType,
    val severity: RiskSeverity,
    val description: String = "",
    val value: String? = null,  // e.g., GPS coordinates, device serial
    val canStrip: Boolean = true
)

enum class PrivacyRiskType {
    GPS_LOCATION, DEVICE_SERIAL, DEVICE_MODEL, CAPTURE_TIMESTAMP,
    EDITING_SOFTWARE, THUMBNAIL_PREVIEW, FACE_DETECTED, ID_DOCUMENT,
    PHONE_NUMBER, QR_CODE, STEGANOGRAPHY
}

enum class RiskSeverity { LOW, MEDIUM, HIGH }

/**
 * Chain of custody entry for C2PA compliance.
 */
data class ChainOfCustody(
    val mediaId: String = "",
    val entries: List<CustodyEntry> = emptyList()
)

data class CustodyEntry(
    val action: CustodyAction,
    val timestamp: Long = System.currentTimeMillis(),
    val deviceId: String = "",
    val softwareName: String = "DeepShield AI",
    val softwareVersion: String = "1.0.0",
    val contentHash: String = "",
    val signature: String = ""
)

enum class CustodyAction {
    CREATED, CAPTURED, WATERMARKED, VERIFIED, EDITED, SHARED, EXPORTED
}
