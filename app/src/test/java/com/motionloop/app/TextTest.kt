package com.motionloop.app

import com.motionloop.app.domain.model.Project
import com.motionloop.app.domain.model.TextAnimation
import com.motionloop.app.domain.model.TextLayer
import com.motionloop.app.domain.model.TextTiming
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TextTest {

    @Test
    fun everyAnimationLoopsSeamlessly() {
        // Phase p and p + n must look the same, or exported loops would jump.
        for (a in TextAnimation.entries) {
            for (p in listOf(0f, 0.1f, 0.3f, 0.6f, 0.9f, 0.99f)) {
                val x = TextTiming.at(a, p)
                val y = TextTiming.at(a, p + 3f)
                // Float phases: 3.1f - 3f is not exactly 0.1f, so compare with a tolerance.
                assertEquals("$a alpha at $p", x.alpha, y.alpha, 1e-3f)
                assertEquals("$a offset at $p", x.offsetX, y.offsetX, 1e-3f)
                assertEquals("$a reveal at $p", x.reveal, y.reveal, 1e-3f)
                assertEquals("$a glow at $p", x.glow, y.glow, 1e-3f)
            }
        }
    }

    @Test
    fun fadeAndSlideComeInHoldAndGoOut() {
        for (a in listOf(TextAnimation.FADE, TextAnimation.SLIDE_IN)) {
            assertEquals(0f, TextTiming.at(a, 0f).alpha, 1e-6f)
            assertEquals(1f, TextTiming.at(a, 0.5f).alpha, 1e-6f)
            assertTrue(TextTiming.at(a, 0.99f).alpha < 0.1f)
        }
        assertEquals(0f, TextTiming.at(TextAnimation.SLIDE_IN, 0.5f).offsetX, 1e-6f)
        assertTrue(TextTiming.at(TextAnimation.SLIDE_IN, 0.05f).offsetX < 0f)
    }

    @Test
    fun typewriterRevealsDuringTheFirstHalf() {
        assertEquals(0f, TextTiming.at(TextAnimation.TYPEWRITER, 0f).reveal, 1e-6f)
        assertEquals(0.5f, TextTiming.at(TextAnimation.TYPEWRITER, 0.25f).reveal, 1e-6f)
        assertEquals(1f, TextTiming.at(TextAnimation.TYPEWRITER, 0.6f).reveal, 1e-6f)
    }

    @Test
    fun staticTextIsAlwaysFullyVisible() {
        for (p in listOf(0f, 0.5f, 0.99f)) {
            val s = TextTiming.at(TextAnimation.NONE, p)
            assertEquals(1f, s.alpha, 1e-6f)
            assertEquals(1f, s.reveal, 1e-6f)
        }
    }

    @Test
    fun renderKeyIgnoresGpuOnlyProperties() {
        val a = TextLayer(id = "1", x = 0.1f, y = 0.2f, rotation = 30f, animation = TextAnimation.FADE)
        val b = a.copy(x = 0.9f, y = 0.8f, rotation = -10f, animation = TextAnimation.SLIDE_IN, behindSubject = false)
        assertEquals(a.renderKey(), b.renderKey())
        // Typewriter needs its order bitmap, so it changes the key.
        assertNotEquals(a.renderKey(), a.copy(animation = TextAnimation.TYPEWRITER).renderKey())
        assertNotEquals(a.renderKey(), a.copy(text = "Other").renderKey())
    }

    @Test
    fun oldProjectsLoadWithoutText() {
        val json = Json { ignoreUnknownKeys = true }
        val p = json.decodeFromString<Project>("""{"id":"a","createdAt":1,"updatedAt":2,"imageWidth":10,"imageHeight":20}""")
        assertTrue(p.texts.isEmpty())
    }
}
