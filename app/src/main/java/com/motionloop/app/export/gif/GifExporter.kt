package com.motionloop.app.export.gif

import android.content.Context
import android.opengl.GLES30
import com.motionloop.app.core.common.DefaultDispatcher
import com.motionloop.app.core.gl.EglCore
import com.motionloop.app.core.gl.GlTexture
import com.motionloop.app.core.gl.GlUtil
import com.motionloop.app.domain.model.ExportSettings
import com.motionloop.app.domain.model.LoopTiming
import com.motionloop.app.domain.model.Project
import com.motionloop.app.domain.repository.LutRepository
import com.motionloop.app.domain.repository.ProjectAssetRepository
import com.motionloop.app.export.applyParallax
import com.motionloop.app.export.applySubject
import com.motionloop.app.export.loadSelections
import com.motionloop.app.export.renderTexts
import com.motionloop.app.render.passes.TextDraw
import com.motionloop.app.export.applySky
import com.motionloop.app.export.buildClipAudio
import com.motionloop.app.export.clipPhases
import com.motionloop.app.domain.repository.ProjectRepository
import com.motionloop.app.export.OffscreenLoop
import com.motionloop.app.render.LoopRenderGraph
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import javax.inject.Inject
import kotlinx.coroutines.currentCoroutineContext
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * GPU renders each frame into an FBO, one glReadPixels per frame on the export GL thread,
 * palette mapping and LZW on the default dispatcher. Nothing touches the UI thread.
 */
class GifExporter @Inject constructor(
    @ApplicationContext private val context: Context,
    private val projects: ProjectRepository,
    private val luts: LutRepository,
    private val assets: ProjectAssetRepository,
    @DefaultDispatcher private val cpu: CoroutineDispatcher,
) {

    suspend fun export(project: Project, settings: ExportSettings, out: File, onProgress: (Float) -> Unit) =
        OffscreenLoop.onGlThread("ml-gif-export") {
            val targetAspect = settings.aspect.widthOverHeight(project.aspect)
            val (cropW, cropH) = OffscreenLoop.cropSize(project.imageWidth, project.imageHeight, targetAspect)
            val (w, h) = OffscreenLoop.fit(
                targetAspect, ExportSettings.GIF_LONG_EDGE, edgeIsShort = false,
                srcW = cropW, srcH = cropH, align = 2,
            )
            val viewport = OffscreenLoop.coverViewport(project.imageWidth, project.imageHeight, w, h)
            val drawEdge = max(viewport[2], viewport[3])
            val delay = ExportSettings.GIF_DELAY_CS
            val frameCount = (settings.lengthSeconds * 100f / delay).roundToInt()
            val cycles = LoopTiming.cyclesForClip(settings.lengthSeconds.toFloat(), project.loop.speed)

            // Beat sync shapes GIF motion too (the GIF itself has no sound). Plain timing if the sound fails.
            val sound = if (project.audio.beatSync) {
                try {
                    buildClipAudio(project, settings.lengthSeconds, assets, cpu)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    null
                }
            } else {
                null
            }
            val phases = clipPhases(project, frameCount, 100f / delay, cycles, sound)
            var egl: EglCore? = null
            var graph: LoopRenderGraph? = null
            var texts: List<TextDraw> = emptyList()
            var target: GlTexture? = null
            var success = false
            try {
                out.parentFile?.mkdirs()
                // Assign before using it, so a failure in makeCurrentPbuffer still releases the context.
                val e = EglCore(EglCore.Kind.PBUFFER).also { egl = it }
                e.makeCurrentPbuffer()
                val bitmap = projects.loadImage(project.id, minOf(max(w, h) * 2, GlUtil.maxTextureSize()))
                val g = LoopRenderGraph(context.assets).also { graph = it }
                g.setImage(bitmap)
                g.setGeometry(project.arrows, project.anchors, project.strokes, loadSelections(project, assets))
                g.setColor(project.adjustments, luts.resolve(project.id, project.adjustments))
                g.setOverlay(project.overlay)
                applySky(g, project, assets, drawEdge)
                applyParallax(g, project, assets)
                applySubject(g, project, assets, drawEdge)
                texts = renderTexts(project, viewport[2])
                g.setTexts(texts)
                bitmap.recycle() // uploaded to the GPU, the CPU copy is no longer needed
                val t = GlTexture.empty(w, h, GLES30.GL_RGBA8, GLES30.GL_RGBA, GLES30.GL_UNSIGNED_BYTE).also { target = it }
                val readBuffer = ByteBuffer.allocateDirect(w * h * 4).order(ByteOrder.nativeOrder())

                fun renderFrame(i: Int): ByteArray {
                    g.draw(
                        fbo = t.framebuffer(), x = viewport[0], y = viewport[1], width = viewport[2], height = viewport[3],
                        phase = phases[i],
                        mode = project.loop.mode,
                        amount = 1f,
                        maskOverlay = 0f,
                        // Not flipped: FBO row 0 is then the image top, which is the order GIF wants.
                        flipY = false,
                    )
                    readBuffer.clear()
                    GLES30.glReadPixels(0, 0, w, h, GLES30.GL_RGBA, GLES30.GL_UNSIGNED_BYTE, readBuffer)
                    GlUtil.checkError("glReadPixels")
                    return ByteArray(w * h * 4).also { readBuffer.get(it) }
                }

                // Pass 1: palette from a few frames spread over the loop.
                val builder = GifQuantizer.Builder()
                for (s in 0 until PALETTE_SAMPLES) {
                    val pixels = renderFrame(s * frameCount / PALETTE_SAMPLES)
                    withContext(cpu) { builder.add(pixels) }
                }
                val quantizer = withContext(cpu) { builder.build() }
                onProgress(0.1f)

                // Pass 2: render, map and encode every frame.
                out.outputStream().buffered().use { stream ->
                    val encoder = GifEncoder(stream, w, h, quantizer.palette)
                    val indices = ByteArray(w * h)
                    for (i in 0 until frameCount) {
                        currentCoroutineContext().ensureActive()
                        val pixels = renderFrame(i)
                        withContext(cpu) {
                            quantizer.map(pixels, w, h, indices)
                            encoder.writeFrame(indices, delay)
                        }
                        onProgress(0.1f + 0.9f * (i + 1) / frameCount)
                    }
                    encoder.finish()
                }
                success = true
            } finally {
                target?.release()
                graph?.release()
                texts.forEach { it.rendered.recycle() }
                egl?.release()
                if (!success) out.delete()
            }
        }

    private companion object {
        const val PALETTE_SAMPLES = 4
    }
}
