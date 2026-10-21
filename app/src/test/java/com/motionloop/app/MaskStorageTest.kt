package com.motionloop.app

import com.motionloop.app.data.project.ProjectAssetRepositoryImpl
import com.motionloop.app.domain.model.MaskCodec
import com.motionloop.app.domain.model.Project
import com.motionloop.app.domain.model.SkySettings
import com.motionloop.app.domain.model.SkyStyle
import com.motionloop.ml.AlphaMask
import kotlinx.serialization.json.Json
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.IOException
import kotlin.random.Random

class MaskStorageTest {

    @Test
    fun maskRoundTrips() {
        val rnd = Random(3)
        val mask = AlphaMask(123, 77, ByteArray(123 * 77) { rnd.nextInt(256).toByte() })
        val out = ByteArrayOutputStream()
        MaskCodec.write(mask, out)
        val back = MaskCodec.read(ByteArrayInputStream(out.toByteArray()))
        assertEquals(123, back.width)
        assertEquals(77, back.height)
        assertArrayEquals(mask.data, back.data)
    }

    @Test
    fun rejectsBadHeadersAndAcceptsEdgeSizes() {
        fun header(magic: Int, version: Int, w: Int, h: Int): ByteArray {
            val out = ByteArrayOutputStream()
            java.io.DataOutputStream(out).apply {
                writeInt(magic); writeInt(version); writeInt(w); writeInt(h); flush()
            }
            return out.toByteArray()
        }
        val magic = 0x4D4C4D4B
        for (bad in listOf(header(magic, 1, 0, 5), header(magic, 1, 8193, 1), header(magic, 2, 4, 4), header(0x12345678, 1, 4, 4))) {
            try {
                MaskCodec.read(ByteArrayInputStream(bad))
                assertTrue("accepted a bad header", false)
            } catch (expected: IOException) {
                // The size guard stops a corrupt file from asking for a huge allocation.
            }
        }
        for ((w, h) in listOf(1 to 1, 8192 to 1, 3 to 8192)) {
            val mask = AlphaMask(w, h, ByteArray(w * h) { (it % 251).toByte() })
            val out = ByteArrayOutputStream()
            MaskCodec.write(mask, out)
            val back = MaskCodec.read(ByteArrayInputStream(out.toByteArray()))
            assertEquals(w, back.width)
            assertEquals(h, back.height)
            assertArrayEquals(mask.data, back.data)
        }
    }

    @Test
    fun flatMasksCompress() {
        val mask = AlphaMask(512, 512, ByteArray(512 * 512) { if (it < 512 * 200) -1 else 0 })
        val out = ByteArrayOutputStream()
        MaskCodec.write(mask, out)
        assertTrue("size ${out.size()}", out.size() < 4096)
    }

    @Test(expected = IOException::class)
    fun rejectsOtherFiles() {
        MaskCodec.read(ByteArrayInputStream("not a mask at all".toByteArray()))
    }

    @Test(expected = IOException::class)
    fun rejectsTruncatedFiles() {
        val out = ByteArrayOutputStream()
        MaskCodec.write(AlphaMask(64, 64, ByteArray(64 * 64) { (it * 31).toByte() }), out)
        val bytes = out.toByteArray()
        MaskCodec.read(ByteArrayInputStream(bytes.copyOf(bytes.size / 2)))
    }

    @Test
    fun revisionNamesParse() {
        assertEquals(12, ProjectAssetRepositoryImpl.revOf("sky_12.mask", "sky", ".mask"))
        assertNull(ProjectAssetRepositoryImpl.revOf("sky_photo_3.jpg", "sky", ".mask"))
        assertNull(ProjectAssetRepositoryImpl.revOf("sky_photo_3.mask", "sky", ".mask"))
        assertNull(ProjectAssetRepositoryImpl.revOf("sky_0.mask", "sky", ".mask"))
        assertNull(ProjectAssetRepositoryImpl.revOf("subject_2.mask", "sky", ".mask"))
        assertEquals(3, ProjectAssetRepositoryImpl.revOf("sky_photo_3.jpg", "sky_photo", ".jpg"))
    }

    @Test
    fun skyActiveRules() {
        assertFalse(SkySettings().isActive)
        assertFalse(SkySettings(enabled = true).isActive) // no area yet
        assertTrue(SkySettings(enabled = true, maskRev = 2).isActive)
        assertFalse(SkySettings(enabled = true, maskRev = 2, style = SkyStyle.PHOTO).isActive) // no photo
        assertTrue(SkySettings(enabled = true, maskRev = 2, style = SkyStyle.PHOTO, photoRev = 1).isActive)
    }

    @Test
    fun oldProjectsLoadWithDefaultSky() {
        val json = Json { ignoreUnknownKeys = true }
        val p = json.decodeFromString<Project>("""{"id":"a","createdAt":1,"updatedAt":2,"imageWidth":10,"imageHeight":20}""")
        assertEquals(SkySettings(), p.sky)
    }
}
