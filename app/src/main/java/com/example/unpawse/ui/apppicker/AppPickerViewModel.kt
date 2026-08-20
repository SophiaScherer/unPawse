package com.example.unpawse.ui.apppicker

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
import com.example.unpawse.data.schedule.ScheduleRepository
import com.example.unpawse.data.usage.AppCategory
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
 * Both device reads — what is installed and how much each has been used — are one-shot and land in
 * a single [DeviceApps] holder; `null` means "still loading". They share a holder rather than a flow
 * each because the top-level `combine` is capped at five arguments (see AGENTS.md), and this keeps
 * it exactly there.
 */
class AppPickerViewModel(
    private val usageRepository: UsageRepository,
    private val installedAppsProvider: InstalledAppsProvider,
    private val deviceUsageProvider: DeviceUsageProvider,
    scheduleRepository: ScheduleRepository,
) : ViewModel() {

    private val searchQuery = MutableStateFlow("")
    private val sortOrder = MutableStateFlow(AppSort.ALPHABETICAL)
    private val deviceApps = MutableStateFlow<DeviceApps?>(null)

    init {
        viewModelScope.launch {
            deviceApps.value = DeviceApps(
                installed = installedAppsProvider.installedApps(),
                dailyAverageSeconds = deviceUsageProvider.dailyAverageSeconds(),
            )
        }
    }

    val uiState: StateFlow<AppPickerUiState> = combine(
        deviceApps,
        usageRepository.observeMonitoredApps(),
        searchQuery,
        scheduleRepository.observeWindows(),
        sortOrder,
    ) { device, monitored, query, windows, sort ->
        if (device == null) {
            AppPickerUiState(searchQuery = query, isLoading = true, sort = sort)
        } else {
            AppPickerUiState(
                searchQuery = query,
                apps = toAppLimitItems(
                    installed = device.installed,
                    monitored = monitored,
                    searchQuery = query,
                    scheduleWindows = windows,
                    dailyAverageSeconds = device.dailyAverageSeconds,
                    sort = sort,
                ),
                isLoading = false,
                sort = sort,
                usageAccessGranted = device.dailyAverageSeconds != null,
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

    fun onSortChange(sort: AppSort) {
        sortOrder.value = sort
    }

    /**
     * Re-reads the usage figures, which is how granting usage access takes effect on return without
     * re-entering the screen — the app-op isn't observable, so the Route calls this on resume.
     *
     * The installed list is deliberately left alone: it can't change while the picker is open in any
     * way that matters, and a PackageManager sweep on every resume would be wasted work.
     */
    fun refresh() {
        val loaded = deviceApps.value ?: return
        viewModelScope.launch {
            deviceApps.value = loaded.copy(dailyAverageSeconds = deviceUsageProvider.dailyAverageSeconds())
        }
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
                    // Only seeds a row that doesn't exist yet; a re-enable keeps the stored choice.
                    defaultCategory = item.category,
                )
            } else {
                usageRepository.setEnabled(item.packageName, enabled = false)
            }
        }
    }

    fun onCategoryChange(item: AppLimitItem, category: AppCategory) {
        viewModelScope.launch {
            usageRepository.setCategory(item.packageName, category)
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

    /** A null [minutes] clears the override, putting weekends back on the everyday budget. */
    fun onWeekendLimitChange(item: AppLimitItem, minutes: Int?) {
        viewModelScope.launch {
            usageRepository.setWeekendLimit(item.packageName, minutes)
        }
    }

    companion object {
        private const val STOP_TIMEOUT_MILLIS = 5_000L

        fun factory(context: Context): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val container = context.appContainer()
                AppPickerViewModel(
                    container.usageRepository,
                    container.installedAppsProvider,
                    container.deviceUsageProvider,
                    container.scheduleRepository,
                )
            }
        }
    }
}

/**
 * The two one-shot device reads the picker needs, held together so the ViewModel's `combine` stays
 * at its five-argument limit. [dailyAverageSeconds] is `null` when usage access is missing, which is
 * also what tells the UI to say so.
 */
private data class DeviceApps(
    val installed: List<InstalledApp>,
    val dailyAverageSeconds: Map<String, Long>?,
)
