package com.personal.calisthenics.core.rig

import com.personal.calisthenics.core.model.BodyRegion
import com.personal.calisthenics.core.model.Highlight
import com.personal.calisthenics.core.model.HighlightKind

/** One anatomy highlight to draw: which region, in which colour family, and how strongly (0..1) right now. */
data class HighlightDraw(val region: BodyRegion, val kind: HighlightKind, val intensity: Float = 0.6f)

/**
 * Highlights as draw requests. [progress] is how far the clip is from Position A (0) to Position B (1), so each
 * highlight pulses toward its own peak; null (a still picture) shows every highlight at its peak strength.
 */
fun List<Highlight>.toDraws(progress: Float? = null): List<HighlightDraw> =
    map { HighlightDraw(it.region, it.kind, it.strengthAt(progress)) }

/** The same shape with every radius multiplied by [k] (axes and centres unchanged). */
internal fun Shape3.scaled(k: Float): Shape3 = when (this) {
    is Sphere3 -> Sphere3(c, r * k)
    is Tube3 -> Tube3(a, b, ra * k, rb * k)
    is Ellip3 -> Ellip3(c, ax * k, ay * k, az * k)
}

/**
 * A patch of colour on a body part. [host] is the id of the body group it sits on, [axis] the point on that part's
 * core nearest the patch, used to tell whether the patch faces the camera (drawn over the part) or points away
 * (hidden behind it).
 */
internal class Hosted(val host: String, val shape: Shape3, val axis: Vec3)

internal object HighlightShapes {

    fun shapes(region: BodyRegion, sk: Skeleton): List<Hosted> {
        val front = sk.front
        val up = sk.up
        fun torso(t: Float) = lerp(sk.hip, sk.shoulder, t)
        val x = sk.side

        fun side(f: (Float) -> Hosted): List<Hosted> = listOf(f(-1f), f(1f))
        fun armId(s: Float) = if (s < 0) "armL" else "armR"
        fun legId(s: Float) = if (s < 0) "legL" else "legR"
        fun shoulderOf(s: Float) = if (s < 0) sk.shoulderL else sk.shoulderR
        fun elbowOf(s: Float) = if (s < 0) sk.elbowL else sk.elbowR
        fun wristOf(s: Float) = if (s < 0) sk.wristL else sk.wristR
        fun hipOf(s: Float) = if (s < 0) sk.hipL else sk.hipR
        fun kneeOf(s: Float) = if (s < 0) sk.kneeL else sk.kneeR
        fun ankleOf(s: Float) = if (s < 0) sk.ankleL else sk.ankleR

        fun tube(host: String, a: Vec3, b: Vec3, r: Float, axis: Vec3) = Hosted(host, Tube3(a, b, r, r), axis)
        fun ball(host: String, c: Vec3, r: Float, axis: Vec3 = c) = Hosted(host, Sphere3(c, r), axis)

        /** A stripe along a limb segment between fractions [t0] and [t1], pushed toward the front by [shift]. */
        fun limb(s: Float, from: Vec3, to: Vec3, host: String, t0: Float, t1: Float, r: Float, shift: Float): Hosted {
            val n = FigureBuilder.frontNormal(from, to) * shift
            return tube(host, lerp(from, to, t0) + n, lerp(from, to, t1) + n, r, lerp(from, to, (t0 + t1) / 2f))
        }

        return when (region) {
            BodyRegion.LATS -> side { s ->
                tube("torso", torso(0.38f) + x * (9f * s) - front * 8.5f, torso(0.88f) + x * (14f * s) - front * 7.5f, 5f, torso(0.6f))
            }
            BodyRegion.MID_BACK -> side { s ->
                tube("torso", torso(0.62f) + x * (5f * s) - front * 9.5f, torso(0.88f) + x * (7f * s) - front * 9.5f, 4.4f, torso(0.75f))
            }
            BodyRegion.LOWER_TRAPS -> listOf(tube("torso", torso(0.5f) - front * 9.5f, torso(0.78f) - front * 9.5f, 5f, torso(0.64f)))
            BodyRegion.SERRATUS -> side { s ->
                tube("torso", torso(0.42f) + x * (14.5f * s), torso(0.7f) + x * (14.5f * s), 3.6f, torso(0.56f))
            }
            BodyRegion.FRONT_DELT -> side { s -> ball(armId(s), shoulderOf(s) + front * 4.6f, 4.8f, shoulderOf(s)) }
            BodyRegion.REAR_DELT -> side { s -> ball(armId(s), shoulderOf(s) - front * 4.6f, 4.8f, shoulderOf(s)) }
            BodyRegion.PECS -> side { s ->
                tube("torso", torso(0.72f) + x * (3f * s) + front * 9f, torso(0.9f) + x * (11f * s) + front * 8f, 5.2f, torso(0.8f))
            }
            BodyRegion.PEC_MINOR -> side { s ->
                tube("torso", sk.shoulder + x * (10f * s) + front * 6.5f - up * 3f, sk.shoulder + x * (12f * s) + front * 6f - up * 7f, 3.4f, torso(0.85f))
            }
            BodyRegion.BICEPS -> side { s -> limb(s, shoulderOf(s), elbowOf(s), armId(s), 0.2f, 0.85f, 3.2f, 3.3f) }
            BodyRegion.TRICEPS -> side { s -> limb(s, shoulderOf(s), elbowOf(s), armId(s), 0.15f, 0.8f, 3.2f, -3.3f) }
            BodyRegion.FOREARM_FLEXORS -> side { s -> limb(s, elbowOf(s), wristOf(s), armId(s), 0.12f, 0.75f, 2.6f, 2.4f) }
            BodyRegion.FOREARM_EXTENSORS -> side { s -> limb(s, elbowOf(s), wristOf(s), armId(s), 0.12f, 0.75f, 2.6f, -2.4f) }
            BodyRegion.ABS -> listOf(tube("torso", torso(0.1f) + front * 8.8f, torso(0.5f) + front * 8.8f, 5.2f, torso(0.3f)))
            BodyRegion.HIP_FLEXORS -> side { s -> ball("torso", hipOf(s) + front * 7f, 5f, sk.hip) }
            BodyRegion.GLUTES -> side { s -> ball("torso", hipOf(s) - front * 8f, 7f, sk.hip) }
            BodyRegion.QUADS -> side { s -> limb(s, hipOf(s), kneeOf(s), legId(s), 0.15f, 0.85f, 4.6f, 4.8f) }
            BodyRegion.HAMSTRINGS -> side { s -> limb(s, hipOf(s), kneeOf(s), legId(s), 0.15f, 0.85f, 4.6f, -4.8f) }
            BodyRegion.CALVES -> side { s -> limb(s, kneeOf(s), ankleOf(s), legId(s), 0.1f, 0.7f, 3.8f, -3.5f) }
            BodyRegion.ERECTORS -> side { s ->
                tube("torso", torso(0.1f) + x * (3.2f * s) - front * 9f, torso(0.6f) + x * (3.2f * s) - front * 9.5f, 3.2f, torso(0.35f))
            }
            BodyRegion.DISTAL_BICEPS_TENDON -> side { s ->
                val n = FigureBuilder.frontNormal(elbowOf(s), wristOf(s))
                ball(armId(s), elbowOf(s) + n * 3.0f, 2.9f, elbowOf(s))
            }
            BodyRegion.MEDIAL_ELBOW -> side { s -> ball(armId(s), elbowOf(s) - x * (3.3f * s), 3.2f, elbowOf(s)) }
            BodyRegion.LATERAL_ELBOW -> side { s -> ball(armId(s), elbowOf(s) + x * (3.3f * s), 3.2f, elbowOf(s)) }
            BodyRegion.WRIST -> side { s -> ball(armId(s), wristOf(s), 3.5f) }
            BodyRegion.SHOULDER_JOINT -> side { s -> ball(armId(s), shoulderOf(s), 6.2f) }
            BodyRegion.PATELLAR_TENDON -> side { s ->
                ball(legId(s), kneeOf(s) + FigureBuilder.frontNormal(kneeOf(s), ankleOf(s)) * 4.2f, 3.0f, kneeOf(s))
            }
            BodyRegion.HAMSTRING_TENDON -> side { s ->
                ball(legId(s), kneeOf(s) - FigureBuilder.frontNormal(kneeOf(s), ankleOf(s)) * 4.2f, 3.0f, kneeOf(s))
            }
            BodyRegion.ACHILLES -> side { s ->
                val k = kneeOf(s)
                val a = ankleOf(s)
                val n = FigureBuilder.frontNormal(k, a) * -3.0f
                tube(legId(s), lerp(k, a, 0.78f) + n, a + n, 2.4f, lerp(k, a, 0.9f))
            }
            BodyRegion.THORACOLUMBAR -> listOf(tube("torso", torso(0.1f) - front * 9.5f, torso(0.5f) - front * 9.5f, 4f, torso(0.3f)))
            BodyRegion.KNEE_JOINT -> side { s -> ball(legId(s), kneeOf(s), 6.2f) }
            BodyRegion.ANKLE_JOINT -> side { s -> ball(legId(s), ankleOf(s), 4.4f) }
            BodyRegion.HIP_JOINT -> side { s -> ball(legId(s), hipOf(s), 6.4f) }
        }
    }

    fun material(kind: HighlightKind): Material = when (kind) {
        HighlightKind.MUSCLE -> Material.MUSCLE
        HighlightKind.TENDON -> Material.TENDON
        HighlightKind.JOINT -> Material.JOINT
    }

    /** How much wider than the anatomical patch the soft wash spreads, per kind (tendon and joint patches are small). */
    private fun spread(kind: HighlightKind): Float = when (kind) {
        HighlightKind.MUSCLE -> 1.9f
        HighlightKind.TENDON -> 2.6f
        HighlightKind.JOINT -> 1.8f
    }

    /** Peak opacity of a wash: a faint hue at rest, still gentle at full load (the figure stays readable). */
    fun peakAlpha(intensity: Float): Float = 0.12f + 0.46f * intensity.coerceIn(0f, 1f)

    /**
     * Soft hue washes for [draws]. A patch facing the camera is a wash right after its host body part; one on the
     * far side is only shown as a faint see-through wash (if [xray]) because the body hides it.
     */
    fun washes(draws: List<HighlightDraw>, sk: Skeleton, cam: Camera, xray: Boolean): List<Wash3> {
        val out = mutableListOf<Wash3>()
        for (d in draws) {
            val material = material(d.kind)
            val alpha = peakAlpha(d.intensity)
            for (h in shapes(d.region, sk)) {
                val centre = when (val s = h.shape) {
                    is Sphere3 -> s.c
                    is Tube3 -> (s.a + s.b) * 0.5f
                    is Ellip3 -> s.c
                }
                val facing = (centre - h.axis).dot(cam.toCamera)
                val spot = h.shape.scaled(spread(d.kind))
                if (facing >= -0.05f) out += Wash3(h.host, listOf(spot), material, alpha)
                else if (xray) out += Wash3(h.host, listOf(spot), material, alpha * 0.55f, onTop = true)
            }
        }
        return out
    }
}
