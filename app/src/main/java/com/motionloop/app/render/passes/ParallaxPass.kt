package com.motionloop.app.render.passes

import android.content.res.AssetManager
import com.motionloop.app.core.gl.FullscreenQuad
import com.motionloop.app.core.gl.GlProgram
import com.motionloop.app.core.gl.GlUtil
import com.motionloop.app.domain.model.ParallaxSettings

/** Final camera stage: shifts the frame by depth along a looping camera path. Draws into the bound target. */
class ParallaxPass(assets: AssetManager, private val quad: FullscreenQuad) : RenderPass {

    private val program = GlProgram.fromAssets(assets, "fullscreen.vert", "parallax.frag")

    fun draw(frameTex: Int, depthTex: Int, settings: ParallaxSettings, phase: Float, aspect: Float, flipY: Boolean) {
        program.use()
        program.set1f("uFlipY", if (flipY) 1f else 0f)
        program.bindTexture("uFrame", 0, frameTex)
        program.bindTexture("uDepth", 1, depthTex)
        program.set1i("uPath", settings.path.ordinal)
        program.set1f("uPhase", phase)
        program.set1f("uOffset", settings.amount.coerceIn(0f, 1f) * ParallaxSettings.MAX_OFFSET)
        program.set1f("uFocus", settings.focus.coerceIn(0f, 1f))
        program.set1f("uAspect", aspect)
        quad.draw()
        GlUtil.checkError("ParallaxPass")
    }

    override fun release() = program.release()
}
