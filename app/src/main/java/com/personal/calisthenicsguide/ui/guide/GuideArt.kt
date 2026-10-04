package com.personal.calisthenicsguide.ui.guide

import android.graphics.Paint
import android.graphics.Typeface
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.personal.calisthenics.core.model.DoDont
import com.personal.calisthenics.core.rig.Annotations
import com.personal.calisthenics.core.rig.Bounds
import com.personal.calisthenics.core.rig.CalloutSide
import com.personal.calisthenics.core.rig.Camera
import com.personal.calisthenics.core.rig.DetailArt
import com.personal.calisthenics.core.rig.DetailPanel
import com.personal.calisthenics.core.rig.DetailScene
import com.personal.calisthenics.core.rig.StillFrame
import com.personal.calisthenics.core.rig.Stills
import com.personal.calisthenicsguide.ui.Muted
import com.personal.calisthenicsguide.ui.Palette
import com.personal.calisthenicsguide.ui.Panel
import com.personal.calisthenicsguide.ui.Pill
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private val ArtBackground = Color(0xFF111318)

/** One DO/DON'T card: the wrong picture with its consequence, then the right picture, each with labelled arrows. */
@Composable
fun DoDontCard(item: DoDont, threeQuarter: Boolean) {
    val camera = if (threeQuarter) Camera.THREE_QUARTER else Camera.SIDE
    Panel {
        Text(item.title, style = MaterialTheme.typography.titleLarge)
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Pill("DON'T", Palette.Red)
            Text(item.wrongLabel, style = MaterialTheme.typography.titleMedium, color = Palette.Red)
        }
        Art(item.wrongPoseKey, item.rightPoseKey, camera)
        Text(item.wrongText, style = MaterialTheme.typography.bodyLarge)
        Text(item.wrongConsequence, style = MaterialTheme.typography.bodyMedium, color = Palette.Red)
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Pill("DO", Palette.Green)
            Text(item.rightLabel, style = MaterialTheme.typography.titleMedium, color = Palette.Green)
        }
        Art(item.rightPoseKey, item.wrongPoseKey, camera)
        Text(item.rightText, style = MaterialTheme.typography.bodyLarge)
    }
}

/** A body still (same scale as its partner) or a close-up, whichever [key] names. */
@Composable
private fun Art(key: String, partner: String, camera: Camera) {
    when {
        key in DetailArt.keys -> DetailArtView(key)
        else -> StillView(key, partner, camera)
    }
}

@Composable
private fun StillView(key: String, partner: String, camera: Camera) {
    val data by produceState<Pair<StillFrame, Bounds>?>(null, key, partner, camera) {
        value = withContext(Dispatchers.Default) {
            val frame = Stills.render(key, camera)
            if (frame == null) null else frame to Stills.bounds(listOf(key, partner), camera)
        }
    }
    val d = data
    if (d == null) {
        Box(modifier = Modifier.fillMaxWidth().height(200.dp), contentAlignment = Alignment.Center) { Muted("Drawing...") }
        return
    }
    val (frame, bounds) = d
    val ratio = (bounds.width / bounds.height).coerceIn(0.8f, 2.2f)
    Canvas(modifier = Modifier.fillMaxWidth().aspectRatio(ratio).clip(RoundedCornerShape(16.dp)).background(ArtBackground)) {
        val fit = ViewFit(bounds, size.width, size.height, padding = 6f)
        drawPrims(frame.prims, fit)
        drawAnnotations(frame.overlay, fit, textSizePx = fit.r(Annotations.TEXT_SIZE))
    }
}

@Composable
private fun DetailArtView(key: String) {
    val scene by produceState<DetailScene?>(null, key) { value = withContext(Dispatchers.Default) { DetailArt.scene(key) } }
    val s = scene
    if (s == null) {
        Box(modifier = Modifier.fillMaxWidth().height(200.dp), contentAlignment = Alignment.Center) { Muted("Drawing...") }
        return
    }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        for (panel in s.panels) DetailPanelView(panel)
    }
}

/** A close-up with its text labels in the gutters beside the drawing and a leader line to each labelled spot. */
@Composable
private fun DetailPanelView(panel: DetailPanel) {
    Canvas(modifier = Modifier.fillMaxWidth().aspectRatio(1.0f).clip(RoundedCornerShape(16.dp)).background(ArtBackground)) {
        val w = size.width
        val h = size.height
        val gutter = w * 0.27f
        val titleH = 36.dp.toPx()
        val inner = w - 2f * gutter
        val artH = h - titleH
        val fit = ViewFit(panel.bounds, inner, artH, padding = 4f)
        translate(left = gutter, top = titleH) {
            clipRect(0f, 0f, inner, artH) { drawPrims(panel.prims, fit) }
        }
        val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = 15.sp.toPx()
            typeface = Typeface.DEFAULT_BOLD
            color = android.graphics.Color.WHITE
        }
        val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = 12.sp.toPx()
            typeface = Typeface.DEFAULT
            color = android.graphics.Color.WHITE
        }
        drawIntoCanvas { it.nativeCanvas.drawText(panel.title, 12.dp.toPx(), 24.dp.toPx(), titlePaint) }
        val lineHeight = textPaint.textSize * 1.25f
        val white = Color(0xFFFAFAFA)
        for (side in CalloutSide.entries) {
            var y = titleH + 16.dp.toPx()
            val edge = if (side == CalloutSide.LEFT) gutter - 8.dp.toPx() else w - gutter + 8.dp.toPx()
            for (c in panel.callouts.filter { it.side == side }.sortedBy { it.order }) {
                val lines = wrapText(c.text, textPaint, gutter - 14.dp.toPx())
                val target = Offset(gutter + fit.x(c.target.x), titleH + fit.y(c.target.y))
                drawLine(white, Offset(edge, y - textPaint.textSize * 0.35f), target, strokeWidth = 2f)
                drawCircle(white, radius = 4.5f, center = target)
                lines.forEachIndexed { i, line ->
                    val x = if (side == CalloutSide.LEFT) edge - textPaint.measureText(line) else edge
                    drawIntoCanvas { it.nativeCanvas.drawText(line, x, y + i * lineHeight, textPaint) }
                }
                y += lines.size * lineHeight + 12.dp.toPx()
            }
        }
    }
}

private fun wrapText(text: String, paint: Paint, maxWidth: Float): List<String> {
    val out = mutableListOf<String>()
    var current = ""
    for (word in text.split(" ")) {
        val candidate = if (current.isEmpty()) word else "$current $word"
        if (current.isNotEmpty() && paint.measureText(candidate) > maxWidth) {
            out += current
            current = word
        } else {
            current = candidate
        }
    }
    if (current.isNotEmpty()) out += current
    return out
}
