package com.personal.calisthenicsguide.ui.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.personal.calisthenics.core.dashboard.DashboardLogic
import com.personal.calisthenics.core.dashboard.DashboardModel
import com.personal.calisthenics.core.plan.Deload
import com.personal.calisthenicsguide.data.Repository
import com.personal.calisthenicsguide.data.SettingKeys
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
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

class DashboardViewModel(private val repository: Repository) : ViewModel() {

    private val zone: ZoneId = ZoneId.systemDefault()

    /** Emits the current time every 30 seconds so the recovery clock keeps moving. */
    private val ticker = flow {
        while (true) {
            emit(System.currentTimeMillis())
            delay(30_000L)
        }
    }

    init {
        viewModelScope.launch { repository.ensureBlockStart(LocalDate.now(zone)) }
    }

    val model: StateFlow<DashboardModel?> = combine(repository.completedSessions, repository.settings, ticker) { sessions, settings, nowMs ->
        val today = DashboardLogic.toDate(nowMs, zone)
        val blockStart = settings[SettingKeys.BLOCK_START]?.let { runCatching { LocalDate.parse(it) }.getOrNull() } ?: Deload.normalizeStart(today)
        val last = sessions.firstOrNull()
        DashboardLogic.build(
            last = last?.session,
            lastRating = last?.rating,
            sessionDates = sessions.map { DashboardLogic.toDate(it.session.endedAtMs, zone) },
            blockStart = blockStart,
            today = today,
            nowMs = nowMs,
            coldMode = settings[SettingKeys.COLD_MODE] == "1",
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    @OptIn(ExperimentalCoroutinesApi::class)
    val checklist: StateFlow<Map<String, Boolean>> = ticker
        .map { DashboardLogic.toDate(it, zone) }
        .distinctUntilChanged()
        .flatMapLatest { repository.checklistFor(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    fun toggle(itemId: String, done: Boolean) {
        viewModelScope.launch { repository.setChecklist(LocalDate.now(zone), itemId, done) }
    }

    /** Starts a fresh 6-week block this week (for example after a break). */
    fun restartBlock() {
        viewModelScope.launch { repository.putSetting(SettingKeys.BLOCK_START, Deload.normalizeStart(LocalDate.now(zone)).toString()) }
    }
}
