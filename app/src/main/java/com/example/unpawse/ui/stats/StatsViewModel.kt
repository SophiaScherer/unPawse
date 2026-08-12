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
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

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
) : ViewModel() {

    /** The day-keyed series behind the charts, gathered so the top-level combine stays narrow. */
    private data class StatsHistory(
        val recentUsage: List<DailyUsage>,
        val unlocks: List<DailyUnlocks>,
    )

    private val history = combine(
        usageRepository.observeRecentUsage(STATS_HISTORY_DAYS),
        unlockRepository.observeRecentUnlocks(STATS_HISTORY_DAYS),
        ::StatsHistory,
    )

    val uiState: StateFlow<StatsUiState> = combine(
        usageRepository.observeMonitoredApps(),
        history,
        captureRepository.observeCaptures(),
    ) { monitoredApps, history, captures ->
        toStatsUiState(monitoredApps, history.recentUsage, captures, history.unlocks)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
        initialValue = StatsUiState.sample(),
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
                )
            }
        }
    }
}
