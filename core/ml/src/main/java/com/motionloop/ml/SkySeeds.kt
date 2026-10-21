package com.motionloop.ml

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/**
 * Picks points that very likely lie in the sky, to seed the interactive segmenter.
 * Works on a small ARGB thumbnail. Pure Kotlin so it is unit tested.
 *
 * A cell scores high when it is in the upper part of the photo, bright, smooth (little texture)
 * and blue or near-neutral (overcast). Up to [MAX_SEEDS] well separated cells are returned.
 */
object SkySeeds {
    const val MAX_SEEDS = 3
    private const val GRID = 12
    private const val MIN_SCORE = 0.45f

    /** Normalized (x, y) seed points, best first. Empty when nothing looks like sky. */
    fun find(argb: IntArray, width: Int, height: Int): List<Pair<Float, Float>> {
        require(argb.size == width * height)
        val cellW = max(1, width / GRID)
        val cellH = max(1, height / GRID)
        val scored = ArrayList<Triple<Float, Int, Int>>()
        // Only the top 60% of rows can seed.
        val maxRow = (GRID * 0.6f).toInt()
        for (gy in 0 until maxRow) {
            for (gx in 0 until GRID) {
                val s = scoreCell(argb, width, height, gx * cellW, gy * cellH, cellW, cellH)
                val heightBonus = 1f - gy.toFloat() / maxRow * 0.4f
                scored += Triple(s * heightBonus, gx, gy)
            }
        }
        scored.sortByDescending { it.first }
        val seeds = ArrayList<Pair<Float, Float>>()
        for ((score, gx, gy) in scored) {
            if (score < MIN_SCORE || seeds.size == MAX_SEEDS) break
            val p = (gx + 0.5f) / GRID to (gy + 0.5f) / GRID
            // Keep seeds apart so they cover different parts of the sky.
            if (seeds.all { abs(it.first - p.first) + abs(it.second - p.second) > 0.25f }) seeds += p
        }
        return seeds
    }

    internal fun scoreCell(argb: IntArray, w: Int, h: Int, x0: Int, y0: Int, cw: Int, ch: Int): Float {
        var n = 0
        var sumL = 0f
        var sumL2 = 0f
        var sumBlue = 0f
        var sumSat = 0f
        for (y in y0 until min(h, y0 + ch)) {
            for (x in x0 until min(w, x0 + cw)) {
                val c = argb[y * w + x]
                val r = (c shr 16 and 255) / 255f
                val g = (c shr 8 and 255) / 255f
                val b = (c and 255) / 255f
                val l = 0.2126f * r + 0.7152f * g + 0.0722f * b
                sumL += l
                sumL2 += l * l
                sumBlue += b - max(r, g)
                sumSat += max(r, max(g, b)) - min(r, min(g, b))
                n++
            }
        }
        if (n == 0) return 0f
        val mean = sumL / n
        val std = sqrt(max(0f, sumL2 / n - mean * mean))
        val blueness = sumBlue / n
        val saturation = sumSat / n
        val smooth = (1f - std / 0.08f).coerceIn(0f, 1f)
        val bright = ((mean - 0.35f) / 0.4f).coerceIn(0f, 1f)
        // Blue sky, or a grey/white overcast sky (low saturation and bright).
        val skyColor = max(((blueness + 0.02f) / 0.15f).coerceIn(0f, 1f), (1f - saturation / 0.12f).coerceIn(0f, 1f) * bright)
        return smooth * (0.4f * bright + 0.6f * skyColor)
    }
}
