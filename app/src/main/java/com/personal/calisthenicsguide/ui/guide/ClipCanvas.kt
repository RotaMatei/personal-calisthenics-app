package com.personal.calisthenicsguide.ui.guide

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import com.personal.calisthenics.core.rig.Bounds
import com.personal.calisthenics.core.rig.Camera
import com.personal.calisthenics.core.rig.ClipFrame
import com.personal.calisthenics.core.rig.ClipPlayer
import com.personal.calisthenics.core.rig.OrbitSpec
import com.personal.calisthenics.core.rig.RigLibrary
import com.personal.calisthenics.core.rig.scaledToLoop
import com.personal.calisthenics.core.seed.SeedData
import com.personal.calisthenicsguide.ui.Muted
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap

/** Camera choices of the exercise clip. */
enum class ClipView(val title: String, val orbit: OrbitSpec) {
    ORBIT("3D orbit", OrbitSpec()),
    THREE_QUARTER("3/4", OrbitSpec(centerYaw = Camera.THREE_QUARTER.yaw, sweepDeg = 0f, pitch = Camera.THREE_QUARTER.pitch, perspective = Camera.THREE_QUARTER.perspective)),
    SIDE("Side", OrbitSpec(centerYaw = Camera.SIDE.yaw, sweepDeg = 0f, pitch = 0f, perspective = 0f)),
    FRONT("Front", OrbitSpec(centerYaw = Camera.FRONT.yaw, sweepDeg = 0f, pitch = 0f, perspective = 0f)),
}

/** Framing rectangles are expensive to compute, so they are kept for the life of the process. */
private val boundsCache = ConcurrentHashMap<String, Bounds>()

/** How long one loop of the clip lasts: the exercise's real tempo for rep exercises when asked, else the authored timing. */
private fun tempoLoopMs(exerciseId: String): Long? {
    val step = SeedData.steps.firstOrNull { it.exerciseId == exerciseId || it.exerciseByDay.values.contains(exerciseId) }
    val seconds = step?.tempo?.repSeconds ?: return null
    return (seconds.coerceAtLeast(2) * 1000L)
}

/**
 * A looping 3D clip of an exercise: the humanoid rig moving between Position A and B at the exercise's tempo, with
 * muscles / tendons / joints coloured by how hard they work at that moment. Drag sideways to rotate the camera.
 */
@Composable
fun ClipCanvas(
    exerciseId: String,
    modifier: Modifier = Modifier,
    view: ClipView = ClipView.ORBIT,
    realTempo: Boolean = false,
    playing: Boolean = true,
    draggable: Boolean = true,
    onProgress: (Float) -> Unit = {},
) {
    val exercise = remember(exerciseId) { SeedData.exercise(exerciseId) }
    val player = remember(exerciseId, view, realTempo) {
        val base = RigLibrary.animation(exerciseId)
        val loop = if (realTempo) tempoLoopMs(exerciseId) else null
        val anim = if (loop != null) base.scaledToLoop(loop) else base
        ClipPlayer(anim, exercise.highlights, view.orbit)
    }
    var timeMs by remember(player) { mutableLongStateOf(0L) }
    var yawOffset by remember(player) { mutableFloatStateOf(0f) }

    LaunchedEffect(player, playing) {
        var last = withFrameNanos { it }
        while (isActive) {
            val now = withFrameNanos { it }
            if (playing) timeMs += (now - last) / 1_000_000L
            last = now
        }
    }

    val bounds by produceState<Bounds?>(null, player, draggable) {
        val key = "$exerciseId|${view.name}|$realTempo|$draggable"
        value = boundsCache[key] ?: withContext(Dispatchers.Default) {
            (if (draggable && view == ClipView.ORBIT) player.boundsAnyYaw else player.bounds).also { boundsCache[key] = it }
        }
    }

    val frame by produceState<ClipFrame?>(null, player) {
        snapshotFlow { timeMs to yawOffset }.collectLatest { (t, yaw) ->
            value = withContext(Dispatchers.Default) { player.frame(t, yaw) }
        }
    }

    val f = frame
    val b = bounds
    LaunchedEffect(f?.progress) { f?.progress?.let(onProgress) }

    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        if (f == null || b == null) {
            Muted("Building the 3D clip...")
        } else {
            Canvas(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(draggable) {
                        if (draggable) detectHorizontalDragGestures { _, dx -> yawOffset += dx * 0.45f }
                    },
            ) {
                drawPrims(f.prims, ViewFit(b, size.width, size.height, padding = 8f))
            }
        }
    }
}
