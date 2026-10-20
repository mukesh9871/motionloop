package com.motionloop.app.render.passes

import android.content.res.AssetManager
import android.opengl.GLES30
import com.motionloop.app.core.gl.GlProgram
import com.motionloop.app.core.gl.GlTexture
import com.motionloop.app.core.gl.GlUtil
import com.motionloop.app.domain.model.MaskMode
import com.motionloop.app.domain.model.MaskStroke
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Replays brush strokes into an R8 freeze mask (1 = frozen).
 * Each stroke becomes a row of soft circular stamps drawn with instancing.
 */
class MaskPass(assets: AssetManager) : RenderPass {

    private val program = GlProgram.fromAssets(assets, "mask_stamp.vert", "mask_stamp.frag")
    private val vao = IntArray(1)
    private val cornerVbo = IntArray(1)
    private val stampVbo = IntArray(1)
    private var stampCapacity = 0
    private var stamps: FloatBuffer = allocate(1024)

    var texture: GlTexture? = null
        private set

    init {
        GLES30.glGenVertexArrays(1, vao, 0)
        GLES30.glGenBuffers(1, cornerVbo, 0)
        GLES30.glGenBuffers(1, stampVbo, 0)
        GLES30.glBindVertexArray(vao[0])

        val corners = floatArrayOf(-1f, -1f, 1f, -1f, -1f, 1f, 1f, 1f)
        GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, cornerVbo[0])
        GLES30.glBufferData(GLES30.GL_ARRAY_BUFFER, corners.size * 4, GlUtil.floatBuffer(corners), GLES30.GL_STATIC_DRAW)
        GLES30.glEnableVertexAttribArray(0)
        GLES30.glVertexAttribPointer(0, 2, GLES30.GL_FLOAT, false, 8, 0)

        GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, stampVbo[0])
        GLES30.glEnableVertexAttribArray(1)
        GLES30.glVertexAttribPointer(1, 3, GLES30.GL_FLOAT, false, 12, 0)
        GLES30.glVertexAttribDivisor(1, 1)
        GLES30.glBindVertexArray(0)
    }

    fun render(aspect: Float, strokes: List<MaskStroke>) {
        val target = ensureTarget(aspect)
        target.bindAsTarget()
        GLES30.glClearColor(0f, 0f, 0f, 0f)
        GLES30.glClear(GLES30.GL_COLOR_BUFFER_BIT)
        if (strokes.isEmpty()) return

        program.use()
        program.set1f("uAspect", aspect)
        GLES30.glEnable(GLES30.GL_BLEND)
        GLES30.glBindVertexArray(vao[0])
        for (stroke in strokes) {
            val count = fillStamps(stroke, aspect)
            if (count == 0) continue
            upload(count)
            program.set1f("uHardness", stroke.hardness.coerceIn(0f, 0.95f))
            if (stroke.mode == MaskMode.FREEZE) {
                // max() so overlapping soft stamps do not build up a hard edge.
                GLES30.glBlendEquation(GLES30.GL_MAX)
            } else {
                // dst * (1 - stamp)
                GLES30.glBlendEquation(GLES30.GL_FUNC_ADD)
                GLES30.glBlendFunc(GLES30.GL_ZERO, GLES30.GL_ONE_MINUS_SRC_COLOR)
            }
            GLES30.glDrawArraysInstanced(GLES30.GL_TRIANGLE_STRIP, 0, 4, count)
        }
        GLES30.glBindVertexArray(0)
        GLES30.glBlendEquation(GLES30.GL_FUNC_ADD)
        GLES30.glDisable(GLES30.GL_BLEND)
        GlUtil.checkError("MaskPass")
    }

    /** Stamps every quarter radius along the stroke. Distances in aspect space so spacing is even. */
    private fun fillStamps(stroke: MaskStroke, aspect: Float): Int {
        val pts = stroke.points
        if (pts.isEmpty()) return 0
        val r = stroke.radius
        val spacing = max(r * 0.25f, 0.001f)
        stamps.clear()
        var count = 0
        fun put(x: Float, y: Float) {
            if (stamps.remaining() < 3) stamps = grow(stamps)
            stamps.put(x).put(y).put(r)
            count++
        }
        put(pts[0].x, pts[0].y)
        for (i in 1 until pts.size) {
            val a = pts[i - 1]
            val b = pts[i]
            // Width-relative units: dx as is, dy scaled by height/width.
            val len = hypot(b.x - a.x, (b.y - a.y) / aspect)
            val steps = (len / spacing).roundToInt()
            for (s in 1..steps) {
                val t = s.toFloat() / steps
                put(a.x + (b.x - a.x) * t, a.y + (b.y - a.y) * t)
            }
            if (steps == 0) put(b.x, b.y)
        }
        stamps.flip()
        return count
    }

    private fun upload(count: Int) {
        GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, stampVbo[0])
        val bytes = count * 12
        if (bytes > stampCapacity) {
            stampCapacity = bytes * 2
            GLES30.glBufferData(GLES30.GL_ARRAY_BUFFER, stampCapacity, null, GLES30.GL_DYNAMIC_DRAW)
        }
        GLES30.glBufferSubData(GLES30.GL_ARRAY_BUFFER, 0, bytes, stamps)
    }

    private fun ensureTarget(aspect: Float): GlTexture {
        val w: Int
        val h: Int
        if (aspect >= 1f) {
            w = LONG_EDGE
            h = max(1, (LONG_EDGE / aspect).roundToInt())
        } else {
            w = max(1, (LONG_EDGE * aspect).roundToInt())
            h = LONG_EDGE
        }
        texture?.let { if (it.width == w && it.height == h) return it else it.release() }
        return GlTexture.empty(w, h, GLES30.GL_R8, GLES30.GL_RED, GLES30.GL_UNSIGNED_BYTE).also { texture = it }
    }

    override fun release() {
        texture?.release()
        texture = null
        GLES30.glDeleteBuffers(2, intArrayOf(cornerVbo[0], stampVbo[0]), 0)
        GLES30.glDeleteVertexArrays(1, vao, 0)
        program.release()
    }

    private companion object {
        const val LONG_EDGE = 1024

        fun allocate(floats: Int): FloatBuffer =
            ByteBuffer.allocateDirect(floats * 4).order(ByteOrder.nativeOrder()).asFloatBuffer()

        fun grow(old: FloatBuffer): FloatBuffer {
            val bigger = allocate(old.capacity() * 2)
            old.flip()
            bigger.put(old)
            return bigger
        }
    }
}
