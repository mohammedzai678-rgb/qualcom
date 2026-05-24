package com.deepshield.ai.ui.screens.watermark

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.core.content.FileProvider
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.deepshield.ai.domain.model.CustodyAction
import com.deepshield.ai.domain.model.CustodyEntry
import com.deepshield.ai.domain.model.WatermarkMode
import com.deepshield.ai.domain.model.WatermarkPayload
import com.deepshield.ai.watermark.CryptoSigner
import com.deepshield.ai.watermark.DctWatermarkEngine
import com.deepshield.ai.watermark.WatermarkProvenanceStore
import dagger.hilt.android.lifecycle.HiltViewModel
import java.io.File
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class WatermarkState(
    val selectedTab: Int = 0,
    val selectedMode: WatermarkMode = WatermarkMode.INVISIBLE,
    val isProcessing: Boolean = false,
    val lastPayload: WatermarkPayload? = null,
    val verificationResult: WatermarkPayload? = null,
    val chainOfCustody: List<CustodyEntry> = emptyList(),
    val publicKeyFingerprint: String = "",
    val selectedEmbedFileName: String? = null,
    val selectedVerifyFileName: String? = null,
    val statusMessage: String = "Select an image to embed or verify a local watermark.",
    val hasWatermarkedImage: Boolean = false,
    val isSaved: Boolean = false,
    val savedUri: Uri? = null
)

@HiltViewModel
class WatermarkViewModel @Inject constructor(
    private val watermarkEngine: DctWatermarkEngine,
    private val cryptoSigner: CryptoSigner,
    private val provenanceStore: WatermarkProvenanceStore
) : ViewModel() {

    private var lastEmbeddedBitmap: Bitmap? = null

    private val _state = MutableStateFlow(
        WatermarkState(
            publicKeyFingerprint = cryptoSigner.getPublicKeyFingerprint()
        )
    )
    val state: StateFlow<WatermarkState> = _state.asStateFlow()

    fun selectTab(tab: Int) {
        _state.update { it.copy(selectedTab = tab) }
    }

    fun selectMode(mode: WatermarkMode) {
        _state.update { it.copy(selectedMode = mode) }
    }

    fun embedWatermark(bitmap: Bitmap, fileName: String) {
        viewModelScope.launch {
            _state.update {
                it.copy(
                    isProcessing = true,
                    selectedEmbedFileName = fileName,
                    statusMessage = "Embedding a signed ${it.selectedMode.displayName()} watermark..."
                )
            }

            try {
                val mode = _state.value.selectedMode
                val ownerFingerprint = cryptoSigner.getPublicKeyFingerprint()
                val payload = withContext(Dispatchers.Default) {
                    val sourceHash = watermarkEngine.computeContentHash(bitmap)
                    val (watermarkedBitmap, rawPayload) = watermarkEngine.embedWatermark(
                        original = bitmap,
                        mode = mode,
                        ownerHash = ownerFingerprint
                    )
                    replaceLastEmbeddedBitmap(watermarkedBitmap)

                    val signedPayload = rawPayload.copy(
                        signature = cryptoSigner.sign(
                            signaturePayload(
                                manifestId = rawPayload.c2paManifestId,
                                contentHash = rawPayload.contentHash,
                                ownerHash = rawPayload.ownerHash
                            )
                        )
                    )

                    provenanceStore.save(
                        WatermarkProvenanceStore.ProvenanceRecord(
                            manifestToken = watermarkEngine.manifestToken(signedPayload.c2paManifestId),
                            manifestId = signedPayload.c2paManifestId,
                            ownerHash = signedPayload.ownerHash,
                            deviceId = signedPayload.deviceId,
                            mode = signedPayload.mode,
                            captureTimestamp = signedPayload.captureTimestamp,
                            sourceContentHash = sourceHash,
                            watermarkedContentHash = signedPayload.contentHash,
                            signature = signedPayload.signature
                        )
                    )

                    signedPayload
                }

                val now = System.currentTimeMillis()
                _state.update { current ->
                    current.copy(
                        isProcessing = false,
                        lastPayload = payload,
                        verificationResult = null,
                        hasWatermarkedImage = true,
                        isSaved = false,
                        savedUri = null,
                        chainOfCustody = current.chainOfCustody + listOf(
                            CustodyEntry(
                                action = CustodyAction.CAPTURED,
                                timestamp = now - 500L,
                                softwareName = fileName,
                                softwareVersion = "source"
                            ),
                            CustodyEntry(
                                action = CustodyAction.WATERMARKED,
                                timestamp = now,
                                contentHash = payload.contentHash,
                                signature = payload.signature
                            )
                        ),
                        statusMessage = "Watermark embedded successfully. Save or share the watermarked image."
                    )
                }
            } catch (_: Exception) {
                _state.update {
                    it.copy(
                        isProcessing = false,
                        statusMessage = "The selected image could not be watermarked."
                    )
                }
            }
        }
    }

    fun saveWatermarkedImage(context: Context) {
        val bitmap = lastEmbeddedBitmap ?: return
        val fileName = _state.value.selectedEmbedFileName ?: "watermarked_image"

        viewModelScope.launch {
            _state.update { it.copy(isProcessing = true, statusMessage = "Saving to gallery...") }

            try {
                val savedUri = withContext(Dispatchers.IO) {
                    saveToMediaStore(context, bitmap, fileName)
                }

                val now = System.currentTimeMillis()
                _state.update { current ->
                    current.copy(
                        isProcessing = false,
                        isSaved = true,
                        savedUri = savedUri,
                        chainOfCustody = current.chainOfCustody + CustodyEntry(
                            action = CustodyAction.EXPORTED,
                            timestamp = now,
                            contentHash = current.lastPayload?.contentHash.orEmpty(),
                            signature = current.lastPayload?.signature.orEmpty()
                        ),
                        statusMessage = "Saved to Pictures/DeepShieldAI. Image is ready to verify later."
                    )
                }
            } catch (_: Exception) {
                _state.update {
                    it.copy(
                        isProcessing = false,
                        statusMessage = "Could not save the image. Please check storage permissions."
                    )
                }
            }
        }
    }

    fun createShareIntent(context: Context): Intent? {
        val bitmap = lastEmbeddedBitmap ?: return null
        val fileName = _state.value.selectedEmbedFileName ?: "watermarked_image"

        return try {
            val cacheDir = File(context.cacheDir, "watermarked").apply { mkdirs() }
            val sanitized = fileName.replace(Regex("[^a-zA-Z0-9._-]"), "_")
            val shareFile = File(cacheDir, "WM_${sanitized}_${System.currentTimeMillis()}.png")
            shareFile.outputStream().use { out ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
            }

            val shareUri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.provider",
                shareFile
            )

            val now = System.currentTimeMillis()
            _state.update { current ->
                current.copy(
                    chainOfCustody = current.chainOfCustody + CustodyEntry(
                        action = CustodyAction.SHARED,
                        timestamp = now,
                        contentHash = current.lastPayload?.contentHash.orEmpty(),
                        signature = current.lastPayload?.signature.orEmpty()
                    ),
                    statusMessage = "Sharing watermarked image..."
                )
            }

            Intent(Intent.ACTION_SEND).apply {
                type = "image/png"
                putExtra(Intent.EXTRA_STREAM, shareUri)
                putExtra(Intent.EXTRA_SUBJECT, "DeepShield AI - Watermarked Image")
                putExtra(
                    Intent.EXTRA_TEXT,
                    "This image has been watermarked with DeepShield AI for provenance verification. " +
                        "Content hash: ${_state.value.lastPayload?.contentHash?.take(16) ?: "N/A"}"
                )
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
        } catch (_: Exception) {
            _state.update { it.copy(statusMessage = "Could not prepare image for sharing.") }
            null
        }
    }

    private fun saveToMediaStore(context: Context, bitmap: Bitmap, originalName: String): Uri? {
        val resolver = context.contentResolver
        val sanitized = originalName
            .substringBeforeLast(".")
            .replace(Regex("[^a-zA-Z0-9._-]"), "_")
        val displayName = "WM_${sanitized}_${System.currentTimeMillis()}"

        val contentValues = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, "$displayName.png")
            put(MediaStore.MediaColumns.MIME_TYPE, "image/png")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.MediaColumns.RELATIVE_PATH, "${Environment.DIRECTORY_PICTURES}/DeepShieldAI")
                put(MediaStore.MediaColumns.IS_PENDING, 1)
            }
        }

        val imageUri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)
            ?: throw IllegalStateException("MediaStore insert returned null")

        resolver.openOutputStream(imageUri)?.use { outputStream ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, outputStream)
        } ?: throw IllegalStateException("Could not open output stream")

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            contentValues.clear()
            contentValues.put(MediaStore.MediaColumns.IS_PENDING, 0)
            resolver.update(imageUri, contentValues, null, null)
        }

        return imageUri
    }

    fun verifyWatermark(bitmap: Bitmap, fileName: String) {
        viewModelScope.launch {
            _state.update {
                it.copy(
                    isProcessing = true,
                    selectedVerifyFileName = fileName,
                    statusMessage = "Verifying watermark integrity from the selected image..."
                )
            }

            try {
                val baselinePayload = _state.value.lastPayload
                val verifiedPayload = withContext(Dispatchers.Default) {
                    val currentHash = watermarkEngine.computeContentHash(bitmap)
                    val exactHashRecord = provenanceStore.findByWatermarkedContentHash(currentHash)
                    val exactHashPayload = when {
                        exactHashRecord != null -> exactHashRecord.toPayload()
                        baselinePayload?.contentHash.equals(currentHash, ignoreCase = true) -> baselinePayload
                        else -> null
                    }

                    val inspection = watermarkEngine.inspectWatermark(bitmap)
                    if (inspection == null && exactHashPayload != null) {
                        return@withContext exactHashPayload.copy(integrityScore = 100f)
                    }
                    if (inspection == null) {
                        return@withContext WatermarkPayload(integrityScore = 0f)
                    }

                    val record = provenanceStore.findByManifestToken(inspection.manifestToken)
                        ?: exactHashRecord
                    val expectedPayload = record?.toPayload() ?: exactHashPayload ?: baselinePayload
                    val expectedToken = expectedPayload?.c2paManifestId
                        ?.takeIf { it.isNotBlank() }
                        ?.let(watermarkEngine::manifestToken)

                    val signatureValid = when {
                        record != null && record.signature.isNotBlank() -> cryptoSigner.verify(
                            signaturePayload(
                                manifestId = record.manifestId,
                                contentHash = record.watermarkedContentHash,
                                ownerHash = record.ownerHash
                            ),
                            record.signature
                        )
                        expectedPayload != null && expectedPayload.signature.isNotBlank() -> cryptoSigner.verify(
                            signaturePayload(
                                manifestId = expectedPayload.c2paManifestId,
                                contentHash = expectedPayload.contentHash,
                                ownerHash = expectedPayload.ownerHash
                            ),
                            expectedPayload.signature
                        )
                        else -> false
                    }

                    val exactHashMatch = when {
                        record != null -> currentHash.equals(record.watermarkedContentHash, ignoreCase = true)
                        expectedPayload != null -> currentHash.equals(expectedPayload.contentHash, ignoreCase = true)
                        else -> false
                    }

                    val tokenMatch = when {
                        record != null -> inspection.manifestToken.equals(record.manifestToken, ignoreCase = true)
                        expectedToken != null -> inspection.manifestToken.equals(expectedToken, ignoreCase = true)
                        else -> inspection.checksumValid
                    }

                    val integrity = when {
                        exactHashMatch && signatureValid -> 100f
                        tokenMatch && signatureValid -> 88f
                        tokenMatch -> 76f
                        inspection.checksumValid -> 60f
                        else -> 0f
                    }

                    val resolvedPayload = record?.toPayload() ?: expectedPayload ?: WatermarkPayload(
                        captureTimestamp = inspection.captureTimestamp,
                        mode = inspection.mode
                    )

                    resolvedPayload.copy(
                        captureTimestamp = inspection.captureTimestamp.takeIf { it > 0 } ?: resolvedPayload.captureTimestamp,
                        mode = inspection.mode,
                        integrityScore = integrity
                    )
                }

                val now = System.currentTimeMillis()
                _state.update { current ->
                    current.copy(
                        isProcessing = false,
                        verificationResult = verifiedPayload,
                        chainOfCustody = current.chainOfCustody + CustodyEntry(
                            action = CustodyAction.VERIFIED,
                            timestamp = now,
                            contentHash = verifiedPayload.contentHash,
                            signature = verifiedPayload.signature
                        ),
                        statusMessage = when {
                            verifiedPayload.integrityScore >= 95f ->
                                "Watermark verified successfully for $fileName."
                            verifiedPayload.integrityScore >= 75f ->
                                "Watermark recovered for $fileName, but the file no longer exactly matches the original exported copy."
                            verifiedPayload.integrityScore > 0f ->
                                "A watermark was detected, but no trusted local provenance record matched it strongly."
                            else ->
                                "No valid watermark could be recovered from the selected image."
                        }
                    )
                }
            } catch (_: Exception) {
                _state.update {
                    it.copy(
                        isProcessing = false,
                        statusMessage = "The selected image could not be verified."
                    )
                }
            }
        }
    }

    fun verifyLastEmbeddedWatermark() {
        val bitmap = lastEmbeddedBitmap ?: return
        val fileName = _state.value.selectedEmbedFileName ?: "embedded_session_copy"
        verifyWatermark(bitmap, fileName)
    }

    private fun signaturePayload(manifestId: String, contentHash: String, ownerHash: String): ByteArray {
        return "$manifestId|$contentHash|$ownerHash".toByteArray(Charsets.UTF_8)
    }

    private fun replaceLastEmbeddedBitmap(bitmap: Bitmap) {
        val previous = lastEmbeddedBitmap
        if (previous != null && previous !== bitmap && !previous.isRecycled) {
            previous.recycle()
        }
        lastEmbeddedBitmap = bitmap
    }

    override fun onCleared() {
        lastEmbeddedBitmap?.takeIf { !it.isRecycled }?.recycle()
        lastEmbeddedBitmap = null
        super.onCleared()
    }
}
