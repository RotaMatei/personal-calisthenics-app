package com.personal.calisthenics.core.rig

import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.sqrt

/** One exercise's rig content: the looping demonstration plus the DO/DON'T stills it owns. */
internal class ExerciseRig(val animation: RigAnimation, val stills: Map<String, RigStill>)

/** A single still pose in a scene (used by the DO/DON'T cards). */
class RigStill(val scene: RigScene, val pose: Pose)

/**
 * Authoring helpers shared by the pose libraries. All lengths are centimetres in the rig's world space
 * (+z forward, +y up, floor at y = 0). Chords are kept slightly below the true limb length so that "straight"
 * limbs never clamp the IK solver.
 */
internal object Kit {
    const val STAND_HIP_Y = 94.85f
    const val LEG = 86.9f
    const val ARM = 55.9f
    const val WRIST_FLOOR = 4.4f
    const val ANKLE_FLAT = 8f
    const val PLANK_ANKLE_Y = 20.7f
    const val KNEE_FLOOR_Y = 6.4f
    const val TORSO = Body.TORSO_LOWER + Body.TORSO_UPPER

    fun hipOf(shoulder: Vec3, lean: Float, flex: Float = 0f): Vec3 =
        shoulder - upDir(lean + flex) * Body.TORSO_UPPER - upDir(lean) * Body.TORSO_LOWER

    fun shoulderOf(hip: Vec3, lean: Float, flex: Float = 0f): Vec3 =
        hip + upDir(lean) * Body.TORSO_LOWER + upDir(lean + flex) * Body.TORSO_UPPER

    /** World position of the shoulder centre for a given shoulder-joint height (the joint sits [shrug] above it). */
    fun centerY(jointY: Float, shrug: Float) = jointY - shrug

    /**
     * Lean (degrees from vertical) for a straight-ish torso so that the hip is [legLen] from [ankle] while the
     * shoulder centre is at [shoulder]. [hipAbove] picks the solution with the hips above the shoulder-ankle line
     * (pike) or below it (sag). When the body cannot fold that way the closest fit is returned.
     */
    fun leanFor(shoulder: Vec3, ankle: Vec3, flex: Float = 0f, legLen: Float = LEG, hipAbove: Boolean = true): Float {
        fun err(lean: Float): Float {
            val h = hipOf(shoulder, lean, flex)
            return hypot(h.y - ankle.y, h.z - ankle.z) - legLen
        }
        fun isAbove(lean: Float): Boolean {
            val h = hipOf(shoulder, lean, flex)
            val dz = ankle.z - shoulder.z
            if (abs(dz) < 1e-3f) return h.z >= shoulder.z
            val lineY = shoulder.y + (ankle.y - shoulder.y) * ((h.z - shoulder.z) / dz)
            return h.y >= lineY
        }
        var bestSide = 0f
        var bestSideErr = Float.MAX_VALUE
        var bestAny = 0f
        var bestAnyErr = Float.MAX_VALUE
        var prevLean = 0f
        var prevErr = err(0f)
        val roots = mutableListOf<Float>()
        var lean = 0.25f
        while (lean <= 180f) {
            val e = err(lean)
            if (abs(e) < bestAnyErr) { bestAnyErr = abs(e); bestAny = lean }
            if (abs(e) < bestSideErr && isAbove(lean) == hipAbove) { bestSideErr = abs(e); bestSide = lean }
            if ((prevErr < 0f) != (e < 0f)) {
                val t = prevErr / (prevErr - e)
                roots += prevLean + (lean - prevLean) * t
            }
            prevLean = lean
            prevErr = e
            lean += 0.25f
        }
        val matching = roots.filter { isAbove(it) == hipAbove }
        return matching.firstOrNull() ?: if (bestSideErr < 0.6f) bestSide else bestAny
    }

    /** Ankle target on the floor side of a body that extends behind the shoulder, [reach] away from it. */
    fun ankleBehind(shoulder: Vec3, ankleY: Float, reach: Float): Vec3 {
        val dy = shoulder.y - ankleY
        val dz = sqrt(maxOf(0f, reach * reach - dy * dy))
        return Vec3(0f, ankleY, shoulder.z - dz)
    }

    /** Foot limbs for a straight leg hanging / extending from [hip] to [ankle] (x spread by [x]). */
    fun feet(ankle: Vec3, x: Float = 8f, pole: PoleSpec = Poles.DOWN, pitch: Float = 0f, contact: Boolean = false) =
        sym(x, ankle.y, ankle.z, pole, pitch, contact)

    fun floorHands(z: Float, x: Float = 19f, y: Float = WRIST_FLOOR, pole: PoleSpec = Poles.OUT_BACK, pitch: Float = 0f) =
        sym(x, y, z, pole, pitch, true)

    /** Hand limbs hanging from a bar at height [barY] (overhand hook grip, fingers pointing up). */
    fun barHands(barY: Float, z: Float = 0f, x: Float = 22f, pole: PoleSpec = Poles.BACK_DOWN) =
        sym(x, barY - 4f, z, pole, -90f, true)

    /** Standing hips with straight legs. */
    fun standing(
        hip: Vec3 = Vec3(0f, STAND_HIP_Y, 0f),
        lean: Float = 0f,
        flex: Float = 0f,
        head: Float = 0f,
        shrug: Float = 0f,
        look: Float = 0f,
        hands: Pair<Limb, Limb>,
        feet: Pair<Limb, Limb> = sym(9f, ANKLE_FLAT, 0f, Poles.FWD, 0f, true),
    ) = pose(
        anchorAt = Anchor.HIP, anchor = hip, lean = lean, spineFlex = flex, headTilt = head, shrug = shrug,
        look = look, hands = hands, feet = feet,
    )

    /** Pose anchored at the shoulder centre. */
    fun shoulderPose(
        shoulder: Vec3,
        lean: Float,
        flex: Float = 0f,
        head: Float = 0f,
        shrug: Float = 0f,
        look: Float = 0f,
        hands: Pair<Limb, Limb>,
        feet: Pair<Limb, Limb>,
    ) = pose(
        anchorAt = Anchor.SHOULDER, anchor = shoulder, lean = lean, spineFlex = flex, headTilt = head, shrug = shrug,
        look = look, hands = hands, feet = feet,
    )

    /**
     * Front-support / plank-family pose. The shoulder joint is at [jointY], [jointZ]; the ankle is placed
     * [reach] away from the shoulder centre along the floor (140.8 = straight line, smaller = folded hips).
     */
    fun plank(
        jointY: Float,
        jointZ: Float,
        wristZ: Float,
        shrug: Float = 0f,
        flex: Float = 0f,
        head: Float = 12f,
        look: Float = 0f,
        ankleY: Float = PLANK_ANKLE_Y,
        reach: Float = 140.8f,
        hipAbove: Boolean = true,
        wristY: Float = WRIST_FLOOR,
        handPitch: Float = 0f,
        handX: Float = 19f,
        armPole: PoleSpec = Poles.OUT_BACK,
        footPitch: Float = 64f,
        legLen: Float = LEG,
    ): Pose {
        val shoulder = Vec3(0f, centerY(jointY, shrug), jointZ)
        val ankle = ankleBehind(shoulder, ankleY, reach)
        val lean = leanFor(shoulder, ankle, flex, legLen, hipAbove)
        return shoulderPose(
            shoulder, lean, flex, head, shrug, look,
            hands = sym(handX, wristY, wristZ, armPole, handPitch, true),
            feet = feet(ankle, 9f, Poles.DOWN, footPitch, contact = true),
        )
    }

    /**
     * Kneeling on all fours: the knees stay at [kneeZ], the shins lie on the floor behind them, the hands are
     * flat on the floor at [wristZ] and the shoulder joint is at ([jointY], [jointZ]). The thigh angle is solved
     * so the torso keeps its length (hips slide back toward the heels or forward over the knees as needed).
     */
    fun kneeling(
        jointY: Float,
        jointZ: Float,
        wristZ: Float,
        kneeZ: Float = 0f,
        shrug: Float = 0f,
        head: Float = 14f,
        look: Float = 0f,
        wristY: Float = WRIST_FLOOR,
        handPitch: Float = 0f,
        handX: Float = 19f,
        armPole: PoleSpec = Poles.OUT_BACK,
    ): Pose {
        val shoulder = Vec3(0f, centerY(jointY, shrug), jointZ)
        fun hipAt(theta: Float) = Vec3(0f, KNEE_FLOOR_Y + Body.THIGH * kotlin.math.cos(rad(theta)), kneeZ - Body.THIGH * kotlin.math.sin(rad(theta)))
        fun err(theta: Float) = (shoulder - hipAt(theta)).length() - TORSO
        var theta = 0f
        var bestAbs = Float.MAX_VALUE
        var bestErr = Float.MAX_VALUE
        var prev = -45f
        var prevErr = err(prev)
        var t = -44.95f
        while (t <= 85f) {
            val e = err(t)
            if (abs(e) < bestErr && bestAbs == Float.MAX_VALUE) { bestErr = abs(e); theta = t }
            if ((prevErr < 0f) != (e < 0f)) {
                val root = prev + (t - prev) * (prevErr / (prevErr - e))
                if (abs(root) < bestAbs) { bestAbs = abs(root); theta = root }
            }
            prev = t
            prevErr = e
            t += 0.05f
        }
        val hip = hipAt(theta)
        val lean = Math.toDegrees(kotlin.math.atan2((shoulder.z - hip.z).toDouble(), (shoulder.y - hip.y).toDouble())).toFloat()
        val ankle = Vec3(0f, KNEE_FLOOR_Y + 0.6f, kneeZ - 43f)
        return shoulderPose(
            shoulder, lean, 0f, head, shrug, look,
            hands = sym(handX, wristY, wristZ, armPole, handPitch, true),
            feet = feet(ankle, 10f, Poles.DOWN, 176f, contact = false),
        )
    }

    /** Bent legs hanging behind a supported / hanging torso: foot position relative to the hip. */
    fun legsBent(hip: Vec3, dropY: Float = 42f, backZ: Float = 44f, x: Float = 9f, pitch: Float = 60f) =
        feet(Vec3(0f, hip.y - dropY, hip.z - backZ), x, Poles.FWD_DOWN, pitch)

    /** Straight legs hanging below the hip. */
    fun legsHang(hip: Vec3, fwd: Float = 0f, x: Float = 9f, pitch: Float = 70f): Pair<Limb, Limb> {
        val drop = sqrt(LEG * LEG - fwd * fwd)
        return feet(Vec3(0f, hip.y - drop, hip.z + fwd), x, Poles.FWD, pitch)
    }

    /** Straight legs extended forward from the hip; [liftDeg] 0 = horizontal, 90 = vertical up. */
    fun legsForward(hip: Vec3, liftDeg: Float, x: Float = 8f, pitch: Float = 60f): Pair<Limb, Limb> {
        val d = Vec3(0f, kotlin.math.sin(rad(liftDeg)), kotlin.math.cos(rad(liftDeg)))
        val a = hip + d * LEG
        return feet(Vec3(0f, a.y, a.z), x, Poles.UP, pitch)
    }
}
