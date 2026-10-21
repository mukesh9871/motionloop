package com.motionloop.app.feature.wallpaper

import android.graphics.Bitmap
import android.opengl.GLES30
import android.os.Handler
import android.os.HandlerThread
import android.os.SystemClock
import android.service.wallpaper.WallpaperService
import android.util.Log
import android.view.Choreographer
import android.view.SurfaceHolder
import com.motionloop.app.core.gl.EglCore
import com.motionloop.app.core.gl.GlTexture
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
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.android.asCoroutineDispatcher
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Plays a saved project as a live wallpaper (11b). Each engine owns a render thread with its own
 * EGL window context and the same render graph as export, so the wallpaper matches the export.
 * The graph renders once per frame at the photo's loaded size (at most [WALLPAPER_EDGE]), then only
 * the part the screen shows is copied to the window. Frames are only posted while visible.
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
        private var frame: GlTexture? = null
        private var project: Project? = null
        private var texts: List<TextDraw> = emptyList()
        private var loadJob: Job? = null
        private var width = 0
        private var height = 0
        private var visible = false
        private var posting = false
        private var phase = 0f
        private var lastFrameNanos = 0L
        private var lastDrawNanos = 0L

        init {
            // Choosing another project while this wallpaper runs: reload without a new surface.
            scope.launch {
                // The revision also reloads the same project after its edits were saved.
                settings.state.map { it.wallpaperProjectId to it.wallpaperRevision }.distinctUntilChanged().drop(1).collect {
                    if (egl != null) reload()
                }
            }
        }

        override fun onSurfaceCreated(holder: SurfaceHolder) {
            scope.launch {
                try {
                    val e = EglCore(EglCore.Kind.WINDOW).also { egl = it }
                    e.makeCurrentWindow(holder.surface)
                    reload()
                } catch (t: Exception) {
                    Log.e(TAG, "Wallpaper setup failed", t)
                }
            }
        }

        override fun onSurfaceChanged(holder: SurfaceHolder, format: Int, w: Int, h: Int) {
            handler.post {
                width = w
                height = h
                safeDraw()
            }
        }

        override fun onVisibilityChanged(isVisible: Boolean) {
            handler.post {
                visible = isVisible
                lastFrameNanos = 0L
                updatePosting()
            }
        }

        override fun onSurfaceDestroyed(holder: SurfaceHolder) {
            // The surface must be released before this returns, so wait for the render thread.
            val done = CountDownLatch(1)
            handler.post {
                loadJob?.cancel()
                loadJob = null
                stopPosting()
                releaseGl()
                done.countDown()
            }
            done.await(2, TimeUnit.SECONDS)
        }

        override fun onDestroy() {
            scope.cancel()
            handler.post {
                stopPosting()
                releaseGl()
                thread.quitSafely()
            }
        }

        override fun doFrame(frameTimeNanos: Long) {
            if (!posting) return
            // About 30 fps is plenty for slow loops and halves the power cost on 60 Hz screens.
            if (frameTimeNanos - lastDrawNanos >= FRAME_INTERVAL_NANOS) {
                lastDrawNanos = frameTimeNanos
                safeDraw()
            }
            if (posting) Choreographer.getInstance().postFrameCallback(this)
        }

        /** Animate only while visible and something animates. A missing project is drawn once, dark. */
        private fun updatePosting() {
            val shouldPost = visible && graph != null
            if (shouldPost && !posting) {
                posting = true
                Choreographer.getInstance().postFrameCallback(this)
            } else if (!shouldPost) {
                stopPosting()
                if (visible) safeDraw()
            }
        }

        private fun stopPosting() {
            posting = false
            Choreographer.getInstance().removeFrameCallback(this)
        }

        /** Drops the current scene (keeping the EGL context) and loads the chosen project again. */
        private fun reload() {
            loadJob?.cancel()
            releaseScene()
            val e = egl ?: return
            loadJob = scope.launch {
                try {
                    load(e)
                } catch (c: CancellationException) {
                    throw c
                } catch (t: Exception) {
                    Log.e(TAG, "Wallpaper load failed", t)
                    releaseScene()
                }
                updatePosting()
                safeDraw()
            }
        }

        /** Loads the chosen project, the same way an export does. Runs on the render thread. */
        private suspend fun load(e: EglCore) {
            val id = settings.state.value.wallpaperProjectId ?: return
            val p = projects.load(id) ?: return
            val bitmap = projects.loadImage(id, minOf(WALLPAPER_EDGE, GlUtil.maxTextureSize()))
            // setImage uploads at once, so the bitmap is freed on every exit, cancellation included.
            try {
                loadScene(e, p, bitmap)
            } finally {
                bitmap.recycle()
            }
        }

        private suspend fun loadScene(e: EglCore, p: Project, bitmap: Bitmap) {
            // Each suspension before this may have outlived the context (surface destroyed meanwhile).
            if (egl !== e || !currentCoroutineContext().isActive) return
            val g = LoopRenderGraph(applicationContext.assets).also { graph = it }
            g.setImage(bitmap)
            g.setGeometry(p.arrows, p.anchors, p.strokes, loadSelections(p, assets))
            g.setColor(p.adjustments, luts.resolve(p.id, p.adjustments))
            g.setOverlay(p.overlay)
            applySky(g, p, assets, WALLPAPER_EDGE)
            // A missing depth or subject file should cost that feature, not the whole wallpaper.
            // Cancellation must still stop the load: a reload keeps the same context, so the egl check misses it.
            try {
                applyParallax(g, p, assets)
            } catch (c: CancellationException) {
                throw c
            } catch (t: Exception) {
                Log.w(TAG, "3D skipped", t)
            }
            try {
                applySubject(g, p, assets, WALLPAPER_EDGE)
            } catch (c: CancellationException) {
                throw c
            } catch (t: Exception) {
                Log.w(TAG, "Cutout skipped", t)
            }
            if (egl !== e || !currentCoroutineContext().isActive) return
            texts = renderTexts(p, bitmap.width)
            g.setTexts(texts)
            frame = GlTexture.empty(bitmap.width, bitmap.height, GLES30.GL_RGBA8, GLES30.GL_RGBA, GLES30.GL_UNSIGNED_BYTE)
            project = p
        }

        /** Draws one frame. Any GL failure stops the wallpaper here instead of crashing the app process. */
        private fun safeDraw() {
            try {
                drawFrame()
            } catch (t: Exception) {
                Log.e(TAG, "Wallpaper frame failed", t)
                stopPosting()
                releaseScene()
            }
        }

        private fun drawFrame() {
            val e = egl ?: return
            if (width == 0 || height == 0) return
            val g = graph
            val p = project
            val f = frame
            if (g == null || p == null || f == null) {
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

            // Render at the photo's loaded size (row 0 = image top), never larger than the photo.
            g.draw(
                fbo = f.framebuffer(), x = 0, y = 0, width = f.width, height = f.height,
                phase = phase, mode = p.loop.mode, amount = 1f, maskOverlay = 0f, flipY = false,
            )
            // Copy only the centered region the screen shows, scaled to the screen and flipped upright.
            val scale = max(width.toFloat() / f.width, height.toFloat() / f.height)
            val cropW = (width / scale).roundToInt().coerceIn(1, f.width)
            val cropH = (height / scale).roundToInt().coerceIn(1, f.height)
            val x0 = (f.width - cropW) / 2
            val y0 = (f.height - cropH) / 2
            GLES30.glBindFramebuffer(GLES30.GL_READ_FRAMEBUFFER, f.framebuffer())
            GLES30.glBindFramebuffer(GLES30.GL_DRAW_FRAMEBUFFER, 0)
            GLES30.glBlitFramebuffer(
                x0, y0, x0 + cropW, y0 + cropH,
                0, height, width, 0,
                GLES30.GL_COLOR_BUFFER_BIT, GLES30.GL_LINEAR,
            )
            GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, 0)
            e.swapBuffers()
        }

        private fun releaseScene() {
            graph?.release()
            graph = null
            frame?.release()
            frame = null
            texts.forEach { it.rendered.recycle() }
            texts = emptyList()
            project = null
        }

        private fun releaseGl() {
            releaseScene()
            egl?.release()
            egl = null
        }
    }

    private companion object {
        const val TAG = "LoopWallpaper"
        const val WALLPAPER_EDGE = 2048
        const val FRAME_INTERVAL_NANOS = 30_000_000L
    }
}
