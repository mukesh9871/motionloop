package com.motionloop.app.core.gl

import android.graphics.Bitmap
import android.opengl.GLES30
import android.opengl.GLUtils

/** A 2D texture, optionally with a framebuffer so passes can render into it. */
class GlTexture private constructor(val id: Int, val width: Int, val height: Int) {

    private var fbo = 0

    /** Lazily creates the FBO, binds it and sets the viewport to the full texture. */
    fun bindAsTarget() {
        if (fbo == 0) {
            val ids = IntArray(1)
            GLES30.glGenFramebuffers(1, ids, 0)
            fbo = ids[0]
            GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, fbo)
            GLES30.glFramebufferTexture2D(GLES30.GL_FRAMEBUFFER, GLES30.GL_COLOR_ATTACHMENT0, GLES30.GL_TEXTURE_2D, id, 0)
            val status = GLES30.glCheckFramebufferStatus(GLES30.GL_FRAMEBUFFER)
            check(status == GLES30.GL_FRAMEBUFFER_COMPLETE) { "FBO incomplete: 0x${Integer.toHexString(status)}" }
        } else {
            GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, fbo)
        }
        GLES30.glViewport(0, 0, width, height)
    }

    fun release() {
        if (fbo != 0) GLES30.glDeleteFramebuffers(1, intArrayOf(fbo), 0)
        GLES30.glDeleteTextures(1, intArrayOf(id), 0)
    }

    companion object {
        fun fromBitmap(bitmap: Bitmap): GlTexture {
            val id = newTextureId(GLES30.GL_LINEAR)
            GLUtils.texImage2D(GLES30.GL_TEXTURE_2D, 0, bitmap, 0)
            GlUtil.checkError("texImage2D bitmap")
            return GlTexture(id, bitmap.width, bitmap.height)
        }

        fun empty(width: Int, height: Int, internalFormat: Int, format: Int, type: Int): GlTexture {
            val id = newTextureId(GLES30.GL_LINEAR)
            GLES30.glTexImage2D(GLES30.GL_TEXTURE_2D, 0, internalFormat, width, height, 0, format, type, null)
            GlUtil.checkError("texImage2D empty")
            return GlTexture(id, width, height)
        }

        private fun newTextureId(filter: Int): Int {
            val ids = IntArray(1)
            GLES30.glGenTextures(1, ids, 0)
            GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, ids[0])
            GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MIN_FILTER, filter)
            GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MAG_FILTER, filter)
            GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_WRAP_S, GLES30.GL_CLAMP_TO_EDGE)
            GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_WRAP_T, GLES30.GL_CLAMP_TO_EDGE)
            return ids[0]
        }
    }
}
