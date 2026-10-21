package com.motionloop.app.core.gl

import android.graphics.Bitmap
import android.opengl.GLES30
import android.opengl.GLUtils
import com.motionloop.ml.AlphaMask
import java.nio.ByteBuffer

/** A 2D texture, optionally with a framebuffer so passes can render into it. */
class GlTexture private constructor(val id: Int, val width: Int, val height: Int) {

    private var fbo = 0

    /** Framebuffer with this texture as color attachment, created on first use. */
    fun framebuffer(): Int {
        if (fbo == 0) {
            val ids = IntArray(1)
            GLES30.glGenFramebuffers(1, ids, 0)
            fbo = ids[0]
            GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, fbo)
            GLES30.glFramebufferTexture2D(GLES30.GL_FRAMEBUFFER, GLES30.GL_COLOR_ATTACHMENT0, GLES30.GL_TEXTURE_2D, id, 0)
            val status = GLES30.glCheckFramebufferStatus(GLES30.GL_FRAMEBUFFER)
            check(status == GLES30.GL_FRAMEBUFFER_COMPLETE) { "FBO incomplete: 0x${Integer.toHexString(status)}" }
        }
        return fbo
    }

    /** Binds the FBO and sets the viewport to the full texture. */
    fun bindAsTarget() {
        GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, framebuffer())
        GLES30.glViewport(0, 0, width, height)
    }

    /** For example GL_MIRRORED_REPEAT, so a sky photo can drift past its edges. */
    fun setWrap(mode: Int) {
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, id)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_WRAP_S, mode)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_WRAP_T, mode)
    }

    /** Rebuilds the mip chain after rendering into level 0 (needs a mipmapped min filter). */
    fun generateMipmaps() {
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, id)
        GLES30.glGenerateMipmap(GLES30.GL_TEXTURE_2D)
    }

    fun setMinFilter(filter: Int) {
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, id)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MIN_FILTER, filter)
    }

    fun release() {
        if (fbo != 0) GLES30.glDeleteFramebuffers(1, intArrayOf(fbo), 0)
        GLES30.glDeleteTextures(1, intArrayOf(id), 0)
    }

    companion object {
        /** Mipmapped, so a large photo shown or exported smaller does not shimmer. */
        fun fromBitmap(bitmap: Bitmap): GlTexture {
            val id = newTextureId(GLES30.GL_LINEAR)
            GLUtils.texImage2D(GLES30.GL_TEXTURE_2D, 0, bitmap, 0)
            GLES30.glGenerateMipmap(GLES30.GL_TEXTURE_2D)
            GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MIN_FILTER, GLES30.GL_LINEAR_MIPMAP_LINEAR)
            GlUtil.checkError("texImage2D bitmap")
            return GlTexture(id, bitmap.width, bitmap.height)
        }

        /** No mipmaps, nearest sampling: for bitmaps whose pixel values are ids (typewriter order). */
        fun fromBitmapNearest(bitmap: Bitmap): GlTexture {
            val id = newTextureId(GLES30.GL_NEAREST)
            GLUtils.texImage2D(GLES30.GL_TEXTURE_2D, 0, bitmap, 0)
            GlUtil.checkError("texImage2D nearest")
            return GlTexture(id, bitmap.width, bitmap.height)
        }

        /** Single-channel R8 texture from a model mask (sky, subject, depth, selection). */
        fun fromMask(mask: AlphaMask): GlTexture {
            val id = newTextureId(GLES30.GL_LINEAR)
            // Rows are width bytes, usually not a multiple of 4.
            GLES30.glPixelStorei(GLES30.GL_UNPACK_ALIGNMENT, 1)
            GLES30.glTexImage2D(
                GLES30.GL_TEXTURE_2D, 0, GLES30.GL_R8, mask.width, mask.height, 0,
                GLES30.GL_RED, GLES30.GL_UNSIGNED_BYTE,
                ByteBuffer.allocateDirect(mask.data.size).put(mask.data).position(0),
            )
            GLES30.glPixelStorei(GLES30.GL_UNPACK_ALIGNMENT, 4)
            GlUtil.checkError("texImage2D mask")
            return GlTexture(id, mask.width, mask.height)
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
