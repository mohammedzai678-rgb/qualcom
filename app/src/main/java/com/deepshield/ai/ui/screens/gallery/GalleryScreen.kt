package com.deepshield.ai.ui.screens.gallery

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.deepshield.ai.domain.model.*
import com.deepshield.ai.ui.components.*
import com.deepshield.ai.ui.theme.*
import com.deepshield.ai.util.decodeBitmap
import com.deepshield.ai.util.generateVideoCacheUri
import com.deepshield.ai.util.readExifMetadata
import com.deepshield.ai.util.readFileInfo

@Composable
fun GalleryScreen(
    onNavigateToHeatmap: (String) -> Unit = {},
    onNavigateToForensics: (String) -> Unit = {},
    viewModel: GalleryViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current

    // Photo picker launcher
    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        uri?.let {
            try {
                val fileInfo = context.contentResolver.readFileInfo(it)
                val isVideo = fileInfo.mimeType?.startsWith("video/") == true ||
                    fileInfo.name.endsWith(".mp4", ignoreCase = true) ||
                    fileInfo.name.endsWith(".mov", ignoreCase = true) ||
                    fileInfo.name.endsWith(".mkv", ignoreCase = true)

                if (isVideo) {
                    viewModel.analyzeVideo(
                        uri = it,
                        fileName = fileInfo.name,
                        fileSize = fileInfo.size
                    )
                } else {
                    val metadata = context.contentResolver.readExifMetadata(it)
                    val bitmap = context.contentResolver.decodeBitmap(it)
                    if (bitmap != null) {
                        viewModel.analyzeImage(
                            bitmap = bitmap,
                            fileName = fileInfo.name,
                            metadata = metadata,
                            fileSize = fileInfo.size
                        )
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    // Camera capture launcher
    val cameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicturePreview()
    ) { bitmap ->
        if (bitmap != null) {
            viewModel.analyzeImage(
                bitmap = bitmap,
                fileName = "Camera_Capture.jpg",
                metadata = emptyMap(),
                fileSize = (bitmap.byteCount).toLong()
            )
        }
    }

    // Camera capture launcher for Video
    var videoUri by remember { mutableStateOf<Uri?>(null) }
    val videoCameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CaptureVideo()
    ) { success ->
        if (success) {
            videoUri?.let { uri ->
                val fileInfo = context.contentResolver.readFileInfo(uri)
                viewModel.analyzeVideo(
                    uri = uri,
                    fileName = fileInfo.name,
                    fileSize = fileInfo.size
                )
            }
        }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(CyberBlack),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Header
        item {
            Column(modifier = Modifier.padding(top = 8.dp)) {
                Text(
                    text = "Media Scanner",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )
                Text(
                    text = "Analyze images and videos for local deepfake artifacts",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextTertiary
                )
            }
        }

        // Upload Zone
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Box(modifier = Modifier.weight(1f)) {
                    FileUploadZone(
                        onTap = {
                            imagePickerLauncher.launch(
                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo)
                            )
                        },
                        title = "Gallery",
                        subtitle = "Select media",
                        accentColor = NeonCyan
                    )
                }
                Box(modifier = Modifier.weight(1f)) {
                    FileUploadZone(
                        onTap = {
                            cameraLauncher.launch(null)
                        },
                        title = "Photo",
                        subtitle = "Take a photo",
                        accentColor = NeonGreen
                    )
                }
                Box(modifier = Modifier.weight(1f)) {
                    FileUploadZone(
                        onTap = {
                            val uri = context.generateVideoCacheUri()
                            videoUri = uri
                            videoCameraLauncher.launch(uri)
                        },
                        title = "Video",
                        subtitle = "Record a video",
                        accentColor = WarningOrange
                    )
                }
            }
        }

        // Scanning Progress
        if (state.isScanning) {
            item {
                GlassCard(modifier = Modifier.fillMaxWidth()) {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                strokeWidth = 2.dp,
                                color = NeonCyan
                            )
                            Text(
                                text = "Analyzing media...",
                                style = MaterialTheme.typography.titleSmall,
                                color = NeonCyan
                            )
                        }

                        ScanProgressBar(
                            progress = state.scanProgress,
                            label = "Detection Pipeline",
                            color = NeonCyan
                        )

                        // Pipeline steps
                        state.currentStep?.let { step ->
                            Text(
                                text = step,
                                style = MaterialTheme.typography.labelSmall,
                                color = TextTertiary
                            )
                        }
                    }
                }
            }
        }

        state.errorMessage?.let { message ->
            item {
                GlassCard(
                    modifier = Modifier.fillMaxWidth(),
                    glowColor = DangerRed.copy(alpha = 0.15f)
                ) {
                    Text(
                        text = message,
                        style = MaterialTheme.typography.bodySmall,
                        color = WarningOrange
                    )
                }
            }
        }

        // Current Result
        state.currentResult?.let { result ->
            item {
                ScanResultCard(
                    result = result,
                    onViewHeatmap = { onNavigateToHeatmap(result.id) },
                    onViewForensics = { onNavigateToForensics(result.id) }
                )
            }
        }

        // Model Scores Breakdown
        state.currentResult?.let { result ->
            if (result.modelScores.isNotEmpty()) {
                item {
                    GlassCard(modifier = Modifier.fillMaxWidth()) {
                        Text(
                            text = "Model Confidence Breakdown",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = TextPrimary
                        )
                        Spacer(modifier = Modifier.height(12.dp))

                        result.modelScores.forEach { (model, score) ->
                            ModelScoreRow(model = model, score = score)
                            Spacer(modifier = Modifier.height(8.dp))
                        }
                    }
                }
            }
        }

        // AI Attribution
        state.currentResult?.aiAttribution?.let { attribution ->
            item {
                GlassCard(
                    modifier = Modifier.fillMaxWidth(),
                    glowColor = DangerRed.copy(alpha = 0.15f)
                ) {
                    Text(
                        text = "AI Source Attribution",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = DangerRed
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Architecture: ${attribution.architectureType}",
                        style = MaterialTheme.typography.labelSmall,
                        color = TextTertiary
                    )
                    Spacer(modifier = Modifier.height(12.dp))

                    attribution.topModels.forEachIndexed { index, candidate ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "#${index + 1}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = TextTertiary
                                )
                                Text(
                                    text = candidate.name,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Medium,
                                    color = TextPrimary
                                )
                            }
                            ConfidenceChip(score = candidate.confidence)
                        }
                    }
                }
            }
        }

        // Scan History
        if (state.scanHistory.isNotEmpty()) {
            item {
                Text(
                    text = "Scan History",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = TextPrimary
                )
            }

            items(state.scanHistory) { result ->
                ScanHistoryItem(
                    result = result,
                    onTap = { onNavigateToForensics(result.id) }
                )
            }
        }

        item { Spacer(modifier = Modifier.height(80.dp)) }
    }
}

@Composable
private fun ScanResultCard(
    result: ScanResult,
    onViewHeatmap: () -> Unit,
    onViewForensics: () -> Unit
) {
    GradientGlassCard(
        modifier = Modifier.fillMaxWidth(),
        gradientColors = when (result.verdict) {
            ScanVerdict.AUTHENTIC -> listOf(NeonGreen, NeonCyanDark)
            ScanVerdict.SUSPICIOUS -> listOf(WarningOrange, WarningAmber)
            ScanVerdict.DEEPFAKE -> listOf(DangerRed, WarningOrange)
            ScanVerdict.UNKNOWN -> listOf(TextTertiary, SurfaceOverlay)
        }
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Verdict badge
            ConfidenceBadge(
                verdict = when (result.verdict) {
                    ScanVerdict.AUTHENTIC -> Verdict.AUTHENTIC
                    ScanVerdict.SUSPICIOUS -> Verdict.SUSPICIOUS
                    ScanVerdict.DEEPFAKE -> Verdict.DEEPFAKE
                    ScanVerdict.UNKNOWN -> Verdict.UNKNOWN
                }
            )

            // Score gauge
            AuthenticityGauge(
                score = result.authenticityScore,
                size = 140.dp,
                label = "Authenticity Score"
            )

            // Stats row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "${result.confidenceScore.toInt()}%",
                        style = MetricSmall,
                        color = NeonCyan
                    )
                    Text("Confidence", style = MaterialTheme.typography.labelSmall, color = TextTertiary)
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "${result.processingTimeMs}ms",
                        style = MetricSmall,
                        color = DeepPurple
                    )
                    Text("Latency", style = MaterialTheme.typography.labelSmall, color = TextTertiary)
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "${result.detectedArtifacts.size}",
                        style = MetricSmall,
                        color = if (result.detectedArtifacts.isNotEmpty()) DangerRed else NeonGreen
                    )
                    Text("Artifacts", style = MaterialTheme.typography.labelSmall, color = TextTertiary)
                }
            }

            // Action buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                NeonOutlineButton(
                    text = "View Heatmap",
                    onClick = onViewHeatmap,
                    modifier = Modifier.weight(1f),
                    borderColor = NeonCyan
                )
                NeonButton(
                    text = "Full Report",
                    onClick = onViewForensics,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun ModelScoreRow(model: String, score: Float) {
    val color = when {
        score < 30f -> DangerRed
        score < 60f -> WarningOrange
        else -> NeonGreen
    }

    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = model,
                style = MaterialTheme.typography.bodySmall,
                color = TextSecondary
            )
            Text(
                text = "${score.toInt()}%",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = color
            )
        }
        ScanProgressBar(progress = score / 100f, color = color)
    }
}

@Composable
private fun ScanHistoryItem(result: ScanResult, onTap: () -> Unit) {
    val verdictColor = when (result.verdict) {
        ScanVerdict.AUTHENTIC -> NeonGreen
        ScanVerdict.SUSPICIOUS -> WarningOrange
        ScanVerdict.DEEPFAKE -> DangerRed
        ScanVerdict.UNKNOWN -> TextTertiary
    }

    GlassCard(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onTap)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(PillShape)
                    .background(verdictColor)
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = result.fileName.ifEmpty { "Unknown File" },
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextPrimary
                )
                Text(
                    text = "${result.mediaType.name} · ${result.processingTimeMs}ms",
                    style = MaterialTheme.typography.labelSmall,
                    color = TextTertiary
                )
            }
            ConfidenceChip(score = result.authenticityScore)
        }
    }
}
