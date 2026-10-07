package com.example.unpawse.ui.home

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.unpawse.appContainer
import com.example.unpawse.data.capture.CaptureRepository
import com.example.unpawse.data.time.dates
import com.example.unpawse.data.usage.DailyUsage
import com.example.unpawse.data.usage.UsageRepository
import com.example.unpawse.service.FocusSession
import com.example.unpawse.service.OverlayPermission
import com.example.unpawse.service.UsageAccess
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import java.time.LocalDate
import java.time.ZonedDateTime
import java.time.LocalTime
import java.time.ZoneId

/** Streams today's usage + captures into [HomeUiState]; all shaping lives in [toHomeUiState]. */
@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModel(
    usageRepository: UsageRepository,
    captureRepository: CaptureRepository,
    userName: Flow<String>,
    private val focusSession: FocusSession,
    private val usageAccessGranted: () -> Boolean,
    private val overlayAccessGranted: () -> Boolean,
    /** The container's shared clock; its date keys the usage query and its time the greeting. */
    clockTicks: Flow<ZonedDateTime>,
    private val nowMillis: () -> Long,
) : ViewModel() {

    /** One day's usage, carried with the date it was queried for so the mapper can't use another. */
    private data class HomeDay(val time: LocalTime, val zone: ZoneId, val date: LocalDate, val usage: List<DailyUsage>)

    // Switches the query at each local midnight. Binding it once, as this used to, left Home on
    // yesterday's figures for the life of the ViewModel while enforcement had already rolled over.
    private val day: Flow<HomeDay> = combine(
        clockTicks,
        clockTicks.dates().flatMapLatest { date ->
            usageRepository.observeUsageForDate(date).map { date to it }
        },
    ) { now, (date, usage) -> HomeDay(now.toLocalTime(), now.zone, date, usage) }

    /**
     * Neither special permission is observable — both are system-Settings toggles with no runtime
     * dialog — so a re-read on resume is the only source (see [refreshPermissions]), the same
     * arrangement `SettingsViewModel` uses for the very same two checks.
     */
    private val permissions = MutableStateFlow(readProtection())

    val uiState: StateFlow<HomeUiState> = combine(
        usageRepository.observeMonitoredApps(),
        day,
        captureRepository.observeCaptures(),
        userName,
        // The fifth and last top-level slot; a sixth flow goes into a holder rather than here, the
        // arity rule `SettingsViewModel` already lives under.
        permissions,
    ) { monitoredApps, day, captures, userName, protection ->
        toHomeUiState(
            monitoredApps = monitoredApps,
            todayUsage = day.usage,
            captures = captures,
            userName = userName,
            protection = protection,
            today = day.date,
            zone = day.zone,
            time = day.time,
        )
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
    val focus: StateFlow<FocusCardState> = focusSession.endTimeMillis.flatMapLatest { end ->
        if (end == null) {
            flowOf(FocusCardState.Inactive)
        } else {
            flow {
                while (true) {
                    val remaining = end - nowMillis()
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
                    container.settingsRepository.userName,
                    container.focusSession,
                    // Lambdas rather than a Context, so the ViewModel body stays JVM-testable.
                    usageAccessGranted = { UsageAccess.isGranted(appContext) },
                    overlayAccessGranted = { OverlayPermission.isGranted(appContext) },
                    clockTicks = container.clockTicks,
                    nowMillis = container.dayClock::nowMillis,
                )
            }
        }
    }
}
