package com.personal.calisthenics.core.rig

import kotlin.math.atan2
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/** 3D building blocks of the figure and props (world centimetres). */
sealed interface Shape3

data class Sphere3(val c: Vec3, val r: Float) : Shape3

/** Tapered capsule from [a] (radius [ra]) to [b] (radius [rb]). */
data class Tube3(val a: Vec3, val b: Vec3, val ra: Float, val rb: Float) : Shape3

/** Ellipsoid centred at [c] with three semi-axis vectors (they need not be unit length, but should be orthogonal). */
data class Ellip3(val c: Vec3, val ax: Vec3, val ay: Vec3, val az: Vec3) : Shape3

/** One shape plus its look. [bias] forces draw order inside its group (higher = drawn later / on top). */
data class Part3(
    val shape: Shape3,
    val material: Material,
    val shine: Boolean = true,
    val bias: Float = 0f,
    val alpha: Float = 1f,
)

/**
 * A set of parts that share one dark outline (so a limb reads as one object). Groups are depth-sorted against each
 * other; [relativeTo] pins a group right behind (negative [rel]) or in front of (positive) another group instead,
 * which is how hair and anatomy highlights stay attached to their host.
 */
class Group3(
    val id: String,
    val parts: List<Part3>,
    val outline: Boolean = true,
    val relativeTo: String? = null,
    val rel: Float = 0.002f,
    val depthCue: Boolean = true,
    val outlineScale: Float = 1f,
)

data class RenderOptions(
    /** Thickness of the dark rim around every group, in cm. */
    val outlineCm: Float = 0.3f,
    /** Direction of the light on the screen (x right, y up). */
    val lightX: Float = -0.5f,
    val lightY: Float = 0.8f,
    /** Draw faint "x-ray" copies of anatomy highlights that sit behind other body parts. */
    val xray: Boolean = true,
    val shadows: Boolean = true,
)

/** Sorts groups back to front and turns their 3D shapes into 2D primitives for [camera]. */
internal class Painter(private val cam: Camera, private val opts: RenderOptions) {

    private class Entry(val k1: Float, val k2: Float, val prim: Prim)

    private val entries = mutableListOf<Entry>()
    companion object {
        /** Pseudo group id: draw in front of everything else. */
        const val TOP = "@top"
    }

    private val lightLen = sqrt(opts.lightX * opts.lightX + opts.lightY * opts.lightY)
    private val lx = opts.lightX / lightLen
    private val ly = opts.lightY / lightLen

    fun nearOf(shape: Shape3): Float = when (shape) {
        is Sphere3 -> cam.project(shape.c).near
        is Tube3 -> cam.project((shape.a + shape.b) * 0.5f).near
        is Ellip3 -> cam.project(shape.c).near
    }

    /** Add already-projected primitives that sort as one unit at [key] (floor, boxes). */
    fun raw(key: Float, prims: List<Prim>) {
        prims.forEachIndexed { i, p -> entries += Entry(key, i.toFloat(), p) }
    }

    /** Depth key of a world point (same scale as the group keys). */
    fun keyOf(p: Vec3): Float = cam.project(p).near

    /** Add [groups]; returns nothing, call [finish] for the sorted primitives. */
    fun add(groups: List<Group3>) {
        val keys = HashMap<String, Float>()
        for (g in groups) {
            if (g.relativeTo != null || g.parts.isEmpty()) continue
            keys[g.id] = g.parts.map { nearOf(it.shape) }.average().toFloat()
        }
        val maxKey = keys.values.maxOrNull() ?: 0f
        var lo = Float.MAX_VALUE
        var hi = -Float.MAX_VALUE
        for (g in groups) {
            if (!g.depthCue) continue
            val k = keys[g.id] ?: continue
            lo = min(lo, k)
            hi = max(hi, k)
        }
        for (g in groups) {
            if (g.parts.isEmpty()) continue
            val key = when {
                g.relativeTo == TOP -> maxKey + 1f + g.rel
                g.relativeTo != null -> (keys[g.relativeTo] ?: 0f) + g.rel
                else -> keys.getValue(g.id)
            }
            val tone = if (g.depthCue && hi > lo + 1f) {
                val n = ((key - lo) / (hi - lo)).coerceIn(0f, 1f)
                0.74f + 0.30f * n
            } else {
                1f
            }
            emit(g, key, tone)
        }
    }

    fun finish(): List<Prim> = entries.sortedWith(compareBy<Entry>({ it.k1 }, { it.k2 })).map { it.prim }

    private fun emit(g: Group3, key: Float, tone: Float) {
        if (g.outline) {
            for (p in g.parts) {
                val prim = project(p, Layer.OUTLINE, grow = opts.outlineCm * g.outlineScale, tone = 1f, depth = key)
                if (prim != null) entries += Entry(key, -1e6f, prim)
            }
        }
        for (p in g.parts) {
            val near = nearOf(p.shape) + p.bias
            project(p, Layer.FILL, 0f, tone, key)?.let { entries += Entry(key, near, it) }
            if (p.shine) project(p, Layer.SHINE, 0f, tone, key)?.let { entries += Entry(key, near + 1e-3f, it) }
        }
    }

    // ------------------------------------------------------------------ projection of one part

    private fun project(part: Part3, layer: Layer, grow: Float, tone: Float, depth: Float): Prim? {
        val m = part.material
        val a = part.alpha
        return when (val s = part.shape) {
            is Sphere3 -> {
                val p = cam.project(s.c)
                val r = (s.r + grow) * p.scale
                when (layer) {
                    Layer.SHINE -> DiscPrim(
                        Vec2(p.x + lx * s.r * 0.30f * p.scale, p.y + ly * s.r * 0.30f * p.scale),
                        s.r * 0.46f * p.scale, m, layer, depth, tone, a,
                    )
                    else -> DiscPrim(Vec2(p.x, p.y), r, m, layer, depth, tone, a)
                }
            }
            is Tube3 -> {
                val pa = cam.project(s.a)
                val pb = cam.project(s.b)
                val ra = (s.ra + grow) * pa.scale
                val rb = (s.rb + grow) * pb.scale
                if (layer != Layer.SHINE) {
                    CapsulePrim(Vec2(pa.x, pa.y), Vec2(pb.x, pb.y), ra, rb, m, layer, depth, tone, a)
                } else {
                    shineCapsule(pa, pb, s.ra * pa.scale, s.rb * pb.scale, m, depth, tone, a)
                }
            }
            is Ellip3 -> {
                val p = cam.project(s.c)
                ellipse(s, p, grow, if (layer == Layer.SHINE) 0.5f else 1f, layer, m, depth, tone, a)
            }
        }
    }

    private fun shineCapsule(pa: Projected, pb: Projected, ra: Float, rb: Float, m: Material, depth: Float, tone: Float, alpha: Float): Prim {
        val dx = pb.x - pa.x
        val dy = pb.y - pa.y
        val len = sqrt(dx * dx + dy * dy)
        if (len < 1e-3f) {
            return DiscPrim(Vec2(pa.x + lx * ra * 0.3f, pa.y + ly * ra * 0.3f), ra * 0.45f, m, Layer.SHINE, depth, tone, alpha)
        }
        val tx = dx / len
        val ty = dy / len
        val dot = lx * tx + ly * ty
        var px = lx - tx * dot
        var py = ly - ty * dot
        val pl = sqrt(px * px + py * py)
        if (pl < 1e-3f) { px = -ty; py = tx } else { px /= pl; py /= pl }
        val shrink = 0.10f
        val ax = pa.x + dx * shrink + px * ra * 0.34f
        val ay = pa.y + dy * shrink + py * ra * 0.34f
        val bx = pb.x - dx * shrink + px * rb * 0.34f
        val by = pb.y - dy * shrink + py * rb * 0.34f
        return CapsulePrim(Vec2(ax, ay), Vec2(bx, by), ra * 0.40f, rb * 0.40f, m, Layer.SHINE, depth, tone, alpha)
    }

    private fun ellipse(
        s: Ellip3, p: Projected, grow: Float, factor: Float, layer: Layer,
        m: Material, depth: Float, tone: Float, alpha: Float,
    ): Prim {
        fun grown(v: Vec3): Vec3 {
            val l = v.length()
            return if (l < 1e-4f) v else v * ((l + grow) / l)
        }
        val axes = listOf(grown(s.ax), grown(s.ay), grown(s.az))
        var a = 0f
        var b = 0f
        var d = 0f
        for (v in axes) {
            val x = v.dot(cam.right) * p.scale * factor
            val y = v.dot(cam.up) * p.scale * factor
            a += x * x
            b += x * y
            d += y * y
        }
        val mean = (a + d) / 2f
        val diff = (a - d) / 2f
        val root = sqrt(diff * diff + b * b)
        val rx = sqrt(max(mean + root, 1e-6f))
        val ry = sqrt(max(mean - root, 1e-6f))
        val angle = Math.toDegrees(0.5 * atan2((2f * b).toDouble(), (a - d).toDouble())).toFloat()
        var cx = p.x
        var cy = p.y
        if (layer == Layer.SHINE) {
            val r = min(rx, ry)
            cx += lx * r * 0.55f
            cy += ly * r * 0.55f
        }
        return EllipsePrim(Vec2(cx, cy), rx, ry, angle, m, layer, depth, tone, alpha)
    }
}
