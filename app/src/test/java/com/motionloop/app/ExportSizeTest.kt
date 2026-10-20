package com.motionloop.app

import com.motionloop.app.export.OffscreenLoop
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ExportSizeTest {

    @Test
    fun portraitFullHdIsAlignedAndKeepsAspect() {
        val (w, h) = OffscreenLoop.fit(3f / 4f, 1080, edgeIsShort = true, srcW = 3000, srcH = 4000, align = 16)
        assertEquals(0, w % 16)
        assertEquals(0, h % 16)
        assertTrue(w in 1056..1080)
        assertEquals(4f / 3f, h.toFloat() / w, 0.03f)
    }

    @Test
    fun neverUpscalesSmallPhotos() {
        val (w, h) = OffscreenLoop.fit(16f / 9f, 1080, edgeIsShort = true, srcW = 960, srcH = 540, align = 16)
        assertTrue(h <= 540)
        assertTrue(w <= 976)
    }

    @Test
    fun gifUsesLongEdge() {
        val (w, h) = OffscreenLoop.fit(2f, 480, edgeIsShort = false, srcW = 4000, srcH = 2000, align = 2)
        assertEquals(480, w)
        assertEquals(240, h)
    }
}
