package com.personal.calisthenicsguide.ui.guide

import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import com.personal.calisthenics.core.rig.AnnotationDraw
import com.personal.calisthenics.core.rig.Bounds
import com.personal.calisthenics.core.rig.CapsulePrim
import com.personal.calisthenics.core.rig.DiscPrim
import com.personal.calisthenics.core.rig.EllipsePrim
import com.personal.calisthenics.core.rig.LabelAlign
import com.personal.calisthenics.core.rig.Layer
import com.personal.calisthenics.core.rig.LinePrim
import com.personal.calisthenics.core.rig.LinearShade
import com.personal.calisthenics.core.rig.Palette
import com.personal.calisthenics.core.rig.PolyPrim
import com.personal.calisthenics.core.rig.Prim
import com.personal.calisthenics.core.rig.RadialShade
import com.personal.calisthenics.core.rig.Shading
import com.personal.calisthenics.core.rig.Vec2
import com.personal.calisthenics.core.rig.WashPrim
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min

/** Maps rig view centimetres (y up) into a canvas of [w] x [h] pixels, centred and uniformly scaled. */
class ViewFit(val bounds: Bounds, val w: Float, val h: Float, pad: Float = 0f) {
    val scale: Float = min((w - 2 * pad) / bounds.width, (h - 2 * pad) / bounds.height)
    private val ox = (w - bounds.width * scale) / 2f
    private val oy = (h - bounds.height * scale) / 2f
    fun x(v: Float) = ox + (v - bounds.minX) * scale
    fun y(v: Float) = oy + (bounds.maxY - v) * scale
    fun r(v: Float) = v * scale
}

private fun colorOf(p: Prim) = Color(Palette.argb(p.material, p.layer, p.tone, p.alpha))

/** Light-to-shadow gradient of a shaded primitive, or null to fill it flat (too small to need it, or flat by design). */
private fun brushOf(p: Prim, fit: ViewFit): Brush? {
    val shade = Shading.shadeOf(p) ?: return null
    val stops = arrayOf(0f to Color(shade.colors[0]), 0.5f to Color(shade.colors[1]), 1f to Color(shade.colors[2]))
    return when (shade) {
        is LinearShade -> {
            val start = Offset(fit.x(shade.from.x), fit.y(shade.from.y))
            val end = Offset(fit.x(shade.to.x), fit.y(shade.to.y))
            if ((start - end).getDistance() < 1f) null else Brush.linearGradient(colorStops = stops, start = start, end = end)
        }
        is RadialShade -> {
            val r = fit.r(shade.radius)
            if (r < 2.5f) null else Brush.radialGradient(
                colorStops = stops, center = Offset(fit.x(shade.centre.x), fit.y(shade.centre.y)), radius = r,
            )
        }
    }
}

private fun polygonPath(poly: List<Vec2>, fit: ViewFit): Path = Path().apply {
    poly.forEachIndexed { i, v ->
        val x = fit.x(v.x)
        val y = fit.y(v.y)
        if (i == 0) moveTo(x, y) else lineTo(x, y)
    }
    close()
}

/** Soft gradient discs of a hue wash, clipped to the outline of the body part they sit on. */
private fun DrawScope.drawWash(p: WashPrim, fit: ViewFit) {
    val clip = Path().apply {
        fillType = PathFillType.NonZero
        p.clip.forEach { addPath(polygonPath(it, fit)) }
    }
    val rgb = Palette.argb(p.material, Layer.FILL, 1f, 1f) and 0xFFFFFF
    clipPath(clip) {
        for (d in p.discs) {
            val r = fit.r(d.r)
            if (r < 1f) continue
            val a = (p.alpha * d.weight).coerceIn(0f, 1f)
            fun c(k: Float) = Color((((a * k * 255f + 0.5f).toInt()) shl 24) or rgb)
            val centre = Offset(fit.x(d.c.x), fit.y(d.c.y))
            drawCircle(
                brush = Brush.radialGradient(
                    colorStops = arrayOf(0f to c(1f), 0.35f to c(0.62f), 0.7f to c(0.22f), 1f to c(0f)),
                    center = centre, radius = r,
                ),
                radius = r, center = centre,
            )
        }
    }
}

/** Paints the back-to-front primitives produced by the rig renderer; identical to the PNG preview tool. */
fun DrawScope.drawPrims(prims: List<Prim>, fit: ViewFit) {
    for (p in prims) {
        val color = colorOf(p)
        val brush = brushOf(p, fit)
        when (p) {
            is WashPrim -> drawWash(p, fit)
            is DiscPrim -> {
                val centre = Offset(fit.x(p.c.x), fit.y(p.c.y))
                if (brush != null) drawCircle(brush, radius = fit.r(p.r), center = centre)
                else drawCircle(color, radius = fit.r(p.r), center = centre)
            }
            is EllipsePrim -> {
                if (brush != null) {
                    // A gradient is laid out in screen space, so fill the rotated ellipse as a polygon instead of rotating the canvas.
                    drawPath(polygonPath(Shading.polygons(p).first(), fit), brush)
                } else {
                    val cx = fit.x(p.c.x)
                    val cy = fit.y(p.c.y)
                    val rx = fit.r(p.rx)
                    val ry = fit.r(p.ry)
                    rotate(degrees = -p.angleDeg, pivot = Offset(cx, cy)) {
                        drawOval(color, topLeft = Offset(cx - rx, cy - ry), size = Size(2 * rx, 2 * ry))
                    }
                }
            }
            is PolyPrim -> {
                if (p.points.size >= 3) {
                    val path = Path()
                    p.points.forEachIndexed { i, v ->
                        val x = fit.x(v.x)
                        val y = fit.y(v.y)
                        if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
                    }
                    path.close()
                    drawPath(path, color)
                }
            }
            is LinePrim -> drawLine(
                color,
                start = Offset(fit.x(p.a.x), fit.y(p.a.y)),
                end = Offset(fit.x(p.b.x), fit.y(p.b.y)),
                strokeWidth = max(1f, fit.r(p.width)),
                cap = StrokeCap.Round,
            )
            is CapsulePrim -> {
                val ax = fit.x(p.a.x)
                val ay = fit.y(p.a.y)
                val bx = fit.x(p.b.x)
                val by = fit.y(p.b.y)
                val ra = fit.r(p.ra)
                val rb = fit.r(p.rb)
                if (brush != null) {
                    drawCircle(brush, ra, Offset(ax, ay))
                    drawCircle(brush, rb, Offset(bx, by))
                } else {
                    drawCircle(color, ra, Offset(ax, ay))
                    drawCircle(color, rb, Offset(bx, by))
                }
                val dx = bx - ax
                val dy = by - ay
                val len = hypot(dx, dy)
                if (len > 0.01f) {
                    val nx = -dy / len
                    val ny = dx / len
                    val path = Path().apply {
                        moveTo(ax + nx * ra, ay + ny * ra)
                        lineTo(bx + nx * rb, by + ny * rb)
                        lineTo(bx - nx * rb, by - ny * rb)
                        lineTo(ax - nx * ra, ay - ny * ra)
                        close()
                    }
                    if (brush != null) drawPath(path, brush) else drawPath(path, color)
                }
            }
        }
    }
}

private val GoodColor = Color(0xFF4ADE80)
private val BadColor = Color(0xFFF87171)

/** Arrow + text label of a DO/DON'T picture (green = correct, red = wrong). */
fun DrawScope.drawAnnotation(l: AnnotationDraw, fit: ViewFit, measurer: TextMeasurer, minTextPx: Float) {
    val colour = if (l.good) GoodColor else BadColor
    val textPx = max(minTextPx, fit.r(l.size))
    val layout = measurer.measure(l.text, TextStyle(fontSize = textPx.toSp(), fontWeight = FontWeight.Bold, color = colour))
    val tw = layout.size.width.toFloat()
    val th = layout.size.height.toFloat()
    val ax = fit.x(l.anchor.x)
    val ay = fit.y(l.anchor.y)
    val lx = fit.x(l.label.x)
    val ty = fit.y(l.label.y)
    val tx = when (l.align) {
        LabelAlign.START -> lx
        LabelAlign.END -> lx - tw
        LabelAlign.CENTER -> lx - tw / 2f
    }
    // Keep the label inside the canvas.
    val left = tx.coerceIn(2f, max(2f, size.width - tw - 2f))
    val top = (ty - th / 2f).coerceIn(2f, max(2f, size.height - th - 2f))
    val centreX = left + tw / 2f
    val sx = if (ax < left) left - 3f else if (ax > left + tw) left + tw + 3f else centreX
    val sy = if (ax in left..(left + tw)) (if (ay < top) top + th else top) else top + th / 2f
    drawLine(colour, Offset(sx, sy), Offset(ax, ay), strokeWidth = 3f, cap = StrokeCap.Round)
    drawCircle(colour, 7f, Offset(ax, ay))
    drawRoundRect(Color(0xCC000000), Offset(left - 5f, top - 2f), Size(tw + 10f, th + 4f), CornerRadius(10f, 10f))
    drawRoundRect(colour, Offset(left - 5f, top - 2f), Size(tw + 10f, th + 4f), CornerRadius(10f, 10f), style = Stroke(1.5f))
    drawText(layout, topLeft = Offset(left, top))
}
