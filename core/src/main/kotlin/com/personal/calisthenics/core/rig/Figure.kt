package com.personal.calisthenics.core.rig

import kotlin.math.abs

internal class HandSetup(val frame: HandFrame, val shape: HandShape, val bar: BarWrap?)

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
        // Pelvis in shorts with a waistband, then a smooth tank-top torso built from overlapping slices
        // (waist narrows, chest widens into a V). The pieces are flat; the group is shaded as a whole.
        parts += Part3(
            Ellip3(sk.hip + sk.lowUp * 3.5f, sk.lowSide * 14.6f, sk.lowUp * 10.5f, sk.lowFront * 11.0f),
            Material.SHORTS, shine = false, bias = -60f, flat = true,
        )
        val slices = 17
        for (i in 0 until slices) {
            val t = 0.22f + 0.70f * i / (slices - 1)
            val k = i / (slices - 1).toFloat()
            // Follow the two-segment spine: first segment hip -> mid, second mid -> shoulder.
            val c = if (t < 0.5f) lerp(sk.hip, sk.mid, t / 0.5f) else lerp(sk.mid, sk.shoulder, (t - 0.5f) / 0.5f)
            val upv = if (t < 0.5f) sk.lowUp else sk.up
            val fr = if (t < 0.5f) sk.lowFront else sk.front
            val sd = if (t < 0.5f) sk.lowSide else sk.side
            val taper = k * k * (3f - 2f * k)
            val rx = 11.8f + 5.2f * taper
            val rz = 9.2f + 3.0f * kotlin.math.sin(k * 2.2f)
            parts += Part3(Ellip3(c, sd * rx, upv * 6.2f, fr * rz), Material.SHIRT, shine = false, bias = -30f + i * 0.5f, flat = true)
        }
        // The trapezius slope from the neck to each shoulder.
        val neckBase = sk.shoulder + sk.up * 1.5f
        parts += Part3(Tube3(neckBase, sk.shoulderL + Vec3(1.5f, -1.2f, 0f), 4.4f, 3.8f), Material.SKIN, shine = false, bias = -10f, flat = true)
        parts += Part3(Tube3(neckBase, sk.shoulderR + Vec3(-1.5f, -1.2f, 0f), 4.4f, 3.8f), Material.SKIN, shine = false, bias = -10f, flat = true)
        return Group3("torso", parts, spine = Triple(sk.hip, sk.shoulder, 15.5f))
    }

    private fun head(sk: Skeleton, cam: Camera): List<Group3> {
        val c = sk.headCenter
        val faceUp = sk.headUp
        val faceFront = sk.faceDir
        val hs = sk.headSide
        val parts = mutableListOf<Part3>()
        // Neck.
        parts += Part3(Tube3(sk.shoulder + sk.up * 1.0f, c - faceUp * 7.5f, 5.0f, 4.5f), Material.SKIN, shine = false, bias = -10f)
        // Skull and jaw.
        parts += Part3(Ellip3(c, hs * 7.9f, faceUp * 10.2f, faceFront * 9.4f), Material.SKIN)
        parts += Part3(Ellip3(c - faceUp * 4.2f + faceFront * 1.6f, hs * 6.4f, faceUp * 5.4f, faceFront * 7.2f), Material.SKIN, shine = false)
        // Hair: a fringe over the top of the forehead (always over the skull), and a back cap that sits behind the
        // skull when the face is toward the camera and over it from the side and the back.
        val frontOn = cam.toCamera.dot(faceFront) > 0.55f
        parts += Part3(
            Ellip3(c + faceUp * 8.7f + faceFront * 0.3f, hs * 8.2f, faceUp * 3.3f, faceFront * 8.3f),
            Material.HAIR, shine = false, bias = 6f,
        )
        parts += Part3(
            Ellip3(c + faceUp * 1.9f - faceFront * 2.9f, hs * 8.3f, faceUp * 10.5f, faceFront * 7.6f),
            Material.HAIR, shine = false, bias = if (frontOn) -20f else 5f,
        )
        // Ears, eyes, brows, nose, mouth: they sit on the surface, so depth sorting hides them on the far side.
        for (s in listOf(-1f, 1f)) {
            parts += Part3(Sphere3(c + hs * (7.7f * s) - faceFront * 0.4f - faceUp * 1.2f, 1.8f), Material.SKIN, shine = false, bias = 7f)
            parts += Part3(Ellip3(c + hs * (3.2f * s) + faceFront * 8.2f + faceUp * 1.6f, hs * 1.55f, faceUp * 1.05f, faceFront * 0.7f), Material.EYE_WHITE, shine = false, bias = 9f)
            parts += Part3(Sphere3(c + hs * (3.2f * s) + faceFront * 8.8f + faceUp * 1.6f, 0.8f), Material.EYE, shine = false, bias = 10f)
            parts += Part3(
                Tube3(
                    c + hs * (1.7f * s) + faceFront * 8.4f + faceUp * 4.0f,
                    c + hs * (4.9f * s) + faceFront * 7.8f + faceUp * 4.5f, 0.45f, 0.4f,
                ),
                Material.HAIR, shine = false, bias = 9f,
            )
        }
        parts += Part3(Sphere3(c + faceFront * 9.3f - faceUp * 1.2f, 1.25f), Material.SKIN, shine = false, bias = 8f)
        parts += Part3(
            Tube3(c + hs * (-1.9f) + faceFront * 8.2f - faceUp * 4.4f, c + hs * 1.9f + faceFront * 8.2f - faceUp * 4.4f, 0.5f, 0.5f),
            Material.LIP, shine = false, bias = 9f,
        )
        return listOf(Group3("head", parts))
    }

    // ------------------------------------------------------------------ limbs

    private fun arm(id: String, shoulder: Vec3, elbow: Vec3, wrist: Vec3): Group3 {
        val belly = lerp(shoulder, elbow, 0.42f)
        val forearm = lerp(elbow, wrist, 0.22f)
        val parts = listOf(
            Part3(Sphere3(shoulder, 5.7f), Material.SKIN),
            Part3(Tube3(shoulder, belly, 5.4f, 5.0f), Material.SKIN),
            Part3(Tube3(belly, elbow, 5.0f, 3.7f), Material.SKIN),
            Part3(Sphere3(elbow, 3.7f), Material.SKIN, shine = false),
            Part3(Tube3(elbow, forearm, 3.7f, 3.9f), Material.SKIN),
            Part3(Tube3(forearm, wrist, 3.9f, 2.5f), Material.SKIN),
        )
        return Group3(id, parts)
    }

    private fun leg(id: String, hip: Vec3, knee: Vec3, ankle: Vec3): Group3 {
        val mid = lerp(hip, knee, 0.5f)
        val back = frontNormal(knee, ankle) * -1.4f
        val parts = listOf(
            Part3(Tube3(mid, knee, 6.9f, 5.2f), Material.SKIN, shine = false),
            Part3(Sphere3(knee, 5.2f), Material.SKIN, shine = false),
            Part3(Tube3(knee, ankle, 4.9f, 3.1f), Material.SKIN),
            Part3(along(knee, ankle, 0.3f, 9.5f, 4.6f, back), Material.SKIN, shine = false),
            Part3(Sphere3(ankle, 3.1f), Material.SKIN, shine = false),
            Part3(Tube3(hip, mid, 8.2f, 7.1f), Material.SHORTS, bias = 20f),
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
        val setup = handSetup(limb, wrist, right, scene)
        return HandRig.build(id, setup.frame, setup.shape, setup.bar, right, toCamera = cam.toCamera)
    }

    /** Orientation, finger shape and gripped bar (if any) of a hand; shared by the capsule and the skinned-mesh figures. */
    fun handSetup(limb: Limb, wrist: Vec3, right: Boolean, scene: RigScene): HandSetup {
        // A relaxed arm hanging at the side turns its palm toward the thigh.
        val l = if (limb.hand == HandShape.AUTO && limb.roll == 0f && !limb.contact && limb.pitch in 45f..135f) limb.copy(roll = -90f) else limb
        val frame = HandRig.frame(l, wrist, right)
        val (shape, bar) = resolveGrip(l, frame, wrist, scene)
        return HandSetup(frame, shape, bar)
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
