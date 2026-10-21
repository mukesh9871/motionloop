package com.motionloop.ml

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.framework.image.ByteBufferExtractor
import com.google.mediapipe.tasks.components.containers.NormalizedKeypoint
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.vision.interactivesegmenter.InteractiveSegmenter
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.segmentation.subject.SubjectSegmentation
import com.google.mlkit.vision.segmentation.subject.SubjectSegmenterOptions
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.tasks.await
import java.nio.ByteOrder
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * The one segmentation module shared by sky detection, tap-to-select (11a),
 * background removal (11d) and text behind subject (11e).
 *
 * Calls block on model inference: call from a background dispatcher.
 * Not for concurrent use from many threads, an internal mutex serializes calls.
 */
class Segmenter(private val context: Context) {

    private val lock = Mutex()
    private var interactive: InteractiveSegmenter? = null
    private val subjectClientLazy = lazy {
        SubjectSegmentation.getClient(
            SubjectSegmenterOptions.Builder().enableForegroundConfidenceMask().build(),
        )
    }
    private val subjectClient by subjectClientLazy

    /** Mask of the object under the normalized point ([x], [y]). Soft edges from model confidence. */
    suspend fun segmentAt(image: Bitmap, x: Float, y: Float): AlphaMask = lock.withLock {
        val input = prepare(image)
        try {
            val result = interactive().segment(
                BitmapImageBuilder(input).build(),
                InteractiveSegmenter.RegionOfInterest.create(NormalizedKeypoint.create(x, y)),
            )
            val masks = result.confidenceMasks().orElse(null)
                ?: throw IllegalStateException("Segmenter returned no confidence mask")
            // The model may return background and foreground channels. The foreground is
            // the channel that is most confident at the tapped point.
            var best: AlphaMask? = null
            var bestScore = -1
            for (m in masks) {
                val buffer = ByteBufferExtractor.extract(m).order(ByteOrder.nativeOrder()).asFloatBuffer()
                val values = FloatArray(m.width * m.height)
                buffer.get(values)
                val mask = AlphaMask.fromFloats(m.width, m.height, values)
                val score = mask.sample(x, y)
                if (score > bestScore) {
                    bestScore = score
                    best = mask
                }
            }
            best ?: throw IllegalStateException("Segmenter returned an empty mask list")
        } finally {
            if (input !== image) input.recycle()
        }
    }

    /** Union of [segmentAt] over several points. */
    suspend fun segmentAtAll(image: Bitmap, points: List<Pair<Float, Float>>): AlphaMask? {
        var acc: AlphaMask? = null
        for ((x, y) in points) {
            val m = segmentAt(image, x, y)
            acc = acc?.combine(m, subtract = false) ?: m
        }
        return acc
    }

    /**
     * Main subject(s) of the photo. ML Kit first. When Play services cannot run it,
     * falls back to the interactive model seeded at the image center.
     */
    suspend fun segmentSubject(image: Bitmap): AlphaMask {
        val input = prepare(image)
        try {
            val result = subjectClient.process(InputImage.fromBitmap(input, 0)).await()
            val buffer = result.foregroundConfidenceMask
            if (buffer != null) {
                val values = FloatArray(input.width * input.height)
                buffer.rewind()
                buffer.get(values)
                return AlphaMask.fromFloats(input.width, input.height, values)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "ML Kit subject segmentation unavailable, using the interactive model", e)
        } finally {
            if (input !== image) input.recycle()
        }
        return segmentAt(image, 0.5f, 0.55f)
    }

    /** Sky detection: seeds from [SkySeeds], then the interactive model. Null when no sky is found. */
    suspend fun segmentSky(image: Bitmap): AlphaMask? {
        val thumb = Bitmap.createScaledBitmap(image, SEED_EDGE, max(1, image.height * SEED_EDGE / image.width), true)
        val pixels = IntArray(thumb.width * thumb.height)
        thumb.getPixels(pixels, 0, thumb.width, 0, 0, thumb.width, thumb.height)
        val seeds = SkySeeds.find(pixels, thumb.width, thumb.height)
        if (thumb !== image) thumb.recycle()
        if (seeds.isEmpty()) return null
        val mask = segmentAtAll(image, seeds) ?: return null
        return mask.takeIf { it.coverage() > 0.02f }
    }

    /** Under the lock: freeing native memory under a running inference would crash. */
    suspend fun close() = lock.withLock {
        interactive?.close()
        interactive = null
        if (subjectClientLazy.isInitialized()) subjectClient.close()
    }

    private fun interactive(): InteractiveSegmenter = interactive ?: InteractiveSegmenter.createFromOptions(
        context,
        InteractiveSegmenter.InteractiveSegmenterOptions.builder()
            .setBaseOptions(BaseOptions.builder().setModelAssetPath(MAGIC_TOUCH).build())
            .setOutputConfidenceMasks(true)
            .setOutputCategoryMask(false)
            .build(),
    ).also { interactive = it }

    /** ARGB_8888 no larger than [INPUT_EDGE] on the long side. Models run at low resolution anyway. */
    private fun prepare(src: Bitmap): Bitmap {
        val scale = INPUT_EDGE.toFloat() / max(src.width, src.height)
        val needsScale = scale < 1f
        val needsConfig = src.config != Bitmap.Config.ARGB_8888
        if (!needsScale && !needsConfig) return src
        val w = if (needsScale) (src.width * scale).roundToInt().coerceAtLeast(1) else src.width
        val h = if (needsScale) (src.height * scale).roundToInt().coerceAtLeast(1) else src.height
        val scaled = Bitmap.createScaledBitmap(src, w, h, true)
        return if (scaled.config == Bitmap.Config.ARGB_8888) scaled else scaled.copy(Bitmap.Config.ARGB_8888, false).also {
            if (scaled !== src) scaled.recycle()
        }
    }

    private companion object {
        const val TAG = "Segmenter"
        const val MAGIC_TOUCH = "magic_touch.tflite"
        const val INPUT_EDGE = 768
        const val SEED_EDGE = 96
    }
}
