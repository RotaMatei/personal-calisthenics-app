package com.personal.calisthenics.core.rig

/** A close-up illustration drawn directly with 2D primitives (no body pose), for example a hand on a bar. */
class DetailScene(val prims: List<Prim>, val bounds: Bounds)

/**
 * Close-up art for DO/DON'T cards whose subject is not a body position. Keys start with `detail.`.
 * The grip drawings are a cross-section looking along the bar: x points away from the athlete, y is up.
 */
object DetailArt {
    const val GRIP_PALM_CREASE = "detail.grip_palm_crease"
    const val GRIP_HOOK = "detail.grip_hook"

    val keys: Set<String> = setOf(GRIP_PALM_CREASE, GRIP_HOOK)

    fun scene(key: String): DetailScene? = when (key) {
        GRIP_PALM_CREASE -> gripScene(deepInPalm = true)
        GRIP_HOOK -> gripScene(deepInPalm = false)
        else -> null
    }

    private const val BAR_R = 3.0f

    private fun v(x: Float, y: Float) = Vec2(x, y)

    private fun gripScene(deepInPalm: Boolean): DetailScene {
        val prims = mutableListOf<Prim>()
        fun cap(a: Vec2, b: Vec2, ra: Float, rb: Float, role: Role, depth: Float) {
            prims += CapsulePrim(a, b, ra, rb, role, depth)
        }

        // Bar cross-section at the origin.
        val barDepth = if (deepInPalm) 1f else 0f
        prims += DiscPrim(v(0f, 0f), BAR_R, Role.EQUIPMENT, barDepth)

        // The hand is drawn relative to a shift: deep in the palm moves the hand so the bar sits mid-palm.
        val dx = if (deepInPalm) 5.5f else 0f
        val dy = if (deepInPalm) -4.2f else 0f
        fun p(x: Float, y: Float) = v(x + dx, y + dy)

        val wrist = p(-11f, 9.5f)
        val knuckle = p(-3.2f, 3.6f)
        // Forearm and palm.
        cap(p(-17f, 17f), wrist, 3.2f, 3.0f, Role.BODY_FAR, -3f)
        cap(wrist, knuckle, 3.0f, 2.8f, Role.BODY_MID, -2f)

        // Fingers curl over the bar (hook) or hang past it (palm crease).
        val f1End = if (deepInPalm) p(2.2f, 1.2f) else p(1.8f, 2.4f)
        val f2End = if (deepInPalm) p(4.4f, -3.4f) else p(4.6f, -2.6f)
        val f3End = if (deepInPalm) p(1.2f, -6.2f) else p(1.6f, -5.6f)
        cap(knuckle, f1End, 1.35f, 1.2f, Role.BODY_NEAR, 2f)
        cap(f1End, f2End, 1.2f, 1.1f, Role.BODY_NEAR, 2f)
        cap(f2End, f3End, 1.1f, 1.0f, Role.BODY_NEAR, 2f)

        // Thumb wraps under the bar and over the fingertips (also in the wrong picture so only the depth differs).
        val thumbBase = p(-7.2f, 3.4f)
        val thumbMid = if (deepInPalm) p(-3.8f, -4.4f) else p(-3.2f, -4.6f)
        val thumbTip = if (deepInPalm) p(1.6f, -6.2f) else p(2.0f, -6.0f)
        cap(thumbBase, thumbMid, 1.5f, 1.2f, Role.BODY_MID, 3f)
        cap(thumbMid, thumbTip, 1.2f, 1.1f, Role.BODY_MID, 3f)

        // Annotation: red where the bar pinches the palm skin, yellow marker at the correct contact line.
        if (deepInPalm) {
            prims += DiscPrim(v(0f, 0f), BAR_R + 2.0f, Role.MUSCLE, 5f)
        } else {
            prims += DiscPrim(v(-1.2f, 3.8f), 1.5f, Role.JOINT, 5f)
        }

        val bounds = Bounds(-20f, -10f, 12f, 20f)
        return DetailScene(prims, bounds)
    }
}
