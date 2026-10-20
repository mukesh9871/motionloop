package com.motionloop.app.feature.editor

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.motionloop.app.domain.model.Anchor
import com.motionloop.app.domain.model.LoopMode
import com.motionloop.app.domain.model.LoopSettings
import com.motionloop.app.domain.model.MaskMode
import com.motionloop.app.domain.model.MaskStroke
import com.motionloop.app.domain.model.MotionArrow
import com.motionloop.app.domain.model.NPoint
import com.motionloop.app.domain.model.Project
import com.motionloop.app.domain.repository.ProjectRepository
import com.motionloop.app.navigation.Routes
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject
import kotlin.math.hypot

@HiltViewModel
class EditorViewModel @Inject constructor(
    savedState: SavedStateHandle,
    private val projects: ProjectRepository,
) : ViewModel() {

    private val projectId: String = checkNotNull(savedState[Routes.ARG_PROJECT_ID])

    private val _state = MutableStateFlow(EditorUiState())
    val state: StateFlow<EditorUiState> = _state.asStateFlow()

    private val undoStack = ArrayDeque<Geometry>()
    private val redoStack = ArrayDeque<Geometry>()

    init {
        viewModelScope.launch {
            val project = projects.load(projectId)
            if (project == null) {
                _state.update { it.copy(loading = false, error = "Project not found") }
                return@launch
            }
            val preview = runCatching { projects.loadImage(projectId, PREVIEW_EDGE) }.getOrNull()
            _state.update {
                it.copy(
                    loading = false,
                    project = project,
                    preview = preview,
                    error = if (preview == null) "Could not load the photo" else null,
                )
            }
        }
    }

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

            EditorTool.ANCHOR -> commit(
                current(project).let { g -> g.copy(anchors = g.anchors + Anchor(UUID.randomUUID().toString(), p)) },
            )

            EditorTool.FREEZE, EditorTool.UNFREEZE -> _state.update {
                it.copy(
                    liveStroke = MaskStroke(
                        points = listOf(p),
                        radius = s.brushRadius,
                        hardness = s.brushHardness,
                        mode = if (s.tool == EditorTool.FREEZE) MaskMode.FREEZE else MaskMode.UNFREEZE,
                    ),
                )
            }

            EditorTool.ERASE -> eraseNear(project, p)
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
            val len = hypot(arrow.end.x - arrow.start.x, arrow.end.y - arrow.start.y)
            if (len >= MIN_ARROW_LENGTH && project.arrows.size < MAX_ARROWS) {
                commit(current(project).let { it.copy(arrows = it.arrows + arrow) })
            }
        }
        s.liveStroke?.let { stroke ->
            _state.update { it.copy(liveStroke = null) }
            commit(current(project).let { it.copy(strokes = it.strokes + stroke) })
        }
    }

    private fun eraseNear(project: Project, p: NPoint) {
        val arrow = project.arrows.minByOrNull { distanceToSegment(p, it.start, it.end) }
            ?.takeIf { distanceToSegment(p, it.start, it.end) < ERASE_RADIUS }
        val anchor = project.anchors.minByOrNull { hypot(p.x - it.center.x, p.y - it.center.y) }
            ?.takeIf { hypot(p.x - it.center.x, p.y - it.center.y) < ERASE_RADIUS }
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
        val updated = project.copy(arrows = g.arrows, anchors = g.anchors, strokes = g.strokes)
        _state.update {
            it.copy(project = updated, canUndo = undoStack.isNotEmpty(), canRedo = redoStack.isNotEmpty())
        }
        persist(updated)
    }

    private fun current(p: Project) = Geometry(p.arrows, p.anchors, p.strokes)

    // endregion

    private fun persist(project: Project) {
        viewModelScope.launch { projects.save(project) }
    }

    private fun distanceToSegment(p: NPoint, a: NPoint, b: NPoint): Float {
        val abx = b.x - a.x
        val aby = b.y - a.y
        val len2 = abx * abx + aby * aby
        val t = if (len2 < 1e-8f) 0f else (((p.x - a.x) * abx + (p.y - a.y) * aby) / len2).coerceIn(0f, 1f)
        return hypot(p.x - (a.x + abx * t), p.y - (a.y + aby * t))
    }

    private companion object {
        /** GLES 3.0 guarantees 2048 px textures. Export uses the full-size photo. */
        const val PREVIEW_EDGE = 2048
        const val MIN_ARROW_LENGTH = 0.01f
        const val MIN_STROKE_STEP = 0.003f
        const val ERASE_RADIUS = 0.04f
        const val MAX_ARROWS = 64
        const val MAX_HISTORY = 100
    }
}
