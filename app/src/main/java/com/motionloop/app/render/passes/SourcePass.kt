package com.motionloop.app.render.passes

import android.content.res.AssetManager
import android.opengl.GLES30
import com.motionloop.app.core.gl.FullscreenQuad
import com.motionloop.app.core.gl.GlProgram
import com.motionloop.app.core.gl.GlTexture
import com.motionloop.app.core.gl.GlUtil
import com.motionloop.app.domain.model.SkySettings

/**
 * Pre-warp stage: renders the photo with its sky replaced into a texture the size of the photo.
 * The warp then samples this texture instead of the photo.
 */
class SourcePass(assets: AssetManager, private val quad: FullscreenQuad) : RenderPass {

    private val program = GlProgram.fromAssets(assets, "fullscreen.vert", "source.frag")

    var texture: GlTexture? = null
        private set

    /** @param replace false shows only a tint over the sky mask (sky panel, before replacing). */
    fun render(
        photo: GlTexture,
        skyMaskTex: Int,
        skyPhotoTex: Int,
        sky: SkySettings,
        replace: Boolean,
        phase: Float,
        aspect: Float,
    ): GlTexture {
        val target = ensureTarget(photo.width, photo.height)
        target.bindAsTarget()
        GLES30.glDisable(GLES30.GL_BLEND)
        program.use()
        program.set1f("uFlipY", 0f)
        program.bindTexture("uPhoto", 0, photo.id)
        program.bindTexture("uSkyMask", 1, skyMaskTex)
        program.bindTexture("uSkyPhoto", 2, skyPhotoTex)
        program.set1i("uStyle", sky.style.ordinal)
        program.set1i("uReplace", if (replace) 1 else 0)
        program.set1f("uPhase", phase)
        program.set1f("uDrift", sky.drift.coerceIn(0f, 1f))
        program.set1f("uFeather", sky.feather.coerceIn(0f, 1f))
        program.set1f("uHarmonize", sky.harmonize.coerceIn(0f, 1f))
        program.set1f("uAspect", aspect)
        quad.draw()
        // The warp minifies this texture, mipmaps keep the result from shimmering.
        target.generateMipmaps()
        GlUtil.checkError("SourcePass")
        return target
    }

    /** Frees the texture while no source stage is needed. */
    fun releaseTarget() {
        texture?.release()
        texture = null
    }

    private fun ensureTarget(width: Int, height: Int): GlTexture {
        texture?.let { if (it.width == width && it.height == height) return it else it.release() }
        return GlTexture.empty(width, height, GLES30.GL_RGBA8, GLES30.GL_RGBA, GLES30.GL_UNSIGNED_BYTE).also {
            it.setMinFilter(GLES30.GL_LINEAR_MIPMAP_LINEAR)
            texture = it
        }
    }

    override fun release() {
        releaseTarget()
        program.release()
    }
}
