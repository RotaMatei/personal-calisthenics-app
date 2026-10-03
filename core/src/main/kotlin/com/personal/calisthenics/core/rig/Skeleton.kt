package com.personal.calisthenics.core.rig

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/** Segment lengths in centimetres. */
object Body {
    const val TORSO_LOWER = 26f
    const val TORSO_UPPER = 28f
    const val NECK = 8f
    const val HEAD_RADIUS = 10f
    const val SHOULDER_HALF = 19f
    const val HIP_HALF = 10f
    const val UPPER_ARM = 30f
    const val FOREARM = 26f
    const val HAND = 9f
    const val THIGH = 44f
    const val SHIN = 43f
    const val FOOT_FRONT = 16f
    const val FOOT_BACK = 4f
    const val ANKLE_HEIGHT = 8f

    const val ARM_REACH = UPPER_ARM + FOREARM
    const val LEG_REACH = THIGH + SHIN
}

enum class LimbId { HAND_L, HAND_R, FOOT_L, FOOT_R }

/** Solved joint positions of a pose. */
data class Skeleton(
    val hip: Vec3,
    val mid: Vec3,
    val shoulder: Vec3,
    val headCenter: Vec3,
    val shoulderL: Vec3,
    val shoulderR: Vec3,
    val elbowL: Vec3,
    val elbowR: Vec3,
    val wristL: Vec3,
    val wristR: Vec3,
    val handTipL: Vec3,
    val handTipR: Vec3,
    val hipL: Vec3,
    val hipR: Vec3,
    val kneeL: Vec3,
    val kneeR: Vec3,
    val ankleL: Vec3,
    val ankleR: Vec3,
    val heelL: Vec3,
    val heelR: Vec3,
    val toeL: Vec3,
    val toeR: Vec3,
    /** Torso up direction (hip -> shoulder, upper spine) and chest-facing direction. */
    val up: Vec3,
    val front: Vec3,
    /** Direction the face points (side view nose marker). */
    val faceDir: Vec3,
    /** Distance between the requested limb target and where the limb actually ended. */
    val reachError: Map<LimbId, Float>,
)

object RigSolver {

    fun solve(pose: Pose): Skeleton {
        val lowUp = upDir(pose.lean)
        val upperAngle = pose.lean + pose.spineFlex
        val upperUp = upDir(upperAngle)

        val hip: Vec3
        val mid: Vec3
        val shoulder: Vec3
        if (pose.anchorAt == Anchor.SHOULDER) {
            shoulder = pose.anchor
            mid = shoulder - upperUp * Body.TORSO_UPPER
            hip = mid - lowUp * Body.TORSO_LOWER
        } else {
            hip = pose.anchor
            mid = hip + lowUp * Body.TORSO_LOWER
            shoulder = mid + upperUp * Body.TORSO_UPPER
        }
        val headCenter = shoulder + upDir(upperAngle + pose.headTilt) * (Body.NECK + Body.HEAD_RADIUS)

        val shoulderL = shoulder + Vec3(-Body.SHOULDER_HALF, pose.shrug, 0f)
        val shoulderR = shoulder + Vec3(Body.SHOULDER_HALF, pose.shrug, 0f)
        val hipL = hip + Vec3(-Body.HIP_HALF, 0f, 0f)
        val hipR = hip + Vec3(Body.HIP_HALF, 0f, 0f)

        val armL = twoBone(shoulderL, pose.handL.target, pose.handL.pole, Body.UPPER_ARM, Body.FOREARM)
        val armR = twoBone(shoulderR, pose.handR.target, pose.handR.pole, Body.UPPER_ARM, Body.FOREARM)
        val legL = twoBone(hipL, pose.footL.target, pose.footL.pole, Body.THIGH, Body.SHIN)
        val legR = twoBone(hipR, pose.footR.target, pose.footR.pole, Body.THIGH, Body.SHIN)

        val handTipL = armL.end + pitchDir(pose.handL.pitch) * Body.HAND
        val handTipR = armR.end + pitchDir(pose.handR.pitch) * Body.HAND

        fun heel(ankle: Vec3, pitch: Float) = ankle + Vec3(0f, -3f, 0f) - pitchDir(pitch) * Body.FOOT_BACK
        fun toe(ankle: Vec3, pitch: Float) = ankle + Vec3(0f, -3f, 0f) + pitchDir(pitch) * Body.FOOT_FRONT

        return Skeleton(
            hip = hip, mid = mid, shoulder = shoulder, headCenter = headCenter,
            shoulderL = shoulderL, shoulderR = shoulderR,
            elbowL = armL.mid, elbowR = armR.mid,
            wristL = armL.end, wristR = armR.end,
            handTipL = handTipL, handTipR = handTipR,
            hipL = hipL, hipR = hipR,
            kneeL = legL.mid, kneeR = legR.mid,
            ankleL = legL.end, ankleR = legR.end,
            heelL = heel(legL.end, pose.footL.pitch), heelR = heel(legR.end, pose.footR.pitch),
            toeL = toe(legL.end, pose.footL.pitch), toeR = toe(legR.end, pose.footR.pitch),
            up = upperUp,
            front = frontDir(upperAngle),
            faceDir = frontDir(upperAngle + pose.headTilt - pose.look),
            reachError = mapOf(
                LimbId.HAND_L to armL.error,
                LimbId.HAND_R to armR.error,
                LimbId.FOOT_L to legL.error,
                LimbId.FOOT_R to legR.error,
            ),
        )
    }

    private data class Chain(val mid: Vec3, val end: Vec3, val error: Float)

    /** Analytic two-bone IK in 3D with a pole vector choosing the bend direction. */
    private fun twoBone(root: Vec3, target: Vec3, pole: Vec3, l1: Float, l2: Float): Chain {
        val toTarget = target - root
        val rawDist = toTarget.length()
        val dir = if (rawDist < 1e-4f) Vec3(0f, -1f, 0f) else toTarget * (1f / rawDist)
        val maxReach = l1 + l2 - 0.05f
        val minReach = abs(l1 - l2) + 0.5f
        val dist = min(max(rawDist, minReach), maxReach)
        val end = root + dir * dist

        val a = (l1 * l1 - l2 * l2 + dist * dist) / (2f * dist)
        val h = sqrt(max(0f, l1 * l1 - a * a))

        var perp = pole - dir * pole.dot(dir)
        if (perp.length() < 1e-4f) {
            perp = Vec3(0f, 0f, 1f) - dir * dir.z
            if (perp.length() < 1e-4f) perp = Vec3(1f, 0f, 0f)
        }
        val mid = root + dir * a + perp.normalized() * h
        val error = (end - target).length()
        return Chain(mid, end, error)
    }
}
