package com.personal.calisthenicsguide.ui.workout

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.FilterChip
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.personal.calisthenics.core.analytics.JointRating
import com.personal.calisthenics.core.analytics.ProgressionState
import com.personal.calisthenics.core.model.StepKind
import com.personal.calisthenics.core.model.WorkoutDay
import com.personal.calisthenics.core.plan.WorkItem
import com.personal.calisthenics.core.rig.ClipSync
import com.personal.calisthenics.core.rig.RigAnimation
import com.personal.calisthenics.core.rig.RigLibrary
import com.personal.calisthenics.core.seed.SeedData
import com.personal.calisthenics.core.session.EngineSnapshot
import com.personal.calisthenics.core.session.SessionMode
import com.personal.calisthenics.core.timer.PhaseKind
import com.personal.calisthenics.core.timer.Timers
import com.personal.calisthenicsguide.session.RunnerState
import com.personal.calisthenicsguide.session.SessionRunner
import com.personal.calisthenicsguide.ui.components.AppCard
import com.personal.calisthenicsguide.ui.components.Pill
import com.personal.calisthenicsguide.ui.components.PrimaryButton
import com.personal.calisthenicsguide.ui.components.SecondaryButton
import com.personal.calisthenicsguide.ui.dashboard.DashboardState
import com.personal.calisthenicsguide.ui.guide.ClipStill
import com.personal.calisthenicsguide.ui.guide.DrivenClip
import com.personal.calisthenicsguide.ui.guide.animationFor
import com.personal.calisthenicsguide.ui.guide.rememberFreeClock
import com.personal.calisthenicsguide.ui.guide.rememberSyncedClock
import com.personal.calisthenicsguide.ui.theme.AppColors
import com.personal.calisthenicsguide.ui.theme.Space
import kotlin.math.roundToInt

private val SessionButtonHeight = 64.dp

/**
 * True while a rep-counted strength set is running and its 3D clip exists: that screen is the clip, edge to edge, with
 * a Next button at the bottom. The app hides the navigation bar then.
 */
fun isRepPlayer(snapshot: EngineSnapshot?): Boolean {
    if (snapshot == null || snapshot.mode != SessionMode.ACTIVE) return false
    val item = snapshot.item ?: return false
    return item.kind == StepKind.STRENGTH && RigLibrary.animationOrNull(item.exerciseId) != null
}

@Composable
fun WorkoutScreen(
    runnerState: RunnerState,
    runner: SessionRunner,
    progression: Map<String, ProgressionState>,
    dashboard: DashboardState?,
    onColdMode: (Boolean) -> Unit,
    onPickDay: (WorkoutDay?) -> Unit,
    onStart: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val snapshot = runnerState.snapshot
    if (!runnerState.active || snapshot == null) {
        WorkoutDetailsScreen(dashboard, onColdMode, onPickDay, onStart, modifier)
        return
    }
    val coldMode = runnerState.plan?.options?.coldMode ?: false
    Column(modifier.fillMaxSize()) {
        if (!isRepPlayer(snapshot)) SessionHeader(snapshot, coldMode, onEnd = runner::endEarly)
        when (snapshot.mode) {
            SessionMode.FLOW -> FlowMode(snapshot, runner)
            SessionMode.READY -> ReadyMode(snapshot, runner)
            SessionMode.ACTIVE -> if (isRepPlayer(snapshot)) RepPlayerMode(snapshot, runner) else ActiveMode(snapshot, runner)
            SessionMode.AWAITING_LOG -> LogMode(snapshot, runner, progression)
            SessionMode.RESTING -> RestMode(snapshot, runner)
            SessionMode.FINISHED -> FinishedMode(runner)
            SessionMode.NOT_STARTED -> Text("Starting...", modifier = Modifier.padding(Space.screen), color = AppColors.TextSecondary)
        }
    }
}

// ------------------------------------------------------------------------------------------------ header

@Composable
private fun SessionHeader(snapshot: EngineSnapshot, coldMode: Boolean, onEnd: () -> Unit) {
    Column(Modifier.padding(horizontal = Space.screen).padding(top = Space.sm), verticalArrangement = Arrangement.spacedBy(Space.xs)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    snapshot.phase?.let { "Phase ${it.index}: ${it.title}" } ?: "Workout",
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                )
                val sub = buildList {
                    if (snapshot.setsTotal > 0) add("${snapshot.setsLogged}/${snapshot.setsTotal} sets logged")
                    if (coldMode) add("Cold-weather supersets")
                }.joinToString("  |  ")
                if (sub.isNotEmpty()) Text(sub, style = MaterialTheme.typography.bodyMedium, color = AppColors.TextSecondary)
            }
            if (snapshot.mode != SessionMode.FINISHED) TextButton(onClick = onEnd) { Text("End", color = AppColors.Danger) }
        }
        if (snapshot.setsTotal > 0) {
            LinearProgressIndicator(
                progress = { snapshot.setsLogged.toFloat() / snapshot.setsTotal },
                modifier = Modifier.fillMaxWidth().height(4.dp),
                color = AppColors.Accent,
                trackColor = AppColors.SurfaceHigh,
            )
        }
    }
}

// ------------------------------------------------------------------------------------------------ shared pieces

/** The 3D clip as a framed stage; [overlay] puts timers and labels on top of the picture. */
@Composable
private fun ClipStage(
    animation: RigAnimation?,
    exerciseId: String,
    clock: State<Long>,
    modifier: Modifier = Modifier,
    topReserve: Dp = 0.dp,
    bottomReserve: Dp = 0.dp,
    framed: Boolean = true,
    overlay: @Composable BoxScope.() -> Unit = {},
) {
    val highlights = remember(exerciseId) { SeedData.exerciseOrNull(exerciseId)?.highlights ?: emptyList() }
    Box(if (framed) modifier.clip(RoundedCornerShape(Space.corner)) else modifier) {
        if (animation != null) {
            DrivenClip(animation, highlights, clock, Modifier.fillMaxSize(), topReserve, bottomReserve)
        } else {
            Box(Modifier.fillMaxSize().background(AppColors.ClipBackdrop))
        }
        overlay()
    }
}

@Composable
private fun Countdown(seconds: Int, color: Color, size: Int = 44, modifier: Modifier = Modifier) {
    Text(formatSeconds(seconds), modifier = modifier, fontSize = size.sp, fontWeight = FontWeight.SemiBold, color = color)
}

@Composable
private fun ThinBar(progress: Float, color: Color, modifier: Modifier = Modifier) {
    LinearProgressIndicator(
        progress = { progress.coerceIn(0f, 1f) },
        modifier = modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)),
        color = color,
        trackColor = AppColors.SurfaceHigh,
    )
}

private fun setLine(item: WorkItem): String =
    "Set ${item.setNumber} of ${item.totalSets}" + (item.side?.let { " - ${it.label}" } ?: "")

private fun targetText(item: WorkItem): String = when (item.kind) {
    StepKind.ISOMETRIC -> "Hold ${item.holdMinSec}-${item.holdMaxSec} s"
    else -> {
        val reps = if (item.repsMin == item.repsMax) "${item.repsMax}" else "${item.repsMin}-${item.repsMax}"
        "$reps reps" + (item.side?.let { " (${it.label})" } ?: "")
    }
}

// ------------------------------------------------------------------------------------------------ warm-up and decompression

@Composable
private fun ColumnScope.FlowMode(snapshot: EngineSnapshot, runner: SessionRunner) {
    val timer = snapshot.timer
    val phase = timer?.phase
    if (timer == null || phase == null) {
        Spacer(Modifier.weight(1f))
        return
    }
    val transition = phase.kind == PhaseKind.TRANSITION
    val drill = snapshot.flowItem
    val exerciseId = drill?.exerciseId ?: ""
    val animation = remember(exerciseId) { RigLibrary.animationOrNull(exerciseId) }
    // Multi-part clips (joint circles) are stretched over the drill so their parts match the timer's sub-labels.
    val source = if (animation != null && !transition) ClipSync.drillTimeMs(animation, timer.phaseElapsedMs, phase.durationMs) else timer.phaseElapsedMs
    val clock = rememberSyncedClock(source, running = !snapshot.paused)
    val accent = if (transition) AppColors.Warn else AppColors.Good

    ClipStage(
        animation, exerciseId, clock,
        Modifier.weight(1f).fillMaxWidth().padding(horizontal = Space.screen, vertical = Space.sm),
        topReserve = 56.dp, bottomReserve = 14.dp,
    ) {
        Pill(if (transition) "Get ready" else "Now", accent, Modifier.align(Alignment.TopStart).padding(Space.md))
        Countdown(timer.phaseRemainingSeconds, if (transition) AppColors.Warn else AppColors.TextPrimary, modifier = Modifier.align(Alignment.TopEnd).padding(horizontal = Space.md, vertical = Space.xs))
        ThinBar(timer.phaseProgress, accent, Modifier.align(Alignment.BottomCenter).padding(Space.md))
    }
    Column(Modifier.padding(horizontal = Space.screen), verticalArrangement = Arrangement.spacedBy(Space.xs)) {
        Text(drill?.label ?: phase.label, style = MaterialTheme.typography.titleLarge)
        val subIndex = timer.subLabelIndex
        if (!transition && phase.subLabels.isNotEmpty() && subIndex != null) {
            Text(phase.subLabels[subIndex], style = MaterialTheme.typography.titleMedium, color = AppColors.Info)
        }
        drill?.let { Text(it.instruction, style = MaterialTheme.typography.bodyMedium, color = AppColors.TextSecondary, maxLines = 3) }
        Text(
            "${(timer.totalProgress * 100).roundToInt()}% of this block - the next drill starts by itself",
            style = MaterialTheme.typography.labelMedium,
            color = AppColors.TextSecondary,
        )
    }
    Row(Modifier.fillMaxWidth().padding(Space.screen), horizontalArrangement = Arrangement.spacedBy(Space.sm)) {
        SecondaryButton("Back", runner::skipFlowBack, Modifier.weight(1f), SessionButtonHeight)
        SecondaryButton(if (snapshot.paused) "Resume" else "Pause", runner::togglePause, Modifier.weight(1f), SessionButtonHeight)
        SecondaryButton("Skip", runner::skipFlowForward, Modifier.weight(1f), SessionButtonHeight)
    }
}

// ------------------------------------------------------------------------------------------------ get ready for a set

@Composable
private fun ColumnScope.ReadyMode(snapshot: EngineSnapshot, runner: SessionRunner) {
    val item = snapshot.item ?: return
    val animation = remember(item.exerciseId) { animationFor(item.exerciseId) }
    val clock = rememberFreeClock()
    ClipStage(
        animation, item.exerciseId, clock,
        Modifier.weight(1f).fillMaxWidth().padding(horizontal = Space.screen, vertical = Space.sm),
        topReserve = 44.dp, bottomReserve = 8.dp,
    ) {
        Pill("Up next", AppColors.Info, Modifier.align(Alignment.TopStart).padding(Space.md))
    }
    Column(Modifier.padding(horizontal = Space.screen), verticalArrangement = Arrangement.spacedBy(Space.xs)) {
        Text(item.title, style = MaterialTheme.typography.titleLarge)
        Text(setLine(item), style = MaterialTheme.typography.titleMedium, color = AppColors.Accent)
        Text(
            targetText(item) + (item.tempo?.let { "  |  Tempo ${it.notation}" } ?: ""),
            style = MaterialTheme.typography.bodyLarge,
        )
        item.supersetWith?.let { Text("Superset with: $it", style = MaterialTheme.typography.bodyMedium, color = AppColors.Warn) }
        if (item.cue.isNotBlank()) Text(item.cue, style = MaterialTheme.typography.bodyMedium, color = AppColors.TextSecondary, maxLines = 3)
        if (item.kind == StepKind.ISOMETRIC) {
            Text("Start gives you a 5 s countdown to get into position and lock the scapula.", style = MaterialTheme.typography.bodyMedium, color = AppColors.TextSecondary)
        }
    }
    Box(Modifier.padding(Space.screen)) {
        PrimaryButton(if (item.kind == StepKind.ISOMETRIC) "Start (5 s get-ready)" else "Start set", runner::startSet, height = SessionButtonHeight)
    }
}

// ------------------------------------------------------------------------------------------------ holds (and rep sets without a clip)

@Composable
private fun ColumnScope.ActiveMode(snapshot: EngineSnapshot, runner: SessionRunner) {
    val item = snapshot.item ?: return
    val timer = snapshot.timer
    val phase = timer?.phase
    val animation = remember(item.exerciseId) { animationFor(item.exerciseId) }
    val clock = rememberSyncedClock(timer?.totalElapsedMs ?: 0L, running = !snapshot.paused)
    val color = phase?.let { phaseColor(it.kind) } ?: AppColors.Accent
    val isHold = item.kind == StepKind.ISOMETRIC

    ClipStage(
        animation, item.exerciseId, clock,
        Modifier.weight(1f).fillMaxWidth().padding(horizontal = Space.screen, vertical = Space.sm),
        topReserve = 56.dp, bottomReserve = 14.dp,
    ) {
        if (timer != null && phase != null) {
            val label = when {
                isHold -> if (phase.kind == PhaseKind.GET_READY) "Get ready" else "Hold"
                else -> phase.rep?.let { "Rep $it of ${item.repsMax ?: item.repsMin ?: 1}" } ?: "Get set"
            }
            Pill(label, color, Modifier.align(Alignment.TopStart).padding(Space.md))
            Countdown(timer.phaseRemainingSeconds, color, modifier = Modifier.align(Alignment.TopEnd).padding(horizontal = Space.md, vertical = Space.xs))
            ThinBar(timer.phaseProgress, color, Modifier.align(Alignment.BottomCenter).padding(Space.md))
        }
    }
    Column(Modifier.padding(horizontal = Space.screen), verticalArrangement = Arrangement.spacedBy(Space.xs)) {
        Text(item.title, style = MaterialTheme.typography.titleLarge)
        Text(setLine(item), style = MaterialTheme.typography.titleMedium, color = AppColors.Accent)
        if (phase == null) {
            Text("Do your set at your own pace, then tap Done.", style = MaterialTheme.typography.bodyLarge)
        } else {
            if (!isHold) Text(phase.label, style = MaterialTheme.typography.titleLarge, color = color)
            Text(phase.hint, style = MaterialTheme.typography.bodyMedium, color = AppColors.TextSecondary)
        }
    }
    Row(Modifier.fillMaxWidth().padding(Space.screen), horizontalArrangement = Arrangement.spacedBy(Space.sm)) {
        SecondaryButton(if (snapshot.paused) "Resume" else "Pause", runner::togglePause, Modifier.weight(1f), SessionButtonHeight)
        PrimaryButton(if (isHold) "Stop hold" else "Next", runner::finishSetEarly, Modifier.weight(2f), SessionButtonHeight)
    }
}

// ------------------------------------------------------------------------------------------------ rep sets: the clip is the screen

@Composable
private fun ColumnScope.RepPlayerMode(snapshot: EngineSnapshot, runner: SessionRunner) {
    val item = snapshot.item ?: return
    val timer = snapshot.timer
    val phase = timer?.phase
    val tempo = item.tempo
    val animation = remember(item.exerciseId) { animationFor(item.exerciseId) }
    // Clip time follows the metronome: rests at the top through the 3 s lead-in, then one loop per rep, in step with the beeps.
    val source = when {
        timer == null -> 0L
        tempo != null -> ClipSync.tempoTimeMs(timer.totalElapsedMs, Timers.TEMPO_LEAD_IN_SEC * 1000L, tempo)
        else -> timer.totalElapsedMs
    }
    val synced = rememberSyncedClock(source, running = !snapshot.paused)
    val free = rememberFreeClock()
    val clock = if (timer == null) free else synced
    val color = phase?.let { phaseColor(it.kind) } ?: AppColors.Accent
    val plannedReps = item.repsMax ?: item.repsMin ?: 1

    ClipStage(
        animation, item.exerciseId, clock,
        Modifier.weight(1f).fillMaxWidth(),
        topReserve = 76.dp, bottomReserve = 132.dp,
        framed = false,
    ) {
        Row(Modifier.align(Alignment.TopStart).fillMaxWidth().padding(start = Space.screen, end = Space.xs, top = Space.sm), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(item.title, style = MaterialTheme.typography.titleMedium, maxLines = 2)
                Text(setLine(item) + "  |  ${targetText(item)}", style = MaterialTheme.typography.bodyMedium, color = AppColors.TextSecondary)
            }
            TextButton(onClick = runner::endEarly) { Text("End", color = AppColors.Danger) }
        }
        Column(
            Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(horizontal = Space.screen, vertical = Space.md),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(Space.xs),
        ) {
            Text(
                phase?.rep?.let { "Rep $it of $plannedReps" } ?: if (phase != null) "Get set" else "Your pace",
                style = MaterialTheme.typography.titleMedium,
                color = AppColors.TextSecondary,
            )
            if (timer != null && phase != null) {
                Text(phase.label.uppercase(), fontSize = 34.sp, fontWeight = FontWeight.SemiBold, color = color, textAlign = TextAlign.Center)
                ThinBar(timer.phaseProgress, color)
            }
            tempo?.let { Text("Tempo ${it.notation}", style = MaterialTheme.typography.labelMedium, color = AppColors.TextSecondary) }
        }
    }
    Row(Modifier.fillMaxWidth().padding(horizontal = Space.screen, vertical = Space.md), horizontalArrangement = Arrangement.spacedBy(Space.sm)) {
        SecondaryButton(if (snapshot.paused) "Resume" else "Pause", runner::togglePause, Modifier.weight(1f), SessionButtonHeight)
        PrimaryButton("Next", runner::finishSetEarly, Modifier.weight(2f), SessionButtonHeight)
    }
}

// ------------------------------------------------------------------------------------------------ log the set

@Composable
private fun ColumnScope.LogMode(snapshot: EngineSnapshot, runner: SessionRunner, progression: Map<String, ProgressionState>) {
    val item = snapshot.item ?: return
    // Pre-filled once per set from the engine's suggestion, so logging is a single tap.
    var reps by remember(item.key) { mutableIntStateOf(snapshot.suggestion.reps ?: item.repsMax ?: 0) }
    var hold by remember(item.key) { mutableIntStateOf(snapshot.suggestion.holdSeconds ?: item.holdMaxSec ?: 0) }
    var rir by remember(item.key) { mutableIntStateOf(2) }
    Column(
        Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = Space.screen, vertical = Space.lg),
        verticalArrangement = Arrangement.spacedBy(Space.lg),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(Space.xs)) {
            Text("Log your set", style = MaterialTheme.typography.labelLarge, color = AppColors.Info)
            Text(item.title, style = MaterialTheme.typography.headlineMedium)
            Text(setLine(item), style = MaterialTheme.typography.titleMedium, color = AppColors.Accent)
            progressionLevel(item, progression)?.let {
                Text("Progression level: $it", style = MaterialTheme.typography.bodyMedium, color = AppColors.TextSecondary)
            }
        }
        AppCard {
            if (item.kind == StepKind.ISOMETRIC) {
                Stepper("Hold (seconds)", hold) { hold = it.coerceIn(0, 120) }
            } else {
                Stepper("Reps completed", reps) { reps = it.coerceIn(0, 50) }
                Text("Reps in reserve (RIR)", style = MaterialTheme.typography.bodyMedium, color = AppColors.TextSecondary, modifier = Modifier.padding(top = Space.sm))
                Row(horizontalArrangement = Arrangement.spacedBy(Space.sm)) {
                    listOf(0, 1, 2, 3).forEach { v ->
                        FilterChip(
                            selected = rir == v,
                            onClick = { rir = v },
                            label = { Text(if (v == 3) "3+" else "$v", fontSize = 18.sp, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center) },
                            modifier = Modifier.weight(1f).heightIn(min = 52.dp),
                        )
                    }
                }
            }
        }
    }
    Box(Modifier.padding(Space.screen)) {
        PrimaryButton("Log set and start rest", onClick = {
            if (item.kind == StepKind.ISOMETRIC) runner.logSet(null, hold, null) else runner.logSet(reps, null, rir)
        }, height = SessionButtonHeight)
    }
}

private fun progressionLevel(item: WorkItem, progression: Map<String, ProgressionState>): String? {
    val exercise = SeedData.exerciseOrNull(item.exerciseId) ?: return null
    val index = progression[item.exerciseId]?.levelIndex ?: exercise.startLevel
    return exercise.ladder.getOrNull(index)?.name
}

@Composable
private fun Stepper(label: String, value: Int, onChange: (Int) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Space.md)) {
        Column(Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.bodyMedium, color = AppColors.TextSecondary)
            Text("$value", fontSize = 40.sp, fontWeight = FontWeight.SemiBold)
        }
        StepButton("-") { onChange(value - 1) }
        StepButton("+") { onChange(value + 1) }
    }
}

@Composable
private fun StepButton(text: String, onClick: () -> Unit) {
    OutlinedButton(
        onClick = onClick,
        modifier = Modifier.size(60.dp),
        shape = RoundedCornerShape(Space.corner),
        contentPadding = PaddingValues(0.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, AppColors.Outline),
    ) { Text(text, fontSize = 26.sp, color = AppColors.TextPrimary) }
}

// ------------------------------------------------------------------------------------------------ rest

@Composable
private fun ColumnScope.RestMode(snapshot: EngineSnapshot, runner: SessionRunner) {
    val timer = snapshot.timer ?: return
    Column(
        Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = Space.screen, vertical = Space.lg),
        verticalArrangement = Arrangement.spacedBy(Space.lg),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("Rest", style = MaterialTheme.typography.titleLarge, color = AppColors.Info)
        Text(
            formatSeconds(timer.phaseRemainingSeconds),
            fontSize = 84.sp,
            fontWeight = FontWeight.SemiBold,
            color = AppColors.Info,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
        ThinBar(1f - timer.phaseProgress, AppColors.Info)
        snapshot.item?.let {
            AppCard {
                Text("Next", style = MaterialTheme.typography.labelLarge, color = AppColors.TextSecondary)
                Row(horizontalArrangement = Arrangement.spacedBy(Space.md), verticalAlignment = Alignment.CenterVertically) {
                    ClipStill(it.exerciseId, Modifier.size(72.dp))
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(it.title, style = MaterialTheme.typography.titleMedium)
                        Text(setLine(it) + "  |  " + targetText(it), style = MaterialTheme.typography.bodyMedium, color = AppColors.Accent)
                    }
                }
                if (it.cue.isNotBlank()) Text(it.cue, style = MaterialTheme.typography.bodyMedium, color = AppColors.TextSecondary)
            }
        }
        Text(
            "Rest chosen from your RIR: ${snapshot.restSeconds} s in total. Breathe slowly and shake out the arms.",
            style = MaterialTheme.typography.bodyMedium,
            color = AppColors.TextSecondary,
        )
    }
    Row(Modifier.fillMaxWidth().padding(Space.screen), horizontalArrangement = Arrangement.spacedBy(Space.sm)) {
        SecondaryButton(if (snapshot.paused) "Resume" else "Pause", runner::togglePause, Modifier.weight(1f), SessionButtonHeight)
        SecondaryButton("+30 s", runner::extendRest, Modifier.weight(1f), SessionButtonHeight)
        PrimaryButton("Skip rest", runner::skipRest, Modifier.weight(1.4f), SessionButtonHeight)
    }
}

// ------------------------------------------------------------------------------------------------ finished

// Joint ratings are hoisted into a tiny holder so the buttons can read them.
private object JointDraft {
    var wrists by mutableIntStateOf(1)
    var elbows by mutableIntStateOf(1)
    var shoulders by mutableIntStateOf(1)
    fun reset() { wrists = 1; elbows = 1; shoulders = 1 }
}

@Composable
private fun ColumnScope.FinishedMode(runner: SessionRunner) {
    Column(
        Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = Space.screen, vertical = Space.lg),
        verticalArrangement = Arrangement.spacedBy(Space.md),
    ) {
        Text("Workout complete", style = MaterialTheme.typography.headlineMedium, color = AppColors.Good)
        Text(
            "Rate how your joints feel (1 = fine, 5 = very stiff or sore). This guides your next session.",
            style = MaterialTheme.typography.bodyLarge,
            color = AppColors.TextSecondary,
        )
        JointSlider("Wrists", JointDraft.wrists) { JointDraft.wrists = it }
        JointSlider("Elbows (inner elbow stiffness above 2 switches the next pull-ups to neutral grip)", JointDraft.elbows) { JointDraft.elbows = it }
        JointSlider("Shoulders", JointDraft.shoulders) { JointDraft.shoulders = it }
    }
    Column(Modifier.fillMaxWidth().padding(Space.screen), verticalArrangement = Arrangement.spacedBy(Space.sm)) {
        PrimaryButton("Save and finish", onClick = {
            runner.finish(JointRating(JointDraft.wrists, JointDraft.elbows, JointDraft.shoulders))
            JointDraft.reset()
        }, height = SessionButtonHeight)
        SecondaryButton("Finish without rating", onClick = {
            runner.finish(null)
            JointDraft.reset()
        }, Modifier.fillMaxWidth(), SessionButtonHeight)
    }
}

@Composable
private fun JointSlider(label: String, value: Int, onChange: (Int) -> Unit) {
    var drag by remember(value) { mutableFloatStateOf(value.toFloat()) }
    AppCard {
        Text("$label: $value / 5", style = MaterialTheme.typography.titleMedium)
        Slider(
            value = drag,
            onValueChange = { drag = it; onChange(it.roundToInt().coerceIn(1, 5)) },
            valueRange = 1f..5f,
            steps = 3,
            modifier = Modifier.heightIn(min = Space.tap),
        )
    }
}

// ------------------------------------------------------------------------------------------------ helpers

private fun phaseColor(kind: PhaseKind): Color = when (kind) {
    PhaseKind.GET_READY, PhaseKind.TRANSITION -> AppColors.Warn
    PhaseKind.HOLD -> AppColors.Good
    PhaseKind.LOWER -> AppColors.Info
    PhaseKind.PAUSE_BOTTOM, PhaseKind.PAUSE_TOP -> AppColors.Joint
    PhaseKind.DRIVE -> AppColors.Accent
    PhaseKind.REST -> AppColors.Info
    PhaseKind.DRILL -> AppColors.Good
}

private fun formatSeconds(seconds: Int): String {
    val s = seconds.coerceAtLeast(0)
    return if (s >= 60) "%d:%02d".format(s / 60, s % 60) else "$s"
}
