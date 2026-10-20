package com.motionloop.app

import com.motionloop.app.export.gif.GifEncoder
import com.motionloop.app.export.gif.GifQuantizer
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayOutputStream
import kotlin.math.abs
import kotlin.random.Random

class GifEncoderTest {

    private fun palette() = IntArray(256) { i -> (i shl 16) or ((255 - i) shl 8) or (i * 7 and 255) }

    private fun encode(w: Int, h: Int, pal: IntArray, vararg frames: ByteArray): ByteArray {
        val out = ByteArrayOutputStream()
        GifEncoder(out, w, h, pal).apply {
            frames.forEach { writeFrame(it, 8) }
            finish()
        }
        return out.toByteArray()
    }

    @Test
    fun noisyFramesRoundTrip() {
        // Random indices defeat LZW matching, so the table fills and resets many times.
        val w = 257
        val h = 131
        val rnd = Random(7)
        val a = ByteArray(w * h) { rnd.nextInt(256).toByte() }
        val b = ByteArray(w * h) { rnd.nextInt(256).toByte() }
        val pal = palette()
        val gif = GifTestDecoder(encode(w, h, pal, a, b))
        assertEquals(w, gif.width)
        assertEquals(h, gif.height)
        assertTrue(gif.loops)
        assertArrayEquals(pal, gif.palette)
        assertEquals(listOf(8, 8), gif.delays)
        assertEquals(2, gif.frames.size)
        assertArrayEquals(a, gif.frames[0])
        assertArrayEquals(b, gif.frames[1])
    }

    @Test
    fun runsAndGradientsRoundTrip() {
        // Long runs grow the dictionary through every code width up to 12 bits.
        val w = 640
        val h = 480
        val runs = ByteArray(w * h) { i -> ((i / 1000) % 256).toByte() }
        val ramp = ByteArray(w * h) { i -> ((i % w) * 256 / w).toByte() }
        val gif = GifTestDecoder(encode(w, h, palette(), runs, ramp))
        assertArrayEquals(runs, gif.frames[0])
        assertArrayEquals(ramp, gif.frames[1])
    }

    @Test
    fun tinyFramesRoundTrip() {
        for (n in 1..5) {
            val data = ByteArray(n) { (it * 3).toByte() }
            val gif = GifTestDecoder(encode(n, 1, palette(), data))
            assertArrayEquals(data, gif.frames[0])
        }
    }

    @Test
    fun quantizerKeepsDistinctColors() {
        // Four flat colors must survive palette building and mapping almost exactly.
        val colors = intArrayOf(0xFF0000, 0x00FF00, 0x0000FF, 0xFFFFFF)
        val w = 64
        val h = 64
        val rgba = ByteArray(w * h * 4)
        for (i in 0 until w * h) {
            val c = colors[(i / w) * 4 / h]
            rgba[i * 4] = (c shr 16).toByte()
            rgba[i * 4 + 1] = (c shr 8).toByte()
            rgba[i * 4 + 2] = c.toByte()
            rgba[i * 4 + 3] = -1
        }
        val q = GifQuantizer.Builder().apply { add(rgba) }.build()
        assertEquals(256, q.palette.size)
        val idx = ByteArray(w * h)
        q.map(rgba, w, h, idx)
        for (i in 0 until w * h) {
            val c = colors[(i / w) * 4 / h]
            val p = q.palette[idx[i].toInt() and 255]
            for (shift in intArrayOf(16, 8, 0)) {
                val diff = abs((c shr shift and 255) - (p shr shift and 255))
                assertTrue("channel off by $diff", diff <= 12)
            }
        }
    }
}
