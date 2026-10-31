package com.motionloop.app.render.editor

import android.content.Context
import android.content.res.AssetManager
import android.opengl.GLES30
import android.opengl.GLSurfaceView
import android.os.Build
import android.os.PowerManager
import android.view.Choreographer
import android.view.Surface
import android.view.SurfaceHolder
import com.motionloop.app.domain.audio.BeatDetector
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
    // Time within the preview sound loop, kept in step with the audio by advancing only while playing.
    private var audioTime = 0f

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
        g.setGeometry(s.arrows, s.anchors, s.strokes, s.selections)
        g.setColor(s.adjustments, s.lut)
        g.setOverlay(s.overlay)
        g.setEffects(s.effects)
        g.setSky(s.sky, s.skyMask, s.skyPhoto, s.skyReplace)
        g.setParallax(s.parallax, s.depth)
        g.setSubject(s.subject, s.subjectMask, s.background, s.backgroundPhoto, s.subjectTintOnly, checker = true)
        g.setTexts(s.texts)

        // Advance phase by elapsed time so speed changes never make the loop jump.
        val now = System.nanoTime()
        if (s.playing && lastFrameNanos != 0L) {
            val dt = ((now - lastFrameNanos) / 1e9f).coerceAtMost(0.1f)
            val env = s.beatEnvelope
            val pulse = if (env != null && env.isNotEmpty()) {
                // The audible position when the sound plays, so pulses land on the beats you hear.
                audioTime = s.audioClock?.seconds() ?: ((audioTime + dt) % (env.size.toFloat() / BeatDetector.ENVELOPE_RATE))
                env[(audioTime * BeatDetector.ENVELOPE_RATE).toInt().coerceIn(0, env.lastIndex)]
            } else {
                0f
            }
            // Same speed rule as the beat-synced export: up to 3x on a strong beat.
            val speed = s.loop.speed * (1f + 2f * s.beatAmount * pulse)
            // Every animated term has period 1, so wrap at 1: large phases lose float precision in the shaders.
            phase = (phase + dt * speed / LoopSettings.BASE_CYCLE_SECONDS) % 1f
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

/**
 * GLSurfaceView sized by Compose to exactly the photo aspect, so no letterboxing math here.
 *
 * Playback is paced here rather than with RENDERMODE_CONTINUOUSLY, which draws on every vsync:
 * 120 times a second on many phones, for loops that are slow by nature. Frames are requested at
 * about 60 fps (30 in battery saver), and a paused preview only draws when its scene changes.
 */
class LoopSurfaceView(context: Context) : GLSurfaceView(context), Choreographer.FrameCallback {
    private val renderer = EditorRenderer(context.assets)
    private val power: PowerManager? = context.getSystemService(PowerManager::class.java)

    // Main thread only.
    private var attached = false
    private var paused = false
    private var ticking = false
    private var interval = FRAME_INTERVAL_NANOS
    private var nextFrameNanos = 0L

    init {
        setEGLContextClientVersion(3)
        setEGLConfigChooser(8, 8, 8, 8, 0, 0)
        preserveEGLContextOnPause = true
        setRenderer(renderer)
        renderMode = RENDERMODE_WHEN_DIRTY
    }

    fun setScene(scene: RenderScene) {
        val old = renderer.scene
        renderer.scene = scene
        updateTicking()
        // Chrome changes (busy badge, messages, brush size) recompose with an equal scene: no new frame.
        if (scene != old) requestRender()
    }

    override fun doFrame(frameTimeNanos: Long) {
        if (!ticking) return
        // A little slack: on a 90 Hz panel a vsync lands just before the due time.
        if (frameTimeNanos >= nextFrameNanos - SLACK_NANOS) {
            nextFrameNanos += interval
            // After a stall, start again from now instead of drawing a burst of late frames.
            if (nextFrameNanos < frameTimeNanos) nextFrameNanos = frameTimeNanos + interval
            requestRender()
        }
        Choreographer.getInstance().postFrameCallback(this)
    }

    override fun surfaceCreated(holder: SurfaceHolder) {
        super.surfaceCreated(holder)
        // Only a hint: lets a high refresh panel run slower while this preview is what moves.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            runCatching { holder.surface.setFrameRate(TARGET_FPS, Surface.FRAME_RATE_COMPATIBILITY_DEFAULT) }
        }
    }

    override fun onResume() {
        super.onResume()
        paused = false
        updateTicking()
    }

    override fun onPause() {
        paused = true
        updateTicking()
        super.onPause()
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        attached = true
        updateTicking()
    }

    override fun onDetachedFromWindow() {
        attached = false
        updateTicking()
        super.onDetachedFromWindow()
    }

    private fun updateTicking() {
        val should = attached && !paused && renderer.scene.playing
        if (should == ticking) return
        ticking = should
        val choreographer = Choreographer.getInstance()
        if (should) {
            // Read once per start, not per frame: it is a call into the system.
            interval = if (power?.isPowerSaveMode == true) SAVER_INTERVAL_NANOS else FRAME_INTERVAL_NANOS
            nextFrameNanos = 0L
            choreographer.postFrameCallback(this)
        } else {
            choreographer.removeFrameCallback(this)
        }
    }

    private companion object {
        const val TARGET_FPS = 60f
        const val FRAME_INTERVAL_NANOS = 16_666_667L
        const val SAVER_INTERVAL_NANOS = 33_333_333L
        const val SLACK_NANOS = 2_000_000L
    }
}
