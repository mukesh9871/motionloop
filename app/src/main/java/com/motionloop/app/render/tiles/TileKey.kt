package com.motionloop.app.render.tiles

import com.motionloop.app.domain.model.AdjustPreset
import com.motionloop.app.domain.model.Atmosphere
import com.motionloop.app.domain.model.BackgroundMode
import com.motionloop.app.domain.model.CameraPath
import com.motionloop.app.domain.model.LightLeak
import com.motionloop.app.domain.model.ParticleType
import com.motionloop.app.domain.model.SkyStyle
import com.motionloop.app.domain.model.StickerKind
import com.motionloop.app.domain.model.TextAnimation

/** One effect shown on the reference photo. [id] names its cache file, so it must stay unique. */
sealed interface TileKey {
    val id: String

    /** False for effects that look the same at every moment: no loop is worth rendering for them. */
    val moves: Boolean
        get() = when (this) {
            is Sky, is Camera, is Sticker, Flow, Water, Disperse -> true
            is Text -> animation != TextAnimation.NONE
            is Particles -> type != ParticleType.NONE
            is Leak -> leak != LightLeak.NONE
            is Air -> atmosphere != Atmosphere.NONE
            is Background, is Grade, is Adjust, Photo, SubjectTint -> false
        }

    data class Sky(val style: SkyStyle) : TileKey { override val id get() = "sky_$style" }
    data class Background(val mode: BackgroundMode) : TileKey { override val id get() = "bg_$mode" }
    data class Camera(val path: CameraPath) : TileKey { override val id get() = "cam_$path" }
    data class Particles(val type: ParticleType) : TileKey { override val id get() = "part_$type" }
    data class Leak(val leak: LightLeak) : TileKey { override val id get() = "leak_$leak" }
    data class Air(val atmosphere: Atmosphere) : TileKey { override val id get() = "air_$atmosphere" }
    data class Sticker(val kind: StickerKind) : TileKey { override val id get() = "sticker_$kind" }
    data class Text(val animation: TextAnimation) : TileKey { override val id get() = "text_$animation" }

    /** Built-in color grade by id, null for no grade. */
    data class Grade(val gradeId: String?) : TileKey { override val id get() = "grade_${gradeId ?: "none"}" }
    data class Adjust(val preset: AdjustPreset) : TileKey { override val id get() = "adjust_$preset" }

    // Help pictures only.
    data object Photo : TileKey { override val id get() = "photo" }
    data object Flow : TileKey { override val id get() = "flow" }
    data object Water : TileKey { override val id get() = "water" }
    data object Disperse : TileKey { override val id get() = "disperse" }
    data object SubjectTint : TileKey { override val id get() = "subject_tint" }
}

/** Output shapes: effect tiles are square, Help pictures 16:9. Sizes in pixels. */
enum class TileShape(val width: Int, val height: Int) {
    SQUARE(192, 192),
    WIDE(384, 216),
}

data class TileRequest(val key: TileKey, val shape: TileShape)
