package com.motionloop.app.domain.model

import kotlinx.serialization.Serializable
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

/** Per-project color settings. All zero / null means "no color pass at all". */
@Serializable
data class Adjustments(
    /** -1..1, added in linear-ish space. */
    val brightness: Float = 0f,
    /** -1..1, scales around mid grey. */
    val contrast: Float = 0f,
    /** -1..1, -1 is greyscale. */
    val saturation: Float = 0f,
    /** -1..1, cool to warm white balance shift. */
    val warmth: Float = 0f,
    /** Built-in grade id ([ColorGrades]) or [CUSTOM_LUT_ID] for an imported .cube file. */
    val gradeId: String? = null,
    /** 0..1 blend of the graded color over the adjusted color. */
    val gradeIntensity: Float = 1f,
) {
    val isIdentity: Boolean
        get() = brightness == 0f && contrast == 0f && saturation == 0f && warmth == 0f &&
            (gradeId == null || gradeIntensity == 0f)

    companion object {
        const val CUSTOM_LUT_ID = "custom"
    }
}

/** A 3D color lookup table: [size]^3 RGB entries, red fastest, then green, then blue (the .cube order). */
class LutData(val size: Int, val rgb: ByteArray) {
    init {
        require(size in 2..65) { "LUT size $size out of range" }
        require(rgb.size == size * size * size * 3) { "LUT data has ${rgb.size} bytes, expected ${size * size * size * 3}" }
    }

    /** Nearest-entry lookup, for tests and CPU previews. Inputs and outputs in 0..1. */
    fun lookup(r: Float, g: Float, b: Float): FloatArray {
        fun idx(v: Float) = (v.coerceIn(0f, 1f) * (size - 1) + 0.5f).toInt()
        val i = ((idx(b) * size + idx(g)) * size + idx(r)) * 3
        return floatArrayOf(
            (rgb[i].toInt() and 255) / 255f,
            (rgb[i + 1].toInt() and 255) / 255f,
            (rgb[i + 2].toInt() and 255) / 255f,
        )
    }

    companion object {
        const val DEFAULT_SIZE = 33

        /** Bakes a color function into a LUT. */
        fun bake(size: Int = DEFAULT_SIZE, f: (FloatArray) -> FloatArray): LutData {
            val data = ByteArray(size * size * size * 3)
            val c = FloatArray(3)
            var o = 0
            for (b in 0 until size) for (g in 0 until size) for (r in 0 until size) {
                c[0] = r / (size - 1f)
                c[1] = g / (size - 1f)
                c[2] = b / (size - 1f)
                val out = f(c.copyOf())
                for (k in 0..2) data[o++] = (out[k].coerceIn(0f, 1f) * 255f + 0.5f).toInt().toByte()
            }
            return LutData(size, data)
        }
    }
}

data class ColorGrade(val id: String, val name: String, val transform: (FloatArray) -> FloatArray)

/**
 * MotionLoop's own built-in grades, defined as math so there are no third-party LUT files.
 * Each takes and returns RGB in 0..1.
 */
object ColorGrades {

    private fun luma(c: FloatArray) = 0.2126f * c[0] + 0.7152f * c[1] + 0.0722f * c[2]
    private fun mix(a: Float, b: Float, t: Float) = a + (b - a) * t
    private fun smooth(x: Float): Float {
        val t = x.coerceIn(0f, 1f)
        return t * t * (3f - 2f * t)
    }

    private fun saturate(c: FloatArray, amount: Float): FloatArray {
        val l = luma(c)
        return floatArrayOf(mix(l, c[0], amount), mix(l, c[1], amount), mix(l, c[2], amount))
    }

    private fun sCurve(v: Float, strength: Float) = mix(v, smooth(v), strength)

    val all: List<ColorGrade> = listOf(
        ColorGrade("golden", "Golden") { c ->
            // Warm, with a little extra red lifted into the shadows.
            val lift = 0.05f * (1f - luma(c))
            floatArrayOf(
                sCurve(c[0] * 1.06f + 0.02f + lift, 0.3f),
                sCurve(c[1] + 0.01f, 0.3f),
                sCurve(c[2] * 0.86f, 0.3f),
            )
        },
        ColorGrade("tide", "Tide") { c ->
            // Cool shadows, slightly warm highlights.
            val l = luma(c)
            val shadow = 1f - smooth(l * 1.6f)
            floatArrayOf(c[0] - 0.05f * shadow + 0.03f * l, c[1] + 0.02f * shadow, c[2] + 0.09f * shadow)
        },
        ColorGrade("ember", "Ember") { c ->
            // Teal shadows, orange skin and highlights.
            val l = luma(c)
            val s = 1f - smooth(l * 1.4f)
            val h = smooth((l - 0.35f) * 1.6f)
            saturate(
                floatArrayOf(
                    sCurve(c[0] - 0.06f * s + 0.08f * h, 0.35f),
                    sCurve(c[1] + 0.03f * s + 0.02f * h, 0.35f),
                    sCurve(c[2] + 0.07f * s - 0.08f * h, 0.35f),
                ),
                1.1f,
            )
        },
        ColorGrade("haze", "Haze") { c ->
            // Lifted blacks, softened whites, gentle desaturation.
            saturate(FloatArray(3) { i -> 0.08f + c[i] * 0.84f }, 0.82f)
        },
        ColorGrade("noir", "Noir") { c ->
            val l = sCurve(luma(c), 0.6f)
            floatArrayOf(l, l, l)
        },
        ColorGrade("bloom", "Bloom") { c ->
            // Vivid, punchy color.
            saturate(FloatArray(3) { i -> sCurve(c[i], 0.4f) }, 1.3f)
        },
        ColorGrade("dusk", "Dusk") { c ->
            // Magenta-violet cast that grows towards highlights.
            val l = luma(c)
            floatArrayOf(c[0] + 0.06f * l, c[1] - 0.03f * l, c[2] + 0.08f * l)
        },
        ColorGrade("frost", "Frost") { c ->
            val cool = floatArrayOf(c[0] * 0.92f, c[1] * 0.99f, min(1f, c[2] * 1.08f + 0.02f))
            saturate(cool, 0.75f).map { v -> v.pow(0.95f) }.toFloatArray()
        },
    )

    fun byId(id: String): ColorGrade? = all.firstOrNull { it.id == id }

    /** Largest per-channel difference between two colors, used by tests. */
    fun maxDiff(a: FloatArray, b: FloatArray) = max(abs(a[0] - b[0]), max(abs(a[1] - b[1]), abs(a[2] - b[2])))
}
