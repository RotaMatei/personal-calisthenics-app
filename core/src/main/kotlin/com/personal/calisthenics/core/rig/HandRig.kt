package com.personal.calisthenics.core.rig

import kotlin.math.asin
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.tan

/** A bar the fingers wrap around: axis point nearest the hand and the bar radius (cm). */
internal data class BarWrap(val center: Vec3, val radius: Float)

/**
 * Orientation of a hand. [wrist] is the wrist joint, [d] points from the wrist toward the knuckles, [n] out of the
 * palm and [s] toward the thumb side. The palm slab is centred [PALM_OFFSET] cm from the wrist along [n].
 */
internal class HandFrame(val wrist: Vec3, val d: Vec3, val n: Vec3, val s: Vec3) {
    val origin: Vec3 get() = wrist + n * HandRig.PALM_OFFSET

    fun world(x: Float, y: Float, z: Float): Vec3 = origin + d * x + n * y + s * z
}

/** Builds an articulated hand: palm, four fingers (three phalanges each) and a three-bone thumb. */
internal object HandRig {
    const val PALM_OFFSET = 1.4f
    const val KNUCKLE = 9.2f

    private class Finger(val s: Float, val lens: List<Float>, val spread: Float, val curl: Float, val thick: Float)

    private val fingers = listOf(
        Finger(3.2f, listOf(4.1f, 2.4f, 2.0f), 7f, 0.95f, 1.0f),
        Finger(1.1f, listOf(4.5f, 2.8f, 2.2f), 1f, 1.0f, 1.0f),
        Finger(-1.0f, listOf(4.2f, 2.6f, 2.1f), -5f, 1.05f, 0.97f),
        Finger(-3.0f, listOf(3.4f, 2.0f, 1.9f), -12f, 1.2f, 0.88f),
    )

    private val radii = listOf(1.0f, 0.92f, 0.82f, 0.72f)

    // ------------------------------------------------------------------ frame

    fun frame(limb: Limb, wrist: Vec3, right: Boolean): HandFrame {
        val sign = if (right) 1f else -1f
        var d = rotY(pitchDir(limb.pitch), limb.yaw * sign)
        var n = rotY(pitchDir(limb.pitch + 90f), limb.yaw * sign)
        if (limb.roll != 0f) n = rotateAbout(n, d, limb.roll * sign)
        d = d.normalized()
        n = (n - d * n.dot(d)).normalized()
        val s = n.cross(d).normalized() * sign
        return HandFrame(wrist, d, n, s)
    }

    private fun rotY(v: Vec3, deg: Float): Vec3 {
        val a = rad(deg)
        return Vec3(v.x * cos(a) + v.z * sin(a), v.y, -v.x * sin(a) + v.z * cos(a))
    }

    private fun rotateAbout(v: Vec3, axis: Vec3, deg: Float): Vec3 {
        val a = rad(deg)
        val k = axis.normalized()
        return v * cos(a) + k.cross(v) * sin(a) + k * (k.dot(v) * (1f - cos(a)))
    }

    // ------------------------------------------------------------------ geometry

    /** Finger joint flexion (MCP, PIP, DIP) in degrees toward the palm for each shape. */
    private fun curls(shape: HandShape): List<Float> = when (shape) {
        HandShape.FLAT -> listOf(-3f, 3f, 3f)
        HandShape.OPEN -> listOf(8f, 12f, 6f)
        HandShape.RELAXED, HandShape.AUTO -> listOf(24f, 34f, 18f)
        HandShape.FIST -> listOf(88f, 100f, 62f)
        HandShape.HOOK -> listOf(50f, 95f, 60f)
    }

    /** Local 2D (d, n) joint positions of a finger bent by [angles]. */
    private fun bentChain(start: Vec2, lens: List<Float>, angles: List<Float>, curl: Float): List<Vec2> {
        val out = mutableListOf(start)
        var ang = 0f
        var p = start
        for (i in lens.indices) {
            ang += angles[i] * curl
            p = Vec2(p.x + cos(rad(ang)) * lens[i], p.y + sin(rad(ang)) * lens[i])
            out += p
        }
        return out
    }

    /**
     * Joint positions of a chain that hugs a circle (the bar) of radius [rw] around [c], starting at [start] and
     * wrapping counter-clockwise ([ccw]) or clockwise. The first bone leaves [start] and lands on the circle;
     * later bones are chords of the circle.
     */
    private fun wrapChain(start: Vec2, c: Vec2, rw: Float, lens: List<Float>, ccw: Boolean): List<Vec2> {
        val sgn = if (ccw) 1f else -1f
        val out = mutableListOf(start)
        val phi0 = atan2(start.y - c.y, start.x - c.x)
        fun onCircle(phi: Float) = Vec2(c.x + rw * cos(phi), c.y + rw * sin(phi))
        val l1 = lens[0]
        var phi = phi0
        var landed: Vec2? = null
        if (hypot(start.x - c.x, start.y - c.y) - rw > l1) {
            // Too far from the bar to touch it: just point at it.
            val dx = c.x - start.x
            val dy = c.y - start.y
            val len = hypot(dx, dy)
            val p = Vec2(start.x + dx / len * l1, start.y + dy / len * l1)
            phi = atan2(p.y - c.y, p.x - c.x)
            landed = p
        } else {
            for (i in 1..720) {
                val ang = phi0 + sgn * rad(i * 0.5f)
                val q = onCircle(ang)
                if (hypot(q.x - start.x, q.y - start.y) >= l1) {
                    phi = ang
                    landed = q
                    break
                }
            }
        }
        val first = landed ?: onCircle(phi0 + sgn * rad(180f)).also { phi = phi0 + sgn * rad(180f) }
        out += first
        for (k in 1 until lens.size) {
            phi += sgn * 2f * asin(min(1f, lens[k] / (2f * rw)))
            out += onCircle(phi)
        }
        return out
    }

    // ------------------------------------------------------------------ build

    /**
     * Groups for one hand. [id] prefixes the group ids ("handL" gives "handL.palm", "handL.f0a", ...).
     * [bar] is only used by [HandShape.HOOK].
     */
    fun build(
        id: String,
        f: HandFrame,
        shape: HandShape,
        bar: BarWrap?,
        right: Boolean,
        /** Which of the four fingers (0 = index .. 3 = little) to build; null builds all. */
        fingerMask: Set<Int>? = null,
        withThumb: Boolean = true,
        /** Hook-only: local (d, n) centre of the wrapped circle overriding the bar centre (close-up art). */
        wrapOverride: Vec2? = null,
        /** Direction toward the camera: fingernails are only built on fingers whose back faces it. */
        toCamera: Vec3? = null,
        /** False draws only the plain palm slab (cross-section drawings). */
        palmPads: Boolean = true,
    ): List<Group3> {
        val groups = mutableListOf<Group3>()
        val resolved = if (shape == HandShape.AUTO) HandShape.RELAXED else shape

        // Palm: slab, thumb-side and little-finger-side pads and a knuckle bump at the base of each finger.
        val palmParts = mutableListOf(
            Part3(Ellip3(f.world(4.6f, 0f, 0f), f.d * 5.0f, f.n * 1.75f, f.s * 4.3f), Material.SKIN),
        )
        if (palmPads) {
            palmParts += Part3(Ellip3(f.world(3.0f, 0.1f, 2.9f), f.d * 3.4f, f.n * 1.9f, f.s * 2.4f), Material.SKIN, shine = false)
            palmParts += Part3(Ellip3(f.world(4.6f, 0f, -2.5f), f.d * 4.0f, f.n * 1.5f, f.s * 1.8f), Material.SKIN, shine = false)
        }
        for (fg in fingers) palmParts += Part3(Sphere3(f.world(KNUCKLE - 0.1f, 0f, fg.s), 1.15f * fg.thick), Material.SKIN, shine = false)
        groups += Group3("$id.palm", palmParts, outlineScale = 0.6f)

        val wrap = if (resolved == HandShape.HOOK && bar != null) {
            wrapOverride ?: bar.center.let { c ->
                val rel = c - f.origin
                Vec2(rel.dot(f.d), rel.dot(f.n))
            }
        } else {
            null
        }

        // Fingers: proximal phalanx and the distal pair are separate groups so the knuckle line shows.
        val angles = curls(resolved)
        fingers.forEachIndexed { i, fg ->
            if (fingerMask != null && i !in fingerMask) return@forEachIndexed
            val knuckle = Vec2(KNUCKLE, 0f)
            val chain = if (wrap != null) {
                wrapChain(knuckle, wrap, bar!!.radius + 0.95f * fg.thick, fg.lens, ccw = true)
            } else {
                bentChain(knuckle, fg.lens, angles, fg.curl)
            }
            val spread = tan(rad(if (resolved == HandShape.FLAT || resolved == HandShape.OPEN) fg.spread else fg.spread * 0.3f))
            fun at(p: Vec2) = f.world(p.x, p.y, fg.s + (p.x - KNUCKLE).coerceAtLeast(0f) * spread)
            val pts = chain.map { at(it) }
            val r = radii.map { it * fg.thick }
            groups += Group3(
                "$id.f${i}a",
                listOf(Part3(Tube3(pts[0], pts[1], r[0], r[1]), Material.SKIN, shine = false)),
                outlineScale = 0.42f,
            )
            val distal = mutableListOf(
                Part3(Tube3(pts[1], pts[2], r[1], r[2]), Material.SKIN, shine = false),
                Part3(Tube3(pts[2], pts[3], r[2], r[3]), Material.SKIN, shine = false),
            )
            if (toCamera != null) {
                val dx = chain[3].x - chain[2].x
                val dy = chain[3].y - chain[2].y
                val dl = hypot(dx, dy)
                val u = f.d * (dx / dl) + f.n * (dy / dl)
                val pn = f.d * (-dy / dl) + f.n * (dx / dl)
                val dorsal = -pn
                if (dorsal.dot(toCamera) > 0.15f) {
                    distal += Part3(
                        Ellip3(pts[3] - u * 0.95f + dorsal * (0.55f * fg.thick), u * 0.9f, dorsal * 0.2f, f.s * (0.5f * fg.thick)),
                        Material.NAIL, shine = false, bias = 3f,
                    )
                }
            }
            groups += Group3("$id.f${i}b", distal, outlineScale = 0.42f)
        }

        // Thumb: metacarpal, proximal and distal bones.
        val sT = 1f
        val cmc = f.world(1.8f, -0.3f, 2.9f * sT)
        val thumbParts: List<Part3>
        if (wrap != null) {
            val mp = Vec2(5.0f, 0.6f)
            val c2 = wrapChain(mp, wrap, bar!!.radius + 1.2f, listOf(3.3f, 2.7f), ccw = false)
            val mpW = f.world(mp.x, mp.y, 3.9f)
            val p1 = f.world(c2[1].x, c2[1].y, 4.0f)
            val p2 = f.world(c2[2].x, c2[2].y, 3.9f)
            thumbParts = listOf(
                Part3(Tube3(cmc, mpW, 1.7f, 1.4f), Material.SKIN, shine = false),
                Part3(Tube3(mpW, p1, 1.4f, 1.2f), Material.SKIN, shine = false),
                Part3(Tube3(p1, p2, 1.2f, 1.0f), Material.SKIN, shine = false),
            )
        } else {
            val (theta, flex1, flex2) = when (resolved) {
                HandShape.FLAT -> Triple(52f, 0f, 0f)
                HandShape.OPEN -> Triple(46f, 8f, 5f)
                HandShape.FIST -> Triple(24f, 55f, 45f)
                else -> Triple(36f, 20f, 15f)
            }
            val mc = (f.d * cos(rad(theta)) + f.s * sin(rad(theta))).normalized()
            val mp = cmc + mc * 4.0f
            val prox = (mc * cos(rad(flex1)) + f.n * sin(rad(flex1))).normalized()
            val p1 = mp + prox * 3.3f
            val dist = (prox * cos(rad(flex2)) + f.n * sin(rad(flex2))).normalized()
            val p2 = p1 + dist * 2.7f
            thumbParts = listOf(
                Part3(Tube3(cmc, mp, 1.7f, 1.4f), Material.SKIN, shine = false),
                Part3(Tube3(mp, p1, 1.4f, 1.2f), Material.SKIN, shine = false),
                Part3(Tube3(p1, p2, 1.2f, 1.0f), Material.SKIN, shine = false),
            )
        }
        if (withThumb) groups += Group3("$id.thumb", thumbParts, outlineScale = 0.5f)
        return groups
    }
}
