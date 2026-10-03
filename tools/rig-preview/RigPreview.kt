package com.personal.calisthenics.tools

import com.personal.calisthenics.core.rig.AnnotationDraw
import com.personal.calisthenics.core.rig.Bounds
import com.personal.calisthenics.core.rig.LabelAlign
import com.personal.calisthenics.core.rig.Stills
import com.personal.calisthenics.core.rig.CalloutSide
import com.personal.calisthenics.core.rig.Camera
import com.personal.calisthenics.core.rig.CapsulePrim
import com.personal.calisthenics.core.rig.DetailArt
import com.personal.calisthenics.core.rig.DiscPrim
import com.personal.calisthenics.core.rig.EllipsePrim
import com.personal.calisthenics.core.rig.HighlightDraw
import com.personal.calisthenics.core.rig.Keyframe
import com.personal.calisthenics.core.rig.LinePrim
import com.personal.calisthenics.core.rig.Palette
import com.personal.calisthenics.core.rig.Pose
import com.personal.calisthenics.core.rig.PolyPrim
import com.personal.calisthenics.core.rig.Prim
import com.personal.calisthenics.core.rig.RigAnimation
import com.personal.calisthenics.core.rig.RigFraming
import com.personal.calisthenics.core.rig.RigLibrary
import com.personal.calisthenics.core.rig.RigRenderer
import com.personal.calisthenics.core.rig.RigScene
import com.personal.calisthenics.core.rig.RigValidation
import com.personal.calisthenics.core.rig.toDraws
import com.personal.calisthenics.core.seed.SeedData
import java.awt.BasicStroke
import java.awt.Color
import java.awt.Font
import java.awt.Graphics2D
import java.awt.RenderingHints
import java.awt.geom.AffineTransform
import java.awt.geom.Ellipse2D
import java.awt.geom.Line2D
import java.awt.geom.Path2D
import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO

/**
 * Developer tool (JVM only): renders contact sheets of the rig so poses can be checked by eye.
 * Usage: RigPreview <outDir> <exerciseId|all|details> ...
 * Each sheet shows Position A and B in the 3/4 view plus side and front views, and every DO/DON'T pair.
 */
object RigPreview {
    private const val CELL_W = 360
    private const val CELL_H = 400

    private class Frame(val bounds: Bounds, val w: Int, val h: Int, padX: Float = 8f, top: Float = 28f, bottom: Float = 8f) {
        val scale = minOf((w - 2 * padX) / bounds.width, (h - top - bottom) / bounds.height)
        private val ox = (w - bounds.width * scale) / 2f
        private val oy = top + (h - top - bottom - bounds.height * scale) / 2f
        fun x(v: Float) = ox + (v - bounds.minX) * scale
        fun y(v: Float) = oy + (bounds.maxY - v) * scale
        fun r(v: Float) = v * scale
    }

    private fun colorOf(p: Prim): Color {
        val argb = Palette.argb(p.material, p.layer, p.tone, p.alpha)
        return Color(argb, true)
    }

    private fun draw(g: Graphics2D, prims: List<Prim>, frame: Frame, ox: Int, oy: Int) {
        val saved = g.transform
        for (p in prims) {
            g.color = colorOf(p)
            when (p) {
                is DiscPrim -> {
                    val r = frame.r(p.r).toDouble()
                    g.fill(Ellipse2D.Double(ox + frame.x(p.c.x) - r, oy + frame.y(p.c.y) - r, 2 * r, 2 * r))
                }
                is EllipsePrim -> {
                    val cx = ox + frame.x(p.c.x).toDouble()
                    val cy = oy + frame.y(p.c.y).toDouble()
                    g.transform = AffineTransform(saved).also { it.rotate(-Math.toRadians(p.angleDeg.toDouble()), cx, cy) }
                    val rx = frame.r(p.rx).toDouble()
                    val ry = frame.r(p.ry).toDouble()
                    g.fill(Ellipse2D.Double(cx - rx, cy - ry, 2 * rx, 2 * ry))
                    g.transform = saved
                }
                is PolyPrim -> {
                    val path = Path2D.Double()
                    p.points.forEachIndexed { i, v ->
                        val x = ox + frame.x(v.x).toDouble()
                        val y = oy + frame.y(v.y).toDouble()
                        if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
                    }
                    path.closePath()
                    g.fill(path)
                }
                is LinePrim -> {
                    g.stroke = BasicStroke(maxOf(1f, frame.r(p.width)), BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND)
                    g.draw(
                        Line2D.Double(
                            ox + frame.x(p.a.x).toDouble(), oy + frame.y(p.a.y).toDouble(),
                            ox + frame.x(p.b.x).toDouble(), oy + frame.y(p.b.y).toDouble(),
                        ),
                    )
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
        g.transform = saved
    }

    /** One labelled DO/DON'T picture: body plus arrow callouts. */
    private fun stillCell(g: Graphics2D, key: String, camera: Camera, bounds: Bounds, col: Int, row: Int, label: String) {
        val ox = col * CELL_W
        val oy = row * CELL_H
        val frame = Frame(bounds, CELL_W, CELL_H)
        g.color = Color(0x11, 0x13, 0x18)
        g.fillRect(ox, oy, CELL_W - 2, CELL_H - 2)
        g.clip = java.awt.Rectangle(ox, oy, CELL_W - 2, CELL_H - 2)
        val still = Stills.render(key, camera)
        if (still != null) {
            draw(g, still.prims, frame, ox, oy)
            g.font = Font("SansSerif", Font.BOLD, maxOf(9, frame.r(4.4f).toInt()))
            for (l in still.overlay) drawLabel(g, l, frame, ox, oy)
        }
        g.clip = null
        g.color = Color.WHITE
        g.font = Font("SansSerif", Font.PLAIN, 12)
        g.drawString(label, ox + 8, oy + 16)
    }

    private fun drawLabel(g: Graphics2D, l: AnnotationDraw, frame: Frame, ox: Int, oy: Int) {
        val colour = if (l.good) Color(0x4a, 0xde, 0x80) else Color(0xf8, 0x71, 0x71)
        val ax = ox + frame.x(l.anchor.x)
        val ay = oy + frame.y(l.anchor.y)
        val fm = g.fontMetrics
        val tw = fm.stringWidth(l.text)
        val tx = when (l.align) { LabelAlign.START -> ox + frame.x(l.label.x); LabelAlign.END -> ox + frame.x(l.label.x) - tw; LabelAlign.CENTER -> ox + frame.x(l.label.x) - tw / 2f }
        val ty = oy + frame.y(l.label.y)
        // Arrow from the nearest label edge to the body point.
        val ex = (tx + tw / 2f).coerceIn(tx, tx + tw)
        val sx = if (ax < tx) tx - 3f else if (ax > tx + tw) tx + tw + 3f else ex
        val sy = if (ax in tx..(tx + tw)) (if (ay < ty) ty + 3f else ty - fm.ascent - 3f) else ty - fm.ascent / 2f
        g.color = colour
        g.stroke = BasicStroke(1.6f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND)
        g.draw(Line2D.Double(sx.toDouble(), sy.toDouble(), ax.toDouble(), ay.toDouble()))
        g.fill(Ellipse2D.Double(ax - 4.0, ay - 4.0, 8.0, 8.0))
        g.color = Color(0, 0, 0, 190)
        g.fillRoundRect((tx - 3).toInt(), (ty - fm.ascent - 2).toInt(), tw + 6, fm.height + 2, 6, 6)
        g.color = colour
        g.drawString(l.text, tx, ty)
    }

    private fun cell(
        g: Graphics2D, scene: RigScene, pose: Pose, camera: Camera, bounds: Bounds,
        highlights: List<HighlightDraw>, col: Int, row: Int, label: String,
    ) {
        val ox = col * CELL_W
        val oy = row * CELL_H
        val frame = Frame(bounds, CELL_W, CELL_H)
        g.color = Color(0x11, 0x13, 0x18)
        g.fillRect(ox, oy, CELL_W - 2, CELL_H - 2)
        g.clip = java.awt.Rectangle(ox, oy, CELL_W - 2, CELL_H - 2)
        draw(g, RigRenderer.render(scene, pose, camera, highlights), frame, ox, oy)
        g.clip = null
        g.color = Color.WHITE
        g.font = Font("SansSerif", Font.PLAIN, 12)
        g.drawString(label, ox + 8, oy + 16)
    }

    private fun boundsFor(scene: RigScene, poses: List<Pose>, camera: Camera): Bounds {
        val frames = poses.map { Keyframe(it, 0, 1) }.let { if (it.size >= 2) it else it + it }
        return RigFraming.bounds(RigAnimation(scene, frames), camera)
    }

    private val views = listOf(
        "3/4" to Camera.THREE_QUARTER,
        "side" to Camera.SIDE,
        "front" to Camera.FRONT,
    )

    fun run(args: Array<String>) {
        val outDir = File(args[0]).also { it.mkdirs() }
        if (args.size >= 2 && args[1] == "details") {
            renderDetails(outDir)
            return
        }
        if (args.size >= 2 && args[1] == "big") {
            // big <id> <keyframe index|A|B> <yaw> <pitch> [yaw pitch ...]: large close-ups framed on the body only.
            val id = args[2]
            val anim = RigLibrary.animation(id)
            val pose = when (args[3]) { "A" -> anim.startPose(); "B" -> anim.endPose(); else -> anim.keyframes[args[3].toInt()].pose }
            val cams = args.drop(4).chunked(2).map { Camera(yaw = it[0].toFloat(), pitch = it[1].toFloat(), perspective = 0.3f) }
            val size = 760
            val img = BufferedImage(size * cams.size, size, BufferedImage.TYPE_INT_RGB)
            val g = img.createGraphics()
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
            val progress = when (args[3]) { "A" -> 0f; "B" -> 1f; else -> null }
            val hl = SeedData.exerciseOrNull(id)?.highlights.orEmpty().toDraws(progress)
            cams.forEachIndexed { i, cam ->
                val bare = RigScene(floor = false, equipment = emptyList())
                val bounds = boundsFor(bare, listOf(pose), cam)
                val frame = Frame(bounds, size, size, padX = 10f, top = 10f, bottom = 10f)
                g.color = Color(0x11, 0x13, 0x18)
                g.fillRect(i * size, 0, size, size)
                g.clip = java.awt.Rectangle(i * size, 0, size, size)
                draw(g, RigRenderer.render(anim.scene, pose, cam, hl), frame, i * size, 0)
                g.clip = null
            }
            g.dispose()
            ImageIO.write(img, "png", File(outDir, "big_$id.png"))
            println("wrote big_$id.png")
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
            val hl = exercise?.highlights.orEmpty().toDraws()
            val hlA = exercise?.highlights.orEmpty().toDraws(0f)
            val hlB = exercise?.highlights.orEmpty().toDraws(1f)
            val tq = RigFraming.bounds(anim, Camera.THREE_QUARTER)
            val side = RigFraming.bounds(anim, Camera.SIDE)
            val front = RigFraming.bounds(anim, Camera.FRONT)
            cell(g, anim.scene, a, Camera.THREE_QUARTER, tq, hlA, 0, 0, "$id A (3/4)")
            cell(g, anim.scene, b, Camera.THREE_QUARTER, tq, hlB, 1, 0, "$id B (3/4)")
            cell(g, anim.scene, b, Camera.SIDE, side, hlB, 2, 0, "$id B (side)")
            cell(g, anim.scene, b, Camera.FRONT, front, hlB, 3, 0, "$id B (front)")
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
                val keys = listOf(dd.wrongPoseKey, dd.rightPoseKey)
                val tqb = Stills.bounds(keys, Camera.THREE_QUARTER)
                val sb = Stills.bounds(keys, Camera.SIDE)
                val row = index + 1
                stillCell(g, dd.wrongPoseKey, Camera.THREE_QUARTER, tqb, 0, row, "WRONG ${dd.wrongLabel.take(30)}")
                stillCell(g, dd.rightPoseKey, Camera.THREE_QUARTER, tqb, 1, row, "RIGHT ${dd.rightLabel.take(30)}")
                stillCell(g, dd.wrongPoseKey, Camera.SIDE, sb, 2, row, "WRONG (side)")
                stillCell(g, dd.rightPoseKey, Camera.SIDE, sb, 3, row, "RIGHT (side)")
                printContacts(id, "wrong:${dd.wrongPoseKey}", wrong.pose)
                printContacts(id, "right:${dd.rightPoseKey}", right.pose)
            }
            g.dispose()
            ImageIO.write(img, "png", File(outDir, "$id.png"))
            println("wrote $id.png")
        }
    }

    private const val PANEL_W = 640
    private const val PANEL_H = 430
    private const val GUTTER = 190

    private fun renderDetails(outDir: File) {
        val keys = DetailArt.keys.sorted()
        val scenes = keys.map { DetailArt.scene(it)!! }
        val panelsPerRow = scenes.maxOf { it.panels.size }
        val img = BufferedImage(PANEL_W * panelsPerRow, PANEL_H * keys.size, BufferedImage.TYPE_INT_RGB)
        val g = img.createGraphics()
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON)
        scenes.forEachIndexed { row, scene ->
            scene.panels.forEachIndexed { col, panel ->
                val ox = col * PANEL_W
                val oy = row * PANEL_H
                g.color = Color(0x11, 0x13, 0x18)
                g.fillRect(ox, oy, PANEL_W - 2, PANEL_H - 2)
                val inner = PANEL_W - 2 * GUTTER
                val frame = Frame(panel.bounds, inner, PANEL_H, padX = 4f, top = 30f, bottom = 6f)
                g.clip = java.awt.Rectangle(ox + GUTTER, oy + 24, inner - 2, PANEL_H - 28)
                draw(g, panel.prims, frame, ox + GUTTER, oy)
                g.clip = null
                g.color = Color.WHITE
                g.font = Font("SansSerif", Font.BOLD, 13)
                g.drawString("${keys[row]}: ${panel.title}", ox + 8, oy + 18)
                g.font = Font("SansSerif", Font.PLAIN, 13)
                for (c in panel.callouts) {
                    val tx = ox + GUTTER + frame.x(c.target.x)
                    val ty = oy + frame.y(c.target.y)
                    val lx = if (c.side == CalloutSide.LEFT) ox + GUTTER - 8f else ox + PANEL_W - GUTTER + 8f
                    val ly = oy + 70f + c.order * 52f
                    g.color = Color(0xfa, 0xfa, 0xfa)
                    g.stroke = BasicStroke(1.2f)
                    g.draw(Line2D.Double(lx.toDouble(), ly.toDouble(), tx.toDouble(), ty.toDouble()))
                    g.fill(Ellipse2D.Double(tx - 3.0, ty - 3.0, 6.0, 6.0))
                    // Wrap the label to the gutter width.
                    val words = c.text.split(" ")
                    val lines = mutableListOf<String>()
                    var cur = ""
                    for (w in words) {
                        if (g.fontMetrics.stringWidth("$cur $w".trim()) > GUTTER - 14) { lines += cur; cur = w } else cur = "$cur $w".trim()
                    }
                    lines += cur
                    lines.forEachIndexed { i, line ->
                        val w = g.fontMetrics.stringWidth(line)
                        val x = if (c.side == CalloutSide.LEFT) lx - w else lx
                        g.drawString(line, x, ly - 4f + i * 15f)
                    }
                }
            }
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
