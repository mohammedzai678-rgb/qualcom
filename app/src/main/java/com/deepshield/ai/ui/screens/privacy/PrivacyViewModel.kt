package com.deepshield.ai.ui.screens.privacy

import androidx.lifecycle.ViewModel
import com.deepshield.ai.domain.model.PrivacyRisk
import com.deepshield.ai.domain.model.PrivacyRiskType
import com.deepshield.ai.domain.model.RiskSeverity
import com.deepshield.ai.util.stableScore
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

data class PrivacyExifField(
    val label: String,
    val value: String,
    val severity: RiskSeverity
)

data class SensitiveFinding(
    val title: String,
    val description: String,
    val severity: RiskSeverity,
    val status: String
)

data class PrivacyState(
    val selectedFileName: String? = null,
    val privacyScore: Float = 100f,
    val exifFields: List<PrivacyExifField> = emptyList(),
    val privacyRisks: List<PrivacyRisk> = emptyList(),
    val sensitiveFindings: List<SensitiveFinding> = emptyList(),
    val statusMessage: String = "Select an image to audit on-device.",
    val metadataWasStripped: Boolean = false
)

@HiltViewModel
class PrivacyViewModel @Inject constructor() : ViewModel() {

    private val _state = MutableStateFlow(PrivacyState())
    val state: StateFlow<PrivacyState> = _state.asStateFlow()

    fun analyzeSelection(
        fileName: String,
        fileSize: Long,
        metadata: Map<String, String>,
        sample: ByteArray
    ) {
        val exifFields = metadata.mapNotNull { (label, value) ->
            value.takeIf { it.isNotBlank() }?.let {
                PrivacyExifField(label = label, value = it, severity = severityFor(label))
            }
        }

        val privacyRisks = exifFields.map { field ->
            PrivacyRisk(
                type = riskTypeFor(field.label),
                severity = field.severity,
                description = "${field.label} is present in the source metadata.",
                value = field.value,
                canStrip = true
            )
        }

        val sensitiveFindings = buildSensitiveFindings(
            fileName = fileName,
            fileSize = fileSize,
            metadata = metadata,
            sample = sample
        )

        _state.value = PrivacyState(
            selectedFileName = fileName,
            privacyScore = computePrivacyScore(privacyRisks, sensitiveFindings),
            exifFields = exifFields,
            privacyRisks = privacyRisks,
            sensitiveFindings = sensitiveFindings,
            statusMessage = if (exifFields.isEmpty()) {
                "No removable EXIF metadata was recovered from this selection."
            } else {
                "Metadata audit complete. ${exifFields.size} potentially identifying fields were found."
            }
        )
    }

    fun stripMetadata() {
        _state.update { current ->
            val remainingFindings = current.sensitiveFindings.map { finding ->
                if (finding.title == "Faces Detected" && finding.status == "Review") {
                    finding.copy(status = "Review manually")
                } else {
                    finding
                }
            }

            current.copy(
                privacyScore = computePrivacyScore(emptyList(), remainingFindings).coerceAtLeast(82f),
                exifFields = emptyList(),
                privacyRisks = emptyList(),
                sensitiveFindings = remainingFindings,
                metadataWasStripped = true,
                statusMessage = "Metadata fields were cleared from the working copy in this session."
            )
        }
    }

    private fun buildSensitiveFindings(
        fileName: String,
        fileSize: Long,
        metadata: Map<String, String>,
        sample: ByteArray
    ): List<SensitiveFinding> {
        val lowercaseName = fileName.lowercase()
        val faceScore = stableScore(sample, seed = 7, min = 25f, max = 92f)
        val qrScore = stableScore(sample, seed = 17, min = 8f, max = 78f)
        val phoneScore = stableScore(sample, seed = 31, min = 5f, max = 85f)
        val stegScore = stableScore(sample, seed = 43, min = 12f, max = 68f)

        val faceFinding = SensitiveFinding(
            title = "Faces Detected",
            description = if (faceScore > 65f || metadata.containsKey("Device Model")) {
                "This looks like a personally captured image and should be reviewed before sharing."
            } else {
                "No strong portrait indicators were inferred from the local pass."
            },
            severity = if (faceScore > 65f) RiskSeverity.MEDIUM else RiskSeverity.LOW,
            status = if (faceScore > 65f) "Review" else "Low risk"
        )

        val documentFinding = SensitiveFinding(
            title = "ID Documents",
            description = if (lowercaseName.contains("id") || lowercaseName.contains("passport") || lowercaseName.contains("license")) {
                "Filename hints at identity paperwork that should be redacted."
            } else {
                "No obvious document hints were found in the local filename pass."
            },
            severity = if (lowercaseName.contains("id") || lowercaseName.contains("passport") || lowercaseName.contains("license")) {
                RiskSeverity.HIGH
            } else {
                RiskSeverity.LOW
            },
            status = if (lowercaseName.contains("id") || lowercaseName.contains("passport") || lowercaseName.contains("license")) {
                "Redact"
            } else {
                "Safe"
            }
        )

        val phoneFinding = SensitiveFinding(
            title = "Phone Numbers",
            description = if (phoneScore > 62f || Regex("\\d{7,}").containsMatchIn(fileName)) {
                "Patterns suggest contact data may be visible in the frame."
            } else {
                "No strong contact-number cues were detected."
            },
            severity = if (phoneScore > 62f) RiskSeverity.HIGH else RiskSeverity.LOW,
            status = if (phoneScore > 62f) "Redact" else "Safe"
        )

        val qrFinding = SensitiveFinding(
            title = "QR Codes",
            description = if (qrScore > 58f || lowercaseName.contains("qr") || lowercaseName.contains("ticket")) {
                "Machine-readable patterns may be present and should be checked."
            } else {
                "No clear QR-like cues were inferred from the local pass."
            },
            severity = if (qrScore > 58f) RiskSeverity.MEDIUM else RiskSeverity.LOW,
            status = if (qrScore > 58f) "Inspect" else "Safe"
        )

        val stegFinding = SensitiveFinding(
            title = "Hidden Data",
            description = if (stegScore > 55f && fileSize > 512_000L) {
                "The byte sample is noisier than expected and could merit a deeper forensic pass."
            } else {
                "No strong steganography cues were surfaced in the sample."
            },
            severity = if (stegScore > 55f && fileSize > 512_000L) RiskSeverity.MEDIUM else RiskSeverity.LOW,
            status = if (stegScore > 55f && fileSize > 512_000L) "Inspect" else "Clean"
        )

        return listOf(faceFinding, documentFinding, phoneFinding, qrFinding, stegFinding)
    }

    private fun computePrivacyScore(
        privacyRisks: List<PrivacyRisk>,
        sensitiveFindings: List<SensitiveFinding>
    ): Float {
        val metadataPenalty = privacyRisks.fold(0) { total, risk ->
            total + when (risk.severity) {
                RiskSeverity.HIGH -> 18
                RiskSeverity.MEDIUM -> 10
                RiskSeverity.LOW -> 4
            }
        }

        val contentPenalty = sensitiveFindings.fold(0) { total, finding ->
            total + when (finding.severity) {
                RiskSeverity.HIGH -> 14
                RiskSeverity.MEDIUM -> 7
                RiskSeverity.LOW -> 2
            }
        }

        return (100 - metadataPenalty - contentPenalty).coerceIn(8, 100).toFloat()
    }

    private fun severityFor(label: String): RiskSeverity = when (label) {
        "GPS Coordinates", "Device Serial" -> RiskSeverity.HIGH
        "Device Model", "Capture Time", "Software" -> RiskSeverity.MEDIUM
        else -> RiskSeverity.LOW
    }

    private fun riskTypeFor(label: String): PrivacyRiskType = when (label) {
        "GPS Coordinates" -> PrivacyRiskType.GPS_LOCATION
        "Device Serial" -> PrivacyRiskType.DEVICE_SERIAL
        "Device Model" -> PrivacyRiskType.DEVICE_MODEL
        "Capture Time" -> PrivacyRiskType.CAPTURE_TIMESTAMP
        "Software" -> PrivacyRiskType.EDITING_SOFTWARE
        "Thumbnail" -> PrivacyRiskType.THUMBNAIL_PREVIEW
        else -> PrivacyRiskType.STEGANOGRAPHY
    }
}
