package com.motionloop.app.feature.editor

import android.graphics.Bitmap
import com.motionloop.app.domain.model.Anchor
import com.motionloop.app.domain.model.LutData
import com.motionloop.app.domain.model.MaskStroke
import com.motionloop.app.domain.model.MotionArrow
import com.motionloop.app.domain.model.Project
import com.motionloop.app.render.RenderScene

enum class EditorTool { ARROW, ANCHOR, FREEZE, UNFREEZE, ERASE }

/** Bottom panel: motion tools or color adjustments. */
enum class EditorPanel { ANIMATE, ADJUST }

/** Everything undo/redo covers. */
data class Geometry(
    val arrows: List<MotionArrow> = emptyList(),
    val anchors: List<Anchor> = emptyList(),
    val strokes: List<MaskStroke> = emptyList(),
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
    /** One-off message (for example a rejected .cube file). */
    val message: String? = null,
) {
    val isBrush: Boolean get() = tool == EditorTool.FREEZE || tool == EditorTool.UNFREEZE

    val visibleArrows: List<MotionArrow>
        get() = project?.arrows.orEmpty() + listOfNotNull(liveArrow)

    val scene: RenderScene
        get() {
            val p = project ?: return RenderScene()
            return RenderScene(
                image = preview,
                arrows = visibleArrows,
                anchors = p.anchors,
                strokes = p.strokes + listOfNotNull(liveStroke),
                loop = p.loop,
                playing = playing,
                showMask = !playing && panel == EditorPanel.ANIMATE && (isBrush || liveStroke != null),
                adjustments = p.adjustments,
                lut = lut.takeIf { lutGradeId != null && lutGradeId == p.adjustments.gradeId },
            )
        }
}
