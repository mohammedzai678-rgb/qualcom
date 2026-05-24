package com.deepshield.ai.ui.screens.gallery

import android.graphics.Bitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.deepshield.ai.data.ScanRepository
import com.deepshield.ai.domain.model.ScanResult
import com.deepshield.ai.ml.DeepfakeDetector
import com.deepshield.ai.ml.VideoDeepfakeDetector
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

data class GalleryState(
    val isScanning: Boolean = false,
    val scanProgress: Float = 0f,
    val currentStep: String? = null,
    val currentResult: ScanResult? = null,
    val scanHistory: List<ScanResult> = emptyList(),
    val errorMessage: String? = null
)

@HiltViewModel
class GalleryViewModel @Inject constructor(
    private val deepfakeDetector: DeepfakeDetector,
    private val videoDeepfakeDetector: VideoDeepfakeDetector,
    private val scanRepository: ScanRepository
) : ViewModel() {

    private val _state = MutableStateFlow(GalleryState())
    val state: StateFlow<GalleryState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            scanRepository.scanHistory.collectLatest { history ->
                _state.value = _state.value.copy(scanHistory = history)
            }
        }
    }

    fun analyzeImage(
        bitmap: Bitmap,
        fileName: String,
        metadata: Map<String, String> = emptyMap(),
        fileSize: Long = 0L
    ) {
        viewModelScope.launch {
            _state.value = _state.value.copy(
                isScanning = true,
                scanProgress = 0f,
                currentStep = "Initializing scan...",
                errorMessage = null
            )

            try {
                val result = deepfakeDetector.analyzeImage(
                    bitmap = bitmap,
                    fileName = fileName,
                    metadata = metadata,
                    fileSize = fileSize
                ) { progress, step ->
                    _state.value = _state.value.copy(
                        scanProgress = progress,
                        currentStep = step
                    )
                }

                scanRepository.addScan(result)
                _state.value = _state.value.copy(
                    isScanning = false,
                    scanProgress = 1f,
                    currentStep = null,
                    currentResult = result,
                    errorMessage = null
                )
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Throwable) {
                _state.value = _state.value.copy(
                    isScanning = false,
                    scanProgress = 0f,
                    currentStep = null,
                    currentResult = null,
                    errorMessage = "Image scan could not be completed for $fileName."
                )
            }
        }
    }

    fun analyzeVideo(
        uri: android.net.Uri,
        fileName: String,
        fileSize: Long = 0L
    ) {
        viewModelScope.launch {
            _state.value = _state.value.copy(
                isScanning = true,
                scanProgress = 0f,
                currentStep = "Initializing video scan...",
                errorMessage = null
            )

            try {
                val result = videoDeepfakeDetector.analyzeVideo(
                    uri = uri,
                    fileName = fileName,
                    fileSize = fileSize
                ) { progress, step ->
                    _state.value = _state.value.copy(
                        scanProgress = progress,
                        currentStep = step
                    )
                }

                val errorMessage = if (result.verdict == com.deepshield.ai.domain.model.ScanVerdict.UNKNOWN &&
                    result.modelScores.isEmpty()
                ) {
                    "Video scan finished without a usable result. Try a shorter clip or record again."
                } else {
                    null
                }

                scanRepository.addScan(result)
                _state.value = _state.value.copy(
                    isScanning = false,
                    scanProgress = 1f,
                    currentStep = null,
                    currentResult = result,
                    errorMessage = errorMessage
                )
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Throwable) {
                _state.value = _state.value.copy(
                    isScanning = false,
                    scanProgress = 0f,
                    currentStep = null,
                    currentResult = null,
                    errorMessage = "Video scan could not be completed for $fileName."
                )
            }
        }
    }
}
