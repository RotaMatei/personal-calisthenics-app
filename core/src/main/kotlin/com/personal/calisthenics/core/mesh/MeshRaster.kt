package com.personal.calisthenics.core.mesh

import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min

/**
 * Small CPU triangle rasteriser: a depth buffer plus Gouraud-shaded colour. Pixels are packed ARGB with alpha 255 where
 * something was drawn and 0 elsewhere, so the picture can sit on any backdrop.
 */
internal class MeshRaster(val w: Int, val h: Int) {
    val depth = FloatArray(w * h)
    val color = IntArray(w * h)

    fun clear() {
        java.util.Arrays.fill(depth, Float.POSITIVE_INFINITY)
        java.util.Arrays.fill(color, 0)
    }

    /**
     * Fills the triangle (pixel coordinates, y down; [az] etc. are depths, smaller = nearer) with colours interpolated
     * between the corner colours (0..1 floats). Counter-clockwise-from-outside triangles are front facing; with
     * [cull] the others are skipped. [bias] is subtracted from the depth before the depth test (decals, lines).
     */
    fun triangle(
        ax: Float, ay: Float, az: Float, ar: Float, ag: Float, ab: Float,
        bx: Float, by: Float, bz: Float, br: Float, bg: Float, bb: Float,
        cx: Float, cy: Float, cz: Float, cr: Float, cg: Float, cb: Float,
        cull: Boolean = true, bias: Float = 0f,
    ) {
        val area = (bx - ax) * (cy - ay) - (by - ay) * (cx - ax)
        if (cull && area >= 0f) return
        if (abs(area) < 1e-7f) return
        val minY = max(0, ceil(min(ay, min(by, cy)) - 0.5f).toInt())
        val maxY = min(h - 1, floor(max(ay, max(by, cy)) - 0.5f).toInt())
        if (minY > maxY) return
        val inv = 1f / area
        for (y in minY..maxY) {
            val py = y + 0.5f
            // The triangle's extent on this pixel row: where its edges cross the row's centre line.
            var lo = Float.POSITIVE_INFINITY
            var hi = Float.NEGATIVE_INFINITY
            if ((ay <= py && py < by) || (by <= py && py < ay)) {
                val x = ax + (py - ay) / (by - ay) * (bx - ax); if (x < lo) lo = x; if (x > hi) hi = x
            }
            if ((by <= py && py < cy) || (cy <= py && py < by)) {
                val x = bx + (py - by) / (cy - by) * (cx - bx); if (x < lo) lo = x; if (x > hi) hi = x
            }
            if ((cy <= py && py < ay) || (ay <= py && py < cy)) {
                val x = cx + (py - cy) / (ay - cy) * (ax - cx); if (x < lo) lo = x; if (x > hi) hi = x
            }
            if (lo > hi) continue
            val x0 = max(0, ceil(lo - 0.5f).toInt())
            val x1 = min(w - 1, floor(hi - 0.5f).toInt())
            val row = y * w
            for (x in x0..x1) {
                val px = x + 0.5f
                val w0 = ((bx - px) * (cy - py) - (by - py) * (cx - px)) * inv
                val w1 = ((cx - px) * (ay - py) - (cy - py) * (ax - px)) * inv
                val w2 = 1f - w0 - w1
                val z = w0 * az + w1 * bz + w2 * cz - bias
                val i = row + x
                if (z >= depth[i]) continue
                depth[i] = z
                color[i] = pack(w0 * ar + w1 * br + w2 * cr, w0 * ag + w1 * bg + w2 * cg, w0 * ab + w1 * bb + w2 * cb)
            }
        }
    }

    /**
     * Writes the picture into [out] ([outW] x [outH] ints, non-premultiplied ARGB). With [ss] > 1 the buffer is [ss]
     * times larger and is box-filtered down; with 1 only silhouette and depth-jump pixels are softened.
     */
    fun resolve(out: IntArray, outW: Int, outH: Int, ss: Int) {
        if (ss > 1) downsample(out, outW, outH, ss) else smooth(out)
    }

    private fun downsample(out: IntArray, outW: Int, outH: Int, ss: Int) {
        val n = ss * ss
        for (y in 0 until outH) {
            for (x in 0 until outW) {
                var covered = 0
                var r = 0
                var g = 0
                var b = 0
                for (dy in 0 until ss) {
                    val row = (y * ss + dy) * w + x * ss
                    for (dx in 0 until ss) {
                        val c = color[row + dx]
                        if ((c ushr 24) != 0) {
                            covered++
                            r += (c shr 16) and 0xFF
                            g += (c shr 8) and 0xFF
                            b += c and 0xFF
                        }
                    }
                }
                out[y * outW + x] = if (covered == 0) 0 else {
                    val a = (covered * 255 + n / 2) / n
                    (a shl 24) or ((r / covered) shl 16) or ((g / covered) shl 8) or (b / covered)
                }
            }
        }
    }

    private fun smooth(out: IntArray) {
        for (y in 0 until h) {
            for (x in 0 until w) {
                val i = y * w + x
                val c = color[i]
                val d = depth[i]
                var edge = false
                val covered = (c ushr 24) != 0
                // Compare with the four neighbours: a silhouette (covered vs empty) or a big depth jump is an edge.
                if (x > 0 && isEdge(covered, d, i - 1)) edge = true
                else if (x < w - 1 && isEdge(covered, d, i + 1)) edge = true
                else if (y > 0 && isEdge(covered, d, i - w)) edge = true
                else if (y < h - 1 && isEdge(covered, d, i + w)) edge = true
                if (!edge) {
                    out[i] = c
                    continue
                }
                // Weighted blend of the pixel (0.5) and its neighbours (0.125 each), in premultiplied space.
                var a = 0f
                var r = 0f
                var g = 0f
                var b = 0f
                fun add(idx: Int, wt: Float) {
                    val cc = color[idx]
                    if ((cc ushr 24) != 0) {
                        a += wt
                        r += wt * ((cc shr 16) and 0xFF)
                        g += wt * ((cc shr 8) and 0xFF)
                        b += wt * (cc and 0xFF)
                    }
                }
                add(i, 0.5f)
                add(if (x > 0) i - 1 else i, 0.125f)
                add(if (x < w - 1) i + 1 else i, 0.125f)
                add(if (y > 0) i - w else i, 0.125f)
                add(if (y < h - 1) i + w else i, 0.125f)
                out[i] = if (a <= 0f) 0 else {
                    val ai = (a * 255f + 0.5f).toInt().coerceIn(0, 255)
                    (ai shl 24) or ((r / a + 0.5f).toInt().coerceIn(0, 255) shl 16) or
                        ((g / a + 0.5f).toInt().coerceIn(0, 255) shl 8) or (b / a + 0.5f).toInt().coerceIn(0, 255)
                }
            }
        }
    }

    private fun isEdge(covered: Boolean, d: Float, j: Int): Boolean {
        val other = (color[j] ushr 24) != 0
        if (other != covered) return true
        return covered && abs(depth[j] - d) > DEPTH_JUMP
    }

    private fun pack(r: Float, g: Float, b: Float): Int {
        val ri = (r * 255f + 0.5f).toInt().coerceIn(0, 255)
        val gi = (g * 255f + 0.5f).toInt().coerceIn(0, 255)
        val bi = (b * 255f + 0.5f).toInt().coerceIn(0, 255)
        return (255 shl 24) or (ri shl 16) or (gi shl 8) or bi
    }

    private companion object {
        /** A depth difference (cm) between neighbouring pixels beyond which they belong to different surfaces. */
        const val DEPTH_JUMP = 3.0f
    }
}
