package com.motionloop.app

import com.motionloop.app.export.gif.GifEncoder
import com.motionloop.app.export.gif.GifQuantizer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import javax.imageio.ImageIO
import kotlin.random.Random

class GifEncoderTest {

    private fun palette() = IntArray(256) { i -> (i shl 16) or ((255 - i) shl 8) or (i * 7 and 255) }

    private fun decodeFirstFrame(bytes: ByteArray) =
        ImageIO.read(ByteArrayInputStream(bytes)) ?: error("ImageIO could not decode the GIF")

    @Test
    fun noisyFrameRoundTripsThroughImageIo() {
        // Random indices defeat LZW matching, so the table fills and resets many times.
        val w = 257
        val h = 131
        val pal = palette()
        val rnd = Random(7)
        val indices = ByteArray(w * h) { rnd.nextInt(256).toByte() }
        val out = ByteArrayOutputStream()
        GifEncoder(out, w, h, pal).apply {
            writeFrame(indices, 8)
            writeFrame(indices, 8)
            finish()
        }
        val img = decodeFirstFrame(out.toByteArray())
        assertEquals(w, img.width)
        assertEquals(h, img.height)
        for (y in 0 until h) {
            for (x in 0 until w) {
                val expected = pal[indices[y * w + x].toInt() and 255]
                assertEquals("pixel $x,$y", expected, img.getRGB(x, y) and 0xFFFFFF)
            }
        }
    }

    @Test
    fun flatFrameRoundTrips() {
        // Long runs exercise the dictionary growth path and code width increases.
        val w = 640
        val h = 480
        val pal = palette()
        val indices = ByteArray(w * h) { i -> ((i / 1000) % 256).toByte() }
        val out = ByteArrayOutputStream()
        GifEncoder(out, w, h, pal).apply {
            writeFrame(indices, 8)
            finish()
        }
        val img = decodeFirstFrame(out.toByteArray())
        for (i in indices.indices step 997) {
            val expected = pal[indices[i].toInt() and 255]
            assertEquals(expected, img.getRGB(i % w, i / w) and 0xFFFFFF)
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
                val diff = kotlin.math.abs((c shr shift and 255) - (p shr shift and 255))
                assertTrue("channel off by $diff", diff <= 12)
            }
        }
    }
}
