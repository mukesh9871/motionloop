package com.motionloop.app

import com.motionloop.app.domain.model.Adjustments
import com.motionloop.app.domain.model.ColorGrades
import com.motionloop.app.domain.model.CubeLutParser
import com.motionloop.app.domain.model.LutData
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ColorGradeTest {

    /** Identity .cube of [n]^3 entries, red fastest. */
    private fun identityCube(n: Int, header: String = "") = buildString {
        append("# test\nTITLE \"identity\"\n")
        append(header)
        append("LUT_3D_SIZE $n\n")
        for (b in 0 until n) for (g in 0 until n) for (r in 0 until n) {
            append("${r / (n - 1f)} ${g / (n - 1f)} ${b / (n - 1f)}\n")
        }
    }

    @Test
    fun parsesIdentityCube() {
        val lut = CubeLutParser.parse(identityCube(17).reader())
        assertEquals(17, lut.size)
        val probe = floatArrayOf(0.25f, 0.5f, 0.75f)
        val out = lut.lookup(probe[0], probe[1], probe[2])
        assertTrue(ColorGrades.maxDiff(probe, out) < 0.04f)
    }

    @Test
    fun appliesDomain() {
        // Values written in 0..2 with DOMAIN_MAX 2 must normalize back to 0..1.
        val text = buildString {
            append("DOMAIN_MIN 0 0 0\nDOMAIN_MAX 2 2 2\nLUT_3D_SIZE 2\n")
            for (b in 0..1) for (g in 0..1) for (r in 0..1) append("${r * 2} ${g * 2} ${b * 2}\n")
        }
        val lut = CubeLutParser.parse(text.reader())
        val white = lut.lookup(1f, 1f, 1f)
        assertEquals(1f, white[0], 0.01f)
        assertEquals(1f, white[2], 0.01f)
    }

    @Test
    fun downsamplesLargeCubes() {
        val lut = CubeLutParser.parse(identityCube(65).reader())
        assertEquals(LutData.DEFAULT_SIZE, lut.size)
        val probe = floatArrayOf(0.1f, 0.6f, 0.9f)
        assertTrue(ColorGrades.maxDiff(probe, lut.lookup(probe[0], probe[1], probe[2])) < 0.04f)
    }

    @Test
    fun rejectsBadFiles() {
        val bad = listOf(
            "LUT_1D_SIZE 16\n0 0 0\n",
            "0 0 0\n",
            "LUT_3D_SIZE 2\n0 0 0\n",
            "LUT_3D_SIZE 2\n" + "0 0 x\n".repeat(8),
            "LUT_3D_SIZE 1\n0 0 0\n",
        )
        for (text in bad) {
            var threw = false
            try {
                CubeLutParser.parse(text.reader())
            } catch (e: CubeLutParser.CubeFormatException) {
                threw = true
            }
            assertTrue("should reject: ${text.take(20)}", threw)
        }
    }

    @Test
    fun builtInGradesStayInRangeAndAreDistinct() {
        val ids = ColorGrades.all.map { it.id }
        assertEquals(ids.size, ids.toSet().size)
        assertFalse(Adjustments.CUSTOM_LUT_ID in ids)
        for (grade in ColorGrades.all) {
            val lut = LutData.bake(9, grade.transform)
            assertEquals(9 * 9 * 9 * 3, lut.rgb.size)
            // Not an identity: each grade must actually change some color.
            val mid = floatArrayOf(0.5f, 0.4f, 0.3f)
            val out = grade.transform(mid.copyOf())
            assertTrue("${grade.id} changes color", ColorGrades.maxDiff(mid, out) > 0.005f)
        }
    }

    @Test
    fun noirIsGreyscale() {
        val noir = ColorGrades.byId("noir")!!
        val out = noir.transform(floatArrayOf(0.8f, 0.2f, 0.4f))
        assertEquals(out[0], out[1], 1e-6f)
        assertEquals(out[1], out[2], 1e-6f)
    }

    @Test
    fun identityAdjustmentsSkipColorPass() {
        assertTrue(Adjustments().isIdentity)
        assertTrue(Adjustments(gradeId = "noir", gradeIntensity = 0f).isIdentity)
        assertFalse(Adjustments(contrast = 0.1f).isIdentity)
        assertFalse(Adjustments(gradeId = "noir").isIdentity)
    }
}
