package com.motionloop.app

import com.motionloop.app.domain.model.LoopSettings
import com.motionloop.app.domain.model.LoopTiming
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
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
    fun crossfadeHidesEachWrap() {
        // Copy 1 wraps at whole phases: copy 2 must have full weight there.
        assertEquals(1f, LoopTiming.secondCopyWeight(0f), 1e-6f)
        assertEquals(1f, LoopTiming.secondCopyWeight(3f), 1e-6f)
        assertEquals(1f, LoopTiming.secondCopyWeight(2.99999f), 1e-4f)
        // Copy 2 wraps at half phases: its weight must be zero there.
        assertEquals(0f, LoopTiming.secondCopyWeight(0.5f), 1e-6f)
        assertEquals(0f, LoopTiming.secondCopyWeight(7.5f), 1e-6f)
    }

    @Test
    fun crossfadeIsContinuous() {
        var prev = LoopTiming.secondCopyWeight(0f)
        var t = 0f
        while (t < 4f) {
            t += 0.001f
            val w = LoopTiming.secondCopyWeight(t)
            assertTrue(abs(w - prev) < 0.01f)
            prev = w
        }
    }
}
