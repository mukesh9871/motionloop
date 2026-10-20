package com.motionloop.app.core.image

import android.content.ContentResolver
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import androidx.exifinterface.media.ExifInterface
import java.io.File
import java.io.IOException
import java.io.InputStream
import kotlin.math.max
import kotlin.math.roundToInt

/** Decodes photos upright (EXIF applied) and no larger than a requested long edge. Call off the main thread. */
object BitmapDecoder {

    fun decode(resolver: ContentResolver, uri: Uri, maxEdge: Int): Bitmap {
        val rotation = resolver.openInputStream(uri)?.use(::exifRotation) ?: 0
        return decode(maxEdge, rotation) { resolver.openInputStream(uri) ?: throw IOException("Cannot open $uri") }
    }

    fun decode(file: File, maxEdge: Int): Bitmap = decode(maxEdge, rotation = 0) { file.inputStream() }

    private fun decode(maxEdge: Int, rotation: Int, open: () -> InputStream): Bitmap {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        open().use { BitmapFactory.decodeStream(it, null, bounds) }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) throw IOException("Unsupported image")

        // Power-of-two subsampling first (cheap, saves memory), exact scale after.
        var sample = 1
        while (max(bounds.outWidth, bounds.outHeight) / (sample * 2) >= maxEdge) sample *= 2
        val opts = BitmapFactory.Options().apply {
            inSampleSize = sample
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        val decoded = open().use { BitmapFactory.decodeStream(it, null, opts) }
            ?: throw IOException("Decode failed")

        val scale = minOf(1f, maxEdge.toFloat() / max(decoded.width, decoded.height))
        if (scale >= 1f && rotation == 0) return decoded
        val m = Matrix().apply {
            postScale(scale, scale)
            postRotate(rotation.toFloat())
        }
        val out = Bitmap.createBitmap(decoded, 0, 0, decoded.width, decoded.height, m, true)
        if (out !== decoded) decoded.recycle()
        return out
    }

    private fun exifRotation(stream: InputStream): Int = try {
        when (ExifInterface(stream).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
            ExifInterface.ORIENTATION_ROTATE_90 -> 90
            ExifInterface.ORIENTATION_ROTATE_180 -> 180
            ExifInterface.ORIENTATION_ROTATE_270 -> 270
            else -> 0
        }
    } catch (_: IOException) {
        0
    }

    fun scaleToLongEdge(src: Bitmap, longEdge: Int): Bitmap {
        val scale = longEdge.toFloat() / max(src.width, src.height)
        if (scale >= 1f) return src
        return Bitmap.createScaledBitmap(
            src,
            (src.width * scale).roundToInt().coerceAtLeast(1),
            (src.height * scale).roundToInt().coerceAtLeast(1),
            true,
        )
    }
}
