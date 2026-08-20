package com.example.unpawse.data

import android.content.Context
import com.example.unpawse.BuildConfig
import com.example.unpawse.data.apps.DeviceUsageProvider
import com.example.unpawse.data.apps.InstalledAppsProvider
import com.example.unpawse.data.apps.PackageManagerInstalledAppsProvider
import com.example.unpawse.data.apps.UsageStatsDeviceUsageProvider
import com.example.unpawse.data.capture.CaptureDatabase
import com.example.unpawse.data.capture.CaptureRepository
import com.example.unpawse.data.capture.PhotoStorage
import com.example.unpawse.data.export.ExportRepository
import com.example.unpawse.data.export.ImportRepository
import com.example.unpawse.data.schedule.ScheduleRepository
import com.example.unpawse.data.schedule.ScheduleWindow
import com.example.unpawse.data.settings.SettingsRepository
import com.example.unpawse.data.unlocks.UnlockRepository
import com.example.unpawse.data.usage.UsageRepository
import com.example.unpawse.ml.CatDetector
import com.example.unpawse.ml.sensitivityToMinConfidence
import com.example.unpawse.service.BlockOverlayController
import com.example.unpawse.service.BlockSession
import com.example.unpawse.service.DailySummaryWorker
import com.example.unpawse.service.FocusSession
import com.example.unpawse.service.ForegroundAppMonitor
import com.example.unpawse.service.ScheduleGate
import com.example.unpawse.service.UsageStatsForegroundAppMonitor
import com.example.unpawse.service.UsageTracker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * Application-scoped dependency graph. Owns the single instances of the database, repositories, and
 * derived app-wide state, replacing the per-ViewModel `factory(context)` wiring that each used to
 * rebuild independently. This is the manual-DI seam a framework (Hilt) would later replace; see
 * [DefaultAppContainer] for the concrete graph and [com.example.unpawse.UnPawseApplication] for its
 * lifetime. Kept as an interface so tests can supply fakes.
 */
interface AppContainer {
    val captureRepository: CaptureRepository
    val settingsRepository: SettingsRepository
    val usageRepository: UsageRepository
    val scheduleRepository: ScheduleRepository

    /**
     * Device-unlock counts, behind the Stats "Unlocks" card. Written by the monitor service's
     * `UnlockReceiver`, read by `StatsViewModel`.
     */
    val unlockRepository: UnlockRepository
    val installedAppsProvider: InstalledAppsProvider

    /**
     * The platform's own per-app usage figures, behind the App Picker's "most used" sort. Separate
     * from [usageRepository] on purpose: that one knows only about apps already being monitored.
     */
    val deviceUsageProvider: DeviceUsageProvider

    /** Gathers every store into one bundle for Settings → Export data. */
    val exportRepository: ExportRepository

    /** Restores such a bundle for Settings → Import data, replacing everything. */
    val importRepository: ImportRepository

    /** Erases every store for Settings → Delete history. */
    val resetRepository: ResetRepository
    val foregroundAppMonitor: ForegroundAppMonitor

    /**
     * Singleton so `UsageMonitorService` can drive it while the UI observes
     * [UsageTracker.blockRequired] — both sides must share one instance.
     */
    val usageTracker: UsageTracker

    /**
     * Singleton so the service can raise the block and the reward loop can later dismiss it.
     * Main-thread only.
     */
    val blockOverlayController: BlockOverlayController

    /**
     * Which app the user owes a cat photo for. Shared so the service can arm it and the camera can
     * settle it.
     */
    val blockSession: BlockSession

    /**
     * The running focus session (if any). Shared so the Home UI can start/stop/observe it while the
     * enforcement service reads it to hard-block monitored apps.
     */
    val focusSession: FocusSession

    /**
     * Decides whether a schedule window is blocking an app right now. Shared so the enforcement
     * service reads the same windows the Schedules UI edits.
     */
    val scheduleGate: ScheduleGate

    /**
     * The [CatDetector] confidence gate, derived live from the Settings sensitivity slider. Held
     * app-wide (rather than per detector) so a settings change takes effect without recreating the
     * camera pipeline; the detector reads `.value` on each capture.
     */
    val catDetectorMinConfidence: StateFlow<Float>

    /**
     * How many minutes one verified cat buys back, from the Settings stepper. Held app-wide for the
     * same reason as [catDetectorMinConfidence]: the camera reads `.value` at the moment it credits,
     * so a change applies to the next capture without rebuilding anything.
     */
    val earnedMinutesPerCat: StateFlow<Int>

    /**
     * Minutes of remaining budget at which to warn before a block. Held app-wide for the same
     * reason as the two above: [UsageTracker] reads `.value` on each tick and stays free of
     * DataStore.
     */
    val warningMinutes: StateFlow<Int>

    /** How often to remind the user while they sit in a limited app; 0 is off. */
    val reminderMinutes: StateFlow<Int>

    /**
     * Every blocking window, mirrored app-wide so [scheduleGate] can read the current set on each
     * enforcement tick without holding a subscription of its own.
     */
    val scheduleWindows: StateFlow<List<ScheduleWindow>>
}

/** Production [AppContainer]; builds every dependency lazily off the singleton Room database. */
class DefaultAppContainer(context: Context) : AppContainer {

    private val appContext = context.applicationContext

    /** Lives as long as the process; hosts the derived app-wide flows below. */
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val database by lazy { CaptureDatabase.getInstance(appContext) }

    override val captureRepository: CaptureRepository by lazy {
        CaptureRepository(database.captureDao(), PhotoStorage(appContext))
    }

    override val settingsRepository: SettingsRepository by lazy {
        SettingsRepository(appContext)
    }

    override val usageRepository: UsageRepository by lazy {
        UsageRepository(database.usageDao())
    }

    override val scheduleRepository: ScheduleRepository by lazy {
        ScheduleRepository(database.scheduleDao())
    }

    // `by lazy` like everything else here, and deliberately NOT one of the eager exceptions below:
    // its first read is a UI read or a receiver write, never a blocking decision made in the same
    // expression that creates it.
    override val unlockRepository: UnlockRepository by lazy {
        UnlockRepository(database.unlockDao())
    }

    override val installedAppsProvider: InstalledAppsProvider by lazy {
        PackageManagerInstalledAppsProvider(appContext)
    }

    // Lazy like its neighbour, and deliberately not one of the eager exceptions below: its first
    // read is a UI read, never a blocking decision taken in the expression that creates it.
    override val deviceUsageProvider: DeviceUsageProvider by lazy {
        UsageStatsDeviceUsageProvider(appContext)
    }

    override val exportRepository: ExportRepository by lazy {
        ExportRepository(
            settings = settingsRepository,
            usage = usageRepository,
            unlocks = unlockRepository,
            schedules = scheduleRepository,
            captures = captureRepository,
            contentResolver = appContext.contentResolver,
            appVersion = "${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})",
        )
    }

    // Lazy like the rest: its first read is a user-initiated row tap, never a blocking decision
    // taken in the expression that builds it.
    override val importRepository: ImportRepository by lazy {
        ImportRepository(
            usage = usageRepository,
            unlocks = unlockRepository,
            schedules = scheduleRepository,
            captures = captureRepository,
            reset = resetRepository,
            applySettings = settingsRepository::applyImported,
            openDocument = appContext.contentResolver::openInputStream,
        )
    }

    override val resetRepository: ResetRepository by lazy {
        ResetRepository(
            usage = usageRepository,
            schedules = scheduleRepository,
            captures = captureRepository,
            unlocks = unlockRepository,
            focusSession = focusSession,
            blockSession = blockSession,
            clearSettings = settingsRepository::clearAll,
        )
    }

    override val foregroundAppMonitor: ForegroundAppMonitor by lazy {
        UsageStatsForegroundAppMonitor(appContext)
    }

    override val usageTracker: UsageTracker by lazy {
        UsageTracker(
            usageRepository,
            foregroundAppMonitor,
            focusSession = focusSession,
            warningMinutes = { warningMinutes.value },
            scheduleBlock = scheduleGate::activeWindowFor,
        )
    }

    override val scheduleGate: ScheduleGate by lazy {
        ScheduleGate(windows = { scheduleWindows.value })
    }

    override val blockOverlayController: BlockOverlayController by lazy {
        BlockOverlayController(appContext)
    }

    override val blockSession: BlockSession by lazy { BlockSession() }

    override val focusSession: FocusSession by lazy { FocusSession() }

    init {
        // Restore a focus session that was mid-run when the process died, then keep DataStore in sync
        // with every start/stop so the next process can restore it too.
        appScope.launch {
            focusSession.restore(settingsRepository.focusEndMillis.first())
            focusSession.endTimeMillis.collect { settingsRepository.setFocusEndMillis(it) }
        }

        // Keep the recap job in step with its toggle. Owning this here rather than in the Settings
        // ViewModel means the schedule is correct even for a process that never opens Settings, and
        // leaves the UI with nothing to know about WorkManager.
        appScope.launch {
            settingsRepository.dailySummaryEnabled.collect { enabled ->
                if (enabled) {
                    DailySummaryWorker.schedule(appContext)
                } else {
                    DailySummaryWorker.cancel(appContext)
                }
            }
        }
    }

    // --- Settings mirrored as app-wide state -------------------------------------------------
    //
    // Deliberately NOT `by lazy`, unlike everything above. `stateIn` seeds the flow with the
    // *default* and only replaces it once DataStore's first emission lands, so a lazy property
    // whose first read is also the read that matters returns the default forever after — the
    // value arrives microseconds too late.
    //
    // That is not hypothetical: every one of these is consumed as a `() -> T` lambda whose first
    // invocation is the decision itself, so the camera granted `BONUS_MINUTES_PER_CAT` instead of
    // the user's chosen minutes, and judged the first photo of each process against the default
    // 0.7 gate instead of their sensitivity setting. Both were reproduced on-device.
    //
    // Initialising here means collection starts in `Application.onCreate`, long before any UI can
    // ask — the launch-into-camera path from the block overlay included.

    override val catDetectorMinConfidence: StateFlow<Float> =
        settingsRepository.sensitivity
            .map(::sensitivityToMinConfidence)
            .stateIn(appScope, SharingStarted.Eagerly, CatDetector.DEFAULT_MIN_CONFIDENCE)

    override val earnedMinutesPerCat: StateFlow<Int> =
        settingsRepository.earnedMinutesPerCat
            .stateIn(appScope, SharingStarted.Eagerly, SettingsRepository.DEFAULT_EARNED_MINUTES_PER_CAT)

    override val warningMinutes: StateFlow<Int> =
        settingsRepository.warningMinutes
            .stateIn(appScope, SharingStarted.Eagerly, SettingsRepository.DEFAULT_WARNING_MINUTES)

    override val reminderMinutes: StateFlow<Int> =
        settingsRepository.reminderMinutes
            .stateIn(appScope, SharingStarted.Eagerly, SettingsRepository.DEFAULT_REMINDER_MINUTES)

    /**
     * Same eager treatment, and for the same reason as the settings above: `ScheduleGate` reads
     * `.value` on every enforcement tick, and the first read is a real blocking decision. A lazy
     * property would answer with the empty seed until Room's first emission landed — which is
     * exactly the window in which someone opens a blocked app.
     */
    override val scheduleWindows: StateFlow<List<ScheduleWindow>> =
        scheduleRepository.observeWindows()
            .stateIn(appScope, SharingStarted.Eagerly, emptyList())
}
