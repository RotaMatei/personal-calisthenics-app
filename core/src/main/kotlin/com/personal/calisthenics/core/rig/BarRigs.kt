package com.personal.calisthenics.core.rig

import kotlin.math.sqrt

/** Pull-up bar, parallel bar, dip bar and low-bar exercises. */
internal object BarRigs {

    // ------------------------------------------------------------------ scenes

    private const val PULL_BAR_Y = 230f
    private const val ROW_BAR_Y = 100f
    private const val DIP_BAR_Y = 112f
    private const val SUPPORT_WRIST_Y = DIP_BAR_Y + Kit.HOOK_BACK
    private const val PARALLEL_WRIST_X = 19.5f
    private const val PARALLEL_BAR_X = PARALLEL_WRIST_X + Kit.HOOK_DROP
    private const val DIP_WRIST_Z = -3f

    private fun post(x: Float, z: Float, top: Float) = Box(Vec3(x - 2.5f, 0f, z - 3f), Vec3(x + 2.5f, top, z + 3f))

    private fun crossBar(y: Float, z: Float = 0f, r: Float = 2.2f, half: Float = 62f) = listOf(
        Cylinder(Vec3(-half, y, z), Vec3(half, y, z), r),
        post(-half - 3f, z, y + 4f),
        post(half + 3f, z, y + 4f),
    )

    private val pullScene = RigScene(equipment = crossBar(PULL_BAR_Y, z = Kit.HOOK_BACK))
    private val rowScene = RigScene(
        equipment = crossBar(ROW_BAR_Y, z = Kit.HOOK_BACK) + listOf(Box(Vec3(-35f, 0f, -168f), Vec3(35f, 45f, -118f))),
    )
    private val dipBarScene = RigScene(equipment = crossBar(DIP_BAR_Y, z = DIP_WRIST_Z + Kit.HOOK_DROP, r = 1.8f, half = 30f))
    private val parallelScene = RigScene(
        equipment = listOf(
            Cylinder(Vec3(-PARALLEL_BAR_X, DIP_BAR_Y, -32f), Vec3(-PARALLEL_BAR_X, DIP_BAR_Y, 48f), 1.9f),
            Cylinder(Vec3(PARALLEL_BAR_X, DIP_BAR_Y, -32f), Vec3(PARALLEL_BAR_X, DIP_BAR_Y, 48f), 1.9f),
            Box(Vec3(-PARALLEL_BAR_X - 2.5f, 0f, -36f), Vec3(-PARALLEL_BAR_X + 2.5f, DIP_BAR_Y, -31f)),
            Box(Vec3(PARALLEL_BAR_X - 2.5f, 0f, -36f), Vec3(PARALLEL_BAR_X + 2.5f, DIP_BAR_Y, -31f)),
            Box(Vec3(-PARALLEL_BAR_X - 2.5f, 0f, 46f), Vec3(-PARALLEL_BAR_X + 2.5f, DIP_BAR_Y, 51f)),
            Box(Vec3(PARALLEL_BAR_X - 2.5f, 0f, 46f), Vec3(PARALLEL_BAR_X + 2.5f, DIP_BAR_Y, 51f)),
        ),
    )
    private val germanScene = RigScene(equipment = crossBar(161.8f, z = -21.6f))

    private fun kf(p: Pose, hold: Long = 500, move: Long = 1100) = Keyframe(p, hold, move)

    fun all(): Map<String, ExerciseRig> = mapOf(
        "passive_dead_hang" to passiveDeadHang(),
        "scapular_pullups" to scapularPullups(),
        "strict_pullups" to strictPullups(),
        "hanging_leg_raises" to hangingLegRaises(),
        "scapular_dips" to scapularDips(),
        "l_sit" to lSit(),
        "parallel_bar_dips" to parallelBarDips(),
        "straight_bar_dips" to straightBarDips(),
        "australian_pullups" to australianPullups(),
        "german_hang" to germanHang(),
    )

    // ------------------------------------------------------------------ hanging helpers

    private fun hang(
        centerY: Float,
        shrug: Float,
        centerZ: Float = 0f,
        lean: Float = 0f,
        flex: Float = 0f,
        head: Float = 0f,
        look: Float = 0f,
        barY: Float = PULL_BAR_Y,
        pole: PoleSpec = Poles.BACK_DOWN,
        legs: (Vec3) -> Pair<Limb, Limb> = { Kit.legsHang(it) },
    ): Pose {
        val s = Vec3(0f, centerY, centerZ)
        val hip = Kit.hipOf(s, lean, flex)
        return Kit.shoulderPose(s, lean, flex, head, shrug, look, hands = Kit.barHands(barY, 0f, pole = pole), feet = legs(hip))
    }

    /** Shoulder-centre height for straight arms under the pull-up bar. */
    private fun straightCenter(shrug: Float, barY: Float = PULL_BAR_Y) = barY - Kit.HOOK_DROP - Kit.ARM - shrug

    // ------------------------------------------------------------------ pull-up bar

    private fun passiveDeadHang(): ExerciseRig {
        val a = hang(straightCenter(6f), 6f, lean = 4f, head = 2f)
        val b = hang(straightCenter(4f), 4f, lean = -4f, head = 2f, legs = { Kit.legsHang(it, fwd = 8f) })
        return ExerciseRig(RigAnimation(pullScene, listOf(kf(a, 600, 1800), kf(b, 600, 1800))), emptyMap())
    }

    private fun scapularPullups(): ExerciseRig {
        val relaxed = hang(straightCenter(6f), 6f)
        val packed = hang(straightCenter(-3f), -3f)
        val wrong = hang(205f, 0f, centerZ = -8f, lean = -8f, head = 6f, look = 22f)
        return ExerciseRig(
            RigAnimation(pullScene, listOf(kf(relaxed, 500, 1000), kf(packed, 2000, 1500))),
            mapOf(
                "scapular_pullups.wrong_bent_elbows" to RigStill(pullScene, wrong),
                "scapular_pullups.right_straight_arm" to RigStill(pullScene, packed),
            ),
        )
    }

    private fun strictPullups(): ExerciseRig {
        val bottom = hang(straightCenter(-2f), -2f)
        val top = hang(224f, -3f, centerZ = -24f, lean = -14f, head = -2f, look = 8f, legs = { Kit.legsBent(it) })
        val wrong = hang(212f, 7f, centerZ = -16f, lean = -6f, head = 30f, look = 24f, legs = { Kit.legsBent(it) })
        return ExerciseRig(
            RigAnimation(pullScene, listOf(kf(bottom, 600, 1500), kf(top, 400, 1000))),
            mapOf(
                "strict_pullups.wrong_shrug_chin_poke" to RigStill(pullScene, wrong),
                "strict_pullups.right_chest_to_bar" to RigStill(pullScene, top),
            ),
        )
    }

    private fun hangingLegRaises(): ExerciseRig {
        val down = hang(straightCenter(-2f), -2f, lean = 3f)
        val up = hang(straightCenter(-2f), -2f, lean = -10f, legs = { Kit.legsForward(it, 4f) })
        val wrong = hang(
            straightCenter(0f), 0f, lean = -14f, flex = -10f,
            legs = { Kit.legsHang(it, fwd = -34f) },
        )
        return ExerciseRig(
            RigAnimation(pullScene, listOf(kf(down, 600, 1500), kf(up, 600, 1500))),
            mapOf(
                "hanging_leg_raises.wrong_swing" to RigStill(pullScene, wrong),
                "hanging_leg_raises.right_strict" to RigStill(pullScene, up),
            ),
        )
    }

    // ------------------------------------------------------------------ parallel bars

    private fun support(
        centerY: Float,
        shrug: Float,
        centerZ: Float = 0f,
        lean: Float = 0f,
        flex: Float = 0f,
        head: Float = 0f,
        look: Float = 0f,
        scene: RigScene = parallelScene,
        wristX: Float = PARALLEL_WRIST_X,
        wristZ: Float = 0f,
        wristY: Float = SUPPORT_WRIST_Y,
        pole: PoleSpec = Poles.OUT_BACK,
        handYaw: Float = 90f,
        legs: (Vec3) -> Pair<Limb, Limb> = { Kit.legsBent(it) },
    ): Pose {
        val s = Vec3(0f, centerY, centerZ)
        val hip = Kit.hipOf(s, lean, flex)
        return Kit.shoulderPose(
            s, lean, flex, head, shrug, look,
            hands = sym(wristX, wristY, wristZ, pole, 0f, true, yaw = handYaw, hand = HandShape.HOOK),
            feet = legs(hip),
        )
    }

    private fun supportCenter(shrug: Float, dz: Float = 0f, wristY: Float = SUPPORT_WRIST_Y) =
        wristY + sqrt(Kit.ARM * Kit.ARM - dz * dz) - shrug

    private fun scapularDips(): ExerciseRig {
        val tall = support(supportCenter(-3f), -3f, lean = 8f, head = 4f)
        val sunk = support(supportCenter(6f), 6f, lean = 8f, head = 0f)
        val wrong = support(158f, 6f, lean = 6f, flex = -8f, head = 0f)
        return ExerciseRig(
            RigAnimation(parallelScene, listOf(kf(tall, 800, 900), kf(sunk, 600, 900))),
            mapOf(
                "scapular_dips.wrong_sagging" to RigStill(parallelScene, wrong),
                "scapular_dips.right_depressed" to RigStill(parallelScene, tall),
            ),
        )
    }

    private fun lSit(): ExerciseRig {
        val center = supportCenter(-3f, dz = 4f)
        val tuck = support(center, -3f, centerZ = -4f, lean = -8f, head = 2f, legs = { hip ->
            Kit.feet(Vec3(0f, hip.y - 15f, hip.z + 11f), 8f, Poles.UP, 60f)
        })
        val full = support(center, -3f, centerZ = -4f, lean = -8f, head = 2f, legs = { Kit.legsForward(it, 3f) })
        val wrong = support(
            supportCenter(7f, dz = 4f) - 8f, 7f, centerZ = -4f, lean = -4f, head = 6f,
            legs = { Kit.legsForward(it, 3f) },
        )
        return ExerciseRig(
            RigAnimation(parallelScene, listOf(kf(tuck, 700, 1200), kf(full, 1200, 1200))),
            mapOf(
                "l_sit.wrong_shrugged" to RigStill(parallelScene, wrong),
                "l_sit.right_depressed" to RigStill(parallelScene, full),
            ),
        )
    }

    private fun parallelBarDips(): ExerciseRig {
        val top = support(supportCenter(-3f), -3f, centerZ = 2f, lean = 12f, head = 6f)
        val bottom = support(143f, 0f, centerZ = 20f, lean = 32f, head = 10f, pole = Poles.OUT_BACK)
        val wrong = support(126f, 0f, centerZ = 24f, lean = 36f, flex = -12f, head = 10f, pole = Poles.OUT)
        return ExerciseRig(
            RigAnimation(parallelScene, listOf(kf(top, 600, 1400), kf(bottom, 400, 800))),
            mapOf(
                "parallel_bar_dips.wrong_deep_flared" to RigStill(parallelScene, wrong),
                "parallel_bar_dips.right_parallel" to RigStill(parallelScene, bottom),
            ),
        )
    }

    private fun straightBarDips(): ExerciseRig {
        fun dip(centerY: Float, shrug: Float, centerZ: Float, lean: Float, flex: Float = 0f, pole: PoleSpec = Poles.BACK, head: Float = 8f) =
            support(
                centerY, shrug, centerZ, lean, flex, head, scene = dipBarScene,
                wristX = 22f, wristZ = DIP_WRIST_Z, wristY = SUPPORT_WRIST_Y, handYaw = 0f, pole = pole,
            )
        val top = dip(supportCenter(-3f, wristY = SUPPORT_WRIST_Y), -3f, -2f, 20f)
        val bottom = dip(138f, 0f, 18f, 40f)
        val wrong = dip(141f, 3f, -14f, 4f, flex = 6f, head = 0f)
        return ExerciseRig(
            RigAnimation(dipBarScene, listOf(kf(top, 600, 1400), kf(bottom, 400, 800))),
            mapOf(
                "straight_bar_dips.wrong_upright" to RigStill(dipBarScene, wrong),
                "straight_bar_dips.right_chest_over" to RigStill(dipBarScene, bottom),
            ),
        )
    }

    // ------------------------------------------------------------------ low bar

    private fun australianPullups(): ExerciseRig {
        val barWristY = ROW_BAR_Y - Kit.HOOK_DROP
        val ankleY = 50f
        fun row(centerY: Float, shrug: Float, reach: Float = 140.8f, hipAbove: Boolean = true, head: Float = 0f, look: Float = 0f): Pose {
            val ankleZ = -140.6f
            val shoulderZ = ankleZ + sqrt(reach * reach - (centerY - ankleY) * (centerY - ankleY))
            val s = Vec3(0f, centerY, shoulderZ)
            val ankle = Vec3(0f, ankleY, ankleZ)
            val lean = Kit.leanFor(s, ankle, 0f, Kit.LEG, hipAbove)
            return Kit.shoulderPose(
                s, lean, 0f, head, shrug, look,
                hands = sym(22f, barWristY, 0f, Poles.BACK_DOWN, -90f, true, hand = HandShape.HOOK),
                feet = Kit.feet(ankle, 9f, Poles.DOWN, -20f, contact = true),
            )
        }
        val hangPose = row(barWristY - Kit.ARM + 2f, -2f)
        val top = row(88f, -2f)
        val wrong = row(50f, 0f, reach = 137.5f, hipAbove = false, head = 14f, look = 26f)
        return ExerciseRig(
            RigAnimation(rowScene, listOf(kf(hangPose, 500, 1200), kf(top, 800, 1200))),
            mapOf(
                "australian_pullups.wrong_sagging" to RigStill(rowScene, wrong),
                "australian_pullups.right_plank_line" to RigStill(rowScene, top),
            ),
        )
    }

    // ------------------------------------------------------------------ german hang

    private fun germanHang(): ExerciseRig {
        fun german(jointY: Float, jointZ: Float, shrug: Float, lean: Float, head: Float = 0f, look: Float = 0f): Pose {
            val s = Vec3(0f, jointY - shrug, jointZ)
            val hip = Kit.hipOf(s, lean, 0f)
            val feetTarget = Vec3(0f, 8f, hip.z + 14f)
            return Kit.shoulderPose(
                s, lean, 0f, head, shrug, look,
                hands = sym(22f, 156f, -30f, Poles.FWD_DOWN, -60f, true, hand = HandShape.HOOK),
                feet = Kit.feet(feetTarget, 12f, Poles.FWD, 0f, contact = true),
            )
        }
        val a = german(jointY = 121f, jointZ = 10f, shrug = -2f, lean = -8f)
        val b = german(jointY = 108f, jointZ = -3f, shrug = -2f, lean = -6f, head = -4f, look = 4f)
        val droppedCenter = Vec3(0f, 106f - 8f, -14f)
        val droppedHip = Kit.hipOf(droppedCenter, 2f)
        val wrong = Kit.shoulderPose(
            droppedCenter, 2f, 0f, 10f, 8f, 0f,
            hands = sym(22f, 156f, -30f, Poles.FWD_DOWN, -60f, true, hand = HandShape.HOOK),
            feet = Kit.feet(Vec3(0f, 16f, droppedHip.z + 20f), 12f, Poles.FWD, 20f),
        )
        return ExerciseRig(
            RigAnimation(germanScene, listOf(kf(a, 600, 1600), kf(b, 1500, 1600))),
            mapOf(
                "german_hang.wrong_passive_drop" to RigStill(germanScene, wrong),
                "german_hang.right_feet_assisted" to RigStill(germanScene, b),
            ),
        )
    }
}
