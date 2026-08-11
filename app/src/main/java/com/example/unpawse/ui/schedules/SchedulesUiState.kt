package com.example.unpawse.ui.schedules

import com.example.unpawse.data.schedule.EVERY_DAY_MASK
import com.example.unpawse.data.schedule.ScheduleRepository
import com.example.unpawse.data.schedule.WEEKDAYS_MASK

/**
 * Immutable UI state for the Schedules screen. [windows] is already sorted and formatted; the screen
 * just renders it. [appOptions] backs the editor's "Applies to" picker, so the sheet never has to
 * reach for the monitored-app list itself.
 */
data class SchedulesUiState(
    val windows: List<ScheduleItem> = emptyList(),
    val appOptions: List<ScheduleAppOption> = emptyList(),
    val isLoading: Boolean = true,
) {
    val activeCount: Int get() = windows.count { it.enabled }

    companion object {
        /** Preview-only fixture. Never build production state from this. */
        fun sample() = SchedulesUiState(
            isLoading = false,
            appOptions = listOf(
                ScheduleAppOption(null, ALL_APPS_LABEL),
                ScheduleAppOption("com.instagram.android", "Instagram"),
                ScheduleAppOption("com.zhiliaoapp.musically", "TikTok"),
            ),
            windows = listOf(
                ScheduleItem(
                    draft = ScheduleDraft(
                        id = 1,
                        label = "Bedtime",
                        startMinuteOfDay = 22 * 60,
                        endMinuteOfDay = 7 * 60,
                    ),
                    timeRange = "10:00 PM – 7:00 AM",
                    daysLabel = "Every day",
                    scopeLabel = ALL_APPS_LABEL,
                    isOvernight = true,
                ),
                ScheduleItem(
                    draft = ScheduleDraft(
                        id = 2,
                        label = "School hours",
                        packageName = "com.instagram.android",
                        startMinuteOfDay = 9 * 60,
                        endMinuteOfDay = 15 * 60,
                        daysMask = WEEKDAYS_MASK,
                    ),
                    timeRange = "9:00 AM – 3:00 PM",
                    daysLabel = "Weekdays",
                    scopeLabel = "Instagram",
                    isOvernight = false,
                ),
            ),
        )
    }
}

/**
 * One window as the list renders it: the display strings plus the [draft] the editor reopens with,
 * so tapping a row needs no second lookup.
 */
data class ScheduleItem(
    val draft: ScheduleDraft,
    val timeRange: String,
    val daysLabel: String,
    val scopeLabel: String,
    val isOvernight: Boolean,
) {
    val id: Long get() = draft.id
    val label: String get() = draft.label
    val enabled: Boolean get() = draft.enabled
}

/**
 * The editable shape of a window — what the sheet holds while the user is mid-edit, before anything
 * is written. An [id] of [ScheduleRepository.NEW_WINDOW_ID] means it hasn't been saved yet.
 */
data class ScheduleDraft(
    val id: Long = ScheduleRepository.NEW_WINDOW_ID,
    val label: String = "",
    val packageName: String? = null,
    val startMinuteOfDay: Int = DEFAULT_START_MINUTE,
    val endMinuteOfDay: Int = DEFAULT_END_MINUTE,
    val daysMask: Int = EVERY_DAY_MASK,
    val enabled: Boolean = true,
) {
    val isNew: Boolean get() = id == ScheduleRepository.NEW_WINDOW_ID

    /** The label to save: blank input falls back to a name rather than an empty row. */
    val effectiveLabel: String get() = label.trim().ifBlank { DEFAULT_LABEL }
}

/** One choice in the editor's "Applies to" picker; a null package means every monitored app. */
data class ScheduleAppOption(val packageName: String?, val label: String)

/** What a global window is called wherever scope is shown. */
const val ALL_APPS_LABEL = "All monitored apps"

/** Name given to a window the user didn't title. */
const val DEFAULT_LABEL = "Blocked time"

/** A new window starts as a bedtime, which is what most people are here for. */
const val DEFAULT_START_MINUTE = 22 * 60
const val DEFAULT_END_MINUTE = 7 * 60
