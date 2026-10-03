package com.personal.calisthenics.core.rig

/**
 * Surface materials of everything the rig draws. Both renderers (Compose canvas in the app, Java2D in the preview
 * tool) fill 2D shapes with [Palette] colours, so the picture is identical everywhere.
 */
enum class Material {
    FLOOR, GRID, SHADOW,
    EQUIPMENT, EQUIPMENT_DARK, EQUIPMENT_LIGHT,
    SKIN, SHIRT, SHORTS, HAIR, SHOE, SOLE, EYE, NAIL,
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
) : Prim

data class DiscPrim(
    val c: Vec2,
    val r: Float,
    override val material: Material,
    override val layer: Layer = Layer.FILL,
    override val depth: Float = 0f,
    override val tone: Float = 1f,
    override val alpha: Float = 1f,
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

/** Shared colour table (ARGB ints, no Android dependency). */
object Palette {
    private val base: Map<Material, Int> = mapOf(
        Material.FLOOR to 0xFF1A212C.toInt(),
        Material.GRID to 0xFF2C3646.toInt(),
        Material.SHADOW to 0xFF05070A.toInt(),
        Material.EQUIPMENT to 0xFFF59E0B.toInt(),
        Material.EQUIPMENT_DARK to 0xFFA86A06.toInt(),
        Material.EQUIPMENT_LIGHT to 0xFFFBBF24.toInt(),
        Material.SKIN to 0xFFE4B48F.toInt(),
        Material.SHIRT to 0xFF8E99AB.toInt(),
        Material.SHORTS to 0xFF445066.toInt(),
        Material.HAIR to 0xFF3A2A1E.toInt(),
        Material.SHOE to 0xFFE9ECF1.toInt(),
        Material.SOLE to 0xFF59647A.toInt(),
        Material.EYE to 0xFF15151A.toInt(),
        Material.NAIL to 0xFFF6DCCB.toInt(),
        Material.BAND to 0xFF22C55E.toInt(),
        Material.MUSCLE to 0xFFEF4444.toInt(),
        Material.TENDON to 0xFF38BDF8.toInt(),
        Material.JOINT to 0xFFFACC15.toInt(),
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

    private fun scale(c: Int, t: Float) = (c * t).toInt().coerceIn(0, 255)

    private fun mix(a: Int, b: Int, t: Float) = (a + (b - a) * t).toInt()
}
