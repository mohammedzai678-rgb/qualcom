package com.deepshield.ai.ui.screens.privacy

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Code
import androidx.compose.material.icons.rounded.CreditCard
import androidx.compose.material.icons.rounded.Face
import androidx.compose.material.icons.rounded.Phone
import androidx.compose.material.icons.rounded.QrCode
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.exifinterface.media.ExifInterface
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.deepshield.ai.domain.model.RiskSeverity
import com.deepshield.ai.ui.components.AuthenticityGauge
import com.deepshield.ai.ui.components.ConfidenceBadge
import com.deepshield.ai.ui.components.FileUploadZone
import com.deepshield.ai.ui.components.GlassCard
import com.deepshield.ai.ui.components.GradientGlassCard
import com.deepshield.ai.ui.components.NeonButton
import com.deepshield.ai.ui.components.StatusChip
import com.deepshield.ai.ui.components.Verdict
import com.deepshield.ai.ui.theme.BadgeShape
import com.deepshield.ai.ui.theme.CodeText
import com.deepshield.ai.ui.theme.CyberBlack
import com.deepshield.ai.ui.theme.DangerRed
import com.deepshield.ai.ui.theme.DeepPurple
import com.deepshield.ai.ui.theme.GlassBorder
import com.deepshield.ai.ui.theme.NeonGreen
import com.deepshield.ai.ui.theme.PillShape
import com.deepshield.ai.ui.theme.TextPrimary
import com.deepshield.ai.ui.theme.TextSecondary
import com.deepshield.ai.ui.theme.TextTertiary
import com.deepshield.ai.ui.theme.WarningOrange
import com.deepshield.ai.util.readByteSample
import com.deepshield.ai.util.readFileInfo

@Composable
fun PrivacyScreen(
    viewModel: PrivacyViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current

    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        val fileInfo = context.contentResolver.readFileInfo(uri)
        val sample = context.contentResolver.readByteSample(uri) ?: ByteArray(0)
        val metadata = runCatching {
            context.contentResolver.openInputStream(uri)?.use { inputStream ->
                extractExifMetadata(ExifInterface(inputStream))
            } ?: emptyMap()
        }.getOrDefault(emptyMap())

        viewModel.analyzeSelection(
            fileName = fileInfo.name,
            fileSize = fileInfo.size,
            metadata = metadata,
            sample = sample
        )
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(CyberBlack),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Column(modifier = Modifier.padding(top = 8.dp)) {
                Text(
                    text = "Privacy Auditor",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )
                Text(
                    text = "Local EXIF audit and lightweight content heuristics",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextTertiary
                )
            }
        }

        item {
            FileUploadZone(
                onTap = { imagePickerLauncher.launch("image/*") },
                title = "Select Media to Audit",
                subtitle = "Inspect an image without sending it to the cloud",
                icon = Icons.Rounded.Security,
                accentColor = DeepPurple
            )
        }

        item {
            GlassCard(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = state.selectedFileName ?: "No image selected yet",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = TextPrimary
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = state.statusMessage,
                    style = MaterialTheme.typography.bodySmall,
                    color = TextTertiary
                )
            }
        }

        item {
            GradientGlassCard(
                modifier = Modifier.fillMaxWidth(),
                gradientColors = if (state.privacyScore >= 70f) {
                    listOf(NeonGreen, DeepPurple)
                } else {
                    listOf(DangerRed, WarningOrange)
                }
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "Privacy Safety Score",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = TextSecondary
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    AuthenticityGauge(score = state.privacyScore, size = 120.dp, label = "Safety")
                    Spacer(modifier = Modifier.height(8.dp))
                    ConfidenceBadge(
                        verdict = when {
                            state.privacyScore >= 75f -> Verdict.AUTHENTIC
                            state.privacyScore >= 50f -> Verdict.SUSPICIOUS
                            else -> Verdict.DEEPFAKE
                        }
                    )
                }
            }
        }

        item {
            GlassCard(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "EXIF Metadata",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = TextPrimary
                    )
                    StatusChip(
                        text = when {
                            state.metadataWasStripped -> "Cleared"
                            state.exifFields.isEmpty() -> "No EXIF found"
                            else -> "${state.exifFields.size} fields found"
                        },
                        color = if (state.exifFields.isEmpty()) NeonGreen else WarningOrange,
                        isActive = state.exifFields.isNotEmpty()
                    )
                }
                Spacer(modifier = Modifier.height(12.dp))

                if (state.exifFields.isEmpty()) {
                    Text(
                        text = "Nothing identifying was recovered from the image metadata.",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextTertiary
                    )
                } else {
                    state.exifFields.forEach { field ->
                        ExifRow(
                            label = field.label,
                            value = field.value,
                            color = severityColor(field.severity),
                            risk = field.severity.name
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))
                NeonButton(
                    text = if (state.exifFields.isNotEmpty()) "Strip Metadata in Session" else "Metadata Already Clear",
                    onClick = viewModel::stripMetadata,
                    enabled = state.exifFields.isNotEmpty(),
                    modifier = Modifier.fillMaxWidth(),
                    gradientColors = listOf(DangerRed, WarningOrange)
                )
            }
        }

        item {
            GlassCard(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = "Sensitive Content Heuristics",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = TextPrimary
                )
                Spacer(modifier = Modifier.height(12.dp))

                if (state.sensitiveFindings.isEmpty()) {
                    Text(
                        text = "Run an audit to populate local content checks.",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextTertiary
                    )
                } else {
                    state.sensitiveFindings.forEach { finding ->
                        SensitiveItem(
                            icon = iconFor(finding.title),
                            title = finding.title,
                            desc = finding.description,
                            color = severityColor(finding.severity),
                            status = finding.status
                        )
                    }
                }
            }
        }

        item { Spacer(modifier = Modifier.height(80.dp)) }
    }
}

@Composable
private fun ExifRow(label: String, value: String, color: Color, risk: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(6.dp)
                .clip(PillShape)
                .background(color)
        )
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = TextSecondary,
            modifier = Modifier.weight(1f)
        )
        Text(
            text = value,
            style = CodeText,
            color = TextPrimary,
            modifier = Modifier.weight(1.5f)
        )
        Box(
            modifier = Modifier
                .clip(BadgeShape)
                .background(color.copy(alpha = 0.12f), BadgeShape)
                .border(1.dp, color.copy(alpha = 0.3f), BadgeShape)
                .padding(horizontal = 6.dp, vertical = 2.dp)
        ) {
            Text(
                text = risk,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = color
            )
        }
    }
}

@Composable
private fun SensitiveItem(icon: ImageVector, title: String, desc: String, color: Color, status: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        androidx.compose.material3.Icon(
            imageVector = icon,
            contentDescription = null,
            tint = color,
            modifier = Modifier.size(20.dp)
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.Medium,
                color = TextPrimary
            )
            Text(
                text = desc,
                style = MaterialTheme.typography.labelSmall,
                color = TextTertiary
            )
        }
        Text(
            text = status,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = color
        )
    }
}

private fun extractExifMetadata(exif: ExifInterface): Map<String, String> = linkedMapOf(
    "GPS Coordinates" to listOfNotNull(
        exif.getAttribute(ExifInterface.TAG_GPS_LATITUDE),
        exif.getAttribute(ExifInterface.TAG_GPS_LONGITUDE)
    ).takeIf { it.isNotEmpty() }?.joinToString(", ").orEmpty(),
    "Device Serial" to exif.getAttribute(ExifInterface.TAG_BODY_SERIAL_NUMBER).orEmpty(),
    "Device Model" to exif.getAttribute(ExifInterface.TAG_MODEL).orEmpty(),
    "Capture Time" to exif.getAttribute(ExifInterface.TAG_DATETIME_ORIGINAL).orEmpty(),
    "Software" to exif.getAttribute(ExifInterface.TAG_SOFTWARE).orEmpty(),
    "Thumbnail" to if (exif.hasThumbnail()) "Embedded preview present" else "",
    "Color Space" to exif.getAttribute(ExifInterface.TAG_COLOR_SPACE).orEmpty()
).filterValues { it.isNotBlank() }

private fun severityColor(severity: RiskSeverity): Color = when (severity) {
    RiskSeverity.HIGH -> DangerRed
    RiskSeverity.MEDIUM -> WarningOrange
    RiskSeverity.LOW -> NeonGreen
}

private fun iconFor(title: String): ImageVector = when (title) {
    "Faces Detected" -> Icons.Rounded.Face
    "ID Documents" -> Icons.Rounded.CreditCard
    "Phone Numbers" -> Icons.Rounded.Phone
    "QR Codes" -> Icons.Rounded.QrCode
    else -> Icons.Rounded.Code
}
