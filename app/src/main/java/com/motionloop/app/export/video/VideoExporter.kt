package com.motionloop.app.export.video

import android.content.Context
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaCodecList
import android.media.MediaFormat
import android.media.MediaMuxer
import com.motionloop.app.core.gl.EglCore
import com.motionloop.app.core.gl.GlUtil
import com.motionloop.app.domain.model.ExportResolution
import com.motionloop.app.domain.model.ExportSettings
import com.motionloop.app.domain.model.LoopTiming
import com.motionloop.app.domain.model.Project
import com.motionloop.app.domain.repository.ProjectRepository
import com.motionloop.app.export.OffscreenLoop
import com.motionloop.app.render.LoopRenderGraph
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.ensureActive
import java.io.File
import javax.inject.Inject
import kotlinx.coroutines.currentCoroutineContext
import kotlin.math.max

/**
 * Renders the loop straight into a MediaCodec input surface (GPU to encoder, no CPU copies)
 * and muxes H.264 into MP4.
 */
class VideoExporter @Inject constructor(
    @ApplicationContext private val context: Context,
    private val projects: ProjectRepository,
) {

    suspend fun export(project: Project, settings: ExportSettings, out: File, onProgress: (Float) -> Unit) =
        OffscreenLoop.onGlThread("ml-video-export") {
            val (w, h) = outputSize(project, settings.resolution)
                ?: error("This device cannot encode ${settings.resolution.label} video")
            val fps = ExportSettings.VIDEO_FPS
            val frameCount = settings.lengthSeconds * fps
            val cycles = LoopTiming.cyclesForClip(settings.lengthSeconds.toFloat(), project.loop.speed)

            val format = MediaFormat.createVideoFormat(MIME, w, h).apply {
                setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface)
                setInteger(MediaFormat.KEY_BIT_RATE, bitrateFor(w, h, fps))
                setInteger(MediaFormat.KEY_FRAME_RATE, fps)
                setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1)
            }
            val codec = MediaCodec.createEncoderByType(MIME)
            var muxer: MediaMuxer? = null
            val egl = EglCore()
            var graph: LoopRenderGraph? = null
            var success = false
            try {
                codec.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
                val inputSurface = codec.createInputSurface()
                codec.start()
                egl.makeCurrentWindow(inputSurface)

                // Decode near output size: sharper than GPU minification and far less memory.
                val bitmap = projects.loadImage(project.id, minOf(max(w, h) * 2, GlUtil.maxTextureSize()))
                graph = LoopRenderGraph(context.assets).apply {
                    setImage(bitmap)
                    setGeometry(project.arrows, project.anchors, project.strokes)
                }

                val m = MediaMuxer(out.path, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4).also { muxer = it }
                val drainer = Drainer(codec, m)
                for (i in 0 until frameCount) {
                    currentCoroutineContext().ensureActive()
                    drainer.drain(endOfStream = false)
                    graph.draw(
                        fbo = 0, x = 0, y = 0, width = w, height = h,
                        phase = LoopTiming.phaseForFrame(i, frameCount, cycles),
                        mode = project.loop.mode,
                        amount = 1f,
                        maskOverlay = 0f,
                        flipY = true,
                    )
                    egl.setPresentationTime(i * 1_000_000_000L / fps)
                    egl.swapBuffers()
                    onProgress((i + 1f) / frameCount)
                }
                codec.signalEndOfInputStream()
                drainer.drain(endOfStream = true)
                success = true
            } finally {
                graph?.release()
                egl.release()
                runCatching { codec.stop() }
                codec.release()
                runCatching { muxer?.stop() }
                muxer?.release()
                if (!success) out.delete()
            }
        }

    /** Pulls encoded packets out of the codec and writes them to the muxer. */
    private class Drainer(private val codec: MediaCodec, private val muxer: MediaMuxer) {
        private val info = MediaCodec.BufferInfo()
        private var track = -1

        fun drain(endOfStream: Boolean) {
            var idleTries = 0
            while (true) {
                val index = codec.dequeueOutputBuffer(info, if (endOfStream) 10_000L else 0L)
                when {
                    index == MediaCodec.INFO_TRY_AGAIN_LATER -> {
                        if (!endOfStream) return
                        // 10 ms per try, give up after 5 s instead of hanging.
                        if (++idleTries > 500) error("Encoder did not finish")
                    }
                    index == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                        check(track < 0) { "Format changed twice" }
                        track = muxer.addTrack(codec.outputFormat)
                        muxer.start()
                    }
                    index >= 0 -> {
                        val buffer = codec.getOutputBuffer(index) ?: error("Null output buffer")
                        if (info.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG != 0) info.size = 0
                        if (info.size > 0 && track >= 0) {
                            buffer.position(info.offset)
                            buffer.limit(info.offset + info.size)
                            muxer.writeSampleData(track, buffer, info)
                        }
                        codec.releaseOutputBuffer(index, false)
                        if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) return
                    }
                }
            }
        }
    }

    companion object {
        private const val MIME = MediaFormat.MIMETYPE_VIDEO_AVC

        /** Output size for [resolution], shrunk until the device encoder accepts it. Null if nothing fits. */
        fun outputSize(project: Project, resolution: ExportResolution): Pair<Int, Int>? {
            val caps = avcCapabilities() ?: return null
            var edge = resolution.shortEdge
            while (edge >= 144) {
                val (w, h) = OffscreenLoop.fit(
                    project.aspect, edge, edgeIsShort = true,
                    srcW = project.imageWidth, srcH = project.imageHeight, align = 16,
                )
                if (caps.isSizeSupported(w, h)) return w to h
                edge = (edge * 0.9f).toInt()
            }
            return null
        }

        /** True if the encoder can do [resolution] at (close to) its nominal size. Used to hide 4K. */
        fun supportsNominal(project: Project, resolution: ExportResolution): Boolean {
            val caps = avcCapabilities() ?: return false
            val (w, h) = OffscreenLoop.fit(project.aspect, resolution.shortEdge, true, Int.MAX_VALUE, Int.MAX_VALUE, 16)
            return caps.isSizeSupported(w, h)
        }

        private fun avcCapabilities(): MediaCodecInfo.VideoCapabilities? =
            MediaCodecList(MediaCodecList.REGULAR_CODECS).codecInfos
                .firstOrNull { it.isEncoder && it.supportedTypes.any { t -> t.equals(MIME, ignoreCase = true) } }
                ?.getCapabilitiesForType(MIME)?.videoCapabilities

        /** About 0.2 bits per pixel per frame: clean for slow-moving content without huge files. */
        private fun bitrateFor(w: Int, h: Int, fps: Int): Int = (w.toLong() * h * fps / 5).coerceIn(2_000_000, 40_000_000).toInt()
    }
}
