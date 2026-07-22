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
import com.example.unpawse.service.FocusSession
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn

/** Streams today's usage + captures into [HomeUiState]; all shaping lives in [toHomeUiState]. */
class HomeViewModel(
    usageRepository: UsageRepository,
    captureRepository: CaptureRepository,
    settingsRepository: SettingsRepository,
    private val focusSession: FocusSession,
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

    /**
     * Live focus-card state. While a session runs, an inner ticker re-emits every second so the
     * countdown updates; `flatMapLatest` cancels it the moment the session ends or restarts.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    val focus: StateFlow<FocusCardState> = focusSession.endTimeMillis.flatMapLatest { end ->
        if (end == null) {
            flowOf(FocusCardState.Inactive)
        } else {
            flow {
                while (true) {
                    val remaining = end - System.currentTimeMillis()
                    if (remaining <= 0) {
                        emit(FocusCardState.Inactive)
                        break
                    }
                    emit(FocusCardState(active = true, remainingLabel = formatCountdown(remaining)))
                    delay(TICK_MILLIS)
                }
            }
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
        initialValue = FocusCardState.Inactive,
    )

    fun startFocus(durationMinutes: Int) = focusSession.start(durationMinutes)

    fun stopFocus() = focusSession.stop()

    companion object {
        private const val STOP_TIMEOUT_MILLIS = 5_000L
        private const val TICK_MILLIS = 1_000L

        fun factory(context: Context): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val container = context.appContainer()
                HomeViewModel(
                    container.usageRepository,
                    container.captureRepository,
                    container.settingsRepository,
                    container.focusSession,
                )
            }
        }
    }
}
