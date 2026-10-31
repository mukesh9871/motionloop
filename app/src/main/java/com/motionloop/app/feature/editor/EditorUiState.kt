package com.motionloop.app.feature.editor

import android.graphics.Bitmap
import com.motionloop.app.domain.model.Anchor
import com.motionloop.app.domain.model.LutData
import com.motionloop.app.domain.model.MaskStroke
import com.motionloop.app.domain.model.MotionArrow
import com.motionloop.app.domain.model.Project
import com.motionloop.app.domain.model.BackgroundMode
import com.motionloop.app.domain.model.SelectionRegion
import com.motionloop.app.render.AudioClock
import com.motionloop.app.domain.model.Sticker
import com.motionloop.app.render.RenderScene
import com.motionloop.app.domain.model.TextAnimation
import com.motionloop.app.domain.model.TextLayer
import com.motionloop.app.render.SelectionLayer
import com.motionloop.app.render.passes.TextDraw
import com.motionloop.app.render.text.RenderedText
import com.motionloop.ml.AlphaMask

enum class EditorTool {
    ARROW, ANCHOR, FREEZE, UNFREEZE, ERASE,

    /** Sky panel: tap to add or remove an area with the segmenter, or paint and erase by hand. */
    SKY_ADD, SKY_REMOVE, SKY_PAINT, SKY_ERASE,

    /** Animate panel: tap an object to freeze it, or to make it the only thing that moves (11a). */
    SELECT_FREEZE, SELECT_ANIMATE,

    /** Cutout panel: tap to add or remove subject areas, or restore and erase by hand (11d). */
    SUBJECT_ADD, SUBJECT_REMOVE, SUBJECT_RESTORE, SUBJECT_ERASE,

    /** Effects panel: tap water to ripple it, paint the dispersion area, drag stickers. */
    WATER, DISPERSE_PAINT, DISPERSE_ERASE, STICKER_MOVE,

    /** Overlay panel: paint where particles appear. */
    OVERLAY_PAINT, OVERLAY_ERASE,

    /** Text panel: tap a text to select it, drag to move it (11e). */
    TEXT_MOVE,
}

/** Bottom panel: motion tools, sky, color adjustments or overlays. */
enum class EditorPanel(val label: String, val defaultTool: EditorTool) {
    ANIMATE("Animate", EditorTool.ARROW),
    EFFECTS("Effects", EditorTool.STICKER_MOVE),
    SKY("Sky", EditorTool.SKY_ADD),
    DEPTH("3D", EditorTool.ARROW),
    CUTOUT("Cutout", EditorTool.SUBJECT_ADD),
    TEXT("Text", EditorTool.TEXT_MOVE),
    SOUND("Sound", EditorTool.ARROW),
    ADJUST("Adjust", EditorTool.ARROW),
    // No painting until "Paint area" is chosen: a stray touch must not limit the particles.
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
    /** Subject area: model mask revision plus brush refinements. */
    val subjectMaskRev: Int = 0,
    val subjectStrokes: List<MaskStroke> = emptyList(),
    val selections: List<SelectionRegion> = emptyList(),
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
    /** Panel the current message belongs to. Null: show it everywhere. */
    val messagePanel: EditorPanel? = null,
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
    val subjectMask: AlphaMask? = null,
    val subjectMaskRev: Int = 0,
    val backgroundPhoto: Bitmap? = null,
    val backgroundPhotoRev: Int = 0,
    /** Loaded tap-to-select masks by revision. */
    val selectionMasks: Map<Int, AlphaMask> = emptyMap(),
    /** Rasterized text by layer id. May lag one edit behind while a new one is drawn. */
    val renderedTexts: Map<String, RenderedText> = emptyMap(),
    val selectedTextId: String? = null,
    val selectedStickerId: String? = null,
    /** Beat envelope of the preview loop, null without sound or beat sync. */
    val beatEnvelope: FloatArray? = null,
    val audioClock: AudioClock? = null,
) {
    val selectedText: TextLayer? get() = project?.texts?.firstOrNull { it.id == selectedTextId }

    val isSkyBrush: Boolean get() = tool == EditorTool.SKY_PAINT || tool == EditorTool.SKY_ERASE
    val isSubjectBrush: Boolean get() = tool == EditorTool.SUBJECT_RESTORE || tool == EditorTool.SUBJECT_ERASE
    val isEffectBrush: Boolean get() = tool == EditorTool.DISPERSE_PAINT || tool == EditorTool.DISPERSE_ERASE
    val isOverlayBrush: Boolean get() = tool == EditorTool.OVERLAY_PAINT || tool == EditorTool.OVERLAY_ERASE
    val isSubjectTool: Boolean get() = isSubjectBrush || tool == EditorTool.SUBJECT_ADD || tool == EditorTool.SUBJECT_REMOVE
    val isBrush: Boolean
        get() = tool == EditorTool.FREEZE || tool == EditorTool.UNFREEZE || isSkyBrush || isSubjectBrush || isEffectBrush || isOverlayBrush

    val selectedSticker: Sticker? get() = project?.effects?.stickers?.firstOrNull { it.id == selectedStickerId }

    /** Tools whose result is the freeze mask, so the editor tints frozen areas while they are active. */
    private val showsFreezeMask: Boolean
        get() = tool == EditorTool.FREEZE || tool == EditorTool.UNFREEZE ||
            tool == EditorTool.SELECT_FREEZE || tool == EditorTool.SELECT_ANIMATE

    // The project's own list while nothing is being drawn: the renderer and Compose skip work for
    // a list they have already seen, which a fresh copy would defeat.
    val visibleArrows: List<MotionArrow>
        get() {
            val arrows = project?.arrows.orEmpty()
            return liveArrow?.let { arrows + it } ?: arrows
        }

    val scene: RenderScene
        get() {
            val p = project ?: return RenderScene()
            return RenderScene(
                image = preview,
                arrows = visibleArrows,
                anchors = p.anchors,
                strokes = if (liveStroke == null || isSkyBrush || isSubjectBrush || isEffectBrush || isOverlayBrush) p.strokes else p.strokes + liveStroke,
                loop = p.loop,
                playing = playing,
                showMask = !playing && panel == EditorPanel.ANIMATE && (showsFreezeMask || liveStroke != null),
                adjustments = p.adjustments,
                lut = lut.takeIf { lutGradeId != null && lutGradeId == p.adjustments.gradeId },
                overlay = if (isOverlayBrush && liveStroke != null) p.overlay.copy(areaStrokes = p.overlay.areaStrokes + liveStroke) else p.overlay,
                effects = if (isEffectBrush && liveStroke != null) {
                    p.effects.copy(dispersion = p.effects.dispersion.copy(strokes = p.effects.dispersion.strokes + liveStroke))
                } else {
                    p.effects
                },
                sky = if (isSkyBrush && liveStroke != null) p.sky.copy(strokes = p.sky.strokes + liveStroke) else p.sky,
                skyMask = skyMask.takeIf { skyMaskRev == p.sky.maskRev },
                skyPhoto = skyPhoto.takeIf { skyPhotoRev == p.sky.photoRev },
                // In the sky panel, show the area as a tint while painting or before replacing is on.
                skyReplace = !(panel == EditorPanel.SKY && !playing && (liveStroke != null || !p.sky.enabled)),
                // While editing, the preview must show the photo where touches land: no zoom, no shift.
                parallax = if (playing || panel == EditorPanel.DEPTH) p.parallax else p.parallax.copy(enabled = false),
                depth = depth.takeIf { depthRev == p.parallax.depthRev },
                selections = p.selections.mapNotNull { r -> selectionMasks[r.rev]?.let { SelectionLayer(it, r.mode) } },
                subject = if (isSubjectBrush && liveStroke != null) {
                    p.subject.copy(strokes = p.subject.strokes + liveStroke)
                } else {
                    p.subject
                },
                subjectMask = subjectMask.takeIf { subjectMaskRev == p.subject.maskRev },
                background = p.background,
                backgroundPhoto = backgroundPhoto.takeIf { backgroundPhotoRev == p.background.photoRev },
                // In the cutout panel, show the subject as a tint while painting or before a background is chosen.
                subjectTintOnly = !playing && (
                    (panel == EditorPanel.CUTOUT && (liveStroke != null || p.background.mode == BackgroundMode.NONE)) ||
                        // Fixing the subject from the Text panel: show what is selected.
                        (panel == EditorPanel.TEXT && isSubjectTool)
                    ),
                // Paused: show every text fully (a fade starts invisible at phase 0), animate only while playing.
                beatEnvelope = beatEnvelope.takeIf { p.audio.beatSync },
                beatAmount = p.audio.beatAmount,
                audioClock = audioClock,
                texts = p.texts.mapNotNull { t ->
                    renderedTexts[t.id]?.let { TextDraw(if (playing) t else t.copy(animation = TextAnimation.NONE), it) }
                },
            )
        }
}
