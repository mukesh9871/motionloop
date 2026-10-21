package com.motionloop.app.data.audio

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import com.motionloop.app.domain.audio.Pcm

/**
 * Plays a PCM loop for the editor preview. A static AudioTrack with loop points loops the whole
 * buffer without gaps and without a feeding thread. Use from the main thread only.
 */
class AudioLoopPlayer {
    private var track: AudioTrack? = null
    private var loaded: Pcm? = null

    /** Loads [pcm] (replacing the previous loop). Null stops and frees the track. */
    fun setLoop(pcm: Pcm?) {
        if (pcm === loaded) return
        release()
        loaded = pcm
        if (pcm == null || pcm.frames < 2) return
        val t = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build(),
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setSampleRate(pcm.sampleRate)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_STEREO)
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .build(),
            )
            .setTransferMode(AudioTrack.MODE_STATIC)
            .setBufferSizeInBytes(pcm.data.size * 2)
            .build()
        t.write(pcm.data, 0, pcm.data.size)
        t.setLoopPoints(0, pcm.frames, -1)
        track = t
    }

    fun setVolume(volume: Float) {
        track?.setVolume(volume.coerceIn(0f, 1f))
    }

    fun play() {
        val t = track ?: return
        if (t.playState != AudioTrack.PLAYSTATE_PLAYING) t.play()
    }

    fun pause() {
        val t = track ?: return
        if (t.playState == AudioTrack.PLAYSTATE_PLAYING) t.pause()
    }

    fun release() {
        track?.let {
            runCatching { it.stop() }
            it.release()
        }
        track = null
        loaded = null
    }
}
