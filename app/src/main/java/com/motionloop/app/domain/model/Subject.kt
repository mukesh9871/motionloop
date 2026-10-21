package com.motionloop.app.domain.model

import kotlinx.serialization.Serializable

/**
 * The main subject's area, shared by background replacement (11d) and text behind the subject (11e).
 * A model mask ([MaskKind.SUBJECT] revision) refined by brush strokes:
 * [MaskMode.FREEZE] strokes restore subject, [MaskMode.UNFREEZE] strokes erase it.
 */
@Serializable
data class SubjectSettings(
    /** Revision of the stored model mask, 0 = none yet. */
    val maskRev: Int = 0,
    val strokes: List<MaskStroke> = emptyList(),
    /** 0..1 softness of the subject edge. */
    val feather: Float = 0.25f,
) {
    val hasArea: Boolean get() = maskRev != 0 || strokes.isNotEmpty()
}

/** Order matters: `source.frag` switches on the ordinal. */
@Serializable
enum class BackgroundMode { NONE, TRANSPARENT, SOLID, GRADIENT, BLUR, PHOTO }

/** What replaces everything outside the subject. Applied before the warp, so arrows and overlays act on it. */
@Serializable
data class BackgroundSettings(
    val mode: BackgroundMode = BackgroundMode.NONE,
    /** ARGB colors for SOLID (first) and GRADIENT (top to bottom). */
    val color1: Int = 0xFF1B1F3B.toInt(),
    val color2: Int = 0xFF7C5CFF.toInt(),
    /** 0..1 strength for BLUR. */
    val blur: Float = 0.7f,
    /** Revision of the imported background photo, 0 = none. */
    val photoRev: Int = 0,
) {
    /** True when the background is replaced, given that the subject has an area. */
    fun isActive(subject: SubjectSettings): Boolean =
        mode != BackgroundMode.NONE && subject.hasArea && (mode != BackgroundMode.PHOTO || photoRev != 0)

    companion object {
        /** Preset swatches offered in the editor. */
        val SWATCHES: List<Int> = listOf(
            0xFF000000, 0xFFFFFFFF, 0xFF1B1F3B, 0xFF7C5CFF, 0xFF2EE6C5, 0xFFFF6B7A, 0xFFFFC857, 0xFF3A86FF,
        ).map { it.toInt() }
    }
}

/** Tap-to-select region (11a): a model mask revision ([MaskKind.SELECTION]) used on the freeze mask. */
@Serializable
data class SelectionRegion(val rev: Int, val mode: SelectionMode)

@Serializable
enum class SelectionMode {
    /** The region stays still. */
    FREEZE,

    /** Only the region moves: everything outside it is frozen. */
    ANIMATE_ONLY,
}
