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
        val orientation = resolver.openInputStream(uri)?.use(::exifOrientation) ?: ExifInterface.ORIENTATION_NORMAL
        return decode(maxEdge, orientation) { resolver.openInputStream(uri) ?: throw IOException("Cannot open $uri") }
    }

    /** Bundled assets: upright, no EXIF handling needed. [open] must return a fresh stream each call. */
    fun decode(maxEdge: Int, open: () -> InputStream): Bitmap = decode(maxEdge, ExifInterface.ORIENTATION_NORMAL, open)

    /** Project files are stored upright already, so EXIF is not read again. */
    fun decode(file: File, maxEdge: Int): Bitmap =
        decode(maxEdge, ExifInterface.ORIENTATION_NORMAL) { file.inputStream() }

    private fun decode(maxEdge: Int, orientation: Int, open: () -> InputStream): Bitmap {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        open().use { BitmapFactory.decodeStream(it, null, bounds) }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) throw IOException("Unsupported image")

        // Power-of-two subsampling, then the decoder scales the rest itself (density scaling), so
        // the full-size image is never held in memory: a 48 MP photo decodes straight to maxEdge.
        val longEdge = max(bounds.outWidth, bounds.outHeight)
        var sample = 1
        while (longEdge / (sample * 2) >= maxEdge) sample *= 2
        val sampled = longEdge / sample
        val opts = BitmapFactory.Options().apply {
            inSampleSize = sample
            inPreferredConfig = Bitmap.Config.ARGB_8888
            if (sampled > maxEdge) {
                inScaled = true
                inDensity = sampled
                inTargetDensity = maxEdge
            }
        }
        val decoded = open().use { BitmapFactory.decodeStream(it, null, opts) }
            ?: throw IOException("Decode failed")

        if (orientation == ExifInterface.ORIENTATION_NORMAL) return decoded
        val m = Matrix().apply { applyOrientation(this, orientation) }
        val out = Bitmap.createBitmap(decoded, 0, 0, decoded.width, decoded.height, m, true)
        if (out !== decoded) decoded.recycle()
        return out
    }

    private fun exifOrientation(stream: InputStream): Int = try {
        ExifInterface(stream).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
    } catch (ignored: IOException) {
        ExifInterface.ORIENTATION_NORMAL
    }

    /** All 8 EXIF orientations, including the mirrored ones some front cameras write. */
    private fun applyOrientation(m: Matrix, orientation: Int) {
        when (orientation) {
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> m.postScale(-1f, 1f)
            ExifInterface.ORIENTATION_ROTATE_180 -> m.postRotate(180f)
            ExifInterface.ORIENTATION_FLIP_VERTICAL -> m.postScale(1f, -1f)
            ExifInterface.ORIENTATION_TRANSPOSE -> {
                m.postRotate(90f)
                m.postScale(-1f, 1f)
            }
            ExifInterface.ORIENTATION_ROTATE_90 -> m.postRotate(90f)
            ExifInterface.ORIENTATION_TRANSVERSE -> {
                m.postRotate(-90f)
                m.postScale(-1f, 1f)
            }
            ExifInterface.ORIENTATION_ROTATE_270 -> m.postRotate(270f)
            else -> Unit
        }
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
