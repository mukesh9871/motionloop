package com.motionloop.app.data.audio

import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import com.motionloop.app.domain.audio.Pcm

/**
 * Plays a PCM loop for the editor preview. A static AudioTrack with loop points loops the whole
 * buffer without gaps and without a feeding thread. Control from the main thread only.
 * [positionSeconds] may be read from the render thread: it is the beat clock of the preview.
 */
class AudioLoopPlayer(private val audioManager: AudioManager) {
    @Volatile private var track: AudioTrack? = null
    @Volatile private var loaded: Pcm? = null

    // Transient focus: other audio pauses or ducks while the preview plays, and a call pauses the preview.
    private val focusRequest = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
        .setAudioAttributes(attributes)
        .setOnAudioFocusChangeListener { change -> if (change < 0) track?.pause() }
        .build()

    /** Loads [pcm] (replacing the previous loop). Null stops and frees the track. */
    fun setLoop(pcm: Pcm?) {
        if (pcm === loaded) return
        release()
        loaded = pcm
        if (pcm == null || pcm.frames < 2) return
        val t = AudioTrack.Builder()
            .setAudioAttributes(attributes)
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
        if (t.playState == AudioTrack.PLAYSTATE_PLAYING) return
        if (audioManager.requestAudioFocus(focusRequest) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED) t.play()
    }

    fun pause() {
        val t = track ?: return
        if (t.playState == AudioTrack.PLAYSTATE_PLAYING) t.pause()
        audioManager.abandonAudioFocusRequest(focusRequest)
    }

    /** Where in the loop playback is, in seconds. Null while nothing is playing. */
    fun positionSeconds(): Float? {
        val t = track ?: return null
        val pcm = loaded ?: return null
        if (t.playState != AudioTrack.PLAYSTATE_PLAYING) return null
        return (t.playbackHeadPosition % pcm.frames).toFloat() / pcm.sampleRate
    }

    fun release() {
        audioManager.abandonAudioFocusRequest(focusRequest)
        track?.let {
            runCatching { it.stop() }
            it.release()
        }
        track = null
        loaded = null
    }
}

private val attributes: AudioAttributes = AudioAttributes.Builder()
    .setUsage(AudioAttributes.USAGE_MEDIA)
    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
    .build()
