package com.personal.calisthenicsguide.ui.player

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.personal.calisthenics.core.dashboard.DashboardLogic
import com.personal.calisthenics.core.model.WorkoutDay
import com.personal.calisthenics.core.plan.DayRotation
import com.personal.calisthenics.core.plan.Deload
import com.personal.calisthenics.core.plan.PlanOptions
import com.personal.calisthenics.core.plan.SessionPlan
import com.personal.calisthenics.core.plan.SessionPlanner
import com.personal.calisthenicsguide.data.Repository
import com.personal.calisthenicsguide.data.SettingKeys
import com.personal.calisthenicsguide.session.SessionController
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.ZoneId

/** What the Workout tab shows before a session starts. */
data class IdleUi(val options: PlanOptions, val plan: SessionPlan, val autoDay: WorkoutDay)

class PlayerViewModel(private val repository: Repository, val controller: SessionController) : ViewModel() {

    private val zone: ZoneId = ZoneId.systemDefault()
    private val dayOverride = MutableStateFlow<WorkoutDay?>(null)
    val selectedDay: StateFlow<WorkoutDay?> = dayOverride.asStateFlow()

    val coldMode: StateFlow<Boolean> = repository.settings
        .map { it[SettingKeys.COLD_MODE] == "1" }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    val idle: StateFlow<IdleUi?> = combine(repository.completedSessions, repository.settings, dayOverride) { sessions, settings, override ->
        val today = LocalDate.now(zone)
        val blockStart = settings[SettingKeys.BLOCK_START]?.let { runCatching { LocalDate.parse(it) }.getOrNull() } ?: Deload.normalizeStart(today)
        val last = sessions.firstOrNull()
        val options = DashboardLogic.nextOptions(
            lastDay = last?.session?.day,
            lastRating = last?.rating,
            deload = Deload.isDeloadWeek(blockStart, today),
            coldMode = settings[SettingKeys.COLD_MODE] == "1",
            dayOverride = override,
        )
        IdleUi(options, SessionPlanner.plan(options), DayRotation.next(last?.session?.day))
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun selectDay(day: WorkoutDay?) {
        dayOverride.value = day
    }

    fun setCold(on: Boolean) {
        viewModelScope.launch { repository.putSetting(SettingKeys.COLD_MODE, if (on) "1" else "0") }
    }

    fun start() {
        idle.value?.let { controller.start(it.options) }
    }
}
