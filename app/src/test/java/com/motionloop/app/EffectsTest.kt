package com.motionloop.app

import com.motionloop.app.domain.model.DispersionSettings
import com.motionloop.app.domain.model.EffectsSettings
import com.motionloop.app.domain.model.MaskMode
import com.motionloop.app.domain.model.MaskStroke
import com.motionloop.app.domain.model.NPoint
import com.motionloop.app.domain.model.OverlaySettings
import com.motionloop.app.domain.model.Project
import com.motionloop.app.domain.model.SelectionMode
import com.motionloop.app.domain.model.StickerKind
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EffectsTest {

    private val paint = MaskStroke(listOf(NPoint(0.5f, 0.5f)), radius = 0.05f, hardness = 0.8f, mode = MaskMode.FREEZE)
    private val erase = paint.copy(mode = MaskMode.UNFREEZE)

    @Test
    fun shaderOrdersAreStable() {
        // sticker.frag: butterfly 1, flame 2, birds 3, glint 4 (ordinal + 1).
        assertEquals(listOf("BUTTERFLY", "FLAME", "BIRDS", "GLINT"), StickerKind.entries.map { it.name })
        assertEquals(listOf("FREEZE", "ANIMATE_ONLY", "RIPPLE"), SelectionMode.entries.map { it.name })
    }

    @Test
    fun dispersionNeedsPaintedArea() {
        assertFalse(DispersionSettings().isActive)
        assertFalse(DispersionSettings(strokes = listOf(erase)).isActive)
        assertTrue(DispersionSettings(strokes = listOf(paint)).isActive)
        assertFalse(DispersionSettings(strokes = listOf(paint), amount = 0f).isActive)
    }

    @Test
    fun overlayAreaOnlyWithPaint() {
        assertFalse(OverlaySettings().hasArea)
        assertFalse(OverlaySettings(areaStrokes = listOf(erase)).hasArea)
        assertTrue(OverlaySettings(areaStrokes = listOf(paint)).hasArea)
    }

    @Test
    fun oldProjectsLoadWithoutEffects() {
        val json = Json { ignoreUnknownKeys = true }
        val p = json.decodeFromString<Project>("""{"id":"a","createdAt":1,"updatedAt":2,"imageWidth":10,"imageHeight":20}""")
        assertEquals(EffectsSettings(), p.effects)
        assertTrue(p.overlay.areaStrokes.isEmpty())
    }
}
