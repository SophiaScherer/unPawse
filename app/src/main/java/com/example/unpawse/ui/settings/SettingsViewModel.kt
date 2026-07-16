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
) : ViewModel() {

    /**
     * Usage access is an app-op toggled in system Settings, so there's nothing to observe — we
     * re-read it whenever the screen resumes (see [refreshUsageAccess]).
     */
    private val usageAccess = MutableStateFlow(usageAccessGranted())

    val uiState: StateFlow<SettingsUiState> = combine(
        settings.sensitivity,
        settings.requireLivePhoto,
        settings.dailySummaryEnabled,
        usageRepository.observeMonitoredApps(),
        usageAccess,
    ) { sensitivity, requireLivePhoto, dailySummary, monitoredApps, accessGranted ->
        SettingsUiState.sample().copy(
            sensitivity = sensitivity,
            requireLivePhoto = requireLivePhoto,
            dailySummaryEnabled = dailySummary,
            appLimitsSummary = monitoredAppsSummary(monitoredApps),
            usageAccessGranted = accessGranted,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
        initialValue = SettingsUiState.sample(),
    )

    /** Re-reads the usage-access app-op; call when the screen resumes (e.g. back from Settings). */
    fun refreshUsageAccess() {
        usageAccess.value = usageAccessGranted()
    }

    fun setSensitivity(value: Float) = viewModelScope.launch { settings.setSensitivity(value) }

    fun setRequireLivePhoto(value: Boolean) =
        viewModelScope.launch { settings.setRequireLivePhoto(value) }

    fun setDailySummary(value: Boolean) = viewModelScope.launch { settings.setDailySummary(value) }

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
                )
            }
        }
    }
}
