package com.personal.calisthenicsguide.ui.dashboard

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.personal.calisthenics.core.analytics.RecoveryState
import com.personal.calisthenics.core.plan.ChecklistGroup
import com.personal.calisthenics.core.plan.Deload
import com.personal.calisthenics.core.plan.PreWorkoutChecklist
import com.personal.calisthenicsguide.ui.components.AppCard
import com.personal.calisthenicsguide.ui.components.CollapsibleCard
import com.personal.calisthenicsguide.ui.components.Pill
import com.personal.calisthenicsguide.ui.components.PrimaryButton
import com.personal.calisthenicsguide.ui.components.ScreenTitle
import com.personal.calisthenicsguide.ui.components.SectionLabel
import com.personal.calisthenicsguide.ui.theme.AppColors
import com.personal.calisthenicsguide.ui.theme.Space
import java.time.format.DateTimeFormatter
import java.util.Locale

private val DateFormat = DateTimeFormatter.ofPattern("EEEE d MMMM", Locale.ENGLISH)

/**
 * Home: today's workout first, then how recovered you are and where you are in the block. The pre-workout checklist and
 * the sound options sit in collapsed cards so the screen stays calm. The button at the bottom opens the workout page;
 * it never starts a workout by itself.
 */
@Composable
fun DashboardScreen(
    state: DashboardState?,
    sessionActive: Boolean,
    onOpenWorkout: () -> Unit,
    onChecklist: (String, Boolean) -> Unit,
    onSound: (Boolean) -> Unit,
    onVibration: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (state == null) {
        Text("Loading...", modifier = modifier.padding(Space.xl), color = AppColors.TextSecondary)
        return
    }
    Column(modifier) {
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Space.screen, vertical = Space.lg),
            verticalArrangement = Arrangement.spacedBy(Space.md),
        ) {
            ScreenTitle("Today", state.today.format(DateFormat))
            NextSessionCard(state, onOpenWorkout)
            RecoveryCard(state)
            BlockCard(state)
            if (state.advice.messages.isNotEmpty()) AdviceCard(state.advice.messages)
            ChecklistCard(state, onChecklist)
            SoundCard(state, onSound, onVibration)
        }
        Box(Modifier.padding(horizontal = Space.screen, vertical = Space.md)) {
            val label = if (sessionActive) "Resume workout" else "View Day ${state.day.label} workout" + if (state.isDeload) " (deload)" else ""
            PrimaryButton(label, onOpenWorkout, height = 60.dp)
        }
    }
}

@Composable
private fun NextSessionCard(state: DashboardState, onOpen: () -> Unit) {
    AppCard(onClick = onOpen) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            SectionLabel("Next session", Modifier.weight(1f))
            if (state.isDeload) Pill("Deload week", AppColors.Warn)
        }
        Text("Day ${state.day.label}", style = MaterialTheme.typography.headlineMedium)
        Text(state.day.focus, style = MaterialTheme.typography.bodyLarge, color = AppColors.TextSecondary)
        val range = state.estimateMinutes
        Text(
            "About ${range.first}-${range.last} min  |  ${state.totalSets} sets  |  ${state.grip.short} pull-ups",
            style = MaterialTheme.typography.bodyMedium,
            color = AppColors.Accent,
        )
        if (state.dayIsOverridden) {
            Text("Not the next day in the rotation (you changed it).", style = MaterialTheme.typography.bodyMedium, color = AppColors.Warn)
        }
    }
}

@Composable
private fun RecoveryCard(state: DashboardState) {
    val r = state.recovery
    val color = when (r.state) {
        RecoveryState.NO_HISTORY -> AppColors.Info
        RecoveryState.TOO_SOON -> AppColors.Warn
        RecoveryState.OPTIMAL_WINDOW -> AppColors.Good
        RecoveryState.OVERDUE -> AppColors.Danger
    }
    AppCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            SectionLabel("Tendon recovery", Modifier.weight(1f))
            Text(
                state.elapsedLabel?.let { "$it since last workout" } ?: "No workout finished yet",
                style = MaterialTheme.typography.bodyMedium,
                color = AppColors.TextSecondary,
            )
        }
        RecoveryBar(progress = r.progress, markerColor = color)
        Text(r.headline, style = MaterialTheme.typography.titleMedium, color = color)
        Text(r.detail, style = MaterialTheme.typography.bodyMedium, color = AppColors.TextSecondary)
    }
}

/** 0-72 h scale with the 48-72 h collagen-synthesis window tinted. */
@Composable
private fun RecoveryBar(progress: Float, markerColor: Color) {
    Column(verticalArrangement = Arrangement.spacedBy(Space.xs)) {
        Canvas(Modifier.fillMaxWidth().height(12.dp)) {
            val radius = CornerRadius(size.height / 2, size.height / 2)
            drawRoundRect(AppColors.SurfaceHigh, size = size, cornerRadius = radius)
            val windowStart = size.width * (48f / 72f)
            drawRoundRect(
                AppColors.Good.copy(alpha = 0.28f),
                topLeft = Offset(windowStart, 0f),
                size = Size(size.width - windowStart, size.height),
                cornerRadius = radius,
            )
            val filled = size.width * progress.coerceIn(0f, 1f)
            if (filled > 0f) drawRoundRect(markerColor.copy(alpha = 0.85f), size = Size(filled, size.height), cornerRadius = radius)
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("0 h", style = MaterialTheme.typography.labelMedium, color = AppColors.TextSecondary)
            Text("48 h", style = MaterialTheme.typography.labelMedium, color = AppColors.Good)
            Text("72 h", style = MaterialTheme.typography.labelMedium, color = AppColors.Good)
        }
    }
}

@Composable
private fun BlockCard(state: DashboardState) {
    val total = Deload.BLOCK_LENGTH_WEEKS
    AppCard {
        SectionLabel("Training block ${state.blockNumber}")
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Space.xs)) {
            for (week in 1..total) {
                val done = week < state.weekInBlock
                val current = week == state.weekInBlock
                val deloadWeek = week == total
                val fill = when {
                    current && deloadWeek -> AppColors.Warn
                    current -> AppColors.Accent
                    done -> AppColors.Accent.copy(alpha = 0.4f)
                    deloadWeek -> AppColors.Warn.copy(alpha = 0.22f)
                    else -> AppColors.SurfaceHigh
                }
                Canvas(Modifier.weight(1f).height(8.dp)) {
                    drawRoundRect(fill, size = size, cornerRadius = CornerRadius(8f, 8f))
                }
            }
        }
        Text(
            if (state.isDeload) "Deload week: total sets are halved. Keep every rep clean and let the tendons catch up."
            else "Week ${state.weekInBlock} of $total  |  deload starts in ${state.weeksUntilDeload} week${if (state.weeksUntilDeload == 1) "" else "s"}",
            style = MaterialTheme.typography.bodyLarge,
            color = if (state.isDeload) AppColors.Warn else AppColors.TextPrimary,
        )
        Text(
            "This week ${state.sessionsThisWeek}/3 sessions  |  ${state.streakWeeks} week${if (state.streakWeeks == 1) "" else "s"} streak",
            style = MaterialTheme.typography.bodyMedium,
            color = AppColors.TextSecondary,
        )
    }
}

@Composable
private fun AdviceCard(messages: List<String>) {
    AppCard(container = AppColors.Warn.copy(alpha = 0.12f)) {
        Text("Adjusted for your last joint log", style = MaterialTheme.typography.titleMedium, color = AppColors.Warn)
        messages.forEach { Text(it, style = MaterialTheme.typography.bodyMedium) }
    }
}

@Composable
private fun ChecklistCard(state: DashboardState, onChecklist: (String, Boolean) -> Unit) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    val all = ChecklistGroup.entries.flatMap { PreWorkoutChecklist.byGroup(it) }
    val done = all.count { state.checklist[it.id] == true }
    CollapsibleCard(
        title = "Before you start",
        summary = "$done of ${all.size} checked",
        expanded = expanded,
        onToggle = { expanded = !expanded },
    ) {
        ChecklistGroup.entries.forEach { group ->
            Column(Modifier.padding(top = Space.sm)) {
                Text(group.title, style = MaterialTheme.typography.labelLarge, color = AppColors.Accent)
                Text(group.subtitle, style = MaterialTheme.typography.bodyMedium, color = AppColors.TextSecondary)
            }
            PreWorkoutChecklist.byGroup(group).forEach { item ->
                Row(Modifier.fillMaxWidth().heightIn(min = 56.dp), verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(
                        checked = state.checklist[item.id] == true,
                        onCheckedChange = { onChecklist(item.id, it) },
                        modifier = Modifier.size(Space.tap),
                    )
                    Column(Modifier.weight(1f)) {
                        Text(item.title, style = MaterialTheme.typography.titleMedium)
                        Text(item.detail, style = MaterialTheme.typography.bodyMedium, color = AppColors.TextSecondary)
                    }
                }
            }
        }
    }
}

@Composable
private fun SoundCard(state: DashboardState, onSound: (Boolean) -> Unit, onVibration: (Boolean) -> Unit) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    CollapsibleCard(
        title = "Timer sound and vibration",
        summary = "Beeps ${if (state.soundOn) "on" else "off"}  |  vibration ${if (state.vibrationOn) "on" else "off"}",
        expanded = expanded,
        onToggle = { expanded = !expanded },
    ) {
        Text("Short beeps lower your music for a moment instead of pausing it.", style = MaterialTheme.typography.bodyMedium, color = AppColors.TextSecondary)
        Row(Modifier.fillMaxWidth().heightIn(min = Space.tap), verticalAlignment = Alignment.CenterVertically) {
            Text("Beeps", Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
            Switch(checked = state.soundOn, onCheckedChange = onSound)
        }
        Row(Modifier.fillMaxWidth().heightIn(min = Space.tap), verticalAlignment = Alignment.CenterVertically) {
            Text("Vibration", Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
            Switch(checked = state.vibrationOn, onCheckedChange = onVibration)
        }
    }
}
