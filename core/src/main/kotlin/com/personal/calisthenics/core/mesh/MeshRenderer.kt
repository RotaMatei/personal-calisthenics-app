package com.personal.calisthenics.core.mesh

import com.personal.calisthenics.core.model.HighlightKind
import com.personal.calisthenics.core.rig.Bounds
import com.personal.calisthenics.core.rig.Box
import com.personal.calisthenics.core.rig.Camera
import com.personal.calisthenics.core.rig.Cylinder
import com.personal.calisthenics.core.rig.Ellip3
import com.personal.calisthenics.core.rig.FigureBuilder
import com.personal.calisthenics.core.rig.HandRig
import com.personal.calisthenics.core.rig.HighlightDraw
import com.personal.calisthenics.core.rig.HighlightShapes
import com.personal.calisthenics.core.rig.Layer
import com.personal.calisthenics.core.rig.Palette
import com.personal.calisthenics.core.rig.Pose
import com.personal.calisthenics.core.rig.RigScene
import com.personal.calisthenics.core.rig.RigSolver
import com.personal.calisthenics.core.rig.Skeleton
import com.personal.calisthenics.core.rig.Sphere3
import com.personal.calisthenics.core.rig.Tube3
import com.personal.calisthenics.core.rig.Vec3
import com.personal.calisthenics.core.rig.scaled
import kotlin.math.PI
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

/** Quality knobs of [MeshRenderer]. [supersample] 2 renders at double size and filters down (stills); 1 is for live clips. */
data class MeshOptions(val supersample: Int = 1, val tint: Boolean = true, val floorShadow: Boolean = true)

/**
 * Draws the posed human mesh, the props (bars, boxes, band) and the floor into an ARGB picture with a depth buffer.
 * The body is uncoloured "clay" lit by a soft key light, a fill and a sky/ground term, with a baked cavity darkening
 * from the rest mesh; the stress highlights are muted hues blended over the clay. One instance owns its buffers, so
 * use one per thread.
 */
class MeshRenderer(private val mesh: HumanMesh) {

    private val rig = HumanRig(mesh)
    private val n = mesh.vertexCount

    private val pos = FloatArray(n * 3)
    private val nrm = FloatArray(n * 3)
    private val sx = FloatArray(n)
    private val sy = FloatArray(n)
    private val sz = FloatArray(n)
    private val vr = FloatArray(n)
    private val vg = FloatArray(n)
    private val vb = FloatArray(n)
    private val tintA = FloatArray(n)
    private val tintR = FloatArray(n)
    private val tintG = FloatArray(n)
    private val tintB = FloatArray(n)
    private val cavity: FloatArray by lazy { computeCavity() }
    private var raster: MeshRaster? = null

    /** Draws into [out] (at least `view.w * view.h` ints, row-major, non-premultiplied ARGB; 0 = nothing drawn). */
    fun render(
        scene: RigScene,
        pose: Pose,
        camera: Camera,
        highlights: List<HighlightDraw>,
        view: ViewMap,
        out: IntArray,
        options: MeshOptions = MeshOptions(),
    ) {
        val ss = options.supersample.coerceIn(1, 3)
        val outW = ceil(view.w).toInt()
        val outH = ceil(view.h).toInt()
        val r = raster?.takeIf { it.w == outW * ss && it.h == outH * ss } ?: MeshRaster(outW * ss, outH * ss).also { raster = it }
        r.clear()

        var t = System.nanoTime()
        fun lap(i: Int) { val now = System.nanoTime(); timings[i] += (now - t) / 1e6; t = now }
        val sk = RigSolver.solve(pose)
        skin(rig.bones(pose, sk, scene))
        lap(0)
        if (options.tint) tint(highlights, sk) else java.util.Arrays.fill(tintA, 0f)
        lap(1)

        val proj = Projector(camera, view, ss)
        val light = Lighting(camera)
        projectAll(proj)
        shadeAll(light)
        lap(2)

        drawScene(r, scene, pose, sk, proj, light, options)
        lap(3)
        drawBody(r)
        lap(4)
        r.resolve(out, outW, outH, ss)
        lap(5)
    }

    /** Copy of the posed vertex positions (x, y, z per vertex, cm) for [pose]; used by the stretch tests. */
    fun skinnedPositions(scene: RigScene, pose: Pose): FloatArray {
        val sk = RigSolver.solve(pose)
        skin(rig.bones(pose, sk, scene))
        return pos.copyOf()
    }

    /** Rectangle (view cm, y up) that the posed body mesh covers from [camera]; used to check that clips are framed. */
    fun bodyBounds(scene: RigScene, pose: Pose, camera: Camera): Bounds {
        val sk = RigSolver.solve(pose)
        skin(rig.bones(pose, sk, scene))
        var minX = Float.MAX_VALUE
        var minY = Float.MAX_VALUE
        var maxX = -Float.MAX_VALUE
        var maxY = -Float.MAX_VALUE
        for (v in 0 until n) {
            val p = camera.project(Vec3(pos[v * 3], pos[v * 3 + 1], pos[v * 3 + 2]))
            minX = min(minX, p.x); maxX = max(maxX, p.x)
            minY = min(minY, p.y); maxY = max(maxY, p.y)
        }
        return Bounds(minX, minY, maxX, maxY)
    }

    /** Accumulated milliseconds per phase (skin, tint, project+shade, scene, body, resolve), for profiling. */
    val timings = DoubleArray(6)

    // ------------------------------------------------------------------ skinning

    private fun skin(bones: Array<BoneXf>) {
        val rest = mesh.rest
        val bi = mesh.boneIndex
        val bw = mesh.boneWeight
        for (v in 0 until n) {
            val x = rest[v * 3]
            val y = rest[v * 3 + 1]
            val z = rest[v * 3 + 2]
            var px = 0f
            var py = 0f
            var pz = 0f
            for (k in 0 until 4) {
                val w = bw[v * 4 + k]
                if (w <= 0f) continue
                val b = bones[bi[v * 4 + k]]
                val m = b.m
                val t = b.t
                px += w * (m[0] * x + m[1] * y + m[2] * z + t[0])
                py += w * (m[3] * x + m[4] * y + m[5] * z + t[1])
                pz += w * (m[6] * x + m[7] * y + m[8] * z + t[2])
            }
            pos[v * 3] = px
            pos[v * 3 + 1] = py
            pos[v * 3 + 2] = pz
        }
        normals(pos, nrm)
    }

    /** Smooth vertex normals (area-weighted) of the current positions. */
    private fun normals(p: FloatArray, out: FloatArray) {
        java.util.Arrays.fill(out, 0f)
        val t = mesh.triangles
        for (i in 0 until mesh.triangleCount) {
            val a = t[i * 3] * 3
            val b = t[i * 3 + 1] * 3
            val c = t[i * 3 + 2] * 3
            val ux = p[b] - p[a]; val uy = p[b + 1] - p[a + 1]; val uz = p[b + 2] - p[a + 2]
            val wx = p[c] - p[a]; val wy = p[c + 1] - p[a + 1]; val wz = p[c + 2] - p[a + 2]
            val nx = uy * wz - uz * wy
            val ny = uz * wx - ux * wz
            val nz = ux * wy - uy * wx
            out[a] += nx; out[a + 1] += ny; out[a + 2] += nz
            out[b] += nx; out[b + 1] += ny; out[b + 2] += nz
            out[c] += nx; out[c + 1] += ny; out[c + 2] += nz
        }
        for (v in 0 until n) {
            val x = out[v * 3]; val y = out[v * 3 + 1]; val z = out[v * 3 + 2]
            val l = sqrt(x * x + y * y + z * z)
            if (l > 1e-12f) {
                out[v * 3] = x / l; out[v * 3 + 1] = y / l; out[v * 3 + 2] = z / l
            }
        }
    }

    /** Cavity term of every vertex from the rest mesh: below 1 in creases and sockets, a touch above on ridges. */
    private fun computeCavity(): FloatArray {
        val rest = mesh.rest
        val rn = FloatArray(n * 3)
        normals(rest, rn)
        val sum = FloatArray(n * 3)
        val count = IntArray(n)
        val t = mesh.triangles
        for (i in 0 until mesh.triangleCount) {
            for (e in 0 until 3) {
                val a = t[i * 3 + e]
                val b = t[i * 3 + (e + 1) % 3]
                for (k in 0 until 3) {
                    sum[a * 3 + k] += rest[b * 3 + k]
                    sum[b * 3 + k] += rest[a * 3 + k]
                }
                count[a]++
                count[b]++
            }
        }
        val out = FloatArray(n)
        for (v in 0 until n) {
            if (count[v] == 0) { out[v] = 1f; continue }
            val dx = sum[v * 3] / count[v] - rest[v * 3]
            val dy = sum[v * 3 + 1] / count[v] - rest[v * 3 + 1]
            val dz = sum[v * 3 + 2] / count[v] - rest[v * 3 + 2]
            val c = dx * rn[v * 3] + dy * rn[v * 3 + 1] + dz * rn[v * 3 + 2] // > 0 in a hollow, < 0 on a bump
            out[v] = (1f - CAVITY_GAIN * c).coerceIn(CAVITY_MIN, CAVITY_MAX)
        }
        return out
    }

    // ------------------------------------------------------------------ tint (stress highlights)

    private fun tint(draws: List<HighlightDraw>, sk: Skeleton) {
        java.util.Arrays.fill(tintA, 0f)
        java.util.Arrays.fill(tintR, 0f)
        java.util.Arrays.fill(tintG, 0f)
        java.util.Arrays.fill(tintB, 0f)
        for (d in draws) {
            val rgb = Palette.argb(HighlightShapes.material(d.kind), Layer.FILL, 1f, 1f)
            val cr = ((rgb shr 16) and 0xFF) / 255f
            val cg = ((rgb shr 8) and 0xFF) / 255f
            val cb = (rgb and 0xFF) / 255f
            val peak = HighlightShapes.peakAlpha(d.intensity)
            val spread = HighlightShapes.spread(d.kind)
            for (h in HighlightShapes.shapes(d.region, sk)) {
                val part = when (h.host) {
                    "torso" -> HumanMesh.Part.TORSO
                    "head" -> HumanMesh.Part.HEAD
                    "armL" -> HumanMesh.Part.ARM_L
                    "armR" -> HumanMesh.Part.ARM_R
                    "legL" -> HumanMesh.Part.LEG_L
                    else -> HumanMesh.Part.LEG_R
                }
                val member = mesh.partWeights(part)
                val shape = h.shape.scaled(spread)
                for (v in 0 until n) {
                    val m = member[v]
                    if (m < 0.1f) continue
                    val rn = normalizedDistance(shape, pos[v * 3], pos[v * 3 + 1], pos[v * 3 + 2])
                    if (rn >= 1f) continue
                    val a = peak * falloff(rn) * smooth01((m - 0.1f) / 0.5f)
                    if (a <= 0.004f) continue
                    // "over" compositing in premultiplied space.
                    tintR[v] = tintR[v] * (1f - a) + cr * a
                    tintG[v] = tintG[v] * (1f - a) + cg * a
                    tintB[v] = tintB[v] * (1f - a) + cb * a
                    tintA[v] = tintA[v] * (1f - a) + a
                }
            }
        }
    }

    private fun normalizedDistance(s: com.personal.calisthenics.core.rig.Shape3, x: Float, y: Float, z: Float): Float = when (s) {
        is Sphere3 -> {
            val dx = x - s.c.x; val dy = y - s.c.y; val dz = z - s.c.z
            sqrt(dx * dx + dy * dy + dz * dz) / s.r
        }
        is Tube3 -> {
            val abx = s.b.x - s.a.x; val aby = s.b.y - s.a.y; val abz = s.b.z - s.a.z
            val len2 = abx * abx + aby * aby + abz * abz
            val px = x - s.a.x; val py = y - s.a.y; val pz = z - s.a.z
            val t = if (len2 < 1e-9f) 0f else ((px * abx + py * aby + pz * abz) / len2).coerceIn(0f, 1f)
            val dx = px - abx * t; val dy = py - aby * t; val dz = pz - abz * t
            sqrt(dx * dx + dy * dy + dz * dz) / (s.ra + (s.rb - s.ra) * t)
        }
        is Ellip3 -> {
            val px = x - s.c.x; val py = y - s.c.y; val pz = z - s.c.z
            fun comp(ax: Vec3): Float {
                val l2 = ax.dot(ax)
                return if (l2 < 1e-9f) 0f else (px * ax.x + py * ax.y + pz * ax.z) / l2
            }
            val u = comp(s.ax); val v = comp(s.ay); val w = comp(s.az)
            sqrt(u * u + v * v + w * w)
        }
    }

    /** Radial profile of a wash: full at the centre, fading out smoothly toward the rim (same stops as the capsule washes). */
    private fun falloff(r: Float): Float = when {
        r <= 0.35f -> 1f - (r / 0.35f) * 0.38f
        r <= 0.7f -> 0.62f - ((r - 0.35f) / 0.35f) * 0.40f
        else -> 0.22f * (1f - (r - 0.7f) / 0.3f)
    }

    private fun smooth01(t: Float): Float {
        val x = t.coerceIn(0f, 1f)
        return x * x * (3f - 2f * x)
    }

    // ------------------------------------------------------------------ projection and lighting

    private class Projector(cam: Camera, view: ViewMap, ss: Int) {
        val tx = cam.target.x; val ty = cam.target.y; val tz = cam.target.z
        val rx = cam.right.x; val ry = cam.right.y; val rz = cam.right.z
        val ux = cam.up.x; val uy = cam.up.y; val uz = cam.up.z
        val fx = cam.forward.x; val fy = cam.forward.y; val fz = cam.forward.z
        val d = cam.distance
        val k = cam.perspective
        val scale = view.scale * ss
        val ox = view.ox * ss
        val oy = view.oy * ss
        val minX = view.bounds.minX
        val maxY = view.bounds.maxY

        fun sc(oz: Float): Float = d / (d + k * oz).coerceAtLeast(d * 0.2f)
    }

    private fun projectAll(pr: Projector) {
        for (v in 0 until n) {
            val rx = pos[v * 3] - pr.tx
            val ry = pos[v * 3 + 1] - pr.ty
            val rz = pos[v * 3 + 2] - pr.tz
            val oz = rx * pr.fx + ry * pr.fy + rz * pr.fz
            val s = pr.sc(oz)
            sx[v] = pr.ox + ((rx * pr.rx + ry * pr.ry + rz * pr.rz) * s - pr.minX) * pr.scale
            sy[v] = pr.oy + (pr.maxY - (rx * pr.ux + ry * pr.uy + rz * pr.uz) * s) * pr.scale
            sz[v] = oz
        }
    }

    /** Lights in camera space (x right, y up, z toward the viewer), so the figure is lit the same from every angle. */
    private class Lighting(cam: Camera) {
        val rx = cam.right.x; val ry = cam.right.y; val rz = cam.right.z
        val ux = cam.up.x; val uy = cam.up.y; val uz = cam.up.z
        val cx = cam.toCamera.x; val cy = cam.toCamera.y; val cz = cam.toCamera.z

        /** Brightness 0..~1.1 for a world-space unit normal. */
        fun intensity(nx: Float, ny: Float, nz: Float): Float {
            val x = nx * rx + ny * ry + nz * rz
            val y = nx * ux + ny * uy + nz * uz
            val z = nx * cx + ny * cy + nz * cz
            val key = max(0f, x * KEY_X + y * KEY_Y + z * KEY_Z)
            val fill = max(0f, x * FILL_X + y * FILL_Y + z * FILL_Z)
            val hemi = 0.5f + 0.5f * y
            val rim = (1f - max(0f, z)).let { it * it * it }
            return 0.30f + 0.22f * hemi + 0.52f * key + 0.14f * fill + 0.10f * rim
        }

        private companion object {
            const val KEY_X = -0.45f; const val KEY_Y = 0.62f; const val KEY_Z = 0.64f
            const val FILL_X = 0.78f; const val FILL_Y = 0.12f; const val FILL_Z = 0.62f
        }
    }

    private fun shadeAll(l: Lighting) {
        for (v in 0 until n) {
            val i = l.intensity(nrm[v * 3], nrm[v * 3 + 1], nrm[v * 3 + 2]) * cavity[v]
            val a = tintA[v]
            val r = CLAY_R * (1f - a) + tintR[v]
            val g = CLAY_G * (1f - a) + tintG[v]
            val b = CLAY_B * (1f - a) + tintB[v]
            vr[v] = r * i
            vg[v] = g * i
            vb[v] = b * i
        }
    }

    private fun drawBody(r: MeshRaster) {
        val t = mesh.triangles
        for (i in 0 until mesh.triangleCount) {
            val a = t[i * 3]
            val b = t[i * 3 + 1]
            val c = t[i * 3 + 2]
            r.triangle(
                sx[a], sy[a], sz[a], vr[a], vg[a], vb[a],
                sx[b], sy[b], sz[b], vr[b], vg[b], vb[b],
                sx[c], sy[c], sz[c], vr[c], vg[c], vb[c],
            )
        }
    }

    // ------------------------------------------------------------------ props and floor

    private fun drawScene(r: MeshRaster, scene: RigScene, pose: Pose, sk: Skeleton, pr: Projector, light: Lighting, options: MeshOptions) {
        val draw = PropDrawer(r, pr, light)
        if (scene.floor) drawFloor(draw, scene.floorY, sk, options.floorShadow)
        for (item in scene.equipment) {
            when (item) {
                is Cylinder -> draw.cylinder(item.a, item.b, item.r, EQUIP_R, EQUIP_G, EQUIP_B)
                is Box -> draw.box(item.min, item.max, EQUIP_R, EQUIP_G, EQUIP_B)
            }
        }
        if (scene.band) {
            val a = HandRig.frame(pose.handL, sk.wristL, false).world(4.6f, 0f, 0f)
            val b = HandRig.frame(pose.handR, sk.wristR, true).world(4.6f, 0f, 0f)
            draw.cylinder(a, b, 0.8f, BAND_R, BAND_G, BAND_B)
        }
    }

    private fun drawFloor(draw: PropDrawer, y: Float, sk: Skeleton, shadow: Boolean) {
        val base = Palette.argb(com.personal.calisthenics.core.rig.Material.FLOOR, Layer.FILL, 1f, 1f)
        val grid = Palette.argb(com.personal.calisthenics.core.rig.Material.GRID, Layer.FILL, 1f, 1f)
        val casters = if (shadow) shadowCasters(sk) else emptyList()
        fun dark(x: Float, z: Float): Float {
            var keep = 1f
            for (c in casters) {
                val dx = x - c.x
                val dz = z - c.z
                keep *= 1f - c.k * exp(-(dx * dx + dz * dz) / (2f * c.sigma * c.sigma))
            }
            return keep
        }
        draw.floor(
            -FLOOR_X, FLOOR_X, FLOOR_Z0, FLOOR_Z1, y, FLOOR_STEP, base, grid, GRID_STEP, ::dark,
        )
    }

    private class Caster(val x: Float, val z: Float, val sigma: Float, val k: Float)

    /** Soft blobs under the body parts (horizontal position, size grows with height above the floor). */
    private fun shadowCasters(sk: Skeleton): List<Caster> {
        val out = ArrayList<Caster>()
        fun add(p: Vec3, radius: Float, strength: Float) {
            val h = max(0f, p.y)
            out += Caster(p.x, p.z, radius + 0.22f * h, strength * (1f - min(0.7f, h / 220f)))
        }
        fun seg(a: Vec3, b: Vec3, radius: Float, strength: Float, steps: Int = 3) {
            for (i in 0..steps) add(a + (b - a) * (i / steps.toFloat()), radius, strength)
        }
        seg(sk.hip, sk.shoulder, 13f, 0.30f, 4)
        add(sk.headCenter, 9f, 0.28f)
        seg(sk.shoulderL, sk.elbowL, 5.5f, 0.22f); seg(sk.elbowL, sk.wristL, 4.5f, 0.22f)
        seg(sk.shoulderR, sk.elbowR, 5.5f, 0.22f); seg(sk.elbowR, sk.wristR, 4.5f, 0.22f)
        seg(sk.hipL, sk.kneeL, 7f, 0.28f); seg(sk.kneeL, sk.ankleL, 5f, 0.28f)
        seg(sk.hipR, sk.kneeR, 7f, 0.28f); seg(sk.kneeR, sk.ankleR, 5f, 0.28f)
        seg(sk.heelL, sk.toeL, 5f, 0.35f, 2); seg(sk.heelR, sk.toeR, 5f, 0.35f, 2)
        add(sk.handTipL, 4f, 0.2f); add(sk.handTipR, 4f, 0.2f)
        return out
    }

    /** Builds prop triangles and sends them through the rasteriser with flat or per-vertex colours. */
    private inner class PropDrawer(val r: MeshRaster, val pr: Projector, val light: Lighting) {
        private val px = FloatArray(3); private val py = FloatArray(3); private val pz = FloatArray(3)

        private fun project(p: Vec3, i: Int) {
            val rx = p.x - pr.tx; val ry = p.y - pr.ty; val rz = p.z - pr.tz
            val oz = rx * pr.fx + ry * pr.fy + rz * pr.fz
            val s = pr.sc(oz)
            px[i] = pr.ox + ((rx * pr.rx + ry * pr.ry + rz * pr.rz) * s - pr.minX) * pr.scale
            py[i] = pr.oy + (pr.maxY - (rx * pr.ux + ry * pr.uy + rz * pr.uz) * s) * pr.scale
            pz[i] = oz
        }

        /** Triangle with a per-corner colour (already lit). */
        fun tri(a: Vec3, b: Vec3, c: Vec3, ca: FloatArray, cb: FloatArray, cc: FloatArray, cull: Boolean = true, bias: Float = 0f) {
            project(a, 0); project(b, 1); project(c, 2)
            r.triangle(
                px[0], py[0], pz[0], ca[0], ca[1], ca[2],
                px[1], py[1], pz[1], cb[0], cb[1], cb[2],
                px[2], py[2], pz[2], cc[0], cc[1], cc[2],
                cull, bias,
            )
        }

        private fun lit(n: Vec3, cr: Float, cg: Float, cb: Float): FloatArray {
            val i = light.intensity(n.x, n.y, n.z)
            return floatArrayOf(min(1f, cr * i), min(1f, cg * i), min(1f, cb * i))
        }

        fun flat(a: Vec3, b: Vec3, c: Vec3, col: FloatArray, cull: Boolean = true, bias: Float = 0f) = tri(a, b, c, col, col, col, cull, bias)

        fun quad(a: Vec3, b: Vec3, c: Vec3, d: Vec3, col: FloatArray, cull: Boolean = true, bias: Float = 0f) {
            flat(a, b, c, col, cull, bias)
            flat(a, c, d, col, cull, bias)
        }

        fun box(lo: Vec3, hi: Vec3, cr: Float, cg: Float, cb: Float) {
            fun v(x: Boolean, y: Boolean, z: Boolean) = Vec3(if (x) hi.x else lo.x, if (y) hi.y else lo.y, if (z) hi.z else lo.z)
            // Faces counter-clockwise seen from outside.
            quad(v(true, false, true), v(true, false, false), v(true, true, false), v(true, true, true), lit(Vec3(1f, 0f, 0f), cr, cg, cb))
            quad(v(false, false, false), v(false, false, true), v(false, true, true), v(false, true, false), lit(Vec3(-1f, 0f, 0f), cr, cg, cb))
            quad(v(false, true, true), v(true, true, true), v(true, true, false), v(false, true, false), lit(Vec3(0f, 1f, 0f), cr, cg, cb))
            quad(v(false, false, true), v(true, false, true), v(true, true, true), v(false, true, true), lit(Vec3(0f, 0f, 1f), cr, cg, cb))
            quad(v(true, false, false), v(false, false, false), v(false, true, false), v(true, true, false), lit(Vec3(0f, 0f, -1f), cr, cg, cb))
            quad(v(false, false, false), v(true, false, false), v(true, false, true), v(false, false, true), lit(Vec3(0f, -1f, 0f), cr, cg, cb))
        }

        fun cylinder(a: Vec3, b: Vec3, rad: Float, cr: Float, cg: Float, cb: Float) {
            val u = (b - a).normalized()
            if (u.length() < 0.5f) return
            val ref = if (kotlin.math.abs(u.y) < 0.9f) Vec3(0f, 1f, 0f) else Vec3(1f, 0f, 0f)
            val p = u.cross(ref).normalized()
            val q = u.cross(p).normalized()
            val seg = 14
            fun ring(i: Int): Vec3 {
                val th = (2.0 * PI * i / seg).toFloat()
                return p * cos(th) + q * sin(th)
            }
            for (i in 0 until seg) {
                val n0 = ring(i)
                val n1 = ring(i + 1)
                val a0 = a + n0 * rad; val a1 = a + n1 * rad
                val b0 = b + n0 * rad; val b1 = b + n1 * rad
                val c0 = lit(n0, cr, cg, cb)
                val c1 = lit(n1, cr, cg, cb)
                tri(a0, a1, b0, c0, c1, c0)
                tri(a1, b1, b0, c1, c1, c0)
                val capB = lit(u, cr, cg, cb)
                tri(b, b0, b1, capB, capB, capB)
                val capA = lit(-u, cr, cg, cb)
                tri(a, a1, a0, capA, capA, capA)
            }
        }

        fun floor(x0: Float, x1: Float, z0: Float, z1: Float, y: Float, step: Float, base: Int, grid: Int, gridStep: Float, keep: (Float, Float) -> Float) {
            val br = ((base shr 16) and 0xFF) / 255f; val bg = ((base shr 8) and 0xFF) / 255f; val bb = (base and 0xFF) / 255f
            val gr = ((grid shr 16) and 0xFF) / 255f; val gg = ((grid shr 8) and 0xFF) / 255f; val gb = (grid and 0xFF) / 255f
            val nx = ((x1 - x0) / step).toInt()
            val nz = ((z1 - z0) / step).toInt()
            val shade = Array(nz + 1) { zi -> FloatArray(nx + 1) { xi -> keep(x0 + xi * step, z0 + zi * step) } }
            fun col(xi: Int, zi: Int, r0: Float, g0: Float, b0: Float): FloatArray {
                val k = shade[zi][xi]
                return floatArrayOf(r0 * k, g0 * k, b0 * k)
            }
            fun p(xi: Int, zi: Int) = Vec3(x0 + xi * step, y, z0 + zi * step)
            for (zi in 0 until nz) {
                for (xi in 0 until nx) {
                    val a = p(xi, zi); val b = p(xi + 1, zi); val c = p(xi + 1, zi + 1); val d = p(xi, zi + 1)
                    val ca = col(xi, zi, br, bg, bb); val cb = col(xi + 1, zi, br, bg, bb)
                    val cc = col(xi + 1, zi + 1, br, bg, bb); val cd = col(xi, zi + 1, br, bg, bb)
                    // Upward facing: counter-clockwise seen from above is (x+, z+) order d, c, b ... use a, d, c / a, c, b.
                    tri(a, d, c, ca, cd, cc)
                    tri(a, c, b, ca, cc, cb)
                }
            }
            // Slab sides so the floor still shows as a line when seen edge-on.
            val low = y - FLOOR_THICKNESS
            val side = floatArrayOf(br * 0.8f, bg * 0.8f, bb * 0.8f)
            quad(Vec3(x0, low, z1), Vec3(x1, low, z1), Vec3(x1, y, z1), Vec3(x0, y, z1), side)
            quad(Vec3(x1, low, z0), Vec3(x0, low, z0), Vec3(x0, y, z0), Vec3(x1, y, z0), side)
            quad(Vec3(x1, low, z1), Vec3(x1, low, z0), Vec3(x1, y, z0), Vec3(x1, y, z1), side)
            quad(Vec3(x0, low, z0), Vec3(x0, low, z1), Vec3(x0, y, z1), Vec3(x0, y, z0), side)
            // Grid lines as thin strips just above the floor.
            val half = 0.35f
            var gx = x0
            while (gx <= x1 + 0.1f) {
                val k = keep(gx, 0f)
                val col = floatArrayOf(gr * k, gg * k, gb * k)
                quad(Vec3(gx - half, y, z1), Vec3(gx + half, y, z1), Vec3(gx + half, y, z0), Vec3(gx - half, y, z0), col, bias = 0.05f)
                gx += gridStep
            }
            var gz = -200f
            while (gz <= z1 + 0.1f) {
                val col = floatArrayOf(gr * 0.7f, gg * 0.7f, gb * 0.7f)
                quad(Vec3(x0, y, gz - half), Vec3(x0, y, gz + half), Vec3(x1, y, gz + half), Vec3(x1, y, gz - half), col, bias = 0.05f)
                gz += gridStep
            }
        }
    }

    private companion object {
        // Clay albedo and the muted prop colours (0..1).
        const val CLAY_R = 0.80f; const val CLAY_G = 0.77f; const val CLAY_B = 0.73f
        const val EQUIP_R = 0.78f; const val EQUIP_G = 0.63f; const val EQUIP_B = 0.40f
        const val BAND_R = 0.48f; const val BAND_G = 0.66f; const val BAND_B = 0.53f
        const val CAVITY_GAIN = 1.1f; const val CAVITY_MIN = 0.74f; const val CAVITY_MAX = 1.08f
        const val FLOOR_X = 130f; const val FLOOR_Z0 = -230f; const val FLOOR_Z1 = 190f
        const val FLOOR_STEP = 10f; const val GRID_STEP = 40f; const val FLOOR_THICKNESS = 1.6f
    }
}
