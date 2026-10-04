package com.personal.calisthenics.core.rig

import kotlin.math.abs

/** Builds the detailed humanoid (torso, head, limbs, hands, feet) as depth-sortable groups. */
internal object FigureBuilder {


    /** Any unit vector perpendicular to [u]. */
    private fun perp(u: Vec3): Vec3 {
        val ref = if (abs(u.y) < 0.9f) Vec3(0f, 1f, 0f) else Vec3(1f, 0f, 0f)
        return u.cross(ref).normalized()
    }

    /** Ellipsoid whose first axis runs along a -> b (half length [half]) and whose other two radii are [rw]. */
    private fun along(a: Vec3, b: Vec3, t: Float, half: Float, rw: Float, offset: Vec3 = Vec3.ZERO): Ellip3 {
        val u = (b - a).normalized()
        val p = perp(u)
        val q = u.cross(p).normalized()
        return Ellip3(lerp(a, b, t) + offset, u * half, p * rw, q * rw)
    }

    /** The sagittal-plane normal of a limb segment pointing toward the anatomical front. */
    fun frontNormal(from: Vec3, to: Vec3): Vec3 {
        val d = (to - from).normalized()
        return Vec3(0f, d.z, -d.y).normalized()
    }

    fun body(pose: Pose, sk: Skeleton, scene: RigScene, cam: Camera): List<Group3> {
        val groups = mutableListOf<Group3>()
        groups += torso(sk)
        groups += head(sk, cam)
        groups += arm("armL", sk.shoulderL, sk.elbowL, sk.wristL)
        groups += arm("armR", sk.shoulderR, sk.elbowR, sk.wristR)
        groups += leg("legL", sk.hipL, sk.kneeL, sk.ankleL)
        groups += leg("legR", sk.hipR, sk.kneeR, sk.ankleR)
        groups += foot("footL", sk.ankleL, pose.footL, right = false)
        groups += foot("footR", sk.ankleR, pose.footR, right = true)
        groups += hand("handL", pose.handL, sk.wristL, right = false, scene = scene, cam = cam)
        groups += hand("handR", pose.handR, sk.wristR, right = true, scene = scene, cam = cam)
        return groups
    }

    // ------------------------------------------------------------------ torso, head

    private fun torso(sk: Skeleton): Group3 {
        val parts = mutableListOf<Part3>()
        // Pelvis in shorts, then a smooth tank-top torso built from overlapping slices (waist narrows, chest widens).
        parts += Part3(
            Ellip3(sk.hip + sk.lowUp * 3.5f, sk.lowSide * 14.6f, sk.lowUp * 10.5f, sk.lowFront * 11.0f),
            Material.SHORTS, shine = false, bias = -60f,
        )
        val slices = 11
        for (i in 0 until slices) {
            val t = 0.20f + 0.70f * i / (slices - 1)
            val k = i / (slices - 1).toFloat()
            val c = lerp(sk.hip, sk.mid, 0f).let { _ ->
                // Follow the two-segment spine: first segment hip -> mid, second mid -> shoulder.
                if (t < 0.5f) lerp(sk.hip, sk.mid, t / 0.5f) else lerp(sk.mid, sk.shoulder, (t - 0.5f) / 0.5f)
            }
            val upv = if (t < 0.5f) sk.lowUp else sk.up
            val fr = if (t < 0.5f) sk.lowFront else sk.front
            val sd = if (t < 0.5f) sk.lowSide else sk.side
            val rx = 12.4f + 3.8f * k
            val rz = 9.6f + 2.2f * k
            parts += Part3(Ellip3(c, sd * rx, upv * 9.0f, fr * rz), Material.SHIRT, shine = i == slices - 1, bias = -30f + i * 0.5f)
        }
        // Trapezius slope from the neck to each shoulder.
        val neckBase = sk.shoulder + sk.up * 1.5f
        parts += Part3(Tube3(neckBase, sk.shoulderL + Vec3(1.5f, -1.2f, 0f), 4.4f, 3.8f), Material.SKIN, shine = false, bias = -10f)
        parts += Part3(Tube3(neckBase, sk.shoulderR + Vec3(-1.5f, -1.2f, 0f), 4.4f, 3.8f), Material.SKIN, shine = false, bias = -10f)
        return Group3("torso", parts)
    }

    private fun head(sk: Skeleton, cam: Camera): List<Group3> {
        val c = sk.headCenter
        val faceUp = sk.headUp
        val faceFront = sk.faceDir
        val hs = sk.headSide
        val parts = mutableListOf<Part3>()
        // Neck.
        parts += Part3(Tube3(sk.shoulder + sk.up * 1.0f, c - faceUp * 7.5f, 4.6f, 4.2f), Material.SKIN, shine = false, bias = -10f)
        // Skull and jaw.
        parts += Part3(Ellip3(c, hs * 7.9f, faceUp * 10.2f, faceFront * 9.4f), Material.SKIN)
        parts += Part3(Ellip3(c - faceUp * 4.2f + faceFront * 1.6f, hs * 6.2f, faceUp * 5.4f, faceFront * 7.2f), Material.SKIN, shine = false)
        // Ears, nose, eyes: they sit on the surface, so depth sorting hides them on the far side.
        for (s in listOf(-1f, 1f)) {
            parts += Part3(Sphere3(c + hs * (7.8f * s) - faceFront * 0.6f - faceUp * 0.4f, 1.7f), Material.SKIN, shine = false)
            parts += Part3(Sphere3(c + hs * (3.1f * s) + faceFront * 8.5f + faceUp * 2.2f, 0.95f), Material.EYE, shine = false, bias = 2f)
        }
        parts += Part3(Sphere3(c + faceFront * 9.6f - faceUp * 0.9f, 1.6f), Material.SKIN, shine = false, bias = 1f)
        val out = mutableListOf(Group3("head", parts))

        // Hair: behind the skull from the front, over its back and top from the side.
        val hairParts = listOf(
            Part3(Ellip3(c + faceUp * 1.9f - faceFront * 2.9f, hs * 8.1f, faceUp * 10.3f, faceFront * 7.4f), Material.HAIR, shine = false),
        )
        val toCamera = cam.toCamera
        val frontOn = toCamera.dot(faceFront) > 0.35f
        out += Group3("hair", hairParts, relativeTo = "head", rel = if (frontOn) -0.004f else 0.004f, outlineScale = 0.8f)
        return out
    }

    // ------------------------------------------------------------------ limbs

    private fun arm(id: String, shoulder: Vec3, elbow: Vec3, wrist: Vec3): Group3 {
        val parts = listOf(
            Part3(Sphere3(shoulder, 5.3f), Material.SKIN),
            Part3(Tube3(shoulder, elbow, 4.6f, 3.6f), Material.SKIN),
            Part3(Sphere3(elbow, 3.5f), Material.SKIN, shine = false),
            Part3(Tube3(elbow, wrist, 3.7f, 2.5f), Material.SKIN),
            Part3(along(elbow, wrist, 0.3f, 8.5f, 4.0f), Material.SKIN, shine = false),
        )
        return Group3(id, parts)
    }

    private fun leg(id: String, hip: Vec3, knee: Vec3, ankle: Vec3): Group3 {
        val mid = lerp(hip, knee, 0.5f)
        val back = frontNormal(knee, ankle) * -1.4f
        val parts = listOf(
            Part3(Tube3(mid, knee, 6.6f, 5.1f), Material.SKIN, shine = false),
            Part3(Sphere3(knee, 5.1f), Material.SKIN, shine = false),
            Part3(Tube3(knee, ankle, 4.9f, 3.1f), Material.SKIN),
            Part3(along(knee, ankle, 0.3f, 9.5f, 4.4f, back), Material.SKIN, shine = false),
            Part3(Sphere3(ankle, 3.1f), Material.SKIN, shine = false),
            Part3(Tube3(hip, mid, 8.0f, 6.8f), Material.SHORTS, bias = 20f),
        )
        return Group3(id, parts)
    }

    private fun foot(id: String, ankle: Vec3, limb: Limb, right: Boolean): Group3 {
        val sign = if (right) 1f else -1f
        fun rotY(v: Vec3, deg: Float): Vec3 {
            val a = rad(deg)
            return Vec3(v.x * kotlin.math.cos(a) + v.z * kotlin.math.sin(a), v.y, -v.x * kotlin.math.sin(a) + v.z * kotlin.math.cos(a))
        }
        val yaw = limb.yaw * sign
        val f = rotY(pitchDir(limb.pitch), yaw)
        val up = rotY(pitchDir(limb.pitch - 90f), yaw)
        val lat = up.cross(f).normalized()
        val c = ankle + f * 6.0f - up * 4.0f
        val parts = listOf(
            Part3(Ellip3(c, f * 10.0f, up * 4.0f, lat * 4.6f), Material.SHOE),
            Part3(Ellip3(c - up * 3.3f, f * 10.2f, up * 0.95f, lat * 4.7f), Material.SOLE, shine = false, bias = 5f),
        )
        return Group3(id, parts, outlineScale = 0.8f)
    }

    // ------------------------------------------------------------------ hands

    private fun hand(id: String, limb: Limb, wrist: Vec3, right: Boolean, scene: RigScene, cam: Camera): List<Group3> {
        // A relaxed arm hanging at the side turns its palm toward the thigh.
        val l = if (limb.hand == HandShape.AUTO && limb.roll == 0f && !limb.contact && limb.pitch in 45f..135f) limb.copy(roll = -90f) else limb
        val frame = HandRig.frame(l, wrist, right)
        val (shape, bar) = resolveGrip(l, frame, wrist, scene)
        return HandRig.build(id, frame, shape, bar, right, toCamera = cam.toCamera)
    }

    private fun resolveGrip(limb: Limb, frame: HandFrame, wrist: Vec3, scene: RigScene): Pair<HandShape, BarWrap?> {
        val bar = findBar(scene, frame)
        return when (limb.hand) {
            HandShape.AUTO -> when {
                bar != null -> HandShape.HOOK to bar
                limb.contact && wrist.y < 14f -> HandShape.FLAT to null
                else -> HandShape.RELAXED to null
            }
            HandShape.HOOK -> HandShape.HOOK to bar
            else -> limb.hand to null
        }
    }

    /** The scene bar the fingers would wrap around, if one lies where a hooked hand would hold it. */
    fun findBar(scene: RigScene, frame: HandFrame): BarWrap? {
        val expected = frame.world(HandRig.KNUCKLE, 3.0f, 0f)
        var best: BarWrap? = null
        var bestDist = 4.5f
        for (e in scene.equipment) {
            if (e !is Cylinder) continue
            val ab = e.b - e.a
            val t = ((expected - e.a).dot(ab) / ab.dot(ab)).coerceIn(0f, 1f)
            val p = e.a + ab * t
            val dist = (p - expected).length()
            if (dist < bestDist) {
                bestDist = dist
                best = BarWrap(p, e.r)
            }
        }
        return best
    }
}
