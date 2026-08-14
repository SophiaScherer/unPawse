package com.example.unpawse.ui.settings

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.unpawse.BuildConfig
import com.example.unpawse.appContainer
import com.example.unpawse.data.ResetRepository
import com.example.unpawse.data.capture.CaptureRepository
import com.example.unpawse.data.export.ExportRepository
import com.example.unpawse.data.export.ImportRepository
import com.example.unpawse.data.export.ImportResult
import com.example.unpawse.data.schedule.ScheduleRepository
import com.example.unpawse.data.schedule.ScheduleWindow
import com.example.unpawse.data.settings.SettingsRepository
import com.example.unpawse.data.usage.MonitoredApp
import com.example.unpawse.data.usage.UsageRepository
import com.example.unpawse.service.Notifications
import com.example.unpawse.service.OverlayPermission
import com.example.unpawse.service.UsageAccess
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate

/**
 * Backs the (stateless) [SettingsScreen] with persisted values from [SettingsRepository]. Wires
 * flows together only; the shaping lives in the pure [toSettingsUiState].
 *
 * Dark mode is intentionally *not* owned here — it is resolved against the system theme and drives
 * the whole app from `UnPawseApp`, which persists its own override through the same repository.
 */
class SettingsViewModel(
    private val settings: SettingsRepository,
    usageRepository: UsageRepository,
    scheduleRepository: ScheduleRepository,
    captureRepository: CaptureRepository,
    private val exportRepository: ExportRepository,
    private val importRepository: ImportRepository,
    private val resetRepository: ResetRepository,
    private val usageAccessGranted: () -> Boolean,
    private val overlayAccessGranted: () -> Boolean,
    private val notificationsGranted: () -> Boolean,
    versionName: String,
    versionCode: Int,
) : ViewModel() {

    /** Compile-time constants, so this never changes and is safe to seed the initial state with. */
    private val version = versionLabel(versionName, versionCode)

    /**
     * Both permissions are system-Settings toggles rather than runtime dialogs, so there's nothing
     * to observe — we re-read them whenever the screen resumes (see [refreshPermissions]).
     */
    private val permissions = MutableStateFlow(readPermissions())

    /** One-shot user-facing messages, mirroring `CameraViewModel`'s event channel. */
    private val _messages = Channel<String>(Channel.BUFFERED)
    val messages = _messages.receiveAsFlow()

    /** Re-entry guard for [importFrom]; see the note there. */
    private val importing = MutableStateFlow(false)

    // `combine` has typed overloads up to five flows, so the repository-backed scalar settings are
    // pre-combined into one holder rather than being added as top-level arguments below.
    private val settingsValues = combine(
        settings.sensitivity,
        settings.dailySummaryEnabled,
        settings.userName,
        settings.earnedMinutesPerCat,
        settings.warningMinutes,
    ) { sensitivity, dailySummary, userName, earnedMinutes, warningMinutes ->
        SettingsValues(sensitivity, dailySummary, userName, earnedMinutes, warningMinutes)
    }

    // The photo row's subtitle needs both a count and a measured size; pre-combined like the
    // settings scalars so the top-level `combine` below stays within its typed arity.
    private val photoStats = combine(
        captureRepository.observeCaptures(),
        captureRepository.observeStorageBytes(),
    ) { captures, bytes -> PhotoStats(captures.size, bytes) }

    // The Screen Time group summarises both halves of a limit — how much (per-app budgets) and when
    // (blocking windows) — so they travel together rather than each taking a top-level slot.
    private val limits = combine(
        usageRepository.observeMonitoredApps(),
        scheduleRepository.observeWindows(),
    ) { monitoredApps, windows -> Limits(monitoredApps, windows) }

    val uiState: StateFlow<SettingsUiState> = combine(
        settingsValues,
        limits,
        permissions,
        photoStats,
        // The fifth and last top-level slot; further settings go into `settingsValues` or a holder
        // beside it, not here.
        settings.reminderMinutes,
    ) { values, limitState, permissionState, photos, reminderMinutes ->
        toSettingsUiState(
            userName = values.userName,
            sensitivity = values.sensitivity,
            dailySummaryEnabled = values.dailySummary,
            earnedMinutesPerCat = values.earnedMinutesPerCat,
            warningMinutes = values.warningMinutes,
            reminderMinutes = reminderMinutes,
            photoCount = photos.count,
            photoStorageBytes = photos.bytes,
            monitoredApps = limitState.monitoredApps,
            scheduleWindows = limitState.scheduleWindows,
            usageAccessGranted = permissionState.usageAccess,
            overlayAccessGranted = permissionState.overlayAccess,
            notificationsGranted = permissionState.notifications,
            versionLabel = version,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
        // Shown for the one frame before DataStore's first read lands. The permissions are read
        // synchronously above, so seed them — otherwise the two access rows flash red "Required"
        // at a user who has in fact granted them.
        initialValue = SettingsUiState(
            usageAccessGranted = permissions.value.usageAccess,
            overlayAccessGranted = permissions.value.overlayAccess,
            notificationsGranted = permissions.value.notifications,
            versionLabel = version,
        ),
    )

    /** Re-reads both special permissions; call when the screen resumes (e.g. back from Settings). */
    fun refreshPermissions() {
        permissions.value = readPermissions()
    }

    private fun readPermissions() =
        PermissionState(usageAccessGranted(), overlayAccessGranted(), notificationsGranted())

    private data class PermissionState(
        val usageAccess: Boolean,
        val overlayAccess: Boolean,
        val notifications: Boolean,
    )

    private data class PhotoStats(val count: Int, val bytes: Long)

    private data class Limits(
        val monitoredApps: List<MonitoredApp>,
        val scheduleWindows: List<ScheduleWindow>,
    )

    private data class SettingsValues(
        val sensitivity: Float,
        val dailySummary: Boolean,
        val userName: String,
        val earnedMinutesPerCat: Int,
        val warningMinutes: Int,
    )

    fun setSensitivity(value: Float) = viewModelScope.launch { settings.setSensitivity(value) }

    fun setDailySummary(value: Boolean) = viewModelScope.launch { settings.setDailySummary(value) }

    fun setEarnedMinutesPerCat(value: Int) =
        viewModelScope.launch { settings.setEarnedMinutesPerCat(value) }

    fun setWarningMinutes(value: Int) = viewModelScope.launch { settings.setWarningMinutes(value) }

    fun setReminderMinutes(value: Int) = viewModelScope.launch { settings.setReminderMinutes(value) }

    /**
     * Writes the data export to the document the user picked, then reports the outcome. A file write
     * has no visible result of its own, so without the message an export is indistinguishable from
     * the dead row this replaced.
     */
    fun exportTo(uri: Uri) = viewModelScope.launch {
        val ok = exportRepository.exportTo(uri)
        _messages.send(if (ok) "Data exported" else "Couldn't write the export file")
    }

    /** Default filename offered by the picker. */
    fun exportFileName(): String = ExportRepository.defaultFileName(LocalDate.now())

    /**
     * Restores a picked export, replacing everything. [onFinished] runs only on success: leaving
     * Settings after a failure would take the explanation with it.
     */
    fun importFrom(uri: Uri, onFinished: () -> Unit) {
        // A second tap while the first import is mid-wipe would race it over the same stores.
        if (!importing.compareAndSet(expect = false, update = true)) return
        viewModelScope.launch {
            try {
                val result = importRepository.importFrom(uri)
                _messages.send(importMessage(result))
                if (result is ImportResult.Restored) onFinished()
            } finally {
                importing.value = false
            }
        }
    }

    /**
     * Erases every store. The caller is expected to have confirmed first, and to leave Settings
     * afterwards — the screen it returns to would otherwise be rendering data that no longer exists.
     */
    fun eraseEverything(onFinished: () -> Unit) = viewModelScope.launch {
        resetRepository.eraseEverything()
        _messages.send("All data deleted")
        onFinished()
    }

    /** Trimmed so trailing spaces don't produce a blank-looking name that still counts as "set". */
    fun setUserName(value: String) = viewModelScope.launch { settings.setUserName(value.trim()) }

    companion object {
        private const val STOP_TIMEOUT_MILLIS = 5_000L

        fun factory(context: Context): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val container = context.appContainer()
                val appContext = context.applicationContext
                SettingsViewModel(
                    settings = container.settingsRepository,
                    usageRepository = container.usageRepository,
                    scheduleRepository = container.scheduleRepository,
                    captureRepository = container.captureRepository,
                    exportRepository = container.exportRepository,
                    importRepository = container.importRepository,
                    resetRepository = container.resetRepository,
                    usageAccessGranted = { UsageAccess.isGranted(appContext) },
                    overlayAccessGranted = { OverlayPermission.isGranted(appContext) },
                    notificationsGranted = { Notifications.canPost(appContext) },
                    versionName = BuildConfig.VERSION_NAME,
                    versionCode = BuildConfig.VERSION_CODE,
                )
            }
        }
    }
}
