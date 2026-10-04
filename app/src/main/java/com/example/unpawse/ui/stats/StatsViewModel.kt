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
import com.example.unpawse.data.time.dates
import com.example.unpawse.data.unlocks.DailyUnlocks
import com.example.unpawse.data.unlocks.UnlockRepository
import com.example.unpawse.data.usage.DailyUsage
import com.example.unpawse.data.usage.MonitoredApp
import com.example.unpawse.data.usage.UsageRepository
import com.example.unpawse.data.usage.UsageScope
import com.example.unpawse.data.usage.UsageSeries
import com.example.unpawse.data.usage.deviceUsageSeries
import com.example.unpawse.data.usage.firstMeasuredDay
import com.example.unpawse.data.usage.mergeUsageHistory
import com.example.unpawse.data.usage.trackedUsageSeries
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.ZoneId

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
@OptIn(ExperimentalCoroutinesApi::class)
class StatsViewModel(
    usageRepository: UsageRepository,
    captureRepository: CaptureRepository,
    unlockRepository: UnlockRepository,
    userName: Flow<String>,
    private val usageScope: Flow<UsageScope>,
    private val setUsageScope: suspend (UsageScope) -> Unit,
    private val installedAppsProvider: InstalledAppsProvider,
    private val deviceUsageProvider: DeviceUsageProvider,
    /**
     * The container's day source. The windows below are re-queried whenever it changes, so a Stats
     * screen left open across midnight moves on to the new day rather than charting the old one.
     */
    private val today: Flow<LocalDate>,
    private val zone: () -> ZoneId,
    private val nowMillis: () -> Long = System::currentTimeMillis,
) : ViewModel() {

    /** The day-keyed series behind the charts and badges, gathered so the combine stays narrow. */
    private data class StatsHistory(
        /** The day the windows were queried for; the mapper's `today`, so the two can't disagree. */
        val day: LocalDate,
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
        /** The last day of the window read; a snapshot for another day is treated as not read yet. */
        val endingOn: LocalDate,
        val readAtMillis: Long,
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
     * uses for the installed-app list. The live 14-day window is laid over it before the rules
     * run ([mergeUsageHistory]), so recent days are current even though this read is not.
     */
    private val allUsage = MutableStateFlow<List<DailyUsage>>(emptyList())

    /**
     * Read on demand rather than at construction: a user who never leaves the tracked scope should
     * pay neither the `PackageManager` sweep nor the fourteen per-day binder calls behind it.
     */
    private val deviceUsage = MutableStateFlow<DeviceUsageSnapshot?>(null)

    /** The day the screen is showing, once known; what a resume or a scope change reloads for. */
    private val currentDay = MutableStateFlow<LocalDate?>(null)

    /** The read in flight and the day it is for, so overlapping triggers don't each start one. */
    private var deviceLoad: Job? = null
    private var deviceLoadDay: LocalDate? = null

    init {
        viewModelScope.launch { allUsage.value = usageRepository.allUsage() }
    }

    private val history = today.flatMapLatest { day ->
        combine(
            usageRepository.observeRecentUsage(STATS_HISTORY_DAYS, endingOn = day),
            unlockRepository.observeRecentUnlocks(STATS_HISTORY_DAYS, endingOn = day),
            allUsage,
            deviceUsage,
        ) { recent, unlocks, all, device -> StatsHistory(day, recent, unlocks, all, device) }
            // Triggered by the stored scope, not by the user touching the toggle: a cold open with ALL
            // already saved has to fetch its own figures, and so does a new day.
            .onStart { requestDeviceUsage(day) }
    }

    val uiState: StateFlow<StatsUiState> = combine(
        usageRepository.observeMonitoredApps(),
        history,
        captureRepository.observeCaptures(),
        userName,
        usageScope,
    ) { monitoredApps, history, captures, name, scope ->
        // The day the windows were queried for, so the series and the mapper cannot straddle
        // midnight differently.
        val day = history.day
        // Before the one-shot read lands this is just the chart window: a badge briefly missing is
        // better than one briefly claiming to be un-earned.
        val allUsage = mergeUsageHistory(history.allUsage, history.recentUsage)
        toStatsUiState(
            monitoredApps = monitoredApps,
            recentUsage = history.recentUsage,
            captures = captures,
            unlocks = history.unlocks,
            userName = name,
            allUsage = allUsage,
            today = day,
            zone = zone(),
            scope = scope,
            series = seriesFor(scope, monitoredApps, history, allUsage, day),
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
        allUsage: List<DailyUsage>,
        day: LocalDate,
    ): UsageSeries? = when (scope) {
        UsageScope.TRACKED -> trackedUsageSeries(
            history.recentUsage,
            monitoredApps,
            day,
            measuredSince = firstMeasuredDay(allUsage, day),
        )
        UsageScope.ALL -> history.device?.takeIf { it.endingOn == day }?.secondsByDateAndPackage?.let { byDate ->
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
            setUsageScope(scope)
            // Loading here as well as on start is what makes the first tap on "All apps" show
            // figures, rather than an empty card waiting for a resume to fill it.
            currentDay.value?.let(::requestDeviceUsage)
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
        currentDay.value?.let(::requestDeviceUsage)
    }

    /**
     * Reads the platform's figures for [day] unless that is already happening or was just done.
     * Opening the screen fires both the flow's start and the Route's resume, which used to mean two
     * `PackageManager` sweeps and twenty-eight binder calls for one visit (audit PERF-06).
     */
    private fun requestDeviceUsage(day: LocalDate) {
        currentDay.value = day
        if (deviceLoad?.isActive == true) {
            if (deviceLoadDay == day) return
            deviceLoad?.cancel()
        }
        deviceLoadDay = day
        deviceLoad = viewModelScope.launch {
            if (usageScope.first() != UsageScope.ALL) return@launch
            if (deviceUsage.value?.isFreshFor(day) == true) return@launch
            loadDeviceUsage(day)
        }
    }

    /**
     * A recent read of [day] that found figures. One that found no usage access is never fresh: the
     * resume after granting it is exactly the read that must not be skipped.
     */
    private fun DeviceUsageSnapshot.isFreshFor(day: LocalDate): Boolean =
        endingOn == day &&
            secondsByDateAndPackage != null &&
            nowMillis() - readAtMillis in 0 until DEVICE_USAGE_FRESH_MILLIS

    private suspend fun loadDeviceUsage(day: LocalDate) {
        deviceUsage.value = DeviceUsageSnapshot(
            endingOn = day,
            readAtMillis = nowMillis(),
            secondsByDateAndPackage = deviceUsageProvider.dailySecondsByDate(
                STATS_HISTORY_DAYS.toInt(),
                endingOn = day,
            ),
            // Only ever the donut's categories, so it rides along with the figures rather than
            // living in its own flow — the installed list can't usefully change while Stats is open.
            installed = installedAppsProvider.installedApps(),
        )
    }

    companion object {
        private const val STOP_TIMEOUT_MILLIS = 5_000L
        private const val DEVICE_USAGE_FRESH_MILLIS = 10_000L

        fun factory(context: Context): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val container = context.appContainer()
                StatsViewModel(
                    container.usageRepository,
                    container.captureRepository,
                    container.unlockRepository,
                    container.settingsRepository.userName,
                    container.settingsRepository.usageScope,
                    container.settingsRepository::setUsageScope,
                    container.installedAppsProvider,
                    container.deviceUsageProvider,
                    today = container.clockTicks.dates(),
                    zone = container.dayClock::zone,
                    nowMillis = container.dayClock::nowMillis,
                )
            }
        }
    }
}
