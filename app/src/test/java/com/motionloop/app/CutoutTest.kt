package com.motionloop.app

import com.motionloop.app.domain.model.BackgroundMode
import com.motionloop.app.domain.model.BackgroundSettings
import com.motionloop.app.domain.model.ExportFormat
import com.motionloop.app.domain.model.Project
import com.motionloop.app.domain.model.SelectionMode
import com.motionloop.app.domain.model.SubjectSettings
import com.motionloop.app.export.png.PngExporter
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CutoutTest {

    @Test
    fun shaderModeOrderIsStable() {
        // source.frag uBgMode: 0 is the editing tint, then the BackgroundMode ordinals 1..5.
        assertEquals(
            listOf("NONE", "TRANSPARENT", "SOLID", "GRADIENT", "BLUR", "PHOTO"),
            BackgroundMode.entries.map { it.name },
        )
        assertEquals(listOf("FREEZE", "ANIMATE_ONLY"), SelectionMode.entries.map { it.name })
        assertTrue(ExportFormat.PNG in ExportFormat.entries)
    }

    @Test
    fun backgroundNeedsASubjectAndAPhotoForPhotoMode() {
        val none = SubjectSettings()
        val some = SubjectSettings(maskRev = 1)
        assertFalse(BackgroundSettings(mode = BackgroundMode.SOLID).isActive(none))
        assertTrue(BackgroundSettings(mode = BackgroundMode.SOLID).isActive(some))
        assertFalse(BackgroundSettings(mode = BackgroundMode.NONE).isActive(some))
        assertFalse(BackgroundSettings(mode = BackgroundMode.PHOTO).isActive(some))
        assertTrue(BackgroundSettings(mode = BackgroundMode.PHOTO, photoRev = 2).isActive(some))
    }

    @Test
    fun pngAlphaFollowsTheMask() {
        // Two pixels: red inside the subject (mask 255), green outside (mask 0).
        val rgba = byteArrayOf(255.toByte(), 0, 0, 255.toByte(), 0, 255.toByte(), 0, 255.toByte())
        val mask = byteArrayOf(255.toByte(), 0, 0, 255.toByte(), 0, 0, 0, 255.toByte())
        val out = PngExporter.compose(rgba, mask, 2, 0.1f)
        assertEquals(0xFFFF0000.toInt(), out[0])
        assertEquals(0x0000FF00, out[1])
        // Without a mask everything is opaque.
        val opaque = PngExporter.compose(rgba, null, 2, 0.1f)
        assertEquals(255, opaque[1] ushr 24)
    }

    @Test
    fun softEdgeIsHalfwayAtTheMiddle() {
        val rgba = ByteArray(4) { -1 }
        val mid = PngExporter.compose(rgba, byteArrayOf(128.toByte(), 0, 0, -1), 1, 0.2f)
        val alpha = mid[0] ushr 24
        assertTrue("alpha $alpha", alpha in 120..136)
    }

    @Test
    fun oldProjectsLoadWithoutCutout() {
        val json = Json { ignoreUnknownKeys = true }
        val p = json.decodeFromString<Project>("""{"id":"a","createdAt":1,"updatedAt":2,"imageWidth":10,"imageHeight":20}""")
        assertEquals(SubjectSettings(), p.subject)
        assertEquals(BackgroundSettings(), p.background)
        assertTrue(p.selections.isEmpty())
    }
}
