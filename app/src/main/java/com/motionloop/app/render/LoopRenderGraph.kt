package com.motionloop.app.render

import android.content.res.AssetManager
import android.graphics.Bitmap
import android.opengl.GLES30
import com.motionloop.app.core.gl.FullscreenQuad
import com.motionloop.app.core.gl.GlTexture
import com.motionloop.app.domain.model.Anchor
import com.motionloop.app.domain.model.LoopMode
import com.motionloop.app.domain.model.MaskStroke
import com.motionloop.app.domain.model.MotionArrow
import com.motionloop.app.render.passes.FlowFieldPass
import com.motionloop.app.render.passes.LoopWarpPass
import com.motionloop.app.render.passes.MaskPass

/**
 * The pass chain shared by the editor preview and the exporters.
 * Phase 1: FlowField + Mask -> LoopWarp. Later phases insert passes between warp and output.
 * Must be created, used and released on one GL thread.
 */
class LoopRenderGraph(assets: AssetManager) {

    private val quad = FullscreenQuad()
    private val flowPass = FlowFieldPass(assets, quad)
    private val maskPass = MaskPass(assets)
    private val warpPass = LoopWarpPass(assets, quad)

    private var source: GlTexture? = null
    private var sourceBitmap: Bitmap? = null
    private var aspect = 1f

    // Last inputs, so flow and mask are only rebuilt when they actually change.
    private var lastArrows: List<MotionArrow>? = null
    private var lastAnchors: List<Anchor>? = null
    private var lastStrokes: List<MaskStroke>? = null

    val hasImage: Boolean get() = source != null

    fun setImage(bitmap: Bitmap) {
        if (bitmap === sourceBitmap) return
        source?.release()
        source = GlTexture.fromBitmap(bitmap)
        sourceBitmap = bitmap
        aspect = bitmap.width.toFloat() / bitmap.height
        // Target sizes depend on aspect, so rebuild both.
        lastArrows = null
        lastStrokes = null
    }

    fun setGeometry(arrows: List<MotionArrow>, anchors: List<Anchor>, strokes: List<MaskStroke>) {
        if (source == null) return
        if (arrows != lastArrows || anchors != lastAnchors) {
            flowPass.render(aspect, arrows, anchors)
            lastArrows = arrows
            lastAnchors = anchors
        }
        if (strokes != lastStrokes) {
            maskPass.render(aspect, strokes)
            lastStrokes = strokes
        }
    }

    /**
     * Draws one frame into framebuffer [fbo] (0 = window or encoder surface) at the given viewport.
     * [flipY] is true for window surfaces, false for FBOs read back row by row.
     */
    fun draw(
        fbo: Int,
        x: Int,
        y: Int,
        width: Int,
        height: Int,
        phase: Float,
        mode: LoopMode,
        amount: Float,
        maskOverlay: Float,
        flipY: Boolean,
    ) {
        val src = source ?: return
        val flow = flowPass.texture ?: return
        val mask = maskPass.texture ?: return
        GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, fbo)
        GLES30.glViewport(x, y, width, height)
        warpPass.draw(src.id, flow.id, mask.id, flowPass.encoded, phase, mode, amount, maskOverlay, flipY)
    }

    fun release() {
        source?.release()
        source = null
        sourceBitmap = null
        flowPass.release()
        maskPass.release()
        warpPass.release()
        quad.release()
    }
}
