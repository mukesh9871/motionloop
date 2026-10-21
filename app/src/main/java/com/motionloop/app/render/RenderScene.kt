package com.motionloop.app.render

import android.graphics.Bitmap
import com.motionloop.app.domain.model.Adjustments
import com.motionloop.app.domain.model.Anchor
import com.motionloop.app.domain.model.LoopSettings
import com.motionloop.app.domain.model.LutData
import com.motionloop.app.domain.model.MaskStroke
import com.motionloop.app.domain.model.MotionArrow
import com.motionloop.app.domain.model.OverlaySettings
import com.motionloop.app.domain.model.SkySettings
import com.motionloop.ml.AlphaMask

/** Immutable snapshot the UI hands to a renderer. The renderer diffs it to skip unchanged work. */
data class RenderScene(
    val image: Bitmap? = null,
    val arrows: List<MotionArrow> = emptyList(),
    val anchors: List<Anchor> = emptyList(),
    val strokes: List<MaskStroke> = emptyList(),
    val loop: LoopSettings = LoopSettings(),
    val playing: Boolean = false,
    val showMask: Boolean = false,
    val adjustments: Adjustments = Adjustments(),
    /** Baked LUT for adjustments.gradeId, resolved off the GL thread by the ViewModel. */
    val lut: LutData? = null,
    val overlay: OverlaySettings = OverlaySettings(),
    val sky: SkySettings = SkySettings(),
    /** Model mask for sky.maskRev, loaded off the GL thread. */
    val skyMask: AlphaMask? = null,
    val skyPhoto: Bitmap? = null,
    /** False while editing the sky area: tint it instead of replacing it. */
    val skyReplace: Boolean = true,
)
