package com.deepshield.ai.ui.screens.livecamera

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Matrix
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.core.EaseInOutCubic
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CameraAlt
import androidx.compose.material.icons.rounded.FlipCameraAndroid
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.deepshield.ai.ui.components.NeonButton
import com.deepshield.ai.ui.theme.CardShape
import com.deepshield.ai.ui.theme.CyberBlack
import com.deepshield.ai.ui.theme.DangerRed
import com.deepshield.ai.ui.theme.DeepPurple
import com.deepshield.ai.ui.theme.GlassBorder
import com.deepshield.ai.ui.theme.MetricSmall
import com.deepshield.ai.ui.theme.NeonCyan
import com.deepshield.ai.ui.theme.NeonGreen
import com.deepshield.ai.ui.theme.PillShape
import com.deepshield.ai.ui.theme.SurfaceCard
import com.deepshield.ai.ui.theme.TextPrimary
import com.deepshield.ai.ui.theme.TextTertiary
import com.deepshield.ai.ui.theme.WarningOrange
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

@Composable
fun LiveCameraScreen(
    viewModel: LiveCameraViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val state by viewModel.state.collectAsStateWithLifecycle()

    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.CAMERA
            ) == PackageManager.PERMISSION_GRANTED
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasCameraPermission = granted
    }

    LaunchedEffect(Unit) {
        if (!hasCameraPermission) {
            permissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(CyberBlack)
    ) {
        if (hasCameraPermission) {
            CameraPreview(
                isFrontCamera = state.isFrontCamera,
                onFrameAnalyzed = { bitmap -> viewModel.analyzeFrame(bitmap) },
                modifier = Modifier.fillMaxSize()
            )

            DetectionOverlay(state = state)
            TopInfoBar(
                state = state,
                onToggleCamera = { viewModel.toggleCamera() }
            )
            BottomControlBar(
                state = state,
                onToggleDetection = {
                    if (state.isDetectionActive) viewModel.stopDetection() else viewModel.startDetection()
                }
            )

            AnimatedVisibility(
                visible = state.isAlertShowing,
                enter = slideInVertically() + fadeIn(),
                exit = slideOutVertically() + fadeOut(),
                modifier = Modifier.align(Alignment.TopCenter)
            ) {
                AlertBanner()
            }
        } else {
            PermissionRequestScreen(
                onRequestPermission = { permissionLauncher.launch(Manifest.permission.CAMERA) }
            )
        }
    }
}

@Composable
private fun CameraPreview(
    isFrontCamera: Boolean,
    onFrameAnalyzed: (Bitmap) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val cameraExecutor: ExecutorService = remember { Executors.newSingleThreadExecutor() }
    val cameraProviderFuture = remember(context) { ProcessCameraProvider.getInstance(context) }
    var previewViewRef by remember { mutableStateOf<PreviewView?>(null) }

    DisposableEffect(cameraExecutor, cameraProviderFuture) {
        onDispose {
            runCatching { cameraProviderFuture.get().unbindAll() }
            cameraExecutor.shutdown()
        }
    }

    DisposableEffect(previewViewRef, isFrontCamera, lifecycleOwner, cameraProviderFuture) {
        val previewView = previewViewRef
        if (previewView == null) {
            onDispose { }
        } else {
            val ctx = previewView.context
            cameraProviderFuture.addListener({
                val cameraProvider = runCatching { cameraProviderFuture.get() }.getOrNull() ?: return@addListener

                val preview = Preview.Builder()
                    .build()
                    .also { it.setSurfaceProvider(previewView.surfaceProvider) }

                val imageAnalysis = ImageAnalysis.Builder()
                    .setTargetResolution(android.util.Size(320, 240))
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888)
                    .build()
                    .also { analysis ->
                        analysis.setAnalyzer(cameraExecutor) { imageProxy ->
                            try {
                                imageProxy.toBitmapRotated()?.let(onFrameAnalyzed)
                            } finally {
                                imageProxy.close()
                            }
                        }
                    }

                val cameraSelector = if (isFrontCamera) {
                    CameraSelector.DEFAULT_FRONT_CAMERA
                } else {
                    CameraSelector.DEFAULT_BACK_CAMERA
                }

                runCatching {
                    cameraProvider.unbindAll()
                    cameraProvider.bindToLifecycle(
                        lifecycleOwner,
                        cameraSelector,
                        preview,
                        imageAnalysis
                    )
                }
            }, ContextCompat.getMainExecutor(ctx))

            onDispose {
                runCatching { cameraProviderFuture.get().unbindAll() }
            }
        }
    }

    AndroidView(
        factory = { ctx ->
            PreviewView(ctx).apply {
                scaleType = PreviewView.ScaleType.FILL_CENTER
            }
        },
        update = { previewView ->
            val ctx = previewView.context
            val cameraProviderFuture = ProcessCameraProvider.getInstance(ctx)
            cameraProviderFuture.addListener({
                val cameraProvider = cameraProviderFuture.get()

                val preview = Preview.Builder()
                    .build()
                    .also { it.setSurfaceProvider(previewView.surfaceProvider) }

                // Reduced resolution (320x240) for analysis — preview stays full res.
                // Only the analysis buffer is smaller for faster ML inference.
                val imageAnalysis = ImageAnalysis.Builder()
                    .setTargetResolution(android.util.Size(320, 240))
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888)
                    .build()
                    .also { analysis ->
                        analysis.setAnalyzer(cameraExecutor) { imageProxy ->
                            // CRITICAL FIX: Apply rotation from ImageProxy.
                            // Back camera images arrive rotated 90°/270° — without
                            // this rotation the deprecated android.media.FaceDetector
                            // cannot find faces (it requires upright images).
                            imageProxy.toBitmapRotated()?.let(onFrameAnalyzed)
                            imageProxy.close()
                        }
                    }

                // Dynamic CameraSelector based on toggle state.
                // Previously hardcoded to DEFAULT_FRONT_CAMERA.
                val cameraSelector = if (isFrontCamera) {
                    CameraSelector.DEFAULT_FRONT_CAMERA
                } else {
                    CameraSelector.DEFAULT_BACK_CAMERA
                }

                try {
                    cameraProvider.unbindAll()
                    cameraProvider.bindToLifecycle(
                        lifecycleOwner,
                        cameraSelector,
                        preview,
                        imageAnalysis
                    )
                } catch (_: Exception) {
                }
            }, ContextCompat.getMainExecutor(ctx))
        },
        modifier = modifier
    )
}

/**
 * Convert ImageProxy to a correctly rotated Bitmap.
 *
 * CameraX does NOT auto-rotate image data — it provides rotationDegrees
 * metadata. Without applying this rotation, the android.media.FaceDetector
 * fails on back-camera frames (they arrive landscape-oriented).
 *
 * Research: Android official docs confirm ImageProxy.imageInfo.rotationDegrees
 * must be applied manually for correct orientation.
 */
private fun ImageProxy.toBitmapRotated(): Bitmap? {
    val plane = planes.firstOrNull() ?: return null
    val buffer = plane.buffer
    buffer.rewind()

    val rowPadding = plane.rowStride - plane.pixelStride * width
    val paddedWidth = width + rowPadding / plane.pixelStride
    val rawBitmap = Bitmap.createBitmap(paddedWidth, height, Bitmap.Config.ARGB_8888)
    rawBitmap.copyPixelsFromBuffer(buffer)

    val croppedBitmap = if (paddedWidth == width) {
        rawBitmap
    } else {
        Bitmap.createBitmap(rawBitmap, 0, 0, width, height).also {
            rawBitmap.recycle()
        }
    }

    // Apply rotation if needed (back camera typically 90° or 270°)
    val rotationDegrees = imageInfo.rotationDegrees
    return if (rotationDegrees != 0) {
        val matrix = Matrix().apply { postRotate(rotationDegrees.toFloat()) }
        Bitmap.createBitmap(croppedBitmap, 0, 0, croppedBitmap.width, croppedBitmap.height, matrix, true).also {
            croppedBitmap.recycle()
        }
    } else {
        croppedBitmap
    }
}

@Composable
private fun DetectionOverlay(state: LiveCameraState) {
    val borderColor = if (state.currentScore >= 70f) NeonGreen else DangerRed
    val pulseAlpha by rememberInfiniteTransition(label = "overlay").animateFloat(
        initialValue = 0.3f,
        targetValue = 0.8f,
        animationSpec = infiniteRepeatable(
            animation = tween(800, easing = EaseInOutCubic),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseAlpha"
    )

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        if (!state.isDetectionActive) return@BoxWithConstraints

        state.detectedFaces.forEach { face ->
            Box(
                modifier = Modifier
                    .offset(
                        x = maxWidth * face.x.coerceIn(0f, 1f),
                        y = maxHeight * face.y.coerceIn(0f, 1f)
                    )
                    .width(maxWidth * face.width.coerceIn(0.05f, 1f))
                    .height(maxHeight * face.height.coerceIn(0.05f, 1f))
                    .border(
                        width = 2.dp,
                        color = borderColor.copy(alpha = pulseAlpha),
                        shape = CardShape
                    )
            ) {
                CornerIndicator(Alignment.TopStart, borderColor)
                CornerIndicator(Alignment.TopEnd, borderColor)
                CornerIndicator(Alignment.BottomStart, borderColor)
                CornerIndicator(Alignment.BottomEnd, borderColor)

                Box(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .offset(y = 16.dp)
                        .clip(PillShape)
                        .background(borderColor.copy(alpha = 0.2f), PillShape)
                        .border(1.dp, borderColor.copy(alpha = 0.5f), PillShape)
                        .padding(horizontal = 12.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = if (state.currentScore >= 70f) {
                            "${state.currentScore.toInt()}% Authentic"
                        } else {
                            "${state.currentScore.toInt()}% Suspicious"
                        },
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = borderColor
                    )
                }
            }
        }
    }
}

@Composable
private fun BoxScope.CornerIndicator(alignment: Alignment, color: Color) {
    Box(
        modifier = Modifier
            .size(20.dp)
            .align(alignment)
            .then(
                when (alignment) {
                    Alignment.TopStart -> Modifier.offset(x = (-2).dp, y = (-2).dp)
                    Alignment.TopEnd -> Modifier.offset(x = 2.dp, y = (-2).dp)
                    Alignment.BottomStart -> Modifier.offset(x = (-2).dp, y = 2.dp)
                    Alignment.BottomEnd -> Modifier.offset(x = 2.dp, y = 2.dp)
                    else -> Modifier
                }
            )
            .border(3.dp, color, CardShape)
    )
}

@Composable
private fun TopInfoBar(
    state: LiveCameraState,
    onToggleCamera: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp)
            .padding(top = 32.dp)
            .clip(CardShape)
            .background(CyberBlack.copy(alpha = 0.7f), CardShape)
            .border(1.dp, GlassBorder, CardShape)
            .padding(12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column {
            Text(
                text = "Live Shield",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = NeonCyan
            )
            Text(
                text = if (state.isDetectionActive) "Detection Active" else "Paused",
                style = MaterialTheme.typography.labelSmall,
                color = if (state.isDetectionActive) NeonGreen else TextTertiary
            )
        }

        Row(
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("${state.fps.toInt()}", style = MetricSmall, color = NeonGreen)
                Text("FPS", style = MaterialTheme.typography.labelSmall, color = TextTertiary)
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("${state.latencyMs.toInt()}ms", style = MetricSmall, color = NeonCyan)
                Text("Latency", style = MaterialTheme.typography.labelSmall, color = TextTertiary)
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("${state.frameCount}", style = MetricSmall, color = DeepPurple)
                Text("Frames", style = MaterialTheme.typography.labelSmall, color = TextTertiary)
            }

            // Camera toggle button
            IconButton(onClick = onToggleCamera) {
                Icon(
                    imageVector = Icons.Rounded.FlipCameraAndroid,
                    contentDescription = if (state.isFrontCamera) "Switch to back camera" else "Switch to front camera",
                    tint = NeonCyan,
                    modifier = Modifier.size(24.dp)
                )
            }
        }
    }
}

@Composable
private fun BottomControlBar(
    state: LiveCameraState,
    onToggleDetection: () -> Unit
) {
    val delegateLabel = when {
        state.npuMetrics.activeDelegate.startsWith("NNAPI") -> "NNAPI"
        state.npuMetrics.activeDelegate.startsWith("GPU") -> "GPU"
        else -> "CPU"
    }

    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.BottomCenter
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    brush = Brush.verticalGradient(
                        colors = listOf(Color.Transparent, CyberBlack.copy(alpha = 0.9f))
                    )
                )
                .padding(16.dp)
                .padding(bottom = 80.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(CardShape)
                    .background(SurfaceCard.copy(alpha = 0.8f), CardShape)
                    .padding(12.dp),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "${state.averageScore.toInt()}%",
                        style = MetricSmall,
                        color = if (state.averageScore >= 70f) NeonGreen else DangerRed
                    )
                    Text("Avg Score", style = MaterialTheme.typography.labelSmall, color = TextTertiary)
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "${state.suspiciousFrames}",
                        style = MetricSmall,
                        color = if (state.suspiciousFrames > 0) DangerRed else NeonGreen
                    )
                    Text("Suspicious", style = MaterialTheme.typography.labelSmall, color = TextTertiary)
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(delegateLabel, style = MetricSmall, color = NeonCyan)
                    Text("Delegate", style = MaterialTheme.typography.labelSmall, color = TextTertiary)
                }
            }

            NeonButton(
                text = if (state.isDetectionActive) "Pause Detection" else "Start Detection",
                onClick = onToggleDetection,
                gradientColors = if (state.isDetectionActive) {
                    listOf(DangerRed, WarningOrange)
                } else {
                    listOf(NeonCyan, DeepPurple)
                }
            )
        }
    }
}

@Composable
private fun AlertBanner() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp)
            .padding(top = 100.dp)
            .clip(CardShape)
            .background(DangerRed.copy(alpha = 0.15f), CardShape)
            .border(1.dp, DangerRed.copy(alpha = 0.5f), CardShape)
            .padding(12.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                imageVector = Icons.Rounded.Warning,
                contentDescription = "Alert",
                tint = DangerRed,
                modifier = Modifier.size(20.dp)
            )
            Text(
                text = "Potential deepfake detected - suspicion level high",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = DangerRed
            )
        }
    }
}

@Composable
private fun PermissionRequestScreen(onRequestPermission: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.Rounded.CameraAlt,
            contentDescription = null,
            tint = NeonCyan,
            modifier = Modifier.size(64.dp)
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "Camera Permission Required",
            style = MaterialTheme.typography.titleLarge,
            color = TextPrimary
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "DeepShield needs camera access for real-time deepfake detection. All processing stays on-device.",
            style = MaterialTheme.typography.bodyMedium,
            color = TextTertiary
        )
        Spacer(modifier = Modifier.height(24.dp))
        NeonButton(
            text = "Grant Permission",
            onClick = onRequestPermission
        )
    }
}
