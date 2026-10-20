package com.motionloop.app.core.gl

import android.content.res.AssetManager
import android.opengl.GLES30

class GlProgram(vertexSrc: String, fragmentSrc: String) {

    val id: Int
    private val uniforms = HashMap<String, Int>()

    init {
        val vs = compile(GLES30.GL_VERTEX_SHADER, vertexSrc)
        val fs = try {
            compile(GLES30.GL_FRAGMENT_SHADER, fragmentSrc)
        } catch (e: IllegalStateException) {
            GLES30.glDeleteShader(vs)
            throw e
        }
        id = GLES30.glCreateProgram()
        GLES30.glAttachShader(id, vs)
        GLES30.glAttachShader(id, fs)
        GLES30.glLinkProgram(id)
        val status = IntArray(1)
        GLES30.glGetProgramiv(id, GLES30.GL_LINK_STATUS, status, 0)
        GLES30.glDeleteShader(vs)
        GLES30.glDeleteShader(fs)
        if (status[0] == 0) {
            val log = GLES30.glGetProgramInfoLog(id)
            GLES30.glDeleteProgram(id)
            throw IllegalStateException("Program link failed: $log")
        }
    }

    fun use() = GLES30.glUseProgram(id)

    fun loc(name: String): Int = uniforms.getOrPut(name) { GLES30.glGetUniformLocation(id, name) }

    fun set1i(name: String, v: Int) = GLES30.glUniform1i(loc(name), v)
    fun set1f(name: String, v: Float) = GLES30.glUniform1f(loc(name), v)
    fun set2f(name: String, x: Float, y: Float) = GLES30.glUniform2f(loc(name), x, y)
    fun set4fv(name: String, count: Int, v: FloatArray) = GLES30.glUniform4fv(loc(name), count, v, 0)

    fun bindTexture(name: String, unit: Int, textureId: Int) {
        GLES30.glActiveTexture(GLES30.GL_TEXTURE0 + unit)
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, textureId)
        set1i(name, unit)
    }

    fun release() = GLES30.glDeleteProgram(id)

    companion object {
        fun fromAssets(assets: AssetManager, vertex: String, fragment: String) = GlProgram(
            assets.open("shaders/$vertex").bufferedReader().use { it.readText() },
            assets.open("shaders/$fragment").bufferedReader().use { it.readText() },
        )

        private fun compile(type: Int, src: String): Int {
            val shader = GLES30.glCreateShader(type)
            GLES30.glShaderSource(shader, src)
            GLES30.glCompileShader(shader)
            val status = IntArray(1)
            GLES30.glGetShaderiv(shader, GLES30.GL_COMPILE_STATUS, status, 0)
            if (status[0] == 0) {
                val log = GLES30.glGetShaderInfoLog(shader)
                GLES30.glDeleteShader(shader)
                throw IllegalStateException("Shader compile failed: $log")
            }
            return shader
        }
    }
}
