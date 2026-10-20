package com.motionloop.app.render.editor

import android.content.Context
import android.content.res.AssetManager
import android.opengl.GLES30
import android.opengl.GLSurfaceView
import com.motionloop.app.domain.model.LoopSettings
import com.motionloop.app.render.LoopRenderGraph
import com.motionloop.app.render.RenderScene
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10

/** Runs on the GLSurfaceView render thread. The UI thread only swaps in new [RenderScene]s. */
class EditorRenderer(private val assets: AssetManager) : GLSurfaceView.Renderer {

    @Volatile
    var scene: RenderScene = RenderScene()

    private var graph: LoopRenderGraph? = null
    private var width = 1
    private var height = 1
    private var phase = 0f
    private var lastFrameNanos = 0L

    override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
        // A new context means every old GL object is gone. Do not release, just drop.
        graph = LoopRenderGraph(assets)
        lastFrameNanos = 0L
    }

    override fun onSurfaceChanged(gl: GL10?, w: Int, h: Int) {
        width = w
        height = h
    }

    override fun onDrawFrame(gl: GL10?) {
        val g = graph ?: return
        val s = scene
        GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, 0)
        GLES30.glViewport(0, 0, width, height)
        GLES30.glClearColor(0f, 0f, 0f, 1f)
        GLES30.glClear(GLES30.GL_COLOR_BUFFER_BIT)

        val image = s.image ?: return
        g.setImage(image)
        g.setGeometry(s.arrows, s.anchors, s.strokes)
        g.setColor(s.adjustments, s.lut)

        // Advance phase by elapsed time so speed changes never make the loop jump.
        val now = System.nanoTime()
        if (s.playing && lastFrameNanos != 0L) {
            val dt = ((now - lastFrameNanos) / 1e9f).coerceAtMost(0.1f)
            phase = (phase + dt * s.loop.speed / LoopSettings.BASE_CYCLE_SECONDS) % 1000f
        }
        lastFrameNanos = if (s.playing) now else 0L

        g.draw(
            fbo = 0, x = 0, y = 0, width = width, height = height,
            phase = phase,
            mode = s.loop.mode,
            amount = if (s.playing) 1f else 0f,
            maskOverlay = if (s.showMask) 1f else 0f,
            flipY = true,
        )
    }
}

/** GLSurfaceView sized by Compose to exactly the photo aspect, so no letterboxing math here. */
class LoopSurfaceView(context: Context) : GLSurfaceView(context) {
    private val renderer = EditorRenderer(context.assets)

    init {
        setEGLContextClientVersion(3)
        setEGLConfigChooser(8, 8, 8, 8, 0, 0)
        preserveEGLContextOnPause = true
        setRenderer(renderer)
        renderMode = RENDERMODE_WHEN_DIRTY
    }

    fun setScene(scene: RenderScene) {
        renderer.scene = scene
        val mode = if (scene.playing) RENDERMODE_CONTINUOUSLY else RENDERMODE_WHEN_DIRTY
        if (renderMode != mode) renderMode = mode
        requestRender()
    }
}
