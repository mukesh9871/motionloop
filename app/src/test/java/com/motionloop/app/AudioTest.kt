package com.motionloop.app

import com.motionloop.app.domain.audio.AmbientSound
import com.motionloop.app.domain.audio.AmbientSynth
import com.motionloop.app.domain.audio.AudioSettings
import com.motionloop.app.domain.audio.AudioSource
import com.motionloop.app.domain.audio.BeatDetector
import com.motionloop.app.domain.audio.ClipAudio
import com.motionloop.app.domain.audio.Pcm
import com.motionloop.app.domain.model.LoopTiming
import com.motionloop.app.domain.model.Project
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.math.sin

class AudioTest {

    @Test
    fun ambientSoundsHaveExactLengthAndLoopSmoothly() {
        for (sound in AmbientSound.entries) {
            val pcm = AmbientSynth.generate(sound, 5f)
            assertEquals(5 * Pcm.RATE, pcm.frames)
            // The jump from the last sample to the first is no bigger than typical neighbor steps.
            var steps = 0.0
            for (i in 1 until pcm.frames) steps += abs(pcm.data[i * 2] - pcm.data[(i - 1) * 2])
            val typical = steps / (pcm.frames - 1)
            val wrap = abs(pcm.data[0] - pcm.data[(pcm.frames - 1) * 2]).toDouble()
            assertTrue("$sound wrap $wrap vs typical $typical", wrap <= typical * 12 + 200)
            // Not silent, not clipped.
            val peak = pcm.data.maxOf { abs(it.toInt()) }
            assertTrue("$sound peak $peak", peak in 5_000..32_767)
        }
    }

    @Test
    fun ambientIsDeterministic() {
        val a = AmbientSynth.generate(AmbientSound.FIRE, 1f)
        val b = AmbientSynth.generate(AmbientSound.FIRE, 1f)
        assertTrue(a.data.contentEquals(b.data))
    }

    @Test
    fun beatsAreFoundInAClickTrack() {
        // Quiet tone with a loud burst every 0.5 s.
        val rate = Pcm.RATE
        val frames = rate * 4
        val data = ShortArray(frames * 2)
        for (i in 0 until frames) {
            val t = i.toFloat() / rate
            val inBurst = (t % 0.5f) < 0.05f
            val v = if (inBurst) 20_000 * sin(i * 0.3f) else 300 * sin(i * 0.05f)
            data[i * 2] = v.toInt().toShort()
            data[i * 2 + 1] = v.toInt().toShort()
        }
        val beats = BeatDetector.beats(Pcm(rate, data))
        assertTrue("found ${beats.size}", beats.size in 6..9)
        for (b in beats) {
            val offset = b % 0.5f
            assertTrue("beat at $b", offset < 0.06f || offset > 0.46f)
        }
    }

    @Test
    fun envelopePeaksOnBeatsAndDecays() {
        val env = BeatDetector.envelope(floatArrayOf(1f), 2f)
        assertEquals(200, env.size)
        assertEquals(1f, env[100], 1e-6f)
        assertTrue(env[115] < 0.5f)
        assertEquals(0f, env[50], 1e-6f)
    }

    @Test
    fun beatSyncKeepsWholeCycles() {
        val fps = 24
        val n = 5 * fps
        val env = BeatDetector.envelope(floatArrayOf(0.5f, 1.3f, 2.2f, 4.1f), 5f)
        val phases = LoopTiming.beatPhases(n, fps.toFloat(), cycles = 2, env, BeatDetector.ENVELOPE_RATE, amount = 1f)
        assertEquals(0f, phases[0], 1e-6f)
        // Monotonic, and the next frame after the end would be exactly 2 cycles.
        for (i in 1 until n) assertTrue(phases[i] > phases[i - 1])
        val step = phases[n - 1] - phases[n - 2]
        assertTrue(phases[n - 1] < 2f && phases[n - 1] + step * 3 > 2f)
        // Faster right after a beat than in a quiet stretch.
        val beatStep = phases[13] - phases[12] // about 0.5 s
        val quietStep = phases[80] - phases[79] // about 3.3 s
        assertTrue(beatStep > quietStep)
    }

    @Test
    fun clipAudioLoopsShortSourcesWithFades() {
        // 1 s of a constant level, fitted to 2.5 s.
        val src = Pcm(Pcm.RATE, ShortArray(Pcm.RATE * 2) { 10_000 })
        val clip = ClipAudio.fit(src, 2.5f, volume = 1f)
        assertEquals((2.5f * Pcm.RATE).toInt(), clip.frames)
        assertEquals(0, clip.data[0].toInt()) // faded in
        assertEquals(10_000, clip.data[Pcm.RATE / 2 * 2].toInt()) // full level mid-loop
        assertEquals(0, clip.data[Pcm.RATE * 2].toInt()) // the loop seam at 1 s dips to 0
        assertEquals(0, clip.data[(clip.frames - 1) * 2].toInt()) // faded out
        val half = ClipAudio.fit(src, 1f, volume = 0.5f)
        assertEquals(5_000, half.data[Pcm.RATE / 2 * 2].toInt())
    }

    @Test
    fun beatsMapThroughLoopsAndTrims() {
        // A 2 s source with beats at 0.5 and 1.5 s, looped into a 5 s clip.
        val mapped = ClipAudio.mapBeats(floatArrayOf(0.5f, 1.5f), 2f, 5f).toList()
        assertEquals(listOf(0.5f, 1.5f, 2.5f, 3.5f, 4.5f), mapped)
        // Trimmed: beats past the clip end are dropped.
        assertEquals(listOf(0.5f), ClipAudio.mapBeats(floatArrayOf(0.5f, 6f), 10f, 3f).toList())
    }

    @Test
    fun soundRules() {
        assertFalse(AudioSettings().hasSound)
        assertTrue(AudioSettings(source = AudioSource.AMBIENT).hasSound)
        assertFalse(AudioSettings(source = AudioSource.FILE).hasSound)
        assertTrue(AudioSettings(source = AudioSource.FILE, fileRev = 1).hasSound)
    }

    @Test
    fun oldProjectsLoadWithoutSound() {
        val json = Json { ignoreUnknownKeys = true }
        val p = json.decodeFromString<Project>("""{"id":"a","createdAt":1,"updatedAt":2,"imageWidth":10,"imageHeight":20}""")
        assertEquals(AudioSettings(), p.audio)
    }
}
