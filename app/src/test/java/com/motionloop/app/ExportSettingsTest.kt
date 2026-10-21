package com.motionloop.app

import com.motionloop.app.domain.model.ExportResolution
import com.motionloop.app.domain.model.ExportSettings
import com.motionloop.app.domain.model.LoopSettings
import com.motionloop.app.domain.model.LoopTiming
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ExportSettingsTest {

    @Test
    fun lengthListsAndProThresholdAgree() {
        val video = ExportSettings.VIDEO_LENGTHS
        assertEquals(video.sorted(), video)
        assertEquals(video.toSet().size, video.size)
        assertTrue(ExportSettings.LONG_SECONDS in video)
        // GIFs are short and never Pro.
        for (g in ExportSettings.GIF_LENGTHS) {
            assertTrue(g in video)
            assertTrue(g < ExportSettings.LONG_SECONDS)
        }
        assertEquals(ExportResolution.entries.map { it.shortEdge }.sorted(), ExportResolution.entries.map { it.shortEdge })
    }

    @Test
    fun everyLengthAndSpeedGivesWholeCycles() {
        for (length in ExportSettings.VIDEO_LENGTHS) {
            var speed = LoopSettings.MIN_SPEED
            while (speed <= LoopSettings.MAX_SPEED + 1e-4f) {
                val cycles = LoopTiming.cyclesForClip(length.toFloat(), speed)
                assertTrue("$length s at $speed", cycles >= 1)
                val frames = length * ExportSettings.VIDEO_FPS
                // The frame after the last one lands exactly on a whole cycle.
                assertEquals(cycles.toFloat(), LoopTiming.phaseForFrame(frames, frames, cycles), 1e-6f)
                speed += 0.05f
            }
        }
    }
}
