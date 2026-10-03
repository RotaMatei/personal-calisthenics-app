package com.personal.calisthenics.tools

import com.personal.calisthenics.core.model.Highlight
import com.personal.calisthenics.core.rig.CapsulePrim
import com.personal.calisthenics.core.rig.DiscPrim
import com.personal.calisthenics.core.rig.FloorPrim
import com.personal.calisthenics.core.rig.Pose
import com.personal.calisthenics.core.rig.Prim
import com.personal.calisthenics.core.rig.RectPrim
import com.personal.calisthenics.core.rig.RigAnimation
import com.personal.calisthenics.core.rig.RigFraming
import com.personal.calisthenics.core.rig.RigLibrary
import com.personal.calisthenics.core.rig.RigRenderer
import com.personal.calisthenics.core.rig.RigScene
import com.personal.calisthenics.core.rig.RigSolver
import com.personal.calisthenics.core.rig.RigValidation
import com.personal.calisthenics.core.rig.Role
import com.personal.calisthenics.core.rig.ViewKind
import com.personal.calisthenics.core.rig.Bounds
import com.personal.calisthenics.core.seed.SeedData
import java.awt.BasicStroke
import java.awt.Color
import java.awt.Font
import java.awt.RenderingHints
import java.awt.geom.Ellipse2D
import java.awt.geom.Path2D
import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO

/**
 * Developer tool (JVM only): renders contact sheets of the rig so poses can be checked by eye.
 * Usage: RigPreview <outDir> <exerciseId|all> ...
 * Each sheet shows Position A and B (side + front) and every DO/DON'T pair.
 */
object RigPreview {
    private const val CELL_W = 300
    private const val CELL_H = 340

    private fun color(role: Role): Color = when (role) {
        Role.FLOOR -> Color(0x4b, 0x55, 0x63)
        Role.EQUIPMENT -> Color(0xf5, 0x9e, 0x0b)
        Role.BODY_FAR -> Color(0x6b, 0x72, 0x80)
        Role.BODY_MID -> Color(0x9c, 0xa3, 0xaf)
        Role.BODY_NEAR -> Color(0xd1, 0xd5, 0xdb)
        Role.HEAD -> Color(0xe5, 0xe7, 0xeb)
        Role.BAND -> Color(0x22, 0xc5, 0x5e)
        Role.MUSCLE -> Color(0xef, 0x44, 0x44, 150)
        Role.TENDON -> Color(0x38, 0xbd, 0xf8, 190)
        Role.JOINT -> Color(0xfa, 0xcc, 0x15, 190)
    }

    private class Frame(val bounds: Bounds, val w: Int, val h: Int) {
        val scale = minOf((w - 16f) / bounds.width, (h - 40f) / bounds.height)
        private val ox = (w - bounds.width * scale) / 2f
        private val oy = 28f + (h - 40f - bounds.height * scale) / 2f
        fun x(v: Float) = ox + (v - bounds.minX) * scale
        fun y(v: Float) = oy + (bounds.maxY - v) * scale
        fun r(v: Float) = v * scale
    }

    private fun draw(g: java.awt.Graphics2D, prims: List<Prim>, frame: Frame, ox: Int, oy: Int) {
        for (p in prims) {
            g.color = color(p.role)
            when (p) {
                is FloorPrim -> {
                    g.stroke = BasicStroke(2f)
                    val yy = oy + frame.y(p.y).toDouble()
                    g.draw(java.awt.geom.Line2D.Double(ox.toDouble(), yy, (ox + frame.w).toDouble(), yy))
                }
                is DiscPrim -> {
                    val r = frame.r(p.r).toDouble()
                    g.fill(Ellipse2D.Double(ox + frame.x(p.c.x) - r, oy + frame.y(p.c.y) - r, 2 * r, 2 * r))
                }
                is RectPrim -> {
                    val x0 = ox + frame.x(p.min.x).toDouble()
                    val x1 = ox + frame.x(p.max.x).toDouble()
                    val y0 = oy + frame.y(p.max.y).toDouble()
                    val y1 = oy + frame.y(p.min.y).toDouble()
                    g.fill(java.awt.geom.Rectangle2D.Double(minOf(x0, x1), minOf(y0, y1), Math.abs(x1 - x0), Math.abs(y1 - y0)))
                }
                is CapsulePrim -> {
                    val ax = ox + frame.x(p.a.x).toDouble()
                    val ay = oy + frame.y(p.a.y).toDouble()
                    val bx = ox + frame.x(p.b.x).toDouble()
                    val by = oy + frame.y(p.b.y).toDouble()
                    val ra = frame.r(p.ra).toDouble()
                    val rb = frame.r(p.rb).toDouble()
                    val dx = bx - ax
                    val dy = by - ay
                    val len = Math.hypot(dx, dy)
                    g.fill(Ellipse2D.Double(ax - ra, ay - ra, 2 * ra, 2 * ra))
                    g.fill(Ellipse2D.Double(bx - rb, by - rb, 2 * rb, 2 * rb))
                    if (len > 0.01) {
                        val nx = -dy / len
                        val ny = dx / len
                        val path = Path2D.Double()
                        path.moveTo(ax + nx * ra, ay + ny * ra)
                        path.lineTo(bx + nx * rb, by + ny * rb)
                        path.lineTo(bx - nx * rb, by - ny * rb)
                        path.lineTo(ax - nx * ra, ay - ny * ra)
                        path.closePath()
                        g.fill(path)
                    }
                }
            }
        }
    }

    private fun cell(
        g: java.awt.Graphics2D, scene: RigScene, pose: Pose, view: ViewKind, bounds: Bounds,
        highlights: List<Highlight>, col: Int, row: Int, label: String,
    ) {
        val ox = col * CELL_W
        val oy = row * CELL_H
        val frame = Frame(bounds, CELL_W, CELL_H)
        g.color = Color(0x11, 0x13, 0x18)
        g.fillRect(ox, oy, CELL_W - 2, CELL_H - 2)
        val sk = RigSolver.solve(pose)
        draw(g, RigRenderer.render(scene, sk, view, highlights), frame, ox, oy)
        g.color = Color.WHITE
        g.font = Font("SansSerif", Font.PLAIN, 12)
        g.drawString("$label (${view.name.lowercase()})", ox + 8, oy + 16)
    }

    private fun boundsFor(scene: RigScene, poses: List<Pose>, view: ViewKind): Bounds {
        val anim = RigAnimation(scene, poses.flatMap { listOf(com.personal.calisthenics.core.rig.Keyframe(it, 0, 1)) }.let { if (it.size >= 2) it else it + it })
        return RigFraming.bounds(anim, view)
    }

    fun run(args: Array<String>) {
        val outDir = File(args[0]).also { it.mkdirs() }
        if (args.size >= 2 && args[1] == "details") {
            renderDetails(outDir)
            return
        }
        val ids = if (args.size < 2 || args[1] == "all") SeedData.exercises.map { it.id } else args.drop(1)
        for (id in ids) {
            val exercise = SeedData.exerciseOrNull(id)
            val anim = RigLibrary.animationOrNull(id)
            if (anim == null) {
                println("MISSING animation: $id")
                continue
            }
            val pairs = exercise?.doDonts.orEmpty().filter { !it.wrongPoseKey.startsWith("detail.") }
            val rows = 1 + pairs.size
            val img = BufferedImage(CELL_W * 4, CELL_H * rows, BufferedImage.TYPE_INT_RGB)
            val g = img.createGraphics()
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
            g.color = Color.BLACK
            g.fillRect(0, 0, img.width, img.height)
            val a = anim.startPose()
            val b = anim.endPose()
            val hl = exercise?.highlights.orEmpty()
            val sideBounds = RigFraming.bounds(anim, ViewKind.SIDE)
            val frontBounds = RigFraming.bounds(anim, ViewKind.FRONT)
            cell(g, anim.scene, a, ViewKind.SIDE, sideBounds, hl, 0, 0, "$id A")
            cell(g, anim.scene, b, ViewKind.SIDE, sideBounds, hl, 1, 0, "$id B")
            cell(g, anim.scene, a, ViewKind.FRONT, frontBounds, hl, 2, 0, "$id A")
            cell(g, anim.scene, b, ViewKind.FRONT, frontBounds, hl, 3, 0, "$id B")
            printContacts(id, "A", a)
            printContacts(id, "B", b)
            for ((i, k) in anim.keyframes.withIndex()) printContacts(id, "kf$i", k.pose)
            pairs.forEachIndexed { index, dd ->
                val wrong = RigLibrary.still(dd.wrongPoseKey)
                val right = RigLibrary.still(dd.rightPoseKey)
                if (wrong == null || right == null) {
                    println("MISSING still for ${dd.wrongPoseKey} / ${dd.rightPoseKey}")
                    return@forEachIndexed
                }
                val sb = boundsFor(wrong.scene, listOf(wrong.pose, right.pose), ViewKind.SIDE)
                val fb = boundsFor(wrong.scene, listOf(wrong.pose, right.pose), ViewKind.FRONT)
                val row = index + 1
                cell(g, wrong.scene, wrong.pose, ViewKind.SIDE, sb, emptyList(), 0, row, "WRONG ${dd.wrongLabel.take(22)}")
                cell(g, right.scene, right.pose, ViewKind.SIDE, sb, emptyList(), 1, row, "RIGHT ${dd.rightLabel.take(22)}")
                cell(g, wrong.scene, wrong.pose, ViewKind.FRONT, fb, emptyList(), 2, row, "WRONG")
                cell(g, right.scene, right.pose, ViewKind.FRONT, fb, emptyList(), 3, row, "RIGHT")
                printContacts(id, "wrong:${dd.wrongPoseKey}", wrong.pose)
                printContacts(id, "right:${dd.rightPoseKey}", right.pose)
            }
            g.dispose()
            ImageIO.write(img, "png", File(outDir, "$id.png"))
            println("wrote $id.png")
        }
    }

    private fun renderDetails(outDir: File) {
        val keys = com.personal.calisthenics.core.rig.DetailArt.keys.sorted()
        val img = BufferedImage(CELL_W * keys.size, CELL_H, BufferedImage.TYPE_INT_RGB)
        val g = img.createGraphics()
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
        keys.forEachIndexed { i, key ->
            val scene = com.personal.calisthenics.core.rig.DetailArt.scene(key)!!
            val frame = Frame(scene.bounds, CELL_W, CELL_H)
            g.color = Color(0x11, 0x13, 0x18)
            g.fillRect(i * CELL_W, 0, CELL_W - 2, CELL_H - 2)
            draw(g, scene.prims.sortedBy { it.depth }, frame, i * CELL_W, 0)
            g.color = Color.WHITE
            g.drawString(key, i * CELL_W + 8, 16)
        }
        g.dispose()
        ImageIO.write(img, "png", File(outDir, "details.png"))
        println("wrote details.png")
    }

    private fun printContacts(id: String, label: String, pose: Pose) {
        for ((limb, err) in RigValidation.contactErrors(pose)) {
            println("  CONTACT $id [$label] $limb off by ${"%.1f".format(err)} cm")
        }
    }
}

fun main(args: Array<String>) = RigPreview.run(args)
