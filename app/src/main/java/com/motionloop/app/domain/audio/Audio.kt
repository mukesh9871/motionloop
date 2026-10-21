package com.motionloop.app.domain.audio

import kotlinx.serialization.Serializable
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.random.Random

@Serializable
enum class AudioSource { NONE, AMBIENT, FILE }

@Serializable
enum class AmbientSound(val label: String) { RAIN("Rain"), WAVES("Waves"), WIND("Wind"), FIRE("Fire") }

/** Sound for the loop (11c). Not part of undo, like other settings. */
@Serializable
data class AudioSettings(
    val source: AudioSource = AudioSource.NONE,
    val ambient: AmbientSound = AmbientSound.RAIN,
    /** Revision of the imported audio file, 0 = none. */
    val fileRev: Int = 0,
    val volume: Float = 0.8f,
    /** Animation speed pulses with the beat of the sound. */
    val beatSync: Boolean = false,
    /** 0..1 how strongly the speed pulses. */
    val beatAmount: Float = 0.6f,
) {
    val hasSound: Boolean get() = source == AudioSource.AMBIENT || (source == AudioSource.FILE && fileRev != 0)
}

/** 16-bit stereo PCM, interleaved L R. */
class Pcm(val sampleRate: Int, val data: ShortArray) {
    val frames: Int get() = data.size / 2
    val seconds: Float get() = frames.toFloat() / sampleRate

    companion object {
        const val RATE = 44_100
    }
}

/**
 * Ambient sounds made in code, so there is nothing to license. Each one loops exactly at its
 * length: the slow movements use whole periods of the length and the tail is crossfaded into the head.
 */
object AmbientSynth {

    fun generate(sound: AmbientSound, seconds: Float, sampleRate: Int = Pcm.RATE, seed: Int = 7): Pcm {
        val n = max(1, (seconds * sampleRate).roundToInt())
        val fade = min(n / 4, sampleRate / 2) // crossfade region, at most half a second
        val total = n + fade
        val left = FloatArray(total)
        val right = FloatArray(total)
        val rnd = Random(seed + sound.ordinal * 31)
        when (sound) {
            AmbientSound.RAIN -> rain(left, right, rnd, sampleRate)
            AmbientSound.WAVES -> waves(left, right, rnd, sampleRate, n)
            AmbientSound.WIND -> wind(left, right, rnd, sampleRate, n)
            AmbientSound.FIRE -> fire(left, right, rnd, sampleRate)
        }
        // Fold the extra tail over the start with an equal-power crossfade: sample n flows into sample 0.
        for (i in 0 until fade) {
            val t = i.toFloat() / fade
            val a = cos(t * PI / 2).toFloat()
            val b = sin(t * PI / 2).toFloat()
            left[i] = left[i] * b + left[n + i] * a
            right[i] = right[i] * b + right[n + i] * a
        }
        normalize(left, right, n)
        val out = ShortArray(n * 2)
        for (i in 0 until n) {
            out[i * 2] = toShort(left[i])
            out[i * 2 + 1] = toShort(right[i])
        }
        return Pcm(sampleRate, out)
    }

    private fun rain(l: FloatArray, r: FloatArray, rnd: Random, rate: Int) {
        var lpL = 0f
        var lpR = 0f
        var dropL = 0f
        var dropR = 0f
        for (i in l.indices) {
            // Hiss: high-passed noise (noise minus its own low-pass).
            val nL = rnd.nextFloat() * 2 - 1
            val nR = rnd.nextFloat() * 2 - 1
            lpL += 0.15f * (nL - lpL)
            lpR += 0.15f * (nR - lpR)
            // Droplets: random short ticks that decay in a few milliseconds.
            if (rnd.nextFloat() < 900f / rate) dropL = rnd.nextFloat() * 0.8f
            if (rnd.nextFloat() < 900f / rate) dropR = rnd.nextFloat() * 0.8f
            dropL *= 0.993f
            dropR *= 0.993f
            l[i] = 0.35f * (nL - lpL) + dropL * (rnd.nextFloat() * 2 - 1)
            r[i] = 0.35f * (nR - lpR) + dropR * (rnd.nextFloat() * 2 - 1)
        }
    }

    private fun waves(l: FloatArray, r: FloatArray, rnd: Random, rate: Int, loop: Int) {
        // Whole number of waves per loop (one every 5 to 8 s), so the swell is periodic.
        val count = max(1, (loop.toFloat() / rate / 6f).roundToInt())
        var brownL = 0f
        var brownR = 0f
        for (i in l.indices) {
            brownL = (brownL + (rnd.nextFloat() * 2 - 1) * 0.02f) * 0.998f
            brownR = (brownR + (rnd.nextFloat() * 2 - 1) * 0.02f) * 0.998f
            val phase = (i % loop).toFloat() / loop * count
            val swell = 0.5f - 0.5f * cos(2 * PI * phase).toFloat()
            val env = 0.15f + 0.85f * swell * swell
            l[i] = brownL * env
            r[i] = brownR * (0.15f + 0.85f * (0.5f - 0.5f * cos(2 * PI * (phase + 0.08f)).toFloat()).let { it * it })
        }
    }

    private fun wind(l: FloatArray, r: FloatArray, rnd: Random, rate: Int, loop: Int) {
        var lp1 = 0f
        var lp2 = 0f
        for (i in l.indices) {
            val phase = (i % loop).toFloat() / loop
            // Gusts: two slow periodic swells per loop, cutoff follows them.
            val gust = 0.5f + 0.3f * sin(2 * PI * 2 * phase).toFloat() + 0.2f * sin(2 * PI * 3 * phase + 1.0).toFloat()
            val k = 0.01f + 0.04f * gust
            val n = rnd.nextFloat() * 2 - 1
            lp1 += k * (n - lp1)
            lp2 += k * (lp1 - lp2)
            l[i] = lp2 * (0.4f + gust)
            r[i] = lp2 * (0.4f + (1f - gust) * 0.8f + 0.2f)
        }
    }

    private fun fire(l: FloatArray, r: FloatArray, rnd: Random, rate: Int) {
        var rumble = 0f
        var crackle = 0f
        var crackleLp = 0f
        var pan = 0.5f
        for (i in l.indices) {
            rumble = (rumble + (rnd.nextFloat() * 2 - 1) * 0.01f) * 0.999f
            if (rnd.nextFloat() < 25f / rate) {
                crackle = 0.5f + rnd.nextFloat() * 0.5f
                pan = rnd.nextFloat()
            }
            crackle *= 0.985f
            val c = crackle * (rnd.nextFloat() * 2 - 1)
            crackleLp += 0.5f * (c - crackleLp)
            l[i] = rumble * 1.5f + crackleLp * (1f - pan)
            r[i] = rumble * 1.5f + crackleLp * pan
        }
    }

    private fun normalize(l: FloatArray, r: FloatArray, n: Int) {
        var peak = 1e-6f
        for (i in 0 until n) peak = max(peak, max(abs(l[i]), abs(r[i])))
        val g = 0.8f / peak
        for (i in 0 until n) {
            l[i] *= g
            r[i] *= g
        }
    }

    private fun toShort(v: Float): Short = (v.coerceIn(-1f, 1f) * 32767f).roundToInt().toShort()
}

/** Builds the sound for a clip of an exact length. */
object ClipAudio {
    private const val FADE_SECONDS = 0.03f

    /**
     * [source] trimmed or looped to [seconds], at [volume]. Short fades at the clip edges and at
     * every loop seam avoid clicks. Ambient sounds should instead be generated at the clip length,
     * which loops seamlessly with the picture.
     */
    fun fit(source: Pcm, seconds: Float, volume: Float): Pcm {
        val n = max(1, (seconds * source.sampleRate).roundToInt())
        val fade = max(1, (FADE_SECONDS * source.sampleRate).roundToInt())
        val out = ShortArray(n * 2)
        val srcFrames = source.frames
        for (i in 0 until n) {
            val s = i % srcFrames
            // Gain dips to 0 at the clip start and end, and at each wrap of a looped source.
            val fromSeam = min(min(s, srcFrames - 1 - s), min(i, n - 1 - i))
            val g = volume.coerceIn(0f, 1f) * min(1f, fromSeam.toFloat() / fade)
            out[i * 2] = (source.data[s * 2] * g).roundToInt().toShort()
            out[i * 2 + 1] = (source.data[s * 2 + 1] * g).roundToInt().toShort()
        }
        return Pcm(source.sampleRate, out)
    }

    fun scaled(pcm: Pcm, volume: Float): Pcm {
        val g = volume.coerceIn(0f, 1f)
        return Pcm(pcm.sampleRate, ShortArray(pcm.data.size) { (pcm.data[it] * g).roundToInt().toShort() })
    }
}

/**
 * Finds beats as sharp rises in loudness, then turns them into a speed pulse envelope:
 * 0 between beats, jumping to 1 on a beat and decaying over about 150 ms.
 */
object BeatDetector {
    const val ENVELOPE_RATE = 100 // values per second

    fun beats(pcm: Pcm): FloatArray {
        val hop = pcm.sampleRate / ENVELOPE_RATE
        val windows = pcm.frames / hop
        if (windows < 3) return FloatArray(0)
        val energy = FloatArray(windows)
        for (w in 0 until windows) {
            var sum = 0.0
            for (f in w * hop until (w + 1) * hop) {
                val m = (pcm.data[f * 2] + pcm.data[f * 2 + 1]) / 65536.0
                sum += m * m
            }
            energy[w] = ln(1e-6 + sum / hop).toFloat()
        }
        // Onset strength: how much louder than the window before.
        val flux = FloatArray(windows)
        for (w in 1 until windows) flux[w] = max(0f, energy[w] - energy[w - 1])
        val result = ArrayList<Float>()
        val minGap = (ENVELOPE_RATE * 0.25f).toInt() // no two beats closer than 250 ms
        var last = -minGap
        for (w in 1 until windows - 1) {
            // Adaptive threshold: mean of the surrounding second, plus a margin.
            val from = max(0, w - ENVELOPE_RATE / 2)
            val to = min(windows, w + ENVELOPE_RATE / 2)
            var mean = 0f
            for (k in from until to) mean += flux[k]
            mean /= (to - from)
            val isPeak = flux[w] >= flux[w - 1] && flux[w] > flux[w + 1]
            if (isPeak && flux[w] > mean * 1.5f + 0.3f && w - last >= minGap) {
                result += w.toFloat() / ENVELOPE_RATE
                last = w
            }
        }
        return result.toFloatArray()
    }

    /** Envelope over [seconds] at [ENVELOPE_RATE], from beat times in seconds. */
    fun envelope(beats: FloatArray, seconds: Float): FloatArray {
        val n = max(1, (seconds * ENVELOPE_RATE).roundToInt())
        val env = FloatArray(n)
        for (b in beats) {
            val start = (b * ENVELOPE_RATE).toInt()
            for (i in start until min(n, start + ENVELOPE_RATE / 2)) {
                val t = (i - start).toFloat() / ENVELOPE_RATE
                env[i] = max(env[i], exp(-t / 0.15f))
            }
        }
        return env
    }
}
