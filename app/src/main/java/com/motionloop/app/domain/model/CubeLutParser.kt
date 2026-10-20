package com.motionloop.app.domain.model

import java.io.Reader
import kotlin.math.roundToInt

/**
 * Parses Adobe / Resolve `.cube` 3D LUT files into [LutData].
 * Supports LUT_3D_SIZE, DOMAIN_MIN / DOMAIN_MAX, comments and TITLE. 1D LUTs are rejected.
 * Large LUTs are resampled to [LutData.DEFAULT_SIZE] or less so GPU memory stays small.
 */
object CubeLutParser {

    class CubeFormatException(message: String) : Exception(message)

    /** Real files go up to 65. Anything above is refused before allocating (129^3 floats is ~26 MB). */
    const val MAX_SOURCE_SIZE = 129

    private val WHITESPACE = Regex("\\s+")

    fun parse(reader: Reader): LutData {
        var size = 0
        var domainMin = floatArrayOf(0f, 0f, 0f)
        var domainMax = floatArrayOf(1f, 1f, 1f)
        var values: FloatArray? = null
        var count = 0

        reader.buffered().useLines { lines ->
            for (raw in lines) {
                val line = raw.substringBefore('#').trim()
                if (line.isEmpty()) continue
                val parts = line.split(WHITESPACE)
                when (parts[0].uppercase()) {
                    "TITLE" -> Unit
                    "LUT_1D_SIZE" -> throw CubeFormatException("1D LUTs are not supported")
                    "LUT_3D_SIZE" -> {
                        size = parts.getOrNull(1)?.toIntOrNull() ?: throw CubeFormatException("Bad LUT_3D_SIZE")
                        if (size !in 2..MAX_SOURCE_SIZE) throw CubeFormatException("LUT_3D_SIZE $size out of range")
                        values = FloatArray(size * size * size * 3)
                    }
                    "DOMAIN_MIN" -> domainMin = triple(parts)
                    "DOMAIN_MAX" -> domainMax = triple(parts)
                    else -> {
                        // Other keywords (LUT_3D_INPUT_RANGE, vendor tags) are skipped. Data lines start with a number.
                        if (parts[0][0].isLetter()) continue
                        val v = values ?: throw CubeFormatException("Data before LUT_3D_SIZE")
                        if (parts.size < 3) throw CubeFormatException("Bad data line: $line")
                        if (count >= v.size) throw CubeFormatException("Too many data lines")
                        for (k in 0..2) {
                            v[count++] = parts[k].toFloatOrNull() ?: throw CubeFormatException("Bad number: ${parts[k]}")
                        }
                    }
                }
            }
        }
        val v = values ?: throw CubeFormatException("Missing LUT_3D_SIZE")
        if (count != v.size) throw CubeFormatException("Expected ${v.size / 3} entries, found ${count / 3}")

        // Normalize to 0..1 using the declared domain.
        for (i in v.indices) {
            val k = i % 3
            val range = domainMax[k] - domainMin[k]
            v[i] = if (range > 0f) (v[i] - domainMin[k]) / range else v[i]
        }

        val outSize = minOf(size, LutData.DEFAULT_SIZE)
        if (outSize == size) {
            return LutData(size, ByteArray(v.size) { i -> (v[i].coerceIn(0f, 1f) * 255f).roundToInt().toByte() })
        }
        // Downsample with trilinear sampling of the source grid.
        return LutData.bake(outSize) { c -> sample(v, size, c[0], c[1], c[2]) }
    }

    private fun triple(parts: List<String>): FloatArray {
        if (parts.size < 4) throw CubeFormatException("Bad domain line")
        return FloatArray(3) { parts[it + 1].toFloatOrNull() ?: throw CubeFormatException("Bad domain value") }
    }

    private fun sample(v: FloatArray, n: Int, r: Float, g: Float, b: Float): FloatArray {
        fun split(x: Float): Pair<Int, Float> {
            val p = x.coerceIn(0f, 1f) * (n - 1)
            val i = minOf(p.toInt(), n - 2)
            return i to (p - i)
        }
        val (ri, rf) = split(r)
        val (gi, gf) = split(g)
        val (bi, bf) = split(b)
        val out = FloatArray(3)
        for (dz in 0..1) for (dy in 0..1) for (dx in 0..1) {
            val w = (if (dx == 0) 1 - rf else rf) * (if (dy == 0) 1 - gf else gf) * (if (dz == 0) 1 - bf else bf)
            val base = (((bi + dz) * n + (gi + dy)) * n + (ri + dx)) * 3
            for (k in 0..2) out[k] += v[base + k] * w
        }
        return out
    }
}
