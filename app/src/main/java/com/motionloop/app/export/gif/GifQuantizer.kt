package com.motionloop.app.export.gif

/**
 * One global 256-color palette for the whole loop (no palette flicker between frames).
 * Colors are bucketed to 15 bits (5 per channel), split by median cut,
 * then every bucket maps to its nearest palette entry through a 32K lookup table.
 */
class GifQuantizer private constructor(val palette: IntArray) {

    private val lut = ByteArray(BUCKETS)

    init {
        for (key in 0 until BUCKETS) {
            val r = ((key shr 10) and 31) shl 3 or 4
            val g = ((key shr 5) and 31) shl 3 or 4
            val b = (key and 31) shl 3 or 4
            var best = 0
            var bestDist = Int.MAX_VALUE
            for (i in palette.indices) {
                val c = palette[i]
                val dr = r - (c shr 16 and 255)
                val dg = g - (c shr 8 and 255)
                val db = b - (c and 255)
                val d = dr * dr + dg * dg + db * db
                if (d < bestDist) {
                    bestDist = d
                    best = i
                }
            }
            lut[key] = best.toByte()
        }
    }

    /** RGBA bytes (row 0 = top) to palette indices, with 4x4 ordered dithering against banding. */
    fun map(rgba: ByteArray, width: Int, height: Int, out: ByteArray) {
        var p = 0
        for (y in 0 until height) {
            for (x in 0 until width) {
                // Bayer offset in [-4, 3.5], about half a 5-bit step.
                val d = BAYER[(y and 3) * 4 + (x and 3)] / 2 - 4
                val r = clamp((rgba[p].toInt() and 255) + d)
                val g = clamp((rgba[p + 1].toInt() and 255) + d)
                val b = clamp((rgba[p + 2].toInt() and 255) + d)
                out[y * width + x] = lut[(r shr 3 shl 10) or (g shr 3 shl 5) or (b shr 3)]
                p += 4
            }
        }
    }

    class Builder {
        private val histogram = IntArray(BUCKETS)

        fun add(rgba: ByteArray) {
            var p = 0
            while (p + 3 < rgba.size) {
                histogram[key(rgba[p].toInt() and 255, rgba[p + 1].toInt() and 255, rgba[p + 2].toInt() and 255)]++
                p += 4
            }
        }

        fun build(): GifQuantizer = GifQuantizer(medianCut(histogram, PALETTE_SIZE))
    }

    companion object {
        const val PALETTE_SIZE = 256
        private const val BUCKETS = 1 shl 15
        private val BAYER = intArrayOf(0, 8, 2, 10, 12, 4, 14, 6, 3, 11, 1, 9, 15, 7, 13, 5)

        private fun clamp(v: Int) = if (v < 0) 0 else if (v > 255) 255 else v
        private fun key(r: Int, g: Int, b: Int) = (r shr 3 shl 10) or (g shr 3 shl 5) or (b shr 3)
        private fun channel(key: Int, c: Int) = when (c) {
            0 -> (key shr 10) and 31
            1 -> (key shr 5) and 31
            else -> key and 31
        }

        /** Returns exactly [size] RGB colors (unused slots are black). Internal for tests. */
        internal fun medianCut(histogram: IntArray, size: Int): IntArray {
            val keys = histogram.indices.filter { histogram[it] > 0 }
            val boxes = ArrayList<List<Int>>()
            if (keys.isNotEmpty()) boxes.add(keys)

            while (boxes.size < size) {
                // Split the box with the most pixels times widest channel range.
                var bestIdx = -1
                var bestScore = 0L
                var bestChannel = 0
                for ((i, box) in boxes.withIndex()) {
                    if (box.size < 2) continue
                    var widest = 0
                    var widestChannel = 0
                    for (c in 0..2) {
                        var lo = 31
                        var hi = 0
                        for (k in box) {
                            val v = channel(k, c)
                            if (v < lo) lo = v
                            if (v > hi) hi = v
                        }
                        if (hi - lo > widest) {
                            widest = hi - lo
                            widestChannel = c
                        }
                    }
                    val count = box.sumOf { histogram[it].toLong() }
                    val score = count * (widest + 1)
                    if (score > bestScore) {
                        bestScore = score
                        bestIdx = i
                        bestChannel = widestChannel
                    }
                }
                if (bestIdx < 0) break

                val sorted = boxes[bestIdx].sortedBy { channel(it, bestChannel) }
                val total = sorted.sumOf { histogram[it].toLong() }
                var acc = 0L
                var cut = 1
                for (i in sorted.indices) {
                    acc += histogram[sorted[i]]
                    if (acc * 2 >= total) {
                        cut = (i + 1).coerceIn(1, sorted.size - 1)
                        break
                    }
                }
                boxes[bestIdx] = sorted.subList(0, cut)
                boxes.add(sorted.subList(cut, sorted.size))
            }

            val palette = IntArray(size)
            for ((i, box) in boxes.withIndex()) {
                var n = 0L
                var r = 0L
                var g = 0L
                var b = 0L
                for (k in box) {
                    val w = histogram[k].toLong()
                    n += w
                    r += w * (channel(k, 0) shl 3 or 4)
                    g += w * (channel(k, 1) shl 3 or 4)
                    b += w * (channel(k, 2) shl 3 or 4)
                }
                palette[i] = ((r / n).toInt() shl 16) or ((g / n).toInt() shl 8) or (b / n).toInt()
            }
            return palette
        }
    }
}
