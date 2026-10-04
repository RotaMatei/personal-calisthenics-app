package com.personal.calisthenicsguide.ui.guide

import android.graphics.Paint
import android.graphics.Typeface
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.nativeCanvas
import com.personal.calisthenics.core.rig.AnnotationDraw
import com.personal.calisthenics.core.rig.Bounds
import com.personal.calisthenics.core.rig.CapsulePrim
import com.personal.calisthenics.core.rig.DiscPrim
import com.personal.calisthenics.core.rig.EllipsePrim
import com.personal.calisthenics.core.rig.LabelAlign
import com.personal.calisthenics.core.rig.LinePrim
import com.personal.calisthenics.core.rig.PolyPrim
import com.personal.calisthenics.core.rig.Prim
import com.personal.calisthenics.core.rig.Vec2
import kotlin.math.hypot
import kotlin.math.min
import com.personal.calisthenics.core.rig.Palette as RigPalette

/** Maps view centimetres (y up) of a [Bounds] rectangle onto a canvas of [width] x [height] pixels, centred. */
class ViewFit(val bounds: Bounds, width: Float, height: Float, padding: Float = 0f) {
    val scale: Float = min((width - 2 * padding) / bounds.width, (height - 2 * padding) / bounds.height)
    private val ox = (width - bounds.width * scale) / 2f
    private val oy = (height - bounds.height * scale) / 2f

    fun x(v: Float) = ox + (v - bounds.minX) * scale
    fun y(v: Float) = oy + (bounds.maxY - v) * scale
    fun point(p: Vec2) = Offset(x(p.x), y(p.y))
    fun r(v: Float) = v * scale
}

private fun colorOf(p: Prim): Color = Color(RigPalette.argb(p.material, p.layer, p.tone, p.alpha))

/** Paints the back-to-front primitives produced by the core renderer. */
fun DrawScope.drawPrims(prims: List<Prim>, fit: ViewFit) {
    for (p in prims) {
        val color = colorOf(p)
        when (p) {
            is DiscPrim -> drawCircle(color, radius = fit.r(p.r), center = fit.point(p.c))
            is EllipsePrim -> {
                val c = fit.point(p.c)
                val rx = fit.r(p.rx)
                val ry = fit.r(p.ry)
                rotate(degrees = -p.angleDeg, pivot = c) {
                    drawOval(color, topLeft = Offset(c.x - rx, c.y - ry), size = Size(2 * rx, 2 * ry))
                }
            }
            is PolyPrim -> {
                if (p.points.size >= 3) {
                    val path = Path()
                    p.points.forEachIndexed { i, v ->
                        val o = fit.point(v)
                        if (i == 0) path.moveTo(o.x, o.y) else path.lineTo(o.x, o.y)
                    }
                    path.close()
                    drawPath(path, color)
                }
            }
            is LinePrim -> drawLine(color, fit.point(p.a), fit.point(p.b), strokeWidth = maxOf(1f, fit.r(p.width)))
            is CapsulePrim -> {
                val a = fit.point(p.a)
                val b = fit.point(p.b)
                val ra = fit.r(p.ra)
                val rb = fit.r(p.rb)
                drawCircle(color, ra, a)
                drawCircle(color, rb, b)
                val dx = b.x - a.x
                val dy = b.y - a.y
                val len = hypot(dx, dy)
                if (len > 0.01f) {
                    val nx = -dy / len
                    val ny = dx / len
                    val path = Path().apply {
                        moveTo(a.x + nx * ra, a.y + ny * ra)
                        lineTo(b.x + nx * rb, b.y + ny * rb)
                        lineTo(b.x - nx * rb, b.y - ny * rb)
                        lineTo(a.x - nx * ra, a.y - ny * ra)
                        close()
                    }
                    drawPath(path, color)
                }
            }
        }
    }
}

private val GOOD = Color(0xFF4ADE80)
private val BAD = Color(0xFFFF6B6B)

/** Draws the arrow labels of a DO/DON'T picture on top of the body. */
fun DrawScope.drawAnnotations(labels: List<AnnotationDraw>, fit: ViewFit, textSizePx: Float) {
    if (labels.isEmpty()) return
    val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = textSizePx
        typeface = Typeface.DEFAULT_BOLD
    }
    for (l in labels) {
        val colour = if (l.good) GOOD else BAD
        val anchor = fit.point(l.anchor)
        paint.color = colour.toArgbInt()
        val width = paint.measureText(l.text)
        val base = fit.point(l.label)
        val left = when (l.align) {
            LabelAlign.START -> base.x
            LabelAlign.END -> base.x - width
            LabelAlign.CENTER -> base.x - width / 2f
        }
        val top = base.y - textSizePx
        val bottom = base.y + textSizePx * 0.35f
        // Arrow from the nearest edge of the label to the body point.
        val startX = when {
            anchor.x < left -> left - 4f
            anchor.x > left + width -> left + width + 4f
            else -> anchor.x
        }
        val startY = when {
            anchor.x in left..(left + width) -> if (anchor.y < top) bottom + 3f else top - 3f
            else -> (top + bottom) / 2f
        }
        drawLine(colour, Offset(startX, startY), anchor, strokeWidth = 3f)
        drawCircle(colour, radius = 7f, center = anchor)
        drawCircle(Color.Black, radius = 7f, center = anchor, style = Stroke(width = 1.5f))
        // Dark plate behind the text so it stays legible over the picture.
        drawRoundRectPlate(left - 8f, top - 6f, width + 16f, bottom - top + 12f)
        drawIntoCanvas { it.nativeCanvas.drawText(l.text, left, base.y, paint) }
    }
}

private fun Color.toArgbInt(): Int = androidx.compose.ui.graphics.toArgb(this)

private fun DrawScope.drawRoundRectPlate(x: Float, y: Float, w: Float, h: Float) {
    drawRoundRect(
        color = Color(0xCC000000),
        topLeft = Offset(x, y),
        size = Size(w, h),
        cornerRadius = androidx.compose.ui.geometry.CornerRadius(10f, 10f),
    )
}
