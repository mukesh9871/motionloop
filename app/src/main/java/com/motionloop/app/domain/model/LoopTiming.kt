package com.motionloop.app.domain.model

import kotlin.math.max
import kotlin.math.roundToInt

/** Loop phase math shared by preview, export and tests. */
object LoopTiming {
    /**
     * Whole number of cycles in a clip, so the last frame flows into the first.
     * Speed is rounded to the nearest cycle count, minimum one.
     */
    fun cyclesForClip(lengthSeconds: Float, speed: Float): Int =
        max(1, (lengthSeconds * speed / LoopSettings.BASE_CYCLE_SECONDS).roundToInt())

    /** Phase in cycles (not wrapped) for frame [index] of [frameCount]. */
    fun phaseForFrame(index: Int, frameCount: Int, cycles: Int): Float =
        cycles.toFloat() * index / frameCount
}
