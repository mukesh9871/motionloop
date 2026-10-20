package com.motionloop.app.render

import android.content.res.AssetManager
import android.graphics.Bitmap
import android.opengl.GLES30
import com.motionloop.app.core.gl.FullscreenQuad
import com.motionloop.app.core.gl.GlTexture
import com.motionloop.app.domain.model.Adjustments
import com.motionloop.app.domain.model.Anchor
import com.motionloop.app.domain.model.LoopMode
import com.motionloop.app.domain.model.LutData
import com.motionloop.app.domain.model.MaskStroke
import com.motionloop.app.domain.model.MotionArrow
import com.motionloop.app.domain.model.OverlaySettings
import com.motionloop.app.render.passes.ColorPass
import com.motionloop.app.render.passes.FlowFieldPass
import com.motionloop.app.render.passes.LoopWarpPass
import com.motionloop.app.render.passes.MaskPass
import com.motionloop.app.render.passes.OverlayPass

/**
 * The pass chain shared by the editor preview and the exporters.
 * FlowField + Mask -> LoopWarp -> [Overlay layer] -> Color + blend.
 * The extra passes are skipped when there is nothing to adjust or overlay.
 * Later phases insert passes between warp and output.
 * Must be created, used and released on one GL thread.
 */
class LoopRenderGraph(assets: AssetManager) {

    private val quad = FullscreenQuad()
    private val flowPass = FlowFieldPass(assets, quad)
    private val maskPass = MaskPass(assets)
    private val warpPass = LoopWarpPass(assets, quad)
    private val colorPass = ColorPass(assets, quad)
    private val overlayPass = OverlayPass(assets, quad)
    private var overlay = OverlaySettings()

    // Offscreen frame between warp and color, only allocated when adjustments are active.
    private var frame: GlTexture? = null
    private var adjustments = Adjustments()

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

    /** Color settings for following frames. [lut] must match [adjustments].gradeId (null for none). */
    fun setColor(adjustments: Adjustments, lut: LutData?) {
        this.adjustments = if (adjustments.gradeId != null && lut == null) adjustments.copy(gradeId = null) else adjustments
        colorPass.setLut(lut)
    }

    fun setOverlay(settings: OverlaySettings) {
        overlay = settings
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

        if (adjustments.isIdentity && !overlay.isActive) {
            // Nothing to grade or overlay: warp straight into the target, no extra pass.
            GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, fbo)
            GLES30.glViewport(x, y, width, height)
            warpPass.draw(src.id, flow.id, mask.id, flowPass.encoded, phase, mode, amount, maskOverlay, flipY)
            return
        }

        // Warp into an offscreen frame (not flipped, row 0 = image top), render the overlay layer
        // the same way, then color and blend both into the target.
        val frame = ensureFrame(width, height)
        frame.bindAsTarget()
        warpPass.draw(src.id, flow.id, mask.id, flowPass.encoded, phase, mode, amount, maskOverlay, flipY = false)
        var overlayTex = 0
        if (overlay.isActive) {
            overlayPass.render(overlay, phase, aspect, width, height)
            overlayTex = overlayPass.layer?.id ?: 0
        }
        GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, fbo)
        GLES30.glViewport(x, y, width, height)
        colorPass.draw(frame.id, adjustments, overlayTex, overlay, flipY)
    }

    private fun ensureFrame(width: Int, height: Int): GlTexture {
        frame?.let { if (it.width == width && it.height == height) return it else it.release() }
        return GlTexture.empty(width, height, GLES30.GL_RGBA8, GLES30.GL_RGBA, GLES30.GL_UNSIGNED_BYTE)
            .also { frame = it }
    }

    fun release() {
        source?.release()
        source = null
        sourceBitmap = null
        frame?.release()
        frame = null
        flowPass.release()
        maskPass.release()
        warpPass.release()
        colorPass.release()
        overlayPass.release()
        quad.release()
    }
}
