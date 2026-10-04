package com.personal.calisthenicsguide.feedback

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.os.Handler
import android.os.Looper
import com.personal.calisthenics.core.timer.BeepPattern
import com.personal.calisthenics.core.timer.CueCatalog
import com.personal.calisthenics.core.timer.CueType
import kotlin.math.PI
import kotlin.math.min
import kotlin.math.sin

/**
 * Plays the short sine beeps of [CueCatalog]. Every beep asks for transient "may duck" audio focus first, so music
 * or podcasts in the background get quieter for a moment instead of being paused, and are restored afterwards.
 */
class Beeper(context: Context) {

    private val audioManager = context.applicationContext.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private val handler = Handler(Looper.getMainLooper())

    private val attributes = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_MEDIA)
        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
        .build()

    private val pcmCache = HashMap<CueType, ShortArray>()
    private var focusRequest: AudioFocusRequest? = null
    private var focusHeld = false

    private val releaseFocus = Runnable { abandonFocus() }

    @Volatile
    var enabled: Boolean = true

    fun play(type: CueType) {
        if (!enabled) return
        val pcm = pcmCache.getOrPut(type) { render(CueCatalog.beep(type)) }
        if (pcm.isEmpty()) return
        requestFocus()
        try {
            val format = AudioFormat.Builder()
                .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                .setSampleRate(SAMPLE_RATE)
                .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                .build()
            val track = AudioTrack.Builder()
                .setAudioAttributes(attributes)
                .setAudioFormat(format)
                .setBufferSizeInBytes(pcm.size * 2)
                .setTransferMode(AudioTrack.MODE_STATIC)
                .build()
            track.write(pcm, 0, pcm.size)
            track.play()
            val lengthMs = pcm.size * 1000L / SAMPLE_RATE
            handler.postDelayed({ runCatching { track.release() } }, lengthMs + 150L)
            // Keep the focus a little longer than the beep so rapid ticks do not make the music pump up and down.
            handler.removeCallbacks(releaseFocus)
            handler.postDelayed(releaseFocus, lengthMs + 700L)
        } catch (_: Exception) {
            // Audio is a convenience; never crash a workout because a beep failed.
        }
    }

    fun release() {
        handler.removeCallbacks(releaseFocus)
        abandonFocus()
    }

    private fun requestFocus() {
        if (focusHeld) return
        val request = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
            .setAudioAttributes(attributes)
            .setWillPauseWhenDucked(false)
            .setOnAudioFocusChangeListener { }
            .build()
        focusRequest = request
        focusHeld = audioManager.requestAudioFocus(request) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
    }

    private fun abandonFocus() {
        val request = focusRequest ?: return
        audioManager.abandonAudioFocusRequest(request)
        focusRequest = null
        focusHeld = false
    }

    private fun render(pattern: BeepPattern): ShortArray {
        val total = pattern.tones.sumOf { it.durationMs } * SAMPLE_RATE / 1000
        val out = ShortArray(total)
        var cursor = 0
        for (tone in pattern.tones) {
            val count = tone.durationMs * SAMPLE_RATE / 1000
            if (tone.frequencyHz > 0) {
                val fade = min(count / 2, SAMPLE_RATE / 200) // 5 ms fades avoid clicks
                for (i in 0 until count) {
                    val envelope = when {
                        i < fade -> i / fade.toDouble()
                        i > count - fade -> (count - i) / fade.toDouble()
                        else -> 1.0
                    }
                    val value = sin(2.0 * PI * tone.frequencyHz * i / SAMPLE_RATE) * envelope * AMPLITUDE
                    out[cursor + i] = (value * Short.MAX_VALUE).toInt().toShort()
                }
            }
            cursor += count
        }
        return out
    }

    private companion object {
        const val SAMPLE_RATE = 44_100
        const val AMPLITUDE = 0.85
    }
}
