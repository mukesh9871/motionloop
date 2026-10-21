package com.motionloop.app.export

import com.motionloop.app.domain.model.BackgroundMode
import com.motionloop.app.domain.model.MaskKind
import com.motionloop.app.domain.model.Project
import com.motionloop.app.domain.model.SkyStyle
import com.motionloop.app.domain.repository.ImageKind
import com.motionloop.app.domain.repository.ProjectAssetRepository
import com.motionloop.app.render.LoopRenderGraph
import com.motionloop.app.render.SelectionLayer

/** Tap-to-select regions of the freeze mask, loaded for an export. Missing files are skipped. */
internal suspend fun loadSelections(project: Project, assets: ProjectAssetRepository): List<SelectionLayer> =
    project.selections.mapNotNull { region ->
        assets.loadMask(project.id, MaskKind.SELECTION, region.rev)?.let { SelectionLayer(it, region.mode) }
    }

/**
 * Subject mask and background for an export. Transparent backgrounds become black in video,
 * since MP4 and GIF here carry no alpha.
 */
internal suspend fun applySubject(graph: LoopRenderGraph, project: Project, assets: ProjectAssetRepository, outputLongEdge: Int) {
    val subject = project.subject
    if (!subject.hasArea) return
    val mask = assets.loadMask(project.id, MaskKind.SUBJECT, subject.maskRev)
    val photo = if (project.background.mode == BackgroundMode.PHOTO) {
        assets.loadImage(project.id, ImageKind.BACKGROUND, project.background.photoRev, outputLongEdge)
    } else {
        null
    }
    graph.setSubject(subject, mask, project.background, photo, tintOnly = false, checker = false)
    // Uploaded to the GPU, and the graph only keeps it for an identity check.
    photo?.recycle()
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
