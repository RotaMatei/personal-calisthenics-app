package com.personal.calisthenicsguide.ui.player

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.personal.calisthenics.core.analytics.JointRating
import com.personal.calisthenics.core.model.StepKind
import com.personal.calisthenics.core.plan.WorkItem
import com.personal.calisthenics.core.seed.SeedData
import com.personal.calisthenics.core.session.EngineSnapshot
import com.personal.calisthenics.core.timer.PhaseKind
import com.personal.calisthenicsguide.session.SessionController
import com.personal.calisthenicsguide.ui.BigButton
import com.personal.calisthenicsguide.ui.Muted
import com.personal.calisthenicsguide.ui.OutlineBigButton
import com.personal.calisthenicsguide.ui.Palette
import com.personal.calisthenicsguide.ui.Panel
import com.personal.calisthenicsguide.ui.Pill
import com.personal.calisthenicsguide.ui.SectionTitle
import com.personal.calisthenicsguide.ui.clock
import com.personal.calisthenicsguide.ui.guide.ClipCanvas

/** Scrollable content with the action buttons pinned to the bottom (the easiest part of the screen to reach). */
@Composable
private fun StageLayout(
    bottom: @Composable ColumnScope.() -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
            content = content,
        )
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            content = bottom,
        )
    }
}

@Composable
private fun BigClock(text: String, color: Color, modifier: Modifier = Modifier) {
    Text(
        text,
        style = MaterialTheme.typography.displayLarge,
        color = color,
        textAlign = TextAlign.Center,
        modifier = modifier.fillMaxWidth(),
    )
}

private fun targetText(item: WorkItem): String = when (item.kind) {
    StepKind.ISOMETRIC -> "Hold ${item.holdMinSec}-${item.holdMaxSec} s"
    else -> {
        val reps = if (item.repsMin == item.repsMax) "${item.repsMax}" else "${item.repsMin}-${item.repsMax}"
        "$reps reps${if (item.side != null) " (${item.side?.label})" else ""}"
    }
}

private fun restText(item: WorkItem): String =
    if (item.restMinSec == item.restMaxSec) "${item.restMaxSec} s rest" else "${item.restMinSec}-${item.restMaxSec} s rest"

// ---------------------------------------------------------------------- flow (warm-up, decompression)

@Composable
fun FlowReadyStage(snap: EngineSnapshot, controller: SessionController) {
    val seconds = snap.flowItems.sumOf { it.seconds } + snap.flowItems.size * 10
    StageLayout(
        bottom = { BigButton("Start", onClick = controller::startFlow, modifier = Modifier.fillMaxWidth()) },
    ) {
        Text(snap.phase?.title ?: "", style = MaterialTheme.typography.headlineSmall, color = Palette.Amber)
        Muted("${snap.flowItems.size} drills, about ${(seconds + 30) / 60} min. The timer rolls from drill to drill on its own, with a 10 second setup beat before each one.")
        Panel {
            for (item in snap.flowItems) {
                Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(item.label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                    Text("${item.seconds} s", style = MaterialTheme.typography.bodyLarge, color = Palette.Muted)
                }
            }
        }
    }
}

@Composable
fun FlowStage(snap: EngineSnapshot, controller: SessionController) {
    val t = snap.timer ?: return
    val phase = t.phase ?: return
    val transition = phase.kind == PhaseKind.TRANSITION
    val color = if (transition) Palette.Cyan else Palette.Green
    StageLayout(
        bottom = {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                OutlineBigButton("Back", onClick = controller::previousFlowPhase, modifier = Modifier.weight(1f))
                BigButton(
                    if (snap.paused) "Resume" else "Pause",
                    onClick = { if (snap.paused) controller.resume() else controller.pause() },
                    modifier = Modifier.weight(1f),
                )
                OutlineBigButton("Skip", onClick = controller::skipFlowPhase, modifier = Modifier.weight(1f))
            }
        },
    ) {
        val index = phase.itemIndex ?: 0
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Pill(if (transition) "SETUP" else "GO", color)
            Muted("Drill ${index + 1} of ${snap.flowItems.size}")
        }
        Text(phase.label, style = MaterialTheme.typography.headlineMedium)
        BigClock(clock(t.phaseRemainingSeconds), color)
        if (phase.hint.isNotBlank()) Text(phase.hint, style = MaterialTheme.typography.bodyLarge)
        if (phase.subLabels.isNotEmpty()) {
            Panel {
                val active = t.subLabelIndex
                phase.subLabels.forEachIndexed { i, label ->
                    Text(
                        label,
                        style = if (i == active) MaterialTheme.typography.titleLarge else MaterialTheme.typography.bodyLarge,
                        color = if (i == active) Palette.Amber else Palette.Muted,
                    )
                }
            }
        }
        LinearProgressIndicator(
            progress = { t.totalProgress },
            modifier = Modifier.fillMaxWidth().height(8.dp),
            color = color,
            trackColor = Palette.SurfaceHigh,
        )
        if (snap.paused) Text("Paused", style = MaterialTheme.typography.titleMedium, color = Palette.Red)
    }
}

// ---------------------------------------------------------------------- one set

@Composable
fun ReadyStage(snap: EngineSnapshot, controller: SessionController) {
    val item = snap.item ?: return
    StageLayout(
        bottom = {
            BigButton("Start set", onClick = controller::startSet, modifier = Modifier.fillMaxWidth())
            OutlineBigButton("Skip this set", onClick = controller::skipSet, modifier = Modifier.fillMaxWidth())
        },
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Pill("Set ${item.setNumber} of ${item.totalSets}", Palette.Amber)
            item.side?.let { Pill(it.label, Palette.Cyan) }
            if (snap.coldMode && item.supersetWith != null) Pill("Superset", Palette.Green)
        }
        Text(item.title, style = MaterialTheme.typography.headlineMedium)
        Box(modifier = Modifier.fillMaxWidth().height(260.dp)) {
            ClipCanvas(item.exerciseId, Modifier.fillMaxSize())
        }
        Panel {
            Text(targetText(item), style = MaterialTheme.typography.headlineSmall, color = Palette.Amber)
            item.tempo?.let { Muted("Tempo ${it.notation}  (lower - pause - drive - pause)") }
            if (item.kind == StepKind.ISOMETRIC) Muted("A 5 second get-ready beat comes first: get into position and lock the shoulder blades.")
            Muted(restText(item))
        }
        if (snap.coldMode && item.supersetWith != null) {
            Panel(color = MaterialTheme.colorScheme.primaryContainer) {
                Text("Antagonist superset with ${item.supersetWith}: alternate the two exercises, rest capped at 90 s.", style = MaterialTheme.typography.bodyLarge)
            }
        }
        if (item.cue.isNotBlank()) Text(item.cue, style = MaterialTheme.typography.bodyLarge)
        snap.nextItem?.let { Muted("Then: ${it.title}") }
    }
}

@Composable
fun ActiveStage(snap: EngineSnapshot, controller: SessionController) {
    val t = snap.timer ?: return
    val phase = t.phase ?: return
    val item = snap.item
    StageLayout(
        bottom = {
            BigButton(
                if (snap.targetReps == null) "Stop hold" else "Finish set",
                onClick = controller::stopSet,
                modifier = Modifier.fillMaxWidth(),
            )
            OutlineBigButton(
                if (snap.paused) "Resume" else "Pause",
                onClick = { if (snap.paused) controller.resume() else controller.pause() },
                modifier = Modifier.fillMaxWidth(),
            )
        },
    ) {
        if (item != null) {
            Text(item.title, style = MaterialTheme.typography.titleLarge)
            Muted("Set ${item.setNumber} of ${item.totalSets}  |  ${targetText(item)}")
        }
        if (snap.targetReps == null) IsometricActive(snap, phase.kind) else TempoActive(snap)
        if (snap.paused) Text("Paused", style = MaterialTheme.typography.titleMedium, color = Palette.Red)
    }
}

@Composable
private fun ColumnScope.IsometricActive(snap: EngineSnapshot, kind: PhaseKind) {
    val t = snap.timer ?: return
    val item = snap.item
    if (kind == PhaseKind.GET_READY) {
        Pill("GET READY", Palette.Cyan)
        BigClock(clock(t.phaseRemainingSeconds), Palette.Cyan)
        Text(t.phase?.hint ?: "", style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
        return
    }
    val held = (t.phaseElapsedMs / 1000L).toInt()
    val min = item?.holdMinSec ?: 0
    val reached = held >= min
    Pill("HOLD", if (reached) Palette.Green else Palette.Amber)
    BigClock("$held s", if (reached) Palette.Green else Palette.Amber)
    LinearProgressIndicator(
        progress = { t.phaseProgress },
        modifier = Modifier.fillMaxWidth().height(14.dp),
        color = if (reached) Palette.Green else Palette.Amber,
        trackColor = Palette.SurfaceHigh,
    )
    Muted(
        if (reached) "Minimum of $min s reached. Keep going to ${item?.holdMaxSec} s while the form stays clean."
        else "Aim for at least $min s. Ends automatically at ${item?.holdMaxSec} s.",
    )
    Text(t.phase?.hint ?: "", style = MaterialTheme.typography.bodyLarge)
}

@Composable
private fun ColumnScope.TempoActive(snap: EngineSnapshot) {
    val t = snap.timer ?: return
    val phase = t.phase ?: return
    val total = snap.targetReps ?: 0
    val color = when (phase.kind) {
        PhaseKind.LOWER -> Palette.Cyan
        PhaseKind.DRIVE -> Palette.Green
        PhaseKind.GET_READY -> Palette.Muted
        else -> Palette.Amber
    }
    // 1 = top position, 0 = bottom position: lowering moves the marker down, driving moves it up.
    val position = when (phase.kind) {
        PhaseKind.LOWER -> 1f - t.phaseProgress
        PhaseKind.PAUSE_BOTTOM -> 0f
        PhaseKind.DRIVE -> t.phaseProgress
        else -> 1f
    }
    val rep = phase.rep
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
        PacingBar(position, color, modifier = Modifier.width(48.dp).height(240.dp))
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(if (rep != null) "Rep $rep of $total" else "Get set", style = MaterialTheme.typography.titleLarge)
            Text(phase.label, style = MaterialTheme.typography.displayMedium, color = color)
            Text("${t.phaseRemainingSeconds}", style = MaterialTheme.typography.displayLarge, color = color)
            Muted(phase.hint)
        }
    }
}

@Composable
private fun PacingBar(position: Float, color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val trackWidth = size.width * 0.34f
        drawRoundRect(
            color = Palette.SurfaceHigh,
            topLeft = Offset((size.width - trackWidth) / 2f, 0f),
            size = Size(trackWidth, size.height),
            cornerRadius = CornerRadius(trackWidth / 2f),
        )
        val radius = size.width * 0.5f
        val y = radius + (size.height - 2f * radius) * (1f - position.coerceIn(0f, 1f))
        drawCircle(color = color, radius = radius, center = Offset(size.width / 2f, y))
    }
}

// ---------------------------------------------------------------------- logging

@Composable
private fun Stepper(label: String, value: String, onMinus: () -> Unit, onPlus: () -> Unit) {
    Panel {
        Muted(label)
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            BigButton("-", onClick = onMinus, modifier = Modifier.width(88.dp), container = Palette.SurfaceHigh, content = Color.White)
            Text(value, style = MaterialTheme.typography.headlineMedium, textAlign = TextAlign.Center, modifier = Modifier.weight(1f))
            BigButton("+", onClick = onPlus, modifier = Modifier.width(88.dp), container = Palette.SurfaceHigh, content = Color.White)
        }
    }
}

@Composable
fun LoggingStage(snap: EngineSnapshot, defaultLevel: Int, controller: SessionController) {
    val item = snap.item ?: return
    val exercise = remember(item.exerciseId) { SeedData.exercise(item.exerciseId) }
    val isHold = item.kind == StepKind.ISOMETRIC
    var reps by remember(item.key) { mutableIntStateOf(snap.suggestedReps ?: item.repsMax ?: 8) }
    var hold by remember(item.key) { mutableIntStateOf(snap.suggestedHold ?: item.holdMaxSec ?: 20) }
    var rir by remember(item.key) { mutableIntStateOf(1) }
    var level by remember(item.key) { mutableIntStateOf(defaultLevel.coerceIn(0, exercise.ladder.lastIndex.coerceAtLeast(0))) }

    StageLayout(
        bottom = {
            BigButton(
                "Save set",
                onClick = { controller.logSet(if (isHold) null else reps, if (isHold) hold else null, if (isHold) null else rir, level) },
                modifier = Modifier.fillMaxWidth(),
            )
        },
    ) {
        Text("Log your set", style = MaterialTheme.typography.headlineSmall, color = Palette.Amber)
        Muted("${item.title}  |  set ${item.setNumber} of ${item.totalSets}${item.side?.let { "  |  ${it.label}" } ?: ""}")
        if (isHold) {
            Stepper("Seconds held", "$hold s", onMinus = { hold = (hold - 1).coerceAtLeast(0) }, onPlus = { hold += 1 })
        } else {
            Stepper("Reps completed", "$reps", onMinus = { reps = (reps - 1).coerceAtLeast(0) }, onPlus = { reps += 1 })
            Panel {
                Muted("Reps in reserve (how many more clean reps you had left)")
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    for (value in 0..3) {
                        val selected = rir == value
                        Button(
                            onClick = { rir = value },
                            modifier = Modifier.weight(1f).height(64.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (selected) Palette.Amber else Palette.SurfaceHigh,
                                contentColor = if (selected) Color.Black else Color.White,
                            ),
                        ) { Text(if (value == 3) "3+" else "$value", style = MaterialTheme.typography.titleLarge) }
                    }
                }
            }
        }
        if (exercise.ladder.size > 1) {
            Stepper(
                "Progression level",
                exercise.ladder[level].name,
                onMinus = { level = (level - 1).coerceAtLeast(0) },
                onPlus = { level = (level + 1).coerceAtMost(exercise.ladder.lastIndex) },
            )
            Muted(exercise.ladder[level].description)
        }
    }
}

// ---------------------------------------------------------------------- rest

@Composable
fun RestStage(snap: EngineSnapshot, controller: SessionController) {
    val t = snap.timer ?: return
    StageLayout(
        bottom = {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                OutlineBigButton("+30 s", onClick = { controller.extendRest(30) }, modifier = Modifier.weight(1f))
                BigButton("Skip rest", onClick = controller::skipRest, modifier = Modifier.weight(2f))
            }
        },
    ) {
        Pill("REST", Palette.Cyan)
        BigClock(clock(t.phaseRemainingSeconds), Palette.Cyan)
        LinearProgressIndicator(
            progress = { t.phaseProgress },
            modifier = Modifier.fillMaxWidth().height(10.dp),
            color = Palette.Cyan,
            trackColor = Palette.SurfaceHigh,
        )
        Muted("Breathe slowly and shake out the arms.")
        val next = snap.nextItem
        Panel {
            Muted("Next up")
            if (next != null) {
                SectionTitle(next.title)
                Text("Set ${next.setNumber} of ${next.totalSets}  |  ${targetText(next)}", style = MaterialTheme.typography.bodyLarge)
                next.tempo?.let { Muted("Tempo ${it.notation}") }
            } else {
                SectionTitle("Last stretch")
                Muted("After this rest the session moves on.")
            }
        }
        if (snap.coldMode) Muted("Cold weather mode: rests are capped at 90 s to keep the muscles warm.")
    }
}

// ---------------------------------------------------------------------- joint log

@Composable
private fun RatingSlider(title: String, hint: String, value: Float, onChange: (Float) -> Unit) {
    val rating = value.toInt()
    Panel {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(title, style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
            Text("$rating / 5", style = MaterialTheme.typography.headlineSmall, color = if (rating > 2) Palette.Red else Palette.Green)
        }
        Slider(
            value = value,
            onValueChange = { onChange(it.toInt().toFloat()) },
            valueRange = 1f..5f,
            steps = 3,
            colors = SliderDefaults.colors(thumbColor = Palette.Amber, activeTrackColor = Palette.Amber),
            modifier = Modifier.height(56.dp),
        )
        Muted(hint)
    }
}

@Composable
fun JointLogStage(controller: SessionController) {
    var wrists by remember { mutableFloatStateOf(1f) }
    var elbows by remember { mutableFloatStateOf(1f) }
    var shoulders by remember { mutableFloatStateOf(1f) }
    StageLayout(
        bottom = {
            BigButton(
                "Save and finish",
                onClick = { controller.finish(JointRating(wrists.toInt(), elbows.toInt(), shoulders.toInt())) },
                modifier = Modifier.fillMaxWidth(),
            )
            OutlineBigButton("Skip rating", onClick = { controller.finish(null) }, modifier = Modifier.fillMaxWidth())
        },
    ) {
        Text("How do your joints feel?", style = MaterialTheme.typography.headlineSmall, color = Palette.Amber)
        Muted("1 = nothing at all, 5 = clearly sore. Be honest: an elbow rating above 2 switches your next pull-ups to a neutral grip.")
        RatingSlider("Wrists", "Pressure in the wrist crease or ache after the support work.", wrists) { wrists = it }
        RatingSlider("Elbows", "Inner-elbow (golfer's elbow) tightness or tendon ache.", elbows) { elbows = it }
        RatingSlider("Shoulders", "Front or top of the shoulder, especially after pressing and holds.", shoulders) { shoulders = it }
    }
}
