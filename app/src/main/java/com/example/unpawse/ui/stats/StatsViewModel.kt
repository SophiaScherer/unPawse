package com.example.unpawse.ui.stats

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.unpawse.appContainer
import com.example.unpawse.data.capture.CaptureRepository
import com.example.unpawse.data.unlocks.DailyUnlocks
import com.example.unpawse.data.unlocks.UnlockRepository
import com.example.unpawse.data.usage.DailyUsage
import com.example.unpawse.data.usage.UsageRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * Streams two weeks of usage, all captures and this fortnight's unlock counts into [StatsUiState];
 * shaping lives in [toStatsUiState].
 *
 * The per-day streams are pre-combined into [StatsHistory] rather than being passed as more
 * top-level `combine` arguments. `combine`'s typed overloads stop at five, and `SettingsViewModel`
 * already sits at that ceiling — collapsing early here means adding another source later is a change
 * to one private data class instead of a rewrite.
 */
class StatsViewModel(
    usageRepository: UsageRepository,
    captureRepository: CaptureRepository,
    unlockRepository: UnlockRepository,
    userName: Flow<String>,
) : ViewModel() {

    /** The day-keyed series behind the charts and badges, gathered so the combine stays narrow. */
    private data class StatsHistory(
        val recentUsage: List<DailyUsage>,
        val unlocks: List<DailyUnlocks>,
        val allUsage: List<DailyUsage>,
    )

    /**
     * The whole usage history, for the achievement rules only.
     *
     * Read **once per screen entry** rather than observed. `UsageTracker` writes `daily_usage` about
     * once a second while a monitored app is in front, so a live full-history flow would re-deliver
     * and re-fold every row on every tick — after a year that is thousands of rows at 1 Hz, to
     * recompute facts that are by definition historical. Same one-shot pattern `AppPickerViewModel`
     * uses for the installed-app list. A badge earned during this visit therefore appears on the
     * next one, which is the right trade for keeping the hot path clean.
     */
    private val allUsage = MutableStateFlow<List<DailyUsage>>(emptyList())

    init {
        viewModelScope.launch { allUsage.value = usageRepository.allUsage() }
    }

    private val history = combine(
        usageRepository.observeRecentUsage(STATS_HISTORY_DAYS),
        unlockRepository.observeRecentUnlocks(STATS_HISTORY_DAYS),
        allUsage,
        ::StatsHistory,
    )

    val uiState: StateFlow<StatsUiState> = combine(
        usageRepository.observeMonitoredApps(),
        history,
        captureRepository.observeCaptures(),
        userName,
    ) { monitoredApps, history, captures, name ->
        toStatsUiState(
            monitoredApps = monitoredApps,
            recentUsage = history.recentUsage,
            captures = captures,
            unlocks = history.unlocks,
            userName = name,
            // Before the one-shot read lands, fall back to the chart window rather than an empty
            // list: a badge briefly missing is better than one briefly claiming to be un-earned.
            allUsage = history.allUsage.ifEmpty { history.recentUsage },
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
        // The nothing-yet state, not the mockup: this seed is what a cold open renders.
        initialValue = StatsUiState.empty(),
    )

    companion object {
        private const val STOP_TIMEOUT_MILLIS = 5_000L

        fun factory(context: Context): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val container = context.appContainer()
                StatsViewModel(
                    container.usageRepository,
                    container.captureRepository,
                    container.unlockRepository,
                    container.settingsRepository.userName,
                )
            }
        }
    }
}
