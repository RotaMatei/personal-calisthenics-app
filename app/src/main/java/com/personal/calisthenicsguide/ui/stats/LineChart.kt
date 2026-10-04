package com.personal.calisthenicsguide.ui.stats

import android.graphics.Paint
import android.graphics.Typeface
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.personal.calisthenics.core.analytics.ChartPoint
import com.personal.calisthenicsguide.ui.Muted
import androidx.compose.foundation.background
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.floor
import kotlin.math.log10
import kotlin.math.pow

data class ChartLine(val name: String, val color: Color, val points: List<ChartPoint>)

private val AxisColor = Color(0xFF3A4556)
private val LabelColor = Color(0xFFB4BFCD)

/** A small multi-series line chart: one point per session, time on the x axis, a rounded scale on the y axis. */
@Composable
fun LineChart(lines: List<ChartLine>, modifier: Modifier = Modifier) {
    val all = lines.flatMap { it.points }
    if (all.isEmpty()) {
        Muted("No data yet. Log a few sessions and the lines will appear here.", modifier = modifier)
        return
    }
    Row(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
        for (line in lines) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Box(modifier = Modifier.size(12.dp).clip(CircleShape).background(line.color))
                Text(line.name, style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
    Canvas(modifier = modifier.fillMaxWidth().height(220.dp)) {
        val left = 44.dp.toPx()
        val right = 14.dp.toPx()
        val top = 12.dp.toPx()
        val bottom = 30.dp.toPx()
        val w = size.width - left - right
        val h = size.height - top - bottom
        val maxValue = niceMax(all.maxOf { it.value })
        val minT = all.minOf { it.epochMs }
        val maxT = all.maxOf { it.epochMs }
        fun px(t: Long): Float = if (maxT == minT) left + w / 2f else left + w * ((t - minT).toFloat() / (maxT - minT).toFloat())
        fun py(v: Double): Float = top + h * (1f - (v / maxValue).toFloat())

        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = 12.sp.toPx()
            typeface = Typeface.DEFAULT
            color = LabelColor.toArgb()
        }
        for (i in 0..4) {
            val y = top + h * i / 4f
            drawLine(AxisColor, Offset(left, y), Offset(left + w, y), strokeWidth = 1.5f)
            val label = formatValue(maxValue * (4 - i) / 4.0)
            drawIntoCanvas { it.nativeCanvas.drawText(label, left - 8.dp.toPx() - paint.measureText(label), y + paint.textSize * 0.35f, paint) }
        }
        val format = DateTimeFormatter.ofPattern("d MMM", Locale.ENGLISH)
        fun dateLabel(ms: Long): String = Instant.ofEpochMilli(ms).atZone(ZoneId.systemDefault()).toLocalDate().format(format)
        val first = dateLabel(minT)
        drawIntoCanvas { it.nativeCanvas.drawText(first, left, size.height - 6.dp.toPx(), paint) }
        if (maxT != minT) {
            val last = dateLabel(maxT)
            drawIntoCanvas { it.nativeCanvas.drawText(last, left + w - paint.measureText(last), size.height - 6.dp.toPx(), paint) }
        }

        for (line in lines) {
            val pts = line.points.map { Offset(px(it.epochMs), py(it.value)) }
            for (i in 0 until pts.size - 1) drawLine(line.color, pts[i], pts[i + 1], strokeWidth = 5f)
            for (p in pts) {
                drawCircle(line.color, radius = 8f, center = p)
                drawCircle(Color(0xFF141A22), radius = 3.5f, center = p)
            }
        }
    }
}

private fun formatValue(v: Double): String = if (v % 1.0 == 0.0) v.toInt().toString() else "%.1f".format(Locale.ENGLISH, v)

/** The smallest "round" top of the scale (four equal steps) that is at least [v]. */
private fun niceMax(v: Double): Double {
    val raw = (v / 4.0).coerceAtLeast(0.25)
    val magnitude = 10.0.pow(floor(log10(raw)))
    var step = 10.0 * magnitude
    for (candidate in listOf(1.0, 2.0, 2.5, 5.0, 10.0)) {
        if (candidate * magnitude >= raw) {
            step = candidate * magnitude
            break
        }
    }
    return step * 4.0
}
