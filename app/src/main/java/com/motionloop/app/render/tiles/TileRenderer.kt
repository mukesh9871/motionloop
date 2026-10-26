package com.motionloop.app.render.tiles

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.selects.select
import kotlinx.coroutines.selects.onTimeout
import kotlinx.coroutines.isActive
import kotlinx.coroutines.currentCoroutineContext
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.opengl.GLES30
import android.util.Log
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
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
    private val queued = ConcurrentHashMap.newKeySet<String>()

    private val queue = Channel<Job>(Channel.UNLIMITED)

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

    /** The photo, and its masks once a tile needs them. A mask that failed stays null until the next idle release. */
    private class References(val photo: Bitmap) {
        val masks = HashMap<String, AlphaMask?>()
        var subjectBox: FloatArray? = null
        var subjectBoxDone = false
    }

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

    // onTimeout in select is still marked experimental.
    @OptIn(ExperimentalCoroutinesApi::class)
    private suspend fun work() {
        while (true) {
            // Nothing asked for a while: free the GL context and the photo until the next request.
            // select, not withTimeoutOrNull(receive): a job arriving as the timeout fires is never lost.
            val job = select<Job?> {
                queue.onReceive { it }
                onTimeout(IDLE_MS) { null }
            }
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
            // Still pictures need no loop: sixteen identical frames would only take memory.
            if (job.animated && r.key.moves && have != null && have.frames == null) {
                val phases = List(FRAMES) { it.toFloat() / FRAMES }
                val frames = render(r, phases)?.map { it.asImageBitmap() }
                if (frames != null) {
                    // Stored before the eviction pass, so the pass sees this loop like any other.
                    _images.update { it + (r to TileImage(have.still, frames)) }
                    keepFramesFor(r)
                }
            }
        } catch (e: CancellationException) {
            // A Play services task cancelled under us must not end the worker for the app's lifetime.
            if (!currentCoroutineContext().isActive) throw e
            Log.w(TAG, "Tile ${job.request.key.id} cancelled", e)
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
        // Only the masks this tile needs: the others are never loaded or computed for it.
        var skyMask: AlphaMask? = null
        var subjectMask: AlphaMask? = null
        var depthMask: AlphaMask? = null

        when (key) {
            is TileKey.Sky -> {
                if (key.style == SkyStyle.PHOTO) return false
                skyMask = mask(refs, "sky") ?: return false
                sky = SkySettings(enabled = true, style = key.style, maskRev = REF_REV, drift = 0.6f)
            }
            is TileKey.Background -> {
                if (key.mode == BackgroundMode.PHOTO) return false
                if (key.mode != BackgroundMode.NONE) {
                    subjectMask = mask(refs, "subject") ?: return false
                    subject = SubjectSettings(maskRev = REF_REV)
                    background = BackgroundSettings(mode = key.mode)
                }
            }
            is TileKey.Camera -> {
                depthMask = mask(refs, "depth") ?: return false
                parallax = ParallaxSettings(enabled = true, path = key.path, amount = 1f, focus = 0.35f, depthRev = REF_REV)
            }
            is TileKey.Particles -> if (key.type != ParticleType.NONE) overlay = OverlaySettings(particles = key.type, density = 0.55f, size = 0.6f)
            is TileKey.Leak -> overlay = OverlaySettings(lightLeak = key.leak, leakIntensity = 0.85f)
            is TileKey.Air -> overlay = OverlaySettings(atmosphere = key.atmosphere, atmosphereAmount = 0.8f)
            is TileKey.Sticker -> effects = EffectsSettings(stickers = listOf(Sticker("tile", key.kind, x = FOCUS_X, y = 0.36f, size = 0.3f)))
            is TileKey.Text -> {
                subjectMask = mask(refs, "subject")
                if (subjectMask != null) subject = SubjectSettings(maskRev = REF_REV)
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
                val water = mask(refs, "water") ?: return false
                selections = listOf(SelectionLayer(water, SelectionMode.RIPPLE))
                effects = EffectsSettings(ripple = RippleSettings(strength = 1f))
            }
            TileKey.Disperse -> {
                val box = subjectBox(refs) ?: return false
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
                subjectMask = mask(refs, "subject") ?: return false
                subject = SubjectSettings(maskRev = REF_REV)
                tint = true
            }
        }

        g.setGeometry(arrows, emptyList(), emptyList(), selections)
        g.setColor(adjustments, lut)
        g.setOverlay(overlay)
        g.setEffects(effects)
        g.setSky(sky, skyMask, null, replace = true)
        g.setParallax(parallax, depthMask)
        g.setSubject(subject, subjectMask, background, null, tintOnly = tint, checker = true)
        g.setTexts(textDraws)
        return true
    }

    private fun ensureGraph(refs: References): LoopRenderGraph? {
        graph?.let { return it }
        if (unavailable) return null
        val e = EglCore(EglCore.Kind.PBUFFER).also { egl = it }
        return try {
            e.makeCurrentPbuffer()
            LoopRenderGraph(context.assets).also {
                it.setImage(refs.photo)
                graph = it
            }
        } catch (t: Exception) {
            // Shaders or framebuffers this GPU refuses: give up on renders, keep the drawings.
            Log.w(TAG, "Tile graph failed", t)
            e.release()
            egl = null
            unavailable = true
            null
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
        return References(photo).also { refs = it }
    }

    /** The reference mask [name], loaded or computed the first time a tile needs it. */
    private suspend fun mask(refs: References, name: String): AlphaMask? {
        if (refs.masks.containsKey(name)) return refs.masks[name]
        val photo = refs.photo
        val result = loadOrCompute(name) {
            when (name) {
                "sky" -> segmenter.segmentSky(photo)
                // The photo is fixed and the person's spot is known: tap there, like a user would.
                // Deterministic, offline, and never the lake (ML Kit's fallback taps the center).
                "subject" -> segmenter.segmentAtAll(photo, listOf(PERSON_X to PERSON_TOP, PERSON_X to PERSON_BOTTOM))
                "depth" -> depthEstimator.estimate(photo)
                "water" -> segmenter.segmentAt(photo, WATER_X, WATER_Y)
                else -> null
            }
        }
        refs.masks[name] = result
        return result
    }

    private suspend fun subjectBox(refs: References): FloatArray? {
        if (!refs.subjectBoxDone) {
            refs.subjectBox = mask(refs, "subject")?.let { withContext(cpu) { bounds(it) } }
            refs.subjectBoxDone = true
        }
        return refs.subjectBox
    }

    /** A reference mask from app storage, or computed once by [compute] and stored. Null when it fails. */
    private suspend fun loadOrCompute(name: String, compute: suspend () -> AlphaMask?): AlphaMask? {
        val file = File(File(context.filesDir, "reference"), "${name}_v$MASK_VERSION.mask")
        withContext(io) {
            if (file.exists()) runCatching { file.inputStream().buffered().use(MaskCodec::read) }.getOrNull() else null
        }?.let { return it }
        val computed = try {
            withContext(cpu) { compute() }
        } catch (e: CancellationException) {
            if (!currentCoroutineContext().isActive) throw e
            Log.w(TAG, "Reference $name mask cancelled", e)
            null
        } catch (e: Exception) {
            Log.w(TAG, "Reference $name mask failed", e)
            null
        } ?: return null
        withContext(io) {
            // Not stored (disk full): still use it this session.
            runCatching {
                file.parentFile?.mkdirs()
                file.writeAtomically { out -> out.buffered().also { MaskCodec.write(computed, it) }.flush() }
            }
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

    // Per installed build: every update (even with the same version code) renders its cards again.
    private val buildStamp: Long by lazy {
        runCatching { context.packageManager.getPackageInfo(context.packageName, 0).lastUpdateTime }.getOrDefault(0L)
    }

    private fun cacheRoot() = File(File(context.cacheDir, "tiles"), "b${buildStamp}_$MASK_VERSION")

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
        // 2: the subject is the tapped person, not ML Kit's guess.
        const val MASK_VERSION = 2

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

        /** Two points on the person sitting on the pier (head and back), in photo coordinates. */
        const val PERSON_X = 0.79f
        const val PERSON_TOP = 0.42f
        const val PERSON_BOTTOM = 0.53f

        /** A point on the open lake, for the water region. */
        const val WATER_X = 0.25f
        const val WATER_Y = 0.55f
    }
}
