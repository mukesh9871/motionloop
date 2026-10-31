package com.motionloop.app.export.png

import com.motionloop.app.domain.repository.GalleryItem
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
import java.nio.ByteBuffer
import java.nio.ByteOrder
import javax.inject.Inject
import kotlin.math.max

/** Read-back RGBA rows of a cutout: subject mask (red), and premultiplied behind and front text. */
internal class CutoutLayers(val mask: ByteArray, val behind: ByteArray, val front: ByteArray)

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

    suspend fun export(project: Project, out: GalleryItem, onProgress: (Float) -> Unit) = OffscreenLoop.onGlThread("ml-png-export") {
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
        try {
            val e = EglCore(EglCore.Kind.PBUFFER).also { egl = it }
            e.makeCurrentPbuffer()
            val bitmap = projects.loadImage(project.id, minOf(max(w, h), GlUtil.maxTextureSize()))
            val g = LoopRenderGraph(context.assets).also { graph = it }
            g.setImage(bitmap)
            g.setGeometry(project.arrows, project.anchors, project.strokes, loadSelections(project, assets))
            g.setColor(project.adjustments, luts.resolve(project.id, project.adjustments))
            g.setOverlay(project.overlay)
            g.setEffects(project.effects)
            applySky(g, project, assets, max(w, h))
            // No 3D camera on a still: the cutout mask must line up with the unshifted photo.
            applySubject(g, project, assets, max(w, h), cutout = cutout)
            // A still shows every text fully: at phase 0 a fade or typewriter would hide it.
            texts = renderTexts(project, w, animated = false)
            g.setTexts(texts)
            bitmap.recycle()
            onProgress(0.3f)

            // A still: no warp (amount 0), loop phase 0. For a cutout the photo, its subject mask and
            // the two text layers are read separately and stacked below, so every edge keeps its color.
            g.drawTextsInFrame = !cutout
            val c = GlTexture.empty(w, h, GLES30.GL_RGBA8, GLES30.GL_RGBA, GLES30.GL_UNSIGNED_BYTE).also { color = it }
            g.draw(c.framebuffer(), 0, 0, w, h, phase = 0f, mode = project.loop.mode, amount = 0f, maskOverlay = 0f, flipY = false)
            val rgba = readPixels(w, h)

            val layers = if (cutout) {
                val a = GlTexture.empty(w, h, GLES30.GL_RGBA8, GLES30.GL_RGBA, GLES30.GL_UNSIGNED_BYTE).also { alpha = it }
                g.drawSubjectMask(a.framebuffer(), w, h)
                val mask = readPixels(w, h)
                g.drawTextLayers(a.framebuffer(), w, h, behind = true, phase = 0f)
                val behind = readPixels(w, h)
                g.drawTextLayers(a.framebuffer(), w, h, behind = false, phase = 0f)
                val front = readPixels(w, h)
                CutoutLayers(mask, behind, front)
            } else {
                null
            }
            onProgress(0.6f)

            withContext(cpu) {
                val feather = project.subject.feather.coerceIn(0f, 1f)
                val pixels = compose(rgba, layers, w * h, 0.04f + 0.4f * feather)
                val image = Bitmap.createBitmap(pixels, w, h, Bitmap.Config.ARGB_8888)
                try {
                    out.openStream().buffered().use { image.compress(Bitmap.CompressFormat.PNG, 100, it) }
                } finally {
                    image.recycle()
                }
            }
            onProgress(1f)
        } finally {
            color?.release()
            alpha?.release()
            graph?.release()
            texts.forEach { it.rendered.recycle() }
            egl?.release()
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
         * Photo RGBA rows (row 0 = top, FBOs are drawn unflipped), optionally stacked as a cutout:
         * behind text, then the subject (photo times its softened mask), then front text, all
         * premultiplied. Returns non-premultiplied ARGB ints, which is what [Bitmap.createBitmap] expects.
         * The mask edge is softened like in the shader: smoothstep(0.5 - f, 0.5 + f, m).
         */
        internal fun compose(rgba: ByteArray, layers: CutoutLayers?, count: Int, f: Float): IntArray = IntArray(count) { i ->
            val o = i * 4
            fun ch(a: ByteArray, k: Int) = (a[o + k].toInt() and 255) / 255f
            if (layers == null) {
                return@IntArray (255 shl 24) or ((rgba[o].toInt() and 255) shl 16) or ((rgba[o + 1].toInt() and 255) shl 8) or (rgba[o + 2].toInt() and 255)
            }
            val m = ch(layers.mask, 0)
            val t = ((m - (0.5f - f)) / (2f * f)).coerceIn(0f, 1f)
            val s = t * t * (3f - 2f * t)
            var r = ch(layers.behind, 0)
            var g = ch(layers.behind, 1)
            var b = ch(layers.behind, 2)
            var a = ch(layers.behind, 3)
            // Subject over behind text.
            r = ch(rgba, 0) * s + r * (1 - s)
            g = ch(rgba, 1) * s + g * (1 - s)
            b = ch(rgba, 2) * s + b * (1 - s)
            a = s + a * (1 - s)
            // Front text over everything.
            val fa = ch(layers.front, 3)
            r = ch(layers.front, 0) + r * (1 - fa)
            g = ch(layers.front, 1) + g * (1 - fa)
            b = ch(layers.front, 2) + b * (1 - fa)
            a = fa + a * (1 - fa)
            if (a <= 0.0001f) return@IntArray 0
            fun out(v: Float) = ((v / a).coerceIn(0f, 1f) * 255f + 0.5f).toInt()
            ((a * 255f + 0.5f).toInt() shl 24) or (out(r) shl 16) or (out(g) shl 8) or out(b)
        }
    }
}
