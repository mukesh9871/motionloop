package com.motionloop.app.feature.wallpaper

import android.os.Handler
import android.os.HandlerThread
import android.os.SystemClock
import android.util.Log
import android.view.Choreographer
import android.view.SurfaceHolder
import android.service.wallpaper.WallpaperService
import com.motionloop.app.core.gl.EglCore
import com.motionloop.app.core.gl.GlUtil
import com.motionloop.app.data.settings.AppSettings
import com.motionloop.app.domain.model.LoopSettings
import com.motionloop.app.domain.model.Project
import com.motionloop.app.domain.repository.LutRepository
import com.motionloop.app.domain.repository.ProjectAssetRepository
import com.motionloop.app.domain.repository.ProjectRepository
import com.motionloop.app.export.applyParallax
import com.motionloop.app.export.applySky
import com.motionloop.app.export.applySubject
import com.motionloop.app.export.loadSelections
import com.motionloop.app.export.renderTexts
import com.motionloop.app.render.LoopRenderGraph
import com.motionloop.app.render.passes.TextDraw
import dagger.hilt.android.AndroidEntryPoint
import android.opengl.GLES30
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.android.asCoroutineDispatcher
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import javax.inject.Inject
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Plays a saved project as a live wallpaper (11b). Each engine owns a render thread with its own
 * EGL window context and the same render graph as export, so the wallpaper matches the export.
 * Frames are only posted while the wallpaper is visible, which keeps it off the battery otherwise.
 */
@AndroidEntryPoint
class LoopWallpaperService : WallpaperService() {

    @Inject lateinit var settings: AppSettings
    @Inject lateinit var projects: ProjectRepository
    @Inject lateinit var assets: ProjectAssetRepository
    @Inject lateinit var luts: LutRepository

    override fun onCreateEngine(): Engine = LoopEngine()

    private inner class LoopEngine : Engine(), Choreographer.FrameCallback {
        private val thread = HandlerThread("ml-wallpaper").apply { start() }
        private val handler = Handler(thread.looper)
        private val scope = CoroutineScope(SupervisorJob() + handler.asCoroutineDispatcher())

        // Touched only on the render thread.
        private var egl: EglCore? = null
        private var graph: LoopRenderGraph? = null
        private var project: Project? = null
        private var texts: List<TextDraw> = emptyList()
        private var width = 0
        private var height = 0
        private var visible = false
        private var phase = 0f
        private var lastFrameNanos = 0L
        private var lastDrawNanos = 0L

        override fun onSurfaceCreated(holder: SurfaceHolder) {
            scope.launch {
                try {
                    val e = EglCore(EglCore.Kind.WINDOW).also { egl = it }
                    e.makeCurrentWindow(holder.surface)
                    load()
                } catch (t: Exception) {
                    Log.e(TAG, "Wallpaper setup failed", t)
                }
            }
        }

        override fun onSurfaceChanged(holder: SurfaceHolder, format: Int, w: Int, h: Int) {
            handler.post {
                width = w
                height = h
                drawFrame()
            }
        }

        override fun onVisibilityChanged(isVisible: Boolean) {
            handler.post {
                visible = isVisible
                lastFrameNanos = 0L
                val choreographer = Choreographer.getInstance()
                choreographer.removeFrameCallback(this)
                if (isVisible) choreographer.postFrameCallback(this)
            }
        }

        override fun onSurfaceDestroyed(holder: SurfaceHolder) {
            // The surface must be released before this returns, so wait for the render thread.
            val done = java.util.concurrent.CountDownLatch(1)
            handler.post {
                Choreographer.getInstance().removeFrameCallback(this)
                releaseGl()
                done.countDown()
            }
            done.await(2, java.util.concurrent.TimeUnit.SECONDS)
        }

        override fun onDestroy() {
            scope.cancel()
            handler.post {
                releaseGl()
                thread.quitSafely()
            }
        }

        override fun doFrame(frameTimeNanos: Long) {
            if (!visible) return
            // About 30 fps is plenty for slow loops and halves the power cost on 60 Hz screens.
            if (frameTimeNanos - lastDrawNanos >= FRAME_INTERVAL_NANOS) {
                lastDrawNanos = frameTimeNanos
                drawFrame()
            }
            Choreographer.getInstance().postFrameCallback(this)
        }

        /** Loads the chosen project, the same way an export does. Runs on the render thread. */
        private suspend fun load() {
            val id = settings.state.value.wallpaperProjectId ?: return
            val p = projects.load(id) ?: return
            val bitmap = projects.loadImage(id, minOf(WALLPAPER_EDGE, GlUtil.maxTextureSize()))
            val g = LoopRenderGraph(applicationContext.assets).also { graph = it }
            g.setImage(bitmap)
            g.setGeometry(p.arrows, p.anchors, p.strokes, loadSelections(p, assets))
            g.setColor(p.adjustments, luts.resolve(p.id, p.adjustments))
            g.setOverlay(p.overlay)
            applySky(g, p, assets, WALLPAPER_EDGE)
            applyParallax(g, p, assets)
            applySubject(g, p, assets, WALLPAPER_EDGE)
            texts = renderTexts(p, bitmap.width)
            g.setTexts(texts)
            bitmap.recycle()
            project = p
            drawFrame()
        }

        private fun drawFrame() {
            val e = egl ?: return
            if (width == 0 || height == 0) return
            val g = graph
            val p = project
            if (g == null || p == null) {
                // No project chosen yet: a calm dark screen instead of garbage.
                GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, 0)
                GLES30.glViewport(0, 0, width, height)
                GLES30.glClearColor(0.055f, 0.06f, 0.1f, 1f)
                GLES30.glClear(GLES30.GL_COLOR_BUFFER_BIT)
                e.swapBuffers()
                return
            }
            val now = SystemClock.elapsedRealtimeNanos()
            if (lastFrameNanos != 0L) {
                val dt = ((now - lastFrameNanos) / 1e9f).coerceAtMost(0.1f)
                phase = (phase + dt * p.loop.speed / LoopSettings.BASE_CYCLE_SECONDS) % 1000f
            }
            lastFrameNanos = now

            // Center-crop the photo to the screen: a viewport larger than the screen, centered.
            val scale = max(width.toFloat() / p.imageWidth, height.toFloat() / p.imageHeight)
            val vw = (p.imageWidth * scale).roundToInt()
            val vh = (p.imageHeight * scale).roundToInt()
            GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, 0)
            GLES30.glViewport(0, 0, width, height)
            GLES30.glClearColor(0f, 0f, 0f, 1f)
            GLES30.glClear(GLES30.GL_COLOR_BUFFER_BIT)
            g.draw(
                fbo = 0, x = (width - vw) / 2, y = (height - vh) / 2, width = vw, height = vh,
                phase = phase, mode = p.loop.mode, amount = 1f, maskOverlay = 0f, flipY = true,
            )
            e.swapBuffers()
        }

        private fun releaseGl() {
            graph?.release()
            graph = null
            texts.forEach { it.rendered.recycle() }
            texts = emptyList()
            egl?.release()
            egl = null
            project = null
        }
    }

    private companion object {
        const val TAG = "LoopWallpaper"
        const val WALLPAPER_EDGE = 2048
        const val FRAME_INTERVAL_NANOS = 30_000_000L
    }
}
