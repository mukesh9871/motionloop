package com.motionloop.app.render.passes

import android.content.res.AssetManager
import android.graphics.Color
import android.opengl.GLES30
import com.motionloop.app.core.gl.FullscreenQuad
import com.motionloop.app.core.gl.GlProgram
import com.motionloop.app.core.gl.GlTexture
import com.motionloop.app.core.gl.GlUtil
import com.motionloop.app.domain.model.BackgroundSettings
import com.motionloop.app.domain.model.SkySettings

/**
 * Pre-warp stage: renders the photo with its sky and/or background replaced into a texture the
 * size of the photo. The warp then samples this texture instead of the photo.
 */
class SourcePass(assets: AssetManager, private val quad: FullscreenQuad) : RenderPass {

    /** Sky inputs. [replace] false only tints the sky area (sky panel, editing view). */
    class SkyInput(
        val maskTex: Int,
        val photoTex: Int,
        val photoAspect: Float,
        val settings: SkySettings,
        val replace: Boolean,
    )

    /** Background inputs. [tintOnly] shows the subject as a tint (background panel, editing view). */
    class BackgroundInput(
        val subjectTex: Int,
        val settings: BackgroundSettings,
        val feather: Float,
        val tintOnly: Boolean,
        val blurredTex: Int,
        val photoTex: Int,
        val photoAspect: Float,
        /** Checkerboard for transparent areas in the editor, black in video. */
        val checker: Boolean,
    )

    private val program = GlProgram.fromAssets(assets, "fullscreen.vert", "source.frag")

    var texture: GlTexture? = null
        private set

    fun render(photo: GlTexture, sky: SkyInput?, background: BackgroundInput?, phase: Float, aspect: Float): GlTexture {
        val target = ensureTarget(photo.width, photo.height)
        target.bindAsTarget()
        GLES30.glDisable(GLES30.GL_BLEND)
        program.use()
        program.set1f("uFlipY", 0f)
        program.bindTexture("uPhoto", 0, photo.id)
        program.set1f("uPhase", phase)
        program.set1f("uAspect", aspect)

        program.set1i("uSkyOn", if (sky != null) 1 else 0)
        program.bindTexture("uSkyMask", 1, sky?.maskTex ?: 0)
        program.bindTexture("uSkyPhoto", 2, sky?.photoTex ?: 0)
        if (sky != null) {
            program.set1i("uStyle", sky.settings.style.ordinal)
            program.set1i("uReplace", if (sky.replace) 1 else 0)
            program.set1f("uDrift", sky.settings.drift.coerceIn(0f, 1f))
            program.set1f("uFeather", sky.settings.feather.coerceIn(0f, 1f))
            program.set1f("uHarmonize", sky.settings.harmonize.coerceIn(0f, 1f))
            program.set1f("uSkyPhotoAspect", sky.photoAspect)
        }

        program.bindTexture("uSubjectMask", 3, background?.subjectTex ?: 0)
        program.bindTexture("uBlurred", 4, background?.blurredTex ?: 0)
        program.bindTexture("uBgPhoto", 5, background?.photoTex ?: 0)
        val bgMode = when {
            background == null -> -1
            background.tintOnly -> 0
            else -> background.settings.mode.ordinal
        }
        program.set1i("uBgMode", bgMode)
        if (background != null) {
            val c1 = background.settings.color1
            val c2 = background.settings.color2
            GLES30.glUniform3f(program.loc("uBgColor1"), Color.red(c1) / 255f, Color.green(c1) / 255f, Color.blue(c1) / 255f)
            GLES30.glUniform3f(program.loc("uBgColor2"), Color.red(c2) / 255f, Color.green(c2) / 255f, Color.blue(c2) / 255f)
            program.set1f("uBgBlur", background.settings.blur.coerceIn(0f, 1f))
            program.set1f("uBgPhotoAspect", background.photoAspect)
            program.set1f("uSubjectFeather", background.feather.coerceIn(0f, 1f))
            program.set1i("uChecker", if (background.checker) 1 else 0)
        }
        quad.draw()
        GLES30.glActiveTexture(GLES30.GL_TEXTURE0)
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
