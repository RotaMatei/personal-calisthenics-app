package com.personal.calisthenicsguide.ui.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.personal.calisthenics.core.analytics.NextSessionAdvice
import com.personal.calisthenics.core.analytics.JointAdvice
import com.personal.calisthenics.core.analytics.Recovery
import com.personal.calisthenics.core.analytics.RecoveryStatus
import com.personal.calisthenics.core.analytics.Streaks
import com.personal.calisthenics.core.model.Grip
import com.personal.calisthenics.core.model.WorkoutDay
import com.personal.calisthenics.core.plan.Deload
import com.personal.calisthenics.core.plan.DayRotation
import com.personal.calisthenics.core.plan.PlanOptions
import com.personal.calisthenics.core.plan.SessionPlanner
import com.personal.calisthenicsguide.data.Repository
import com.personal.calisthenicsguide.data.SettingKeys
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.ZoneId

data class DashboardState(
    val today: LocalDate,
    val recovery: RecoveryStatus,
    val elapsedLabel: String?,
    val suggestedDay: WorkoutDay,
    val day: WorkoutDay,
    val dayIsOverridden: Boolean,
    val grip: Grip,
    val advice: NextSessionAdvice,
    val weekInBlock: Int,
    val blockNumber: Int,
    val isDeload: Boolean,
    val weeksUntilDeload: Int,
    val coldMode: Boolean,
    val estimateMinutes: IntRange,
    val totalSets: Int,
    val streakWeeks: Int,
    val sessionsThisWeek: Int,
    val checklist: Map<String, Boolean>,
    val options: PlanOptions,
)

@OptIn(ExperimentalCoroutinesApi::class)
class DashboardViewModel(private val repository: Repository) : ViewModel() {

    private val zone: ZoneId = ZoneId.systemDefault()

    /** Emits the current time now and every 30 seconds so the recovery clock keeps moving. */
    private val clock: Flow<Long> = flow {
        while (true) {
            emit(System.currentTimeMillis())
            delay(30_000L)
        }
    }

    private val dates: Flow<LocalDate> = clock.map { LocalDate.now(zone) }.distinctUntilChanged()

    val state: StateFlow<DashboardState?> = combine(
        repository.completedSessions,
        repository.settings,
        dates.flatMapLatest { repository.checklistFor(it) },
        clock,
    ) { sessions, settings, checklist, nowMs ->
        val today = LocalDate.now(zone)
        val last = sessions.maxByOrNull { it.session.endedAtMs }
        val suggested = DayRotation.next(last?.session?.day)
        val override = WorkoutDay.fromLabel(settings[SettingKeys.DAY_OVERRIDE])
        val day = override ?: suggested
        val blockStart = settings[SettingKeys.BLOCK_START]?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
            ?: Deload.normalizeStart(today)
        val deload = Deload.isDeloadWeek(blockStart, today)
        val cold = settings[SettingKeys.COLD_MODE] == "true"
        // Joint advice only applies to the day that follows the last session.
        val advice = JointAdvice.evaluate(last?.rating)
        val grip = advice.gripFor(day.pullGrip)
        val options = PlanOptions(day = day, deload = deload, coldMode = cold, pullGrip = grip)
        val plan = SessionPlanner.plan(options)
        val weekStart = com.personal.calisthenics.core.analytics.VolumeGuard.weekStart(today)
        val sessionDates = sessions.map { java.time.Instant.ofEpochMilli(it.session.endedAtMs).atZone(zone).toLocalDate() }
        val recovery = Recovery.status(last?.session?.endedAtMs, nowMs)
        DashboardState(
            today = today,
            recovery = recovery,
            elapsedLabel = last?.let { Recovery.formatElapsed(recovery.elapsedMs) },
            suggestedDay = suggested,
            day = day,
            dayIsOverridden = override != null && override != suggested,
            grip = grip,
            advice = advice,
            weekInBlock = Deload.weekInBlock(blockStart, today),
            blockNumber = Deload.blockNumber(blockStart, today),
            isDeload = deload,
            weeksUntilDeload = Deload.weeksUntilDeload(blockStart, today),
            coldMode = cold,
            estimateMinutes = plan.estimatedMinutes(),
            totalSets = plan.workItems.size,
            streakWeeks = Streaks.weeklyStreak(sessionDates, today),
            sessionsThisWeek = sessionDates.count { !it.isBefore(weekStart) },
            checklist = checklist,
            options = options,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    init {
        viewModelScope.launch { repository.ensureBlockStart(LocalDate.now(zone)) }
    }

    fun setChecklist(itemId: String, done: Boolean) {
        viewModelScope.launch { repository.setChecklist(LocalDate.now(zone), itemId, done) }
    }

    fun setColdMode(on: Boolean) {
        viewModelScope.launch { repository.putSetting(SettingKeys.COLD_MODE, on.toString()) }
    }

    /** Pass null to go back to the automatic A -> B -> C rotation. */
    fun overrideDay(day: WorkoutDay?) {
        viewModelScope.launch {
            repository.putSetting(SettingKeys.DAY_OVERRIDE, day?.label ?: "")
        }
    }

    class Factory(private val repository: Repository) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T = DashboardViewModel(repository) as T
    }
}
