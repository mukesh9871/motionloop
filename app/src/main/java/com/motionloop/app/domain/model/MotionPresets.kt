package com.motionloop.app.domain.model

import kotlin.math.hypot

/**
 * One-tap starting points: a small grid of arrows where that kind of motion usually is in a photo.
 * People then refine with their own paths, anchors and the freeze brush.
 */
enum class MotionPreset(val label: String) {
    /** Falling water down the middle. */
    WATERFALL("Waterfall"),

    /** Water flowing sideways along the bottom. */
    RIVER("River"),

    /** Clouds drifting along the top. */
    CLOUDS("Clouds"),

    /** Smoke, steam or flames rising in the middle. */
    RISE("Rise"),
}

object MotionPresets {

    /** Region in normalized image space, direction in width units, and travel per cycle in widths. */
    private data class Spec(
        val left: Float,
        val top: Float,
        val right: Float,
        val bottom: Float,
        val dx: Float,
        val dy: Float,
        val length: Float,
        val cols: Int,
        val rows: Int,
    )

    private fun spec(preset: MotionPreset) = when (preset) {
        MotionPreset.WATERFALL -> Spec(0.36f, 0.18f, 0.64f, 0.72f, 0f, 1f, 0.14f, cols = 2, rows = 3)
        MotionPreset.RIVER -> Spec(0.12f, 0.66f, 0.76f, 0.9f, 1f, 0f, 0.1f, cols = 3, rows = 2)
        MotionPreset.CLOUDS -> Spec(0.08f, 0.08f, 0.78f, 0.34f, 1f, 0f, 0.07f, cols = 3, rows = 2)
        MotionPreset.RISE -> Spec(0.38f, 0.34f, 0.62f, 0.8f, 0f, -1f, 0.1f, cols = 2, rows = 3)
    }

    /**
     * Straight arrows for [preset] on a photo of [aspect] (width / height). Lengths are measured in
     * image widths, so the motion has the same speed on any photo shape. Every point stays in [0,1].
     */
    fun arrows(preset: MotionPreset, aspect: Float, newId: () -> String): List<MotionArrow> {
        val s = spec(preset)
        val norm = hypot(s.dx, s.dy)
        // Width units to normalized units: x as is, y scaled by width / height.
        val stepX = s.dx / norm * s.length
        val stepY = s.dy / norm * s.length * aspect
        val arrows = ArrayList<MotionArrow>(s.cols * s.rows)
        for (row in 0 until s.rows) {
            for (col in 0 until s.cols) {
                val x = lerp(s.left, s.right, (col + 0.5f) / s.cols)
                val y = lerp(s.top, s.bottom, (row + 0.5f) / s.rows)
                // Centered on the grid point, then kept inside the photo.
                val start = NPoint((x - stepX / 2).coerceIn(0f, 1f), (y - stepY / 2).coerceIn(0f, 1f))
                val end = NPoint((x + stepX / 2).coerceIn(0f, 1f), (y + stepY / 2).coerceIn(0f, 1f))
                arrows += MotionArrow(newId(), start, end)
            }
        }
        return arrows
    }

    private fun lerp(a: Float, b: Float, t: Float) = a + (b - a) * t
}
