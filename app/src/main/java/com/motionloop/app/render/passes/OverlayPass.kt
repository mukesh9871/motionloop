package com.motionloop.app.render.passes

import android.content.res.AssetManager
import android.opengl.GLES30
import com.motionloop.app.core.gl.FullscreenQuad
import com.motionloop.app.core.gl.GlProgram
import com.motionloop.app.core.gl.GlTexture
import com.motionloop.app.core.gl.GlUtil
import com.motionloop.app.domain.model.LightLeak
import com.motionloop.app.domain.model.OverlaySettings
import com.motionloop.app.domain.model.ParticleSeeds
import com.motionloop.app.domain.model.ParticleType

/**
 * Renders light leaks and particles into a premultiplied RGBA layer.
 * ColorPass then blends the layer over the photo with the chosen blend mode.
 */
class OverlayPass(assets: AssetManager, private val quad: FullscreenQuad) : RenderPass {

    private val particles = GlProgram.fromAssets(assets, "particles.vert", "particles.frag")
    private val leak = GlProgram.fromAssets(assets, "fullscreen.vert", "light_leak.frag")
    private val vao = IntArray(1)
    private val cornerVbo = IntArray(1)
    private val seedVbo = IntArray(1)

    var layer: GlTexture? = null
        private set

    init {
        GLES30.glGenVertexArrays(1, vao, 0)
        GLES30.glGenBuffers(1, cornerVbo, 0)
        GLES30.glGenBuffers(1, seedVbo, 0)
        GLES30.glBindVertexArray(vao[0])

        val corners = floatArrayOf(-1f, -1f, 1f, -1f, -1f, 1f, 1f, 1f)
        GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, cornerVbo[0])
        GLES30.glBufferData(GLES30.GL_ARRAY_BUFFER, corners.size * 4, GlUtil.floatBuffer(corners), GLES30.GL_STATIC_DRAW)
        GLES30.glEnableVertexAttribArray(0)
        GLES30.glVertexAttribPointer(0, 2, GLES30.GL_FLOAT, false, 8, 0)

        // Seeds never change, so upload once.
        val seeds = ParticleSeeds.data
        GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, seedVbo[0])
        GLES30.glBufferData(GLES30.GL_ARRAY_BUFFER, seeds.size * 4, GlUtil.floatBuffer(seeds), GLES30.GL_STATIC_DRAW)
        GLES30.glEnableVertexAttribArray(1)
        GLES30.glVertexAttribPointer(1, 4, GLES30.GL_FLOAT, false, 16, 0)
        GLES30.glVertexAttribDivisor(1, 1)
        GLES30.glBindVertexArray(0)
    }

    /** Renders the overlay layer at [width] x [height]. Leaves the layer FBO bound. */
    fun render(settings: OverlaySettings, phase: Float, aspect: Float, width: Int, height: Int) {
        val target = ensureLayer(width, height)
        target.bindAsTarget()
        GLES30.glClearColor(0f, 0f, 0f, 0f)
        GLES30.glClear(GLES30.GL_COLOR_BUFFER_BIT)
        GLES30.glEnable(GLES30.GL_BLEND)
        GLES30.glBlendEquation(GLES30.GL_FUNC_ADD)
        // Premultiplied "over".
        GLES30.glBlendFunc(GLES30.GL_ONE, GLES30.GL_ONE_MINUS_SRC_ALPHA)

        if (settings.lightLeak != LightLeak.NONE && settings.leakIntensity > 0f) {
            leak.use()
            leak.set1f("uFlipY", 0f)
            leak.set1i("uStyle", settings.lightLeak.ordinal) // NONE = 0, WARM = 1, ...
            leak.set1f("uPhase", phase)
            leak.set1f("uIntensity", settings.leakIntensity.coerceIn(0f, 1f))
            leak.set1f("uAspect", aspect)
            quad.draw()
        }

        if (settings.particles != ParticleType.NONE) {
            particles.use()
            particles.set1f("uFlipY", 0f)
            particles.set1i("uType", settings.particles.ordinal) // NONE = 0, SPARKLE = 1, ...
            particles.set1f("uPhase", phase)
            particles.set1f(
                "uSpeed",
                settings.speed.coerceIn(OverlaySettings.MIN_SPEED, OverlaySettings.MAX_SPEED).toFloat(),
            )
            particles.set1f("uSize", settings.size.coerceIn(0f, 1f))
            particles.set1f("uAspect", aspect)
            particles.set1f("uPixel", 1f / width)
            GLES30.glBindVertexArray(vao[0])
            GLES30.glDrawArraysInstanced(GLES30.GL_TRIANGLE_STRIP, 0, 4, settings.particleCount)
            GLES30.glBindVertexArray(0)
        }

        GLES30.glDisable(GLES30.GL_BLEND)
        GlUtil.checkError("OverlayPass")
    }

    /** Frees the layer while overlays are off. */
    fun releaseLayer() {
        layer?.release()
        layer = null
    }

    private fun ensureLayer(width: Int, height: Int): GlTexture {
        layer?.let { if (it.width == width && it.height == height) return it else it.release() }
        return GlTexture.empty(width, height, GLES30.GL_RGBA8, GLES30.GL_RGBA, GLES30.GL_UNSIGNED_BYTE)
            .also { layer = it }
    }

    override fun release() {
        layer?.release()
        layer = null
        GLES30.glDeleteBuffers(2, intArrayOf(cornerVbo[0], seedVbo[0]), 0)
        GLES30.glDeleteVertexArrays(1, vao, 0)
        particles.release()
        leak.release()
    }
}
