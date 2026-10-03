package com.personal.calisthenicsguide.ui.dashboard

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.personal.calisthenics.core.analytics.RecoveryState
import com.personal.calisthenics.core.model.WorkoutDay
import com.personal.calisthenics.core.plan.ChecklistGroup
import com.personal.calisthenics.core.plan.Deload
import com.personal.calisthenics.core.plan.PreWorkoutChecklist
import com.personal.calisthenicsguide.ui.theme.AppColors

@Composable
fun DashboardScreen(
    state: DashboardState?,
    sessionActive: Boolean,
    onStartWorkout: () -> Unit,
    onResumeWorkout: () -> Unit,
    onChecklist: (String, Boolean) -> Unit,
    onColdMode: (Boolean) -> Unit,
    onSound: (Boolean) -> Unit,
    onVibration: (Boolean) -> Unit,
    onPickDay: (WorkoutDay?) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (state == null) {
        Text("Loading...", modifier = modifier.padding(24.dp))
        return
    }
    Column(modifier = modifier) {
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text("Calisthenics Park Guide", style = MaterialTheme.typography.headlineMedium)
            RecoveryCard(state)
            NextSessionCard(state, onColdMode, onPickDay)
            DeloadCard(state)
            if (state.advice.messages.isNotEmpty()) AdviceCard(state.advice.messages)
            ChecklistCard(state, onChecklist)
            FeedbackCard(state, onSound, onVibration)
            Spacer(Modifier.height(8.dp))
        }
        // Start button sits at the bottom, directly above the navigation bar, for one-handed chalky taps.
        Button(
            onClick = if (sessionActive) onResumeWorkout else onStartWorkout,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 10.dp)
                .heightIn(min = 68.dp),
            shape = RoundedCornerShape(18.dp),
            colors = ButtonDefaults.buttonColors(containerColor = AppColors.Accent, contentColor = AppColors.AccentOn),
        ) {
            val label = if (sessionActive) "Resume workout" else "Start Day ${state.day.label}" + if (state.isDeload) " (Deload)" else ""
            Text(label, style = MaterialTheme.typography.titleLarge)
        }
    }
}

@Composable
private fun SectionCard(title: String, subtitle: String? = null, content: @Composable () -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = AppColors.Surface),
        shape = RoundedCornerShape(20.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, color = AppColors.Accent)
            if (subtitle != null) Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = AppColors.TextSecondary)
            content()
        }
    }
}

@Composable
private fun RecoveryCard(state: DashboardState) {
    val r = state.recovery
    val color = when (r.state) {
        RecoveryState.NO_HISTORY -> AppColors.Cyan
        RecoveryState.TOO_SOON -> AppColors.Warn
        RecoveryState.OPTIMAL_WINDOW -> AppColors.Good
        RecoveryState.OVERDUE -> AppColors.Danger
    }
    SectionCard("Tendon recovery", "Collagen synthesis window: 48-72 hours after your last session") {
        Text(
            state.elapsedLabel?.let { "$it since last workout" } ?: "No workout finished yet",
            style = MaterialTheme.typography.titleLarge,
        )
        RecoveryBar(progress = r.progress, markerColor = color)
        Text(r.headline, color = color, style = MaterialTheme.typography.titleMedium)
        Text(r.detail, style = MaterialTheme.typography.bodyMedium, color = AppColors.TextSecondary)
    }
}

/** 0-72 h scale with the 48-72 h optimal window highlighted. */
@Composable
private fun RecoveryBar(progress: Float, markerColor: Color) {
    Column {
        Canvas(Modifier.fillMaxWidth().height(22.dp)) {
            val radius = CornerRadius(size.height / 2, size.height / 2)
            drawRoundRect(AppColors.SurfaceHigh, size = size, cornerRadius = radius)
            val windowStart = size.width * (48f / 72f)
            drawRoundRect(
                AppColors.Good.copy(alpha = 0.35f),
                topLeft = Offset(windowStart, 0f),
                size = Size(size.width - windowStart, size.height),
                cornerRadius = radius,
            )
            val filled = size.width * progress.coerceIn(0f, 1f)
            if (filled > 0f) drawRoundRect(markerColor, size = Size(filled, size.height), cornerRadius = radius)
        }
        Row(Modifier.fillMaxWidth().padding(top = 4.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("0 h", style = MaterialTheme.typography.bodyMedium, color = AppColors.TextSecondary)
            Text("48 h", style = MaterialTheme.typography.bodyMedium, color = AppColors.Good)
            Text("72 h", style = MaterialTheme.typography.bodyMedium, color = AppColors.Good)
        }
    }
}

@Composable
private fun NextSessionCard(state: DashboardState, onColdMode: (Boolean) -> Unit, onPickDay: (WorkoutDay?) -> Unit) {
    SectionCard("Next session") {
        Text(
            "Day ${state.day.label}" + if (state.isDeload) "  -  Deload" else "",
            style = MaterialTheme.typography.headlineMedium,
        )
        Text(
            "Usually ${state.day.nominalWeekday.getDisplayName(java.time.format.TextStyle.FULL, java.util.Locale.ENGLISH)}s. ${state.day.focus}",
            style = MaterialTheme.typography.bodyLarge,
        )
        Text("Pull-up grip: ${state.grip.displayName}", color = AppColors.Cyan, style = MaterialTheme.typography.bodyLarge)
        val range = state.estimateMinutes
        Text(
            "About ${range.first}-${range.last} min, ${state.totalSets} sets. The range depends on how long you rest.",
            color = AppColors.TextSecondary,
            style = MaterialTheme.typography.bodyMedium,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            WorkoutDay.entries.forEach { d ->
                FilterChip(
                    selected = d == state.day,
                    onClick = { onPickDay(if (d == state.suggestedDay) null else d) },
                    label = { Text(d.label + if (d == state.suggestedDay) " (next)" else "") },
                    modifier = Modifier.heightIn(min = 48.dp),
                )
            }
        }
        if (state.dayIsOverridden) {
            Text("Rotation overridden for this session.", color = AppColors.Warn, style = MaterialTheme.typography.bodyMedium)
        }
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Cold weather / antagonist supersets", style = MaterialTheme.typography.titleMedium)
                Text(
                    "Pairs exercises 3+4 and 5+6 with 90 s rests to stay warm",
                    color = AppColors.TextSecondary,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            Switch(checked = state.coldMode, onCheckedChange = onColdMode)
        }
        Text(
            "Streak: ${state.streakWeeks} week${if (state.streakWeeks == 1) "" else "s"} at 3 sessions  |  This week: ${state.sessionsThisWeek}/3",
            color = AppColors.TextSecondary,
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

@Composable
private fun DeloadCard(state: DashboardState) {
    val total = Deload.BLOCK_LENGTH_WEEKS
    SectionCard(
        "Deload tracker",
        "Block ${state.blockNumber}: 5 weeks of accumulation, then a deload week with half the sets",
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            for (week in 1..total) {
                val done = week < state.weekInBlock
                val current = week == state.weekInBlock
                val deloadWeek = week == total
                val fill = when {
                    current && deloadWeek -> AppColors.Cyan
                    current -> AppColors.Accent
                    done -> AppColors.Good.copy(alpha = 0.6f)
                    deloadWeek -> AppColors.Cyan.copy(alpha = 0.25f)
                    else -> AppColors.SurfaceHigh
                }
                Canvas(Modifier.weight(1f).height(16.dp)) {
                    drawRoundRect(fill, size = size, cornerRadius = CornerRadius(8f, 8f))
                }
            }
        }
        Text(
            if (state.isDeload) "Deload week: total sets are halved. Keep every rep clean and let the tendons catch up."
            else "Week ${state.weekInBlock} of $total  -  deload starts in ${state.weeksUntilDeload} week${if (state.weeksUntilDeload == 1) "" else "s"}",
            style = MaterialTheme.typography.bodyLarge,
            color = if (state.isDeload) AppColors.Cyan else AppColors.TextPrimary,
        )
    }
}

@Composable
private fun AdviceCard(messages: List<String>) {
    Card(
        colors = CardDefaults.cardColors(containerColor = AppColors.Warn.copy(alpha = 0.18f)),
        shape = RoundedCornerShape(20.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("Adjusted for your last joint log", style = MaterialTheme.typography.titleMedium, color = AppColors.Warn)
            messages.forEach { Text(it, style = MaterialTheme.typography.bodyMedium) }
        }
    }
}

@Composable
private fun ChecklistCard(state: DashboardState, onChecklist: (String, Boolean) -> Unit) {
    ChecklistGroup.entries.forEach { group ->
        SectionCard(group.title, group.subtitle) {
            PreWorkoutChecklist.byGroup(group).forEach { item ->
                val checked = state.checklist[item.id] == true
                Row(
                    Modifier.fillMaxWidth().heightIn(min = 56.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Checkbox(checked = checked, onCheckedChange = { onChecklist(item.id, it) }, modifier = Modifier.size(48.dp))
                    Column(Modifier.weight(1f)) {
                        Text(item.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                        Text(item.detail, style = MaterialTheme.typography.bodyMedium, color = AppColors.TextSecondary)
                    }
                }
            }
        }
    }
}

@Composable
private fun FeedbackCard(state: DashboardState, onSound: (Boolean) -> Unit, onVibration: (Boolean) -> Unit) {
    SectionCard("Timer feedback", "Short beeps lower your music for a moment instead of pausing it") {
        Row(Modifier.fillMaxWidth().heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("Beeps", Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
            Switch(checked = state.soundOn, onCheckedChange = onSound)
        }
        Row(Modifier.fillMaxWidth().heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("Vibration", Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
            Switch(checked = state.vibrationOn, onCheckedChange = onVibration)
        }
    }
}
