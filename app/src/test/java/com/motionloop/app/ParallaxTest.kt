package com.motionloop.app

import com.motionloop.app.domain.model.CameraPath
import com.motionloop.app.domain.model.ParallaxSettings
import com.motionloop.app.domain.model.Project
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.max

class ParallaxTest {

    @Test
    fun activeNeedsDepthAndAmount() {
        assertFalse(ParallaxSettings().isActive)
        assertFalse(ParallaxSettings(enabled = true).isActive)
        assertTrue(ParallaxSettings(enabled = true, depthRev = 1).isActive)
        assertFalse(ParallaxSettings(enabled = true, depthRev = 1, amount = 0f).isActive)
    }

    @Test
    fun shaderPathOrderIsStable() {
        // parallax.frag: 0 circle, 1 sway, 2 zoom, 3 tilt, 4 float.
        assertEquals(listOf("CIRCLE", "SWAY", "ZOOM", "TILT", "FLOAT"), CameraPath.entries.map { it.name })
    }

    @Test
    fun reserveCoversTheLargestShiftPerPath() {
        for (aspect in listOf(0.4f, 0.75f, 1f, 1.78f, 2.4f, 3.5f)) {
            for (path in CameraPath.entries) {
                val p = ParallaxSettings(path = path, amount = 1f)
                val reserve = p.reserve(aspect)
                // Largest shift of each path in uv units (see parallax.frag).
                val largest = when (path) {
                    CameraPath.CIRCLE -> p.offset * max(1f, aspect)
                    CameraPath.SWAY -> p.offset
                    CameraPath.ZOOM -> p.offset // (uv - 0.5) * 2 * offset at the edge
                    CameraPath.TILT -> p.offset * aspect
                    CameraPath.FLOAT -> max(p.offset, 0.5f * p.offset * aspect)
                }
                assertTrue("$path at $aspect", reserve >= largest)
                // The shader divides by 1 - 2 * reserve: it must stay well below half the frame.
                assertTrue("$path at $aspect", reserve < 0.4f)
            }
        }
    }

    @Test
    fun swayAndZoomCropLessThanCircleOnWidePhotos() {
        val wide = 21f / 9f
        val circle = ParallaxSettings(path = CameraPath.CIRCLE).reserve(wide)
        assertTrue(ParallaxSettings(path = CameraPath.SWAY).reserve(wide) < circle)
        assertTrue(ParallaxSettings(path = CameraPath.ZOOM).reserve(wide) < circle)
    }

    @Test
    fun oldProjectsLoadWithoutParallax() {
        val json = Json { ignoreUnknownKeys = true }
        val p = json.decodeFromString<Project>("""{"id":"a","createdAt":1,"updatedAt":2,"imageWidth":10,"imageHeight":20}""")
        assertEquals(ParallaxSettings(), p.parallax)
    }
}
