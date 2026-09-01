package com.example.unpawse.ui.stats

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.unpawse.appContainer
import com.example.unpawse.data.apps.DeviceUsageProvider
import com.example.unpawse.data.apps.InstalledApp
import com.example.unpawse.data.apps.InstalledAppsProvider
import com.example.unpawse.data.apps.platformCategories
import com.example.unpawse.data.capture.CaptureRepository
import com.example.unpawse.data.settings.SettingsRepository
import com.example.unpawse.data.unlocks.DailyUnlocks
import com.example.unpawse.data.unlocks.UnlockRepository
import com.example.unpawse.data.usage.DailyUsage
import com.example.unpawse.data.usage.MonitoredApp
import com.example.unpawse.data.usage.UsageRepository
import com.example.unpawse.data.usage.UsageScope
import com.example.unpawse.data.usage.UsageSeries
import com.example.unpawse.data.usage.deviceUsageSeries
import com.example.unpawse.data.usage.trackedUsageSeries
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate

/**
 * Streams two weeks of usage, all captures and this fortnight's unlock counts into [StatsUiState];
 * shaping lives in [toStatsUiState].
 *
 * The per-day streams are pre-combined into [StatsHistory] rather than being passed as more
 * top-level `combine` arguments. `combine`'s typed overloads stop at five, and `SettingsViewModel`
 * already sits at that ceiling — collapsing early here means adding another source later is a change
 * to one private data class instead of a rewrite. The usage scope takes the fifth and last top-level
 * slot; the device reads behind [UsageScope.ALL] went into the holder, which is what it is for.
 */
class StatsViewModel(
    usageRepository: UsageRepository,
    captureRepository: CaptureRepository,
    unlockRepository: UnlockRepository,
    userName: Flow<String>,
    private val settingsRepository: SettingsRepository,
    private val installedAppsProvider: InstalledAppsProvider,
    private val deviceUsageProvider: DeviceUsageProvider,
    /** Injected so the series and the mapper agree on today, as `UsageRepository` does for rollover. */
    private val today: () -> LocalDate = LocalDate::now,
) : ViewModel() {

    /** The day-keyed series behind the charts and badges, gathered so the combine stays narrow. */
    private data class StatsHistory(
        val recentUsage: List<DailyUsage>,
        val unlocks: List<DailyUnlocks>,
        val allUsage: List<DailyUsage>,
        val device: DeviceUsageSnapshot?,
    )

    /**
     * The two device reads behind [UsageScope.ALL], held together for the same reason
     * `AppPickerViewModel` holds its pair — the top-level `combine` is at its five-argument ceiling.
     *
     * A null holder means "not read yet"; a null [secondsByDateAndPackage] inside one means "read,
     * and there is no usage access". Both render as an unmeasurable scope, but only the second is
     * final, which is why the load is idempotent and a resume re-runs it.
     */
    private data class DeviceUsageSnapshot(
        val secondsByDateAndPackage: Map<String, Map<String, Long>>?,
        val installed: List<InstalledApp>,
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

    /**
     * Read on demand rather than at construction: a user who never leaves the tracked scope should
     * pay neither the `PackageManager` sweep nor the fourteen per-day binder calls behind it.
     */
    private val deviceUsage = MutableStateFlow<DeviceUsageSnapshot?>(null)

    init {
        viewModelScope.launch { allUsage.value = usageRepository.allUsage() }
        // Triggered by the stored value, not by the user touching the chips: a cold open with ALL
        // already saved has to fetch its own figures.
        viewModelScope.launch {
            if (settingsRepository.usageScope.first() == UsageScope.ALL) loadDeviceUsage()
        }
    }

    private val history = combine(
        usageRepository.observeRecentUsage(STATS_HISTORY_DAYS),
        unlockRepository.observeRecentUnlocks(STATS_HISTORY_DAYS),
        allUsage,
        deviceUsage,
        ::StatsHistory,
    )

    val uiState: StateFlow<StatsUiState> = combine(
        usageRepository.observeMonitoredApps(),
        history,
        captureRepository.observeCaptures(),
        userName,
        settingsRepository.usageScope,
    ) { monitoredApps, history, captures, name, scope ->
        // Read once per emission so the series and the mapper cannot straddle midnight differently.
        val day = today()
        toStatsUiState(
            monitoredApps = monitoredApps,
            recentUsage = history.recentUsage,
            captures = captures,
            unlocks = history.unlocks,
            userName = name,
            // Before the one-shot read lands, fall back to the chart window rather than an empty
            // list: a badge briefly missing is better than one briefly claiming to be un-earned.
            allUsage = history.allUsage.ifEmpty { history.recentUsage },
            today = day,
            scope = scope,
            series = seriesFor(scope, monitoredApps, history, day),
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
        // The nothing-yet state, not the mockup: this seed is what a cold open renders.
        initialValue = StatsUiState.empty(),
    )

    /**
     * Which store the scoped figures come from.
     *
     * Null — reachable only in [UsageScope.ALL] — means the scope has nothing to measure, and the
     * mapper blanks every scoped figure for it. A snapshot still in flight reports the same way on
     * purpose: a chart drawn from an empty map for one frame would be zeroes posing as measurements,
     * which is the failure the whole null-versus-empty distinction exists to prevent.
     */
    private fun seriesFor(
        scope: UsageScope,
        monitoredApps: List<MonitoredApp>,
        history: StatsHistory,
        day: LocalDate,
    ): UsageSeries? = when (scope) {
        UsageScope.TRACKED -> trackedUsageSeries(history.recentUsage, monitoredApps, day)
        UsageScope.ALL -> history.device?.secondsByDateAndPackage?.let { byDate ->
            deviceUsageSeries(
                secondsByDateAndPackage = byDate,
                platformCategories = history.device.installed.platformCategories(),
                monitoredApps = monitoredApps,
                today = day,
            )
        }
    }

    fun onScopeChange(scope: UsageScope) {
        viewModelScope.launch {
            settingsRepository.setUsageScope(scope)
            // Loading here as well as in `init` is what makes the first tap on "All apps" show
            // figures, rather than an empty card waiting for a resume to fill it.
            if (scope == UsageScope.ALL) loadDeviceUsage()
        }
    }

    /**
     * Re-reads the platform's figures, which is how granting usage access takes effect on return
     * without re-entering the screen — the app-op isn't observable, so the Route calls this on
     * resume. Mirrors `AppPickerViewModel.refresh()`.
     *
     * Deliberately a no-op in tracked scope: nothing on the screen reads the platform there, so a
     * resume must not cost a `PackageManager` sweep.
     */
    fun refresh() {
        viewModelScope.launch {
            if (settingsRepository.usageScope.first() == UsageScope.ALL) loadDeviceUsage()
        }
    }

    private suspend fun loadDeviceUsage() {
        deviceUsage.value = DeviceUsageSnapshot(
            secondsByDateAndPackage = deviceUsageProvider.dailySecondsByDate(STATS_HISTORY_DAYS.toInt()),
            // Only ever the donut's categories, so it rides along with the figures rather than
            // living in its own flow — the installed list can't usefully change while Stats is open.
            installed = installedAppsProvider.installedApps(),
        )
    }

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
                    container.settingsRepository,
                    container.installedAppsProvider,
                    container.deviceUsageProvider,
                )
            }
        }
    }
}
