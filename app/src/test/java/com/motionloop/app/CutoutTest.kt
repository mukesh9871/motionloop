package com.motionloop.app

import com.motionloop.app.domain.model.BackgroundMode
import com.motionloop.app.domain.model.BackgroundSettings
import com.motionloop.app.domain.model.ExportFormat
import com.motionloop.app.domain.model.Project
import com.motionloop.app.domain.model.SubjectSettings
import com.motionloop.app.export.png.CutoutLayers
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

    private fun px(r: Int, g: Int, b: Int, a: Int) = byteArrayOf(r.toByte(), g.toByte(), b.toByte(), a.toByte())
    private val none = ByteArray(8)

    @Test
    fun pngAlphaFollowsTheMask() {
        // Two pixels: red inside the subject (mask 255), green outside (mask 0).
        val rgba = px(255, 0, 0, 255) + px(0, 255, 0, 255)
        val mask = px(255, 0, 0, 255) + px(0, 0, 0, 255)
        val out = PngExporter.compose(rgba, CutoutLayers(mask, none, none), 2, 0.1f)
        assertEquals(0xFFFF0000.toInt(), out[0])
        assertEquals(0, out[1])
        // Without layers everything is opaque photo.
        val opaque = PngExporter.compose(rgba, null, 2, 0.1f)
        assertEquals(0xFF00FF00.toInt(), opaque[1])
    }

    @Test
    fun textKeepsItsOwnColorOutsideTheSubject() {
        // Outside the subject, half-covered white front text: white at half alpha, no photo color mixed in.
        val rgba = px(0, 255, 0, 255)
        val front = px(128, 128, 128, 128) // premultiplied white at 50%
        val out = PngExporter.compose(rgba, CutoutLayers(ByteArray(4), ByteArray(4), front), 1, 0.1f)
        assertEquals(128, out[0] ushr 24)
        assertEquals(255, (out[0] shr 16) and 255)
        assertEquals(255, (out[0] shr 8) and 255)
    }

    @Test
    fun behindTextIsHiddenByTheSubject() {
        // Inside the subject, opaque blue behind text must not show: the photo (red) wins.
        val rgba = px(255, 0, 0, 255)
        val behind = px(0, 0, 255, 255)
        val out = PngExporter.compose(rgba, CutoutLayers(px(255, 0, 0, 255), behind, ByteArray(4)), 1, 0.1f)
        assertEquals(0xFFFF0000.toInt(), out[0])
    }

    @Test
    fun softEdgeIsHalfwayAtTheMiddle() {
        val rgba = ByteArray(4) { -1 }
        val mid = PngExporter.compose(rgba, CutoutLayers(px(128, 0, 0, 255), ByteArray(4), ByteArray(4)), 1, 0.2f)
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
