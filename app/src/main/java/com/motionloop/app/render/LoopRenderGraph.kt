package com.motionloop.app.render

import android.content.res.AssetManager
import android.graphics.Bitmap
import android.opengl.GLES30
import com.motionloop.app.core.gl.FullscreenQuad
import com.motionloop.app.core.gl.GlProgram
import com.motionloop.app.core.gl.GlTexture
import com.motionloop.app.domain.model.Adjustments
import com.motionloop.app.domain.model.BackgroundMode
import com.motionloop.app.domain.model.BackgroundSettings
import com.motionloop.app.domain.model.SelectionMode
import com.motionloop.app.domain.model.SubjectSettings
import com.motionloop.app.domain.model.Anchor
import com.motionloop.app.domain.model.LoopMode
import com.motionloop.app.domain.model.LutData
import com.motionloop.app.domain.model.MaskStroke
import com.motionloop.app.domain.model.MotionArrow
import com.motionloop.app.domain.model.OverlaySettings
import com.motionloop.app.domain.model.ParallaxSettings
import com.motionloop.app.domain.model.SkySettings
import com.motionloop.app.domain.model.SkyStyle
import com.motionloop.app.render.passes.BlurPass
import com.motionloop.app.render.passes.ColorPass
import com.motionloop.app.render.passes.FlowFieldPass
import com.motionloop.app.render.passes.LoopWarpPass
import com.motionloop.app.render.passes.MaskPass
import com.motionloop.app.render.passes.OverlayPass
import com.motionloop.app.render.passes.ParallaxPass
import com.motionloop.app.render.passes.SourcePass
import com.motionloop.app.render.passes.TextDraw
import com.motionloop.app.render.passes.TextPass
import com.motionloop.ml.AlphaMask

/**
 * The pass chain shared by the editor preview, the exporters and the live wallpaper.
 *
 * photo -> [Source: sky, background] -> LoopWarp (flow, freeze mask) -> [Behind text] -> [Parallax] -> [Front text]
 *       -> [Color + overlay] -> target
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
    private val subjectMaskPass = MaskPass(assets, quad)
    private val blurPass = BlurPass(assets, quad)
    private val maskCopy = GlProgram.fromAssets(assets, "fullscreen.vert", "mask_layer.frag")
    private val sourcePass = SourcePass(assets, quad)
    private val warpPass = LoopWarpPass(assets, quad)
    private val colorPass = ColorPass(assets, quad)
    private val overlayPass = OverlayPass(assets, quad)
    private val parallaxPass = ParallaxPass(assets, quad)
    private val textPass = TextPass(assets, quad)
    private var texts: List<TextDraw> = emptyList()

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

    private var skyPhotoBitmap: Bitmap? = null

    private var parallax = ParallaxSettings()
    private val depthMask = MaskTexture()

    // Subject (11d background, 11e text) and tap-to-select regions (11a).
    private var subject = SubjectSettings()
    private val subjectMask = MaskTexture()
    private var background = BackgroundSettings()
    private var subjectTintOnly = false
    private var checker = true
    private var bgPhoto: GlTexture? = null
    private var bgPhotoBitmap: Bitmap? = null
    private var lastSubjectStrokes: List<MaskStroke>? = null
    private var lastSubjectMask: AlphaMask? = null
    private var subjectMaskBuilt = false
    private val selectionTextures = ArrayList<MaskTexture>()
    private val animateOnlyTexture = MaskTexture()
    private var lastSelections: List<SelectionLayer>? = null

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

    // Text behind the subject (11e): before parallax, so it moves with the background in 3D and the
    // subject drawn over it keeps hiding it correctly.
    private val behindTextStage = object : PostStage {
        private var phase = 0f

        override fun prepare(phase: Float, width: Int, height: Int) {
            this.phase = phase
        }

        override fun draw(inputTex: Int, flipY: Boolean) =
            textPass.drawBehind(inputTex, subjectMaskTextureId, subject.feather, texts, phase, aspect, flipY)
    }

    // Text in front: after parallax, so the depth map never shears it.
    private val frontTextStage = object : PostStage {
        private var phase = 0f

        override fun prepare(phase: Float, width: Int, height: Int) {
            this.phase = phase
        }

        override fun draw(inputTex: Int, flipY: Boolean) =
            textPass.drawFront(inputTex, texts, subjectMaskTextureId != 0, phase, aspect, flipY)
    }

    // 3D camera move by depth, applied to the warped photo (and text) before color and overlays.
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
        subjectMaskBuilt = false
    }

    /**
     * Motion inputs. The freeze mask is the tap-to-select [selections] (model masks) with the
     * brush [strokes] on top, so strokes can refine a selected region.
     */
    fun setGeometry(
        arrows: List<MotionArrow>,
        anchors: List<Anchor>,
        strokes: List<MaskStroke>,
        selections: List<SelectionLayer> = emptyList(),
    ) {
        if (photo == null) return
        if (arrows != lastArrows || anchors != lastAnchors) {
            flowPass.render(aspect, arrows, anchors)
            lastArrows = arrows
            lastAnchors = anchors
        }
        // SelectionLayer equality compares masks by object, so this is cheap.
        if (strokes != lastStrokes || selections != lastSelections) {
            // Freeze regions add to the freeze mask one by one. "Only this" regions are first joined,
            // then everything outside the union freezes: inverting each one alone would leave only
            // their overlap moving.
            val freeze = selections.filter { it.mode == SelectionMode.FREEZE }
            val animateOnly = selections.filter { it.mode == SelectionMode.ANIMATE_ONLY }.map { it.mask }
            while (selectionTextures.size < freeze.size) selectionTextures += MaskTexture()
            while (selectionTextures.size > freeze.size) selectionTextures.removeAt(selectionTextures.lastIndex).release()
            val layers = ArrayList<MaskPass.Layer>()
            if (animateOnly.isNotEmpty()) {
                // Small masks (segmenter resolution), joined once per change, not per frame.
                animateOnlyTexture.set(if (animateOnly.size == 1) animateOnly[0] else cachedUnion(animateOnly))
                layers += MaskPass.Layer(animateOnlyTexture.id, MaskPass.LayerMode.ADD_INVERTED)
            } else {
                animateOnlyTexture.set(null)
            }
            freeze.forEachIndexed { i, sel ->
                selectionTextures[i].set(sel.mask)
                layers += MaskPass.Layer(selectionTextures[i].id, MaskPass.LayerMode.ADD)
            }
            maskPass.render(aspect, strokes, layers)
            lastStrokes = strokes
            lastSelections = selections
        }
    }

    /**
     * Subject and background inputs. [mask] is the model mask for [subject].maskRev (null if none or
     * not loaded), [bgPhoto] the photo for [BackgroundMode.PHOTO]. [tintOnly] shows the subject as a
     * tint (background panel, editing view). [checker] draws transparency as a checkerboard (editor).
     */
    fun setSubject(
        subject: SubjectSettings,
        mask: AlphaMask?,
        background: BackgroundSettings,
        bgPhoto: Bitmap?,
        tintOnly: Boolean,
        checker: Boolean,
    ) {
        if (photo == null) return
        this.subject = subject
        this.background = background
        this.subjectTintOnly = tintOnly
        this.checker = checker
        subjectMask.set(mask)
        if (bgPhoto !== bgPhotoBitmap) {
            this.bgPhoto?.release()
            this.bgPhoto = bgPhoto?.let(GlTexture::fromBitmap)
            bgPhotoBitmap = bgPhoto
        }
        if (!subjectMaskBuilt || subject.strokes != lastSubjectStrokes || mask !== lastSubjectMask) {
            val maskId = subjectMask.id
            val layers = if (maskId != 0) listOf(MaskPass.Layer(maskId, MaskPass.LayerMode.ADD)) else emptyList()
            subjectMaskPass.render(aspect, subject.strokes, layers)
            lastSubjectStrokes = subject.strokes
            lastSubjectMask = mask
            subjectMaskBuilt = true
        }
    }

    /** Draws the refined subject mask (red channel, row 0 = image top) into [fbo], for PNG cutouts. */
    fun drawSubjectMask(fbo: Int, width: Int, height: Int) {
        bindTarget(fbo, 0, 0, width, height)
        GLES30.glDisable(GLES30.GL_BLEND)
        GLES30.glClearColor(0f, 0f, 0f, 1f)
        GLES30.glClear(GLES30.GL_COLOR_BUFFER_BIT)
        val id = subjectMaskTextureId
        if (id == 0) return
        maskCopy.use()
        maskCopy.set1f("uFlipY", 0f)
        maskCopy.bindTexture("uMask", 0, id)
        maskCopy.set1f("uInvert", 0f)
        quad.draw()
    }

    /**
     * Draws the behind (or front) text layers alone, premultiplied, over transparent black into [fbo].
     * PNG cutouts composite them with the subject themselves, so text keeps its own colors at the edges.
     */
    fun drawTextLayers(fbo: Int, width: Int, height: Int, behind: Boolean, phase: Float) {
        bindTarget(fbo, 0, 0, width, height)
        GLES30.glClearColor(0f, 0f, 0f, 0f)
        GLES30.glClear(GLES30.GL_COLOR_BUFFER_BIT)
        if (texts.isNotEmpty()) textPass.drawLayers(texts, subjectMaskTextureId != 0, behind, phase, aspect)
    }

    /** Texts are not drawn into frames while this is false (PNG cutouts draw them separately). */
    var drawTextsInFrame = true

    /** Refined subject mask (model + strokes), for text behind the subject. 0 when there is none. */
    val subjectMaskTextureId: Int
        get() = if (subject.hasArea) subjectMaskPass.texture?.id ?: 0 else 0

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

    /** Text layers to draw. Bitmaps are uploaded once per object and freed when no longer listed. */
    fun setTexts(texts: List<TextDraw>) {
        this.texts = texts
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

        val stages = ArrayList<PostStage>(4)
        // Behind text and parallax move with the photo, front text sits on top of the 3D move.
        // Color is per pixel and overlays stay in screen space.
        val hasSubject = subjectMaskTextureId != 0
        val frameTexts = drawTextsInFrame && texts.isNotEmpty()
        if (frameTexts && texts.any { textPass.isBehind(it, hasSubject) }) stages += behindTextStage
        if (parallax.isActive && depthMask.id != 0) stages += parallaxStage
        if (frameTexts && texts.any { !textPass.isBehind(it, hasSubject) }) stages += frontTextStage
        if (!adjustments.isIdentity || overlay.isActive) stages += colorStage
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

    /** The photo, or the photo with its sky and/or background replaced when those stages are active. */
    private fun sourceTexture(src: GlTexture, phase: Float): Int {
        val skyMaskTex = skyMaskPass.texture
        val skyInput = if (sky.hasArea && skyMaskTex != null && (!skyReplace || sky.isActive)) {
            val photoAspect = skyPhoto?.let { it.width.toFloat() / it.height } ?: aspect
            SourcePass.SkyInput(skyMaskTex.id, skyPhoto?.id ?: 0, photoAspect, sky, skyReplace)
        } else {
            null
        }

        val subjectTex = subjectMaskPass.texture
        val bgOn = subject.hasArea && subjectTex != null && (subjectTintOnly || background.isActive(subject))
        val bgInput = if (bgOn) {
            val blurred = if (!subjectTintOnly && background.mode == BackgroundMode.BLUR) blurPass.texture(src).id else 0
            val bgAspect = bgPhoto?.let { it.width.toFloat() / it.height } ?: aspect
            SourcePass.BackgroundInput(
                subjectTex!!.id, background, subject.feather, subjectTintOnly,
                blurred, bgPhoto?.id ?: 0, bgAspect, checker,
            )
        } else {
            null
        }

        if (skyInput == null && bgInput == null) {
            sourcePass.releaseTarget()
            return src.id
        }
        return sourcePass.render(src, skyInput, bgInput, phase, aspect).id
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
        subjectMask.release()
        selectionTextures.forEach { it.release() }
        selectionTextures.clear()
        animateOnlyTexture.release()
        bgPhoto?.release()
        bgPhoto = null
        bgPhotoBitmap = null
        releaseFrames()
        flowPass.release()
        maskPass.release()
        skyMaskPass.release()
        subjectMaskPass.release()
        blurPass.release()
        maskCopy.release()
        sourcePass.release()
        warpPass.release()
        colorPass.release()
        overlayPass.release()
        parallaxPass.release()
        textPass.release()
        quad.release()
    }

    // Brush strokes rebuild the freeze mask on every move: keep the joined mask while its inputs are the same objects.
    private var unionInputs: List<AlphaMask> = emptyList()
    private var unionResult: AlphaMask? = null

    private fun cachedUnion(masks: List<AlphaMask>): AlphaMask {
        val cached = unionResult
        if (cached != null && masks.size == unionInputs.size && masks.indices.all { masks[it] === unionInputs[it] }) return cached
        return masks.drop(1).fold(masks.first()) { acc, m -> acc.combine(m, subtract = false) }.also {
            unionInputs = masks
            unionResult = it
        }
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
