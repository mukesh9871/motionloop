package com.motionloop.app

import com.motionloop.app.domain.model.LoopSettings
import com.motionloop.app.domain.model.LoopTiming
import org.junit.Assert.assertEquals
import org.junit.Test
import kotlin.math.abs
import kotlin.math.floor

class LoopTimingTest {

    @Test
    fun cyclesAreWholeAndAtLeastOne() {
        assertEquals(1, LoopTiming.cyclesForClip(3f, 0.25f))
        assertEquals(1, LoopTiming.cyclesForClip(3f, 1f))
        assertEquals(5, LoopTiming.cyclesForClip(15f, 1f))
        assertEquals(10, LoopTiming.cyclesForClip(15f, LoopSettings.MAX_SPEED))
    }

    @Test
    fun frameAfterLastWrapsToFirst() {
        // The loop is seamless when frame N (one past the end) has the same wrapped phase as frame 0.
        for (seconds in listOf(3, 5, 10, 15)) {
            for (speed in listOf(0.25f, 0.6f, 1f, 1.7f, 2f)) {
                val n = seconds * 24
                val cycles = LoopTiming.cyclesForClip(seconds.toFloat(), speed)
                val end = LoopTiming.phaseForFrame(n, n, cycles)
                assertEquals(0f, end - floor(end), 1e-5f)
            }
        }
    }

    @Test
    fun crossfadeWeightHidesEachWrap() {
        // Mirrors loop_warp.frag: copy 1 has weight 1 - |2 p1 - 1|, zero exactly where p1 wraps.
        fun weight1(phase: Float): Float {
            val p1 = phase - floor(phase)
            return 1f - abs(2f * p1 - 1f)
        }
        assertEquals(0f, weight1(0f), 1e-6f)
        assertEquals(0f, weight1(3f), 1e-6f)
        assertEquals(1f, weight1(0.5f), 1e-6f)
        // Copy 2 wraps at p1 = 0.5, where its weight (1 - weight1) is zero.
        assertEquals(0f, 1f - weight1(2.5f), 1e-6f)
    }
}
