package com.personal.calisthenicsguide.ui.stats

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.personal.calisthenics.core.analytics.ChartPoint
import com.personal.calisthenics.core.analytics.ChartSeries
import com.personal.calisthenics.core.analytics.Heatmap
import com.personal.calisthenics.core.analytics.HeatmapMonth
import com.personal.calisthenics.core.analytics.JointAdvice
import com.personal.calisthenics.core.analytics.JointRating
import com.personal.calisthenics.core.analytics.Streaks
import com.personal.calisthenics.core.analytics.VolumeCheck
import com.personal.calisthenics.core.analytics.VolumeGuard
import com.personal.calisthenics.core.model.WorkoutDay
import com.personal.calisthenics.core.plan.Deload
import com.personal.calisthenicsguide.data.Repository
import com.personal.calisthenicsguide.data.SettingKeys
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId

data class JointEntry(val date: LocalDate, val day: WorkoutDay, val rating: JointRating)

data class StatsUi(
    val heatmap: HeatmapMonth,
    val canGoForward: Boolean,
    val streak: Int,
    val totalSessions: Int,
    val volume: VolumeCheck,
    val lSit: List<ChartPoint>,
    val planche: List<ChartPoint>,
    val pullups: List<ChartPoint>,
    val dips: List<ChartPoint>,
    /** Newest first. */
    val joints: List<JointEntry>,
    /** Advice derived from the most recent joint rating. */
    val advice: List<String>,
)

class StatsViewModel(repository: Repository) : ViewModel() {

    private val zone: ZoneId = ZoneId.systemDefault()
    private val month = MutableStateFlow(YearMonth.now(zone))

    fun shiftMonth(delta: Long) {
        val next = month.value.plusMonths(delta)
        if (!next.isAfter(YearMonth.now(zone))) month.value = next
    }

    val ui: StateFlow<StatsUi?> = combine(
        repository.completedSessions,
        repository.loggedSets,
        repository.settings,
        month,
    ) { sessions, sets, settings, shown ->
        val today = LocalDate.now(zone)
        val blockStart = settings[SettingKeys.BLOCK_START]?.let { runCatching { LocalDate.parse(it) }.getOrNull() } ?: Deload.normalizeStart(today)
        fun dateOf(ms: Long): LocalDate = Instant.ofEpochMilli(ms).atZone(zone).toLocalDate()
        val sessionDays = sessions.associate { dateOf(it.session.endedAtMs) to it.session.day }
        val weeks = VolumeGuard.weeklyVolumes(sets, zone)
        val volume = VolumeGuard.evaluate(weeks, VolumeGuard.weekStart(today)) { Deload.isDeloadWeek(blockStart, it) }
        val joints = sessions.mapNotNull { record ->
            record.rating?.let { JointEntry(dateOf(record.session.endedAtMs), record.session.day, it) }
        }
        StatsUi(
            heatmap = Heatmap.month(shown, sessionDays, blockStart, today),
            canGoForward = shown.isBefore(YearMonth.now(zone)),
            streak = Streaks.weeklyStreak(sessions.map { dateOf(it.session.endedAtMs) }, today),
            totalSessions = sessions.size,
            volume = volume,
            lSit = ChartSeries.maxHoldPerSession(sets, "l_sit"),
            planche = ChartSeries.maxHoldPerSession(sets, "planche_lean"),
            pullups = ChartSeries.totalRepsPerSession(sets, setOf("strict_pullups")),
            dips = ChartSeries.totalRepsPerSession(sets, setOf("parallel_bar_dips", "straight_bar_dips")),
            joints = joints,
            advice = JointAdvice.evaluate(sessions.firstOrNull()?.rating).messages,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
}
