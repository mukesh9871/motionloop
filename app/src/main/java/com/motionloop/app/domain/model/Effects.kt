package com.motionloop.app.domain.model

import kotlinx.serialization.Serializable

/** Animated stickers drawn in code. Order matters: `sticker.frag` switches on ordinal + 1. */
@Serializable
enum class StickerKind(val label: String) {
    BUTTERFLY("Butterfly"),
    FLAME("Flame"),
    BIRDS("Birds"),
    GLINT("Glint"),
}

/** One placed sticker. Position in normalized image space, [size] as a fraction of image width. */
@Serializable
data class Sticker(
    val id: String,
    val kind: StickerKind,
    val x: Float = 0.5f,
    val y: Float = 0.5f,
    val size: Float = DEFAULT_SIZE,
    /** Degrees, clockwise. */
    val rotation: Float = 0f,
) {
    companion object {
        const val MAX = 12
        const val DEFAULT_SIZE = 0.18f
        const val MIN_SIZE = 0.05f
        const val MAX_SIZE = 0.6f
    }
}

/**
 * Dispersion: a painted area breaks into flakes that drift away and come back once per cycle.
 * Holes left by the flakes show a soft blur of the photo.
 */
@Serializable
data class DispersionSettings(
    val enabled: Boolean = true,
    val strokes: List<MaskStroke> = emptyList(),
    /** Direction the flakes fly, degrees: 0 = right, 90 = down. */
    val angle: Float = -30f,
    /** 0..1, how far flakes travel. */
    val distance: Float = 0.5f,
    /** 0..1, share of the area that breaks apart. */
    val amount: Float = 0.6f,
) {
    val isActive: Boolean get() = enabled && amount > 0f && strokes.any { it.mode == MaskMode.FREEZE }
}

/** Ripples on tapped water regions ([SelectionMode.RIPPLE]). */
@Serializable
data class RippleSettings(
    /** 0..1 wave strength. */
    val strength: Float = 0.5f,
)

/** Effects that are not part of undo, like other settings. */
@Serializable
data class EffectsSettings(
    val stickers: List<Sticker> = emptyList(),
    val dispersion: DispersionSettings = DispersionSettings(),
    val ripple: RippleSettings = RippleSettings(),
)
