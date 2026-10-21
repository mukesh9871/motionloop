package com.motionloop.app.render.passes

import android.content.res.AssetManager
import com.motionloop.app.core.gl.FullscreenQuad
import com.motionloop.app.core.gl.GlProgram
import com.motionloop.app.core.gl.GlUtil
import com.motionloop.app.domain.model.LoopMode

/** Warps the photo along the flow field. Draws into whatever target is currently bound. */
class LoopWarpPass(assets: AssetManager, private val quad: FullscreenQuad) : RenderPass {

    private val program = GlProgram.fromAssets(assets, "fullscreen.vert", "loop_warp.frag")

    fun draw(
        imageTex: Int,
        flowTex: Int,
        maskTex: Int,
        flowEncoded: Boolean,
        phase: Float,
        mode: LoopMode,
        amount: Float,
        maskOverlay: Float,
        flipY: Boolean,
        rippleTex: Int = 0,
        rippleAmount: Float = 0f,
    ) {
        program.use()
        program.set1f("uFlipY", if (flipY) 1f else 0f)
        program.bindTexture("uImage", 0, imageTex)
        program.bindTexture("uFlow", 1, flowTex)
        program.bindTexture("uMask", 2, maskTex)
        program.set1f("uPhase", phase)
        program.set1i("uMode", if (mode == LoopMode.CONTINUOUS) 0 else 1)
        program.set1f("uAmount", amount)
        program.set1f("uEncoded", if (flowEncoded) 1f else 0f)
        program.set1f("uMaxFlow", FlowFieldPass.MAX_FLOW)
        program.set1f("uMaskOverlay", maskOverlay)
        // Without water regions, bind the freeze mask so the sampler is valid; amount 0 skips it.
        program.bindTexture("uRipple", 3, if (rippleTex != 0) rippleTex else maskTex)
        program.set1f("uRippleAmount", if (rippleTex != 0) rippleAmount else 0f)
        quad.draw()
        GlUtil.checkError("LoopWarpPass")
    }

    override fun release() = program.release()
}
