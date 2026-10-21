package com.motionloop.app.render.passes

import android.content.res.AssetManager
import android.opengl.GLES30
import com.motionloop.app.core.gl.FullscreenQuad
import com.motionloop.app.core.gl.GlProgram
import com.motionloop.app.core.gl.GlTexture
import com.motionloop.app.core.gl.GlUtil
import kotlin.math.max

/**
 * Strong blur of the photo for the "blurred original" background. Rendered once per photo at
 * a quarter of its size (blur hides the lower resolution), as two rounds of separable passes.
 */
class BlurPass(assets: AssetManager, private val quad: FullscreenQuad) : RenderPass {

    private val blur = GlProgram.fromAssets(assets, "fullscreen.vert", "blur.frag")
    private var a: GlTexture? = null
    private var b: GlTexture? = null
    // Compared by object: a new texture can reuse a deleted texture's GL id.
    private var builtFor: GlTexture? = null

    /** The blurred photo, rebuilt only when [photo] changes. */
    fun texture(photo: GlTexture): GlTexture {
        val current = a
        if (current != null && builtFor === photo) return current
        val w = max(1, photo.width / 4)
        val h = max(1, photo.height / 4)
        release(keepPrograms = true)
        val ta = GlTexture.empty(w, h, GLES30.GL_RGBA8, GLES30.GL_RGBA, GLES30.GL_UNSIGNED_BYTE).also { a = it }
        val tb = GlTexture.empty(w, h, GLES30.GL_RGBA8, GLES30.GL_RGBA, GLES30.GL_UNSIGNED_BYTE).also { b = it }
        GLES30.glDisable(GLES30.GL_BLEND)

        // Downsample: the photo is mipmapped, so a plain linear draw averages it well.
        ta.bindAsTarget()
        blur.use()
        blur.set1f("uFlipY", 0f)
        blur.bindTexture("uSrc", 0, photo.id)
        blur.set2f("uStep", 0f, 0f)
        quad.draw()

        repeat(2) {
            tb.bindAsTarget()
            blur.bindTexture("uSrc", 0, ta.id)
            blur.set2f("uStep", 1f / w, 0f)
            quad.draw()
            ta.bindAsTarget()
            blur.bindTexture("uSrc", 0, tb.id)
            blur.set2f("uStep", 0f, 1f / h)
            quad.draw()
        }
        b?.release()
        b = null
        builtFor = photo
        GlUtil.checkError("BlurPass")
        return ta
    }

    private fun release(keepPrograms: Boolean) {
        a?.release()
        a = null
        b?.release()
        b = null
        builtFor = null
        if (!keepPrograms) blur.release()
    }

    override fun release() = release(keepPrograms = false)
}
