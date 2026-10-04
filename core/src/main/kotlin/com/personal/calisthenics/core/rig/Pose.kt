package com.personal.calisthenics.core.rig

enum class Anchor { SHOULDER, HIP }

/**
 * One limb end target (hand or foot) with the direction the middle joint (elbow or knee) should bend toward.
 * [contact] marks targets that must touch a bar, the floor or a bench, so the preview tool can verify reach.
 * [pitch] orients the hand / foot in the sagittal plane (0 = forward, 90 = pointing down).
 */
data class Limb(
    val target: Vec3,
    val pole: Vec3,
    val pitch: Float = 0f,
    val contact: Boolean = false,
    /** Turns the hand / foot about the vertical axis in degrees; positive turns outward (away from the midline). */
    val yaw: Float = 0f,
    /** Rotates the palm about the finger direction in degrees (0 = palm toward the down / forward side). */
    val roll: Float = 0f,
    /** How the hand is shaped. AUTO picks flat on the floor, a hook around a nearby bar, otherwise relaxed. */
    val hand: HandShape = HandShape.AUTO,
)

enum class HandShape { AUTO, HOOK, FLAT, RELAXED, OPEN, FIST }

/** Describes a pole as out (away from the body midline), up and forward components. */
data class PoleSpec(val out: Float, val up: Float, val fwd: Float)

/**
 * A full-body pose, authored once in 3D. [lean] is the forward lean of the lower spine from vertical in degrees
 * (0 standing, 90 horizontal with the head forward, 180 upside down), [spineFlex] adds forward flexion of the
 * upper spine and [headTilt] moves the head relative to the spine. The anchor is either the shoulder centre or
 * the hip centre, whichever is pinned in place for this movement.
 */
data class Pose(
    val anchorAt: Anchor,
    val anchor: Vec3,
    val lean: Float,
    val spineFlex: Float,
    val headTilt: Float,
    /** Raises (+) or lowers (-) both shoulder joints relative to the shoulder centre, in cm (shrug / depression). */
    val shrug: Float,
    val handL: Limb,
    val handR: Limb,
    val footL: Limb,
    val footR: Limb,
    /** Extra face rotation in degrees: positive looks up (chin up), negative looks down. Does not move the head. */
    val look: Float = 0f,
    /** Side tilt of the head in degrees (ear toward the shoulder): positive tilts toward the figure's right (+x). */
    val headRoll: Float = 0f,
    /** Turn of the head about the neck in degrees: positive turns the face toward the figure's right (+x). */
    val headTurn: Float = 0f,
    /** Side lean of the lower spine in degrees (positive toward +x), used by hip circles and side bends. */
    val sideLean: Float = 0f,
    /** Extra side lean of the upper spine on top of [sideLean]. */
    val sideFlex: Float = 0f,
) {
    fun lerpTo(other: Pose, t: Float): Pose {
        require(anchorAt == other.anchorAt) { "Cannot blend poses with different anchors" }
        return Pose(
            anchorAt,
            lerp(anchor, other.anchor, t),
            lerp(lean, other.lean, t),
            lerp(spineFlex, other.spineFlex, t),
            lerp(headTilt, other.headTilt, t),
            lerp(shrug, other.shrug, t),
            blend(handL, other.handL, t),
            blend(handR, other.handR, t),
            blend(footL, other.footL, t),
            blend(footR, other.footR, t),
            lerp(look, other.look, t),
            lerp(headRoll, other.headRoll, t),
            lerp(headTurn, other.headTurn, t),
            lerp(sideLean, other.sideLean, t),
            lerp(sideFlex, other.sideFlex, t),
        )
    }

    private fun blend(a: Limb, b: Limb, t: Float) = Limb(
        lerp(a.target, b.target, t),
        lerp(a.pole, b.pole, t),
        lerp(a.pitch, b.pitch, t),
        a.contact && b.contact,
        lerp(a.yaw, b.yaw, t),
        lerp(a.roll, b.roll, t),
        if (t < 0.5f) a.hand else b.hand,
    )
}

/** Mirror-symmetric helper: returns (left, right) limbs at x = -x / +x with the pole mirrored. */
fun sym(
    x: Float,
    y: Float,
    z: Float,
    pole: PoleSpec,
    pitch: Float = 0f,
    contact: Boolean = false,
    yaw: Float = 0f,
    roll: Float = 0f,
    hand: HandShape = HandShape.AUTO,
): Pair<Limb, Limb> =
    Limb(Vec3(-x, y, z), Vec3(-pole.out, pole.up, pole.fwd), pitch, contact, yaw, roll, hand) to
        Limb(Vec3(x, y, z), Vec3(pole.out, pole.up, pole.fwd), pitch, contact, yaw, roll, hand)

fun limb(x: Float, y: Float, z: Float, pole: Vec3, pitch: Float = 0f, contact: Boolean = false) =
    Limb(Vec3(x, y, z), pole, pitch, contact)

fun pose(
    anchorAt: Anchor,
    anchor: Vec3,
    lean: Float = 0f,
    spineFlex: Float = 0f,
    headTilt: Float = 0f,
    shrug: Float = 0f,
    look: Float = 0f,
    headRoll: Float = 0f,
    headTurn: Float = 0f,
    sideLean: Float = 0f,
    sideFlex: Float = 0f,
    hands: Pair<Limb, Limb>,
    feet: Pair<Limb, Limb>,
) = Pose(
    anchorAt, anchor, lean, spineFlex, headTilt, shrug, hands.first, hands.second, feet.first, feet.second, look,
    headRoll, headTurn, sideLean, sideFlex,
)

/** Common pole directions. */
object Poles {
    val BACK = PoleSpec(0f, 0f, -1f)
    val FWD = PoleSpec(0f, 0f, 1f)
    val DOWN = PoleSpec(0f, -1f, 0f)
    val UP = PoleSpec(0f, 1f, 0f)
    val OUT = PoleSpec(1f, 0f, 0f)
    val IN = PoleSpec(-1f, 0f, 0f)
    val OUT_DOWN = PoleSpec(0.7f, -0.7f, 0f)
    val OUT_BACK = PoleSpec(0.7f, 0f, -0.7f)
    val BACK_DOWN = PoleSpec(0f, -0.6f, -0.8f)
    val FWD_OUT = PoleSpec(0.4f, 0f, 0.9f)
    val FWD_IN = PoleSpec(-0.5f, 0f, 0.85f)
    val FWD_DOWN = PoleSpec(0f, -0.5f, 0.85f)
    val UP_OUT = PoleSpec(0.6f, 0.8f, 0f)
}
