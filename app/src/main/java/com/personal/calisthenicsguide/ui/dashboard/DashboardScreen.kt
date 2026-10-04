package com.personal.calisthenicsguide.ui.dashboard

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.personal.calisthenics.core.analytics.Recovery
import com.personal.calisthenics.core.analytics.RecoveryState
import com.personal.calisthenics.core.dashboard.DashboardModel
import com.personal.calisthenics.core.dashboard.PreWorkoutChecklist
import com.personal.calisthenics.core.plan.Deload
import com.personal.calisthenicsguide.ui.BigButton
import com.personal.calisthenicsguide.ui.Muted
import com.personal.calisthenicsguide.ui.Palette
import com.personal.calisthenicsguide.ui.Panel
import com.personal.calisthenicsguide.ui.Pill
import com.personal.calisthenicsguide.ui.SectionTitle
import com.personal.calisthenicsguide.ui.appViewModel

@Composable
fun DashboardScreen(modifier: Modifier = Modifier, onStartWorkout: () -> Unit) {
    val vm = appViewModel { DashboardViewModel(it.repository) }
    val model by vm.model.collectAsStateWithLifecycle()
    val checklist by vm.checklist.collectAsStateWithLifecycle()
    val m = model
    Column(
        modifier = modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text("Tendon Guide", style = MaterialTheme.typography.headlineMedium, color = Palette.Amber)
        if (m == null) {
            Muted("Loading...")
            return@Column
        }
        RecoveryCard(m)
        NextSessionCard(m, onStartWorkout)
        DeloadCard(m, onRestart = vm::restartBlock)
        ChecklistCard(checklist, vm::toggle)
    }
}

@Composable
private fun RecoveryCard(m: DashboardModel) {
    val r = m.recovery
    val accent = when (r.state) {
        RecoveryState.OPTIMAL_WINDOW -> Palette.Green
        RecoveryState.TOO_SOON -> Palette.Cyan
        RecoveryState.OVERDUE -> Palette.Amber
        RecoveryState.NO_HISTORY -> Palette.Muted
    }
    Panel {
        SectionTitle("Tendon recovery")
        Text(
            if (r.state == RecoveryState.NO_HISTORY) "--" else Recovery.formatElapsed(r.elapsedMs),
            style = MaterialTheme.typography.displayMedium,
            color = accent,
        )
        Text(r.headline, style = MaterialTheme.typography.titleMedium, color = accent)
        RecoveryBar(progress = r.progress, accent = accent)
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Muted("0 h")
            Muted("48 h")
            Muted("72 h")
        }
        Muted(r.detail)
    }
}

/** A 0-72 h bar with the optimal 48-72 h collagen-synthesis window shaded. */
@Composable
private fun RecoveryBar(progress: Float, accent: Color) {
    Canvas(modifier = Modifier.fillMaxWidth().height(28.dp)) {
        val h = size.height
        val corner = CornerRadius(h / 2f, h / 2f)
        drawRoundRect(Palette.SurfaceHigh, size = Size(size.width, h), cornerRadius = corner)
        val windowStart = size.width * (48f / 72f)
        drawRoundRect(Palette.Green.copy(alpha = 0.35f), topLeft = Offset(windowStart, 0f), size = Size(size.width - windowStart, h), cornerRadius = corner)
        val fill = size.width * progress.coerceIn(0f, 1f)
        if (fill > 0f) drawRoundRect(accent, size = Size(fill, h), cornerRadius = corner)
        drawRect(Color.White, topLeft = Offset(windowStart - 1.5f, 0f), size = Size(3f, h))
    }
}

@Composable
private fun NextSessionCard(m: DashboardModel, onStart: () -> Unit) {
    Panel(color = MaterialTheme.colorScheme.primaryContainer) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Box(
                modifier = Modifier.heightIn(min = 84.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(m.nextDay.label, style = MaterialTheme.typography.displayLarge, color = Palette.Amber)
            }
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Next session: ${m.nextDay.title}", style = MaterialTheme.typography.titleLarge)
                Text(m.nextDay.focus, style = MaterialTheme.typography.bodyMedium, color = Palette.Muted)
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Pill("Pull: ${m.grip.short}", Palette.Amber)
            Pill("~${m.estimatedMinutes.first}-${m.estimatedMinutes.last} min", Palette.Cyan)
            if (m.deloadWeek) Pill("DELOAD", Palette.Green)
        }
        if (m.deloadWeek) Muted("Deload mode: total sets in Phases 1 and 2 are halved so your connective tissue can catch up.")
        if (m.gripOverriddenByElbow) Muted("Neutral grip chosen because of inner-elbow stiffness in your last log.")
        for (message in m.advice.messages) Text(message, style = MaterialTheme.typography.bodyMedium, color = Palette.Amber)
        BigButton("Go to workout", onClick = onStart, modifier = Modifier.fillMaxWidth())
    }
}

@Composable
private fun DeloadCard(m: DashboardModel, onRestart: () -> Unit) {
    Panel {
        SectionTitle(if (m.deloadWeek) "Week ${m.weekInBlock} of ${Deload.BLOCK_LENGTH_WEEKS}: Deload" else "Week ${m.weekInBlock} of ${Deload.BLOCK_LENGTH_WEEKS}")
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
            for (week in 1..Deload.BLOCK_LENGTH_WEEKS) {
                val isDeload = week == Deload.BLOCK_LENGTH_WEEKS
                val color = when {
                    week < m.weekInBlock -> if (isDeload) Palette.Green else Palette.Amber
                    week == m.weekInBlock -> if (isDeload) Palette.Green else Palette.Cyan
                    else -> Palette.SurfaceHigh
                }
                Box(
                    modifier = Modifier.weight(1f).height(36.dp).background6(color),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(if (isDeload) "D" else week.toString(), color = if (week <= m.weekInBlock) Color.Black else Palette.Muted, fontWeight = FontWeight.Bold)
                }
            }
        }
        Muted(
            if (m.deloadWeek) "Block ${m.blockNumber}: deload week. Next week a new 5-week block starts."
            else "Block ${m.blockNumber}: ${m.weeksUntilDeload} week(s) until the deload week. ${m.sessionsThisWeek} session(s) done this week.",
        )
        Text(
            "Start a new block this week",
            color = Palette.Cyan,
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.clickable(onClick = onRestart).padding(vertical = 8.dp),
        )
    }
}

private fun Modifier.background6(color: Color): Modifier =
    this.then(Modifier.background(color, RoundedCornerShape(10.dp)))

@Composable
private fun ChecklistCard(done: Map<String, Boolean>, onToggle: (String, Boolean) -> Unit) {
    Panel {
        SectionTitle("Pre-workout checklist")
        Muted("Check these about 45 minutes before you train. They reset every day.")
        for (group in PreWorkoutChecklist.groups) {
            Text(group, style = MaterialTheme.typography.titleMedium, color = Palette.Amber)
            for (item in PreWorkoutChecklist.items.filter { it.group == group }) {
                val checked = done[item.id] == true
                Row(
                    modifier = Modifier.fillMaxWidth().heightIn(min = 64.dp).clickable { onToggle(item.id, !checked) },
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Checkbox(
                        checked = checked,
                        onCheckedChange = { onToggle(item.id, it) },
                        colors = CheckboxDefaults.colors(checkedColor = Palette.Green, uncheckedColor = Palette.Muted, checkmarkColor = Color.Black),
                    )
                    Column {
                        Text(item.title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
                        Muted(item.detail)
                    }
                }
            }
        }
    }
}
