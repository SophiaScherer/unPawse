package com.example.unpawse.data

import android.content.Context
import com.example.unpawse.BuildConfig
import com.example.unpawse.data.apps.InstalledAppsProvider
import com.example.unpawse.data.apps.PackageManagerInstalledAppsProvider
import com.example.unpawse.data.capture.CaptureDatabase
import com.example.unpawse.data.capture.CaptureRepository
import com.example.unpawse.data.capture.PhotoStorage
import com.example.unpawse.data.export.ExportRepository
import com.example.unpawse.data.settings.SettingsRepository
import com.example.unpawse.data.usage.UsageRepository
import com.example.unpawse.ml.CatDetector
import com.example.unpawse.ml.sensitivityToMinConfidence
import com.example.unpawse.service.BlockOverlayController
import com.example.unpawse.service.BlockSession
import com.example.unpawse.service.DailySummaryWorker
import com.example.unpawse.service.FocusSession
import com.example.unpawse.service.ForegroundAppMonitor
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
    val installedAppsProvider: InstalledAppsProvider

    /** Gathers every store into one JSON document for Settings → Export data. */
    val exportRepository: ExportRepository

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

    override val installedAppsProvider: InstalledAppsProvider by lazy {
        PackageManagerInstalledAppsProvider(appContext)
    }

    override val exportRepository: ExportRepository by lazy {
        ExportRepository(
            settings = settingsRepository,
            usage = usageRepository,
            captures = captureRepository,
            contentResolver = appContext.contentResolver,
            appVersion = "${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})",
        )
    }

    override val resetRepository: ResetRepository by lazy {
        ResetRepository(
            usage = usageRepository,
            captures = captureRepository,
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
        )
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

    override val catDetectorMinConfidence: StateFlow<Float> by lazy {
        settingsRepository.sensitivity
            .map(::sensitivityToMinConfidence)
            .stateIn(appScope, SharingStarted.Eagerly, CatDetector.DEFAULT_MIN_CONFIDENCE)
    }

    override val earnedMinutesPerCat: StateFlow<Int> by lazy {
        settingsRepository.earnedMinutesPerCat
            .stateIn(appScope, SharingStarted.Eagerly, SettingsRepository.DEFAULT_EARNED_MINUTES_PER_CAT)
    }

    override val warningMinutes: StateFlow<Int> by lazy {
        settingsRepository.warningMinutes
            .stateIn(appScope, SharingStarted.Eagerly, SettingsRepository.DEFAULT_WARNING_MINUTES)
    }
}
