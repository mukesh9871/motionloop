package com.motionloop.app.feature.editor

import android.graphics.Bitmap
import com.motionloop.app.domain.model.Anchor
import com.motionloop.app.domain.model.MaskStroke
import com.motionloop.app.domain.model.MotionArrow
import com.motionloop.app.domain.model.Project
import com.motionloop.app.render.RenderScene

enum class EditorTool { ARROW, ANCHOR, FREEZE, UNFREEZE, ERASE }

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
                showMask = !playing && (isBrush || liveStroke != null),
            )
        }
}
