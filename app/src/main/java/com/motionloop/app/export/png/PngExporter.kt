package com.motionloop.app.export.png

import android.content.Context
import android.graphics.Bitmap
import android.opengl.GLES30
import com.motionloop.app.core.common.DefaultDispatcher
import com.motionloop.app.core.gl.EglCore
import com.motionloop.app.core.gl.GlTexture
import com.motionloop.app.core.gl.GlUtil
import com.motionloop.app.domain.model.BackgroundMode
import com.motionloop.app.domain.model.Project
import com.motionloop.app.domain.repository.LutRepository
import com.motionloop.app.domain.repository.ProjectAssetRepository
import com.motionloop.app.domain.repository.ProjectRepository
import com.motionloop.app.export.OffscreenLoop
import com.motionloop.app.export.applySky
import com.motionloop.app.export.applySubject
import com.motionloop.app.export.loadSelections
import com.motionloop.app.export.renderTexts
import com.motionloop.app.render.passes.TextDraw
import com.motionloop.app.render.LoopRenderGraph
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import javax.inject.Inject
import kotlin.math.max

/**
 * Still image export. With a transparent background the subject mask becomes the PNG's alpha,
 * which is the cutout (11d). Otherwise the PNG is opaque.
 */
class PngExporter @Inject constructor(
    @ApplicationContext private val context: Context,
    private val projects: ProjectRepository,
    private val luts: LutRepository,
    private val assets: ProjectAssetRepository,
    @DefaultDispatcher private val cpu: CoroutineDispatcher,
) {

    suspend fun export(project: Project, out: File, onProgress: (Float) -> Unit) = OffscreenLoop.onGlThread("ml-png-export") {
        val (w, h) = OffscreenLoop.fit(
            project.aspect, MAX_EDGE, edgeIsShort = false,
            srcW = project.imageWidth, srcH = project.imageHeight, align = 2,
        )
        val cutout = project.background.mode == BackgroundMode.TRANSPARENT && project.subject.hasArea
        var egl: EglCore? = null
        var graph: LoopRenderGraph? = null
        var texts: List<TextDraw> = emptyList()
        var color: GlTexture? = null
        var alpha: GlTexture? = null
        var success = false
        try {
            out.parentFile?.mkdirs()
            val e = EglCore(EglCore.Kind.PBUFFER).also { egl = it }
            e.makeCurrentPbuffer()
            val bitmap = projects.loadImage(project.id, minOf(max(w, h), GlUtil.maxTextureSize()))
            val g = LoopRenderGraph(context.assets).also { graph = it }
            g.setImage(bitmap)
            g.setGeometry(project.arrows, project.anchors, project.strokes, loadSelections(project, assets))
            g.setColor(project.adjustments, luts.resolve(project.id, project.adjustments))
            g.setOverlay(project.overlay)
            applySky(g, project, assets, max(w, h))
            // No 3D camera on a still: the cutout mask must line up with the unshifted photo.
            applySubject(g, project, assets, max(w, h), cutout = cutout)
            texts = renderTexts(project, w)
            g.setTexts(texts)
            bitmap.recycle()
            onProgress(0.3f)

            // A still: no warp (amount 0), loop phase 0.
            val c = GlTexture.empty(w, h, GLES30.GL_RGBA8, GLES30.GL_RGBA, GLES30.GL_UNSIGNED_BYTE).also { color = it }
            g.draw(c.framebuffer(), 0, 0, w, h, phase = 0f, mode = project.loop.mode, amount = 0f, maskOverlay = 0f, flipY = false)
            val rgba = readPixels(w, h)

            val mask = if (cutout) {
                val a = GlTexture.empty(w, h, GLES30.GL_RGBA8, GLES30.GL_RGBA, GLES30.GL_UNSIGNED_BYTE).also { alpha = it }
                g.drawSubjectMask(a.framebuffer(), w, h)
                readPixels(w, h)
            } else {
                null
            }
            onProgress(0.6f)

            withContext(cpu) {
                val feather = project.subject.feather.coerceIn(0f, 1f)
                val pixels = compose(rgba, mask, w * h, 0.04f + 0.4f * feather)
                val image = Bitmap.createBitmap(pixels, w, h, Bitmap.Config.ARGB_8888)
                try {
                    out.outputStream().buffered().use { image.compress(Bitmap.CompressFormat.PNG, 100, it) }
                } finally {
                    image.recycle()
                }
            }
            onProgress(1f)
            success = true
        } finally {
            color?.release()
            alpha?.release()
            graph?.release()
            texts.forEach { it.rendered.recycle() }
            egl?.release()
            if (!success) out.delete()
        }
    }

    private fun readPixels(w: Int, h: Int): ByteArray {
        val buffer = ByteBuffer.allocateDirect(w * h * 4).order(ByteOrder.nativeOrder())
        GLES30.glReadPixels(0, 0, w, h, GLES30.GL_RGBA, GLES30.GL_UNSIGNED_BYTE, buffer)
        GlUtil.checkError("glReadPixels")
        return ByteArray(w * h * 4).also { buffer.get(it) }
    }

    companion object {
        /** Long edge of the still. PNGs are lossless, so keep a high but bounded size. */
        const val MAX_EDGE = 2048

        /**
         * RGBA rows (row 0 = top, FBOs are drawn unflipped) plus an optional mask in the red channel
         * to non-premultiplied ARGB ints, which is what [Bitmap.createBitmap] expects.
         * The mask edge is softened like in the shader: smoothstep(0.5 - f, 0.5 + f, m).
         */
        internal fun compose(rgba: ByteArray, mask: ByteArray?, count: Int, f: Float): IntArray = IntArray(count) { i ->
            val r = rgba[i * 4].toInt() and 255
            val g = rgba[i * 4 + 1].toInt() and 255
            val b = rgba[i * 4 + 2].toInt() and 255
            val a = if (mask == null) {
                255
            } else {
                val m = (mask[i * 4].toInt() and 255) / 255f
                val t = ((m - (0.5f - f)) / (2f * f)).coerceIn(0f, 1f)
                (t * t * (3f - 2f * t) * 255f + 0.5f).toInt()
            }
            (a shl 24) or (r shl 16) or (g shl 8) or b
        }
    }
}
