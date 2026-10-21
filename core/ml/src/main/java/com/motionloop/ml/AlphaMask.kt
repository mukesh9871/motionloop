package com.motionloop.ml

/**
 * Single-channel 8-bit mask, row 0 = image top. 255 = fully inside (sky, subject, near).
 * Size is independent of the photo: it is sampled with normalized coordinates.
 */
class AlphaMask(val width: Int, val height: Int, val data: ByteArray) {
    init {
        require(width > 0 && height > 0) { "Empty mask" }
        require(data.size == width * height) { "Mask data ${data.size} != $width x $height" }
    }

    operator fun get(x: Int, y: Int): Int = data[y * width + x].toInt() and 255

    /** Nearest sample at normalized coordinates. */
    fun sample(nx: Float, ny: Float): Int {
        val x = (nx * width).toInt().coerceIn(0, width - 1)
        val y = (ny * height).toInt().coerceIn(0, height - 1)
        return get(x, y)
    }

    /** Pixel-wise union (max) or difference (this * (1 - other)), resampling [other] to this size. */
    fun combine(other: AlphaMask, subtract: Boolean): AlphaMask {
        val out = ByteArray(data.size)
        for (y in 0 until height) {
            val oy = y * other.height / height
            for (x in 0 until width) {
                val ox = x * other.width / width
                val a = data[y * width + x].toInt() and 255
                val b = other.data[oy * other.width + ox].toInt() and 255
                val v = if (subtract) a * (255 - b) / 255 else maxOf(a, b)
                out[y * width + x] = v.toByte()
            }
        }
        return AlphaMask(width, height, out)
    }

    fun inverted(): AlphaMask = AlphaMask(width, height, ByteArray(data.size) { (255 - (data[it].toInt() and 255)).toByte() })

    /** Share of pixels above half, used to tell "nothing found" apart from a real result. */
    fun coverage(): Float = data.count { (it.toInt() and 255) >= 128 }.toFloat() / data.size

    companion object {
        fun fromFloats(width: Int, height: Int, values: FloatArray): AlphaMask =
            AlphaMask(width, height, ByteArray(values.size) { (values[it].coerceIn(0f, 1f) * 255f + 0.5f).toInt().toByte() })
    }
}
