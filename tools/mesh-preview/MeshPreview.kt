package com.personal.calisthenics.tools

import com.personal.calisthenics.core.mesh.HumanMesh
import com.personal.calisthenics.core.mesh.MeshFraming
import com.personal.calisthenics.core.mesh.MeshOptions
import com.personal.calisthenics.core.mesh.MeshRenderer
import com.personal.calisthenics.core.mesh.ViewMap
import com.personal.calisthenics.core.rig.Bounds
import com.personal.calisthenics.core.rig.Camera
import com.personal.calisthenics.core.rig.ClipPlayer
import com.personal.calisthenics.core.rig.Vec3
import com.personal.calisthenics.core.rig.RigLibrary
import com.personal.calisthenics.core.rig.toDraws
import com.personal.calisthenics.core.seed.SeedData
import java.awt.Color
import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO

/**
 * Developer tool (JVM only): renders the skinned human mesh so poses can be checked by eye.
 *   MeshPreview <outDir> big <id> <A|B|keyframe> <yaw> <pitch> [yaw pitch ...]   one big picture per camera
 *   MeshPreview <outDir> sheet <A|B> <id> [id ...]   one cell per exercise (3/4 view), 6 per row
 *   MeshPreview <outDir> zoom <id> <A|B|keyframe> <yaw> <pitch> <x> <y> <z> <halfSizeCm> [yaw2 pitch2 ...]   close-up
 *   MeshPreview <outDir> strip <id> [frames] [yaw pitch ...]                        one loop as a filmstrip
 */
object MeshPreview {
    private val BACKDROP = Color(0x11, 0x14, 0x18)

    @JvmStatic
    fun main(args: Array<String>) {
        val out = File(args[0]).also { it.mkdirs() }
        val mesh = HumanMesh.fromResource() ?: error("human.bin is not on the class path")
        val renderer = MeshRenderer(mesh)
        when (args[1]) {
            "big" -> big(out, renderer, args[2], args[3], args.drop(4).map { it.toFloat() }.chunked(2), supersample = 2)
            "sheet" -> sheet(out, renderer, args[2], args.drop(3))
            "zoom" -> zoom(out, renderer, args[2], args[3], args.drop(4).map { it.toFloat() })
            "strip" -> strip(out, renderer, args[2], args.getOrNull(3)?.toIntOrNull() ?: 8, args.drop(4).map { it.toFloat() }.chunked(2))
            else -> error("unknown mode ${args[1]}")
        }
    }

    private fun draw(renderer: MeshRenderer, id: String, frame: String?, timeMs: Long, cam: Camera, w: Int, h: Int, ss: Int): BufferedImage {
        val anim = RigLibrary.animation(id)
        val pose = when (frame) {
            "A" -> anim.startPose()
            "B" -> anim.endPose()
            null -> anim.poseAt(timeMs)
            else -> anim.keyframes[frame.toInt()].pose
        }
        val progress = when (frame) { "A" -> 0f; "B" -> 1f; else -> anim.progressAt(timeMs) }
        val draws = SeedData.exerciseOrNull(id)?.highlights.orEmpty().toDraws(progress)
        val bounds = MeshFraming.clip(ClipPlayer(anim, emptyList()), anyYaw = true) // the framing the app uses
        val view = ViewMap(bounds, w.toFloat(), h.toFloat(), pad = 6f)
        val pixels = IntArray(w * h)
        val t0 = System.nanoTime()
        renderer.render(anim.scene, pose, cam, draws, view, pixels, MeshOptions(supersample = ss))
        val ms = (System.nanoTime() - t0) / 1e6
        println("  $id yaw=${cam.yaw} pitch=${cam.pitch} ${w}x$h ss=$ss: ${"%.1f".format(ms)} ms")
        val img = BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB)
        img.setRGB(0, 0, w, h, pixels, 0, w)
        val canvas = BufferedImage(w, h, BufferedImage.TYPE_INT_RGB)
        val g = canvas.createGraphics()
        g.color = BACKDROP
        g.fillRect(0, 0, w, h)
        g.drawImage(img, 0, 0, null)
        g.dispose()
        return canvas
    }

    private fun big(out: File, renderer: MeshRenderer, id: String, frame: String, cams: List<List<Float>>, supersample: Int) {
        val w = 720
        val h = 900
        val sheet = BufferedImage(w * cams.size, h, BufferedImage.TYPE_INT_RGB)
        val g = sheet.createGraphics()
        cams.forEachIndexed { i, c ->
            // Render once to warm up the JIT so the printed time is representative, then draw.
            draw(renderer, id, frame, 0, Camera(c[0], c[1], perspective = 0.3f), w, h, supersample)
            g.drawImage(draw(renderer, id, frame, 0, Camera(c[0], c[1], perspective = 0.3f), w, h, supersample), i * w, 0, null)
        }
        g.dispose()
        ImageIO.write(sheet, "png", File(out, "mesh_big_${id}_$frame.png"))
        println("wrote mesh_big_${id}_$frame.png")
    }

    private fun sheet(out: File, renderer: MeshRenderer, frame: String, ids: List<String>) {
        val w = 300
        val h = 380
        val cols = 6
        val rows = (ids.size + cols - 1) / cols
        val sheet = BufferedImage(w * cols, h * rows, BufferedImage.TYPE_INT_RGB)
        val g = sheet.createGraphics()
        ids.forEachIndexed { i, id ->
            g.drawImage(draw(renderer, id, frame, 0, Camera(-38f, 12f, perspective = 0.3f), w, h, 1), (i % cols) * w, (i / cols) * h, null)
            g.color = Color.WHITE
            g.drawString(id, (i % cols) * w + 6, (i / cols) * h + 14)
        }
        g.dispose()
        ImageIO.write(sheet, "png", File(out, "mesh_sheet_$frame.png"))
        println("wrote mesh_sheet_$frame.png")
    }

    private fun zoom(out: File, renderer: MeshRenderer, id: String, frame: String, a: List<Float>) {
        val anim = RigLibrary.animation(id)
        val pose = when (frame) { "A" -> anim.startPose(); "B" -> anim.endPose(); else -> anim.keyframes[frame.toInt()].pose }
        val progress = when (frame) { "A" -> 0f; "B" -> 1f; else -> null }
        val draws = SeedData.exerciseOrNull(id)?.highlights.orEmpty().toDraws(progress)
        val centre = Vec3(a[2], a[3], a[4])
        val half = a[5]
        val views = mutableListOf(listOf(a[0], a[1]))
        views += a.drop(6).chunked(2).filter { it.size == 2 }
        val size = 640
        val sheet = BufferedImage(size * views.size, size, BufferedImage.TYPE_INT_RGB)
        val g = sheet.createGraphics()
        views.forEachIndexed { i, v ->
            val cam = Camera(v[0], v[1], target = centre, perspective = 0.3f)
            val view = ViewMap(Bounds(-half, -half, half, half), size.toFloat(), size.toFloat())
            val pixels = IntArray(size * size)
            renderer.render(anim.scene, pose, cam, draws, view, pixels, MeshOptions(supersample = 2))
            val img = BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB)
            img.setRGB(0, 0, size, size, pixels, 0, size)
            g.color = BACKDROP
            g.fillRect(i * size, 0, size, size)
            g.drawImage(img, i * size, 0, null)
        }
        g.dispose()
        ImageIO.write(sheet, "png", File(out, "mesh_zoom_${id}_$frame.png"))
        println("wrote mesh_zoom_${id}_$frame.png")
    }

    private fun strip(out: File, renderer: MeshRenderer, id: String, frames: Int, cams: List<List<Float>>) {
        val anim = RigLibrary.animation(id)
        val camList = if (cams.isEmpty()) listOf(listOf(-38f, 12f)) else cams
        val w = 300
        val h = 400
        val sheet = BufferedImage(w * frames, h * camList.size, BufferedImage.TYPE_INT_RGB)
        val g = sheet.createGraphics()
        camList.forEachIndexed { row, c ->
            for (f in 0 until frames) {
                val t = anim.loopMs * f / frames
                g.drawImage(draw(renderer, id, null, t, Camera(c[0], c[1], perspective = 0.3f), w, h, 1), f * w, row * h, null)
            }
        }
        g.dispose()
        ImageIO.write(sheet, "png", File(out, "mesh_strip_$id.png"))
        println("wrote mesh_strip_$id.png")
    }
}
