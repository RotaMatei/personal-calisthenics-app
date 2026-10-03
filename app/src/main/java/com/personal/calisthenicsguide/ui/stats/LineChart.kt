package com.personal.calisthenicsguide.ui.stats

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.personal.calisthenics.core.analytics.ChartPoint
import com.personal.calisthenics.core.analytics.LineScale
import com.personal.calisthenicsguide.ui.theme.AppColors
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val DateLabel = DateTimeFormatter.ofPattern("d MMM")

/** Clean line chart of one value per session. Shows an empty-state hint until there are two sessions. */
@Composable
fun LineChart(title: String, unit: String, points: List<ChartPoint>, color: Color, modifier: Modifier = Modifier) {
    val measurer = rememberTextMeasurer()
    Column(modifier.fillMaxWidth()) {
        Text(title, style = MaterialTheme.typography.titleMedium)
        val last = points.lastOrNull()
        Text(
            when {
                last == null -> "No data yet"
                else -> "Latest: ${formatValue(last.value)} $unit  |  Best: ${formatValue(points.maxOf { it.value })} $unit"
            },
            style = MaterialTheme.typography.bodyMedium,
            color = AppColors.TextSecondary,
        )
        if (points.isEmpty()) return@Column
        val scale = LineScale.of(points.map { it.value })
        val zone = ZoneId.systemDefault()
        Canvas(Modifier.fillMaxWidth().height(170.dp).padding(top = 8.dp)) {
            val left = 44.dp.toPx()
            val bottom = 22.dp.toPx()
            val top = 6.dp.toPx()
            val right = 8.dp.toPx()
            val w = size.width - left - right
            val h = size.height - top - bottom
            val labelStyle = TextStyle(fontSize = 11.sp, color = AppColors.TextSecondary)
            // Horizontal grid with value labels.
            for (tick in scale.ticks) {
                val y = top + h * (1f - scale.fraction(tick))
                drawLine(AppColors.SurfaceHigh, Offset(left, y), Offset(left + w, y), strokeWidth = 1.5f)
                val text = measurer.measure(formatValue(tick), labelStyle)
                drawText(text, topLeft = Offset(left - text.size.width - 6.dp.toPx(), y - text.size.height / 2f))
            }
            val minT = points.first().epochMs
            val maxT = points.last().epochMs
            fun xOf(i: Int, p: ChartPoint): Float =
                if (points.size == 1) left + w / 2f
                else if (maxT == minT) left + w * i / (points.size - 1)
                else left + w * ((p.epochMs - minT).toFloat() / (maxT - minT))
            val path = Path()
            points.forEachIndexed { i, p ->
                val x = xOf(i, p)
                val y = top + h * (1f - scale.fraction(p.value))
                if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
            }
            drawPath(path, color, style = Stroke(width = 4f, cap = StrokeCap.Round))
            points.forEachIndexed { i, p ->
                drawCircle(color, 6f, Offset(xOf(i, p), top + h * (1f - scale.fraction(p.value))))
            }
            // First and last date under the axis.
            fun dateText(ms: Long) = DateLabel.format(Instant.ofEpochMilli(ms).atZone(zone))
            val first = measurer.measure(dateText(minT), labelStyle)
            drawText(first, topLeft = Offset(left, size.height - first.size.height))
            if (points.size > 1) {
                val lastText = measurer.measure(dateText(maxT), labelStyle)
                drawText(lastText, topLeft = Offset(size.width - right - lastText.size.width, size.height - lastText.size.height))
            }
        }
    }
}

private fun formatValue(v: Double): String = if (v % 1.0 == 0.0) v.toInt().toString() else "%.1f".format(v)
