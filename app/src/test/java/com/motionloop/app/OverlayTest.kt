package com.motionloop.app

import com.motionloop.app.domain.model.BlendMode
import com.motionloop.app.domain.model.LightLeak
import com.motionloop.app.domain.model.OverlaySettings
import com.motionloop.app.domain.model.ParticleSeeds
import com.motionloop.app.domain.model.ParticleType
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OverlayTest {

    @Test
    fun shaderConstantsMatchEnumOrdinals() {
        // particles.vert uType: 1 sparkle, 2 snow, 3 rain, 4 bokeh, 5 embers.
        assertEquals(listOf("NONE", "SPARKLE", "SNOW", "RAIN", "BOKEH", "EMBERS"), ParticleType.entries.map { it.name })
        // light_leak.frag uStyle: 1 warm, 2 rose, 3 aurora.
        assertEquals(listOf("NONE", "WARM", "ROSE", "AURORA"), LightLeak.entries.map { it.name })
        // color.frag uBlendMode: 0 normal, 1 add, 2 screen, 3 overlay, 4 multiply, 5 soft light.
        assertEquals(
            listOf("NORMAL", "ADD", "SCREEN", "OVERLAY", "MULTIPLY", "SOFT_LIGHT"),
            BlendMode.entries.map { it.name },
        )
    }

    @Test
    fun activeOnlyWithSomethingVisible() {
        assertFalse(OverlaySettings().isActive)
        assertTrue(OverlaySettings(particles = ParticleType.SNOW).isActive)
        assertTrue(OverlaySettings(lightLeak = LightLeak.WARM).isActive)
        assertFalse(OverlaySettings(lightLeak = LightLeak.WARM, leakIntensity = 0f).isActive)
        assertFalse(OverlaySettings(particles = ParticleType.SNOW, opacity = 0f).isActive)
    }

    @Test
    fun particleCountFollowsDensity() {
        assertEquals(0, OverlaySettings().particleCount)
        assertEquals(OverlaySettings.MAX_PARTICLES, OverlaySettings(particles = ParticleType.RAIN, density = 1f).particleCount)
        assertEquals(1, OverlaySettings(particles = ParticleType.RAIN, density = 0f).particleCount)
        assertEquals(OverlaySettings.MAX_PARTICLES, OverlaySettings(particles = ParticleType.RAIN, density = 5f).particleCount)
    }

    @Test
    fun seedsAreStableAndInRange() {
        val a = ParticleSeeds.data
        assertEquals(OverlaySettings.MAX_PARTICLES * 4, a.size)
        assertTrue(a.all { it in 0f..1f })
        // Same object every time: preview and export must share one layout.
        assertTrue(a === ParticleSeeds.data)
    }

    @Test
    fun oldProjectsWithoutOverlayStillLoad() {
        val json = Json { ignoreUnknownKeys = true }
        val old = """{"id":"a","createdAt":1,"updatedAt":2,"imageWidth":10,"imageHeight":20}"""
        val p = json.decodeFromString<com.motionloop.app.domain.model.Project>(old)
        assertEquals(OverlaySettings(), p.overlay)
        assertFalse(p.overlay.isActive)
    }
}
