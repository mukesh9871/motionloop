package com.motionloop.app.render.passes

import android.content.res.AssetManager
import android.graphics.Bitmap
import android.opengl.GLES30
import com.motionloop.app.core.gl.FullscreenQuad
import com.motionloop.app.core.gl.GlProgram
import com.motionloop.app.core.gl.GlTexture
import com.motionloop.app.core.gl.GlUtil
import com.motionloop.app.domain.model.TextLayer
import com.motionloop.app.domain.model.TextTiming
import com.motionloop.app.render.text.RenderedText

/** A text layer and its rasterized bitmaps, ready to draw. */
class TextDraw(val layer: TextLayer, val rendered: RenderedText)

/**
 * Text layers over the frame, with the subject drawn again on top of the "behind" layers (11e).
 * Draws into the bound target: frame copy, behind text, subject, front text.
 */
class TextPass(assets: AssetManager, private val quad: FullscreenQuad) : RenderPass {

    private val textProgram = GlProgram.fromAssets(assets, "text.vert", "text.frag")
    private val frameProgram = GlProgram.fromAssets(assets, "fullscreen.vert", "subject_over.frag")

    /** Uploaded bitmaps by object, so a layer is uploaded once per rasterization. */
    private val textures = HashMap<Bitmap, GlTexture>()

    fun draw(
        frameTex: Int,
        subjectTex: Int,
        subjectFeather: Float,
        texts: List<TextDraw>,
        phase: Float,
        aspect: Float,
        flipY: Boolean,
    ) {
        syncTextures(texts)
        GLES30.glDisable(GLES30.GL_BLEND)
        drawFrame(frameTex, subjectTex, subjectFeather, mode = 0, flipY)

        GLES30.glEnable(GLES30.GL_BLEND)
        GLES30.glBlendEquation(GLES30.GL_FUNC_ADD)
        GLES30.glBlendFunc(GLES30.GL_ONE, GLES30.GL_ONE_MINUS_SRC_ALPHA)
        val behind = subjectTex != 0
        texts.filter { it.layer.behindSubject && behind }.forEach { drawText(it, phase, aspect, flipY) }
        if (behind && texts.any { it.layer.behindSubject }) {
            drawFrame(frameTex, subjectTex, subjectFeather, mode = 1, flipY)
        }
        texts.filter { !it.layer.behindSubject || !behind }.forEach { drawText(it, phase, aspect, flipY) }
        GLES30.glDisable(GLES30.GL_BLEND)
        GLES30.glActiveTexture(GLES30.GL_TEXTURE0)
        GlUtil.checkError("TextPass")
    }

    private fun drawFrame(frameTex: Int, subjectTex: Int, feather: Float, mode: Int, flipY: Boolean) {
        frameProgram.use()
        frameProgram.set1f("uFlipY", if (flipY) 1f else 0f)
        frameProgram.bindTexture("uFrame", 0, frameTex)
        frameProgram.bindTexture("uSubject", 1, subjectTex)
        frameProgram.set1f("uFeather", feather.coerceIn(0f, 1f))
        frameProgram.set1i("uMode", mode)
        quad.draw()
    }

    private fun drawText(t: TextDraw, phase: Float, aspect: Float, flipY: Boolean) {
        val r = t.rendered
        val main = textures[r.main] ?: return
        val anim = TextTiming.at(t.layer.animation, phase)
        if (anim.alpha <= 0f) return
        textProgram.use()
        textProgram.set1f("uFlipY", if (flipY) 1f else 0f)
        // Bitmap pixels to image widths: the font size was resolved against refWidth.
        val halfW = main.width / (2f * r.refWidth)
        val halfH = main.height / (2f * r.refWidth)
        textProgram.set2f("uCenter", t.layer.x + anim.offsetX, t.layer.y)
        textProgram.set2f("uHalfSize", halfW, halfH)
        textProgram.set1f("uRotation", Math.toRadians(t.layer.rotation.toDouble()).toFloat())
        textProgram.set1f("uAspect", aspect)
        textProgram.bindTexture("uText", 0, main.id)
        textProgram.bindTexture("uGlow", 1, r.glow?.let { textures[it]?.id } ?: 0)
        textProgram.bindTexture("uOrder", 2, r.order?.let { textures[it]?.id } ?: 0)
        textProgram.set1f("uAlpha", anim.alpha)
        textProgram.set1f("uGlowAlpha", if (r.glow != null) anim.glow else 0f)
        textProgram.set1f("uReveal", anim.reveal)
        textProgram.set1i("uUseOrder", if (r.order != null) 1 else 0)
        quad.draw()
    }

    /** Uploads new bitmaps and frees textures of bitmaps no longer in use. */
    private fun syncTextures(texts: List<TextDraw>) {
        val wanted = HashSet<Bitmap>()
        texts.forEach { t -> listOfNotNull(t.rendered.main, t.rendered.glow, t.rendered.order).forEach(wanted::add) }
        val iterator = textures.entries.iterator()
        while (iterator.hasNext()) {
            val (bitmap, texture) = iterator.next()
            if (bitmap !in wanted) {
                texture.release()
                iterator.remove()
            }
        }
        for (bitmap in wanted) {
            if (bitmap !in textures && !bitmap.isRecycled) textures[bitmap] = GlTexture.fromBitmap(bitmap)
        }
    }

    override fun release() {
        textures.values.forEach { it.release() }
        textures.clear()
        textProgram.release()
        frameProgram.release()
    }
}
