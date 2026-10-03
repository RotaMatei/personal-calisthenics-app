package com.personal.calisthenics.core.rig

import com.personal.calisthenics.core.model.BodyRegion
import com.personal.calisthenics.core.model.Highlight
import com.personal.calisthenics.core.model.HighlightKind

enum class ViewKind(val title: String) { SIDE("Side view"), FRONT("Front view") }

sealed interface Equipment
data class Cylinder(val a: Vec3, val b: Vec3, val r: Float) : Equipment
data class Box(val min: Vec3, val max: Vec3) : Equipment

/** Static props around the figure. [band] draws a resistance band between the two hands. */
data class RigScene(
    val floor: Boolean = true,
    val equipment: List<Equipment> = emptyList(),
    val band: Boolean = false,
    val floorY: Float = 0f,
)

enum class Role { FLOOR, EQUIPMENT, BODY_FAR, BODY_MID, BODY_NEAR, HEAD, BAND, MUSCLE, TENDON, JOINT }

/** Drawing primitives in world centimetres with y pointing up. The UI flips y and scales to fit. */
sealed interface Prim {
    val role: Role
    val depth: Float
}

data class CapsulePrim(
    val a: Vec2,
    val b: Vec2,
    val ra: Float,
    val rb: Float,
    override val role: Role,
    override val depth: Float,
) : Prim

data class DiscPrim(val c: Vec2, val r: Float, override val role: Role, override val depth: Float) : Prim

data class RectPrim(val min: Vec2, val max: Vec2, override val role: Role, override val depth: Float) : Prim

data class FloorPrim(val y: Float) : Prim {
    override val role: Role get() = Role.FLOOR
    override val depth: Float get() = -1e6f
}

object RigRenderer {

    private const val EQUIPMENT_DEPTH = -1e5f
    private const val HIGHLIGHT_DEPTH = 1e4f
    private const val BAND_DEPTH = 2e4f

    fun project(v: Vec3, view: ViewKind): Vec2 = if (view == ViewKind.SIDE) Vec2(v.z, v.y) else Vec2(v.x, v.y)

    private fun depth(v: Vec3, view: ViewKind): Float = if (view == ViewKind.SIDE) -v.x else v.z

    fun render(
        scene: RigScene,
        sk: Skeleton,
        view: ViewKind,
        highlights: List<Highlight> = emptyList(),
    ): List<Prim> {
        val prims = mutableListOf<Prim>()
        if (scene.floor) prims += FloorPrim(scene.floorY)
        for (item in scene.equipment) prims += equipmentPrims(item, view)
        prims += bodyPrims(sk, view)
        for (h in highlights) prims += highlightPrims(h, sk, view)
        if (scene.band) {
            val a = project(sk.handTipL, view)
            val b = project(sk.handTipR, view)
            prims += CapsulePrim(a, b, 0.8f, 0.8f, Role.BAND, BAND_DEPTH)
        }
        return prims.sortedBy { it.depth }
    }

    // ------------------------------------------------------------------ equipment

    private fun equipmentPrims(item: Equipment, view: ViewKind): List<Prim> = when (item) {
        is Cylinder -> listOf(
            CapsulePrim(project(item.a, view), project(item.b, view), item.r, item.r, Role.EQUIPMENT, EQUIPMENT_DEPTH),
        )
        is Box -> {
            val a = project(item.min, view)
            val b = project(item.max, view)
            listOf(
                RectPrim(
                    Vec2(minOf(a.x, b.x), minOf(a.y, b.y)),
                    Vec2(maxOf(a.x, b.x), maxOf(a.y, b.y)),
                    Role.EQUIPMENT,
                    EQUIPMENT_DEPTH,
                ),
            )
        }
    }

    // ------------------------------------------------------------------ body

    private fun bodyPrims(sk: Skeleton, view: ViewKind): List<Prim> {
        val side = view == ViewKind.SIDE
        val out = mutableListOf<Prim>()

        fun cap(a: Vec3, b: Vec3, ra: Float, rb: Float, role: Role, depthBias: Float = 0f) {
            val depthValue = (depth(a, view) + depth(b, view)) / 2f + depthBias
            out += CapsulePrim(project(a, view), project(b, view), ra, rb, role, depthValue)
        }

        // In the side view the figure's right side is farther from the camera than its left side.
        val rightRole = if (side) Role.BODY_FAR else Role.BODY_NEAR
        val leftRole = Role.BODY_NEAR

        // Torso, pelvis and shoulder girdle.
        if (side) {
            cap(sk.hip, sk.mid, 10.5f, 11.5f, Role.BODY_MID)
            cap(sk.mid, sk.shoulder, 11.5f, 12f, Role.BODY_MID)
        } else {
            cap(sk.hip, sk.shoulder, 13f, 17f, Role.BODY_MID)
            cap(sk.shoulderL, sk.shoulderR, 6.5f, 6.5f, Role.BODY_MID)
            cap(sk.hipL, sk.hipR, 7.5f, 7.5f, Role.BODY_MID)
        }
        // Neck and head.
        val neckBase = sk.shoulder
        val headBase = sk.headCenter - (sk.headCenter - sk.shoulder).normalized() * Body.HEAD_RADIUS
        cap(neckBase, headBase, 4.2f, 4.2f, Role.BODY_MID, depthBias = 0.1f)
        out += DiscPrim(project(sk.headCenter, view), Body.HEAD_RADIUS, Role.HEAD, depth(sk.headCenter, view) + 0.2f)
        // Face marker so the facing direction reads in the side view.
        if (side) {
            val nose = sk.headCenter + sk.faceDir * (Body.HEAD_RADIUS - 1f)
            out += DiscPrim(project(nose, view), 1.6f, Role.BODY_FAR, depth(sk.headCenter, view) + 0.4f)
        }

        // Arms.
        cap(sk.shoulderR, sk.elbowR, 5.2f, 4.4f, rightRole)
        cap(sk.elbowR, sk.wristR, 4.4f, 3.6f, rightRole)
        cap(sk.wristR, sk.handTipR, 3.6f, 3.0f, rightRole)
        cap(sk.shoulderL, sk.elbowL, 5.2f, 4.4f, leftRole)
        cap(sk.elbowL, sk.wristL, 4.4f, 3.6f, leftRole)
        cap(sk.wristL, sk.handTipL, 3.6f, 3.0f, leftRole)

        // Legs.
        cap(sk.hipR, sk.kneeR, 8.2f, 6.2f, rightRole)
        cap(sk.kneeR, sk.ankleR, 6.2f, 4.6f, rightRole)
        cap(sk.heelR, sk.toeR, 4f, 3.2f, rightRole)
        cap(sk.hipL, sk.kneeL, 8.2f, 6.2f, leftRole)
        cap(sk.kneeL, sk.ankleL, 6.2f, 4.6f, leftRole)
        cap(sk.heelL, sk.toeL, 4f, 3.2f, leftRole)
        return out
    }

    // ------------------------------------------------------------------ anatomy highlights

    private data class Shape(val a: Vec3, val b: Vec3, val r: Float)

    private fun highlightPrims(h: Highlight, sk: Skeleton, view: ViewKind): List<Prim> {
        val role = when (h.kind) {
            HighlightKind.MUSCLE -> Role.MUSCLE
            HighlightKind.TENDON -> Role.TENDON
            HighlightKind.JOINT -> Role.JOINT
        }
        return regionShapes(h.region, sk).map { s ->
            if ((s.a - s.b).length() < 0.01f) {
                DiscPrim(project(s.a, view), s.r, role, HIGHLIGHT_DEPTH)
            } else {
                CapsulePrim(project(s.a, view), project(s.b, view), s.r, s.r, role, HIGHLIGHT_DEPTH)
            }
        }
    }

    private fun along(a: Vec3, b: Vec3, t: Float) = lerp(a, b, t)

    /** Sagittal-plane normal of a limb segment that points toward the anatomical front (+z for a hanging limb). */
    private fun frontNormal(from: Vec3, to: Vec3): Vec3 {
        val d = (to - from).normalized()
        return Vec3(0f, d.z, -d.y).normalized()
    }

    private fun regionShapes(region: BodyRegion, sk: Skeleton): List<Shape> {
        val front = sk.front
        val sideR = Vec3(1f, 0f, 0f)
        val sideL = Vec3(-1f, 0f, 0f)
        fun torso(t: Float) = along(sk.hip, sk.shoulder, t)
        fun both(f: (Vec3) -> Shape): List<Shape> = listOf(f(sideL), f(sideR))
        val arms = listOf(sk.shoulderL to sk.elbowL, sk.shoulderR to sk.elbowR)
        val forearms = listOf(sk.elbowL to sk.wristL, sk.elbowR to sk.wristR)
        val thighs = listOf(sk.hipL to sk.kneeL, sk.hipR to sk.kneeR)
        val shins = listOf(sk.kneeL to sk.ankleL, sk.kneeR to sk.ankleR)

        /** A capsule along a limb segment, shifted toward the segment's front (+) or back (-). */
        fun limb(segments: List<Pair<Vec3, Vec3>>, t0: Float, t1: Float, r: Float, shift: Float) =
            segments.map { (a, b) ->
                val n = frontNormal(a, b) * shift
                Shape(along(a, b, t0) + n, along(a, b, t1) + n, r)
            }

        return when (region) {
            BodyRegion.LATS -> both { s -> Shape(torso(0.38f) + s * 9f - front * 4f, torso(0.88f) + s * 14f - front * 4f, 5.5f) }
            BodyRegion.MID_BACK -> both { s -> Shape(torso(0.62f) + s * 5f - front * 8f, torso(0.88f) + s * 7f - front * 8f, 4.6f) }
            BodyRegion.LOWER_TRAPS -> listOf(Shape(torso(0.5f) - front * 8f, torso(0.78f) - front * 8f, 5.2f))
            BodyRegion.SERRATUS -> both { s -> Shape(torso(0.42f) + s * 12f, torso(0.7f) + s * 13f, 3.8f) }
            BodyRegion.FRONT_DELT -> listOf(Shape(sk.shoulderL + front * 4f, sk.shoulderL + front * 4f, 6.5f), Shape(sk.shoulderR + front * 4f, sk.shoulderR + front * 4f, 6.5f))
            BodyRegion.REAR_DELT -> listOf(Shape(sk.shoulderL - front * 4f, sk.shoulderL - front * 4f, 6.5f), Shape(sk.shoulderR - front * 4f, sk.shoulderR - front * 4f, 6.5f))
            BodyRegion.PECS -> both { s -> Shape(torso(0.72f) + s * 3f + front * 8f, torso(0.92f) + s * 12f + front * 7f, 6f) }
            BodyRegion.PEC_MINOR -> both { s -> Shape(sk.shoulder + s * 10f + front * 5f - sk.up * 3f, sk.shoulder + s * 13f + front * 4f - sk.up * 7f, 3.8f) }
            BodyRegion.BICEPS -> limb(arms, 0.2f, 0.85f, 5f, 3f)
            BodyRegion.TRICEPS -> limb(arms, 0.15f, 0.8f, 5f, -3f)
            BodyRegion.FOREARM_FLEXORS -> limb(forearms, 0.12f, 0.75f, 4.4f, 2f)
            BodyRegion.FOREARM_EXTENSORS -> limb(forearms, 0.12f, 0.75f, 4.4f, -2f)
            BodyRegion.ABS -> listOf(Shape(torso(0.1f) + front * 8f, torso(0.5f) + front * 8f, 6.5f))
            BodyRegion.HIP_FLEXORS -> listOf(Shape(sk.hipL + front * 4f, sk.hipL + front * 4f, 7f), Shape(sk.hipR + front * 4f, sk.hipR + front * 4f, 7f))
            BodyRegion.GLUTES -> listOf(Shape(sk.hipL - front * 5f, sk.hipL - front * 5f, 9f), Shape(sk.hipR - front * 5f, sk.hipR - front * 5f, 9f))
            BodyRegion.QUADS -> limb(thighs, 0.15f, 0.85f, 6.5f, 3.5f)
            BodyRegion.HAMSTRINGS -> limb(thighs, 0.15f, 0.85f, 6.5f, -3.5f)
            BodyRegion.CALVES -> limb(shins, 0.1f, 0.7f, 5.2f, -2.8f)
            BodyRegion.ERECTORS -> listOf(Shape(torso(0.1f) - front * 8f, torso(0.6f) - front * 8f, 5.2f))
            BodyRegion.DISTAL_BICEPS_TENDON -> listOf(Shape(sk.elbowL, sk.elbowL, 4.6f), Shape(sk.elbowR, sk.elbowR, 4.6f))
            BodyRegion.MEDIAL_ELBOW, BodyRegion.LATERAL_ELBOW -> listOf(Shape(sk.elbowL, sk.elbowL, 5.2f), Shape(sk.elbowR, sk.elbowR, 5.2f))
            BodyRegion.WRIST -> listOf(Shape(sk.wristL, sk.wristL, 5f), Shape(sk.wristR, sk.wristR, 5f))
            BodyRegion.SHOULDER_JOINT -> listOf(Shape(sk.shoulderL, sk.shoulderL, 8f), Shape(sk.shoulderR, sk.shoulderR, 8f))
            BodyRegion.PATELLAR_TENDON -> shins.map { (a, b) -> val p = a + frontNormal(a, b) * 4f; Shape(p, p, 4.6f) }
            BodyRegion.HAMSTRING_TENDON -> shins.map { (a, b) -> val p = a - frontNormal(a, b) * 4f; Shape(p, p, 4.6f) }
            BodyRegion.ACHILLES -> shins.map { (a, b) -> val n = frontNormal(a, b) * -3f; Shape(along(a, b, 0.78f) + n, b + n, 3.4f) }
            BodyRegion.THORACOLUMBAR -> listOf(Shape(torso(0.1f) - front * 9f, torso(0.5f) - front * 9f, 4.8f))
            BodyRegion.KNEE_JOINT -> listOf(Shape(sk.kneeL, sk.kneeL, 8f), Shape(sk.kneeR, sk.kneeR, 8f))
            BodyRegion.ANKLE_JOINT -> listOf(Shape(sk.ankleL, sk.ankleL, 6f), Shape(sk.ankleR, sk.ankleR, 6f))
            BodyRegion.HIP_JOINT -> listOf(Shape(sk.hipL, sk.hipL, 8f), Shape(sk.hipR, sk.hipR, 8f))
        }
    }
}
