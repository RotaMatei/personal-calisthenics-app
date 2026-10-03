package com.personal.calisthenics.core.seed

import com.personal.calisthenics.core.model.FlowPart
import com.personal.calisthenics.core.model.Phase
import com.personal.calisthenics.core.model.StepKind
import com.personal.calisthenics.core.model.Tempo
import com.personal.calisthenics.core.model.WorkoutDay
import com.personal.calisthenics.core.model.WorkoutStep

/**
 * The pre-loaded full-body session (about 70-75 minutes).
 * Phase 1 and 2 steps carry the spec numbering 1-9; the cold-weather mode pairs 3 with 4 and 5 with 6.
 */
internal object WorkoutSeed {

    // ---------------------------------------------------------------- Phase 0: Bulletproof Joints warm-up
    private val warmup: List<WorkoutStep> = listOf(
        flowStep(
            id = "w0_jacks", exerciseId = "jumping_jacks", title = "Jumping Jacks", phase = Phase.WARMUP,
            cue = "Raise your core temperature with a steady rhythm and soft landings.",
            parts = listOf(FlowPart("Jumping Jacks", 45, "Steady rhythm, land softly on the balls of your feet.")),
        ),
        flowStep(
            id = "w0_circles", exerciseId = "joint_circles", title = "Neck, Shoulder, Elbow, Hip & Ankle Circles", phase = Phase.WARMUP,
            cue = "About 5 slow circles each way per joint, head to toe.",
            parts = listOf(
                FlowPart(
                    "Joint Circles", 50, "Large, smooth circles; keep the rest of the body quiet.",
                    subLabels = listOf("Neck (half circles)", "Shoulders", "Elbows", "Hips", "Ankles"),
                ),
            ),
        ),
        flowStep(
            id = "w0_dislocates", exerciseId = "banded_dislocates", title = "20x Banded Shoulder Dislocates", phase = Phase.WARMUP,
            cue = "Elbows locked, ribs down; widen the grip until the pass is smooth.",
            parts = listOf(FlowPart("Banded Shoulder Dislocates x20", 35, "Straight arms, ribs down, slow sweep overhead and behind.")),
        ),
        flowStep(
            id = "w0_pullaparts", exerciseId = "band_pull_aparts", title = "20x Band Pull-Aparts", phase = Phase.WARMUP,
            cue = "Squeeze the shoulder blades back and down; no shrugging.",
            parts = listOf(FlowPart("Band Pull-Aparts x20", 35, "Straight arms, pull from the shoulder blades, control the return.")),
        ),
        flowStep(
            id = "w0_knuckles", exerciseId = "first_knuckle_raises", title = "First Knuckle Raises / Palm Pulses (15 reps)", phase = Phase.WARMUP,
            cue = "Floor wrist prep with elbows locked.",
            parts = listOf(FlowPart("First Knuckle Raises x15", 45, "Elbows locked; roll the palm heels up and lower with control.")),
        ),
        flowStep(
            id = "w0_extensor", exerciseId = "wrist_extensor_leans", title = "Forward Wrist Extensor Leans (10 reps x 2 s hold)", phase = Phase.WARMUP,
            cue = "Floor wrist prep with elbows locked.",
            parts = listOf(FlowPart("Wrist Extensor Leans x10", 50, "Lean the shoulders past the wrists, hold 2 s, rock back.")),
        ),
        flowStep(
            id = "w0_flexor", exerciseId = "palms_back_flexor_stretch", title = "Palms-Back Flexor Stretch (10 slow pulses)", phase = Phase.WARMUP,
            cue = "Floor wrist prep with elbows locked.",
            parts = listOf(FlowPart("Palms-Back Flexor Pulses x10", 40, "Fingers back toward the knees; rock the hips slowly toward the heels.")),
        ),
        flowStep(
            id = "w0_dorsal", exerciseId = "dorsal_wrist_pushups", title = "Dorsal Wrist Push-ups at 15% bodyweight (10 reps)", phase = Phase.WARMUP,
            cue = "Floor wrist prep with elbows locked.",
            parts = listOf(FlowPart("Dorsal Wrist Push-ups x10", 45, "Back of the hands on the floor, clench and release the fists.")),
        ),
        flowStep(
            id = "w0_hang", exerciseId = "passive_dead_hang", title = "Passive Dead Hang (15 s)", phase = Phase.WARMUP,
            cue = "Scapular and elbow activation starts here.",
            parts = listOf(FlowPart("Passive Dead Hang", 15, "Hook grip, relaxed shoulders, breathe.")),
        ),
        flowStep(
            id = "w0_scap_pullups", exerciseId = "scapular_pullups", title = "Scapular Pull-ups (10 reps x 2 s pause at top)", phase = Phase.WARMUP,
            cue = "Straight arms; only the shoulder blades move.",
            parts = listOf(FlowPart("Scapular Pull-ups x10", 60, "Depress the shoulder blades, hold 2 s at the top, lower slowly.")),
        ),
        flowStep(
            id = "w0_scap_dips", exerciseId = "scapular_dips", title = "Parallel Bar Support Hold + Scapular Dips (10 reps)", phase = Phase.WARMUP,
            cue = "Locked elbows for every rep.",
            parts = listOf(
                FlowPart(
                    "Support Hold + Scapular Dips x10", 60, "Hold the support, then dip and press the shoulder blades only.",
                    subLabels = listOf("Support hold: lock the elbows", "Scapular dips x10"),
                ),
            ),
        ),
        flowStep(
            id = "w0_scap_pushups", exerciseId = "scapular_pushups", title = "Scapular Push-ups (10 reps, full protraction)", phase = Phase.WARMUP,
            cue = "Finish with full protraction at the top.",
            parts = listOf(FlowPart("Scapular Push-ups x10", 50, "High plank, elbows locked, round the upper back at the top.")),
        ),
    )

    // ---------------------------------------------------------------- Phase 1: Isometrics
    private val isometrics: List<WorkoutStep> = listOf(
        WorkoutStep(
            id = "p1_lsit", number = 1, phase = Phase.ISOMETRICS, exerciseId = "l_sit",
            title = "Parallel Bar L-Sit / Tuck L-Sit", kind = StepKind.ISOMETRIC, sets = 3,
            holdMinSec = 15, holdMaxSec = 20, restMinSec = 90, restMaxSec = 90,
            cue = "Lock elbows completely, push shoulders down away from ears (scapular depression).",
        ),
        WorkoutStep(
            id = "p1_planche", number = 2, phase = Phase.ISOMETRICS, exerciseId = "planche_lean",
            title = "Planche Lean", kind = StepKind.ISOMETRIC, sets = 3,
            holdMinSec = 15, holdMaxSec = 20, restMinSec = 90, restMaxSec = 90,
            cue = "Straight arms, posterior pelvic tilt (tuck tailbone), protract scapula, lean shoulders past wrists.",
        ),
    )

    // ---------------------------------------------------------------- Phase 2: Strength & hypertrophy
    private val strength: List<WorkoutStep> = listOf(
        WorkoutStep(
            id = "p2_pullups", number = 3, phase = Phase.STRENGTH, exerciseId = "strict_pullups",
            title = "Strict Pull-ups", kind = StepKind.STRENGTH, sets = 4, repsMin = 6, repsMax = 10,
            tempo = Tempo.parse("3-1-X-0"), restMinSec = 150, restMaxSec = 180,
            cue = "Hook grip. Lower for 3 s, pause 1 s in the hang, drive up explosively.",
            titleByDay = mapOf(
                WorkoutDay.A to "Strict Pull-ups (Overhand)",
                WorkoutDay.B to "Strict Pull-ups (Neutral Grip)",
                WorkoutDay.C to "Chin-ups (Supinated)",
            ),
            partnerStepId = "p2_dips",
        ),
        WorkoutStep(
            id = "p2_dips", number = 4, phase = Phase.STRENGTH, exerciseId = "parallel_bar_dips",
            title = "Parallel Bar Dips", kind = StepKind.STRENGTH, sets = 4, repsMin = 8, repsMax = 12,
            tempo = Tempo.parse("3-0-1-0"), restMinSec = 120, restMaxSec = 150,
            cue = "Lock out with depressed shoulders; stop at parallel.",
            exerciseByDay = mapOf(WorkoutDay.C to "straight_bar_dips"),
            titleByDay = mapOf(WorkoutDay.C to "Straight Bar Dips"),
            partnerStepId = "p2_pullups",
        ),
        WorkoutStep(
            id = "p2_aus", number = 5, phase = Phase.STRENGTH, exerciseId = "australian_pullups",
            title = "Elevated Australian Pull-ups (Bodyweight Rows)", kind = StepKind.STRENGTH, sets = 3,
            repsMin = 8, repsMax = 12, tempo = Tempo.parse("2-0-1-1"), restMinSec = 120, restMaxSec = 120,
            cue = "1 s chest-to-bar squeeze at the top of every rep.",
            partnerStepId = "p2_pike",
        ),
        WorkoutStep(
            id = "p2_pike", number = 6, phase = Phase.STRENGTH, exerciseId = "pike_pushups",
            title = "Pike Push-ups", kind = StepKind.STRENGTH, sets = 3, repsMin = 6, repsMax = 10,
            tempo = Tempo.parse("3-1-1-0"), restMinSec = 120, restMaxSec = 120,
            cue = "Head and hands form an equilateral tripod triangle; elbows about 45 degrees.",
            exerciseByDay = mapOf(WorkoutDay.B to "pseudo_planche_pushups"),
            titleByDay = mapOf(WorkoutDay.B to "Pseudo Planche Push-ups"),
            partnerStepId = "p2_aus",
        ),
        WorkoutStep(
            id = "p2_legs", number = 7, phase = Phase.STRENGTH, exerciseId = "pistol_squat",
            title = "Pistol Squat Progression / Bulgarian Split Squats", kind = StepKind.STRENGTH, sets = 3,
            repsMin = 6, repsMax = 8, tempo = Tempo.parse("3-1-1-0"), restMinSec = 60, restMaxSec = 60,
            perSide = true, cue = "Heel down, knee over the middle toes. Rest 60 s between legs.",
        ),
        WorkoutStep(
            id = "p2_nordic", number = 8, phase = Phase.STRENGTH, exerciseId = "nordic_curl",
            title = "Assisted Nordic Hamstring Curls / Single-Leg Hip Thrust", kind = StepKind.STRENGTH, sets = 3,
            repsMin = 8, repsMax = 10, tempo = Tempo.parse("4-0-1-0"), restMinSec = 90, restMaxSec = 90,
            cue = "4 s eccentric lowering with a straight line from knees to head.",
        ),
        WorkoutStep(
            id = "p2_raises", number = 9, phase = Phase.STRENGTH, exerciseId = "hanging_leg_raises",
            title = "Hanging Leg Raises / Toes-to-Bar", kind = StepKind.STRENGTH, sets = 3,
            repsMin = 8, repsMax = 12, tempo = Tempo.parse("3-0-1-0"), restMinSec = 90, restMaxSec = 90,
            cue = "3 s strict lowering with zero momentum.",
        ),
    )

    // ---------------------------------------------------------------- Phase 3: Loaded stretching & decompression
    private val decompression: List<WorkoutStep> = listOf(
        flowStep(
            id = "p3_german", exerciseId = "german_hang", title = "German Hang Progression (2 x 30 s)", phase = Phase.DECOMPRESSION, sets = 2,
            cue = "Targets the distal biceps tendon, anterior deltoid and pec minor.",
            parts = listOf(
                FlowPart("German Hang, set 1 of 2", 30, "Feet-assisted skin the cat on a low bar; keep the shoulders active."),
                FlowPart("German Hang, set 2 of 2", 30, "Feet-assisted skin the cat on a low bar; keep the shoulders active."),
            ),
        ),
        flowStep(
            id = "p3_hang", exerciseId = "passive_dead_hang", title = "Passive Dead Hang with Parasympathetic Breathing (2 x 45 s)", phase = Phase.DECOMPRESSION, sets = 2,
            cue = "Spinal and shoulder decompression with a slow 4 s exhale.",
            parts = listOf(
                FlowPart("Passive Dead Hang, set 1 of 2", 45, "Relax the shoulders; breathe out slowly for 4 seconds."),
                FlowPart("Passive Dead Hang, set 2 of 2", 45, "Relax the shoulders; breathe out slowly for 4 seconds."),
            ),
        ),
        flowStep(
            id = "p3_forearm", exerciseId = "forearm_flexor_extensor_stretch", title = "Locked-Elbow Forearm Flexor & Extensor Stretch (30 s each)", phase = Phase.DECOMPRESSION, sets = 1,
            cue = "Direct golfer's elbow prevention. Keep the elbow locked.",
            parts = listOf(
                FlowPart("Flexor Stretch, left arm", 30, "Palm up, elbow locked, draw the fingers back gently."),
                FlowPart("Flexor Stretch, right arm", 30, "Palm up, elbow locked, draw the fingers back gently."),
                FlowPart("Extensor Stretch, left arm", 30, "Palm down, elbow locked, draw the fingers toward you gently."),
                FlowPart("Extensor Stretch, right arm", 30, "Palm down, elbow locked, draw the fingers toward you gently."),
            ),
        ),
        flowStep(
            id = "p3_squat", exerciseId = "prying_deep_squat", title = "Prying Deep Squat Hold (90 s)", phase = Phase.DECOMPRESSION, sets = 1,
            cue = "Continuous ankle dorsiflexion and Achilles / patellar tendon mobilization.",
            parts = listOf(FlowPart("Prying Deep Squat Hold", 90, "Heels flat, elbows prying the knees apart, breathe slowly.")),
        ),
        flowStep(
            id = "p3_jefferson", exerciseId = "jefferson_curl", title = "Bodyweight Jefferson Curl off a Bench (2 x 30 s)", phase = Phase.DECOMPRESSION, sets = 2,
            cue = "Hamstring tendons and thoracolumbar fascia, one vertebra at a time.",
            parts = listOf(
                FlowPart("Jefferson Curl, set 1 of 2", 30, "Roll down vertebra by vertebra, head first, no added weight."),
                FlowPart("Jefferson Curl, set 2 of 2", 30, "Roll down vertebra by vertebra, head first, no added weight."),
            ),
        ),
    )

    val all: List<WorkoutStep> = warmup + isometrics + strength + decompression

    private fun flowStep(
        id: String,
        exerciseId: String,
        title: String,
        phase: Phase,
        cue: String,
        parts: List<FlowPart>,
        sets: Int = 1,
    ) = WorkoutStep(
        id = id,
        number = null,
        phase = phase,
        exerciseId = exerciseId,
        title = title,
        kind = StepKind.FLOW,
        sets = sets,
        cue = cue,
        flowParts = parts,
    )
}
