package com.example.unpawse.ui.home

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.unpawse.appContainer
import com.example.unpawse.data.capture.CaptureRepository
import com.example.unpawse.data.settings.SettingsRepository
import com.example.unpawse.data.usage.UsageRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

/** Streams today's usage + captures into [HomeUiState]; all shaping lives in [toHomeUiState]. */
class HomeViewModel(
    usageRepository: UsageRepository,
    captureRepository: CaptureRepository,
    settingsRepository: SettingsRepository,
) : ViewModel() {

    val uiState: StateFlow<HomeUiState> = combine(
        usageRepository.observeMonitoredApps(),
        usageRepository.observeTodayUsage(),
        captureRepository.observeCaptures(),
        settingsRepository.userName,
    ) { monitoredApps, todayUsage, captures, userName ->
        toHomeUiState(monitoredApps, todayUsage, captures, userName)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
        initialValue = HomeUiState.sample(),
    )

    companion object {
        private const val STOP_TIMEOUT_MILLIS = 5_000L

        fun factory(context: Context): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val container = context.appContainer()
                HomeViewModel(
                    container.usageRepository,
                    container.captureRepository,
                    container.settingsRepository,
                )
            }
        }
    }
}
