package com.deepshield.ai.ui.screens.watermark

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.CameraAlt
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.FileDownload
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.VerifiedUser
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.deepshield.ai.domain.model.CustodyAction
import com.deepshield.ai.domain.model.CustodyEntry
import com.deepshield.ai.domain.model.WatermarkMode
import com.deepshield.ai.domain.model.WatermarkPayload
import com.deepshield.ai.ui.components.AuthenticityGauge
import com.deepshield.ai.ui.components.ConfidenceBadge
import com.deepshield.ai.ui.components.FileUploadZone
import com.deepshield.ai.ui.components.GlassCard
import com.deepshield.ai.ui.components.GradientGlassCard
import com.deepshield.ai.ui.components.NeonButton
import com.deepshield.ai.ui.components.Verdict
import com.deepshield.ai.ui.theme.CardShape
import com.deepshield.ai.ui.theme.ChipShape
import com.deepshield.ai.ui.theme.CodeText
import com.deepshield.ai.ui.theme.CyberBlack
import com.deepshield.ai.ui.theme.DangerRed
import com.deepshield.ai.ui.theme.DeepPurple
import com.deepshield.ai.ui.theme.ElectricBlue
import com.deepshield.ai.ui.theme.GlassBorder
import com.deepshield.ai.ui.theme.NeonCyan
import com.deepshield.ai.ui.theme.NeonCyanDark
import com.deepshield.ai.ui.theme.NeonGreen
import com.deepshield.ai.ui.theme.PillShape
import com.deepshield.ai.ui.theme.TextPrimary
import com.deepshield.ai.ui.theme.TextTertiary
import com.deepshield.ai.ui.theme.WarningOrange
import com.deepshield.ai.util.decodeBitmap
import com.deepshield.ai.util.readFileInfo
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun WatermarkScreen(
    viewModel: WatermarkViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val tabs = listOf("Embed", "Verify", "Chain of Custody")

    val embedPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        val fileInfo = context.contentResolver.readFileInfo(uri)
        val bitmap = context.contentResolver.decodeBitmap(uri)
        if (bitmap != null) {
            viewModel.embedWatermark(bitmap, fileInfo.name)
        }
    }

    val verifyPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        val fileInfo = context.contentResolver.readFileInfo(uri)
        val bitmap = context.contentResolver.decodeBitmap(uri)
        if (bitmap != null) {
            viewModel.verifyWatermark(bitmap, fileInfo.name)
        }
    }

    // Camera capture launcher for Embed
    val embedCameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicturePreview()
    ) { bitmap ->
        if (bitmap != null) {
            viewModel.embedWatermark(bitmap, "Camera_Capture.jpg")
        }
    }

    // Camera capture launcher for Verify
    val verifyCameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicturePreview()
    ) { bitmap ->
        if (bitmap != null) {
            viewModel.verifyWatermark(bitmap, "Camera_Capture.jpg")
        }
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
                    text = "Authenticity Shield",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )
                Text(
                    text = "Cryptographic watermarking and local provenance checks",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextTertiary
                )
            }
        }

        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                tabs.forEachIndexed { index, tab ->
                    val isSelected = state.selectedTab == index
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(ChipShape)
                            .background(
                                if (isSelected) NeonCyan.copy(alpha = 0.12f) else com.deepshield.ai.ui.theme.GlassWhite,
                                ChipShape
                            )
                            .border(
                                1.dp,
                                if (isSelected) NeonCyan.copy(alpha = 0.5f) else GlassBorder,
                                ChipShape
                            )
                            .clickable { viewModel.selectTab(index) }
                            .padding(vertical = 10.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = tab,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                            color = if (isSelected) NeonCyan else TextTertiary
                        )
                    }
                }
            }
        }

        item {
            GlassCard(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = state.statusMessage,
                    style = MaterialTheme.typography.bodySmall,
                    color = TextTertiary
                )
                if (state.isProcessing) {
                    Spacer(modifier = Modifier.height(10.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            strokeWidth = 2.dp,
                            color = DeepPurple
                        )
                        Text(
                            text = "This never leaves the device.",
                            style = MaterialTheme.typography.labelSmall,
                            color = TextPrimary
                        )
                    }
                }
            }
        }

        when (state.selectedTab) {
            0 -> {
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Box(modifier = Modifier.weight(1f)) {
                            FileUploadZone(
                                onTap = { 
                                    embedPickerLauncher.launch(
                                        androidx.activity.result.PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                    ) 
                                },
                                title = "Gallery",
                                subtitle = "Select image to watermark",
                                accentColor = DeepPurple
                            )
                        }
                        Box(modifier = Modifier.weight(1f)) {
                            FileUploadZone(
                                onTap = { embedCameraLauncher.launch(null) },
                                title = "Camera",
                                subtitle = "Take photo to watermark",
                                accentColor = NeonCyan
                            )
                        }
                    }
                }
                item {
                    GlassCard(modifier = Modifier.fillMaxWidth()) {
                        Text(
                            text = "Watermark Mode",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = TextPrimary
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        WatermarkMode.entries.forEach { mode ->
                            val isSelected = state.selectedMode == mode
                            val (color, desc) = when (mode) {
                                WatermarkMode.INVISIBLE -> NeonGreen to "Balanced for invisible tagging and routine verification."
                                WatermarkMode.ROBUST -> NeonCyan to "Prioritizes survival through compression and sharing."
                                WatermarkMode.FRAGILE -> WarningOrange to "Best for tamper-evident review workflows."
                            }
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(ChipShape)
                                    .background(
                                        if (isSelected) color.copy(alpha = 0.08f) else Color.Transparent,
                                        ChipShape
                                    )
                                    .border(
                                        1.dp,
                                        if (isSelected) color.copy(alpha = 0.3f) else GlassBorder,
                                        ChipShape
                                    )
                                    .clickable { viewModel.selectMode(mode) }
                                    .padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                RadioButton(
                                    selected = isSelected,
                                    onClick = { viewModel.selectMode(mode) },
                                    colors = RadioButtonDefaults.colors(selectedColor = color)
                                )
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = mode.displayName(),
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Medium,
                                        color = TextPrimary
                                    )
                                    Text(
                                        text = desc,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = TextTertiary
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                        }
                    }
                }
                item {
                    GlassCard(modifier = Modifier.fillMaxWidth()) {
                        Text(
                            text = "Embedded Payload",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = TextPrimary
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        state.lastPayload?.let { payload ->
                            PayloadRow("Source", state.selectedEmbedFileName ?: "Session image")
                            PayloadRow("Device ID", payload.deviceId)
                            PayloadRow("Owner Hash", payload.ownerHash.ifBlank { state.publicKeyFingerprint })
                            PayloadRow("Timestamp", formatTimestamp(payload.captureTimestamp))
                            PayloadRow("C2PA UUID", payload.c2paManifestId.take(18))
                            PayloadRow("Content Hash", payload.contentHash.take(18))
                            PayloadRow("Signature", payload.signature.take(18))
                            PayloadRow("Mode", payload.mode.name)
                        } ?: Text(
                            text = "Pick an image to generate a signed watermark payload.",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextTertiary
                        )
                    }
                }
                // --- Save & Share buttons (appear after watermarking) ---
                if (state.hasWatermarkedImage) {
                    item {
                        GlassCard(modifier = Modifier.fillMaxWidth()) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Watermarked Image Ready",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.SemiBold,
                                    color = NeonGreen
                                )
                                if (state.isSaved) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        androidx.compose.material3.Icon(
                                            imageVector = Icons.Rounded.CheckCircle,
                                            contentDescription = "Saved",
                                            tint = NeonGreen,
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Text(
                                            text = "Saved",
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = FontWeight.Bold,
                                            color = NeonGreen
                                        )
                                    }
                                }
                            }
                            Spacer(modifier = Modifier.height(12.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                NeonButton(
                                    text = "Save to Gallery",
                                    onClick = { viewModel.saveWatermarkedImage(context) },
                                    enabled = !state.isProcessing && !state.isSaved,
                                    modifier = Modifier.weight(1f),
                                    gradientColors = listOf(NeonGreen, NeonCyan)
                                )
                                NeonButton(
                                    text = "Share",
                                    onClick = {
                                        viewModel.createShareIntent(context)?.let { intent ->
                                            context.startActivity(
                                                Intent.createChooser(intent, "Share Watermarked Image")
                                            )
                                        }
                                    },
                                    enabled = !state.isProcessing,
                                    modifier = Modifier.weight(1f),
                                    gradientColors = listOf(ElectricBlue, DeepPurple)
                                )
                            }
                        }
                    }
                }
                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        NeonButton(
                            text = if (state.selectedEmbedFileName == null) "Gallery" else "Another from Gallery",
                            onClick = { 
                                embedPickerLauncher.launch(
                                    androidx.activity.result.PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                ) 
                            },
                            enabled = !state.isProcessing,
                            modifier = Modifier.weight(1f)
                        )
                        NeonButton(
                            text = "Camera",
                            onClick = { embedCameraLauncher.launch(null) },
                            enabled = !state.isProcessing,
                            modifier = Modifier.weight(1f),
                            gradientColors = listOf(NeonCyan, DeepPurple)
                        )
                    }
                }
            }

            1 -> {
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Box(modifier = Modifier.weight(1f)) {
                            FileUploadZone(
                                onTap = { 
                                    verifyPickerLauncher.launch(
                                        androidx.activity.result.PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                    ) 
                                },
                                title = "Gallery",
                                subtitle = "Select media to verify",
                                accentColor = NeonGreen
                            )
                        }
                        Box(modifier = Modifier.weight(1f)) {
                            FileUploadZone(
                                onTap = { verifyCameraLauncher.launch(null) },
                                title = "Camera",
                                subtitle = "Take photo to verify",
                                accentColor = WarningOrange
                            )
                        }
                    }
                }
                item {
                    state.verificationResult?.let { result ->
                        GradientGlassCard(
                            modifier = Modifier.fillMaxWidth(),
                            gradientColors = if (result.integrityScore >= 80f) {
                                listOf(NeonGreen, NeonCyanDark)
                            } else {
                                listOf(DangerRed, WarningOrange)
                            }
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                AuthenticityGauge(score = result.integrityScore, size = 120.dp, label = "Integrity")
                                Spacer(modifier = Modifier.height(12.dp))
                                ConfidenceBadge(
                                    verdict = if (result.integrityScore >= 80f) {
                                        Verdict.AUTHENTIC
                                    } else {
                                        Verdict.SUSPICIOUS
                                    }
                                )
                                Spacer(modifier = Modifier.height(12.dp))
                                Text(
                                    text = if (result.integrityScore >= 80f) {
                                        "Watermark intact. Provenance matches a trusted local record."
                                    } else {
                                        "Verification completed, but the saved file does not fully match the original trusted export."
                                    },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = if (result.integrityScore >= 80f) NeonGreen else WarningOrange
                                )
                            }
                        }
                    } ?: GlassCard(modifier = Modifier.fillMaxWidth()) {
                        Text(
                            text = "Verify the last embedded image instantly, or reopen any saved DeepShield image to match against its local provenance record.",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextTertiary
                        )
                    }
                }
                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        NeonButton(
                            text = if (state.lastPayload != null) "Verify Last" else "Verify Gallery",
                            onClick = {
                                if (state.lastPayload != null) {
                                    viewModel.verifyLastEmbeddedWatermark()
                                } else {
                                    verifyPickerLauncher.launch(
                                        androidx.activity.result.PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                    )
                                }
                            },
                            enabled = !state.isProcessing,
                            modifier = Modifier.weight(1f),
                            gradientColors = listOf(NeonGreen, NeonCyan)
                        )
                        NeonButton(
                            text = "Verify Camera",
                            onClick = { verifyCameraLauncher.launch(null) },
                            enabled = !state.isProcessing,
                            modifier = Modifier.weight(1f),
                            gradientColors = listOf(WarningOrange, DangerRed)
                        )
                    }
                }
            }

            2 -> {
                if (state.chainOfCustody.isEmpty()) {
                    item {
                        GlassCard(modifier = Modifier.fillMaxWidth()) {
                            Text(
                                text = "Embed or verify a watermark first to build a live chain-of-custody trail.",
                                style = MaterialTheme.typography.bodySmall,
                                color = TextTertiary
                            )
                        }
                    }
                } else {
                    item {
                        GlassCard(modifier = Modifier.fillMaxWidth()) {
                            Text(
                                text = "Media Provenance Chain",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = TextPrimary
                            )
                            Text(
                                text = "Session-signed provenance events",
                                style = MaterialTheme.typography.labelSmall,
                                color = NeonCyan
                            )
                        }
                    }
                    itemsIndexed(state.chainOfCustody) { index, entry ->
                        ChainEntry(entry = entry, isLast = index == state.chainOfCustody.lastIndex)
                    }
                }
            }
        }

        item { Spacer(modifier = Modifier.height(80.dp)) }
    }
}

@Composable
private fun PayloadRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = TextTertiary
        )
        Text(
            text = value,
            style = CodeText,
            color = NeonCyan
        )
    }
}

@Composable
private fun ChainEntry(entry: CustodyEntry, isLast: Boolean) {
    val (icon, color) = when (entry.action) {
        CustodyAction.CAPTURED -> Icons.Rounded.CameraAlt to NeonCyan
        CustodyAction.WATERMARKED -> Icons.Rounded.VerifiedUser to DeepPurple
        CustodyAction.VERIFIED -> Icons.Rounded.CheckCircle to NeonGreen
        CustodyAction.SHARED -> Icons.Rounded.Share to WarningOrange
        CustodyAction.EDITED -> Icons.Rounded.Edit to DangerRed
        CustodyAction.EXPORTED -> Icons.Rounded.FileDownload to ElectricBlue
        CustodyAction.CREATED -> Icons.Rounded.Add to NeonCyan
    }

    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(PillShape)
                    .background(color.copy(alpha = 0.12f))
                    .border(1.dp, color.copy(alpha = 0.3f), PillShape),
                contentAlignment = Alignment.Center
            ) {
                androidx.compose.material3.Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = color,
                    modifier = Modifier.size(18.dp)
                )
            }
            if (!isLast) {
                Box(
                    modifier = Modifier
                        .width(2.dp)
                        .height(32.dp)
                        .background(GlassBorder)
                )
            }
        }
        GlassCard(modifier = Modifier.weight(1f), contentPadding = 10.dp) {
            Text(
                text = entry.action.name,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = color
            )
            Text(
                text = "${entry.softwareName} v${entry.softwareVersion}",
                style = MaterialTheme.typography.labelSmall,
                color = TextTertiary
            )
            Text(
                text = formatTimestamp(entry.timestamp),
                style = CodeText,
                color = TextTertiary
            )
        }
    }
}

private fun formatTimestamp(timestamp: Long): String {
    if (timestamp <= 0L) return "Unavailable"
    return SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date(timestamp))
}
