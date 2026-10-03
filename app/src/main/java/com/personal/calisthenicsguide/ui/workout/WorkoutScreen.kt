package com.personal.calisthenicsguide.ui.workout

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.personal.calisthenics.core.analytics.JointRating
import com.personal.calisthenics.core.analytics.ProgressionState
import com.personal.calisthenics.core.model.StepKind
import com.personal.calisthenics.core.plan.WorkItem
import com.personal.calisthenics.core.seed.SeedData
import com.personal.calisthenics.core.session.EngineSnapshot
import com.personal.calisthenics.core.session.SessionMode
import com.personal.calisthenics.core.timer.PhaseKind
import com.personal.calisthenicsguide.session.RunnerState
import com.personal.calisthenicsguide.session.SessionRunner
import com.personal.calisthenicsguide.ui.theme.AppColors
import kotlin.math.roundToInt

private val BigButtonHeight = 72.dp

@Composable
fun WorkoutScreen(
    runnerState: RunnerState,
    runner: SessionRunner,
    progression: Map<String, ProgressionState>,
    coldModeSetting: Boolean,
    onColdMode: (Boolean) -> Unit,
    onStart: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val snapshot = runnerState.snapshot
    if (!runnerState.active || snapshot == null) {
        IdleWorkout(coldModeSetting, onColdMode, onStart, modifier)
        return
    }

    val coldMode = runnerState.plan?.options?.coldMode ?: coldModeSetting
    Column(modifier.fillMaxSize()) {
        Header(snapshot, coldMode, onColdMode = {}, locked = true, onEnd = runner::endEarly)
        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            when (snapshot.mode) {
                SessionMode.FLOW -> FlowContent(snapshot)
                SessionMode.READY -> ReadyContent(snapshot)
                SessionMode.ACTIVE -> ActiveContent(snapshot)
                SessionMode.AWAITING_LOG -> LogContent(snapshot, progression)
                SessionMode.RESTING -> RestContent(snapshot)
                SessionMode.FINISHED -> FinishedContent()
                SessionMode.NOT_STARTED -> Text("Starting...")
            }
        }
        BottomActions(snapshot, runner, progression)
    }
}

// ------------------------------------------------------------------------------------------------ idle

@Composable
private fun IdleWorkout(coldMode: Boolean, onColdMode: (Boolean) -> Unit, onStart: () -> Unit, modifier: Modifier) {
    Column(modifier.fillMaxSize()) {
        ColdModeRow(coldMode, onColdMode, enabled = true, modifier = Modifier.padding(16.dp))
        Column(
            Modifier.weight(1f).padding(horizontal = 24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text("No active workout", style = MaterialTheme.typography.headlineMedium)
            Spacer(Modifier.height(8.dp))
            Text(
                "The session runs warm-up, isometrics, strength and decompression with automatic timers. " +
                    "Day, deload and grip are chosen on the Home tab.",
                textAlign = TextAlign.Center,
                color = AppColors.TextSecondary,
            )
        }
        PrimaryButton("Start today's workout", onStart, Modifier.padding(16.dp))
    }
}

@Composable
private fun ColdModeRow(checked: Boolean, onChange: (Boolean) -> Unit, enabled: Boolean, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text("Cold Weather / Antagonist Supersets", style = MaterialTheme.typography.titleMedium)
            Text(
                if (enabled) "Pairs 3+4 and 5+6, 90 s rest between paired sets" else "Chosen when the session started",
                style = MaterialTheme.typography.bodyMedium,
                color = AppColors.TextSecondary,
            )
        }
        Switch(checked = checked, onCheckedChange = onChange, enabled = enabled)
    }
}

// ------------------------------------------------------------------------------------------------ header

@Composable
private fun Header(
    snapshot: EngineSnapshot,
    coldMode: Boolean,
    onColdMode: (Boolean) -> Unit,
    locked: Boolean,
    onEnd: () -> Unit,
) {
    Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        ColdModeRow(coldMode, onColdMode, enabled = !locked)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(snapshot.phase?.let { "Phase ${it.index}: ${it.title}" } ?: "Workout", style = MaterialTheme.typography.titleMedium, color = AppColors.Accent)
                if (snapshot.setsTotal > 0) {
                    Text("${snapshot.setsLogged}/${snapshot.setsTotal} sets logged", style = MaterialTheme.typography.bodyMedium, color = AppColors.TextSecondary)
                }
            }
            if (snapshot.mode != SessionMode.FINISHED) TextButton(onClick = onEnd) { Text("End", color = AppColors.Danger) }
        }
        if (snapshot.setsTotal > 0) {
            LinearProgressIndicator(
                progress = { snapshot.setsLogged.toFloat() / snapshot.setsTotal },
                modifier = Modifier.fillMaxWidth().height(8.dp),
                color = AppColors.Accent,
                trackColor = AppColors.SurfaceHigh,
            )
        }
    }
}

// ------------------------------------------------------------------------------------------------ content

@Composable
private fun BigCountdown(seconds: Int, color: Color = AppColors.TextPrimary) {
    Text(
        formatSeconds(seconds),
        modifier = Modifier.fillMaxWidth(),
        textAlign = TextAlign.Center,
        fontSize = 96.sp,
        fontWeight = FontWeight.Bold,
        color = color,
    )
}

@Composable
private fun PacingBar(progress: Float, color: Color) {
    Canvas(Modifier.fillMaxWidth().height(34.dp)) {
        val radius = CornerRadius(size.height / 2, size.height / 2)
        drawRoundRect(AppColors.SurfaceHigh, size = size, cornerRadius = radius)
        val w = size.width * progress.coerceIn(0f, 1f)
        if (w > 0f) drawRoundRect(color, size = Size(w, size.height), cornerRadius = radius)
    }
}

@Composable
private fun InfoCard(content: @Composable () -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = AppColors.Surface),
        shape = RoundedCornerShape(20.dp),
        modifier = Modifier.fillMaxWidth(),
    ) { Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) { content() } }
}

@Composable
private fun FlowContent(snapshot: EngineSnapshot) {
    val timer = snapshot.timer ?: return
    val phase = timer.phase ?: return
    val transition = phase.kind == PhaseKind.TRANSITION
    val drill = snapshot.flowItem
    Text(
        if (transition) "Get ready" else "Now",
        color = if (transition) AppColors.Warn else AppColors.Good,
        style = MaterialTheme.typography.titleMedium,
    )
    Text(drill?.label ?: phase.label, style = MaterialTheme.typography.headlineMedium)
    BigCountdown(timer.phaseRemainingSeconds, if (transition) AppColors.Warn else AppColors.TextPrimary)
    PacingBar(timer.phaseProgress, if (transition) AppColors.Warn else AppColors.Good)
    val subIndex = timer.subLabelIndex
    if (!transition && phase.subLabels.isNotEmpty() && subIndex != null) {
        Text(phase.subLabels[subIndex], style = MaterialTheme.typography.titleLarge, color = AppColors.Cyan)
    }
    drill?.let {
        InfoCard {
            Text(it.instruction, style = MaterialTheme.typography.bodyLarge)
        }
    }
    Text(
        "Flow progress ${(timer.totalProgress * 100).roundToInt()}% - the next drill starts automatically",
        color = AppColors.TextSecondary,
        style = MaterialTheme.typography.bodyMedium,
    )
}

private fun targetText(item: WorkItem): String = when (item.kind) {
    StepKind.ISOMETRIC -> "Hold ${item.holdMinSec}-${item.holdMaxSec} s"
    else -> {
        val reps = if (item.repsMin == item.repsMax) "${item.repsMax}" else "${item.repsMin}-${item.repsMax}"
        "$reps reps" + (item.side?.let { " (${it.label})" } ?: "")
    }
}

@Composable
private fun ReadyContent(snapshot: EngineSnapshot) {
    val item = snapshot.item ?: return
    Text("Up next", color = AppColors.Cyan, style = MaterialTheme.typography.titleMedium)
    Text(item.title, style = MaterialTheme.typography.headlineMedium)
    Text(
        "Set ${item.setNumber} of ${item.totalSets}" + (item.side?.let { " - ${it.label}" } ?: ""),
        style = MaterialTheme.typography.titleLarge,
        color = AppColors.Accent,
    )
    InfoCard {
        Text(targetText(item), style = MaterialTheme.typography.titleLarge)
        item.tempo?.let { Text("Tempo ${it.notation} (lower - pause - drive - squeeze)", style = MaterialTheme.typography.bodyLarge) }
        item.supersetWith?.let { Text("Superset with: $it", color = AppColors.Warn, style = MaterialTheme.typography.bodyLarge) }
        if (item.cue.isNotBlank()) Text(item.cue, style = MaterialTheme.typography.bodyMedium, color = AppColors.TextSecondary)
    }
    if (item.kind == StepKind.ISOMETRIC) {
        Text("Tapping Start gives you a 5 s countdown to get into position and lock the scapula.", color = AppColors.TextSecondary)
    }
}

@Composable
private fun ActiveContent(snapshot: EngineSnapshot) {
    val item = snapshot.item ?: return
    val timer = snapshot.timer
    Text(item.title, style = MaterialTheme.typography.titleLarge)
    val phase = timer?.phase
    if (timer == null || phase == null) {
        Text("Do your set at your own pace, then tap Done.", style = MaterialTheme.typography.bodyLarge)
        return
    }
    val color = phaseColor(phase.kind)
    if (item.kind == StepKind.ISOMETRIC) {
        Text(
            if (phase.kind == PhaseKind.GET_READY) "GET READY" else "HOLD",
            style = MaterialTheme.typography.headlineMedium,
            color = color,
        )
        BigCountdown(timer.phaseRemainingSeconds, color)
        PacingBar(timer.phaseProgress, color)
        Text(phase.hint, style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
    } else {
        val totalReps = (item.repsMax ?: item.repsMin ?: 1)
        Text(
            phase.rep?.let { "Rep $it of $totalReps" } ?: "Get set",
            style = MaterialTheme.typography.titleLarge,
            color = AppColors.TextSecondary,
        )
        Text(phase.label.uppercase(), style = MaterialTheme.typography.headlineMedium, color = color, fontSize = 44.sp)
        BigCountdown(timer.phaseRemainingSeconds, color)
        PacingBar(timer.phaseProgress, color)
        Text(phase.hint, style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
        item.tempo?.let { Text("Tempo ${it.notation}", color = AppColors.TextSecondary, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center) }
    }
}

@Composable
private fun LogContent(snapshot: EngineSnapshot, progression: Map<String, ProgressionState>) {
    val item = snapshot.item ?: return
    Text("Log your set", color = AppColors.Cyan, style = MaterialTheme.typography.titleMedium)
    Text(item.title, style = MaterialTheme.typography.headlineMedium)
    Text(
        "Set ${item.setNumber} of ${item.totalSets}" + (item.side?.let { " - ${it.label}" } ?: ""),
        color = AppColors.Accent,
        style = MaterialTheme.typography.titleLarge,
    )
    progressionLevel(item, progression)?.let {
        Text("Progression level: $it", color = AppColors.TextSecondary, style = MaterialTheme.typography.bodyLarge)
    }
}

private fun progressionLevel(item: WorkItem, progression: Map<String, ProgressionState>): String? {
    val exercise = SeedData.exerciseOrNull(item.exerciseId) ?: return null
    val index = progression[item.exerciseId]?.levelIndex ?: exercise.startLevel
    return exercise.ladder.getOrNull(index)?.name
}

@Composable
private fun RestContent(snapshot: EngineSnapshot) {
    val timer = snapshot.timer ?: return
    Text("Rest", style = MaterialTheme.typography.headlineMedium, color = AppColors.Cyan)
    BigCountdown(timer.phaseRemainingSeconds, AppColors.Cyan)
    PacingBar(1f - timer.phaseProgress, AppColors.Cyan)
    snapshot.item?.let {
        InfoCard {
            Text("Next: ${it.title}", style = MaterialTheme.typography.titleMedium)
            Text(
                "Set ${it.setNumber} of ${it.totalSets}" + (it.side?.let { s -> " - ${s.label}" } ?: "") + " - " + targetText(it),
                style = MaterialTheme.typography.bodyLarge,
            )
            if (it.cue.isNotBlank()) Text(it.cue, color = AppColors.TextSecondary, style = MaterialTheme.typography.bodyMedium)
        }
    }
    Text(
        "Rest chosen from your RIR: ${snapshot.restSeconds} s total. Breathe slowly and shake out the arms.",
        color = AppColors.TextSecondary,
        style = MaterialTheme.typography.bodyMedium,
    )
}

@Composable
private fun FinishedContent() {
    Text("Workout complete", style = MaterialTheme.typography.headlineMedium, color = AppColors.Good)
    Text("Rate how your joints feel (1 = fine, 5 = very stiff or sore). This guides your next session.", style = MaterialTheme.typography.bodyLarge)
    JointLog()
}

// Joint ratings are hoisted into a tiny holder so the bottom button can read them.
private object JointDraft {
    var wrists by mutableIntStateOf(1)
    var elbows by mutableIntStateOf(1)
    var shoulders by mutableIntStateOf(1)
    fun reset() { wrists = 1; elbows = 1; shoulders = 1 }
}

@Composable
private fun JointLog() {
    JointSlider("Wrists", JointDraft.wrists) { JointDraft.wrists = it }
    JointSlider("Elbows (inner elbow stiffness above 2 switches the next pull-ups to neutral grip)", JointDraft.elbows) { JointDraft.elbows = it }
    JointSlider("Shoulders", JointDraft.shoulders) { JointDraft.shoulders = it }
}

@Composable
private fun JointSlider(label: String, value: Int, onChange: (Int) -> Unit) {
    var drag by remember(value) { mutableFloatStateOf(value.toFloat()) }
    InfoCard {
        Text("$label: $value / 5", style = MaterialTheme.typography.titleMedium)
        Slider(
            value = drag,
            onValueChange = { drag = it; onChange(it.roundToInt().coerceIn(1, 5)) },
            valueRange = 1f..5f,
            steps = 3,
            modifier = Modifier.heightIn(min = 48.dp),
        )
    }
}

// ------------------------------------------------------------------------------------------------ bottom actions

@Composable
private fun BottomActions(snapshot: EngineSnapshot, runner: SessionRunner, progression: Map<String, ProgressionState>) {
    Column(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        when (snapshot.mode) {
            SessionMode.FLOW -> {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    SecondaryButton("Back", runner::skipFlowBack, Modifier.weight(1f))
                    SecondaryButton(if (snapshot.paused) "Resume" else "Pause", runner::togglePause, Modifier.weight(1f))
                    SecondaryButton("Skip", runner::skipFlowForward, Modifier.weight(1f))
                }
            }
            SessionMode.READY -> {
                val isometric = snapshot.item?.kind == StepKind.ISOMETRIC
                PrimaryButton(if (isometric) "Start (5 s get-ready)" else "Start set", runner::startSet)
            }
            SessionMode.ACTIVE -> {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    SecondaryButton(if (snapshot.paused) "Resume" else "Pause", runner::togglePause, Modifier.weight(1f))
                    PrimaryButton(
                        if (snapshot.item?.kind == StepKind.ISOMETRIC) "Stop hold" else "Done / failed",
                        runner::finishSetEarly,
                        Modifier.weight(2f),
                    )
                }
            }
            SessionMode.AWAITING_LOG -> LogControls(snapshot, runner)
            SessionMode.RESTING -> {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    SecondaryButton(if (snapshot.paused) "Resume" else "Pause", runner::togglePause, Modifier.weight(1f))
                    SecondaryButton("+30 s", runner::extendRest, Modifier.weight(1f))
                    PrimaryButton("Skip rest", runner::skipRest, Modifier.weight(1.4f))
                }
            }
            SessionMode.FINISHED -> {
                PrimaryButton("Save & finish", onClick = {
                    runner.finish(JointRating(JointDraft.wrists, JointDraft.elbows, JointDraft.shoulders))
                    JointDraft.reset()
                })
                SecondaryButton("Finish without rating", onClick = {
                    runner.finish(null)
                    JointDraft.reset()
                })
            }
            SessionMode.NOT_STARTED -> Unit
        }
    }
}

@Composable
private fun LogControls(snapshot: EngineSnapshot, runner: SessionRunner) {
    val item = snapshot.item ?: return
    // Pre-filled once per set from the engine's suggestion.
    var reps by remember(item.key) { mutableIntStateOf(snapshot.suggestion.reps ?: item.repsMax ?: 0) }
    var hold by remember(item.key) { mutableIntStateOf(snapshot.suggestion.holdSeconds ?: item.holdMaxSec ?: 0) }
    var rir by remember(item.key) { mutableIntStateOf(2) }
    if (item.kind == StepKind.ISOMETRIC) {
        Stepper("Hold (seconds)", hold) { hold = it.coerceIn(0, 120) }
    } else {
        Stepper("Reps completed", reps) { reps = it.coerceIn(0, 50) }
        Text("Reps in reserve (RIR)", style = MaterialTheme.typography.bodyMedium, color = AppColors.TextSecondary)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(0, 1, 2, 3).forEach { v ->
                FilterChip(
                    selected = rir == v,
                    onClick = { rir = v },
                    label = { Text(if (v == 3) "3+" else "$v", fontSize = 20.sp) },
                    modifier = Modifier.weight(1f).heightIn(min = 52.dp),
                )
            }
        }
    }
    PrimaryButton("Log set & start rest", onClick = {
        if (item.kind == StepKind.ISOMETRIC) runner.logSet(null, hold, null) else runner.logSet(reps, null, rir)
    })
}

@Composable
private fun Stepper(label: String, value: Int, onChange: (Int) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Column(Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.bodyMedium, color = AppColors.TextSecondary)
            Text("$value", fontSize = 40.sp, fontWeight = FontWeight.Bold)
        }
        OutlinedButton(onClick = { onChange(value - 1) }, modifier = Modifier.size(64.dp), shape = RoundedCornerShape(16.dp), contentPadding = PaddingValues(0.dp)) {
            Text("-", fontSize = 28.sp)
        }
        OutlinedButton(onClick = { onChange(value + 1) }, modifier = Modifier.size(64.dp), shape = RoundedCornerShape(16.dp), contentPadding = PaddingValues(0.dp)) {
            Text("+", fontSize = 28.sp)
        }
    }
}

@Composable
private fun PrimaryButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Button(
        onClick = onClick,
        modifier = modifier.fillMaxWidth().heightIn(min = BigButtonHeight),
        shape = RoundedCornerShape(18.dp),
        colors = ButtonDefaults.buttonColors(containerColor = AppColors.Accent, contentColor = AppColors.AccentOn),
    ) { Text(text, style = MaterialTheme.typography.titleLarge) }
}

@Composable
private fun SecondaryButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    OutlinedButton(
        onClick = onClick,
        modifier = modifier.heightIn(min = BigButtonHeight),
        shape = RoundedCornerShape(18.dp),
    ) { Text(text, style = MaterialTheme.typography.titleMedium) }
}

private fun phaseColor(kind: PhaseKind): Color = when (kind) {
    PhaseKind.GET_READY, PhaseKind.TRANSITION -> AppColors.Warn
    PhaseKind.HOLD -> AppColors.Good
    PhaseKind.LOWER -> AppColors.Cyan
    PhaseKind.PAUSE_BOTTOM, PhaseKind.PAUSE_TOP -> AppColors.Joint
    PhaseKind.DRIVE -> AppColors.Accent
    PhaseKind.REST -> AppColors.Cyan
    PhaseKind.DRILL -> AppColors.Good
}

private fun formatSeconds(seconds: Int): String {
    val s = seconds.coerceAtLeast(0)
    return if (s >= 60) "%d:%02d".format(s / 60, s % 60) else "$s"
}
