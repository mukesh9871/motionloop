package com.motionloop.app.export

import com.motionloop.app.data.audio.AudioDecoder
import com.motionloop.app.domain.audio.AmbientSynth
import com.motionloop.app.domain.audio.AudioSource
import com.motionloop.app.domain.audio.BeatDetector
import com.motionloop.app.domain.audio.ClipAudio
import com.motionloop.app.domain.audio.Pcm
import com.motionloop.app.domain.model.BackgroundMode
import com.motionloop.app.domain.model.LoopTiming
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import com.motionloop.app.domain.model.MaskKind
import com.motionloop.app.domain.model.TextAnimation
import com.motionloop.app.domain.model.Project
import com.motionloop.app.domain.model.SkyStyle
import com.motionloop.app.domain.repository.ImageKind
import com.motionloop.app.domain.repository.ProjectAssetRepository
import com.motionloop.app.render.LoopRenderGraph
import com.motionloop.app.render.SelectionLayer
import com.motionloop.app.render.passes.TextDraw
import com.motionloop.app.render.text.TextRasterizer

/** Tap-to-select regions of the freeze mask, loaded for an export. */
internal suspend fun loadSelections(project: Project, assets: ProjectAssetRepository): List<SelectionLayer> =
    // Missing files are skipped: the editor removes such regions when the project is opened.
    project.selections.mapNotNull { region ->
        assets.loadMask(project.id, MaskKind.SELECTION, region.rev)?.let { SelectionLayer(it, region.mode) }
    }

/**
 * Subject mask and background for an export. Transparent backgrounds become black in video,
 * since MP4 and GIF here carry no alpha.
 */
internal suspend fun applySubject(
    graph: LoopRenderGraph,
    project: Project,
    assets: ProjectAssetRepository,
    outputLongEdge: Int,
    cutout: Boolean = false,
) {
    val subject = project.subject
    if (!subject.hasArea) return
    val mask = assets.loadMask(project.id, MaskKind.SUBJECT, subject.maskRev)
    // A stored area whose file is gone would replace the whole frame, or cut out nothing.
    if (subject.maskRev != 0 && mask == null) error("The subject mask is missing. Find the subject again in Cutout.")
    if (cutout) {
        // PNG cutout: keep the photo's own colors, the mask becomes alpha. Blending over black here
        // and applying alpha again would darken the edges twice.
        graph.setSubject(subject, mask, project.background.copy(mode = BackgroundMode.NONE), null, tintOnly = false, checker = false)
        return
    }
    val photo = if (project.background.mode == BackgroundMode.PHOTO) {
        assets.loadImage(project.id, ImageKind.BACKGROUND, project.background.photoRev, outputLongEdge)
    } else {
        null
    }
    graph.setSubject(subject, mask, project.background, photo, tintOnly = false, checker = false)
    // Uploaded to the GPU, and the graph only keeps it for an identity check.
    photo?.recycle()
}

/**
 * Rasterizes the project's text layers for an export at [outputWidth] (at least the editor's
 * resolution, so text never looks softer than in the preview). The caller recycles the bitmaps.
 */
internal fun renderTexts(project: Project, outputWidth: Int, animated: Boolean = true): List<TextDraw> =
    project.texts.map {
        val layer = if (animated) it else it.copy(animation = TextAnimation.NONE)
        TextDraw(layer, TextRasterizer.render(layer, maxOf(1440, outputWidth)))
    }

/**
 * The project's sound fitted to exactly [seconds], or null when it has none. Ambient sounds are
 * generated at the clip length, so they loop as seamlessly as the picture. CPU work on [cpu].
 */
internal suspend fun buildClipAudio(project: Project, seconds: Int, assets: ProjectAssetRepository, cpu: CoroutineDispatcher): Pcm? {
    val audio = project.audio
    if (!audio.hasSound) return null
    return withContext(cpu) {
        when (audio.source) {
            AudioSource.AMBIENT -> ClipAudio.scaled(AmbientSynth.generate(audio.ambient, seconds.toFloat()), audio.volume)
            AudioSource.FILE -> {
                val file = assets.audioFile(project.id, audio.fileRev) ?: error("The audio file is missing. Choose it again in Sound.")
                ClipAudio.fit(AudioDecoder.decode(file), seconds.toFloat(), audio.volume)
            }
            AudioSource.NONE -> null
        }
    }
}

/** Per-frame phases, beat-synced when the project asks for it, otherwise the plain whole-cycle ramp. */
internal fun clipPhases(project: Project, frameCount: Int, fps: Int, cycles: Int, clipAudio: Pcm?): FloatArray {
    if (clipAudio == null || !project.audio.beatSync) {
        return FloatArray(frameCount) { LoopTiming.phaseForFrame(it, frameCount, cycles) }
    }
    val envelope = BeatDetector.envelope(BeatDetector.beats(clipAudio), clipAudio.seconds)
    return LoopTiming.beatPhases(frameCount, fps, cycles, envelope, BeatDetector.ENVELOPE_RATE, project.audio.beatAmount)
}

/** Loads the depth map for an export's 3D camera and hands it to the graph. */
internal suspend fun applyParallax(graph: LoopRenderGraph, project: Project, assets: ProjectAssetRepository) {
    val parallax = project.parallax
    if (!parallax.isActive) return
    // Fail loudly rather than export something that differs from the preview.
    val depth = assets.loadMask(project.id, MaskKind.DEPTH, parallax.depthRev)
        ?: error("The depth map is missing. Create it again in the 3D panel.")
    graph.setParallax(parallax, depth)
}

/**
 * Loads the sky mask and photo for an export and hands them to the graph.
 * Runs on the export GL thread, the loads themselves hop to IO and come back.
 */
internal suspend fun applySky(graph: LoopRenderGraph, project: Project, assets: ProjectAssetRepository, outputLongEdge: Int) {
    val sky = project.sky
    if (!sky.isActive) return
    val mask = assets.loadMask(project.id, MaskKind.SKY, sky.maskRev)
    val photo = if (sky.style == SkyStyle.PHOTO) {
        assets.loadImage(project.id, ImageKind.SKY_PHOTO, sky.photoRev, outputLongEdge)
    } else {
        null
    }
    graph.setSky(sky, mask, photo, replace = true)
    // Uploaded to the GPU, and the graph only keeps it for an identity check.
    photo?.recycle()
}
