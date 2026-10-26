package com.motionloop.app.render.tiles

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.opengl.GLES30
import android.util.Log
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import com.motionloop.app.BuildConfig
import com.motionloop.app.core.common.DefaultDispatcher
import com.motionloop.app.core.common.IoDispatcher
import com.motionloop.app.core.gl.EglCore
import com.motionloop.app.core.gl.GlTexture
import com.motionloop.app.core.gl.GlUtil
import com.motionloop.app.core.image.BitmapDecoder
import com.motionloop.app.data.writeAtomically
import com.motionloop.app.domain.model.Adjustments
import com.motionloop.app.domain.model.BackgroundMode
import com.motionloop.app.domain.model.BackgroundSettings
import com.motionloop.app.domain.model.ColorGrades
import com.motionloop.app.domain.model.DispersionSettings
import com.motionloop.app.domain.model.EffectsSettings
import com.motionloop.app.domain.model.LoopMode
import com.motionloop.app.domain.model.LutData
import com.motionloop.app.domain.model.MaskCodec
import com.motionloop.app.domain.model.MaskMode
import com.motionloop.app.domain.model.MaskStroke
import com.motionloop.app.domain.model.MotionArrow
import com.motionloop.app.domain.model.NPoint
import com.motionloop.app.domain.model.OverlaySettings
import com.motionloop.app.domain.model.ParallaxSettings
import com.motionloop.app.domain.model.ParticleType
import com.motionloop.app.domain.model.RippleSettings
import com.motionloop.app.domain.model.SelectionMode
import com.motionloop.app.domain.model.SkySettings
import com.motionloop.app.domain.model.SkyStyle
import com.motionloop.app.domain.model.Sticker
import com.motionloop.app.domain.model.SubjectSettings
import com.motionloop.app.domain.model.TextLayer
import com.motionloop.app.render.LoopRenderGraph
import com.motionloop.app.render.SelectionLayer
import com.motionloop.app.render.passes.TextDraw
import com.motionloop.app.render.text.TextRasterizer
import com.motionloop.ml.AlphaMask
import com.motionloop.ml.DepthEstimator
import com.motionloop.ml.Segmenter
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.max
import kotlin.math.roundToInt

/** A rendered tile: a still, and the loop as frames once someone asked for it to play. */
class TileImage(val still: ImageBitmap, val frames: List<ImageBitmap>? = null)

/**
 * Renders effects on the reference photo with the real render graph, so every card and Help picture
 * shows exactly what the effect does, on the same photo.
 *
 * Work runs on one long-lived thread that owns its own EGL context (released after a quiet spell).
 * Stills are cached on disk per app version, loops only in memory. The masks the effects need (sky,
 * subject, depth, water) are computed once by the on-device models and kept in app storage.
 */
@Singleton
class TileRenderer @Inject constructor(
    @ApplicationContext private val context: Context,
    private val segmenter: Segmenter,
    private val depthEstimator: DepthEstimator,
    @DefaultDispatcher private val cpu: CoroutineDispatcher,
    @IoDispatcher private val io: CoroutineDispatcher,
) {
    private val gl = Executors.newSingleThreadExecutor { Thread(it, "ml-tiles") }.asCoroutineDispatcher()
    private val scope = CoroutineScope(SupervisorJob() + gl)
    private val queue = Channel<Job>(Channel.UNLIMITED)
    private val queued = ConcurrentHashMap.newKeySet<String>()

    /** How many tiles on screen currently play each loop. Loops still in use are never evicted. */
    private val inUse = ConcurrentHashMap<TileRequest, Int>()

    private val _images = MutableStateFlow<Map<TileRequest, TileImage>>(emptyMap())
    val images: StateFlow<Map<TileRequest, TileImage>> = _images.asStateFlow()

    private class Job(val request: TileRequest, val animated: Boolean)

    // Owned by the gl thread only.
    private var egl: EglCore? = null
    private var graph: LoopRenderGraph? = null
    private val targets = HashMap<TileShape, GlTexture>()
    private var refs: References? = null
    private var unavailable = false
    private val luts = HashMap<String, LutData>()
    private val texts = HashMap<TextLayer, TextDraw>()
    private val animatedOrder = ArrayDeque<TileRequest>()

    private class References(
        val photo: Bitmap,
        val sky: AlphaMask?,
        val subject: AlphaMask?,
        val depth: AlphaMask?,
        val water: AlphaMask?,
        /** Subject bounds in normalized photo coordinates: left, top, right, bottom. */
        val subjectBox: FloatArray?,
    )

    init {
        scope.launch { work() }
    }

    /** Asks for [request]. With [animated], the loop frames are rendered too. Safe from any thread. */
    fun request(request: TileRequest, animated: Boolean) {
        val have = _images.value[request]
        if (have != null && (!animated || have.frames != null)) return
        if (queued.add(tag(request, animated))) queue.trySend(Job(request, animated))
    }

    /** A tile started playing [request]: its frames stay while it plays. */
    fun retain(request: TileRequest) {
        inUse.merge(request, 1, Int::plus)
    }

    /** The tile stopped playing [request] (deselected or left the screen). */
    fun releaseLoop(request: TileRequest) {
        inUse.computeIfPresent(request) { _, n -> if (n <= 1) null else n - 1 }
    }

    private fun tag(r: TileRequest, animated: Boolean) = "${r.shape}_${r.key.id}_$animated"

    private suspend fun work() {
        while (true) {
            // Nothing asked for a while: free the GL context and the photo until the next request.
            val job = withTimeoutOrNull(IDLE_MS) { queue.receive() }
            if (job == null) {
                releaseGl()
                val next = queue.receive()
                process(next)
            } else {
                process(job)
            }
        }
    }

    private suspend fun process(job: Job) {
        try {
            val r = job.request
            if (_images.value[r] == null) {
                val still = loadCached(r) ?: render(r, listOf(STILL_PHASE))?.single()?.also { saveCached(r, it) }
                if (still != null) _images.update { it + (r to TileImage(still.asImageBitmap())) }
            }
            val have = _images.value[r]
            if (job.animated && have != null && have.frames == null) {
                val phases = List(FRAMES) { it.toFloat() / FRAMES }
                val frames = render(r, phases)?.map { it.asImageBitmap() }
                if (frames != null) {
                    keepFramesFor(r)
                    _images.update { it + (r to TileImage(have.still, frames)) }
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // A tile that cannot be rendered keeps its drawn picture: never worth a crash.
            Log.w(TAG, "Tile ${job.request.key.id} failed", e)
        } finally {
            queued.remove(tag(job.request, job.animated))
        }
    }

    /**
     * Loops take memory: past [MAX_ANIMATED], the oldest loop no tile is playing gives up its frames.
     * Loops still playing are kept even past the limit (they are on screen, so there are few).
     */
    private fun keepFramesFor(r: TileRequest) {
        animatedOrder.remove(r)
        animatedOrder.addLast(r)
        while (animatedOrder.size > MAX_ANIMATED) {
            val old = animatedOrder.firstOrNull { (inUse[it] ?: 0) == 0 } ?: break
            animatedOrder.remove(old)
            _images.update { map -> map[old]?.let { map + (old to TileImage(it.still)) } ?: map }
        }
    }

    // region rendering

    private suspend fun render(r: TileRequest, phases: List<Float>): List<Bitmap>? {
        val refs = references() ?: return null
        val g = ensureGraph(refs) ?: return null
        if (!configure(g, r.key, refs)) return null
        val shape = r.shape
        val target = targets.getOrPut(shape) {
            GlTexture.empty(shape.width, shape.height, GLES30.GL_RGBA8, GLES30.GL_RGBA, GLES30.GL_UNSIGNED_BYTE)
        }
        val viewport = viewportFor(refs.photo, shape)
        val buffer = ByteBuffer.allocateDirect(shape.width * shape.height * 4).order(ByteOrder.nativeOrder())
        return phases.map { phase ->
            g.draw(
                fbo = target.framebuffer(), x = viewport[0], y = viewport[1], width = viewport[2], height = viewport[3],
                phase = phase,
                mode = LoopMode.CONTINUOUS,
                amount = 1f,
                maskOverlay = 0f,
                // FBO row 0 is the image top, the row order a Bitmap expects.
                flipY = false,
            )
            buffer.clear()
            GLES30.glReadPixels(0, 0, shape.width, shape.height, GLES30.GL_RGBA, GLES30.GL_UNSIGNED_BYTE, buffer)
            GlUtil.checkError("tile readback")
            buffer.rewind()
            Bitmap.createBitmap(shape.width, shape.height, Bitmap.Config.ARGB_8888).apply { copyPixelsFromBuffer(buffer) }
        }
    }

    /** Cover crop of the photo, centered on the person (right of center) for square tiles. */
    private fun viewportFor(photo: Bitmap, shape: TileShape): IntArray {
        val w = shape.width
        val h = shape.height
        val scale = maxOf(w.toFloat() / photo.width, h.toFloat() / photo.height)
        val vw = max(w, (photo.width * scale).roundToInt())
        val vh = max(h, (photo.height * scale).roundToInt())
        val x = (w / 2f - FOCUS_X * vw).roundToInt().coerceIn(w - vw, 0)
        val y = (h / 2f - FOCUS_Y * vh).roundToInt().coerceIn(h - vh, 0)
        return intArrayOf(x, y, vw, vh)
    }

    /** Sets every input of the graph for [key]. False when the key cannot be shown (no photo style, missing mask). */
    private suspend fun configure(g: LoopRenderGraph, key: TileKey, refs: References): Boolean {
        var arrows = emptyList<MotionArrow>()
        var selections = emptyList<SelectionLayer>()
        var adjustments = Adjustments()
        var lut: LutData? = null
        var overlay = OverlaySettings()
        var effects = EffectsSettings()
        var sky = SkySettings()
        var parallax = ParallaxSettings()
        var subject = SubjectSettings()
        var background = BackgroundSettings()
        var tint = false
        var textDraws = emptyList<TextDraw>()

        when (key) {
            is TileKey.Sky -> {
                if (key.style == SkyStyle.PHOTO || refs.sky == null) return false
                sky = SkySettings(enabled = true, style = key.style, maskRev = REF_REV, drift = 0.6f)
            }
            is TileKey.Background -> {
                if (key.mode == BackgroundMode.PHOTO) return false
                if (key.mode != BackgroundMode.NONE) {
                    if (refs.subject == null) return false
                    subject = SubjectSettings(maskRev = REF_REV)
                    background = BackgroundSettings(mode = key.mode)
                }
            }
            is TileKey.Camera -> {
                if (refs.depth == null) return false
                parallax = ParallaxSettings(enabled = true, path = key.path, amount = 1f, focus = 0.35f, depthRev = REF_REV)
            }
            is TileKey.Particles -> if (key.type != ParticleType.NONE) overlay = OverlaySettings(particles = key.type, density = 0.55f, size = 0.6f)
            is TileKey.Leak -> overlay = OverlaySettings(lightLeak = key.leak, leakIntensity = 0.85f)
            is TileKey.Air -> overlay = OverlaySettings(atmosphere = key.atmosphere, atmosphereAmount = 0.8f)
            is TileKey.Sticker -> effects = EffectsSettings(stickers = listOf(Sticker("tile", key.kind, x = FOCUS_X, y = 0.36f, size = 0.3f)))
            is TileKey.Text -> {
                if (refs.subject != null) subject = SubjectSettings(maskRev = REF_REV)
                val layer = TextLayer("tile", "LOOP", size = 0.2f, x = FOCUS_X, y = 0.42f, animation = key.animation, shadow = true, behindSubject = true)
                val draw = texts.getOrPut(layer.renderKey()) { withContext(cpu) { TextDraw(layer, TextRasterizer.render(layer, TEXT_WIDTH)) } }
                textDraws = listOf(TextDraw(layer, draw.rendered))
            }
            is TileKey.Grade -> {
                val id = key.gradeId
                if (id != null) {
                    val grade = ColorGrades.byId(id) ?: return false
                    lut = luts.getOrPut(id) { withContext(cpu) { LutData.bake(transform = grade.transform) } }
                    adjustments = Adjustments(gradeId = id)
                }
            }
            is TileKey.Adjust -> adjustments = key.preset.applyTo(Adjustments())
            TileKey.Photo -> Unit
            TileKey.Flow -> arrows = listOf(
                MotionArrow("f1", NPoint(0.05f, 0.5f), NPoint(0.4f, 0.52f)),
                MotionArrow("f2", NPoint(0.05f, 0.75f), NPoint(0.35f, 0.8f)),
            )
            TileKey.Water -> {
                val water = refs.water ?: return false
                selections = listOf(SelectionLayer(water, SelectionMode.RIPPLE))
                effects = EffectsSettings(ripple = RippleSettings(strength = 1f))
            }
            TileKey.Disperse -> {
                val box = refs.subjectBox ?: return false
                // A brush stroke down the middle of the person, as wide as they are.
                val cx = (box[0] + box[2]) / 2
                val stroke = MaskStroke(
                    points = List(6) { i -> NPoint(cx, box[1] + (box[3] - box[1]) * i / 5f) },
                    radius = ((box[2] - box[0]) * 0.6f).coerceAtLeast(0.03f),
                    hardness = 0.8f,
                    mode = MaskMode.FREEZE,
                )
                effects = EffectsSettings(dispersion = DispersionSettings(strokes = listOf(stroke), angle = -20f, distance = 0.6f, amount = 0.7f))
            }
            TileKey.SubjectTint -> {
                if (refs.subject == null) return false
                subject = SubjectSettings(maskRev = REF_REV)
                tint = true
            }
        }

        g.setGeometry(arrows, emptyList(), emptyList(), selections)
        g.setColor(adjustments, lut)
        g.setOverlay(overlay)
        g.setEffects(effects)
        g.setSky(sky, refs.sky, null, replace = true)
        g.setParallax(parallax, refs.depth)
        g.setSubject(subject, refs.subject, background, null, tintOnly = tint, checker = true)
        g.setTexts(textDraws)
        return true
    }

    private fun ensureGraph(refs: References): LoopRenderGraph? {
        graph?.let { return it }
        val e = EglCore(EglCore.Kind.PBUFFER).also { egl = it }
        e.makeCurrentPbuffer()
        return LoopRenderGraph(context.assets).also {
            it.setImage(refs.photo)
            graph = it
        }
    }

    private fun releaseGl() {
        graph?.release()
        graph = null
        targets.values.forEach { it.release() }
        targets.clear()
        texts.clear()
        egl?.release()
        egl = null
        // The photo and masks come back from disk quickly, no need to hold them while idle.
        refs?.photo?.recycle()
        refs = null
    }

    // endregion

    // region reference photo and masks

    private suspend fun references(): References? {
        refs?.let { return it }
        if (unavailable) return null
        val photo = withContext(io) {
            runCatching { BitmapDecoder.decode(PHOTO_EDGE) { context.assets.open(REFERENCE_ASSET) } }.getOrNull()
        }
        if (photo == null) {
            // Builds without the downloaded photo keep the drawn pictures.
            unavailable = true
            return null
        }
        val sky = mask("sky") { segmenter.segmentSky(photo) }
        val subject = mask("subject") { segmenter.segmentSubject(photo).takeIf { it.coverage() > 0.003f } }
        val depth = mask("depth") { depthEstimator.estimate(photo) }
        val water = mask("water") { segmenter.segmentAt(photo, WATER_X, WATER_Y) }
        val box = subject?.let { withContext(cpu) { bounds(it) } }
        return References(photo, sky, subject, depth, water, box).also { refs = it }
    }

    /** A reference mask from app storage, or computed once by [compute] and stored. Null when it fails. */
    private suspend fun mask(name: String, compute: suspend () -> AlphaMask?): AlphaMask? {
        val file = File(File(context.filesDir, "reference"), "${name}_v$MASK_VERSION.mask")
        withContext(io) {
            if (file.exists()) runCatching { file.inputStream().buffered().use(MaskCodec::read) }.getOrNull() else null
        }?.let { return it }
        val computed = try {
            withContext(cpu) { compute() }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "Reference $name mask failed", e)
            null
        } ?: return null
        withContext(io) {
            file.parentFile?.mkdirs()
            file.writeAtomically { out -> out.buffered().also { MaskCodec.write(computed, it) }.flush() }
        }
        return computed
    }

    private fun bounds(mask: AlphaMask): FloatArray? {
        var minX = mask.width
        var minY = mask.height
        var maxX = -1
        var maxY = -1
        for (y in 0 until mask.height) for (x in 0 until mask.width) {
            if (mask[x, y] >= 128) {
                if (x < minX) minX = x
                if (x > maxX) maxX = x
                if (y < minY) minY = y
                if (y > maxY) maxY = y
            }
        }
        if (maxX < 0) return null
        return floatArrayOf(
            minX.toFloat() / mask.width, minY.toFloat() / mask.height,
            (maxX + 1).toFloat() / mask.width, (maxY + 1).toFloat() / mask.height,
        )
    }

    // endregion

    // region disk cache of stills

    private fun cacheFile(r: TileRequest) = File(cacheRoot(), "${r.shape}_${r.key.id}.jpg")

    // Per app version: a release with new shaders renders its cards again.
    private fun cacheRoot() = File(File(context.cacheDir, "tiles"), "v${BuildConfig.VERSION_CODE}_$MASK_VERSION")

    private suspend fun loadCached(r: TileRequest): Bitmap? = withContext(io) {
        val file = cacheFile(r)
        if (file.exists()) BitmapFactory.decodeFile(file.path) else null
    }

    private suspend fun saveCached(r: TileRequest, bitmap: Bitmap) = withContext(io) {
        val root = cacheRoot()
        if (!root.exists()) {
            // Older versions' tiles are stale now.
            root.parentFile?.listFiles()?.filter { it != root }?.forEach { it.deleteRecursively() }
            root.mkdirs()
        }
        runCatching { cacheFile(r).writeAtomically { bitmap.compress(Bitmap.CompressFormat.JPEG, 90, it) } }
        Unit
    }

    // endregion

    private companion object {
        const val TAG = "TileRenderer"
        const val REFERENCE_ASSET = "reference/pier.jpg"
        const val PHOTO_EDGE = 768
        const val TEXT_WIDTH = 720
        const val MASK_VERSION = 1

        /** Any non-zero revision: the graph only checks that an area exists, the mask comes from here. */
        const val REF_REV = 1
        const val STILL_PHASE = 0.3f
        const val FRAMES = 16
        // About 2 MB per square loop, 5 MB per Help loop.
        const val MAX_ANIMATED = 3
        const val IDLE_MS = 20_000L

        /** Where the person sits in the reference photo: square crops center there. */
        const val FOCUS_X = 0.66f
        const val FOCUS_Y = 0.45f

        /** A point on the open lake, for the water region. */
        const val WATER_X = 0.25f
        const val WATER_Y = 0.55f
    }
}
