package com.personal.calisthenics.core.timer

/** Every audible / tactile event the timers can emit. */
enum class CueType {
    // Generic countdown
    COUNT_TICK,
    COUNT_FINAL,

    // Isometric hold with get-ready buffer
    GET_READY_START,
    HOLD_GO,
    HOLD_MIN_REACHED,
    HOLD_STOP,

    // Tempo metronome
    TEMPO_LOWER,
    TEMPO_PAUSE_BOTTOM,
    TEMPO_DRIVE,
    TEMPO_PAUSE_TOP,
    SET_DONE,

    // Rest
    REST_START,
    REST_WARNING,
    REST_DONE,

    // Flow (warm-up / stretching)
    FLOW_TRANSITION,
    FLOW_DRILL_START,
    FLOW_SUBSTEP,
    FLOW_DONE,
}

/** One sine beep. [frequencyHz] = 0 produces silence of [durationMs]. */
data class Tone(val frequencyHz: Int, val durationMs: Int)

data class BeepPattern(val tones: List<Tone>) {
    val totalMs: Int get() = tones.sumOf { it.durationMs }
}

/**
 * Vibration timings in the Android waveform format: [delay, on, off, on, ...] in milliseconds,
 * always ending with an "on" entry. [amplitudes] holds one 1..255 value per "on" entry (pulse).
 */
data class HapticPattern(val timingsMs: List<Long>, val amplitudes: List<Int>) {
    init {
        require(timingsMs.isNotEmpty() && timingsMs.size % 2 == 0) { "Timings must be [delay, on, off, on, ...] ending on 'on'" }
        require(amplitudes.size == timingsMs.size / 2) { "One amplitude per vibration pulse" }
        require(amplitudes.all { it in 1..255 }) { "Amplitudes must be 1..255" }
    }

    val pulseCount: Int get() = amplitudes.size

    /** Per-entry amplitudes as Android's createWaveform expects: 0 for delay/off entries. */
    fun fullAmplitudes(): List<Int> {
        val result = ArrayList<Int>(timingsMs.size)
        var pulse = 0
        for (index in timingsMs.indices) {
            if (index % 2 == 1) {
                result.add(amplitudes[pulse])
                pulse++
            } else {
                result.add(0)
            }
        }
        return result
    }
}

/** Distinct sound + vibration signature for each cue so you can tell them apart without looking. */
object CueCatalog {

    private fun beep(vararg tones: Tone) = BeepPattern(tones.toList())
    private fun t(hz: Int, ms: Int) = Tone(hz, ms)
    private fun silence(ms: Int) = Tone(0, ms)

    // timings: delay, on, off, on...  (always ends on an "on" entry)
    private fun haptic(timings: List<Long>, amplitudes: List<Int>) = HapticPattern(timings, amplitudes)

    fun beep(type: CueType): BeepPattern = when (type) {
        CueType.COUNT_TICK -> beep(t(880, 90))
        CueType.COUNT_FINAL -> beep(t(1175, 130))
        CueType.GET_READY_START -> beep(t(660, 80), silence(70), t(660, 80))
        CueType.HOLD_GO -> beep(t(1568, 380))
        CueType.HOLD_MIN_REACHED -> beep(t(1047, 70), silence(60), t(1319, 70))
        CueType.HOLD_STOP -> beep(t(523, 260), silence(80), t(392, 340))
        CueType.TEMPO_LOWER -> beep(t(440, 70))
        CueType.TEMPO_PAUSE_BOTTOM -> beep(t(587, 70))
        CueType.TEMPO_DRIVE -> beep(t(988, 90))
        CueType.TEMPO_PAUSE_TOP -> beep(t(784, 70))
        CueType.SET_DONE -> beep(t(784, 110), silence(60), t(988, 110), silence(60), t(1319, 220))
        CueType.REST_START -> beep(t(392, 100))
        CueType.REST_WARNING -> beep(t(740, 160))
        CueType.REST_DONE -> beep(t(784, 130), silence(60), t(988, 130), silence(60), t(1319, 260))
        CueType.FLOW_TRANSITION -> beep(t(587, 90), silence(70), t(740, 90))
        CueType.FLOW_DRILL_START -> beep(t(1319, 260))
        CueType.FLOW_SUBSTEP -> beep(t(698, 70))
        CueType.FLOW_DONE -> beep(t(659, 150), silence(70), t(880, 150), silence(70), t(1175, 300))
    }

    fun haptic(type: CueType): HapticPattern = when (type) {
        CueType.COUNT_TICK -> haptic(listOf(0, 40), listOf(120))
        CueType.COUNT_FINAL -> haptic(listOf(0, 70), listOf(200))
        CueType.GET_READY_START -> haptic(listOf(0, 60, 80, 60), listOf(150, 150))
        CueType.HOLD_GO -> haptic(listOf(0, 450), listOf(255))
        CueType.HOLD_MIN_REACHED -> haptic(listOf(0, 50, 60, 50, 60, 50), listOf(180, 180, 180))
        CueType.HOLD_STOP -> haptic(listOf(0, 250, 90, 250), listOf(255, 200))
        CueType.TEMPO_LOWER -> haptic(listOf(0, 30), listOf(90))
        CueType.TEMPO_PAUSE_BOTTOM -> haptic(listOf(0, 45), listOf(130))
        CueType.TEMPO_DRIVE -> haptic(listOf(0, 90), listOf(255))
        CueType.TEMPO_PAUSE_TOP -> haptic(listOf(0, 35), listOf(110))
        CueType.SET_DONE -> haptic(listOf(0, 120, 70, 120, 70, 260), listOf(200, 200, 255))
        CueType.REST_START -> haptic(listOf(0, 80), listOf(100))
        CueType.REST_WARNING -> haptic(listOf(0, 150, 100, 150), listOf(160, 160))
        CueType.REST_DONE -> haptic(listOf(0, 200, 90, 200, 90, 450), listOf(220, 220, 255))
        CueType.FLOW_TRANSITION -> haptic(listOf(0, 80, 70, 80), listOf(140, 140))
        CueType.FLOW_DRILL_START -> haptic(listOf(0, 300), listOf(230))
        CueType.FLOW_SUBSTEP -> haptic(listOf(0, 35), listOf(100))
        CueType.FLOW_DONE -> haptic(listOf(0, 150, 80, 150, 80, 500), listOf(200, 200, 255))
    }
}
