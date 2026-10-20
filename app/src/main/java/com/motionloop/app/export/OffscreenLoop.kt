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

    /** Fits [aspect] to a short edge (or long edge), rounded down to [align] and never above the source. */
    fun fit(aspect: Float, edge: Int, edgeIsShort: Boolean, srcW: Int, srcH: Int, align: Int): Pair<Int, Int> {
        val landscape = aspect >= 1f
        val ratio = if (landscape) aspect else 1f / aspect
        var short = if (edgeIsShort) edge.toFloat() else edge / ratio
        short = minOf(short, minOf(srcW, srcH).toFloat())
        val long = short * ratio
        val s = max(align, (short / align).toInt() * align)
        val l = max(align, (long / align).roundToInt() * align)
        return if (landscape) l to s else s to l
    }
}
