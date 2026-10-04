package com.personal.calisthenicsguide.feedback

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import com.personal.calisthenics.core.timer.CueCatalog
import com.personal.calisthenics.core.timer.CueType

/** Plays the distinct vibration pattern of each cue so the workout can be followed without looking at the screen. */
class Haptics(context: Context) {

    private val vibrator: Vibrator? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        (context.applicationContext.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager)?.defaultVibrator
    } else {
        @Suppress("DEPRECATION")
        context.applicationContext.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
    }

    @Volatile
    var enabled: Boolean = true

    fun play(type: CueType) {
        val v = vibrator ?: return
        if (!enabled || !v.hasVibrator()) return
        val pattern = CueCatalog.haptic(type)
        val timings = pattern.timingsMs.toLongArray()
        try {
            val effect = if (v.hasAmplitudeControl()) {
                VibrationEffect.createWaveform(timings, pattern.fullAmplitudes().toIntArray(), -1)
            } else {
                VibrationEffect.createWaveform(timings, -1)
            }
            v.vibrate(effect)
        } catch (_: Exception) {
            // Vibration is a convenience; never crash a workout because of it.
        }
    }

    fun cancel() {
        vibrator?.cancel()
    }
}

/** Sound and vibration for one cue, each switchable. */
class Feedback(context: Context) {
    val beeper = Beeper(context)
    val haptics = Haptics(context)

    fun cue(type: CueType) {
        beeper.play(type)
        haptics.play(type)
    }

    fun release() {
        beeper.release()
        haptics.cancel()
    }
}
