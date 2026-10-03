package com.personal.calisthenics.core.rig

import kotlin.math.sqrt

/** Floor-based work: kneeling wrist prep, plank-family drills and pike push-ups. */
internal object FloorRigs {

    private val floor = RigScene()
    private fun kf(p: Pose, hold: Long = 500, move: Long = 1100) = Keyframe(p, hold, move)

    /** Straight-arm shoulder height above wrists that are [dz] behind the shoulder joint. */
    private fun armY(dz: Float, wristY: Float = Kit.WRIST_FLOOR) = wristY + sqrt(Kit.ARM * Kit.ARM - dz * dz)

    fun all(): Map<String, ExerciseRig> = mapOf(
        "first_knuckle_raises" to firstKnuckleRaises(),
        "wrist_extensor_leans" to wristExtensorLeans(),
        "palms_back_flexor_stretch" to palmsBackFlexorStretch(),
        "dorsal_wrist_pushups" to dorsalWristPushups(),
        "scapular_pushups" to scapularPushups(),
        "planche_lean" to plancheLean(),
        "pseudo_planche_pushups" to pseudoPlanchePushups(),
        "pike_pushups" to pikePushups(),
    )

    // ------------------------------------------------------------------ kneeling wrist prep

    private fun firstKnuckleRaises(): ExerciseRig {
        val a = Kit.kneeling(jointY = armY(0f), jointZ = 52f, wristZ = 52f)
        val b = Kit.kneeling(jointY = armY(0f, 9f), jointZ = 52f, wristZ = 52f, wristY = 9f, handPitch = 40f)
        val wrong = Kit.kneeling(jointY = 36f, jointZ = 52f, wristZ = 52f, head = 20f)
        return ExerciseRig(
            RigAnimation(floor, listOf(kf(a, 400, 900), kf(b, 600, 900))),
            mapOf(
                "first_knuckle_raises.wrong_bent_elbows" to RigStill(floor, wrong),
                "first_knuckle_raises.right_locked" to RigStill(floor, b),
            ),
        )
    }

    private fun wristExtensorLeans(): ExerciseRig {
        val a = Kit.kneeling(jointY = armY(0f), jointZ = 52f, wristZ = 52f)
        val b = Kit.kneeling(jointY = armY(12f), jointZ = 64f, wristZ = 52f)
        val wrong = Kit.kneeling(jointY = armY(12f, 10f), jointZ = 64f, wristZ = 52f, wristY = 10f, handPitch = 35f)
        return ExerciseRig(
            RigAnimation(floor, listOf(kf(a, 400, 1100), kf(b, 2000, 1100))),
            mapOf(
                "wrist_extensor_leans.wrong_palm_lifting" to RigStill(floor, wrong),
                "wrist_extensor_leans.right_flat_palm" to RigStill(floor, b),
            ),
        )
    }

    private fun palmsBackFlexorStretch(): ExerciseRig {
        val a = Kit.kneeling(jointY = armY(0f), jointZ = 52f, wristZ = 52f, handPitch = 180f, handRoll = 180f)
        val b = Kit.kneeling(jointY = 45.5f, jointZ = 15f, wristZ = 52f, handPitch = 180f, handRoll = 180f, head = 10f)
        val wrong = Kit.kneeling(jointY = 31f, jointZ = 22f, wristZ = 52f, handPitch = 180f, handRoll = 180f, head = 5f, armPole = Poles.OUT_BACK)
        return ExerciseRig(
            RigAnimation(floor, listOf(kf(a, 300, 1200), kf(b, 700, 1200))),
            mapOf(
                "palms_back_flexor_stretch.wrong_forced" to RigStill(floor, wrong),
                "palms_back_flexor_stretch.right_slow_pulses" to RigStill(floor, b),
            ),
        )
    }

    private fun dorsalWristPushups(): ExerciseRig {
        val light = Kit.kneeling(jointY = armY(0f), jointZ = 10.3f, wristZ = 10.3f, handPitch = 180f, wristY = 6f, head = 18f)
        val more = Kit.kneeling(jointY = armY(7.7f, 6f), jointZ = 18f, wristZ = 10.3f, handPitch = 180f, wristY = 6f, head = 18f)
        val wrong = Kit.kneeling(jointY = armY(12f, 6f), jointZ = 64f, wristZ = 52f, wristY = 6f, handPitch = 180f, head = 14f)
        return ExerciseRig(
            RigAnimation(floor, listOf(kf(light, 500, 1000), kf(more, 500, 1000))),
            mapOf(
                "dorsal_wrist_pushups.wrong_heavy_load" to RigStill(floor, wrong),
                "dorsal_wrist_pushups.right_light_load" to RigStill(floor, light),
            ),
        )
    }

    // ------------------------------------------------------------------ plank family

    private fun scapularPushups(): ExerciseRig {
        val protracted = Kit.plank(jointY = armY(0f), jointZ = 0f, wristZ = 0f, shrug = -3f, flex = -6f, head = 10f)
        val retracted = Kit.plank(jointY = armY(0f), jointZ = 0f, wristZ = 0f, shrug = 3f, flex = 5f, head = 10f)
        val wrong = Kit.plank(jointY = 38f, jointZ = 0f, wristZ = 0f, head = 10f)
        return ExerciseRig(
            RigAnimation(floor, listOf(kf(protracted, 1000, 1000), kf(retracted, 500, 1000))),
            mapOf(
                "scapular_pushups.wrong_elbow_bend" to RigStill(floor, wrong),
                "scapular_pushups.right_protracted" to RigStill(floor, protracted),
            ),
        )
    }

    private fun plancheLean(): ExerciseRig {
        val light = Kit.plank(jointY = armY(4f), jointZ = 4f, wristZ = 0f, shrug = -3f, flex = -6f, head = 10f)
        val deep = Kit.plank(jointY = armY(16f), jointZ = 16f, wristZ = 0f, shrug = -3f, flex = -6f, head = 10f)
        val wrong = Kit.plank(
            jointY = armY(16f), jointZ = 16f, wristZ = 0f, shrug = 3f, flex = 7f, head = 10f,
            reach = 137.5f, hipAbove = false,
        )
        return ExerciseRig(
            RigAnimation(floor, listOf(kf(light, 500, 1400), kf(deep, 1500, 1400))),
            mapOf(
                "planche_lean.wrong_arched" to RigStill(floor, wrong),
                "planche_lean.right_tucked_protracted" to RigStill(floor, deep),
            ),
        )
    }

    private fun pseudoPlanchePushups(): ExerciseRig {
        val top = Kit.plank(jointY = armY(26f), jointZ = 26f, wristZ = 0f, shrug = -2f, flex = -4f, head = 10f, armPole = Poles.BACK)
        val bottom = Kit.plank(jointY = 30f, jointZ = 30f, wristZ = 0f, shrug = 0f, head = 10f, armPole = Poles.BACK)
        val wrongTop = Kit.plank(jointY = armY(0f), jointZ = 0f, wristZ = 0f, head = 10f)
        return ExerciseRig(
            RigAnimation(floor, listOf(kf(top, 300, 1500), kf(bottom, 500, 1000))),
            mapOf(
                "pseudo_planche_pushups.wrong_no_lean" to RigStill(floor, wrongTop),
                "pseudo_planche_pushups.right_leaning" to RigStill(floor, top),
            ),
        )
    }

    // ------------------------------------------------------------------ pike push-up

    private fun pikePose(shoulderZ: Float, shoulderY: Float, shrug: Float, head: Float, look: Float, pole: PoleSpec): Pose {
        val shoulder = Vec3(0f, shoulderY, shoulderZ)
        val ankle = Vec3(0f, 11f, -83f)
        val lean = Kit.leanFor(shoulder, ankle, 0f, Kit.LEG, hipAbove = true)
        return Kit.shoulderPose(
            shoulder, lean, 0f, head, shrug, look,
            hands = sym(19f, Kit.WRIST_FLOOR, 0f, pole, 0f, true, hand = HandShape.FLAT),
            feet = Kit.feet(ankle, 9f, Poles.FWD, 20f, contact = true),
        )
    }

    private fun pikePushups(): ExerciseRig {
        val top = pikePose(shoulderZ = 0f, shoulderY = 55.5f, shrug = 4f, head = 14f, look = 0f, pole = Poles.OUT_BACK)
        val bottom = pikePose(shoulderZ = 9f, shoulderY = 36f, shrug = 0f, head = 14f, look = 15f, pole = Poles.OUT_BACK)
        val wrong = pikePose(shoulderZ = -2f, shoulderY = 36f, shrug = 0f, head = 14f, look = 0f, pole = Poles.OUT)
        return ExerciseRig(
            RigAnimation(floor, listOf(kf(top, 300, 1500), kf(bottom, 400, 1000))),
            mapOf(
                "pike_pushups.wrong_flare_head_between" to RigStill(floor, wrong),
                "pike_pushups.right_tripod" to RigStill(floor, bottom),
            ),
        )
    }
}
