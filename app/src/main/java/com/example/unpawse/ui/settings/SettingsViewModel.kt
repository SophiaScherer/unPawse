package com.example.unpawse.ui.settings

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.unpawse.appContainer
import com.example.unpawse.data.settings.SettingsRepository
import com.example.unpawse.data.usage.UsageRepository
import com.example.unpawse.service.OverlayPermission
import com.example.unpawse.service.UsageAccess
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * Backs the (stateless) [SettingsScreen] with persisted values from [SettingsRepository]. Replaces
 * the session-only `rememberSaveable` state that previously lived in the NavHost.
 *
 * Dark mode is intentionally *not* owned here — it is resolved against the system theme and drives
 * the whole app from `UnPawseApp`, which persists its own override through the same repository. The
 * remaining static labels (daily limit, break duration, confidence, etc.) come from
 * [SettingsUiState.sample] for now; later phases replace them with real data.
 */
class SettingsViewModel(
    private val settings: SettingsRepository,
    usageRepository: UsageRepository,
    private val usageAccessGranted: () -> Boolean,
    private val overlayAccessGranted: () -> Boolean,
) : ViewModel() {

    /**
     * Both permissions are system-Settings toggles rather than runtime dialogs, so there's nothing
     * to observe — we re-read them whenever the screen resumes (see [refreshPermissions]).
     */
    private val permissions = MutableStateFlow(readPermissions())

    // `combine` has typed overloads up to five flows; there are six sources, so the four
    // repository-backed scalar settings are pre-combined into one holder.
    private val settingsValues = combine(
        settings.sensitivity,
        settings.requireLivePhoto,
        settings.dailySummaryEnabled,
        settings.userName,
    ) { sensitivity, requireLivePhoto, dailySummary, userName ->
        SettingsValues(sensitivity, requireLivePhoto, dailySummary, userName)
    }

    val uiState: StateFlow<SettingsUiState> = combine(
        settingsValues,
        usageRepository.observeMonitoredApps(),
        permissions,
    ) { values, monitoredApps, permissionState ->
        SettingsUiState.sample().copy(
            sensitivity = values.sensitivity,
            requireLivePhoto = values.requireLivePhoto,
            dailySummaryEnabled = values.dailySummary,
            userName = values.userName,
            appLimitsSummary = monitoredAppsSummary(monitoredApps),
            usageAccessGranted = permissionState.usageAccess,
            overlayAccessGranted = permissionState.overlayAccess,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
        initialValue = SettingsUiState.sample(),
    )

    /** Re-reads both special permissions; call when the screen resumes (e.g. back from Settings). */
    fun refreshPermissions() {
        permissions.value = readPermissions()
    }

    private fun readPermissions() = PermissionState(usageAccessGranted(), overlayAccessGranted())

    private data class PermissionState(val usageAccess: Boolean, val overlayAccess: Boolean)

    private data class SettingsValues(
        val sensitivity: Float,
        val requireLivePhoto: Boolean,
        val dailySummary: Boolean,
        val userName: String,
    )

    fun setSensitivity(value: Float) = viewModelScope.launch { settings.setSensitivity(value) }

    fun setRequireLivePhoto(value: Boolean) =
        viewModelScope.launch { settings.setRequireLivePhoto(value) }

    fun setDailySummary(value: Boolean) = viewModelScope.launch { settings.setDailySummary(value) }

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
                    usageAccessGranted = { UsageAccess.isGranted(appContext) },
                    overlayAccessGranted = { OverlayPermission.isGranted(appContext) },
                )
            }
        }
    }
}
