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
     * Beat-synced phases (11c): speed rises on each beat ([envelope] sampled at [envelopeRate] per
     * second, values 0..1), then the whole clip is rescaled so it still holds exactly [cycles]
     * cycles. Frame [frameCount] would land on a whole cycle again, so the loop stays seamless.
     */
    fun beatPhases(frameCount: Int, fps: Float, cycles: Int, envelope: FloatArray, envelopeRate: Int, amount: Float): FloatArray {
        val speeds = FloatArray(frameCount) { i ->
            val e = if (envelope.isEmpty()) 0f else envelope[((i.toFloat() / fps) * envelopeRate).toInt().coerceIn(0, envelope.lastIndex)]
            // Up to 3x speed on a strong beat at full amount.
            1f + 2f * amount.coerceIn(0f, 1f) * e
        }
        val total = speeds.sum()
        val phases = FloatArray(frameCount)
        var acc = 0f
        for (i in 0 until frameCount) {
            phases[i] = cycles * acc / total
            acc += speeds[i]
        }
        return phases
    }

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
