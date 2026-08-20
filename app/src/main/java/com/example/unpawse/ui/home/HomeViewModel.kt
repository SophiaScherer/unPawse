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
import com.example.unpawse.service.OverlayPermission
import com.example.unpawse.service.UsageAccess
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
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
    private val usageAccessGranted: () -> Boolean,
    private val overlayAccessGranted: () -> Boolean,
) : ViewModel() {

    /**
     * Neither special permission is observable — both are system-Settings toggles with no runtime
     * dialog — so a re-read on resume is the only source (see [refreshPermissions]), the same
     * arrangement `SettingsViewModel` uses for the very same two checks.
     */
    private val permissions = MutableStateFlow(readProtection())

    val uiState: StateFlow<HomeUiState> = combine(
        usageRepository.observeMonitoredApps(),
        usageRepository.observeTodayUsage(),
        captureRepository.observeCaptures(),
        settingsRepository.userName,
        // The fifth and last top-level slot; a sixth flow goes into a holder rather than here, the
        // arity rule `SettingsViewModel` already lives under.
        permissions,
    ) { monitoredApps, todayUsage, captures, userName, protection ->
        toHomeUiState(monitoredApps, todayUsage, captures, userName, protection)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
        // Shown for the one frame before the repositories emit. Seeding this with `sample()` meant
        // every cold launch opened on the mockup's name, streak and screen time. The permissions are
        // read synchronously, so seed the real status too — otherwise Home flashes "protection is
        // off" at a user who granted everything.
        initialValue = HomeUiState.empty(permissions.value),
    )

    /** Re-reads both special permissions; call when the screen resumes (e.g. back from Settings). */
    fun refreshPermissions() {
        permissions.value = readProtection()
    }

    private fun readProtection() = resolveProtection(usageAccessGranted(), overlayAccessGranted())

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
                val appContext = context.applicationContext
                HomeViewModel(
                    container.usageRepository,
                    container.captureRepository,
                    container.settingsRepository,
                    container.focusSession,
                    // Lambdas rather than a Context, so the ViewModel body stays JVM-testable.
                    usageAccessGranted = { UsageAccess.isGranted(appContext) },
                    overlayAccessGranted = { OverlayPermission.isGranted(appContext) },
                )
            }
        }
    }
}
