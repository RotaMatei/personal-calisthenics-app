package com.personal.calisthenicsguide.ui.workout

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.personal.calisthenics.core.model.WorkoutDay
import com.personal.calisthenics.core.plan.OutlineRow
import com.personal.calisthenics.core.plan.OutlineSection
import com.personal.calisthenics.core.plan.SessionPlanner
import com.personal.calisthenics.core.plan.WorkoutOutline
import com.personal.calisthenics.core.seed.SeedData
import com.personal.calisthenicsguide.ui.components.AppCard
import com.personal.calisthenicsguide.ui.components.Pill
import com.personal.calisthenicsguide.ui.components.PrimaryButton
import com.personal.calisthenicsguide.ui.components.ScreenTitle
import com.personal.calisthenicsguide.ui.dashboard.DashboardState
import com.personal.calisthenicsguide.ui.guide.ClipStill
import com.personal.calisthenicsguide.ui.guide.ClipView
import com.personal.calisthenicsguide.ui.theme.AppColors
import com.personal.calisthenicsguide.ui.theme.Space

/**
 * What the Workout tab shows while no session runs: today's workout in full. Every exercise lists its exact sets and
 * reps (or seconds) and its rest in minutes, with a picture of the correct position; tapping a row opens it like a
 * drawer onto the looping 3D clip with the details under it. Nothing starts until the Start button at the bottom.
 */
@Composable
fun WorkoutDetailsScreen(
    state: DashboardState?,
    onColdMode: (Boolean) -> Unit,
    onPickDay: (WorkoutDay?) -> Unit,
    onStart: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (state == null) {
        Text("Loading...", modifier = modifier.padding(Space.xl), color = AppColors.TextSecondary)
        return
    }
    val sections = remember(state.options) { WorkoutOutline.of(SessionPlanner.plan(state.options)) }
    var expandedId by rememberSaveable { mutableStateOf<String?>(null) }

    Column(modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(horizontal = Space.screen, vertical = Space.lg),
            verticalArrangement = Arrangement.spacedBy(Space.sm),
        ) {
            item(key = "header") { Header(state) }
            item(key = "options") {
                Box(Modifier.padding(top = Space.sm, bottom = Space.xs)) { OptionsCard(state, onColdMode, onPickDay) }
            }
            sections.forEach { section ->
                item(key = "phase_${section.phase.name}") { PhaseHeading(section) }
                items(section.rows, key = { it.stepId }) { row ->
                    ExerciseRow(
                        row = row,
                        expanded = expandedId == row.stepId,
                        onToggle = { expandedId = if (expandedId == row.stepId) null else row.stepId },
                    )
                }
            }
        }
        Box(Modifier.padding(horizontal = Space.screen, vertical = Space.md)) {
            PrimaryButton("Start workout", onStart, height = 60.dp)
        }
    }
}

@Composable
private fun Header(state: DashboardState) {
    Column(verticalArrangement = Arrangement.spacedBy(Space.sm)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Space.md)) {
            Text("Day ${state.day.label}", style = MaterialTheme.typography.headlineMedium)
            if (state.isDeload) Pill("Deload week", AppColors.Warn)
        }
        Text(state.day.focus, style = MaterialTheme.typography.bodyLarge, color = AppColors.TextSecondary)
        val range = state.estimateMinutes
        Text(
            "About ${range.first}-${range.last} min  |  ${state.totalSets} sets  |  ${state.grip.short} pull-ups",
            style = MaterialTheme.typography.bodyMedium,
            color = AppColors.TextSecondary,
        )
    }
}

@Composable
private fun OptionsCard(state: DashboardState, onColdMode: (Boolean) -> Unit, onPickDay: (WorkoutDay?) -> Unit) {
    AppCard {
        Row(horizontalArrangement = Arrangement.spacedBy(Space.sm), verticalAlignment = Alignment.CenterVertically) {
            WorkoutDay.entries.forEach { d ->
                FilterChip(
                    selected = d == state.day,
                    onClick = { onPickDay(if (d == state.suggestedDay) null else d) },
                    label = { Text("Day ${d.label}" + if (d == state.suggestedDay) " (next)" else "") },
                    modifier = Modifier.heightIn(min = Space.tap),
                )
            }
        }
        if (state.dayIsOverridden) {
            Text("You picked a different day than the rotation suggests.", style = MaterialTheme.typography.bodyMedium, color = AppColors.Warn)
        }
        Row(Modifier.fillMaxWidth().heightIn(min = Space.tap), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Cold weather / antagonist supersets", style = MaterialTheme.typography.titleMedium)
                Text(
                    "Pairs exercises 3+4 and 5+6 with 1.5 min rests to stay warm",
                    style = MaterialTheme.typography.bodyMedium,
                    color = AppColors.TextSecondary,
                )
            }
            Switch(checked = state.coldMode, onCheckedChange = onColdMode)
        }
    }
}

@Composable
private fun PhaseHeading(section: OutlineSection) {
    Column(Modifier.padding(top = Space.lg, bottom = Space.xs), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text("Phase ${section.phase.index}: ${section.phase.title}", style = MaterialTheme.typography.titleMedium)
        Text("${section.phase.subtitle}  |  ${section.minutesLabel}", style = MaterialTheme.typography.bodyMedium, color = AppColors.TextSecondary)
        section.note?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = AppColors.TextSecondary) }
    }
}

@Composable
private fun ExerciseRow(row: OutlineRow, expanded: Boolean, onToggle: () -> Unit) {
    val exercise = remember(row.exerciseId) { SeedData.exerciseOrNull(row.exerciseId) }
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(Space.corner))
            .background(AppColors.Surface)
            .animateContentSize(),
    ) {
        Row(
            Modifier.fillMaxWidth().clickable(onClick = onToggle).padding(Space.md),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Space.md),
        ) {
            ClipStill(row.exerciseId, Modifier.size(76.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text((row.number?.let { "$it. " } ?: "") + row.title, style = MaterialTheme.typography.titleMedium)
                Text(row.prescription, style = MaterialTheme.typography.bodyLarge, color = AppColors.Accent)
                row.rest?.let { Text("Rest $it", style = MaterialTheme.typography.bodyMedium, color = AppColors.TextSecondary) }
            }
            Icon(
                if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                contentDescription = if (expanded) "Close" else "Open",
                tint = AppColors.TextSecondary,
            )
        }
        if (expanded) {
            Column(
                Modifier.padding(start = Space.md, end = Space.md, bottom = Space.md),
                verticalArrangement = Arrangement.spacedBy(Space.sm),
            ) {
                ClipView(row.exerciseId, exercise?.highlights ?: emptyList(), height = 300.dp)
                Text(summaryLine(row), style = MaterialTheme.typography.titleMedium)
                if (row.cue.isNotBlank()) Text(row.cue, style = MaterialTheme.typography.bodyLarge)
                row.details.forEach { Text(it, style = MaterialTheme.typography.bodyMedium, color = AppColors.TextSecondary) }
                exercise?.let { Text(it.summary, style = MaterialTheme.typography.bodyMedium, color = AppColors.TextSecondary) }
                row.notes.forEach { Text(it, style = MaterialTheme.typography.bodyMedium, color = AppColors.Warn) }
                Spacer(Modifier.size(Space.xs))
            }
        }
    }
}

/** "4 x 6-10 reps  |  Rest 2.5-3 min  |  Tempo 3-1-X-0". */
private fun summaryLine(row: OutlineRow): String = listOfNotNull(
    row.prescription,
    row.rest?.let { "Rest $it" },
    row.tempo?.let { "Tempo $it" },
).joinToString("  |  ")
