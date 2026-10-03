package com.personal.calisthenics.core.model

import java.time.DayOfWeek

/** The three rotating full-body sessions. Rotation changes the tendon stress angle each session. */
enum class WorkoutDay(
    val label: String,
    val nominalWeekday: DayOfWeek,
    val title: String,
    val focus: String,
    val pullGrip: Grip,
) {
    A("A", DayOfWeek.MONDAY, "Day A", "Pronated pull (overhand pull-ups) + vertical push (pike push-ups)", Grip.OVERHAND),
    B("B", DayOfWeek.WEDNESDAY, "Day B", "Neutral-grip pull + straight/bent-arm push (pseudo planche push-ups)", Grip.NEUTRAL),
    C("C", DayOfWeek.FRIDAY, "Day C", "Supinated pull (chin-ups) + straight bar dips", Grip.CHIN);

    /** The day that follows this one in the A -> B -> C -> A cycle. */
    fun next(): WorkoutDay = entries[(ordinal + 1) % entries.size]

    companion object {
        fun fromLabel(label: String?): WorkoutDay? = entries.firstOrNull { it.label == label }
    }
}

/** Grip used for the strict pull-up slot. */
enum class Grip(val displayName: String, val short: String) {
    OVERHAND("Overhand (pronated)", "Overhand"),
    NEUTRAL("Neutral (parallel)", "Neutral"),
    CHIN("Underhand (supinated chin-up)", "Chin-up"),
}

/** The four sequential phases of a session. */
enum class Phase(val index: Int, val title: String, val subtitle: String, val minutesLabel: String) {
    WARMUP(0, "Bulletproof Joints Warm-up", "Wrists, elbows and scapulae first", "12-15 min"),
    ISOMETRICS(1, "Isometrics & Straight-Arm Strength", "Joint-friendly static holds", "10 min"),
    STRENGTH(2, "Full Body Strength & Hypertrophy", "Log reps, RIR and progression level", "40-45 min"),
    DECOMPRESSION(3, "Loaded Stretching & Tendon Decompression", "Lengthen under gentle load", "10 min");
}

/** Used by the Tendon Safety Guard (weekly pull / push volume). */
enum class MovementCategory(val displayName: String) {
    PULL("Pull"),
    PUSH("Push"),
    LEGS("Legs"),
    CORE("Core"),
    ISOMETRIC("Isometric"),
    MOBILITY("Mobility & prep"),
}

/** Decides how the 4-stage progression matrix is worded. */
enum class ExerciseKind { REPS, HOLD }

enum class StepKind { FLOW, ISOMETRIC, STRENGTH }

/** Colour semantics for anatomy highlights: red muscle, blue tendon, yellow joint. */
enum class HighlightKind { MUSCLE, TENDON, JOINT }

/** Anatomical regions the rig knows how to highlight. */
enum class BodyRegion(val displayName: String) {
    LATS("Latissimus dorsi"),
    MID_BACK("Rhomboids & mid traps"),
    LOWER_TRAPS("Lower trapezius"),
    SERRATUS("Serratus anterior"),
    FRONT_DELT("Anterior deltoid"),
    REAR_DELT("Posterior deltoid"),
    PECS("Pectorals"),
    PEC_MINOR("Pectoralis minor"),
    BICEPS("Biceps"),
    TRICEPS("Triceps"),
    FOREARM_FLEXORS("Forearm flexors"),
    FOREARM_EXTENSORS("Forearm extensors"),
    ABS("Abdominals"),
    HIP_FLEXORS("Hip flexors"),
    GLUTES("Glutes"),
    QUADS("Quadriceps"),
    HAMSTRINGS("Hamstrings"),
    CALVES("Calves"),
    ERECTORS("Spinal erectors"),
    DISTAL_BICEPS_TENDON("Distal biceps tendon"),
    MEDIAL_ELBOW("Medial elbow (golfer's elbow origin)"),
    LATERAL_ELBOW("Lateral elbow (extensor origin)"),
    WRIST("Wrist joint"),
    SHOULDER_JOINT("Shoulder joint"),
    PATELLAR_TENDON("Patellar tendon"),
    ACHILLES("Achilles tendon"),
    HAMSTRING_TENDON("Hamstring tendons"),
    THORACOLUMBAR("Thoracolumbar fascia"),
    KNEE_JOINT("Knee joint"),
    ANKLE_JOINT("Ankle joint"),
    HIP_JOINT("Hip joint"),
}

/** How hard a region works (or how much a tendon / joint is stressed) in one exercise. Drawn as colour strength. */
enum class Load(val weight: Float, val label: String) {
    PRIMARY(1.0f, "Primary"),
    SECONDARY(0.7f, "Secondary"),
    MINOR(0.4f, "Minor"),
}

/**
 * Where in one repetition the load on a region peaks. START is Position A (first keyframe of the clip), END is
 * Position B (end of range); STEADY means the load is about the same all the way through (holds, stabilisers).
 */
enum class PeakAt { STEADY, START, END }

/**
 * One anatomy highlight: the region, its colour family ([kind]: red muscle, blue tendon, yellow joint), how strongly
 * it is loaded ([load]) and where in the rep that load peaks ([peak]). The strength drawn at a moment of the clip is
 * [strengthAt].
 */
data class Highlight(
    val region: BodyRegion,
    val kind: HighlightKind,
    val note: String = "",
    val load: Load = Load.PRIMARY,
    val peak: PeakAt = PeakAt.STEADY,
) {
    /**
     * Colour strength 0..1 when the clip is [progress] of the way from Position A (0) to Position B (1). A null
     * progress (a still picture) shows the peak. Off-peak, a highlight fades to 40% of its full strength.
     */
    fun strengthAt(progress: Float?): Float {
        val p = progress?.coerceIn(0f, 1f)
        val phase = when {
            p == null || peak == PeakAt.STEADY -> 1f
            peak == PeakAt.START -> 0.4f + 0.6f * (1f - p)
            else -> 0.4f + 0.6f * p
        }
        return load.weight * phase
    }
}

/** A visual "DO vs DON'T" card. Pose keys refer to entries in the rig library. */
data class DoDont(
    val title: String,
    val wrongLabel: String,
    val wrongText: String,
    val wrongConsequence: String,
    val rightLabel: String,
    val rightText: String,
    val wrongPoseKey: String,
    val rightPoseKey: String,
)

data class ProgressionLevel(val name: String, val description: String)

/**
 * One entry of the biomechanical encyclopedia. Every exercise has a rig animation (same id in the rig library),
 * anatomy highlights, cues, DO/DON'T cards and a progression ladder for the 4-stage gatekeeper.
 */
data class Exercise(
    val id: String,
    val name: String,
    val summary: String,
    val category: MovementCategory,
    val kind: ExerciseKind,
    val highlights: List<Highlight>,
    val setupCues: List<String>,
    val executionCues: List<String>,
    val jointProtection: String,
    val doDonts: List<DoDont>,
    val ladder: List<ProgressionLevel>,
    val startLevel: Int,
)

/** Rep cadence: eccentric - bottom pause - concentric - top pause. 'X' means an explosive concentric. */
data class Tempo(
    val eccentricSec: Int,
    val bottomPauseSec: Int,
    val concentricSec: Int,
    val topPauseSec: Int,
    val explosive: Boolean = false,
) {
    val notation: String get() = "$eccentricSec-$bottomPauseSec-${if (explosive) "X" else concentricSec.toString()}-$topPauseSec"
    val repSeconds: Int get() = eccentricSec + bottomPauseSec + concentricSec + topPauseSec

    companion object {
        /** Parses "3-1-X-0" style notation. */
        fun parse(text: String): Tempo {
            val parts = text.trim().split("-")
            require(parts.size == 4) { "Tempo must have four parts: $text" }
            val explosive = parts[2].equals("X", ignoreCase = true)
            return Tempo(
                eccentricSec = parts[0].toInt(),
                bottomPauseSec = parts[1].toInt(),
                concentricSec = if (explosive) 1 else parts[2].toInt(),
                topPauseSec = parts[3].toInt(),
                explosive = explosive,
            )
        }
    }
}

/** One timed drill inside a flow list (warm-up and decompression). [subLabels] split the time evenly. */
data class FlowPart(val label: String, val seconds: Int, val instruction: String, val subLabels: List<String> = emptyList())

/**
 * A prescription slot in the session. Phases 1 and 2 carry the spec's numbering (1-9).
 * [exerciseByDay] / [titleByDay] describe day-dependent variants.
 */
data class WorkoutStep(
    val id: String,
    val number: Int?,
    val phase: Phase,
    val exerciseId: String,
    val title: String,
    val kind: StepKind,
    val sets: Int,
    val repsMin: Int? = null,
    val repsMax: Int? = null,
    val holdMinSec: Int? = null,
    val holdMaxSec: Int? = null,
    val tempo: Tempo? = null,
    val restMinSec: Int = 0,
    val restMaxSec: Int = 0,
    val perSide: Boolean = false,
    val cue: String = "",
    val flowParts: List<FlowPart> = emptyList(),
    val exerciseByDay: Map<WorkoutDay, String> = emptyMap(),
    val titleByDay: Map<WorkoutDay, String> = emptyMap(),
    val partnerStepId: String? = null,
) {
    fun exerciseFor(day: WorkoutDay): String = exerciseByDay[day] ?: exerciseId
    fun titleFor(day: WorkoutDay): String = titleByDay[day] ?: title
}
