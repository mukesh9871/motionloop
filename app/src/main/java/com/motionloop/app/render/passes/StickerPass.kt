package com.motionloop.app.render.passes

import android.content.res.AssetManager
import android.opengl.GLES30
import com.motionloop.app.core.gl.FullscreenQuad
import com.motionloop.app.core.gl.GlProgram
import com.motionloop.app.core.gl.GlUtil
import com.motionloop.app.domain.model.Sticker

/** Copies the frame, then draws animated stickers over it (premultiplied "over"). */
class StickerPass(assets: AssetManager, private val quad: FullscreenQuad) : RenderPass {

    private val copy = GlProgram.fromAssets(assets, "fullscreen.vert", "copy.frag")
    private val sticker = GlProgram.fromAssets(assets, "sticker.vert", "sticker.frag")

    fun draw(inputTex: Int, stickers: List<Sticker>, phase: Float, aspect: Float, flipY: Boolean) {
        val flip = if (flipY) 1f else 0f
        GLES30.glDisable(GLES30.GL_BLEND)
        copy.use()
        copy.set1f("uFlipY", flip)
        copy.bindTexture("uSrc", 0, inputTex)
        quad.draw()

        GLES30.glEnable(GLES30.GL_BLEND)
        GLES30.glBlendEquation(GLES30.GL_FUNC_ADD)
        GLES30.glBlendFunc(GLES30.GL_ONE, GLES30.GL_ONE_MINUS_SRC_ALPHA)
        sticker.use()
        sticker.set1f("uFlipY", flip)
        sticker.set1f("uAspect", aspect)
        sticker.set1f("uPhase", phase)
        for (s in stickers) {
            // StickerKind order matches sticker.frag: butterfly 1, flame 2, birds 3, glint 4.
            sticker.set1i("uKind", s.kind.ordinal + 1)
            sticker.set2f("uCenter", s.x, s.y)
            sticker.set1f("uSize", s.size)
            sticker.set1f("uRotation", Math.toRadians(s.rotation.toDouble()).toFloat())
            quad.draw()
        }
        GLES30.glDisable(GLES30.GL_BLEND)
        GlUtil.checkError("StickerPass")
    }

    override fun release() {
        copy.release()
        sticker.release()
    }
}
