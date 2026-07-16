package com.example.unpawse.ui.apppicker

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.unpawse.appContainer
import com.example.unpawse.data.apps.InstalledApp
import com.example.unpawse.data.apps.InstalledAppsProvider
import com.example.unpawse.data.usage.UsageRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * Drives the app picker: joins the device's installed apps with the monitored-app rows and writes
 * the user's choices back to [UsageRepository].
 *
 * The installed-app list is loaded once (it can't change while the picker is open in any way that
 * matters) and held in a flow so it composes with the live monitored-apps stream; `null` means
 * "still loading".
 */
class AppPickerViewModel(
    private val usageRepository: UsageRepository,
    private val installedAppsProvider: InstalledAppsProvider,
) : ViewModel() {

    private val searchQuery = MutableStateFlow("")
    private val installedApps = MutableStateFlow<List<InstalledApp>?>(null)

    init {
        viewModelScope.launch { installedApps.value = installedAppsProvider.installedApps() }
    }

    val uiState: StateFlow<AppPickerUiState> = combine(
        installedApps,
        usageRepository.observeMonitoredApps(),
        searchQuery,
    ) { installed, monitored, query ->
        if (installed == null) {
            AppPickerUiState(searchQuery = query, isLoading = true)
        } else {
            AppPickerUiState(
                searchQuery = query,
                apps = toAppLimitItems(installed, monitored, query),
                isLoading = false,
            )
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
        initialValue = AppPickerUiState(),
    )

    fun onSearchChange(query: String) {
        searchQuery.value = query
    }

    /**
     * Switching an app on writes a monitored row with its current (default) limit. Switching it off
     * only clears the `enabled` flag — the row and its limit survive, so re-enabling restores the
     * budget the user previously chose instead of silently resetting it.
     */
    fun onToggleMonitored(item: AppLimitItem, monitored: Boolean) {
        viewModelScope.launch {
            if (monitored) {
                usageRepository.setLimit(
                    packageName = item.packageName,
                    appLabel = item.label,
                    dailyLimitMinutes = item.dailyLimitMinutes,
                    enabled = true,
                )
            } else {
                usageRepository.setEnabled(item.packageName, enabled = false)
            }
        }
    }

    /** Changing a limit implies the app is monitored (the stepper only shows for monitored rows). */
    fun onLimitChange(item: AppLimitItem, minutes: Int) {
        viewModelScope.launch {
            usageRepository.setLimit(
                packageName = item.packageName,
                appLabel = item.label,
                dailyLimitMinutes = minutes,
                enabled = true,
            )
        }
    }

    companion object {
        private const val STOP_TIMEOUT_MILLIS = 5_000L

        fun factory(context: Context): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val container = context.appContainer()
                AppPickerViewModel(container.usageRepository, container.installedAppsProvider)
            }
        }
    }
}
