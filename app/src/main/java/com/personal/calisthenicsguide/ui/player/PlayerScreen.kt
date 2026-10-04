package com.personal.calisthenicsguide.ui.player

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.personal.calisthenics.core.model.Phase
import com.personal.calisthenics.core.model.StepKind
import com.personal.calisthenics.core.model.WorkoutDay
import com.personal.calisthenics.core.session.StageKind
import com.personal.calisthenicsguide.session.SessionController
import com.personal.calisthenicsguide.session.SessionUi
import com.personal.calisthenicsguide.ui.BigButton
import com.personal.calisthenicsguide.ui.Muted
import com.personal.calisthenicsguide.ui.Palette
import com.personal.calisthenicsguide.ui.Panel
import com.personal.calisthenicsguide.ui.SectionTitle
import com.personal.calisthenicsguide.ui.appViewModel

@Composable
fun PlayerScreen(modifier: Modifier = Modifier) {
    val vm = appViewModel { PlayerViewModel(it.repository, it.sessionController) }
    val ui by vm.controller.ui.collectAsStateWithLifecycle()
    val idle by vm.idle.collectAsStateWithLifecycle()
    val cold by vm.coldMode.collectAsStateWithLifecycle()
    val selected by vm.selectedDay.collectAsStateWithLifecycle()

    // Keep the screen awake for the whole workout (the foreground service covers the screen-off case).
    val view = LocalView.current
    DisposableEffect(ui.active) {
        view.keepScreenOn = ui.active
        onDispose { view.keepScreenOn = false }
    }

    Column(modifier = modifier.fillMaxSize()) {
        val snap = ui.snapshot
        val coldChecked = if (ui.active && snap != null) snap.coldMode else cold
        val coldEnabled = if (ui.active && snap != null) snap.coldModeChangeable else ui.summary == null
        ColdModeBar(
            checked = coldChecked,
            enabled = coldEnabled,
            onChange = { on -> if (ui.active) vm.controller.setColdMode(on) else vm.setCold(on) },
        )
        when {
            ui.summary != null -> SummaryView(ui, onDone = vm.controller::dismissSummary)
            ui.active && snap != null -> ActiveView(ui, vm.controller)
            else -> IdleView(idle, selected, onSelect = vm::selectDay, onStart = vm::start)
        }
    }
}

@Composable
private fun ColdModeBar(checked: Boolean, enabled: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surface).padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text("Cold Weather / Antagonist Supersets", style = MaterialTheme.typography.titleMedium)
            Muted(if (checked) "Pairs exercises 3+4 and 5+6, rest capped at 90 s" else "Off: normal rests and exercise order")
        }
        Switch(checked = checked, onCheckedChange = onChange, enabled = enabled)
    }
}

// ---------------------------------------------------------------------- idle

@Composable
private fun IdleView(idle: IdleUi?, selected: WorkoutDay?, onSelect: (WorkoutDay?) -> Unit, onStart: () -> Unit) {
    if (idle == null) {
        Muted("Loading...", modifier = Modifier.padding(16.dp))
        return
    }
    val plan = idle.plan
    Column(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text("Today's workout", style = MaterialTheme.typography.headlineMedium, color = Palette.Amber)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                for (day in WorkoutDay.entries) {
                    val isAuto = day == idle.autoDay
                    FilterChip(
                        selected = idle.options.day == day,
                        onClick = { onSelect(if (isAuto) null else day) },
                        label = { Text("Day ${day.label}${if (isAuto) " (next)" else ""}", modifier = Modifier.padding(vertical = 10.dp)) },
                    )
                }
            }
            Panel {
                SectionTitle("Day ${idle.options.day.label}: ${idle.options.day.focus}")
                Muted("Pull-ups use the ${idle.options.pullGrip.displayName} grip. About ${plan.estimatedMinutes().first}-${plan.estimatedMinutes().last} minutes${if (idle.options.deload) ", deload week (sets halved)" else ""}.")
            }
            for (phase in Phase.entries) {
                PhasePreview(plan, phase)
            }
        }
        Column(modifier = Modifier.padding(16.dp)) {
            BigButton("Start session", onClick = onStart, modifier = Modifier.fillMaxWidth())
        }
    }
}

@Composable
private fun PhasePreview(plan: com.personal.calisthenics.core.plan.SessionPlan, phase: Phase) {
    val steps = plan.steps.filter { it.phase == phase }
    Panel {
        Text("PHASE ${phase.index}  ${phase.minutesLabel}", style = MaterialTheme.typography.labelMedium, color = Palette.Cyan)
        SectionTitle(phase.title)
        when (phase) {
            Phase.WARMUP, Phase.DECOMPRESSION -> for (step in steps) for (item in step.flowItems) {
                Text("${item.label}  -  ${item.seconds} s", style = MaterialTheme.typography.bodyLarge)
            }
            else -> for (step in steps) {
                val target = when (step.kind) {
                    StepKind.ISOMETRIC -> "${step.sets} x ${step.holdMinSec}-${step.holdMaxSec} s"
                    else -> "${step.sets} x ${step.repsMin}-${step.repsMax}${if (step.perSide) " per leg" else ""}"
                }
                Text("${step.number}. ${step.title}", style = MaterialTheme.typography.bodyLarge)
                Muted("$target${step.tempo?.let { "  |  tempo ${it.notation}" } ?: ""}  |  rest ${if (step.restMinSec == step.restMaxSec) "${step.restMaxSec}" else "${step.restMinSec}-${step.restMaxSec}"} s")
            }
        }
    }
}

// ---------------------------------------------------------------------- summary

@Composable
private fun SummaryView(ui: SessionUi, onDone: () -> Unit) {
    val s = ui.summary ?: return
    Column(modifier = Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Column(modifier = Modifier.weight(1f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text("Session complete", style = MaterialTheme.typography.headlineMedium, color = Palette.Green)
            Panel {
                SectionTitle("Day ${s.day.label}${if (s.deload) " (deload)" else ""}")
                Text("${s.sets} sets logged in about ${s.minutes} min", style = MaterialTheme.typography.bodyLarge)
                Muted("Your tendon recovery clock has started: the best window to train again is 48 to 72 hours from now.")
            }
            for (message in s.messages) {
                Panel(color = MaterialTheme.colorScheme.primaryContainer) { Text(message, style = MaterialTheme.typography.bodyLarge) }
            }
        }
        BigButton("Done", onClick = onDone, modifier = Modifier.fillMaxWidth())
    }
}

// ---------------------------------------------------------------------- active

@Composable
private fun ActiveView(ui: SessionUi, controller: SessionController) {
    val snap = ui.snapshot ?: return
    var confirmEnd by remember { mutableStateOf(false) }
    Column(modifier = Modifier.fillMaxSize()) {
        ProgressHeader(snap.phase, snap.completedSets, snap.totalSets, onEnd = { confirmEnd = true })
        when (snap.stage) {
            StageKind.FLOW_READY -> FlowReadyStage(snap, controller)
            StageKind.FLOW -> FlowStage(snap, controller)
            StageKind.READY -> ReadyStage(snap, controller)
            StageKind.ACTIVE -> ActiveStage(snap, controller)
            StageKind.LOGGING -> LoggingStage(snap, ui.defaultLevel, controller)
            StageKind.REST -> RestStage(snap, controller)
            StageKind.JOINT_LOG -> JointLogStage(controller)
            else -> Muted("...", modifier = Modifier.padding(16.dp))
        }
    }
    if (confirmEnd) {
        AlertDialog(
            onDismissRequest = { confirmEnd = false },
            title = { Text("End the session?") },
            text = { Text("Sets you already logged are kept. You will rate your joints next.") },
            confirmButton = { TextButton(onClick = { confirmEnd = false; controller.endEarly() }) { Text("End session") } },
            dismissButton = { TextButton(onClick = { confirmEnd = false }) { Text("Keep going") } },
        )
    }
}

@Composable
private fun ProgressHeader(phase: Phase?, done: Int, total: Int, onEnd: () -> Unit) {
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                if (phase != null) {
                    Text("PHASE ${phase.index}", style = MaterialTheme.typography.labelMedium, color = Palette.Cyan)
                    Text(phase.title, style = MaterialTheme.typography.titleMedium)
                } else {
                    Text("Finishing up", style = MaterialTheme.typography.titleMedium)
                }
            }
            TextButton(onClick = onEnd) { Text("End", color = Palette.Red, style = MaterialTheme.typography.labelLarge) }
        }
        androidx.compose.material3.LinearProgressIndicator(
            progress = { if (total == 0) 0f else done.toFloat() / total },
            modifier = Modifier.fillMaxWidth().height(8.dp),
            color = Palette.Amber,
            trackColor = Palette.SurfaceHigh,
        )
        Muted("$done of $total sets logged")
    }
}
