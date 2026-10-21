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
import kotlin.math.min

/** Decodes any audio file Android can read to 44.1 kHz stereo 16-bit PCM. Blocking: call off the main thread. */
object AudioDecoder {

    /** Longest stretch kept: loops are short, and this bounds memory (60 s is about 10 MB). */
    const val MAX_SECONDS = 60

    fun decode(context: Context, uri: Uri): Pcm = decode { it.setDataSource(context, uri, null) }

    fun decode(file: File): Pcm = decode { it.setDataSource(file.path) }

    private fun decode(open: (MediaExtractor) -> Unit): Pcm {
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
            var maxFrames = rate * MAX_SECONDS
            var idle = 0
            while (true) {
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
                    }
                }
                val outIndex = c.dequeueOutputBuffer(info, 10_000)
                when {
                    outIndex == MediaCodec.INFO_TRY_AGAIN_LATER -> {
                        // 10 ms per try: give up after 5 s without output once all input is in.
                        if (inputDone && ++idle > 500) throw IOException("The audio decoder stopped responding")
                    }
                    outIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                        rate = c.outputFormat.getInteger(MediaFormat.KEY_SAMPLE_RATE)
                        channels = c.outputFormat.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
                        maxFrames = rate * MAX_SECONDS
                    }
                    outIndex >= 0 -> {
                        idle = 0
                        val buffer = c.getOutputBuffer(outIndex)!!
                        val pcmEncoding = runCatching { c.outputFormat.getInteger(MediaFormat.KEY_PCM_ENCODING) }
                            .getOrDefault(AudioFormat.ENCODING_PCM_16BIT)
                        if (pcmEncoding != AudioFormat.ENCODING_PCM_16BIT) throw IOException("Unsupported audio sample format")
                        val shorts = buffer.order(ByteOrder.LITTLE_ENDIAN).asShortBuffer()
                        shorts.position(info.offset / 2)
                        shorts.limit((info.offset + info.size) / 2)
                        while (shorts.hasRemaining() && out.size / channels < maxFrames) out.add(shorts.get())
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

    /** Mixes or duplicates channels to stereo and resamples linearly to 44.1 kHz. */
    internal fun toStereo44k(samples: ShortArray, rate: Int, channels: Int): Pcm {
        val frames = samples.size / channels
        val outFrames = (frames.toLong() * Pcm.RATE / rate).toInt().coerceAtLeast(1)
        val out = ShortArray(outFrames * 2)
        fun sample(frame: Int, ch: Int): Float {
            val f = frame.coerceIn(0, frames - 1)
            return samples[f * channels + min(ch, channels - 1)].toFloat()
        }
        for (i in 0 until outFrames) {
            val src = i.toDouble() * rate / Pcm.RATE
            val f0 = src.toInt()
            val t = (src - f0).toFloat()
            for (ch in 0..1) {
                val v = sample(f0, ch) * (1 - t) + sample(f0 + 1, ch) * t
                out[i * 2 + ch] = v.toInt().coerceIn(-32768, 32767).toShort()
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

    fun encode(pcm: Pcm): EncodedAudio {
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
                    }
                }
                val outIndex = codec.dequeueOutputBuffer(info, 10_000)
                when {
                    outIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> outFormat = codec.outputFormat
                    outIndex == MediaCodec.INFO_TRY_AGAIN_LATER -> if (inputDone && ++idle > 500) throw IOException("Audio encoder did not finish")
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
