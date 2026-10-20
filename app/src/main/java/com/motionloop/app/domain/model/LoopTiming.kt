package com.motionloop.app.domain.model

import kotlin.math.abs
import kotlin.math.floor
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

    /**
     * Weight of the second warp copy in continuous mode. Reference for `loop_warp.frag`,
     * keep the two in sync. Copy 1 wraps at whole phases, where this is 1 (copy 1 hidden).
     * Copy 2 wraps at half phases, where this is 0 (copy 2 hidden).
     */
    fun secondCopyWeight(phase: Float): Float {
        val p1 = phase - floor(phase)
        return abs(2f * p1 - 1f)
    }
}
