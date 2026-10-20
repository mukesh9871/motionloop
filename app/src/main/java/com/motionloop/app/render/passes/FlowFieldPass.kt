package com.motionloop.app.render.passes

import android.content.res.AssetManager
import android.opengl.GLES30
import com.motionloop.app.core.gl.FullscreenQuad
import com.motionloop.app.core.gl.GlProgram
import com.motionloop.app.core.gl.GlTexture
import com.motionloop.app.core.gl.GlUtil
import com.motionloop.app.domain.model.Anchor
import com.motionloop.app.domain.model.MotionArrow
import kotlin.math.max
import kotlin.math.roundToInt

/** Rasterizes arrows and anchors into a low-resolution velocity texture. */
class FlowFieldPass(assets: AssetManager, private val quad: FullscreenQuad) : RenderPass {

    private val program = GlProgram.fromAssets(assets, "fullscreen.vert", "flow_field.frag")

    /** Half-float targets need EXT_color_buffer_half_float on GLES 3.0. Fall back to packed RGBA8. */
    val encoded: Boolean = !(GlUtil.hasExtension("GL_EXT_color_buffer_half_float") ||
        GlUtil.hasExtension("GL_EXT_color_buffer_float"))

    var texture: GlTexture? = null
        private set

    private val arrowData = FloatArray(MAX_ITEMS * 4)
    private val anchorData = FloatArray(MAX_ITEMS * 4)

    fun render(aspect: Float, arrows: List<MotionArrow>, anchors: List<Anchor>) {
        val target = ensureTarget(aspect)
        val arrowCount = minOf(arrows.size, MAX_ITEMS)
        for (i in 0 until arrowCount) {
            val a = arrows[i]
            arrowData[i * 4] = a.start.x
            arrowData[i * 4 + 1] = a.start.y
            arrowData[i * 4 + 2] = a.end.x
            arrowData[i * 4 + 3] = a.end.y
        }
        val anchorCount = minOf(anchors.size, MAX_ITEMS)
        for (i in 0 until anchorCount) {
            val a = anchors[i]
            anchorData[i * 4] = a.center.x
            anchorData[i * 4 + 1] = a.center.y
            anchorData[i * 4 + 2] = a.radius
            anchorData[i * 4 + 3] = 0f
        }

        target.bindAsTarget()
        GLES30.glDisable(GLES30.GL_BLEND)
        program.use()
        program.set1f("uFlipY", 0f)
        program.set1i("uArrowCount", arrowCount)
        program.set4fv("uArrows", MAX_ITEMS, arrowData)
        program.set1i("uAnchorCount", anchorCount)
        program.set4fv("uAnchors", MAX_ITEMS, anchorData)
        program.set1f("uAspect", aspect)
        program.set1f("uEncode", if (encoded) 1f else 0f)
        program.set1f("uMaxFlow", MAX_FLOW)
        quad.draw()
        GlUtil.checkError("FlowFieldPass")
    }

    private fun ensureTarget(aspect: Float): GlTexture {
        val (w, h) = sizeFor(aspect)
        texture?.let { if (it.width == w && it.height == h) return it else it.release() }
        return (
            if (encoded) {
                GlTexture.empty(w, h, GLES30.GL_RGBA8, GLES30.GL_RGBA, GLES30.GL_UNSIGNED_BYTE)
            } else {
                GlTexture.empty(w, h, GLES30.GL_RG16F, GLES30.GL_RG, GLES30.GL_HALF_FLOAT)
            }
        ).also { texture = it }
    }

    override fun release() {
        texture?.release()
        texture = null
        program.release()
    }

    companion object {
        const val MAX_ITEMS = 64

        /** Upper bound on per-cycle displacement, only used by the RGBA8 fallback encoding. */
        const val MAX_FLOW = 1f

        // Flow is smooth, so a small texture with linear filtering is enough.
        private const val LONG_EDGE = 256

        fun sizeFor(aspect: Float): Pair<Int, Int> =
            if (aspect >= 1f) {
                LONG_EDGE to max(1, (LONG_EDGE / aspect).roundToInt())
            } else {
                max(1, (LONG_EDGE * aspect).roundToInt()) to LONG_EDGE
            }
    }
}
