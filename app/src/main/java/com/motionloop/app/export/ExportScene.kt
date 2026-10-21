package com.motionloop.app.export

import com.motionloop.app.domain.model.MaskKind
import com.motionloop.app.domain.model.Project
import com.motionloop.app.domain.model.SkyStyle
import com.motionloop.app.domain.repository.ImageKind
import com.motionloop.app.domain.repository.ProjectAssetRepository
import com.motionloop.app.render.LoopRenderGraph

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
