package com.motionloop.app.render.passes

import android.content.res.AssetManager
import android.opengl.GLES30
import com.motionloop.app.core.gl.FullscreenQuad
import com.motionloop.app.core.gl.GlProgram
import com.motionloop.app.core.gl.GlUtil
import com.motionloop.app.domain.model.Adjustments
import com.motionloop.app.domain.model.LutData
import java.nio.ByteBuffer

/** Brightness, contrast, saturation, warmth and a 3D LUT grade. Draws into the bound target. */
class ColorPass(assets: AssetManager, private val quad: FullscreenQuad) : RenderPass {

    private val program = GlProgram.fromAssets(assets, "fullscreen.vert", "color.frag")
    private var lutTexture = 0
    private var lutSize = 0
    private var uploadedLut: LutData? = null

    /** Uploads [lut] as a 3D texture when it changed. Null frees the texture. */
    fun setLut(lut: LutData?) {
        if (lut === uploadedLut) return
        uploadedLut = lut
        deleteLut()
        if (lut == null) return
        val ids = IntArray(1)
        GLES30.glGenTextures(1, ids, 0)
        lutTexture = ids[0]
        lutSize = lut.size
        GLES30.glBindTexture(GLES30.GL_TEXTURE_3D, lutTexture)
        for (p in intArrayOf(GLES30.GL_TEXTURE_WRAP_S, GLES30.GL_TEXTURE_WRAP_T, GLES30.GL_TEXTURE_WRAP_R)) {
            GLES30.glTexParameteri(GLES30.GL_TEXTURE_3D, p, GLES30.GL_CLAMP_TO_EDGE)
        }
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_3D, GLES30.GL_TEXTURE_MIN_FILTER, GLES30.GL_LINEAR)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_3D, GLES30.GL_TEXTURE_MAG_FILTER, GLES30.GL_LINEAR)
        // Rows are size * 3 bytes, usually not a multiple of 4.
        GLES30.glPixelStorei(GLES30.GL_UNPACK_ALIGNMENT, 1)
        GLES30.glTexImage3D(
            GLES30.GL_TEXTURE_3D, 0, GLES30.GL_RGB8, lut.size, lut.size, lut.size, 0,
            GLES30.GL_RGB, GLES30.GL_UNSIGNED_BYTE,
            ByteBuffer.allocateDirect(lut.rgb.size).put(lut.rgb).position(0),
        )
        GLES30.glPixelStorei(GLES30.GL_UNPACK_ALIGNMENT, 4)
        GLES30.glBindTexture(GLES30.GL_TEXTURE_3D, 0)
        GlUtil.checkError("ColorPass LUT upload")
    }

    fun draw(srcTex: Int, adjustments: Adjustments, flipY: Boolean) {
        program.use()
        program.set1f("uFlipY", if (flipY) 1f else 0f)
        program.bindTexture("uSrc", 0, srcTex)
        // sampler3D needs its own unit: two sampler types on one unit is a draw-time error.
        GLES30.glActiveTexture(GLES30.GL_TEXTURE1)
        GLES30.glBindTexture(GLES30.GL_TEXTURE_3D, lutTexture)
        program.set1i("uLut", 1)
        val hasLut = lutTexture != 0 && adjustments.gradeId != null
        program.set1f("uLutSize", if (hasLut) lutSize.toFloat() else 1f)
        program.set1f("uLutMix", if (hasLut) adjustments.gradeIntensity.coerceIn(0f, 1f) else 0f)
        program.set1f("uBrightness", adjustments.brightness)
        program.set1f("uContrast", adjustments.contrast)
        program.set1f("uSaturation", adjustments.saturation)
        program.set1f("uWarmth", adjustments.warmth)
        quad.draw()
        GLES30.glBindTexture(GLES30.GL_TEXTURE_3D, 0)
        GLES30.glActiveTexture(GLES30.GL_TEXTURE0)
        GlUtil.checkError("ColorPass")
    }

    private fun deleteLut() {
        if (lutTexture != 0) GLES30.glDeleteTextures(1, intArrayOf(lutTexture), 0)
        lutTexture = 0
        lutSize = 0
    }

    override fun release() {
        deleteLut()
        uploadedLut = null
        program.release()
    }
}
