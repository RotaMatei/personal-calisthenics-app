package com.personal.calisthenics.core.rig

/** Standing warm-up drills: jumping jacks, joint circles, band dislocates and band pull-aparts. */
internal object PrepRigs {

    private val floor = RigScene()
    private val bandScene = RigScene(band = true)

    private val armsDown = sym(23f, 96f, 1f, Poles.BACK, 90f)
    private fun kf(p: Pose, hold: Long = 300, move: Long = 700) = Keyframe(p, hold, move)

    fun all(): Map<String, ExerciseRig> = mapOf(
        "jumping_jacks" to jumpingJacks(),
        "joint_circles" to jointCircles(),
        "banded_dislocates" to bandedDislocates(),
        "band_pull_aparts" to bandPullAparts(),
    )

    // ------------------------------------------------------------------ jumping jacks

    private fun jumpingJacks(): ExerciseRig {
        val a = Kit.standing(hands = armsDown)
        val b = Kit.standing(
            hip = Vec3(0f, 91.5f, 0f),
            hands = sym(36f, 196f, 0f, Poles.UP_OUT, -80f),
            feet = sym(33f, 8f, 0f, Poles.FWD_OUT, 0f, true),
        )
        val wrong = Kit.standing(hands = armsDown)
        val right = Kit.standing(
            hip = Vec3(0f, 82f, 2f),
            lean = 6f,
            hands = sym(26f, 100f, 14f, Poles.BACK, 80f),
            feet = sym(9f, 12.5f, 0f, Poles.FWD, 28f, true),
        )
        return ExerciseRig(
            RigAnimation(floor, listOf(kf(a, 350, 450), kf(b, 350, 450))),
            mapOf(
                "jumping_jacks.wrong_stiff_landing" to RigStill(floor, wrong),
                "jumping_jacks.right_soft_landing" to RigStill(floor, right),
            ),
        )
    }

    // ------------------------------------------------------------------ joint circles

    private fun jointCircles(): ExerciseRig {
        val front = sym(22f, 146f, 46f, Poles.UP, -10f)
        val frontL = front.first
        val frontR = front.second
        val backDown = sym(24f, 100f, -28f, Poles.BACK, 100f)
        val a = Kit.standing(hands = Limb(frontL.target, frontL.pole, -10f) to Limb(backDown.second.target, backDown.second.pole, 100f))
        val b = Kit.standing(hands = Limb(backDown.first.target, backDown.first.pole, 100f) to Limb(frontR.target, frontR.pole, -10f), head = 8f)
        val wrong = Kit.standing(hands = armsDown, head = -32f, look = 38f)
        val right = Kit.standing(hands = armsDown, head = 6f)
        return ExerciseRig(
            RigAnimation(floor, listOf(kf(a, 200, 1200), kf(b, 200, 1200))),
            mapOf(
                "joint_circles.wrong_neck_hyperextend" to RigStill(floor, wrong),
                "joint_circles.right_neck_neutral" to RigStill(floor, right),
            ),
        )
    }

    // ------------------------------------------------------------------ band dislocates

    private fun bandedDislocates(): ExerciseRig {
        fun p(y: Float, z: Float) = Kit.standing(hands = sym(40f, y, z, Poles.BACK_DOWN, 70f))
        val low = p(100f, 14f)
        val frontHigh = p(148f, 50f)
        val overhead = Kit.standing(hands = sym(40f, 195f, 8f, Poles.BACK, -80f))
        val behindHigh = p(148f, -50f)
        val behindLow = p(103f, -17f)
        val frames = listOf(
            kf(low, 100, 600), kf(frontHigh, 0, 500), kf(overhead, 0, 500), kf(behindHigh, 0, 500),
            kf(behindLow, 200, 500), kf(behindHigh, 0, 500), kf(overhead, 0, 500), kf(frontHigh, 0, 500),
        )
        val wrong = Kit.standing(
            hip = Vec3(0f, Kit.STAND_HIP_Y, 5f), lean = -8f, flex = -9f,
            hands = sym(30f, 186f, -6f, Poles.OUT_BACK, -70f),
        )
        val right = Kit.standing(hands = sym(40f, 195f, 8f, Poles.BACK, -80f))
        return ExerciseRig(
            RigAnimation(bandScene, frames),
            mapOf(
                "banded_dislocates.wrong_ribs_flared" to RigStill(bandScene, wrong),
                "banded_dislocates.right_ribs_down" to RigStill(bandScene, right),
            ),
        )
    }

    // ------------------------------------------------------------------ band pull-aparts

    private fun bandPullAparts(): ExerciseRig {
        val a = Kit.standing(shrug = -1f, hands = sym(14f, 147f, 52f, Poles.OUT_DOWN, 0f), head = 3f)
        val b = Kit.standing(shrug = -2f, hands = sym(72f, 147f, 12f, Poles.BACK, 0f), head = 3f)
        val wrong = Kit.standing(
            hip = Vec3(0f, Kit.STAND_HIP_Y, 4f), lean = -7f, flex = -7f, shrug = 6f,
            hands = sym(72f, 150f, 8f, Poles.BACK, 0f), head = 6f,
        )
        return ExerciseRig(
            RigAnimation(bandScene, listOf(kf(a, 300, 900), kf(b, 800, 900))),
            mapOf(
                "band_pull_aparts.wrong_shrug" to RigStill(bandScene, wrong),
                "band_pull_aparts.right_depressed" to RigStill(bandScene, b),
            ),
        )
    }
}
