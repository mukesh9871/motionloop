package com.motionloop.app.render.passes

import android.content.res.AssetManager
import android.opengl.GLES30
import com.motionloop.app.core.gl.FullscreenQuad
import com.motionloop.app.core.gl.GlProgram
import com.motionloop.app.core.gl.GlUtil
import com.motionloop.app.domain.model.DispersionSettings
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * Dispersion: the frame with holes where flakes have left (filled by [holeTex], a blur of the
 * photo), then the flakes themselves as instanced quads. Draws into the bound target.
 */
class DispersionPass(assets: AssetManager, private val quad: FullscreenQuad) : RenderPass {

    private val base = GlProgram.fromAssets(assets, "fullscreen.vert", "disperse_base.frag")
    private val flakes = GlProgram.fromAssets(assets, "disperse.vert", "disperse.frag")

    fun draw(inputTex: Int, maskTex: Int, holeTex: Int, settings: DispersionSettings, phase: Float, aspect: Float, flipY: Boolean) {
        val cols = GRID.toFloat()
        // Square cells on screen: rows follow the aspect ratio.
        val rows = max(1, (GRID / aspect).roundToInt()).toFloat()
        val amount = settings.amount.coerceIn(0f, 1f)
        val flip = if (flipY) 1f else 0f

        GLES30.glDisable(GLES30.GL_BLEND)
        base.use()
        base.set1f("uFlipY", flip)
        base.bindTexture("uFrame", 0, inputTex)
        base.bindTexture("uMask", 1, maskTex)
        base.bindTexture("uHole", 2, holeTex)
        base.set2f("uGrid", cols, rows)
        base.set1f("uPhase", phase)
        base.set1f("uAmount", amount)
        quad.draw()

        GLES30.glEnable(GLES30.GL_BLEND)
        GLES30.glBlendEquation(GLES30.GL_FUNC_ADD)
        GLES30.glBlendFunc(GLES30.GL_ONE, GLES30.GL_ONE_MINUS_SRC_ALPHA)
        val angle = Math.toRadians(settings.angle.toDouble())
        flakes.use()
        flakes.set1f("uFlipY", flip)
        flakes.bindTexture("uFrame", 0, inputTex)
        flakes.bindTexture("uMask", 1, maskTex)
        flakes.set2f("uGrid", cols, rows)
        flakes.set1f("uPhase", phase)
        flakes.set1f("uAmount", amount)
        flakes.set1f("uDistance", settings.distance.coerceIn(0f, 1f) * MAX_DISTANCE)
        flakes.set2f("uDir", cos(angle).toFloat(), sin(angle).toFloat())
        flakes.set1f("uAspect", aspect)
        quad.drawInstanced((cols * rows).toInt())
        GLES30.glDisable(GLES30.GL_BLEND)
        GlUtil.checkError("DispersionPass")
    }

    override fun release() {
        base.release()
        flakes.release()
    }

    companion object {
        /** Cells across the width: flakes per photo width. */
        const val GRID = 110

        /** Farthest a flake flies at distance 1, in image widths. */
        const val MAX_DISTANCE = 0.6f
    }
}
