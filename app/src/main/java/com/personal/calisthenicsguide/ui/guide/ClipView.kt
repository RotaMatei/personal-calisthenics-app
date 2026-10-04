package com.personal.calisthenicsguide.ui.guide

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import com.personal.calisthenics.core.model.Highlight
import com.personal.calisthenics.core.rig.Camera
import com.personal.calisthenics.core.rig.ClipPlayer
import com.personal.calisthenics.core.rig.RigAnimation
import com.personal.calisthenics.core.rig.RigFraming
import com.personal.calisthenics.core.rig.RigLibrary
import com.personal.calisthenics.core.rig.scaledToLoop
import com.personal.calisthenics.core.seed.SeedData
import com.personal.calisthenicsguide.ui.theme.AppColors

private enum class ClipView(val title: String) { ORBIT("3D orbit"), SIDE("Side"), FRONT("Front") }

/** The exercise's real rep length when the workout uses it with a tempo, else the authored loop. */
private fun animationFor(exerciseId: String): RigAnimation? {
    val base = RigLibrary.animationOrNull(exerciseId) ?: return null
    val tempo = SeedData.steps.firstOrNull { step ->
        step.tempo != null && (step.exerciseId == exerciseId || step.exerciseByDay.values.contains(exerciseId))
    }?.tempo
    return if (tempo != null) base.scaledToLoop(maxOf(tempo.repSeconds, 3) * 1000L) else base
}

/**
 * Looping 3D clip of [exerciseId]: auto-orbiting camera, drag to rotate, chips to jump to the side or front view.
 * Highlights are coloured by this exercise's load and pulse with the rep phase.
 */
@Composable
fun ClipView(exerciseId: String, highlights: List<Highlight>, modifier: Modifier = Modifier) {
    val animation = remember(exerciseId) { animationFor(exerciseId) }
    if (animation == null) {
        Text("No 3D clip for this exercise.", modifier = modifier.padding(16.dp))
        return
    }
    val player = remember(exerciseId, highlights) { ClipPlayer(animation, highlights) }
    var view by remember(exerciseId) { mutableStateOf(ClipView.ORBIT) }
    var playing by remember(exerciseId) { mutableStateOf(true) }
    var timeMs by remember(exerciseId) { mutableLongStateOf(0L) }
    var yawOffset by remember(exerciseId) { mutableFloatStateOf(0f) }

    // Animation clock: advances only while playing.
    LaunchedEffect(exerciseId, playing) {
        if (!playing) return@LaunchedEffect
        var last = withFrameNanos { it }
        while (true) {
            val now = withFrameNanos { it }
            timeMs += (now - last) / 1_000_000L
            last = now
        }
    }

    val fixedBounds = remember(exerciseId, view) {
        when (view) {
            ClipView.ORBIT -> null
            ClipView.SIDE -> RigFraming.bounds(animation, Camera.SIDE)
            ClipView.FRONT -> RigFraming.bounds(animation, Camera.FRONT)
        }
    }

    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(340.dp)
                .clip(RoundedCornerShape(18.dp))
                .background(Color(0xFF111318))
                .pointerInput(view) {
                    if (view == ClipView.ORBIT) {
                        detectDragGestures { _, drag -> yawOffset += drag.x * 0.4f }
                    }
                },
        ) {
            Canvas(Modifier.fillMaxWidth().height(340.dp)) {
                val frame = when (view) {
                    ClipView.ORBIT -> player.frame(timeMs, yawOffset)
                    ClipView.SIDE -> player.frame(timeMs, camera = Camera.SIDE)
                    ClipView.FRONT -> player.frame(timeMs, camera = Camera.FRONT)
                }
                val bounds = fixedBounds ?: player.boundsAnyYaw
                drawPrims(frame.prims, ViewFit(bounds, size.width, size.height, pad = 8f))
            }
            // Multi-part clips (joint circles) name the part being played: Neck, Shoulders, Elbows, Hips, Ankles.
            val caption = animation.captionAt(timeMs)
            if (caption != null) {
                Text(
                    caption,
                    color = MaterialTheme.colorScheme.onSurface,
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.align(Alignment.TopCenter).padding(12.dp),
                )
            }
            Text(
                if (view == ClipView.ORBIT) "Drag to rotate" else "Tap Play/Pause to freeze",
                color = AppColors.TextSecondary,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.align(Alignment.BottomStart).padding(10.dp),
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            ClipView.entries.forEach { v ->
                FilterChip(
                    selected = view == v,
                    onClick = { view = v; if (v == ClipView.ORBIT) yawOffset = 0f },
                    label = { Text(v.title) },
                    modifier = Modifier.heightIn(min = 48.dp),
                )
            }
            FilterChip(
                selected = !playing,
                onClick = { playing = !playing },
                label = { Text(if (playing) "Pause" else "Play") },
                modifier = Modifier.heightIn(min = 48.dp),
            )
        }
    }
}
