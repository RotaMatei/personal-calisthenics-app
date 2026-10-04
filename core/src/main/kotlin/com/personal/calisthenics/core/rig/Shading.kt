package com.personal.calisthenics.core.rig

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/** A gradient to fill a shaped primitive with: stops are [lit, base, shadow] ARGB at 0, 0.5 and 1. */
sealed interface Shade {
    val colors: IntArray
}

/** Gradient across a limb: lit side at [from], shadow side at [to]. */
class LinearShade(val from: Vec2, val to: Vec2, override val colors: IntArray) : Shade

/** Gradient over a rounded shape: lit at [centre] (pushed toward the light), shadow at distance [radius]. */
class RadialShade(val centre: Vec2, val radius: Float, override val colors: IntArray) : Shade

/**
 * Light and form shading shared by every renderer: where the light comes from, the gradient of each shaded
 * primitive, and the polygons of its outline (used to clip washes and shadows to a body part).
 */
object Shading {
    /** Direction of the light in the picture (x right, y up), unit length. */
    val lightX: Float
    val lightY: Float

    init {
        val o = RenderOptions()
        val l = hypot(o.lightX, o.lightY)
        lightX = o.lightX / l
        lightY = o.lightY / l
    }

    private fun Prim.isFlat(): Boolean = when (this) {
        is DiscPrim -> flat
        is EllipsePrim -> flat
        is CapsulePrim -> flat
        else -> false
    }

    /** The shade of a FILL primitive, or null when it should be filled flat. */
    fun shadeOf(p: Prim): Shade? {
        if (p.layer != Layer.FILL || !Palette.isShaded(p.material) || p.isFlat()) return null
        val colors = Palette.shadeStops(p.material, p.tone, p.alpha)
        return when (p) {
            is DiscPrim -> RadialShade(Vec2(p.c.x + lightX * p.r * 0.38f, p.c.y + lightY * p.r * 0.38f), p.r * 1.42f, colors)
            is EllipsePrim -> {
                val small = min(p.rx, p.ry)
                val big = max(p.rx, p.ry)
                RadialShade(Vec2(p.c.x + lightX * small * 0.38f, p.c.y + lightY * small * 0.38f), (small * 0.5f + big * 0.5f) * 1.42f, colors)
            }
            is CapsulePrim -> {
                val dx = p.b.x - p.a.x
                val dy = p.b.y - p.a.y
                val len = hypot(dx, dy)
                val mid = Vec2((p.a.x + p.b.x) / 2f, (p.a.y + p.b.y) / 2f)
                val r = (p.ra + p.rb) / 2f
                if (len < 1e-3f) {
                    RadialShade(Vec2(mid.x + lightX * r * 0.38f, mid.y + lightY * r * 0.38f), r * 1.42f, colors)
                } else {
                    // The light component across the limb decides which edge is lit.
                    val tx = dx / len
                    val ty = dy / len
                    val dot = lightX * tx + lightY * ty
                    var px = lightX - tx * dot
                    var py = lightY - ty * dot
                    val pl = hypot(px, py)
                    if (pl < 1e-3f) { px = -ty; py = tx } else { px /= pl; py /= pl }
                    // Strength of the lighting across the limb: a limb pointing at the light is shaded less.
                    val k = r * (0.55f + 0.45f * pl.coerceAtMost(1f))
                    LinearShade(Vec2(mid.x + px * k, mid.y + py * k), Vec2(mid.x - px * k, mid.y - py * k), colors)
                }
            }
            is PolyPrim, is LinePrim, is WashPrim -> null
        }
    }

    /** Outline of a body primitive as polygons in view centimetres, all wound the same way so their union is a clip. */
    fun polygons(p: Prim): List<List<Vec2>> = when (p) {
        is DiscPrim -> listOf(circle(p.c, p.r))
        is EllipsePrim -> listOf(ellipse(p.c, p.rx, p.ry, p.angleDeg))
        is CapsulePrim -> {
            val dx = p.b.x - p.a.x
            val dy = p.b.y - p.a.y
            val len = hypot(dx, dy)
            val out = mutableListOf(circle(p.a, p.ra), circle(p.b, p.rb))
            if (len > 1e-3f) {
                val nx = -dy / len
                val ny = dx / len
                out += oriented(
                    listOf(
                        Vec2(p.a.x + nx * p.ra, p.a.y + ny * p.ra), Vec2(p.b.x + nx * p.rb, p.b.y + ny * p.rb),
                        Vec2(p.b.x - nx * p.rb, p.b.y - ny * p.rb), Vec2(p.a.x - nx * p.ra, p.a.y - ny * p.ra),
                    ),
                )
            }
            out
        }
        is PolyPrim -> listOf(oriented(p.points))
        is LinePrim, is WashPrim -> emptyList()
    }

    private const val SEGMENTS = 32

    private fun circle(c: Vec2, r: Float): List<Vec2> = ellipse(c, r, r, 0f)

    private fun ellipse(c: Vec2, rx: Float, ry: Float, angleDeg: Float): List<Vec2> {
        val a = angleDeg * (PI.toFloat() / 180f)
        val ca = cos(a)
        val sa = sin(a)
        return (0 until SEGMENTS).map { i ->
            val t = (2.0 * PI * i / SEGMENTS).toFloat()
            val x = rx * cos(t)
            val y = ry * sin(t)
            Vec2(c.x + x * ca - y * sa, c.y + x * sa + y * ca)
        }
    }

    /** [points] wound counter-clockwise (positive area in a y-up frame). */
    private fun oriented(points: List<Vec2>): List<Vec2> {
        var area = 0f
        for (i in points.indices) {
            val p = points[i]
            val q = points[(i + 1) % points.size]
            area += p.x * q.y - q.x * p.y
        }
        return if (area >= 0f) points else points.reversed()
    }
}
