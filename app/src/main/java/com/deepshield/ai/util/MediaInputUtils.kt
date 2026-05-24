package com.deepshield.ai.util

import android.content.ContentResolver
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.provider.OpenableColumns
import androidx.core.content.FileProvider
import androidx.exifinterface.media.ExifInterface
import java.io.ByteArrayOutputStream
import java.io.File
import java.security.MessageDigest

data class PickedFileInfo(
    val name: String,
    val size: Long,
    val mimeType: String?
)

fun Context.generateVideoCacheUri(): Uri {
    val dir = File(cacheDir, "camera")
    if (!dir.exists()) dir.mkdirs()
    val file = File(dir, "video_capture_${System.currentTimeMillis()}.mp4")
    return FileProvider.getUriForFile(this, "${packageName}.provider", file)
}

fun ContentResolver.readFileInfo(uri: Uri): PickedFileInfo {
    var displayName = uri.lastPathSegment?.substringAfterLast('/') ?: "selected_media"
    var fileSize = 0L

    query(uri, arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE), null, null, null)
        ?.use { cursor ->
            val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
            if (cursor.moveToFirst()) {
                if (nameIndex >= 0) {
                    displayName = cursor.getString(nameIndex) ?: displayName
                }
                if (sizeIndex >= 0 && !cursor.isNull(sizeIndex)) {
                    fileSize = cursor.getLong(sizeIndex)
                }
            }
        }

    return PickedFileInfo(
        name = displayName,
        size = fileSize,
        mimeType = getType(uri)
    )
}

fun ContentResolver.readByteSample(uri: Uri, maxBytes: Int = 256 * 1024): ByteArray? {
    return openInputStream(uri)?.use { inputStream ->
        val buffer = ByteArray(8 * 1024)
        val output = ByteArrayOutputStream()
        var totalBytes = 0

        while (totalBytes < maxBytes) {
            val bytesToRead = minOf(buffer.size, maxBytes - totalBytes)
            val read = inputStream.read(buffer, 0, bytesToRead)
            if (read <= 0) break
            output.write(buffer, 0, read)
            totalBytes += read
        }

        output.toByteArray()
    }
}

fun ContentResolver.decodeBitmap(uri: Uri): Bitmap? {
    return openInputStream(uri)?.use { inputStream ->
        BitmapFactory.decodeStream(inputStream)
    }
}

fun ContentResolver.readExifMetadata(uri: Uri): Map<String, String> {
    return openInputStream(uri)?.use { inputStream ->
        val exif = ExifInterface(inputStream)
        linkedMapOf(
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
    } ?: emptyMap()
}

fun stableScore(sample: ByteArray, seed: Int, min: Float = 20f, max: Float = 95f): Float {
    if (sample.isEmpty()) return min

    val digest = MessageDigest.getInstance("SHA-256")
        .digest("$seed:${sample.size}".toByteArray() + sample)

    val normalized = (
        ((digest[0].toInt() and 0xFF) shl 8) or
            (digest[1].toInt() and 0xFF)
        ) / 65535f

    return (min + (max - min) * normalized).coerceIn(min, max)
}

fun stableFingerprint(sample: ByteArray, length: Int = 12): String {
    if (sample.isEmpty()) return "unavailable"
    val digest = MessageDigest.getInstance("SHA-256").digest(sample)
    return digest.joinToString("") { "%02x".format(it) }.take(length)
}
