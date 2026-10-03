package com.personal.calisthenics.core.rig

/** Where a callout's text sits: in the left or right gutter beside the picture, stacked by [Callout.order]. */
enum class CalloutSide { LEFT, RIGHT }

/** A label with a leader line to [target] (view cm of its panel). The UI draws the text in the gutter. */
data class Callout(val text: String, val target: Vec2, val side: CalloutSide, val order: Int = 0)

/** One drawing of a close-up, with its own camera framing and labels. */
class DetailPanel(val title: String, val prims: List<Prim>, val bounds: Bounds, val callouts: List<Callout>)

/** A close-up illustration made of one or more labelled panels (no full-body pose). */
class DetailScene(val panels: List<DetailPanel>) {
    val prims: List<Prim> get() = panels.flatMap { it.prims }
    val bounds: Bounds get() = panels.first().bounds
}

/**
 * Close-up art for DO/DON'T cards whose subject is not a body position. Keys start with `detail.`.
 * The grip drawings show a real 3D hand (palm, four fingers, thumb) on a bar: one panel from the front so the four
 * fingers and the thumb can be told apart, one cross-section along the bar showing where the bar sits in the hand.
 */
object DetailArt {
    const val GRIP_PALM_CREASE = "detail.grip_palm_crease"
    const val GRIP_HOOK = "detail.grip_hook"

    val keys: Set<String> = setOf(GRIP_PALM_CREASE, GRIP_HOOK)

    fun scene(key: String): DetailScene? = when (key) {
        GRIP_PALM_CREASE -> gripScene(deepInPalm = true)
        GRIP_HOOK -> gripScene(deepInPalm = false)
        else -> null
    }

    private const val BAR_R = 2.2f
    private const val BAR_HALF = 8.5f

    private class GripModel(
        val frame: HandFrame,
        val bar: BarWrap,
        val wrap: Vec2,
        val hand: List<Group3>,
        val forearm: Group3,
        val barGroups: List<Group3>,
    )

    private fun gripModel(deep: Boolean, fingerMask: Set<Int>?, withThumb: Boolean, cam: Camera, shape: HandShape = HandShape.HOOK, pads: Boolean = true): GripModel {
        val frame = HandFrame(Vec3.ZERO, Vec3(0f, 1f, 0f), Vec3(0f, 0f, 1f), Vec3(-1f, 0f, 0f))
        val wrap = if (deep) Vec2(5.4f, 2.7f) else Vec2(HandRig.KNUCKLE, 3.0f)
        val centre = frame.origin + frame.d * wrap.x + frame.n * wrap.y
        val bar = BarWrap(centre, BAR_R)
        val hand = HandRig.build("hand", frame, shape, bar, right = true, fingerMask = fingerMask, withThumb = withThumb, wrapOverride = wrap, toCamera = cam.toCamera, palmPads = pads)
        val forearm = Group3(
            "forearm",
            listOf(
                Part3(Tube3(Vec3(0f, -30f, 0f), Vec3(0f, 0f, 0f), 4.2f, 2.5f), Material.SKIN),
                Part3(Sphere3(Vec3(0f, 0f, 0f), 2.5f), Material.SKIN, shine = false),
            ),
        )
        val barGroups = RigRenderer.cylinderGroups(0, Cylinder(centre + Vec3(-BAR_HALF, 0f, 0f), centre + Vec3(BAR_HALF, 0f, 0f), BAR_R))
        return GripModel(frame, bar, wrap, hand, forearm, barGroups)
    }

    private fun tipOf(groups: List<Group3>, id: String): Vec3 {
        val g = groups.first { it.id == id }
        return g.parts.last { it.shape is Tube3 }.let { (it.shape as Tube3).b }
    }

    /** View bounds that frame only the hand and bar (the long forearm is cropped by the panel). */
    private fun boundsOfPoints(cam: Camera, groups: List<Group3>, margin: Float): Bounds {
        var minX = Float.MAX_VALUE
        var minY = Float.MAX_VALUE
        var maxX = -Float.MAX_VALUE
        var maxY = -Float.MAX_VALUE
        fun inc(p: Vec3, r: Float) {
            val q = cam.project(p)
            minX = minOf(minX, q.x - r * q.scale); minY = minOf(minY, q.y - r * q.scale)
            maxX = maxOf(maxX, q.x + r * q.scale); maxY = maxOf(maxY, q.y + r * q.scale)
        }
        for (g in groups) for (part in g.parts) when (val s = part.shape) {
            is Sphere3 -> inc(s.c, s.r)
            is Tube3 -> { inc(s.a, s.ra); inc(s.b, s.rb) }
            is Ellip3 -> { inc(s.c + s.ax, 0f); inc(s.c - s.ax, 0f); inc(s.c + s.ay, 0f); inc(s.c - s.ay, 0f); inc(s.c + s.az, 0f); inc(s.c - s.az, 0f) }
        }
        return Bounds(minX - margin, minY - margin, maxX + margin, maxY + margin)
    }

    private fun band(id: String, frame: HandFrame, d: Float, material: Material, alpha: Float): Group3 = Group3(
        id,
        listOf(Part3(Tube3(frame.world(d, 1.7f, -4.0f), frame.world(d, 1.7f, 4.0f), 0.9f, 0.9f), material, shine = false, alpha = alpha)),
        outline = false, relativeTo = Painter.TOP, rel = 0f, depthCue = false,
    )

    private fun gripScene(deepInPalm: Boolean): DetailScene {
        val opts = RenderOptions(outlineCm = 0.16f, xray = false)
        val wrapPoint = if (deepInPalm) Vec2(5.4f, 2.7f) else Vec2(HandRig.KNUCKLE, 3.0f)
        val barCentre = Vec3(0f, wrapPoint.x, HandRig.PALM_OFFSET + wrapPoint.y)
        val camO = Camera(yaw = 0f, pitch = 0f, target = Vec3(0f, 8f, HandRig.PALM_OFFSET), distance = 90f, perspective = 0f)
        val camA = Camera(yaw = 30f, pitch = 24f, target = barCentre + Vec3(0f, -2f, 0f), distance = 90f, perspective = 0.6f)
        val camB = Camera(yaw = -90f, pitch = 0f, target = barCentre + Vec3(0f, -3f, 0f), distance = 90f, perspective = 0f)

        // Panel 1: the open hand, palm toward the viewer, with every part named and the two grip zones marked.
        val open = gripModel(deepInPalm, null, true, camO, HandShape.FLAT)
        val zones = listOf(
            band("zone.right", open.frame, HandRig.KNUCKLE - 0.6f, Material.JOINT, if (deepInPalm) 0.45f else 0.9f),
            band("zone.wrong", open.frame, 5.4f, Material.MUSCLE, if (deepInPalm) 0.9f else 0.0f),
        ).filter { g -> (g.parts[0].alpha > 0f) }
        val openGroups = open.hand + open.forearm + zones
        val primsO = RigRenderer.renderGroups(openGroups, camO, opts)
        fun o(p: Vec3) = camO.project(p).let { Vec2(it.x, it.y) }
        val calloutsO = buildList {
            add(Callout("Thumb", o(tipOf(open.hand, "hand.thumb")), CalloutSide.LEFT, 0))
            add(Callout("Palm", o(open.frame.world(3.4f, 1.7f, 0f)), CalloutSide.LEFT, 2))
            add(Callout("Index finger", o(tipOf(open.hand, "hand.f0b")), CalloutSide.RIGHT, 0))
            add(Callout("Middle finger", o(tipOf(open.hand, "hand.f1b")), CalloutSide.RIGHT, 1))
            add(Callout("Ring finger", o(tipOf(open.hand, "hand.f2b")), CalloutSide.RIGHT, 2))
            add(Callout("Little finger", o(tipOf(open.hand, "hand.f3b")), CalloutSide.RIGHT, 3))
            if (deepInPalm) {
                add(Callout("Palm crease: the bar is too deep here", o(open.frame.world(5.4f, 1.7f, -3f)), CalloutSide.LEFT, 3))
                add(Callout("Correct spot: base of the fingers", o(open.frame.world(HandRig.KNUCKLE - 0.6f, 1.7f, 3.2f)), CalloutSide.LEFT, 1))
            } else {
                add(Callout("Bar goes here: base of the fingers", o(open.frame.world(HandRig.KNUCKLE - 0.6f, 1.7f, 3.2f)), CalloutSide.LEFT, 1))
            }
        }
        val panelO = DetailPanel("Your hand (palm side)", primsO, boundsOfPoints(camO, open.hand + zones, 2f), calloutsO)

        // Panel 2: from in front of the bar, slightly above, so the four fingertips and the thumb separate.
        val full = gripModel(deepInPalm, null, true, camA)
        val groupsA = full.hand + full.forearm + full.barGroups
        val primsA = RigRenderer.renderGroups(groupsA, camA, opts)
        fun a(p: Vec3) = camA.project(p).let { Vec2(it.x, it.y) }
        val calloutsA = listOf(
            Callout("4 fingers curl over the bar", a(tipOf(full.hand, "hand.f1b")), CalloutSide.RIGHT, 0),
            Callout("Thumb wraps around", a(tipOf(full.hand, "hand.thumb")), CalloutSide.LEFT, 0),
            Callout("Bar", a(full.bar.center + Vec3(BAR_HALF - 1f, 0f, 0f)), CalloutSide.RIGHT, 1),
            Callout("Palm", a(full.frame.world(2.5f, -1.6f, 0f)), CalloutSide.LEFT, 1),
        )
        val panelA = DetailPanel("On the bar (front view)", primsA, boundsOfPoints(camA, full.hand + full.barGroups, 2.5f), calloutsA)

        // Panel 3: cross-section looking along the bar with one finger shown, so the contact point reads clearly.
        val one = gripModel(deepInPalm, setOf(1), false, camB, pads = false)
        val contact = one.frame.world(one.wrap.x, 1.7f, 0f)
        val marker = Group3(
            "marker",
            listOf(
                Part3(
                    Sphere3(contact, if (deepInPalm) 1.8f else 1.4f),
                    if (deepInPalm) Material.MUSCLE else Material.JOINT, shine = false, alpha = 0.9f,
                ),
            ),
            outline = false, relativeTo = Painter.TOP, rel = 0f, depthCue = false,
        )
        val groupsB = one.hand + one.forearm + one.barGroups + marker
        val primsB = RigRenderer.renderGroups(groupsB, camB, opts)
        fun b(p: Vec3) = camB.project(p).let { Vec2(it.x, it.y) }
        val calloutsB = if (deepInPalm) {
            listOf(
                Callout("Bar buried in the palm crease", b(contact), CalloutSide.LEFT, 0),
                Callout("Skin pinches here", b(contact + Vec3(0f, 0.5f, 1.6f)), CalloutSide.LEFT, 1),
                Callout("Finger", b(tipOf(one.hand, "hand.f1b")), CalloutSide.RIGHT, 0),
            )
        } else {
            listOf(
                Callout("Bar rests at the base of the fingers", b(contact), CalloutSide.LEFT, 0),
                Callout("Finger hooks over", b(tipOf(one.hand, "hand.f1b")), CalloutSide.RIGHT, 0),
                Callout("Palm stays clear", b(one.frame.world(3f, -1.6f, 0f)), CalloutSide.LEFT, 1),
            )
        }
        val panelB = DetailPanel("Side cross-section", primsB, boundsOfPoints(camB, one.hand + one.barGroups, 3f), calloutsB)
        return DetailScene(listOf(panelO, panelA, panelB))
    }
}
