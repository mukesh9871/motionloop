package com.motionloop.app.domain.model

import com.motionloop.app.domain.audio.AudioSettings
import kotlinx.serialization.Serializable

/** Point in normalized image space: (0,0) top-left, (1,1) bottom-right. */
@Serializable
data class NPoint(val x: Float, val y: Float)

/**
 * Motion path: a straight arrow, or a free-hand curve in [path] (start and end included).
 * Its length is how far a pixel travels along it in one loop cycle.
 */
@Serializable
data class MotionArrow(val id: String, val start: NPoint, val end: NPoint, val path: List<NPoint> = emptyList()) {
    /** The drawn line: the free path when there is one, otherwise start to end. */
    val points: List<NPoint> get() = if (path.size >= 2) path else listOf(start, end)
}

/** Keeps the area around [center] still. [radius] is a fraction of image width. */
@Serializable
data class Anchor(val id: String, val center: NPoint, val radius: Float = DEFAULT_RADIUS) {
    companion object {
        const val DEFAULT_RADIUS = 0.035f
    }
}

@Serializable
enum class MaskMode { FREEZE, UNFREEZE }

/** Brush stroke on the freeze mask. [radius] is a fraction of image width. */
@Serializable
data class MaskStroke(
    val points: List<NPoint>,
    val radius: Float,
    val hardness: Float,
    val mode: MaskMode,
)

@Serializable
enum class LoopMode { CONTINUOUS, BOOMERANG }

@Serializable
data class LoopSettings(
    val mode: LoopMode = LoopMode.CONTINUOUS,
    /** Multiplier on the base cycle rate. */
    val speed: Float = 1f,
) {
    companion object {
        /** Seconds per cycle at speed 1. */
        const val BASE_CYCLE_SECONDS = 3f
        const val MIN_SPEED = 0.25f
        const val MAX_SPEED = 2f
    }
}

@Serializable
data class Project(
    val id: String,
    val createdAt: Long,
    val updatedAt: Long,
    val imageWidth: Int,
    val imageHeight: Int,
    val arrows: List<MotionArrow> = emptyList(),
    val anchors: List<Anchor> = emptyList(),
    val strokes: List<MaskStroke> = emptyList(),
    val loop: LoopSettings = LoopSettings(),
    val adjustments: Adjustments = Adjustments(),
    val overlay: OverlaySettings = OverlaySettings(),
    val sky: SkySettings = SkySettings(),
    val parallax: ParallaxSettings = ParallaxSettings(),
    val subject: SubjectSettings = SubjectSettings(),
    val background: BackgroundSettings = BackgroundSettings(),
    /** Tap-to-select regions on the freeze mask (11a). */
    val selections: List<SelectionRegion> = emptyList(),
    /** Text layers (11e), drawn in list order. */
    val texts: List<TextLayer> = emptyList(),
    val audio: AudioSettings = AudioSettings(),
    /** Stickers, dispersion and water ripples. */
    val effects: EffectsSettings = EffectsSettings(),
) {
    val aspect: Float get() = imageWidth.toFloat() / imageHeight

    /**
     * Something changes over the loop. False for a photo with only color, cutout or still text:
     * one frame shows all of it. Errs on the side of true (an active sky or overlay always counts).
     */
    val animates: Boolean
        get() = arrows.isNotEmpty() ||
            (effects.ripple.strength > 0f && selections.any { it.mode == SelectionMode.RIPPLE }) ||
            overlay.isActive || sky.isActive || parallax.isActive ||
            effects.stickers.isNotEmpty() || effects.dispersion.isActive ||
            texts.any { it.animation != TextAnimation.NONE }
}
