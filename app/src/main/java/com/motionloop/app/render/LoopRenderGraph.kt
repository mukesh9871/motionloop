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
import com.motionloop.app.domain.model.ParallaxSettings
import com.motionloop.app.domain.model.SkySettings
import com.motionloop.app.domain.model.SkyStyle
import com.motionloop.app.render.passes.ColorPass
import com.motionloop.app.render.passes.FlowFieldPass
import com.motionloop.app.render.passes.LoopWarpPass
import com.motionloop.app.render.passes.MaskPass
import com.motionloop.app.render.passes.OverlayPass
import com.motionloop.app.render.passes.ParallaxPass
import com.motionloop.app.render.passes.SourcePass
import com.motionloop.ml.AlphaMask

/**
 * The pass chain shared by the editor preview, the exporters and the live wallpaper.
 *
 * photo -> [Source: sky replace] -> LoopWarp (flow, freeze mask) -> [Color + overlay] -> [Parallax] -> target
 *
 * Post stages (color + overlay so far) ping-pong between two offscreen frames, and the last one
 * draws into the target. Every bracketed or post stage is skipped when it has nothing to do.
 * Must be created, used and released on one GL thread.
 */
class LoopRenderGraph(assets: AssetManager) {

    /** A full-frame stage after the warp. [prepare] may bind its own FBOs, [draw] draws into the bound target. */
    private interface PostStage {
        fun prepare(phase: Float, width: Int, height: Int) = Unit
        fun draw(inputTex: Int, flipY: Boolean)
    }

    private val quad = FullscreenQuad()
    private val flowPass = FlowFieldPass(assets, quad)
    private val maskPass = MaskPass(assets, quad)
    private val skyMaskPass = MaskPass(assets, quad)
    private val sourcePass = SourcePass(assets, quad)
    private val warpPass = LoopWarpPass(assets, quad)
    private val colorPass = ColorPass(assets, quad)
    private val overlayPass = OverlayPass(assets, quad)
    private val parallaxPass = ParallaxPass(assets, quad)

    private var photo: GlTexture? = null
    private var photoBitmap: Bitmap? = null
    private var aspect = 1f

    // Post stage ping-pong frames, allocated only while a post stage is active.
    private var frameA: GlTexture? = null
    private var frameB: GlTexture? = null

    private var adjustments = Adjustments()
    private var overlay = OverlaySettings()

    private var sky = SkySettings()
    private var skyReplace = true
    private val skyMask = MaskTexture()
    private var skyPhoto: GlTexture? = null

    private var parallax = ParallaxSettings()
    private val depthMask = MaskTexture()
    private var skyPhotoBitmap: Bitmap? = null

    // Last inputs, so flow and masks are only rebuilt when they actually change.
    private var lastArrows: List<MotionArrow>? = null
    private var lastAnchors: List<Anchor>? = null
    private var lastStrokes: List<MaskStroke>? = null
    private var lastSkyStrokes: List<MaskStroke>? = null
    // Compared by object, not GL id: a new texture can reuse a deleted texture's id.
    private var lastSkyMask: AlphaMask? = null
    private var skyMaskBuilt = false

    private val colorStage = object : PostStage {
        private var overlayTex = 0

        override fun prepare(phase: Float, width: Int, height: Int) {
            overlayTex = if (overlay.isActive) {
                overlayPass.render(overlay, phase, aspect, width, height)
                overlayPass.layer?.id ?: 0
            } else {
                overlayPass.releaseLayer()
                0
            }
        }

        override fun draw(inputTex: Int, flipY: Boolean) = colorPass.draw(inputTex, adjustments, overlayTex, overlay, flipY)
    }

    // Last post stage: 3D camera move by depth. Always last, so it moves everything drawn before it.
    private val parallaxStage = object : PostStage {
        private var phase = 0f

        override fun prepare(phase: Float, width: Int, height: Int) {
            this.phase = phase
        }

        override fun draw(inputTex: Int, flipY: Boolean) =
            parallaxPass.draw(inputTex, depthMask.id, parallax, phase, aspect, flipY)
    }

    val hasImage: Boolean get() = photo != null

    fun setImage(bitmap: Bitmap) {
        if (bitmap === photoBitmap) return
        photo?.release()
        photo = GlTexture.fromBitmap(bitmap)
        photoBitmap = bitmap
        aspect = bitmap.width.toFloat() / bitmap.height
        // Target sizes depend on aspect, so rebuild flow and masks.
        lastArrows = null
        lastStrokes = null
        skyMaskBuilt = false
    }

    fun setGeometry(arrows: List<MotionArrow>, anchors: List<Anchor>, strokes: List<MaskStroke>) {
        if (photo == null) return
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
     * Sky replacement inputs. [mask] is the model mask for [settings].maskRev (null if none or not loaded),
     * [photo] the sky photo for [SkyStyle.PHOTO]. [replace] false only tints the sky area (editing view).
     */
    fun setSky(settings: SkySettings, mask: AlphaMask?, photo: Bitmap?, replace: Boolean) {
        if (this.photo == null) return
        sky = settings
        skyReplace = replace
        skyMask.set(mask)
        if (photo !== skyPhotoBitmap) {
            skyPhoto?.release()
            skyPhoto = photo?.let { GlTexture.fromBitmap(it).apply { setWrap(GLES30.GL_MIRRORED_REPEAT) } }
            skyPhotoBitmap = photo
        }
        if (!skyMaskBuilt || settings.strokes != lastSkyStrokes || mask !== lastSkyMask) {
            val maskId = skyMask.id
            val layers = if (maskId != 0) listOf(MaskPass.Layer(maskId, MaskPass.LayerMode.ADD)) else emptyList()
            skyMaskPass.render(aspect, settings.strokes, layers)
            lastSkyStrokes = settings.strokes
            lastSkyMask = mask
            skyMaskBuilt = true
        }
    }

    /** 3D camera. [depth] is the depth map for [settings].depthRev (null if none or not loaded). */
    fun setParallax(settings: ParallaxSettings, depth: AlphaMask?) {
        parallax = settings
        depthMask.set(depth)
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
        val src = photo ?: return
        val flow = flowPass.texture ?: return
        val mask = maskPass.texture ?: return

        val warpInput = sourceTexture(src, phase)

        val stages = ArrayList<PostStage>(2)
        if (!adjustments.isIdentity || overlay.isActive) stages += colorStage
        if (parallax.isActive && depthMask.id != 0) stages += parallaxStage
        if (!overlay.isActive) overlayPass.releaseLayer()

        if (stages.isEmpty()) {
            // Nothing after the warp: draw straight into the target and free the offscreen frames.
            releaseFrames()
            bindTarget(fbo, x, y, width, height)
            warpPass.draw(warpInput, flow.id, mask.id, flowPass.encoded, phase, mode, amount, maskOverlay, flipY)
            return
        }

        // Offscreen frames are never flipped: row 0 = image top, like the photo.
        var current = ensureFrame(true, width, height)
        current.bindAsTarget()
        warpPass.draw(warpInput, flow.id, mask.id, flowPass.encoded, phase, mode, amount, maskOverlay, flipY = false)
        for ((i, stage) in stages.withIndex()) {
            stage.prepare(phase, width, height)
            val last = i == stages.lastIndex
            if (last) {
                bindTarget(fbo, x, y, width, height)
                stage.draw(current.id, flipY)
            } else {
                val next = ensureFrame(current !== frameA, width, height)
                next.bindAsTarget()
                stage.draw(current.id, flipY = false)
                current = next
            }
        }
        if (stages.size < 2) {
            frameB?.release()
            frameB = null
        }
    }

    /** The photo, or the photo with its sky replaced when the sky stage is active. */
    private fun sourceTexture(src: GlTexture, phase: Float): Int {
        val skyMaskTex = skyMaskPass.texture
        val showSky = sky.hasArea && skyMaskTex != null && (skyReplace && sky.isActive || !skyReplace)
        if (!showSky) {
            sourcePass.releaseTarget()
            return src.id
        }
        val photoAspect = skyPhoto?.let { it.width.toFloat() / it.height } ?: aspect
        return sourcePass.render(src, skyMaskTex!!.id, skyPhoto?.id ?: 0, photoAspect, sky, skyReplace, phase, aspect).id
    }

    private fun bindTarget(fbo: Int, x: Int, y: Int, width: Int, height: Int) {
        GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, fbo)
        GLES30.glViewport(x, y, width, height)
    }

    private fun ensureFrame(a: Boolean, width: Int, height: Int): GlTexture {
        val existing = if (a) frameA else frameB
        existing?.let { if (it.width == width && it.height == height) return it else it.release() }
        val created = GlTexture.empty(width, height, GLES30.GL_RGBA8, GLES30.GL_RGBA, GLES30.GL_UNSIGNED_BYTE)
        if (a) frameA = created else frameB = created
        return created
    }

    private fun releaseFrames() {
        frameA?.release()
        frameA = null
        frameB?.release()
        frameB = null
    }

    fun release() {
        photo?.release()
        photo = null
        photoBitmap = null
        skyPhoto?.release()
        skyPhoto = null
        skyPhotoBitmap = null
        skyMask.release()
        depthMask.release()
        releaseFrames()
        flowPass.release()
        maskPass.release()
        skyMaskPass.release()
        sourcePass.release()
        warpPass.release()
        colorPass.release()
        overlayPass.release()
        parallaxPass.release()
        quad.release()
    }

    /** Uploads a model mask once per mask object. */
    private class MaskTexture {
        private var mask: AlphaMask? = null
        private var texture: GlTexture? = null
        val id: Int get() = texture?.id ?: 0

        fun set(m: AlphaMask?) {
            if (m === mask) return
            texture?.release()
            texture = m?.let(GlTexture::fromMask)
            mask = m
        }

        fun release() {
            texture?.release()
            texture = null
            mask = null
        }
    }
}
