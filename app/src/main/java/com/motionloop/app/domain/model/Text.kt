package com.motionloop.app.domain.model

import kotlinx.serialization.Serializable

/** System font families only, so there is nothing to license. */
@Serializable
enum class TextFont(val label: String, val family: String, val bold: Boolean, val italic: Boolean) {
    SANS("Sans", "sans-serif", false, false),
    SANS_BOLD("Bold", "sans-serif", true, false),
    CONDENSED("Condensed", "sans-serif-condensed", true, false),
    SERIF("Serif", "serif", false, false),
    SERIF_ITALIC("Italic", "serif", false, true),
    MONO("Mono", "monospace", false, false),
    HAND("Script", "cursive", false, false),
}

/** Order matters: `text.frag` and TextPass switch on these. */
@Serializable
enum class TextAnimation(val label: String) {
    NONE("None"),
    SLIDE_IN("Slide in"),
    FADE("Fade"),
    TYPEWRITER("Typewriter"),
    GLOW_PULSE("Glow pulse"),
}

/**
 * One text layer (11e). Position is the center in normalized image space, size is the font size
 * as a fraction of image width. Animations run once per loop cycle, so they loop seamlessly.
 */
@Serializable
data class TextLayer(
    val id: String,
    val text: String = "Your text",
    val font: TextFont = TextFont.SANS_BOLD,
    val color: Int = 0xFFFFFFFF.toInt(),
    val size: Float = 0.12f,
    /** Degrees, clockwise. */
    val rotation: Float = 0f,
    /** Extra space between letters, in ems. */
    val letterSpacing: Float = 0f,
    val shadow: Boolean = false,
    val outline: Boolean = false,
    val outlineColor: Int = 0xFF000000.toInt(),
    val x: Float = 0.5f,
    val y: Float = 0.4f,
    val animation: TextAnimation = TextAnimation.NONE,
    /** Drawn behind the subject (needs a subject in Cutout). */
    val behindSubject: Boolean = true,
) {
    /** Everything that changes the rasterized look. Position, rotation and animation are GPU-only. */
    fun renderKey(): TextLayer = copy(x = 0f, y = 0f, rotation = 0f, behindSubject = false, animation = animation.rasterNeeds())

    companion object {
        const val MAX_LENGTH = 120
        const val MAX_LAYERS = 8
        const val MIN_SIZE = 0.03f
        const val MAX_SIZE = 0.4f
    }
}

/** Typewriter and glow need extra bitmaps, the others do not. */
private fun TextAnimation.rasterNeeds(): TextAnimation = when (this) {
    TextAnimation.TYPEWRITER, TextAnimation.GLOW_PULSE -> this
    else -> TextAnimation.NONE
}

/** Animation state at a loop phase. Pure, so the timing is unit tested. */
data class TextAnimState(
    val alpha: Float,
    /** Horizontal offset in image widths (slide). */
    val offsetX: Float,
    /** Share of characters shown (typewriter), 1 = all. */
    val reveal: Float,
    /** Glow strength 0..1. */
    val glow: Float,
)

object TextTiming {
    private const val IN_END = 0.25f
    private const val OUT_START = 0.85f

    fun at(animation: TextAnimation, phase: Float): TextAnimState {
        val p = phase - kotlin.math.floor(phase)
        // In over the first quarter of the cycle, hold, out over the last 15%. Periodic per cycle.
        val inT = smooth((p / IN_END).coerceIn(0f, 1f))
        val outT = smooth(((p - OUT_START) / (1f - OUT_START)).coerceIn(0f, 1f))
        return when (animation) {
            TextAnimation.NONE -> TextAnimState(1f, 0f, 1f, 0f)
            TextAnimation.FADE -> TextAnimState(inT * (1f - outT), 0f, 1f, 0f)
            TextAnimation.SLIDE_IN -> TextAnimState(inT * (1f - outT), -0.25f * (1f - inT), 1f, 0f)
            TextAnimation.TYPEWRITER -> TextAnimState(1f - outT, 0f, (p / 0.5f).coerceIn(0f, 1f), 0f)
            TextAnimation.GLOW_PULSE -> TextAnimState(1f, 0f, 1f, 0.5f - 0.5f * kotlin.math.cos(2f * Math.PI.toFloat() * p))
        }
    }

    private fun smooth(t: Float) = t * t * (3f - 2f * t)
}
