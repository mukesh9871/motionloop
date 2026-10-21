package com.motionloop.app.export

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.withContext
import java.util.concurrent.Executors
import kotlin.math.max
import kotlin.math.roundToInt

/** Helpers shared by the exporters. */
internal object OffscreenLoop {

    /**
     * EGL contexts are bound to one thread, so each export runs on its own single thread.
     * Coroutine suspension inside still resumes on that same thread.
     */
    suspend fun <T> onGlThread(name: String, block: suspend () -> T): T {
        val executor = Executors.newSingleThreadExecutor { r -> Thread(r, name) }
        val dispatcher: CoroutineDispatcher = executor.asCoroutineDispatcher()
        try {
            return withContext(dispatcher) { block() }
        } finally {
            executor.shutdown()
        }
    }

    /** The largest region of a [srcW] x [srcH] photo with [targetAspect] (width / height), in photo pixels. */
    fun cropSize(srcW: Int, srcH: Int, targetAspect: Float): Pair<Int, Int> {
        val photoAspect = srcW.toFloat() / srcH
        return if (targetAspect > photoAspect) {
            srcW to max(1, (srcW / targetAspect).roundToInt())
        } else {
            max(1, (srcH * targetAspect).roundToInt()) to srcH
        }
    }

    /**
     * Viewport (x, y, width, height) that draws a [srcW] x [srcH] photo center-cropped to fill a
     * [w] x [h] target: larger than the target and centered, so the overflow is clipped.
     */
    fun coverViewport(srcW: Int, srcH: Int, w: Int, h: Int): IntArray {
        val scale = maxOf(w.toFloat() / srcW, h.toFloat() / srcH)
        val vw = max(w, (srcW * scale).roundToInt())
        val vh = max(h, (srcH * scale).roundToInt())
        return intArrayOf((w - vw) / 2, (h - vh) / 2, vw, vh)
    }

    /** Fits [aspect] to a short edge (or long edge), rounded down to [align] and never above the source. */
    fun fit(aspect: Float, edge: Int, edgeIsShort: Boolean, srcW: Int, srcH: Int, align: Int): Pair<Int, Int> {
        val landscape = aspect >= 1f
        val ratio = if (landscape) aspect else 1f / aspect
        var short = if (edgeIsShort) edge.toFloat() else edge / ratio
        short = minOf(short, minOf(srcW, srcH).toFloat())
        val long = short * ratio
        val s = max(align, (short / align).toInt() * align)
        // Floor both edges: rounding up could ask for a few pixels more than the source has.
        val l = max(align, (long / align).toInt() * align)
        return if (landscape) l to s else s to l
    }
}
