package com.motionloop.app.feature.editor

import android.graphics.Bitmap
import com.motionloop.app.domain.model.Anchor
import com.motionloop.app.domain.model.LutData
import com.motionloop.app.domain.model.MaskStroke
import com.motionloop.app.domain.model.MotionArrow
import com.motionloop.app.domain.model.Project
import com.motionloop.app.render.RenderScene
import com.motionloop.ml.AlphaMask

enum class EditorTool {
    ARROW, ANCHOR, FREEZE, UNFREEZE, ERASE,

    /** Sky panel: tap to add or remove an area with the segmenter, or paint and erase by hand. */
    SKY_ADD, SKY_REMOVE, SKY_PAINT, SKY_ERASE,
}

/** Bottom panel: motion tools, sky, color adjustments or overlays. */
enum class EditorPanel(val label: String, val defaultTool: EditorTool) {
    ANIMATE("Animate", EditorTool.ARROW),
    SKY("Sky", EditorTool.SKY_ADD),
    DEPTH("3D", EditorTool.ARROW),
    ADJUST("Adjust", EditorTool.ARROW),
    OVERLAY("Overlay", EditorTool.ARROW),
}

/** Everything undo/redo covers. */
data class Geometry(
    val arrows: List<MotionArrow> = emptyList(),
    val anchors: List<Anchor> = emptyList(),
    val strokes: List<MaskStroke> = emptyList(),
    /** Sky area: model mask revision plus brush refinements. */
    val skyMaskRev: Int = 0,
    val skyStrokes: List<MaskStroke> = emptyList(),
)

data class EditorUiState(
    val loading: Boolean = true,
    val error: String? = null,
    val project: Project? = null,
    val preview: Bitmap? = null,
    val tool: EditorTool = EditorTool.ARROW,
    /** Brush radius as a fraction of image width. */
    val brushRadius: Float = 0.06f,
    val brushHardness: Float = 0.5f,
    val playing: Boolean = false,
    val canUndo: Boolean = false,
    val canRedo: Boolean = false,
    /** In-progress gesture, drawn and rendered before it is committed to history. */
    val liveArrow: MotionArrow? = null,
    val liveStroke: MaskStroke? = null,
    val panel: EditorPanel = EditorPanel.ANIMATE,
    /** Baked LUT and the grade id it was baked for. Only used when the id still matches. */
    val lut: LutData? = null,
    val lutGradeId: String? = null,
    val hasCustomLut: Boolean = false,
    /** One-off message (for example a rejected .cube file). */
    val message: String? = null,
    /** Model work in progress, shown over the preview ("Finding the sky"). */
    val busy: String? = null,
    /** Loaded sky mask and the revision it belongs to. Only used while it matches the project. */
    val skyMask: AlphaMask? = null,
    val skyMaskRev: Int = 0,
    val skyPhoto: Bitmap? = null,
    val skyPhotoRev: Int = 0,
    val depth: AlphaMask? = null,
    val depthRev: Int = 0,
) {
    val isSkyBrush: Boolean get() = tool == EditorTool.SKY_PAINT || tool == EditorTool.SKY_ERASE
    val isBrush: Boolean get() = tool == EditorTool.FREEZE || tool == EditorTool.UNFREEZE || isSkyBrush

    val visibleArrows: List<MotionArrow>
        get() = project?.arrows.orEmpty() + listOfNotNull(liveArrow)

    val scene: RenderScene
        get() {
            val p = project ?: return RenderScene()
            return RenderScene(
                image = preview,
                arrows = visibleArrows,
                anchors = p.anchors,
                strokes = if (isSkyBrush) p.strokes else p.strokes + listOfNotNull(liveStroke),
                loop = p.loop,
                playing = playing,
                showMask = !playing && panel == EditorPanel.ANIMATE && (isBrush || liveStroke != null),
                adjustments = p.adjustments,
                lut = lut.takeIf { lutGradeId != null && lutGradeId == p.adjustments.gradeId },
                overlay = p.overlay,
                sky = if (isSkyBrush && liveStroke != null) p.sky.copy(strokes = p.sky.strokes + liveStroke) else p.sky,
                skyMask = skyMask.takeIf { skyMaskRev == p.sky.maskRev },
                skyPhoto = skyPhoto.takeIf { skyPhotoRev == p.sky.photoRev },
                // In the sky panel, show the area as a tint while painting or before replacing is on.
                skyReplace = !(panel == EditorPanel.SKY && !playing && (liveStroke != null || !p.sky.enabled)),
                parallax = p.parallax,
                depth = depth.takeIf { depthRev == p.parallax.depthRev },
            )
        }
}
