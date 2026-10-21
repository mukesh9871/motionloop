package com.motionloop.app.feature.editor

import android.graphics.Bitmap
import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.motionloop.app.core.common.DefaultDispatcher
import com.motionloop.app.data.settings.AppSettings
import com.motionloop.app.data.settings.SettingsState
import com.motionloop.app.domain.pro.Entitlements
import com.motionloop.app.domain.model.Adjustments
import com.motionloop.app.domain.model.Anchor
import com.motionloop.app.domain.model.LoopMode
import com.motionloop.app.domain.model.LoopSettings
import com.motionloop.app.domain.model.MaskKind
import com.motionloop.app.domain.model.MaskMode
import com.motionloop.app.domain.model.MaskStroke
import com.motionloop.app.domain.model.EffectsSettings
import com.motionloop.app.domain.model.MotionArrow
import com.motionloop.app.domain.model.Sticker
import com.motionloop.app.domain.model.StickerKind
import com.motionloop.app.domain.model.MotionPaths
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
import com.motionloop.app.domain.model.BackgroundMode
import com.motionloop.app.domain.model.BackgroundSettings
import com.motionloop.app.domain.model.ParallaxSettings
import com.motionloop.app.domain.model.SelectionMode
import com.motionloop.app.domain.model.SelectionRegion
import com.motionloop.app.domain.model.TextLayer
import com.motionloop.app.render.text.TextRasterizer
import com.motionloop.ml.DepthEstimator
import com.motionloop.ml.Segmenter
import dagger.hilt.android.lifecycle.HiltViewModel
import java.util.UUID
import javax.inject.Inject
import kotlin.math.hypot
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import android.media.AudioManager
import com.motionloop.app.data.audio.AudioDecoder
import com.motionloop.app.render.AudioClock
import com.motionloop.app.data.audio.AudioLoopPlayer
import com.motionloop.app.domain.audio.AmbientSynth
import com.motionloop.app.domain.audio.AudioSettings
import com.motionloop.app.domain.audio.AudioSource
import com.motionloop.app.domain.audio.BeatDetector
import com.motionloop.app.domain.audio.ClipAudio
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.delay
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
    private val depthEstimator: DepthEstimator,
    @DefaultDispatcher private val cpu: CoroutineDispatcher,
    audioManager: AudioManager,
    entitlements: Entitlements,
    appSettings: AppSettings,
) : ViewModel() {

    val isPro: StateFlow<Boolean> = entitlements.isPro
    val settings: StateFlow<SettingsState> = appSettings.state

    private val projectId: String = checkNotNull(savedState[Routes.ARG_PROJECT_ID])

    // Above _state and init: the initial state reads it, and init starts a collector that uses it.
    // Explicit types: player and _state refer to each other through lambdas.
    private val player: AudioLoopPlayer = AudioLoopPlayer(audioManager).apply { onFocusLost = { setPlaying(false) } }
    private val _state: MutableStateFlow<EditorUiState> = MutableStateFlow(EditorUiState(audioClock = AudioClock { player.positionSeconds() }))
    val state: StateFlow<EditorUiState> = _state.asStateFlow()

    private var lutJob: Job? = null
    private var skyMaskJob: Job? = null
    private var skyPhotoJob: Job? = null
    private var subjectMaskJob: Job? = null
    private var backgroundPhotoJob: Job? = null
    private val textJobs = HashMap<String, Job>()
    private val textJobKeys = HashMap<String, TextLayer>()
    private var soundJob: Job? = null
    private var loadedSound: Pair<AudioSource, Any>? = null
    private var dragStart: NPoint? = null
    private var dragOrigin: NPoint? = null
    private val undoStack = ArrayDeque<Geometry>()
    private val redoStack = ArrayDeque<Geometry>()

    // Panel where the running model action started: its result message belongs there,
    // even if the user switched panels while it ran.
    private var modelOrigin: EditorPanel? = null

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
            assets.prune(projectId, MaskKind.DEPTH, setOf(project.parallax.depthRev))
            assets.prune(projectId, MaskKind.SUBJECT, setOf(project.subject.maskRev))
            assets.prune(projectId, MaskKind.SELECTION, project.selections.map { it.rev }.toSet())
            assets.pruneImages(projectId, ImageKind.BACKGROUND, setOf(project.background.photoRev))
            assets.pruneAudio(projectId, setOf(project.audio.fileRev))
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
            loadDepth(project.parallax.depthRev)
            loadSubjectMask(project.subject.maskRev)
            loadBackgroundPhoto(project.background.photoRev)
            loadSelections(project.selections)
            rasterizeTexts(project.texts)
            loadSound(project.audio)
        }
        // The preview sound follows play and pause.
        viewModelScope.launch {
            _state.map { it.playing }.distinctUntilChanged().collect { playing ->
                if (playing) player.play() else player.pause()
            }
        }
    }

    // region sound (11c)

    /** Sound settings, not part of undo. Reloads the preview loop only when the source changed. */
    fun setAudio(change: (AudioSettings) -> AudioSettings) {
        val p = _state.value.project ?: return
        val updated = p.copy(audio = change(p.audio))
        _state.update { it.copy(project = updated) }
        persist(updated)
        player.setVolume(updated.audio.volume)
        loadSound(updated.audio)
    }

    fun importAudio(uri: Uri) = runModel("Loading the sound") {
        val rev = assets.importAudio(projectId, uri)
        // Check that it decodes before the project points at it: an unreadable file would break exports.
        val file = assets.audioFile(projectId, rev) ?: error("The audio file could not be stored")
        withContext(cpu) { AudioDecoder.decode(file, maxSeconds = 2) }
        setAudio { it.copy(source = AudioSource.FILE, fileRev = rev) }
    }

    private fun loadSound(audio: AudioSettings) {
        val key: Pair<AudioSource, Any>? = when {
            !audio.hasSound -> null
            audio.source == AudioSource.AMBIENT -> AudioSource.AMBIENT to audio.ambient
            else -> AudioSource.FILE to audio.fileRev
        }
        if (key == loadedSound) return
        loadedSound = key
        soundJob?.cancel()
        if (key == null) {
            player.setLoop(null, null)
            _state.update { it.copy(beatEnvelope = null) }
            return
        }
        soundJob = viewModelScope.launch {
            try {
                val pcm = withContext(cpu) {
                    if (audio.source == AudioSource.AMBIENT) {
                        AmbientSynth.generate(audio.ambient, PREVIEW_SOUND_SECONDS)
                    } else {
                        val file = assets.audioFile(projectId, audio.fileRev) ?: error("The audio file is missing")
                        // The preview loops the first seconds: a static audio buffer must stay small.
                        ClipAudio.fit(AudioDecoder.decode(file, maxSeconds = PREVIEW_SOUND_SECONDS.toInt()), PREVIEW_SOUND_SECONDS, 1f)
                    }
                }
                val envelope = withContext(cpu) { BeatDetector.envelope(BeatDetector.beats(pcm), pcm.seconds) }
                // The track is filled off the main thread, then handed over at once (no suspension between).
                val track = withContext(cpu) { player.build(pcm) }
                player.setLoop(pcm, track)
                player.setVolume(_state.value.project?.audio?.volume ?: 1f)
                if (_state.value.playing) player.play()
                _state.update { it.copy(beatEnvelope = envelope) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                loadedSound = null
                _state.update { it.copy(messagePanel = it.panel, message = "Could not play that sound: ${e.message}") }
            }
        }
    }

    override fun onCleared() {
        player.release()
    }

    // endregion

    // region text (11e)

    /** Adds a text layer near the top third, selected and ready to edit. */
    fun addText() {
        val p = _state.value.project ?: return
        if (p.texts.size >= TextLayer.MAX_LAYERS) {
            _state.update { it.copy(messagePanel = it.panel, message = "Up to ${TextLayer.MAX_LAYERS} text layers.") }
            return
        }
        // Behind the subject by default. Without a subject it simply draws over the photo until
        // one is found, so find it now: the panel offers tap and paint fixes if it is wrong.
        val layer = TextLayer(
            id = UUID.randomUUID().toString(),
            behindSubject = true,
            y = 0.3f + 0.08f * p.texts.size,
        )
        setTexts(p.texts + layer)
        _state.update { it.copy(selectedTextId = layer.id) }
        // Only for the first text: later ones would repeat a failed search on photos without a subject.
        if (!p.subject.hasArea && p.texts.isEmpty()) detectSubject(record = false)
    }

    fun selectText(id: String?) = _state.update { it.copy(selectedTextId = id) }

    /** Edits the selected layer. Text edits are not part of undo, like other settings. */
    fun updateSelectedText(change: (TextLayer) -> TextLayer) {
        val p = _state.value.project ?: return
        val id = _state.value.selectedTextId ?: return
        setTexts(p.texts.map { if (it.id == id) change(it).let { t -> t.copy(text = t.text.take(TextLayer.MAX_LENGTH)) } else it })
    }

    fun deleteSelectedText() {
        val p = _state.value.project ?: return
        val id = _state.value.selectedTextId ?: return
        textJobs.remove(id)?.cancel()
        textJobKeys.remove(id)
        setTexts(p.texts.filterNot { it.id == id })
        _state.update { it.copy(selectedTextId = null, renderedTexts = it.renderedTexts - id) }
        // The subject tools live in the text list: with no text left, go back to moving texts.
        if (_state.value.project?.texts.isNullOrEmpty()) _state.update { it.copy(tool = EditorTool.TEXT_MOVE) }
    }

    private fun setTexts(texts: List<TextLayer>, persistNow: Boolean = true) {
        val p = _state.value.project ?: return
        val updated = p.copy(texts = texts)
        _state.update { it.copy(project = updated) }
        if (persistNow) persist(updated)
        rasterizeTexts(texts)
    }

    /** Redraws only layers whose look changed. Older bitmaps are left to the GC: the GL thread may still upload them. */
    private fun rasterizeTexts(texts: List<TextLayer>) {
        for (layer in texts) {
            val key = layer.renderKey()
            if (_state.value.renderedTexts[layer.id]?.key == key) {
                // Already showing this look: a pending render of another look is stale.
                textJobs.remove(layer.id)?.cancel()
                textJobKeys.remove(layer.id)
                continue
            }
            // Already rendering exactly this look (for example during a drag): let it finish.
            if (textJobKeys[layer.id] == key && textJobs[layer.id]?.isActive == true) continue
            textJobs[layer.id]?.cancel()
            textJobKeys[layer.id] = key
            textJobs[layer.id] = viewModelScope.launch {
                // Slider drags and typing change the look many times a second: draw only the last one.
                delay(TEXT_DEBOUNCE_MS)
                val rendered = withContext(cpu) { TextRasterizer.render(layer, TEXT_REF_WIDTH) }
                _state.update { s ->
                    // Only if this is still the layer's current look (and the layer still exists).
                    val current = s.project?.texts?.firstOrNull { it.id == layer.id }?.renderKey()
                    if (current == key) s.copy(renderedTexts = s.renderedTexts + (layer.id to rendered)) else s
                }
            }
        }
    }

    /** Text panel touch: pick the text under the finger, then drag it. */
    private fun textDown(p: NPoint) {
        val project = _state.value.project ?: return
        // Topmost first: front layers are drawn after behind layers, then in list order.
        val behindFirst = project.texts.sortedBy { !(it.behindSubject && project.subject.hasArea) }
        val hit = behindFirst.lastOrNull { t -> hitsText(t, p, project.aspect) }
        _state.update { it.copy(selectedTextId = hit?.id) }
        dragStart = if (hit != null) p else null
        dragOrigin = hit?.let { NPoint(it.x, it.y) }
    }

    private fun textMove(p: NPoint) {
        val start = dragStart ?: return
        val origin = dragOrigin ?: return
        val project = _state.value.project ?: return
        val id = _state.value.selectedTextId ?: return
        val nx = (origin.x + p.x - start.x).coerceIn(0f, 1f)
        val ny = (origin.y + p.y - start.y).coerceIn(0f, 1f)
        // Live position only: saved once on release.
        setTexts(project.texts.map { if (it.id == id) it.copy(x = nx, y = ny) else it }, persistNow = false)
    }

    private fun textUp() {
        if (dragStart != null) _state.value.project?.let(::persist)
        dragStart = null
        dragOrigin = null
    }

    /** Hit test against the rotated text box (from the rasterized size), with a little slack. */
    private fun hitsText(t: TextLayer, p: NPoint, aspect: Float): Boolean {
        val r = _state.value.renderedTexts[t.id] ?: return distance(p, NPoint(t.x, t.y), aspect) < 0.08f
        // The raster has padding for outline and glow: hit the glyph area plus a small finger margin.
        val pad = TextRasterizer.PAD_EM * t.size
        val halfW = r.main.width / (2f * r.refWidth) - pad + 0.02f
        val halfH = r.main.height / (2f * r.refWidth) - pad + 0.02f
        // Into width units around the center, then undo the rotation.
        val dx = p.x - t.x
        val dy = (p.y - t.y) / aspect
        val a = -Math.toRadians(t.rotation.toDouble())
        val lx = (dx * kotlin.math.cos(a) - dy * kotlin.math.sin(a)).toFloat()
        val ly = (dx * kotlin.math.sin(a) + dy * kotlin.math.cos(a)).toFloat()
        return kotlin.math.abs(lx) <= halfW && kotlin.math.abs(ly) <= halfH
    }

    // endregion

    // region cutout (11d) and tap-to-select (11a), all on the shared segmenter

    /** Background settings, not part of undo. */
    fun setBackground(change: (BackgroundSettings) -> BackgroundSettings) {
        val p = _state.value.project ?: return
        val updated = p.copy(background = change(p.background))
        _state.update { it.copy(project = updated) }
        persist(updated)
    }

    fun setSubjectFeather(feather: Float) {
        val p = _state.value.project ?: return
        val updated = p.copy(subject = p.subject.copy(feather = feather))
        _state.update { it.copy(project = updated) }
        persist(updated)
    }

    /** Finds the main subject automatically (ML Kit, with the interactive model as fallback). */
    /**
     * Finds the main subject automatically. [record] false applies it like a setting (no history
     * entry), for the automatic search when the first text is added: texts are not undoable either.
     */
    fun detectSubject(record: Boolean = true) = runModel("Finding the subject") { preview ->
        val mask = withContext(cpu) { segmenter.segmentSubject(preview) }
        if (mask.coverage() < 0.005f) {
            _state.update { it.copy(messagePanel = messageOrigin(), message = "No clear subject found. Tap it or paint it to select it.") }
            return@runModel
        }
        val rev = assets.saveMask(projectId, MaskKind.SUBJECT, mask)
        _state.update { it.copy(subjectMask = mask, subjectMaskRev = rev) }
        val project = _state.value.project ?: return@runModel
        if (record) {
            commit(current(project).copy(subjectMaskRev = rev))
        } else {
            val updated = project.copy(subject = project.subject.copy(maskRev = rev))
            _state.update { it.copy(project = updated) }
            persist(updated)
        }
    }

    private fun subjectTap(p: NPoint, add: Boolean) =
        runModel(if (add) "Adding to the subject" else "Removing from the subject") { preview ->
            val region = withContext(cpu) { segmenter.segmentAt(preview, p.x, p.y) }
            val s = _state.value
            val subject = s.project?.subject ?: return@runModel
            val base = when {
                subject.maskRev == 0 -> null
                s.subjectMaskRev == subject.maskRev && s.subjectMask != null -> s.subjectMask
                else -> assets.loadMask(projectId, MaskKind.SUBJECT, subject.maskRev)
            }
            val combined = when {
                base != null -> withContext(cpu) { base.combine(region, subtract = !add) }
                add -> region
                else -> {
                    val hint = if (subject.strokes.isNotEmpty()) "Use Erase to remove painted areas." else "There is no subject to remove from yet."
                    _state.update { it.copy(messagePanel = messageOrigin(), message = hint) }
                    return@runModel
                }
            }
            val rev = assets.saveMask(projectId, MaskKind.SUBJECT, combined)
            val project = _state.value.project ?: return@runModel
            if (project.subject.maskRev != subject.maskRev) return@runModel
            _state.update { it.copy(subjectMask = combined, subjectMaskRev = rev) }
            commit(current(project).copy(subjectMaskRev = rev))
        }

    /** Tap-to-select: the object under [p] becomes a freeze region or the only moving region. */
    private fun selectRegion(p: NPoint, mode: SelectionMode) =
        runModel(
            when (mode) {
                SelectionMode.FREEZE -> "Freezing the object"
                SelectionMode.ANIMATE_ONLY -> "Selecting the object"
                SelectionMode.RIPPLE -> "Finding the water"
            },
        ) { preview ->
            val region = withContext(cpu) { segmenter.segmentAt(preview, p.x, p.y) }
            val rev = assets.saveMask(projectId, MaskKind.SELECTION, region)
            _state.update { it.copy(selectionMasks = it.selectionMasks + (rev to region)) }
            val project = _state.value.project ?: return@runModel
            val g = current(project)
            commit(g.copy(selections = g.selections + SelectionRegion(rev, mode)))
        }

    /** Removes the Hold and Only this regions (undoable). Water regions belong to Effects. */
    fun clearSelections() {
        val project = _state.value.project ?: return
        val g = current(project)
        val kept = g.selections.filter { it.mode == SelectionMode.RIPPLE }
        if (kept.size != g.selections.size) commit(g.copy(selections = kept))
    }

    /** Removes the water regions (undoable). */
    fun clearWater() {
        val project = _state.value.project ?: return
        val g = current(project)
        val kept = g.selections.filterNot { it.mode == SelectionMode.RIPPLE }
        if (kept.size != g.selections.size) commit(g.copy(selections = kept))
    }

    // region effects: stickers, dispersion, ripples

    /** Effects settings, not part of undo. */
    fun setEffects(change: (EffectsSettings) -> EffectsSettings) {
        val p = _state.value.project ?: return
        val updated = p.copy(effects = change(p.effects))
        _state.update { it.copy(project = updated) }
        persist(updated)
    }

    fun addSticker(kind: StickerKind) {
        val p = _state.value.project ?: return
        if (p.effects.stickers.size >= Sticker.MAX) {
            _state.update { it.copy(messagePanel = it.panel, message = "Up to ${Sticker.MAX} stickers.") }
            return
        }
        // Staggered so several new stickers do not land exactly on top of each other.
        val n = p.effects.stickers.size
        val sticker = Sticker(UUID.randomUUID().toString(), kind, x = 0.5f + 0.06f * (n % 3 - 1), y = 0.45f + 0.05f * (n % 4))
        setEffects { it.copy(stickers = it.stickers + sticker) }
        _state.update { it.copy(selectedStickerId = sticker.id, tool = EditorTool.STICKER_MOVE) }
    }

    fun selectSticker(id: String?) = _state.update { it.copy(selectedStickerId = id) }

    fun updateSelectedSticker(change: (Sticker) -> Sticker) {
        val id = _state.value.selectedStickerId ?: return
        setEffects { e -> e.copy(stickers = e.stickers.map { if (it.id == id) change(it) else it }) }
    }

    fun deleteSelectedSticker() {
        val id = _state.value.selectedStickerId ?: return
        setEffects { e -> e.copy(stickers = e.stickers.filterNot { it.id == id }) }
        _state.update { it.copy(selectedStickerId = null) }
    }

    fun clearDispersion() = setEffects { it.copy(dispersion = it.dispersion.copy(strokes = emptyList())) }

    fun clearOverlayArea() = setOverlay { it.copy(areaStrokes = emptyList()) }

    private fun stickerDown(p: NPoint) {
        val project = _state.value.project ?: return
        // Topmost (last drawn) first. Hit radius: the sticker's half size, at least a finger.
        val hit = project.effects.stickers.lastOrNull { distance(p, NPoint(it.x, it.y), project.aspect) < maxOf(it.size / 2f, 0.05f) }
        _state.update { it.copy(selectedStickerId = hit?.id) }
        dragStart = if (hit != null) p else null
        dragOrigin = hit?.let { NPoint(it.x, it.y) }
    }

    private fun stickerMove(p: NPoint) {
        val start = dragStart ?: return
        val origin = dragOrigin ?: return
        val project = _state.value.project ?: return
        val id = _state.value.selectedStickerId ?: return
        val nx = (origin.x + p.x - start.x).coerceIn(0f, 1f)
        val ny = (origin.y + p.y - start.y).coerceIn(0f, 1f)
        // Live position only: saved once on release.
        val effects = project.effects.copy(stickers = project.effects.stickers.map { if (it.id == id) it.copy(x = nx, y = ny) else it })
        _state.update { it.copy(project = project.copy(effects = effects)) }
    }

    private fun stickerUp() {
        if (dragStart != null) _state.value.project?.let(::persist)
        dragStart = null
        dragOrigin = null
    }

    // endregion

    fun importBackgroundPhoto(uri: Uri) = runModel("Loading the photo") {
        val rev = assets.importImage(projectId, ImageKind.BACKGROUND, uri)
        setBackground { it.copy(mode = BackgroundMode.PHOTO, photoRev = rev) }
        loadBackgroundPhoto(rev)
    }

    private fun loadSubjectMask(rev: Int) {
        subjectMaskJob?.cancel()
        if (rev == _state.value.subjectMaskRev && (rev == 0 || _state.value.subjectMask != null)) return
        subjectMaskJob = viewModelScope.launch {
            val mask = assets.loadMask(projectId, MaskKind.SUBJECT, rev)
            if (rev != 0 && mask == null) {
                // File gone or damaged: drop the area instead of replacing the whole frame.
                val p = _state.value.project ?: return@launch
                if (p.subject.maskRev == rev) {
                    val updated = p.copy(subject = p.subject.copy(maskRev = 0))
                    _state.update { it.copy(project = updated, messagePanel = it.panel, message = "The subject mask is missing. Find the subject again in Cutout.") }
                    persist(updated)
                }
                return@launch
            }
            _state.update { it.copy(subjectMask = mask, subjectMaskRev = rev) }
        }
    }

    private fun loadBackgroundPhoto(rev: Int) {
        if (rev == 0) return
        backgroundPhotoJob?.cancel()
        backgroundPhotoJob = viewModelScope.launch {
            val photo = assets.loadImage(projectId, ImageKind.BACKGROUND, rev, SKY_PHOTO_EDGE)
            _state.update { it.copy(backgroundPhoto = photo, backgroundPhotoRev = rev) }
        }
    }

    /** Loads masks for regions not loaded yet (also after undo or redo brings one back). */
    private fun loadSelections(regions: List<SelectionRegion>) {
        val missing = regions.map { it.rev }.filter { it !in _state.value.selectionMasks }
        if (missing.isEmpty()) return
        viewModelScope.launch {
            val loaded = missing.mapNotNull { rev -> assets.loadMask(projectId, MaskKind.SELECTION, rev)?.let { rev to it } }
            _state.update { it.copy(selectionMasks = it.selectionMasks + loaded) }
            val lost = missing.toSet() - loaded.map { it.first }.toSet()
            if (lost.isNotEmpty()) {
                // Regions whose files are gone cannot be shown: remove them rather than keep invisible ones.
                val p = _state.value.project ?: return@launch
                val updated = p.copy(selections = p.selections.filterNot { it.rev in lost })
                // Also out of the history, or undo would bring them back and remove them again.
                undoStack.replaceAll { g -> g.copy(selections = g.selections.filterNot { it.rev in lost }) }
                redoStack.replaceAll { g -> g.copy(selections = g.selections.filterNot { it.rev in lost }) }
                _state.update { it.copy(project = updated, messagePanel = it.panel, message = "Some selected objects could not be loaded and were removed.") }
                persist(updated)
            }
        }
    }

    // endregion

    // region 3D

    /** Settings, not part of undo. */
    fun setParallax(change: (ParallaxSettings) -> ParallaxSettings) {
        val p = _state.value.project ?: return
        val updated = p.copy(parallax = change(p.parallax))
        _state.update { it.copy(project = updated) }
        persist(updated)
    }

    /** Runs the depth model once per project, the result is stored and reused. */
    fun createDepth() = runModel("Measuring depth") { preview ->
        val depth = withContext(cpu) { depthEstimator.estimate(preview) }
        val rev = assets.saveMask(projectId, MaskKind.DEPTH, depth)
        _state.update { it.copy(depth = depth, depthRev = rev) }
        setParallax { it.copy(depthRev = rev, enabled = true) }
    }

    private fun loadDepth(rev: Int) {
        if (rev == 0) return
        viewModelScope.launch {
            val depth = assets.loadMask(projectId, MaskKind.DEPTH, rev)
            if (depth == null) {
                // The file is gone or damaged: offer to create it again instead of a dead 3D panel.
                setParallax { it.copy(depthRev = 0, enabled = false) }
                _state.update { it.copy(messagePanel = it.panel, message = "The depth map is missing. Create it again in the 3D panel.") }
                return@launch
            }
            _state.update { it.copy(depth = depth, depthRev = rev) }
        }
    }

    // endregion

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
            _state.update { it.copy(messagePanel = messageOrigin(), message = "No clear sky found. Tap the sky to select it.") }
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
        val sky = s.project?.sky ?: return@runModel
        // The stored mask may still be loading right after the editor opened: read it directly.
        val base = when {
            sky.maskRev == 0 -> null
            s.skyMaskRev == sky.maskRev && s.skyMask != null -> s.skyMask
            else -> assets.loadMask(projectId, MaskKind.SKY, sky.maskRev)
        }
        val combined = when {
            base != null -> withContext(cpu) { base.combine(region, subtract = !add) }
            add -> region
            else -> {
                // No model area: taps only edit the model area, painted strokes need Erase.
                val hint = if (sky.strokes.isNotEmpty()) "Use Erase to remove painted sky." else "There is no sky area to remove yet."
                _state.update { it.copy(messagePanel = messageOrigin(), message = hint) }
                return@runModel
            }
        }
        val rev = assets.saveMask(projectId, MaskKind.SKY, combined)
        val project = _state.value.project ?: return@runModel
        // The area changed under us (undo while the model ran): drop this result.
        if (project.sky.maskRev != sky.maskRev) return@runModel
        _state.update { it.copy(skyMask = combined, skyMaskRev = rev) }
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
        val origin = _state.value.panel
        viewModelScope.launch {
            try {
                modelOrigin = origin
                block(preview)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                val reason = e.message ?: e.javaClass.simpleName
                _state.update { it.copy(messagePanel = messageOrigin(), message = "That did not work: $reason") }
            } finally {
                modelOrigin = null
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
        // A newer import replaces an older one still decoding.
        skyPhotoJob?.cancel()
        skyPhotoJob = viewModelScope.launch {
            val photo = assets.loadImage(projectId, ImageKind.SKY_PHOTO, rev, SKY_PHOTO_EDGE)
            _state.update { it.copy(skyPhoto = photo, skyPhotoRev = rev) }
        }
    }

    // endregion

    // region color

    fun setPanel(panel: EditorPanel) =
        _state.update {
            // Leaving a panel clears its own messages. A result that belongs to another panel waits there.
            val keep = it.messagePanel != null && it.messagePanel != it.panel
            it.copy(
                panel = panel,
                tool = panel.defaultTool,
                liveArrow = null,
                liveStroke = null,
                message = it.message.takeIf { keep },
                messagePanel = it.messagePanel.takeIf { keep },
            )
        }

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
                _state.update { it.copy(messagePanel = it.panel, message = e.message ?: "Could not import that file") }
            }
        }
    }

    fun dismissMessage() = _state.update { it.copy(message = null, messagePanel = null) }

    private fun messageOrigin(): EditorPanel = modelOrigin ?: _state.value.panel

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
                _state.update { it.copy(messagePanel = it.panel, message = "The imported LUT file is missing. Import it again.") }
            }
        }
    }

    // endregion

    // region tools and playback

    fun setTool(tool: EditorTool) = _state.update { it.copy(tool = tool, playing = false) }
    fun setBrushRadius(r: Float) = _state.update { it.copy(brushRadius = r) }
    fun setBrushHardness(h: Float) = _state.update { it.copy(brushHardness = h) }
    fun togglePlay() = _state.update { it.copy(playing = !it.playing) }
    fun setPlaying(playing: Boolean) {
        _state.update { it.copy(playing = playing) }
    }

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
                _state.update { it.copy(liveArrow = MotionArrow(UUID.randomUUID().toString(), p, p, path = listOf(p))) }

            // The flow shader takes at most MAX_ITEMS anchors, so do not accept more than it can use.
            EditorTool.ANCHOR -> if (project.anchors.size < FlowFieldPass.MAX_ITEMS) {
                commit(
                    current(project).let { g -> g.copy(anchors = g.anchors + Anchor(UUID.randomUUID().toString(), p)) },
                )
            }

            EditorTool.FREEZE, EditorTool.UNFREEZE, EditorTool.SKY_PAINT, EditorTool.SKY_ERASE,
            EditorTool.SUBJECT_RESTORE, EditorTool.SUBJECT_ERASE,
            EditorTool.DISPERSE_PAINT, EditorTool.DISPERSE_ERASE, EditorTool.OVERLAY_PAINT, EditorTool.OVERLAY_ERASE,
            -> _state.update {
                // Freeze and every "paint" tool add to their mask, the erase tools remove from it.
                val adds = s.tool == EditorTool.FREEZE || s.tool == EditorTool.SKY_PAINT || s.tool == EditorTool.SUBJECT_RESTORE ||
                    s.tool == EditorTool.DISPERSE_PAINT || s.tool == EditorTool.OVERLAY_PAINT
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
            EditorTool.SUBJECT_ADD -> subjectTap(p, add = true)
            EditorTool.SUBJECT_REMOVE -> subjectTap(p, add = false)
            EditorTool.TEXT_MOVE -> textDown(p)
            EditorTool.SELECT_FREEZE -> selectRegion(p, SelectionMode.FREEZE)
            EditorTool.SELECT_ANIMATE -> selectRegion(p, SelectionMode.ANIMATE_ONLY)
            EditorTool.WATER -> selectRegion(p, SelectionMode.RIPPLE)
            EditorTool.STICKER_MOVE -> stickerDown(p)
        }
    }

    fun pointerMove(p: NPoint) {
        if (_state.value.tool == EditorTool.TEXT_MOVE) {
            textMove(p)
            return
        }
        if (_state.value.tool == EditorTool.STICKER_MOVE) {
            stickerMove(p)
            return
        }
        _state.update { s ->
            when {
                s.liveArrow != null -> {
                    // Free-hand: the path follows the finger. Tiny moves are skipped to keep it short.
                    val last = s.liveArrow.path.lastOrNull() ?: s.liveArrow.start
                    if (hypot(p.x - last.x, p.y - last.y) < MIN_PATH_STEP) s.copy(liveArrow = s.liveArrow.copy(end = p))
                    else s.copy(liveArrow = s.liveArrow.copy(end = p, path = s.liveArrow.path + p))
                }
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
        if (_state.value.tool == EditorTool.TEXT_MOVE) {
            textUp()
            return
        }
        if (_state.value.tool == EditorTool.STICKER_MOVE) {
            stickerUp()
            return
        }
        val s = _state.value
        val project = s.project ?: return
        s.liveArrow?.let { live ->
            _state.update { it.copy(liveArrow = null) }
            val raw = if (live.path.lastOrNull() == live.end) live.path else live.path + live.end
            val pts = MotionPaths.simplify(raw, project.aspect)
            // A path that is nearly straight is stored as a plain arrow.
            val arrow = live.copy(path = if (pts.size > 2) pts else emptyList())
            val len = MotionPaths.length(arrow.points, project.aspect)
            val budget = FlowFieldPass.MAX_SEGMENTS - MotionPaths.segmentCount(project.arrows)
            when {
                len < MIN_ARROW_LENGTH -> Unit
                arrow.points.size - 1 > budget ->
                    _state.update { it.copy(messagePanel = EditorPanel.ANIMATE, message = "That is as many motion paths as fit. Erase one to draw more.") }
                else -> commit(current(project).let { it.copy(arrows = it.arrows + arrow) })
            }
        }
        s.liveStroke?.let { stroke ->
            _state.update { it.copy(liveStroke = null) }
            // Effect areas are settings, like the rest of their panel: not part of undo.
            // At the cap, refuse new strokes rather than silently dropping the first (often the main) ones.
            val full = (if (s.isEffectBrush) project.effects.dispersion.strokes.size else project.overlay.areaStrokes.size) >= MAX_AREA_STROKES
            // Erasing always works, so a full area can still be trimmed.
            if ((s.isEffectBrush || s.isOverlayBrush) && full && stroke.mode == MaskMode.FREEZE) {
                _state.update { it.copy(messagePanel = it.panel, message = "That area holds as many strokes as it can. Erase or start over to paint more.") }
                return
            }
            if (s.isEffectBrush) {
                setEffects { e -> e.copy(dispersion = e.dispersion.copy(strokes = e.dispersion.strokes + stroke)) }
                return
            }
            if (s.isOverlayBrush) {
                setOverlay { o -> o.copy(areaStrokes = o.areaStrokes + stroke) }
                return
            }
            val g = current(project)
            commit(
                when {
                    s.isSkyBrush -> g.copy(skyStrokes = g.skyStrokes + stroke)
                    s.isSubjectBrush -> g.copy(subjectStrokes = g.subjectStrokes + stroke)
                    else -> g.copy(strokes = g.strokes + stroke)
                },
            )
        }
    }

    private fun eraseNear(project: Project, p: NPoint) {
        val aspect = project.aspect
        val arrow = project.arrows.minByOrNull { MotionPaths.distanceTo(it, p, aspect) }
            ?.takeIf { MotionPaths.distanceTo(it, p, aspect) < ERASE_RADIUS }
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

    private fun commit(next: Geometry) {
        val project = _state.value.project ?: return
        undoStack.addLast(current(project))
        if (undoStack.size > MAX_HISTORY) undoStack.removeFirst()
        redoStack.clear()
        apply(project, next)
    }

    /** Drops loaded selection masks that neither the project nor the history still refer to. */
    private fun pruneSelectionMasks(project: Project) {
        val live = HashSet<Int>()
        project.selections.forEach { live += it.rev }
        undoStack.forEach { g -> g.selections.forEach { live += it.rev } }
        redoStack.forEach { g -> g.selections.forEach { live += it.rev } }
        _state.update { s -> if (s.selectionMasks.keys.all { it in live }) s else s.copy(selectionMasks = s.selectionMasks.filterKeys { it in live }) }
    }

    private fun apply(project: Project, g: Geometry) {
        val updated = project.copy(
            arrows = g.arrows,
            anchors = g.anchors,
            strokes = g.strokes,
            sky = project.sky.copy(maskRev = g.skyMaskRev, strokes = g.skyStrokes),
            subject = project.subject.copy(maskRev = g.subjectMaskRev, strokes = g.subjectStrokes),
            selections = g.selections,
        )
        loadSkyMask(g.skyMaskRev)
        loadSubjectMask(g.subjectMaskRev)
        loadSelections(g.selections)
        _state.update {
            it.copy(project = updated, canUndo = undoStack.isNotEmpty(), canRedo = redoStack.isNotEmpty())
        }
        pruneSelectionMasks(updated)
        persist(updated)
    }

    private fun current(p: Project) = Geometry(
        p.arrows, p.anchors, p.strokes, p.sky.maskRev, p.sky.strokes, p.subject.maskRev, p.subject.strokes, p.selections,
    )

    // endregion

    /** Queued on the repository's app-wide serial writer, so it survives leaving the screen. */
    private fun persist(project: Project) = projects.saveInBackground(project)

    // Distances in units of image width: y is scaled by height / width = 1 / aspect,
    // so hit areas are round on screen for any photo shape.
    private fun distance(a: NPoint, b: NPoint, aspect: Float): Float =
        hypot(b.x - a.x, (b.y - a.y) / aspect)


    private companion object {
        /** GLES 3.0 guarantees 2048 px textures. Export uses the full-size photo. */
        const val PREVIEW_EDGE = 2048
        const val MIN_ARROW_LENGTH = 0.01f
        const val MIN_STROKE_STEP = 0.003f
        const val MIN_PATH_STEP = 0.006f

        /** Paint strokes an effect area accepts, so it stays cheap to draw. Erase strokes are always taken. */
        const val MAX_AREA_STROKES = 60
        const val ERASE_RADIUS = 0.04f
        const val MAX_HISTORY = 100
        const val SKY_PHOTO_EDGE = 2048

        /** Text is rasterized against this image width: sharp at 1080p and still small. */
        const val TEXT_REF_WIDTH = 1440
        const val TEXT_DEBOUNCE_MS = 40L
        const val PREVIEW_SOUND_SECONDS = 12f
    }
}
