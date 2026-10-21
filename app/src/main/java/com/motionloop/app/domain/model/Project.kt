package com.motionloop.app.domain.model

import kotlinx.serialization.Serializable

/** Point in normalized image space: (0,0) top-left, (1,1) bottom-right. */
@Serializable
data class NPoint(val x: Float, val y: Float)

/** Straight motion arrow. Its length is how far a pixel travels in one loop cycle. */
@Serializable
data class MotionArrow(val id: String, val start: NPoint, val end: NPoint)

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
) {
    val aspect: Float get() = imageWidth.toFloat() / imageHeight
}
