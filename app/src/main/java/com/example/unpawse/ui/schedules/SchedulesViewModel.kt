package com.example.unpawse.ui.schedules

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.unpawse.appContainer
import com.example.unpawse.data.schedule.ScheduleRepository
import com.example.unpawse.data.usage.UsageRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * Drives the Schedules screen: joins the stored windows with the monitored apps (so a window can be
 * shown by app name rather than package) and writes edits back to [ScheduleRepository].
 *
 * Wires flows only; the shaping lives in the pure [toSchedulesUiState], as on every other screen.
 */
class SchedulesViewModel(
    private val scheduleRepository: ScheduleRepository,
    usageRepository: UsageRepository,
) : ViewModel() {

    val uiState: StateFlow<SchedulesUiState> = combine(
        scheduleRepository.observeWindows(),
        usageRepository.observeMonitoredApps(),
    ) { windows, monitoredApps ->
        toSchedulesUiState(windows, monitoredApps)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
        initialValue = SchedulesUiState(),
    )

    /** Inserts or updates, depending on whether the draft carries a saved id. */
    fun save(draft: ScheduleDraft) {
        viewModelScope.launch { scheduleRepository.save(draft.toWindow()) }
    }

    fun delete(id: Long) {
        viewModelScope.launch { scheduleRepository.delete(id) }
    }

    /**
     * Pausing a window keeps it and its times, so someone who wants their bedtime back next week
     * doesn't have to rebuild it — the same reasoning as switching an app off in the App Picker.
     */
    fun setEnabled(id: Long, enabled: Boolean) {
        viewModelScope.launch { scheduleRepository.setEnabled(id, enabled) }
    }

    companion object {
        private const val STOP_TIMEOUT_MILLIS = 5_000L

        fun factory(context: Context): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val container = context.appContainer()
                SchedulesViewModel(container.scheduleRepository, container.usageRepository)
            }
        }
    }
}
