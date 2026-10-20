package com.motionloop.app.render

import android.graphics.Bitmap
import com.motionloop.app.domain.model.Anchor
import com.motionloop.app.domain.model.LoopSettings
import com.motionloop.app.domain.model.MaskStroke
import com.motionloop.app.domain.model.MotionArrow

/** Immutable snapshot the UI hands to a renderer. The renderer diffs it to skip unchanged work. */
data class RenderScene(
    val image: Bitmap? = null,
    val arrows: List<MotionArrow> = emptyList(),
    val anchors: List<Anchor> = emptyList(),
    val strokes: List<MaskStroke> = emptyList(),
    val loop: LoopSettings = LoopSettings(),
    val playing: Boolean = false,
    val showMask: Boolean = false,
)
