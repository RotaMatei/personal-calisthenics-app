package com.personal.calisthenics.core.rig

/**
 * Surface materials of everything the rig draws. Both renderers (Compose canvas in the app, Java2D in the preview
 * tool) fill 2D shapes with [Palette] colours, so the picture is identical everywhere.
 */
enum class Material {
    FLOOR, GRID, SHADOW,
    EQUIPMENT, EQUIPMENT_DARK, EQUIPMENT_LIGHT,
    SKIN, SHIRT, SHIRT_TRIM, SHORTS, SHORTS_TRIM, HAIR, SHOE, SOLE, EYE, EYE_WHITE, LIP, NAIL,
    BAND, MUSCLE, TENDON, JOINT, GUIDE, OUTLINE,
}

/** FILL is the base colour, OUTLINE the dark rim drawn first (slightly larger), SHINE a lighter highlight on top. */
enum class Layer { FILL, OUTLINE, SHINE }

/**
 * Drawing primitives in view centimetres with y pointing up (the UI flips y and scales to fit). The list returned
 * by [RigRenderer] is already sorted back to front, so a renderer just paints it in order.
 */
sealed interface Prim {
    val material: Material
    val layer: Layer
    val depth: Float
    val tone: Float
    val alpha: Float
}

data class CapsulePrim(
    val a: Vec2,
    val b: Vec2,
    val ra: Float,
    val rb: Float,
    override val material: Material,
    override val layer: Layer = Layer.FILL,
    override val depth: Float = 0f,
    override val tone: Float = 1f,
    override val alpha: Float = 1f,
    /** True for pieces of a body part that is shaded as a whole (see [Group3.spine]): no gradient of their own. */
    val flat: Boolean = false,
) : Prim

data class DiscPrim(
    val c: Vec2,
    val r: Float,
    override val material: Material,
    override val layer: Layer = Layer.FILL,
    override val depth: Float = 0f,
    override val tone: Float = 1f,
    override val alpha: Float = 1f,
    /** True for pieces of a body part that is shaded as a whole (see [Group3.spine]): no gradient of their own. */
    val flat: Boolean = false,
) : Prim

/** Ellipse with semi-axes [rx] (along [angleDeg], counter-clockwise from +x with y up) and [ry]. */
data class EllipsePrim(
    val c: Vec2,
    val rx: Float,
    val ry: Float,
    val angleDeg: Float,
    override val material: Material,
    override val layer: Layer = Layer.FILL,
    override val depth: Float = 0f,
    override val tone: Float = 1f,
    override val alpha: Float = 1f,
    /** True for pieces of a body part that is shaded as a whole (see [Group3.spine]): no gradient of their own. */
    val flat: Boolean = false,
) : Prim

data class PolyPrim(
    val points: List<Vec2>,
    override val material: Material,
    override val layer: Layer = Layer.FILL,
    override val depth: Float = 0f,
    override val tone: Float = 1f,
    override val alpha: Float = 1f,
) : Prim

data class LinePrim(
    val a: Vec2,
    val b: Vec2,
    val width: Float,
    override val material: Material,
    override val layer: Layer = Layer.FILL,
    override val depth: Float = 0f,
    override val tone: Float = 1f,
    override val alpha: Float = 1f,
) : Prim

/** One soft spot of a [WashPrim]: a round gradient of radius [r] centred at [c] (view centimetres). */
data class WashDisc(val c: Vec2, val r: Float, val weight: Float = 1f)

/**
 * A soft hue wash over the body surface (muscle, tendon or joint load). It is not a shape of its own: each of the
 * [discs] is a radial gradient (strongest at the centre, fading to nothing at the rim) and everything is clipped to
 * the union of the [clip] polygons, the outline of the body part the wash sits on. [alpha] is the peak opacity.
 */
data class WashPrim(
    val clip: List<List<Vec2>>,
    val discs: List<WashDisc>,
    override val material: Material,
    override val alpha: Float,
    override val layer: Layer = Layer.FILL,
    override val depth: Float = 0f,
    override val tone: Float = 1f,
) : Prim

/** Shared colour table (ARGB ints, no Android dependency). */
object Palette {
    private val base: Map<Material, Int> = mapOf(
        Material.FLOOR to 0xFF1A212C.toInt(),
        Material.GRID to 0xFF2C3646.toInt(),
        Material.SHADOW to 0xFF05070A.toInt(),
        Material.EQUIPMENT to 0xFFC7A166.toInt(),
        Material.EQUIPMENT_DARK to 0xFF8C7048.toInt(),
        Material.EQUIPMENT_LIGHT to 0xFFDDBF8C.toInt(),
        Material.SKIN to 0xFFE4B48F.toInt(),
        Material.SHIRT to 0xFF8E99AB.toInt(),
        Material.SHIRT_TRIM to 0xFF6F7A8D.toInt(),
        Material.SHORTS to 0xFF445066.toInt(),
        Material.SHORTS_TRIM to 0xFF34405A.toInt(),
        Material.HAIR to 0xFF3A2A1E.toInt(),
        Material.SHOE to 0xFFE9ECF1.toInt(),
        Material.SOLE to 0xFF59647A.toInt(),
        Material.EYE to 0xFF15151A.toInt(),
        Material.EYE_WHITE to 0xFFF1EEE9.toInt(),
        Material.LIP to 0xFFB5705F.toInt(),
        Material.NAIL to 0xFFF6DCCB.toInt(),
        Material.BAND to 0xFF7AA887.toInt(),
        Material.MUSCLE to 0xFFD9707F.toInt(),
        Material.TENDON to 0xFF4FB8AC.toInt(),
        Material.JOINT to 0xFFDCC48E.toInt(),
        Material.GUIDE to 0xFFFFFFFF.toInt(),
        Material.OUTLINE to 0xFF090D13.toInt(),
    )

    /** Colour of [material] on [layer], scaled by [tone] (depth cueing) and [alpha]. */
    fun argb(material: Material, layer: Layer = Layer.FILL, tone: Float = 1f, alpha: Float = 1f): Int {
        val b = base.getValue(if (layer == Layer.OUTLINE) Material.OUTLINE else material)
        val r0 = (b shr 16) and 0xFF
        val g0 = (b shr 8) and 0xFF
        val b0 = b and 0xFF
        val (r, g, bl) = when (layer) {
            Layer.OUTLINE -> Triple(r0, g0, b0)
            Layer.FILL -> Triple(scale(r0, tone), scale(g0, tone), scale(b0, tone))
            Layer.SHINE -> Triple(
                scale(mix(r0, 255, 0.22f), tone),
                scale(mix(g0, 255, 0.22f), tone),
                scale(mix(b0, 255, 0.22f), tone),
            )
        }
        val a = (alpha.coerceIn(0f, 1f) * 255f + 0.5f).toInt()
        return (a shl 24) or (r shl 16) or (g shl 8) or bl
    }

    /** Materials whose fills get a light-to-dark gradient so the flat shapes read as rounded volumes. */
    fun isShaded(material: Material): Boolean = when (material) {
        Material.FLOOR, Material.GRID, Material.SHADOW, Material.EYE, Material.EYE_WHITE, Material.LIP, Material.NAIL, Material.GUIDE,
        Material.OUTLINE,
        Material.MUSCLE, Material.TENDON, Material.JOINT,
        -> false
        else -> true
    }

    /** The lit, base and shadow colours (ARGB) of [material] for a shaded fill, all scaled by [tone] and carrying [alpha]. */
    fun shadeStops(material: Material, tone: Float, alpha: Float): IntArray {
        val b = base.getValue(material)
        val r0 = scale((b shr 16) and 0xFF, tone)
        val g0 = scale((b shr 8) and 0xFF, tone)
        val b0 = scale(b and 0xFF, tone)
        val a = (alpha.coerceIn(0f, 1f) * 255f + 0.5f).toInt()
        fun pack(r: Int, g: Int, bl: Int) = (a shl 24) or (r shl 16) or (g shl 8) or bl
        return intArrayOf(
            pack(mix(r0, 255, LIT_MIX), mix(g0, 255, LIT_MIX), mix(b0, 255, LIT_MIX)),
            pack(r0, g0, b0),
            pack(mix(r0, 0x0B, SHADE_MIX), mix(g0, 0x0E, SHADE_MIX), mix(b0, 0x16, SHADE_MIX)),
        )
    }

    private const val LIT_MIX = 0.20f
    private const val SHADE_MIX = 0.42f

    private fun scale(c: Int, t: Float) = (c * t).toInt().coerceIn(0, 255)

    private fun mix(a: Int, b: Int, t: Float) = (a + (b - a) * t).toInt()
}
