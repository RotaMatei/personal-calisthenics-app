package com.personal.calisthenicsguide.ui.guide

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.personal.calisthenics.core.mesh.MeshFrames
import com.personal.calisthenics.core.model.Highlight
import com.personal.calisthenics.core.rig.CalloutSide
import com.personal.calisthenics.core.rig.Camera
import com.personal.calisthenics.core.rig.DetailArt
import com.personal.calisthenics.core.rig.Stills
import com.personal.calisthenicsguide.ui.theme.AppColors

/** One labelled DO/DON'T body picture. Pass the same [bounds] for a wrong/right pair so they share one scale. */
@Composable
fun StillView(
    key: String,
    camera: Camera,
    bounds: com.personal.calisthenics.core.rig.Bounds,
    highlights: List<Highlight>,
    modifier: Modifier = Modifier,
    height: Dp = 260.dp,
) {
    val measurer = rememberTextMeasurer()
    // Capsule picture and the labels laid over the figure; the labels are drawn on the mesh picture too.
    val frame = remember(key, camera, highlights) { Stills.render(key, camera, highlights) }
    if (frame == null) {
        Box(modifier.fillMaxWidth().height(height).clip(RoundedCornerShape(14.dp)).background(AppColors.ClipBackdrop))
        return
    }
    StaticMeshPicture(
        cacheKey = "still|$key|$camera|${highlights.hashCode()}",
        bounds = bounds,
        pad = 6f,
        modifier = modifier
            .fillMaxWidth()
            .height(height)
            .clip(RoundedCornerShape(14.dp))
            .background(AppColors.ClipBackdrop),
        overlay = {
            val fit = ViewFit(bounds, size.width, size.height, pad = 6f)
            for (label in frame.overlay) drawAnnotation(label, fit, measurer, minTextPx = 11.sp.toPx())
        },
        fallback = { drawPrims(frame.prims, ViewFit(bounds, size.width, size.height, pad = 6f)) },
    ) { renderer, view, out, options ->
        MeshFrames.still(renderer, key, camera, highlights, view, out, options)
    }
}

/** Close-up art (hand-on-bar grip): panels with a gutter on each side holding the callout text. */
@Composable
fun DetailView(key: String, modifier: Modifier = Modifier) {
    val scene = remember(key) { DetailArt.scene(key) } ?: return
    val measurer = rememberTextMeasurer()
    Column(modifier, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        scene.panels.forEach { panel ->
            Text(panel.title, style = MaterialTheme.typography.titleMedium)
            Canvas(
                Modifier
                    .fillMaxWidth()
                    .height(300.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(AppColors.ClipBackdrop),
            ) {
                val gutter = size.width * 0.27f
                val fit = ViewFit(panel.bounds, size.width - 2 * gutter, size.height, pad = 4f)
                // Shift the picture into the middle column.
                translate(left = gutter) { drawPrims(panel.prims, fit) }
                val textStyle = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = Color.White)
                panel.callouts.forEach { c ->
                    val tx = gutter + fit.x(c.target.x)
                    val ty = fit.y(c.target.y)
                    val maxWidth = (gutter - 14.dp.toPx()).toInt().coerceAtLeast(40)
                    val layout = measurer.measure(
                        c.text, textStyle.copy(fontSize = 13.sp), constraints = androidx.compose.ui.unit.Constraints(maxWidth = maxWidth),
                    )
                    val ly = 40.dp.toPx() + c.order * 62.dp.toPx()
                    val lx = if (c.side == CalloutSide.LEFT) gutter - 8.dp.toPx() else size.width - gutter + 8.dp.toPx()
                    drawLine(AppColors.TextPrimary, Offset(lx, ly), Offset(tx, ty), strokeWidth = 2f, cap = StrokeCap.Round)
                    drawCircle(AppColors.TextPrimary, 5f, Offset(tx, ty))
                    val textX = if (c.side == CalloutSide.LEFT) lx - layout.size.width else lx
                    drawText(layout, topLeft = Offset(textX, ly - layout.size.height / 2f))
                }
            }
        }
    }
}
