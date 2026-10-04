package com.personal.calisthenicsguide.ui.stats

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.personal.calisthenics.core.analytics.CellKind
import com.personal.calisthenics.core.analytics.HeatmapCell
import com.personal.calisthenics.core.analytics.HeatmapMonth
import com.personal.calisthenics.core.analytics.Streaks
import com.personal.calisthenics.core.analytics.VolumeCheck
import com.personal.calisthenicsguide.ui.Muted
import com.personal.calisthenicsguide.ui.Palette
import com.personal.calisthenicsguide.ui.Panel
import com.personal.calisthenicsguide.ui.Pill
import com.personal.calisthenicsguide.ui.SectionTitle
import com.personal.calisthenicsguide.ui.appViewModel
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

@Composable
fun StatsScreen(modifier: Modifier = Modifier) {
    val vm = appViewModel { StatsViewModel(it.repository) }
    val ui by vm.ui.collectAsStateWithLifecycle()
    val state = ui
    if (state == null) {
        Muted("Loading...", modifier = modifier.padding(16.dp))
        return
    }
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item { Text("Stats", style = MaterialTheme.typography.headlineMedium, color = Palette.Amber) }
        item { VolumeCard(state.volume) }
        item {
            ConsistencyCard(
                heatmap = state.heatmap,
                streak = state.streak,
                totalSessions = state.totalSessions,
                canGoForward = state.canGoForward,
                onPrevious = { vm.shiftMonth(-1) },
                onNext = { vm.shiftMonth(1) },
            )
        }
        item {
            Panel {
                SectionTitle("Isometric strength")
                Muted("Longest hold of each session, in seconds.")
                LineChart(
                    listOf(
                        ChartLine("L-sit", Palette.Amber, state.lSit),
                        ChartLine("Planche lean", Palette.Cyan, state.planche),
                    ),
                )
            }
        }
        item {
            Panel {
                SectionTitle("Pull and push volume")
                Muted("Total clean reps per session.")
                LineChart(
                    listOf(
                        ChartLine("Pull-ups", Palette.Green, state.pullups),
                        ChartLine("Dips", Palette.Red, state.dips),
                    ),
                )
            }
        }
        item { JointCard(state) }
    }
}

// ---------------------------------------------------------------------- tendon safety guard

@Composable
private fun VolumeCard(volume: VolumeCheck) {
    val warning = volume.warning
    Panel(color = if (warning != null) Color(0xFF4A1414) else MaterialTheme.colorScheme.surface) {
        if (warning != null) {
            Text("Tendon safety warning", style = MaterialTheme.typography.titleLarge, color = Palette.Red)
            Text(warning, style = MaterialTheme.typography.bodyLarge)
        } else {
            SectionTitle("Tendon safety guard")
        }
        val pull = volume.current.pullReps
        val base = volume.baseline
        if (base == null) {
            Muted("This week: $pull pulling reps. Next week this is compared with the week before to catch volume spikes above 15%.")
        } else {
            val change = volume.pullChangePercent?.let { " (${if (it >= 0) "+" else ""}${"%.0f".format(Locale.ENGLISH, it)}%)" } ?: ""
            Muted("Pulling reps this week: $pull, last comparable week: ${base.pullReps}$change.")
        }
    }
}

// ---------------------------------------------------------------------- consistency heatmap

private fun cellColor(kind: CellKind): Color = when (kind) {
    CellKind.WORKOUT_A -> Palette.Amber
    CellKind.WORKOUT_B -> Palette.Cyan
    CellKind.WORKOUT_C -> Palette.Green
    CellKind.REST -> Palette.SurfaceHigh
    CellKind.FUTURE -> Color.Transparent
}

@Composable
private fun ConsistencyCard(
    heatmap: HeatmapMonth,
    streak: Int,
    totalSessions: Int,
    canGoForward: Boolean,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
) {
    Panel {
        SectionTitle("Consistency")
        Text(
            if (streak == 0) "No weekly streak yet" else "$streak week${if (streak == 1) "" else "s"} streak",
            style = MaterialTheme.typography.headlineSmall,
            color = if (streak == 0) Palette.Muted else Palette.Green,
        )
        Muted("A streak week has at least ${Streaks.WEEKLY_TARGET} sessions. $totalSessions session${if (totalSessions == 1) "" else "s"} logged in total.")
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onPrevious, modifier = Modifier.heightIn(min = 56.dp)) { Text("<", style = MaterialTheme.typography.titleLarge) }
            Text(
                "${heatmap.month.month.getDisplayName(TextStyle.FULL, Locale.ENGLISH)} ${heatmap.month.year}",
                style = MaterialTheme.typography.titleMedium,
                textAlign = TextAlign.Center,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = onNext, enabled = canGoForward, modifier = Modifier.heightIn(min = 56.dp)) { Text(">", style = MaterialTheme.typography.titleLarge) }
        }
        HeatmapGrid(heatmap)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Pill("Day A", Palette.Amber)
            Pill("Day B", Palette.Cyan)
            Pill("Day C", Palette.Green)
        }
        Muted("${heatmap.workoutCount} workout${if (heatmap.workoutCount == 1) "" else "s"} this month. Outlined squares are deload week days.")
    }
}

@Composable
private fun HeatmapGrid(month: HeatmapMonth) {
    val labels = listOf("M", "T", "W", "T", "F", "S", "S")
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            for (label in labels) {
                Text(label, style = MaterialTheme.typography.labelMedium, color = Palette.Muted, textAlign = TextAlign.Center, modifier = Modifier.weight(1f))
            }
        }
        for (week in month.weeks) {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                for (cell in week) {
                    if (cell == null) Box(modifier = Modifier.weight(1f).aspectRatio(1f)) else HeatCell(cell, Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun HeatCell(cell: HeatmapCell, modifier: Modifier) {
    val shape = RoundedCornerShape(8.dp)
    val worked = cell.kind == CellKind.WORKOUT_A || cell.kind == CellKind.WORKOUT_B || cell.kind == CellKind.WORKOUT_C
    val borderColor = when {
        cell.isToday -> Color.White
        cell.deloadWeek -> Palette.Muted
        else -> Color.Transparent
    }
    Box(
        modifier = modifier
            .aspectRatio(1f)
            .clip(shape)
            .background(cellColor(cell.kind))
            .border(if (cell.isToday) 2.dp else 1.5.dp, borderColor, shape),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            "${cell.date.dayOfMonth}",
            style = MaterialTheme.typography.labelMedium,
            color = if (worked) Color.Black else Palette.Muted,
        )
    }
}

// ---------------------------------------------------------------------- joint log

@Composable
private fun JointCard(state: StatsUi) {
    Panel {
        SectionTitle("Joint log")
        for (message in state.advice) {
            Panel(color = MaterialTheme.colorScheme.primaryContainer) { Text(message, style = MaterialTheme.typography.bodyLarge) }
        }
        if (state.joints.isEmpty()) {
            Muted("After each session you rate wrists, elbows and shoulders from 1 to 5. The history shows up here.")
        } else {
            Muted("Newest first. Ratings above 2 are shown in red.")
            val format = DateTimeFormatter.ofPattern("EEE d MMM", Locale.ENGLISH)
            for (entry in state.joints.take(20)) {
                Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Column(modifier = Modifier.weight(1.4f)) {
                        Text(entry.date.format(format), style = MaterialTheme.typography.bodyLarge)
                        Muted("Day ${entry.day.label}")
                    }
                    RatingCell("Wrists", entry.rating.wrists, Modifier.weight(1f))
                    RatingCell("Elbows", entry.rating.elbows, Modifier.weight(1f))
                    RatingCell("Shoulders", entry.rating.shoulders, Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun RatingCell(label: String, value: Int, modifier: Modifier) {
    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text("$value", style = MaterialTheme.typography.titleLarge, color = if (value > 2) Palette.Red else Palette.Green)
        Text(label, style = MaterialTheme.typography.labelMedium, color = Palette.Muted, maxLines = 1)
    }
}
