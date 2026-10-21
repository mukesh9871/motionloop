package com.motionloop.app.domain.model

import kotlinx.serialization.Serializable

/** Order matters: `source.frag` switches on the ordinal. */
@Serializable
enum class SkyStyle { CLEAR, SUNSET, NIGHT, STORM, AURORA, PHOTO }

/**
 * Sky replacement. The sky area is a model mask (stored as a revision file, see [MaskKind.SKY])
 * refined by brush strokes: [MaskMode.FREEZE] strokes add sky, [MaskMode.UNFREEZE] strokes remove it.
 */
@Serializable
data class SkySettings(
    val enabled: Boolean = false,
    val style: SkyStyle = SkyStyle.CLEAR,
    /** Revision of the stored model mask, 0 = none yet. */
    val maskRev: Int = 0,
    val strokes: List<MaskStroke> = emptyList(),
    /** 0..1 how far clouds (or the sky photo) drift per loop. */
    val drift: Float = 0.5f,
    /** 0..1 softness of the sky edge. */
    val feather: Float = 0.3f,
    /** 0..1 how much the rest of the photo takes on the new sky's light. */
    val harmonize: Float = 0.3f,
    /** Revision of the imported sky photo for [SkyStyle.PHOTO], 0 = none. */
    val photoRev: Int = 0,
) {
    val hasArea: Boolean get() = maskRev != 0 || strokes.isNotEmpty()

    /** Something will actually be replaced. */
    val isActive: Boolean get() = enabled && hasArea && (style != SkyStyle.PHOTO || photoRev != 0)
}

/** Kinds of per-project masks, stored as `<prefix>_<rev>.mask` files. */
enum class MaskKind(val prefix: String) {
    SKY("sky"),
    SUBJECT("subject"),
    DEPTH("depth"),
    SELECTION("selection"),
}
