package com.motionloop.app.feature.editor

import android.graphics.Bitmap
import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.motionloop.app.core.common.DefaultDispatcher
import com.motionloop.app.domain.model.Adjustments
import com.motionloop.app.domain.model.Anchor
import com.motionloop.app.domain.model.LoopMode
import com.motionloop.app.domain.model.LoopSettings
import com.motionloop.app.domain.model.MaskKind
import com.motionloop.app.domain.model.MaskMode
import com.motionloop.app.domain.model.MaskStroke
import com.motionloop.app.domain.model.MotionArrow
import com.motionloop.app.domain.model.NPoint
import com.motionloop.app.domain.model.OverlaySettings
import com.motionloop.app.domain.model.Project
import com.motionloop.app.domain.model.SkySettings
import com.motionloop.app.domain.model.SkyStyle
import com.motionloop.app.domain.repository.ImageKind
import com.motionloop.app.domain.repository.LutRepository
import com.motionloop.app.domain.repository.ProjectAssetRepository
import com.motionloop.app.domain.repository.ProjectRepository
import com.motionloop.app.navigation.Routes
import com.motionloop.app.render.passes.FlowFieldPass
import com.motionloop.ml.Segmenter
import dagger.hilt.android.lifecycle.HiltViewModel
import java.util.UUID
import javax.inject.Inject
import kotlin.math.hypot
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@HiltViewModel
class EditorViewModel @Inject constructor(
    savedState: SavedStateHandle,
    private val projects: ProjectRepository,
    private val luts: LutRepository,
    private val assets: ProjectAssetRepository,
    private val segmenter: Segmenter,
    @DefaultDispatcher private val cpu: CoroutineDispatcher,
) : ViewModel() {

    private val projectId: String = checkNotNull(savedState[Routes.ARG_PROJECT_ID])

    private val _state = MutableStateFlow(EditorUiState())
    val state: StateFlow<EditorUiState> = _state.asStateFlow()

    private var lutJob: Job? = null
    private var skyMaskJob: Job? = null
    private val undoStack = ArrayDeque<Geometry>()
    private val redoStack = ArrayDeque<Geometry>()

    init {
        viewModelScope.launch {
            // Reopening right after leaving: wait for that session's queued saves first.
            projects.flush()
            val project = projects.load(projectId)
            if (project == null) {
                _state.update { it.copy(loading = false, error = "Project not found") }
                return@launch
            }
            // Before anything can save a new revision: older mask revisions and sky photos can go,
            // since undo history starts empty.
            assets.prune(projectId, MaskKind.SKY, setOf(project.sky.maskRev))
            assets.pruneImages(projectId, ImageKind.SKY_PHOTO, setOf(project.sky.photoRev))
            val preview = try {
                projects.loadImage(projectId, PREVIEW_EDGE)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                null
            }
            _state.update {
                it.copy(
                    loading = false,
                    project = project,
                    preview = preview,
                    error = if (preview == null) "Could not load the photo" else null,
                )
            }
            refreshLut()
            _state.update { it.copy(hasCustomLut = luts.hasCustom(projectId)) }
            loadSkyMask(project.sky.maskRev)
            loadSkyPhoto(project.sky.photoRev)
        }
    }

    // region sky

    /** Settings that are not part of undo (style, drift, edge, blend, on/off). */
    fun setSky(change: (SkySettings) -> SkySettings) {
        val p = _state.value.project ?: return
        val updated = p.copy(sky = change(p.sky))
        _state.update { it.copy(project = updated) }
        persist(updated)
    }

    /** Runs the segmenter from automatic sky seeds and replaces the sky area with the result. */
    fun detectSky() = runModel("Finding the sky") { preview ->
        val mask = withContext(cpu) { segmenter.segmentSky(preview) }
        if (mask == null) {
            _state.update { it.copy(message = "No clear sky found. Tap the sky to select it.") }
            return@runModel
        }
        val rev = assets.saveMask(projectId, MaskKind.SKY, mask)
        _state.update { it.copy(skyMask = mask, skyMaskRev = rev) }
        val project = _state.value.project ?: return@runModel
        commit(current(project).copy(skyMaskRev = rev))
        setSky { it.copy(enabled = true) }
    }

    /** Adds or removes the region under [p] (the segmenter picks the object there). */
    private fun skyTap(p: NPoint, add: Boolean) = runModel(if (add) "Adding to the sky" else "Removing from the sky") { preview ->
        val region = withContext(cpu) { segmenter.segmentAt(preview, p.x, p.y) }
        val s = _state.value
        val base = s.skyMask.takeIf { s.skyMaskRev == s.project?.sky?.maskRev && s.skyMaskRev != 0 }
        val combined = when {
            base != null -> withContext(cpu) { base.combine(region, subtract = !add) }
            add -> region
            else -> {
                // No model area yet: only painted strokes exist, which taps do not change.
                _state.update { it.copy(message = "Use Erase to remove painted sky.") }
                return@runModel
            }
        }
        val rev = assets.saveMask(projectId, MaskKind.SKY, combined)
        _state.update { it.copy(skyMask = combined, skyMaskRev = rev) }
        val project = _state.value.project ?: return@runModel
        commit(current(project).copy(skyMaskRev = rev))
    }

    fun importSkyPhoto(uri: Uri) = runModel("Loading the photo") {
        val rev = assets.importImage(projectId, ImageKind.SKY_PHOTO, uri)
        setSky { it.copy(style = SkyStyle.PHOTO, photoRev = rev, enabled = true) }
        loadSkyPhoto(rev)
    }

    /** Model and file work, one at a time, with a busy label and a readable error. */
    private fun runModel(label: String, block: suspend (preview: Bitmap) -> Unit) {
        val preview = _state.value.preview ?: return
        if (_state.value.busy != null) return
        _state.update { it.copy(busy = label, playing = false) }
        viewModelScope.launch {
            try {
                block(preview)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                val reason = e.message ?: e.javaClass.simpleName
                _state.update { it.copy(message = "That did not work: $reason") }
            } finally {
                _state.update { it.copy(busy = null) }
            }
        }
    }

    private fun loadSkyMask(rev: Int) {
        // Fast undo / redo: only the latest request may land, even when this one needs no load.
        skyMaskJob?.cancel()
        if (rev == _state.value.skyMaskRev && (rev == 0 || _state.value.skyMask != null)) return
        skyMaskJob = viewModelScope.launch {
            val mask = assets.loadMask(projectId, MaskKind.SKY, rev)
            _state.update { it.copy(skyMask = mask, skyMaskRev = rev) }
        }
    }

    private fun loadSkyPhoto(rev: Int) {
        if (rev == 0) return
        viewModelScope.launch {
            val photo = assets.loadImage(projectId, ImageKind.SKY_PHOTO, rev, SKY_PHOTO_EDGE)
            _state.update { it.copy(skyPhoto = photo, skyPhotoRev = rev) }
        }
    }

    // endregion

    // region color

    fun setPanel(panel: EditorPanel) =
        _state.update { it.copy(panel = panel, tool = panel.defaultTool, liveArrow = null, liveStroke = null) }

    /** Slider changes. Not part of undo history: they are cheap to drag back and reset. */
    fun setAdjustments(change: (Adjustments) -> Adjustments) {
        val p = _state.value.project ?: return
        val before = p.adjustments
        val updated = p.copy(adjustments = change(before))
        _state.update { it.copy(project = updated) }
        persist(updated)
        if (updated.adjustments.gradeId != before.gradeId) refreshLut()
    }

    fun resetAdjustments() = setAdjustments { Adjustments() }

    /** Overlay changes. Like adjustments, not part of undo history. */
    fun setOverlay(change: (OverlaySettings) -> OverlaySettings) {
        val p = _state.value.project ?: return
        val updated = p.copy(overlay = change(p.overlay))
        _state.update { it.copy(project = updated) }
        persist(updated)
    }

    fun selectGrade(id: String?) = setAdjustments { it.copy(gradeId = id, gradeIntensity = 1f) }

    fun importCube(uri: Uri) {
        viewModelScope.launch {
            try {
                luts.importCube(projectId, uri)
                _state.update { it.copy(hasCustomLut = true) }
                val p = _state.value.project ?: return@launch
                if (p.adjustments.gradeId == Adjustments.CUSTOM_LUT_ID) {
                    refreshLut() // same id, new file contents
                } else {
                    selectGrade(Adjustments.CUSTOM_LUT_ID)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.update { it.copy(message = e.message ?: "Could not import that file") }
            }
        }
    }

    fun dismissMessage() = _state.update { it.copy(message = null) }

    /** Bakes or loads the LUT for the current grade off the main thread. Latest request wins. */
    private fun refreshLut() {
        lutJob?.cancel()
        val p = _state.value.project ?: return
        val gradeId = p.adjustments.gradeId
        if (gradeId == null) {
            _state.update { it.copy(lut = null, lutGradeId = null) }
            return
        }
        lutJob = viewModelScope.launch {
            val lut = luts.resolve(projectId, p.adjustments)
            _state.update { it.copy(lut = lut, lutGradeId = if (lut != null) gradeId else null) }
            if (lut == null && gradeId == Adjustments.CUSTOM_LUT_ID) {
                _state.update { it.copy(message = "The imported LUT file is missing. Import it again.") }
            }
        }
    }

    // endregion

    // region tools and playback

    fun setTool(tool: EditorTool) = _state.update { it.copy(tool = tool, playing = false) }
    fun setBrushRadius(r: Float) = _state.update { it.copy(brushRadius = r) }
    fun setBrushHardness(h: Float) = _state.update { it.copy(brushHardness = h) }
    fun togglePlay() = _state.update { it.copy(playing = !it.playing) }
    fun setPlaying(playing: Boolean) = _state.update { it.copy(playing = playing) }

    fun setSpeed(speed: Float) = updateLoop { it.copy(speed = speed.coerceIn(LoopSettings.MIN_SPEED, LoopSettings.MAX_SPEED)) }
    fun setLoopMode(mode: LoopMode) = updateLoop { it.copy(mode = mode) }

    private fun updateLoop(change: (LoopSettings) -> LoopSettings) {
        val p = _state.value.project ?: return
        val updated = p.copy(loop = change(p.loop))
        _state.update { it.copy(project = updated) }
        persist(updated)
    }

    // endregion

    // region gestures (points in normalized image space)

    fun pointerDown(p: NPoint) {
        val s = _state.value
        val project = s.project ?: return
        _state.update { it.copy(playing = false) }
        when (s.tool) {
            EditorTool.ARROW ->
                _state.update { it.copy(liveArrow = MotionArrow(UUID.randomUUID().toString(), p, p)) }

            // The flow shader takes at most MAX_ITEMS anchors, so do not accept more than it can use.
            EditorTool.ANCHOR -> if (project.anchors.size < FlowFieldPass.MAX_ITEMS) {
                commit(
                    current(project).let { g -> g.copy(anchors = g.anchors + Anchor(UUID.randomUUID().toString(), p)) },
                )
            }

            EditorTool.FREEZE, EditorTool.UNFREEZE, EditorTool.SKY_PAINT, EditorTool.SKY_ERASE -> _state.update {
                // Freeze and sky paint add to their mask, unfreeze and sky erase remove from it.
                val adds = s.tool == EditorTool.FREEZE || s.tool == EditorTool.SKY_PAINT
                it.copy(
                    liveStroke = MaskStroke(
                        points = listOf(p),
                        radius = s.brushRadius,
                        hardness = s.brushHardness,
                        mode = if (adds) MaskMode.FREEZE else MaskMode.UNFREEZE,
                    ),
                )
            }

            EditorTool.ERASE -> eraseNear(project, p)
            EditorTool.SKY_ADD -> skyTap(p, add = true)
            EditorTool.SKY_REMOVE -> skyTap(p, add = false)
        }
    }

    fun pointerMove(p: NPoint) {
        _state.update { s ->
            when {
                s.liveArrow != null -> s.copy(liveArrow = s.liveArrow.copy(end = p))
                s.liveStroke != null -> {
                    val last = s.liveStroke.points.last()
                    // Skip sub-pixel moves so long strokes stay small.
                    if (hypot(p.x - last.x, p.y - last.y) < MIN_STROKE_STEP) s
                    else s.copy(liveStroke = s.liveStroke.copy(points = s.liveStroke.points + p))
                }
                else -> s
            }
        }
    }

    fun pointerUp() {
        val s = _state.value
        val project = s.project ?: return
        s.liveArrow?.let { arrow ->
            _state.update { it.copy(liveArrow = null) }
            val len = distance(arrow.start, arrow.end, project.aspect)
            if (len >= MIN_ARROW_LENGTH && project.arrows.size < FlowFieldPass.MAX_ITEMS) {
                commit(current(project).let { it.copy(arrows = it.arrows + arrow) })
            }
        }
        s.liveStroke?.let { stroke ->
            _state.update { it.copy(liveStroke = null) }
            val g = current(project)
            commit(if (s.isSkyBrush) g.copy(skyStrokes = g.skyStrokes + stroke) else g.copy(strokes = g.strokes + stroke))
        }
    }

    private fun eraseNear(project: Project, p: NPoint) {
        val aspect = project.aspect
        val arrow = project.arrows.minByOrNull { distanceToSegment(p, it.start, it.end, aspect) }
            ?.takeIf { distanceToSegment(p, it.start, it.end, aspect) < ERASE_RADIUS }
        val anchor = project.anchors.minByOrNull { distance(p, it.center, aspect) }
            ?.takeIf { distance(p, it.center, aspect) < ERASE_RADIUS }
        val g = current(project)
        when {
            arrow != null -> commit(g.copy(arrows = g.arrows - arrow))
            anchor != null -> commit(g.copy(anchors = g.anchors - anchor))
        }
    }

    // endregion

    // region history

    fun undo() {
        val project = _state.value.project ?: return
        val previous = undoStack.removeLastOrNull() ?: return
        redoStack.addLast(current(project))
        apply(project, previous)
    }

    fun redo() {
        val project = _state.value.project ?: return
        val next = redoStack.removeLastOrNull() ?: return
        undoStack.addLast(current(project))
        apply(project, next)
    }

    fun clearAll() {
        val project = _state.value.project ?: return
        if (current(project) != Geometry()) commit(Geometry())
    }

    private fun commit(next: Geometry) {
        val project = _state.value.project ?: return
        undoStack.addLast(current(project))
        if (undoStack.size > MAX_HISTORY) undoStack.removeFirst()
        redoStack.clear()
        apply(project, next)
    }

    private fun apply(project: Project, g: Geometry) {
        val updated = project.copy(
            arrows = g.arrows,
            anchors = g.anchors,
            strokes = g.strokes,
            sky = project.sky.copy(maskRev = g.skyMaskRev, strokes = g.skyStrokes),
        )
        loadSkyMask(g.skyMaskRev)
        _state.update {
            it.copy(project = updated, canUndo = undoStack.isNotEmpty(), canRedo = redoStack.isNotEmpty())
        }
        persist(updated)
    }

    private fun current(p: Project) = Geometry(p.arrows, p.anchors, p.strokes, p.sky.maskRev, p.sky.strokes)

    // endregion

    /** Queued on the repository's app-wide serial writer, so it survives leaving the screen. */
    private fun persist(project: Project) = projects.saveInBackground(project)

    // Distances in units of image width: y is scaled by height / width = 1 / aspect,
    // so hit areas are round on screen for any photo shape.
    private fun distance(a: NPoint, b: NPoint, aspect: Float): Float =
        hypot(b.x - a.x, (b.y - a.y) / aspect)

    private fun distanceToSegment(p: NPoint, a: NPoint, b: NPoint, aspect: Float): Float {
        val abx = b.x - a.x
        val aby = (b.y - a.y) / aspect
        val apx = p.x - a.x
        val apy = (p.y - a.y) / aspect
        val len2 = abx * abx + aby * aby
        val t = if (len2 < 1e-8f) 0f else ((apx * abx + apy * aby) / len2).coerceIn(0f, 1f)
        return hypot(apx - abx * t, apy - aby * t)
    }

    private companion object {
        /** GLES 3.0 guarantees 2048 px textures. Export uses the full-size photo. */
        const val PREVIEW_EDGE = 2048
        const val MIN_ARROW_LENGTH = 0.01f
        const val MIN_STROKE_STEP = 0.003f
        const val ERASE_RADIUS = 0.04f
        const val MAX_HISTORY = 100
        const val SKY_PHOTO_EDGE = 2048
    }
}
