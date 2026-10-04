package com.personal.calisthenicsguide.ui.stats

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.personal.calisthenics.core.analytics.CellKind
import com.personal.calisthenics.core.analytics.ChartSeries
import com.personal.calisthenics.core.analytics.Heatmap
import com.personal.calisthenics.core.analytics.JointAdvice
import com.personal.calisthenics.core.analytics.Streaks
import com.personal.calisthenics.core.analytics.VolumeGuard
import com.personal.calisthenics.core.model.WorkoutDay
import com.personal.calisthenics.core.plan.Deload
import com.personal.calisthenicsguide.data.Repository
import com.personal.calisthenicsguide.data.SettingKeys
import com.personal.calisthenicsguide.ui.theme.AppColors
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle as DateTextStyle
import java.util.Locale

private val DayA = Color(0xFFFFC400)
private val DayB = Color(0xFF4DD9F0)
private val DayC = Color(0xFFC792EA)

@Composable
fun StatsScreen(repository: Repository, modifier: Modifier = Modifier) {
    val sessions by repository.completedSessions.collectAsState(initial = emptyList())
    val sets by repository.loggedSets.collectAsState(initial = emptyList())
    val settings by repository.settings.collectAsState(initial = emptyMap())
    val zone = remember { ZoneId.systemDefault() }
    val today = LocalDate.now(zone)
    val blockStart = settings[SettingKeys.BLOCK_START]?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
        ?: Deload.normalizeStart(today)

    val sessionDays = remember(sessions) {
        sessions.associate { Instant.ofEpochMilli(it.session.endedAtMs).atZone(zone).toLocalDate() to it.session.day }
    }

    Column(
        modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text("Stats", style = MaterialTheme.typography.headlineMedium)

        // ---- Safety guard first: the warning is the most important thing on this tab.
        val weekly = remember(sets) { VolumeGuard.weeklyVolumes(sets, zone) }
        val check = VolumeGuard.evaluate(weekly, VolumeGuard.weekStart(today)) { Deload.isDeloadWeek(blockStart, it) }
        SafetyGuardCard(check)

        HeatmapCard(sessionDays, blockStart, today, sessions.size, Streaks.weeklyStreak(sessionDays.keys.toList(), today))

        Card(colors = CardDefaults.cardColors(containerColor = AppColors.Surface), shape = RoundedCornerShape(20.dp), modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
                Text("Strength progression", style = MaterialTheme.typography.titleMedium, color = AppColors.Accent)
                LineChart("L-sit max hold", "s", ChartSeries.maxHoldPerSession(sets, "l_sit"), DayA)
                LineChart("Planche lean max hold", "s", ChartSeries.maxHoldPerSession(sets, "planche_lean"), DayB)
                LineChart("Pull-ups: clean reps per workout", "reps", ChartSeries.totalRepsPerSession(sets, setOf("strict_pullups")), AppColors.Good)
                LineChart(
                    "Dips: clean reps per workout", "reps",
                    ChartSeries.totalRepsPerSession(sets, setOf("parallel_bar_dips", "straight_bar_dips")), DayC,
                )
            }
        }

        JointHistoryCard(sessions.sortedByDescending { it.session.endedAtMs }, zone)
        Spacer(Modifier.height(16.dp))
    }
}

// ------------------------------------------------------------------------------------------------ safety guard

@Composable
private fun SafetyGuardCard(check: com.personal.calisthenics.core.analytics.VolumeCheck) {
    val warn = check.warning
    Card(
        colors = CardDefaults.cardColors(containerColor = if (warn != null) AppColors.Danger.copy(alpha = 0.22f) else AppColors.Surface),
        shape = RoundedCornerShape(20.dp),
        modifier = Modifier.fillMaxWidth().then(if (warn != null) Modifier.border(2.dp, AppColors.Danger, RoundedCornerShape(20.dp)) else Modifier),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Tendon Safety Guard", style = MaterialTheme.typography.titleMedium, color = if (warn != null) AppColors.Danger else AppColors.Accent)
            if (warn != null) Text(warn, style = MaterialTheme.typography.titleMedium)
            Text(
                "This week: ${check.current.pullReps} pull reps, ${check.current.pushReps} push reps",
                style = MaterialTheme.typography.bodyLarge,
            )
            val base = check.baseline
            if (base == null) {
                Text("No earlier week to compare with yet.", style = MaterialTheme.typography.bodyMedium, color = AppColors.TextSecondary)
            } else {
                Text(
                    "Previous week: ${base.pullReps} pull (${percent(check.pullChangePercent)}), ${base.pushReps} push (${percent(check.pushChangePercent)} this week)",
                    style = MaterialTheme.typography.bodyMedium,
                    color = AppColors.TextSecondary,
                )
            }
            Text(
                "Alert threshold: pulling volume more than ${VolumeGuard.SPIKE_PERCENT}% above the previous week. A deload week is skipped as the baseline.",
                style = MaterialTheme.typography.bodyMedium,
                color = AppColors.TextSecondary,
            )
        }
    }
}

private fun percent(value: Double?): String = value?.let { (if (it >= 0) "+" else "") + "%.0f%%".format(it) } ?: "n/a"

// ------------------------------------------------------------------------------------------------ heatmap

@Composable
private fun HeatmapCard(sessionDays: Map<LocalDate, WorkoutDay>, blockStart: LocalDate, today: LocalDate, total: Int, streak: Int) {
    var monthOffset by rememberSaveable { mutableStateOf(0) }
    val month = YearMonth.from(today).plusMonths(monthOffset.toLong())
    val data = remember(sessionDays, month, blockStart, today) { Heatmap.month(month, sessionDays, blockStart, today) }
    Card(colors = CardDefaults.cardColors(containerColor = AppColors.Surface), shape = RoundedCornerShape(20.dp), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Consistency", style = MaterialTheme.typography.titleMedium, color = AppColors.Accent)
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedButton(onClick = { monthOffset-- }, modifier = Modifier.heightIn(min = 48.dp)) { Text("<") }
                Text(
                    month.month.getDisplayName(DateTextStyle.FULL, Locale.ENGLISH) + " " + month.year,
                    modifier = Modifier.weight(1f),
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.titleMedium,
                )
                OutlinedButton(onClick = { monthOffset++ }, enabled = monthOffset < 0, modifier = Modifier.heightIn(min = 48.dp)) { Text(">") }
            }
            Row(Modifier.fillMaxWidth()) {
                listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun").forEach {
                    Text(it, Modifier.weight(1f), textAlign = TextAlign.Center, style = MaterialTheme.typography.bodyMedium, color = AppColors.TextSecondary)
                }
            }
            data.weeks.forEach { week ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                    week.forEach { cell ->
                        Box(Modifier.weight(1f).aspectRatio(1f), contentAlignment = Alignment.Center) {
                            if (cell != null) {
                                val fill = when (cell.kind) {
                                    CellKind.WORKOUT_A -> DayA
                                    CellKind.WORKOUT_B -> DayB
                                    CellKind.WORKOUT_C -> DayC
                                    CellKind.REST -> AppColors.SurfaceHigh
                                    CellKind.FUTURE -> AppColors.Background
                                }
                                val worked = cell.kind == CellKind.WORKOUT_A || cell.kind == CellKind.WORKOUT_B || cell.kind == CellKind.WORKOUT_C
                                var mod = Modifier.fillMaxSize().background(fill, RoundedCornerShape(8.dp))
                                if (cell.deloadWeek) mod = mod.border(1.5.dp, AppColors.Info.copy(alpha = 0.7f), RoundedCornerShape(8.dp))
                                if (cell.isToday) mod = mod.border(2.5.dp, Color.White, RoundedCornerShape(8.dp))
                                Box(mod, contentAlignment = Alignment.Center) {
                                    Text(
                                        if (worked) (sessionDays[cell.date]?.label ?: "") else cell.date.dayOfMonth.toString(),
                                        color = if (worked) Color.Black else AppColors.TextSecondary,
                                        fontWeight = if (worked) FontWeight.Bold else FontWeight.Normal,
                                        style = MaterialTheme.typography.bodyMedium,
                                    )
                                }
                            }
                        }
                    }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
                Legend(DayA, "Day A"); Legend(DayB, "Day B"); Legend(DayC, "Day C"); Legend(AppColors.SurfaceHigh, "Rest")
            }
            Text("Cyan outline = deload week", style = MaterialTheme.typography.bodyMedium, color = AppColors.Info)
            Text(
                "${data.workoutCount} workout${if (data.workoutCount == 1) "" else "s"} this month  |  $total total  |  " +
                    "$streak-week streak (3 sessions per week)",
                style = MaterialTheme.typography.bodyMedium,
                color = AppColors.TextSecondary,
            )
        }
    }
}

@Composable
private fun Legend(color: Color, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(14.dp).background(color, RoundedCornerShape(4.dp)))
        Spacer(Modifier.size(5.dp))
        Text(text, style = MaterialTheme.typography.bodyMedium)
    }
}

// ------------------------------------------------------------------------------------------------ joint history

@Composable
private fun JointHistoryCard(records: List<com.personal.calisthenicsguide.data.SessionRecord>, zone: ZoneId) {
    val fmt = remember { DateTimeFormatter.ofPattern("EEE d MMM", Locale.ENGLISH) }
    val rated = records.filter { it.rating != null }
    Card(colors = CardDefaults.cardColors(containerColor = AppColors.Surface), shape = RoundedCornerShape(20.dp), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Joint health log", style = MaterialTheme.typography.titleMedium, color = AppColors.Accent)
            if (rated.isEmpty()) {
                Text("Ratings you give after each workout (1 = fine, 5 = very stiff) appear here.", style = MaterialTheme.typography.bodyMedium, color = AppColors.TextSecondary)
            } else {
                val advice = JointAdvice.evaluate(rated.first().rating)
                advice.messages.forEach { Text(it, color = AppColors.Warn, style = MaterialTheme.typography.bodyMedium) }
                Row(Modifier.fillMaxWidth()) {
                    Text("Date", Modifier.weight(1.6f), style = MaterialTheme.typography.bodyMedium, color = AppColors.TextSecondary)
                    listOf("Wrists", "Elbows", "Shoulders").forEach {
                        Text(it, Modifier.weight(1f), textAlign = TextAlign.Center, style = MaterialTheme.typography.bodyMedium, color = AppColors.TextSecondary)
                    }
                }
                rated.take(12).forEach { rec ->
                    val r = rec.rating ?: return@forEach
                    Row(Modifier.fillMaxWidth().heightIn(min = 40.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            fmt.format(Instant.ofEpochMilli(rec.session.endedAtMs).atZone(zone)) + " (${rec.session.day.label})",
                            Modifier.weight(1.6f),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        listOf(r.wrists, r.elbows, r.shoulders).forEach { v ->
                            Text(
                                "$v",
                                Modifier.weight(1f),
                                textAlign = TextAlign.Center,
                                fontWeight = FontWeight.Bold,
                                color = if (v > JointAdvice.STIFFNESS_THRESHOLD) AppColors.Danger else AppColors.Good,
                            )
                        }
                    }
                }
            }
        }
    }
}
