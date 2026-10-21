package com.motionloop.app

import com.motionloop.app.domain.model.ExportAspect
import com.motionloop.app.export.OffscreenLoop
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ExportShapeTest {

    @Test
    fun cropKeepsTheLargestRegionOfTheTargetShape() {
        // Landscape 4000x3000 to 9:16: full height, narrow width.
        val (w, h) = OffscreenLoop.cropSize(4000, 3000, 9f / 16f)
        assertEquals(3000, h)
        assertEquals(1688, w)
        // Portrait 3000x4000 to 1:1: full width.
        assertEquals(3000 to 3000, OffscreenLoop.cropSize(3000, 4000, 1f))
    }

    @Test
    fun coverViewportFillsAndCenters() {
        // Landscape photo into a 1080x1920 story frame.
        val v = OffscreenLoop.coverViewport(4000, 3000, 1080, 1920)
        val (x, y, vw, vh) = listOf(v[0], v[1], v[2], v[3])
        assertTrue(vw >= 1080 && vh >= 1920)
        assertEquals(1920, vh)
        assertEquals((1080 - vw) / 2, x)
        assertEquals(0, y)
        // Same shape: no overflow.
        assertTrue(OffscreenLoop.coverViewport(4000, 3000, 800, 600).contentEquals(intArrayOf(0, 0, 800, 600)))
    }

    @Test
    fun originalKeepsThePhotoShape() {
        assertEquals(1.5f, ExportAspect.ORIGINAL.widthOverHeight(1.5f), 1e-6f)
        assertEquals(9f / 16f, ExportAspect.STORY.widthOverHeight(1.5f), 1e-6f)
        assertEquals(0.8f, ExportAspect.PORTRAIT.widthOverHeight(1.5f), 1e-6f)
    }
}
