package com.motionloop.ml

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MlLogicTest {

    private fun rgb(r: Int, g: Int, b: Int) = (0xFF shl 24) or (r shl 16) or (g shl 8) or b

    /** Top half smooth blue sky, bottom half noisy dark ground. */
    private fun landscape(w: Int, h: Int): IntArray = IntArray(w * h) { i ->
        val x = i % w
        val y = i / w
        if (y < h / 2) {
            rgb(110, 160, 230)
        } else {
            val v = 40 + ((x * 37 + y * 91) % 60)
            rgb(v, v + 10, v / 2)
        }
    }

    @Test
    fun seedsLandInTheSky() {
        val seeds = SkySeeds.find(landscape(96, 72), 96, 72)
        assertTrue("expected at least one seed", seeds.isNotEmpty())
        assertTrue(seeds.size <= SkySeeds.MAX_SEEDS)
        for ((_, y) in seeds) assertTrue("seed y $y should be in the top half", y < 0.5f)
    }

    @Test
    fun noSeedsWithoutSky() {
        // Busy, dark texture everywhere: nothing should look like sky.
        val w = 96
        val h = 72
        val pixels = IntArray(w * h) { i ->
            val v = 20 + ((i * 7919) % 90)
            rgb(v, v / 2, v / 3)
        }
        assertTrue(SkySeeds.find(pixels, w, h).isEmpty())
    }

    @Test
    fun overcastSkyIsFound() {
        val w = 96
        val h = 72
        val pixels = IntArray(w * h) { i -> if (i / w < h / 2) rgb(215, 218, 222) else rgb(60, 50, 40 + (i % 50)) }
        assertTrue(SkySeeds.find(pixels, w, h).isNotEmpty())
    }

    @Test
    fun combineUnionAndSubtract() {
        val a = AlphaMask(2, 1, byteArrayOf(0, 255.toByte()))
        val b = AlphaMask(2, 1, byteArrayOf(255.toByte(), 255.toByte()))
        val union = a.combine(b, subtract = false)
        assertEquals(255, union[0, 0])
        assertEquals(255, union[1, 0])
        val diff = b.combine(a, subtract = true)
        assertEquals(255, diff[0, 0])
        assertEquals(0, diff[1, 0])
    }

    @Test
    fun combineResamplesOtherSize() {
        val big = AlphaMask(4, 4, ByteArray(16))
        val small = AlphaMask(2, 2, byteArrayOf(255.toByte(), 0, 0, 0))
        val out = big.combine(small, subtract = false)
        // Top-left quarter comes from the small mask's top-left pixel.
        assertEquals(255, out[0, 0])
        assertEquals(255, out[1, 1])
        assertEquals(0, out[2, 2])
        assertEquals(0.25f, out.coverage(), 1e-6f)
    }

    @Test
    fun depthNormalizeSpansZeroToOne() {
        val v = floatArrayOf(3f, 5f, 4f)
        DepthEstimator.normalize(v)
        assertEquals(0f, v[0], 1e-6f)
        assertEquals(1f, v[1], 1e-6f)
        assertEquals(0.5f, v[2], 1e-6f)
        val flat = floatArrayOf(2f, 2f)
        DepthEstimator.normalize(flat)
        assertEquals(0.5f, flat[0], 1e-6f)
    }
}
