package com.personal.calisthenics.core.rig

import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.sin

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
            hands = sym(36f, 196f, 0f, Poles.UP_OUT, -80f, hand = HandShape.OPEN),
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

    /** Each part of the joint-circles clip lasts this long, so the five parts split the drill into equal slots. */
    const val CIRCLE_PART_MS = 6000L

    private val shoulderY = Kit.STAND_HIP_Y + Kit.TORSO
    private const val ARM_R = 55f

    /** Standing pose with the pelvis shifted by ([dx], [dz]) while the shoulders stay over the feet (hip circles). */
    private fun shifted(
        dx: Float,
        dz: Float,
        hands: (dx: Float, dz: Float) -> Pair<Limb, Limb>,
        feet: Pair<Limb, Limb> = sym(9f, Kit.ANKLE_FLAT, 0f, Poles.FWD, 0f, true),
        head: Float = 0f,
    ): Pose {
        val side = -Math.toDegrees(asin(dx / Body.TORSO_LOWER.toDouble())).toFloat()
        val lean = -Math.toDegrees(asin(dz / Body.TORSO_LOWER.toDouble())).toFloat()
        // The tilted pelvis lifts one hip joint, so sink it just enough that both planted legs still reach the floor.
        fun build(y: Float) = pose(
            anchorAt = Anchor.HIP, anchor = Vec3(dx, y, dz), lean = lean, spineFlex = -lean,
            headTilt = head, sideLean = side, sideFlex = -side, hands = hands(dx, dz), feet = feet,
        )
        var y = Kit.STAND_HIP_Y
        var result = build(y)
        repeat(8) {
            val errors = RigSolver.solve(result).reachError
            val err = maxOf(errors.getValue(LimbId.FOOT_L), errors.getValue(LimbId.FOOT_R))
            if (err < 0.05f) return result
            y -= err + 0.1f
            result = build(y)
        }
        return result
    }

    /** Both fists on the hips, elbows out and slightly back; the hands travel with the pelvis. */
    private fun handsOnHips(dx: Float, dz: Float): Pair<Limb, Limb> {
        val y = Kit.STAND_HIP_Y + 4f
        val pole = Vec3(0.7f, 0f, -0.7f)
        return Limb(Vec3(-17f + dx, y, 2f + dz), Vec3(-pole.x, pole.y, pole.z), 0f, false, 0f, 0f, HandShape.FIST) to
            Limb(Vec3(17f + dx, y, 2f + dz), pole, 0f, false, 0f, 0f, HandShape.FIST)
    }

    /** Both arms straight and pointing [theta] degrees round the shoulder: 0 up, 90 forward, 180 down, 270 back. */
    private fun armsAt(theta: Float): Pair<Limb, Limb> {
        val t = Math.toRadians(theta.toDouble())
        val y = shoulderY + ARM_R * cos(t).toFloat()
        val z = ARM_R * sin(t).toFloat()
        val pole = Vec3(0f, -sin(t).toFloat(), cos(t).toFloat())
        fun one(x: Float, p: Vec3) = Limb(Vec3(x, y, z), p, theta - 90f, false, 0f, 0f, HandShape.OPEN)
        return one(-Body.SHOULDER_HALF - 1f, pole) to one(Body.SHOULDER_HALF + 1f, pole)
    }

    /** Fingertips on the shoulders, so the elbow swings round on a circle; [phi] 0 = elbows forward, 90 = up. */
    private fun elbowCircle(phi: Float): Pair<Limb, Limb> {
        val t = Math.toRadians(phi.toDouble())
        val pole = Vec3(0f, sin(t).toFloat(), cos(t).toFloat())
        val y = shoulderY + 3f
        val reach = Body.SHOULDER_HALF + 7f
        return Limb(Vec3(-reach, y, 0f), pole, 0f, false, 0f, 0f, HandShape.RELAXED) to
            Limb(Vec3(reach, y, 0f), pole, 0f, false, 0f, 0f, HandShape.RELAXED)
    }

    /** One foot off the floor with the toe tracing a circle: pitch swings up and down, yaw in and out. */
    private fun liftedFoot(left: Boolean, phi: Float): Pair<Limb, Limb> {
        val t = Math.toRadians(phi.toDouble())
        val x = 9f
        val flat = sym(x, Kit.ANKLE_FLAT, 0f, Poles.FWD, 0f, true)
        val lifted = Limb(
            Vec3(if (left) -x else x, 18f, 14f), Vec3(0f, 0f, 1f),
            22f + 36f * cos(t).toFloat(), false, 28f * sin(t).toFloat(),
        )
        return if (left) lifted to flat.second else flat.first to lifted
    }

    private fun circleAngles(step: Float = 45f): List<Float> {
        val n = (360f / step).toInt()
        val forward = (0 until n).map { it * step }
        val back = (n - 1 downTo 1).map { it * step }
        return forward + listOf(360f) + back + listOf(0f)
    }

    /** One captioned part: poses evenly share [CIRCLE_PART_MS]; moves ease at the indices in [easeAt], else flow. */
    private fun part(label: String, poses: List<Pose>, easeAt: Set<Int> = emptySet(), allEase: Boolean = false): List<Keyframe> {
        val each = CIRCLE_PART_MS / poses.size
        val lastMs = CIRCLE_PART_MS - each * (poses.size - 1)
        return poses.mapIndexed { i, p ->
            val last = i == poses.lastIndex
            Keyframe(p, 0L, if (last) lastMs else each, if (i == 0) label else null, flow = !(allEase || last || i in easeAt))
        }
    }

    private fun jointCircles(): ExerciseRig {
        val neutral = Kit.standing(hands = armsDown)
        // 1. Neck half circles: ear to right shoulder, down through the chin, ear to left shoulder, and back.
        fun neck(roll: Float, tilt: Float) = Kit.standing(hands = armsDown, head = tilt, headRoll = roll)
        val neckPoses = listOf(
            neck(0f, 0f), neck(30f, 0f), neck(22f, 22f), neck(0f, 32f), neck(-22f, 22f), neck(-30f, 0f),
            neck(-22f, 22f), neck(0f, 32f), neck(22f, 22f), neck(30f, 0f),
        )
        // 2. Shoulder circles with straight arms: a full circle forwards, then a full circle backwards.
        val shoulderAngles = (0..12).map { 180f - 30f * it } + (13..24).map { -180f + 30f * (it - 12) }
        val shoulderPoses = shoulderAngles.map { Kit.standing(hands = armsAt(it)) }
        // 3. Elbow circles with the fingertips on the shoulders.
        val elbowPoses = circleAngles().map { Kit.standing(hands = elbowCircle(it + 180f)) }
        // 4. Hip circles: the pelvis draws a circle while the shoulders stay put; one way, then the other.
        val hipPoses = circleAngles().map {
            val t = Math.toRadians(it.toDouble())
            shifted(9f * cos(t).toFloat(), 9f * sin(t).toFloat(), ::handsOnHips)
        }
        // 5. Ankle circles: right foot, then left foot.
        fun ankle(left: Boolean, phi: Float) = shifted(if (left) 3f else -3f, 0f, ::handsOnHips, liftedFoot(left, phi))
        val anklePoses = (0 until 8).map { ankle(false, it * 45f) } + (0 until 8).map { ankle(true, it * 45f) }

        val frames = part("Neck (half circles)", neckPoses, allEase = true) +
            part("Shoulders", shoulderPoses, easeAt = setOf(11, 12)) +
            part("Elbows", elbowPoses, easeAt = setOf(7, 8)) +
            part("Hips", hipPoses, easeAt = setOf(7, 8)) +
            part("Ankles", anklePoses, easeAt = setOf(7))

        val wrong = Kit.standing(hands = armsDown, head = -32f, look = 38f)
        val right = Kit.standing(hands = armsDown, head = 6f)
        return ExerciseRig(
            RigAnimation(floor, frames),
            mapOf(
                "joint_circles.wrong_neck_hyperextend" to RigStill(floor, wrong),
                "joint_circles.right_neck_neutral" to RigStill(floor, right),
            ),
        )
    }

    // ------------------------------------------------------------------ band dislocates

    private fun bandedDislocates(): ExerciseRig {
        fun p(y: Float, z: Float) = Kit.standing(hands = sym(40f, y, z, Poles.BACK_DOWN, 70f, hand = HandShape.FIST))
        val low = p(100f, 14f)
        val frontHigh = p(148f, 50f)
        val overhead = Kit.standing(hands = sym(40f, 195f, 8f, Poles.BACK, -80f, hand = HandShape.FIST))
        val behindHigh = p(148f, -50f)
        val behindLow = p(103f, -17f)
        val frames = listOf(
            kf(low, 100, 600), kf(frontHigh, 0, 500), kf(overhead, 0, 500), kf(behindHigh, 0, 500),
            kf(behindLow, 200, 500), kf(behindHigh, 0, 500), kf(overhead, 0, 500), kf(frontHigh, 0, 500),
        )
        val wrong = Kit.standing(
            hip = Vec3(0f, Kit.STAND_HIP_Y, 5f), lean = -8f, flex = -9f,
            hands = sym(30f, 186f, -6f, Poles.OUT_BACK, -70f, hand = HandShape.FIST),
        )
        val right = Kit.standing(hands = sym(40f, 195f, 8f, Poles.BACK, -80f, hand = HandShape.FIST))
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
        val a = Kit.standing(shrug = -1f, hands = sym(14f, 147f, 52f, Poles.OUT_DOWN, 0f, hand = HandShape.FIST), head = 3f)
        val b = Kit.standing(shrug = -2f, hands = sym(72f, 147f, 12f, Poles.BACK, 0f, hand = HandShape.FIST), head = 3f)
        val wrong = Kit.standing(
            hip = Vec3(0f, Kit.STAND_HIP_Y, 4f), lean = -7f, flex = -7f, shrug = 6f,
            hands = sym(72f, 150f, 8f, Poles.BACK, 0f, hand = HandShape.FIST), head = 6f,
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
