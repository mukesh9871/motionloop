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
        // parallax.frag: 0 circle, 1 sway, 2 zoom.
        assertEquals(listOf("CIRCLE", "SWAY", "ZOOM"), CameraPath.entries.map { it.name })
    }

    @Test
    fun zoomMarginCoversTheLargestShift() {
        // Mirrors parallax.frag: the reserve kept at each edge must exceed the largest shift.
        for (aspect in listOf(0.4f, 0.75f, 1f, 1.78f, 2.4f, 3.5f)) {
            val offset = ParallaxSettings.MAX_OFFSET
            val wanted = 1.1f * offset * max(1f, aspect)
            assertTrue("reserve must stay below half the frame", wanted < 0.5f)
            val margin = wanted / (1f - 2f * wanted)
            val reserve = margin / (1f + 2f * margin)
            val largestShift = offset * max(1f, aspect)
            assertTrue("aspect $aspect: reserve $reserve < shift $largestShift", reserve >= largestShift)
        }
    }

    @Test
    fun oldProjectsLoadWithoutParallax() {
        val json = Json { ignoreUnknownKeys = true }
        val p = json.decodeFromString<Project>("""{"id":"a","createdAt":1,"updatedAt":2,"imageWidth":10,"imageHeight":20}""")
        assertEquals(ParallaxSettings(), p.parallax)
    }
}
