package com.example.unpawse.ui.stats

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.unpawse.appContainer
import com.example.unpawse.data.capture.CaptureRepository
import com.example.unpawse.data.usage.UsageRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

/** Streams two weeks of usage + all captures into [StatsUiState]; shaping lives in [toStatsUiState]. */
class StatsViewModel(
    usageRepository: UsageRepository,
    captureRepository: CaptureRepository,
) : ViewModel() {

    val uiState: StateFlow<StatsUiState> = combine(
        usageRepository.observeMonitoredApps(),
        usageRepository.observeRecentUsage(STATS_HISTORY_DAYS),
        captureRepository.observeCaptures(),
    ) { monitoredApps, recentUsage, captures ->
        toStatsUiState(monitoredApps, recentUsage, captures)
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
                StatsViewModel(container.usageRepository, container.captureRepository)
            }
        }
    }
}
