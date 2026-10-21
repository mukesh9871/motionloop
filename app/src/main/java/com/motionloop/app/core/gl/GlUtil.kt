package com.motionloop.app.core.gl

import android.opengl.GLES30
import android.util.Log
import com.motionloop.app.BuildConfig
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer

object GlUtil {
    private const val TAG = "GlUtil"

    /** Debug builds only: glGetError stalls the GPU pipeline. */
    fun checkError(op: String) {
        if (!BuildConfig.DEBUG) return
        val err = GLES30.glGetError()
        if (err != GLES30.GL_NO_ERROR) {
            val msg = "$op: glError 0x${Integer.toHexString(err)}"
            Log.e(TAG, msg)
            throw IllegalStateException(msg)
        }
    }

    fun floatBuffer(values: FloatArray): FloatBuffer =
        ByteBuffer.allocateDirect(values.size * 4).order(ByteOrder.nativeOrder()).asFloatBuffer().apply {
            put(values)
            position(0)
        }

    fun hasExtension(name: String): Boolean =
        GLES30.glGetString(GLES30.GL_EXTENSIONS)?.split(' ')?.contains(name) == true

    fun maxTextureSize(): Int {
        val v = IntArray(1)
        GLES30.glGetIntegerv(GLES30.GL_MAX_TEXTURE_SIZE, v, 0)
        return v[0]
    }
}

/** Fullscreen triangle strip with a [0,1] uv. Shared by every full-frame pass. */
class FullscreenQuad {
    private val vao = IntArray(1)
    private val vbo = IntArray(1)

    init {
        // x, y, u, v. Clip y = -1 maps to v = 0, the first row of an uploaded bitmap (image top).
        val data = floatArrayOf(
            -1f, -1f, 0f, 0f,
            1f, -1f, 1f, 0f,
            -1f, 1f, 0f, 1f,
            1f, 1f, 1f, 1f,
        )
        GLES30.glGenVertexArrays(1, vao, 0)
        GLES30.glGenBuffers(1, vbo, 0)
        GLES30.glBindVertexArray(vao[0])
        GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, vbo[0])
        GLES30.glBufferData(GLES30.GL_ARRAY_BUFFER, data.size * 4, GlUtil.floatBuffer(data), GLES30.GL_STATIC_DRAW)
        GLES30.glEnableVertexAttribArray(0)
        GLES30.glVertexAttribPointer(0, 2, GLES30.GL_FLOAT, false, 16, 0)
        GLES30.glEnableVertexAttribArray(1)
        GLES30.glVertexAttribPointer(1, 2, GLES30.GL_FLOAT, false, 16, 8)
        GLES30.glBindVertexArray(0)
    }

    fun draw() {
        GLES30.glBindVertexArray(vao[0])
        GLES30.glDrawArrays(GLES30.GL_TRIANGLE_STRIP, 0, 4)
        GLES30.glBindVertexArray(0)
    }

    /** [count] copies of the quad; the vertex shader places each one by gl_InstanceID. */
    fun drawInstanced(count: Int) {
        GLES30.glBindVertexArray(vao[0])
        GLES30.glDrawArraysInstanced(GLES30.GL_TRIANGLE_STRIP, 0, 4, count)
        GLES30.glBindVertexArray(0)
    }

    fun release() {
        GLES30.glDeleteBuffers(1, vbo, 0)
        GLES30.glDeleteVertexArrays(1, vao, 0)
    }
}
