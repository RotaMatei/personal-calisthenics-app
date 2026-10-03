package com.personal.calisthenicsguide.feedback

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import com.personal.calisthenics.core.timer.BeepPattern
import com.personal.calisthenics.core.timer.CueCatalog
import com.personal.calisthenics.core.timer.CueType
import kotlin.math.PI
import kotlin.math.sin

/**
 * Plays the short beeps and vibration patterns of [CueCatalog]. Beeps are synthesised sine tones. Each beep asks
 * for transient audio focus with "may duck", so background music is lowered for a moment instead of paused.
 * Call from the main thread.
 */
class CueFeedback(context: Context) {

    private val appContext = context.applicationContext
    private val audioManager = appContext.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private val handler = Handler(Looper.getMainLooper())
    private val pcmCache = HashMap<CueType, ShortArray>()

    private val vibrator: Vibrator? =
        if (Build.VERSION.SDK_INT >= 31) {
            (appContext.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager)?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            appContext.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        }

    private val attributes = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
        .build()

    private val focusRequest = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
        .setAudioAttributes(attributes)
        .setWillPauseWhenDucked(false)
        .setOnAudioFocusChangeListener { }
        .build()

    private val abandonFocus = Runnable { audioManager.abandonAudioFocusRequest(focusRequest) }

    fun play(type: CueType, sound: Boolean, vibrate: Boolean) {
        if (sound) beep(type)
        if (vibrate) vibrate(type)
    }

    private fun beep(type: CueType) {
        val pcm = pcmCache.getOrPut(type) { synthesize(CueCatalog.beep(type)) }
        if (pcm.isEmpty()) return
        runCatching {
            audioManager.requestAudioFocus(focusRequest)
            val track = AudioTrack.Builder()
                .setAudioAttributes(attributes)
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setSampleRate(SAMPLE_RATE)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                        .build(),
                )
                .setBufferSizeInBytes(pcm.size * 2)
                .setTransferMode(AudioTrack.MODE_STATIC)
                .build()
            track.write(pcm, 0, pcm.size)
            track.play()
            val durationMs = pcm.size * 1000L / SAMPLE_RATE
            handler.postDelayed({ runCatching { track.release() } }, durationMs + 150L)
            handler.removeCallbacks(abandonFocus)
            handler.postDelayed(abandonFocus, durationMs + 250L)
        }
    }

    private fun vibrate(type: CueType) {
        val v = vibrator ?: return
        if (!v.hasVibrator()) return
        val pattern = CueCatalog.haptic(type)
        runCatching {
            val timings = pattern.timingsMs.toLongArray()
            val effect = if (v.hasAmplitudeControl()) {
                VibrationEffect.createWaveform(timings, pattern.fullAmplitudes().toIntArray(), -1)
            } else {
                VibrationEffect.createWaveform(timings, -1)
            }
            v.vibrate(effect)
        }
    }

    private fun synthesize(pattern: BeepPattern): ShortArray {
        val total = pattern.tones.sumOf { it.durationMs * SAMPLE_RATE / 1000 }
        val out = ShortArray(total)
        var cursor = 0
        for (tone in pattern.tones) {
            val n = tone.durationMs * SAMPLE_RATE / 1000
            if (tone.frequencyHz > 0) {
                val fade = (SAMPLE_RATE * 0.006).toInt().coerceAtMost(n / 2).coerceAtLeast(1)
                for (i in 0 until n) {
                    val envelope = when {
                        i < fade -> i / fade.toDouble()
                        i > n - fade -> (n - i) / fade.toDouble()
                        else -> 1.0
                    }
                    val sample = sin(2.0 * PI * tone.frequencyHz * i / SAMPLE_RATE) * envelope * 0.7
                    out[cursor + i] = (sample * Short.MAX_VALUE).toInt().toShort()
                }
            }
            cursor += n
        }
        return out
    }

    private companion object {
        const val SAMPLE_RATE = 22_050
    }
}
