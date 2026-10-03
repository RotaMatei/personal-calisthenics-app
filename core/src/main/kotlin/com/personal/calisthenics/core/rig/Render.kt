package com.personal.calisthenics.core.rig

import kotlin.math.ceil

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

object RigRenderer {

    private const val FLOOR_X = 130f
    private const val FLOOR_Z0 = -230f
    private const val FLOOR_Z1 = 190f
    private const val GRID_STEP = 40f

    /** Materials that make up the ground, ignored when framing the picture. */
    val groundMaterials = setOf(Material.FLOOR, Material.GRID, Material.SHADOW)

    fun render(
        scene: RigScene,
        pose: Pose,
        view: ViewKind,
        highlights: List<HighlightDraw> = emptyList(),
        options: RenderOptions = RenderOptions(),
    ): List<Prim> = render(scene, pose, view.camera, highlights, options)

    /** Everything visible for [pose] in [scene] as sorted 2D primitives (back to front). */
    fun render(
        scene: RigScene,
        pose: Pose,
        camera: Camera,
        highlights: List<HighlightDraw> = emptyList(),
        options: RenderOptions = RenderOptions(),
    ): List<Prim> {
        val sk = RigSolver.solve(pose)
        val painter = Painter(camera, options)
        val groups = mutableListOf<Group3>()
        groups += FigureBuilder.body(pose, sk, scene, camera)
        groups += HighlightShapes.groups(highlights, sk, camera, options.xray)
        if (scene.band) groups += bandGroups(pose, sk)

        if (scene.floor) painter.raw(-1e9f, floorPrims(scene, camera))
        for ((i, item) in scene.equipment.withIndex()) {
            when (item) {
                is Cylinder -> groups += cylinderGroups(i, item)
                is Box -> painter.raw(painter.keyOf((item.min + item.max) * 0.5f), boxPrims(item, camera))
            }
        }
        painter.add(groups)
        return painter.finish()
    }

    /** Renders ready-made [groups] (close-up scenes) without a pose, floor or highlights. */
    fun renderGroups(groups: List<Group3>, camera: Camera, options: RenderOptions = RenderOptions()): List<Prim> {
        val painter = Painter(camera, options)
        painter.add(groups)
        return painter.finish()
    }

    // ------------------------------------------------------------------ equipment

    /** Bars are cut into short pieces so hands and arms can be sorted in front of or behind each one. */
    internal fun cylinderGroups(index: Int, c: Cylinder): List<Group3> {
        val length = (c.b - c.a).length()
        val pieces = ceil(length / 6f).toInt().coerceAtLeast(1)
        return (0 until pieces).map { k ->
            val a = lerp(c.a, c.b, k / pieces.toFloat())
            val b = lerp(c.a, c.b, (k + 1) / pieces.toFloat())
            Group3(
                "eq.$index.$k",
                listOf(Part3(Tube3(a, b, c.r, c.r), Material.EQUIPMENT, shine = false)),
                outline = false,
                depthCue = false,
            )
        }
    }

    private fun boxPrims(box: Box, cam: Camera): List<Prim> {
        val lo = box.min
        val hi = box.max
        fun v(x: Boolean, y: Boolean, z: Boolean) = Vec3(if (x) hi.x else lo.x, if (y) hi.y else lo.y, if (z) hi.z else lo.z)
        class Face(val n: Vec3, val corners: List<Vec3>)
        val faces = listOf(
            Face(Vec3(1f, 0f, 0f), listOf(v(true, false, false), v(true, true, false), v(true, true, true), v(true, false, true))),
            Face(Vec3(-1f, 0f, 0f), listOf(v(false, false, false), v(false, true, false), v(false, true, true), v(false, false, true))),
            Face(Vec3(0f, 1f, 0f), listOf(v(false, true, false), v(true, true, false), v(true, true, true), v(false, true, true))),
            Face(Vec3(0f, 0f, 1f), listOf(v(false, false, true), v(true, false, true), v(true, true, true), v(false, true, true))),
            Face(Vec3(0f, 0f, -1f), listOf(v(false, false, false), v(true, false, false), v(true, true, false), v(false, true, false))),
        )
        val out = mutableListOf<Prim>()
        for (f in faces) {
            val centre = f.corners.fold(Vec3.ZERO) { acc, p -> acc + p } * 0.25f
            if ((cam.position - centre).dot(f.n) <= 0f) continue
            val material = when {
                f.n.y > 0.5f -> Material.EQUIPMENT_LIGHT
                kotlin.math.abs(f.n.x) > 0.5f -> Material.EQUIPMENT_DARK
                else -> Material.EQUIPMENT
            }
            val pts = f.corners.map { p -> cam.project(p).let { Vec2(it.x, it.y) } }
            out += PolyPrim(pts, material)
        }
        return out
    }

    private fun floorPrims(scene: RigScene, cam: Camera): List<Prim> {
        val y = scene.floorY
        fun p(x: Float, z: Float) = cam.project(Vec3(x, y, z)).let { Vec2(it.x, it.y) }
        val out = mutableListOf<Prim>()
        out += PolyPrim(listOf(p(-FLOOR_X, FLOOR_Z0), p(FLOOR_X, FLOOR_Z0), p(FLOOR_X, FLOOR_Z1), p(-FLOOR_X, FLOOR_Z1)), Material.FLOOR)
        var x = -FLOOR_X
        while (x <= FLOOR_X + 0.1f) {
            out += LinePrim(p(x, FLOOR_Z0), p(x, FLOOR_Z1), 0.35f, Material.GRID)
            x += GRID_STEP
        }
        var z = -200f
        while (z <= FLOOR_Z1 + 0.1f) {
            out += LinePrim(p(-FLOOR_X, z), p(FLOOR_X, z), 0.35f, Material.GRID)
            z += GRID_STEP
        }
        if (cam.pitch < 4f) out += LinePrim(p(0f, FLOOR_Z0), p(0f, FLOOR_Z1), 0.8f, Material.GRID)
        return out
    }

    private fun bandGroups(pose: Pose, sk: Skeleton): List<Group3> {
        val a = HandRig.frame(pose.handL, sk.wristL, false).world(4.6f, 0f, 0f)
        val b = HandRig.frame(pose.handR, sk.wristR, true).world(4.6f, 0f, 0f)
        val length = (b - a).length()
        val pieces = ceil(length / 8f).toInt().coerceAtLeast(1)
        return (0 until pieces).map { k ->
            val p0 = lerp(a, b, k / pieces.toFloat())
            val p1 = lerp(a, b, (k + 1) / pieces.toFloat())
            Group3("band.$k", listOf(Part3(Tube3(p0, p1, 0.8f, 0.8f), Material.BAND, shine = false)), outline = false, depthCue = false)
        }
    }
}
