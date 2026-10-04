package com.personal.calisthenicsguide.ui.guide

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.personal.calisthenics.core.model.Highlight
import com.personal.calisthenics.core.rig.Bounds
import com.personal.calisthenics.core.rig.ClipPlayer
import com.personal.calisthenics.core.rig.Prim
import com.personal.calisthenics.core.rig.RigAnimation
import com.personal.calisthenics.core.rig.RigLibrary
import com.personal.calisthenicsguide.ui.theme.AppColors
import com.personal.calisthenicsguide.ui.theme.Space

private class ClockAnchor(var sourceMs: Long, var nanos: Long)

/**
 * A smooth clip clock that follows a timer which only reports ~10 times a second. Every new [sourceMs] re-anchors the
 * clock; between reports it runs on at real time (unless [running] is false), so the 3D clip moves at the display's
 * frame rate yet never drifts from the timer, its beeps or its sub-labels.
 */
@Composable
fun rememberSyncedClock(sourceMs: Long, running: Boolean): State<Long> {
    val anchor = remember { ClockAnchor(sourceMs, System.nanoTime()) }
    val clock = remember { mutableLongStateOf(sourceMs) }
    val isRunning by rememberUpdatedState(running)
    SideEffect {
        anchor.sourceMs = sourceMs
        anchor.nanos = System.nanoTime()
    }
    LaunchedEffect(Unit) {
        while (true) {
            withFrameNanos {
                val now = System.nanoTime()
                clock.longValue = if (isRunning) anchor.sourceMs + (now - anchor.nanos) / 1_000_000L else anchor.sourceMs
            }
        }
    }
    return clock
}

/** A clip clock that simply runs from zero (previews that are not tied to a timer). */
@Composable
fun rememberFreeClock(): State<Long> {
    val clock = remember { mutableLongStateOf(0L) }
    LaunchedEffect(Unit) {
        var last = withFrameNanos { it }
        while (true) {
            val now = withFrameNanos { it }
            clock.longValue += (now - last) / 1_000_000L
            last = now
        }
    }
    return clock
}

/**
 * The 3D clip filling its parent, at the time given by [clock]. [topReserve] and [bottomReserve] keep the figure clear of
 * text laid over the picture; the dark backdrop still fills everything. Dragging turns the camera.
 */
@Composable
fun DrivenClip(
    animation: RigAnimation,
    highlights: List<Highlight>,
    clock: State<Long>,
    modifier: Modifier = Modifier,
    topReserve: Dp = 0.dp,
    bottomReserve: Dp = 0.dp,
) {
    val player = remember(animation, highlights) { ClipPlayer(animation, highlights) }
    var yaw by remember(animation) { mutableFloatStateOf(0f) }
    Canvas(
        modifier
            .background(AppColors.ClipBackdrop)
            .pointerInput(animation) { detectHorizontalDragGestures { _, drag -> yaw += drag * 0.4f } },
    ) {
        val top = topReserve.toPx()
        val usable = (size.height - top - bottomReserve.toPx()).coerceAtLeast(1f)
        val frame = player.frame(clock.value, yaw)
        translate(top = top) {
            drawPrims(frame.prims, ViewFit(player.boundsAnyYaw, size.width, usable, pad = 10f))
        }
    }
}

private class StillData(val prims: List<Prim>, val bounds: Bounds)

/** Position A of an exercise as a small, still picture: the thumbnail of a row on the workout details page. */
@Composable
fun ClipStill(exerciseId: String, modifier: Modifier = Modifier) {
    val still = remember(exerciseId) {
        RigLibrary.animationOrNull(exerciseId)?.let { animation ->
            val player = ClipPlayer(animation, emptyList())
            StillData(player.frame(0L).prims, player.bounds)
        }
    }
    val shape = RoundedCornerShape(Space.md)
    if (still == null) {
        Box(modifier.clip(shape).background(AppColors.ClipBackdrop), contentAlignment = Alignment.Center) {
            Text("-", style = MaterialTheme.typography.titleMedium, color = AppColors.TextSecondary)
        }
        return
    }
    Canvas(modifier.clip(shape).background(AppColors.ClipBackdrop)) {
        drawPrims(still.prims, ViewFit(still.bounds, size.width, size.height, pad = 4f))
    }
}
