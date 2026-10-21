package com.motionloop.app.data.audio

import android.content.Context
import android.media.AudioFormat
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import com.motionloop.app.domain.audio.Pcm
import java.io.File
import java.io.IOException
import java.nio.ByteOrder
import kotlinx.coroutines.CancellationException
import kotlin.math.min
import kotlin.math.roundToInt

/** Decodes any audio file Android can read to 44.1 kHz stereo 16-bit PCM. Blocking: call off the main thread. */
object AudioDecoder {

    /** Longest stretch kept: loops are short, and this bounds memory (60 s is about 10 MB). */
    const val MAX_SECONDS = 60

    /** [maxSeconds] bounds work and memory to what the caller needs. [cancelled] is polled between buffers. */
    fun decode(context: Context, uri: Uri, maxSeconds: Int = MAX_SECONDS, cancelled: () -> Boolean = { false }): Pcm =
        decode(maxSeconds, cancelled) { it.setDataSource(context, uri, null) }

    fun decode(file: File, maxSeconds: Int = MAX_SECONDS, cancelled: () -> Boolean = { false }): Pcm =
        decode(maxSeconds, cancelled) { it.setDataSource(file.path) }

    private fun decode(maxSeconds: Int, cancelled: () -> Boolean, open: (MediaExtractor) -> Unit): Pcm {
        val extractor = MediaExtractor()
        var codec: MediaCodec? = null
        try {
            open(extractor)
            val track = (0 until extractor.trackCount).firstOrNull {
                extractor.getTrackFormat(it).getString(MediaFormat.KEY_MIME)?.startsWith("audio/") == true
            } ?: throw IOException("No audio track in that file")
            extractor.selectTrack(track)
            val format = extractor.getTrackFormat(track)
            val mime = format.getString(MediaFormat.KEY_MIME)!!
            val c = MediaCodec.createDecoderByType(mime).also { codec = it }
            c.configure(format, null, null, 0)
            c.start()

            var rate = format.getInteger(MediaFormat.KEY_SAMPLE_RATE)
            var channels = format.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
            val out = ShortArrayBuilder()
            val info = MediaCodec.BufferInfo()
            var inputDone = false
            val seconds = maxSeconds.coerceIn(1, MAX_SECONDS)
            var maxFrames = rate * seconds
            var idle = 0
            while (true) {
                if (cancelled()) throw CancellationException("Decoding cancelled")
                if (!inputDone) {
                    val inIndex = c.dequeueInputBuffer(10_000)
                    if (inIndex >= 0) {
                        val buffer = c.getInputBuffer(inIndex)!!
                        val size = extractor.readSampleData(buffer, 0)
                        if (size < 0) {
                            c.queueInputBuffer(inIndex, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                            inputDone = true
                        } else {
                            c.queueInputBuffer(inIndex, 0, size, extractor.sampleTime, 0)
                            extractor.advance()
                        }
                        idle = 0
                    }
                }
                val outIndex = c.dequeueOutputBuffer(info, 10_000)
                when {
                    outIndex == MediaCodec.INFO_TRY_AGAIN_LATER -> {
                        // 10 ms per try: give up after 10 s without any progress, before or after the last input.
                        if (++idle > 1000) throw IOException("The audio decoder stopped responding")
                    }
                    outIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                        rate = c.outputFormat.getInteger(MediaFormat.KEY_SAMPLE_RATE)
                        channels = c.outputFormat.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
                        maxFrames = rate * seconds
                    }
                    outIndex >= 0 -> {
                        idle = 0
                        val buffer = c.getOutputBuffer(outIndex)!!
                        val pcmEncoding = runCatching { c.outputFormat.getInteger(MediaFormat.KEY_PCM_ENCODING) }
                            .getOrDefault(AudioFormat.ENCODING_PCM_16BIT)
                        // getOutputBuffer already limits the buffer to this packet's data.
                        buffer.order(ByteOrder.LITTLE_ENDIAN)
                        when (pcmEncoding) {
                            AudioFormat.ENCODING_PCM_16BIT -> {
                                val shorts = buffer.asShortBuffer()
                                while (shorts.hasRemaining() && out.size / channels < maxFrames) out.add(shorts.get())
                            }
                            AudioFormat.ENCODING_PCM_FLOAT -> {
                                val floats = buffer.asFloatBuffer()
                                while (floats.hasRemaining() && out.size / channels < maxFrames) {
                                    out.add((floats.get().coerceIn(-1f, 1f) * 32767f).roundToInt().toShort())
                                }
                            }
                            else -> throw IOException("Unsupported audio sample format")
                        }
                        c.releaseOutputBuffer(outIndex, false)
                        val full = out.size / channels >= maxFrames
                        if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0 || full) break
                    }
                }
            }
            if (out.size < channels) throw IOException("The audio file is empty")
            return toStereo44k(out.toArray(), rate, channels)
        } finally {
            codec?.let {
                runCatching { it.stop() }
                it.release()
            }
            extractor.release()
        }
    }

    /**
     * To stereo (mono duplicated, 5.1 and up downmixed with center and surrounds at -3 dB, other
     * layouts take their first two channels) and resampled linearly to 44.1 kHz.
     */
    internal fun toStereo44k(samples: ShortArray, rate: Int, channels: Int): Pcm {
        val frames = samples.size / channels
        val outFrames = (frames.toLong() * Pcm.RATE / rate).toInt().coerceAtLeast(1)
        val out = ShortArray(outFrames * 2)
        fun raw(frame: Int, ch: Int): Float = samples[frame.coerceIn(0, frames - 1) * channels + ch].toFloat()
        // Order for 5.1: FL, FR, C, LFE, BL, BR.
        fun sample(frame: Int, side: Int): Float = when {
            channels == 1 -> raw(frame, 0)
            channels >= 6 -> (raw(frame, side) + 0.707f * raw(frame, 2) + 0.707f * raw(frame, 4 + side)) / 2.414f
            else -> raw(frame, side)
        }
        for (i in 0 until outFrames) {
            val src = i.toDouble() * rate / Pcm.RATE
            val f0 = src.toInt()
            val t = (src - f0).toFloat()
            for (side in 0..1) {
                val v = sample(f0, side) * (1 - t) + sample(f0 + 1, side) * t
                out[i * 2 + side] = v.roundToInt().coerceIn(-32768, 32767).toShort()
            }
        }
        return Pcm(Pcm.RATE, out)
    }

    private class ShortArrayBuilder {
        private var data = ShortArray(1 shl 16)
        var size = 0
            private set

        fun add(v: Short) {
            if (size == data.size) data = data.copyOf(data.size * 2)
            data[size++] = v
        }

        fun toArray(): ShortArray = data.copyOf(size)
    }
}

/** One encoded AAC packet, kept in memory until the muxer starts. */
class AacSample(val data: ByteArray, val presentationUs: Long, val flags: Int)

class EncodedAudio(val format: MediaFormat, val samples: List<AacSample>)

/** Encodes PCM to AAC-LC for the MP4 export. 15 s of 128 kbps AAC is about 240 KB. */
object AudioEncoder {
    private const val MIME = MediaFormat.MIMETYPE_AUDIO_AAC
    private const val BITRATE = 128_000

    /** [cancelled] is polled between buffers, so cancelling an export does not wait for the encoder. */
    fun encode(pcm: Pcm, cancelled: () -> Boolean = { false }): EncodedAudio {
        val format = MediaFormat.createAudioFormat(MIME, pcm.sampleRate, 2).apply {
            setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectLC)
            setInteger(MediaFormat.KEY_BIT_RATE, BITRATE)
            setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, 16_384)
        }
        val codec = MediaCodec.createEncoderByType(MIME)
        try {
            codec.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
            codec.start()
            val samples = ArrayList<AacSample>()
            var outFormat: MediaFormat? = null
            val info = MediaCodec.BufferInfo()
            var frame = 0
            var inputDone = false
            var idle = 0
            while (true) {
                if (cancelled()) throw CancellationException("Encoding cancelled")
                if (!inputDone) {
                    val inIndex = codec.dequeueInputBuffer(10_000)
                    if (inIndex >= 0) {
                        val buffer = codec.getInputBuffer(inIndex)!!
                        buffer.clear()
                        val pts = frame * 1_000_000L / pcm.sampleRate
                        val count = min(buffer.capacity() / 4, pcm.frames - frame)
                        if (count <= 0) {
                            codec.queueInputBuffer(inIndex, 0, 0, pts, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                            inputDone = true
                        } else {
                            val shorts = buffer.order(ByteOrder.LITTLE_ENDIAN).asShortBuffer()
                            shorts.put(pcm.data, frame * 2, count * 2)
                            codec.queueInputBuffer(inIndex, 0, count * 4, pts, 0)
                            frame += count
                        }
                        idle = 0
                    }
                }
                val outIndex = codec.dequeueOutputBuffer(info, 10_000)
                when {
                    outIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> outFormat = codec.outputFormat
                    // 10 ms per try: no progress at all for 10 s means the encoder is stuck.
                    outIndex == MediaCodec.INFO_TRY_AGAIN_LATER -> if (++idle > 1000) throw IOException("Audio encoder did not finish")
                    outIndex >= 0 -> {
                        idle = 0
                        val buffer = codec.getOutputBuffer(outIndex)!!
                        // Codec config data goes into the track format, not into the stream.
                        if (info.size > 0 && info.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG == 0) {
                            val bytes = ByteArray(info.size)
                            buffer.position(info.offset)
                            buffer.get(bytes)
                            samples += AacSample(bytes, info.presentationTimeUs, info.flags)
                        }
                        codec.releaseOutputBuffer(outIndex, false)
                        if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) break
                    }
                }
            }
            return EncodedAudio(outFormat ?: throw IOException("Audio encoder gave no format"), samples)
        } finally {
            runCatching { codec.stop() }
            codec.release()
        }
    }
}
