package com.muddassir.squadgym.core.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import android.provider.OpenableColumns
import java.io.ByteArrayOutputStream

data class PickedImage(
    val bytes: ByteArray,
    val mimeType: String,
    val fileName: String,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is PickedImage) return false
        return bytes.contentEquals(other.bytes) && mimeType == other.mimeType && fileName == other.fileName
    }

    override fun hashCode(): Int {
        var result = bytes.contentHashCode()
        result = 31 * result + mimeType.hashCode()
        result = 31 * result + fileName.hashCode()
        return result
    }
}

/**
 * Reads a gallery/camera Uri into a bounded JPEG.
 *
 * Profile photos are capped at 1024 px and re-encoded at quality 85, so the API's
 * 5 MB upload limit is never hit and the image stays crisp on a phone.
 */
object ImageReader {

    private const val MAX_DIMENSION = 1024
    private const val QUALITY = 85

    fun read(context: Context, uri: Uri): PickedImage? = runCatching {
        val bitmap = decode(context, uri) ?: return null
        val scaled = scaleDown(bitmap, MAX_DIMENSION)
        val output = ByteArrayOutputStream()
        scaled.compress(Bitmap.CompressFormat.JPEG, QUALITY, output)
        if (scaled !== bitmap) bitmap.recycle()
        scaled.recycle()

        PickedImage(
            bytes = output.toByteArray(),
            mimeType = "image/jpeg",
            fileName = displayName(context, uri)?.substringBeforeLast('.')?.plus(".jpg") ?: "profile.jpg",
        )
    }.getOrNull()

    private fun decode(context: Context, uri: Uri): Bitmap? {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            runCatching { ImageDecoder.decodeBitmap(ImageDecoder.createSource(context.contentResolver, uri)) { decoder, _, _ ->
                decoder.isMutableRequired = false
                decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            } }.getOrNull()
        } else {
            context.contentResolver.openInputStream(uri)?.use { stream ->
                BitmapFactory.decodeStream(stream)
            }
        }
    }

    fun scaleDown(bitmap: Bitmap, maxDimension: Int): Bitmap {
        val longest = maxOf(bitmap.width, bitmap.height)
        if (longest <= maxDimension) return bitmap
        val ratio = maxDimension.toFloat() / longest
        return Bitmap.createScaledBitmap(
            bitmap,
            (bitmap.width * ratio).toInt().coerceAtLeast(1),
            (bitmap.height * ratio).toInt().coerceAtLeast(1),
            true,
        )
    }

    private fun displayName(context: Context, uri: Uri): String? {
        return context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
            ?.use { cursor ->
                if (cursor.moveToFirst()) cursor.getString(0) else null
            }
    }
}
