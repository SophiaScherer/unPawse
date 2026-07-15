package com.example.unpawse.data

import android.content.Context
import com.example.unpawse.data.capture.CaptureDatabase
import com.example.unpawse.data.capture.CaptureRepository
import com.example.unpawse.data.capture.PhotoStorage
import com.example.unpawse.data.settings.SettingsRepository
import com.example.unpawse.ml.CatDetector
import com.example.unpawse.ml.sensitivityToMinConfidence
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

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

    /**
     * The [CatDetector] confidence gate, derived live from the Settings sensitivity slider. Held
     * app-wide (rather than per detector) so a settings change takes effect without recreating the
     * camera pipeline; the detector reads `.value` on each capture.
     */
    val catDetectorMinConfidence: StateFlow<Float>
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

    override val catDetectorMinConfidence: StateFlow<Float> by lazy {
        settingsRepository.sensitivity
            .map(::sensitivityToMinConfidence)
            .stateIn(appScope, SharingStarted.Eagerly, CatDetector.DEFAULT_MIN_CONFIDENCE)
    }
}
