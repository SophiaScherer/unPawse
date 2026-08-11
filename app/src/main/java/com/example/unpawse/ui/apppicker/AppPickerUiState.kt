package com.example.unpawse.ui.apppicker

import com.example.unpawse.data.usage.UNLIMITED_MINUTES
import com.example.unpawse.ui.components.steppedValue

/**
 * Immutable UI state for the app picker. [apps] is already filtered by [searchQuery] and sorted;
 * the screen just renders it.
 */
data class AppPickerUiState(
    val searchQuery: String = "",
    val apps: List<AppLimitItem> = emptyList(),
    val isLoading: Boolean = true,
) {
    val monitoredCount: Int get() = apps.count { it.monitored }

    companion object {
        fun sample() = AppPickerUiState(
            searchQuery = "",
            isLoading = false,
            apps = listOf(
                AppLimitItem(
                    "com.instagram.android", "Instagram", monitored = true, dailyLimitMinutes = 30,
                    weekendLimitMinutes = 90, scheduleSummary = "Bedtime, School hours",
                ),
                AppLimitItem(
                    "com.zhiliaoapp.musically", "TikTok", monitored = true, dailyLimitMinutes = 45,
                    weekendLimitMinutes = UNLIMITED_MINUTES, scheduleSummary = "Bedtime",
                ),
                AppLimitItem("com.spotify.music", "Spotify", monitored = false, dailyLimitMinutes = DEFAULT_LIMIT_MINUTES),
                AppLimitItem(
                    "com.google.android.youtube", "YouTube", monitored = true, dailyLimitMinutes = 90,
                    scheduleSummary = "Bedtime",
                ),
                AppLimitItem("com.reddit.frontpage", "Reddit", monitored = false, dailyLimitMinutes = DEFAULT_LIMIT_MINUTES),
            ),
        )
    }
}

/**
 * One row: an installed app plus whether/how it's limited.
 *
 * [weekendLimitMinutes] is the Saturday/Sunday override — `null` follows [dailyLimitMinutes], and
 * [UNLIMITED_MINUTES] means no cap. [scheduleSummary] names the blocking windows that cover this
 * app, so the "when" half of a limit is visible from where the "how much" half is set.
 */
data class AppLimitItem(
    val packageName: String,
    val label: String,
    val monitored: Boolean,
    val dailyLimitMinutes: Int,
    val weekendLimitMinutes: Int? = null,
    val scheduleSummary: String = NO_SCHEDULES_SUMMARY,
) {
    val weekendMode: WeekendMode
        get() = when {
            weekendLimitMinutes == null -> WeekendMode.SAME_AS_WEEKDAYS
            weekendLimitMinutes < 0 -> WeekendMode.UNLIMITED
            else -> WeekendMode.CUSTOM
        }

    /** The value the custom stepper should show, falling back to the everyday budget. */
    val weekendStepperMinutes: Int
        get() = weekendLimitMinutes?.takeIf { it > 0 } ?: dailyLimitMinutes
}

/** How an app's weekend budget is set — the three states the picker offers. */
enum class WeekendMode(val label: String) {
    SAME_AS_WEEKDAYS("Same as weekdays"),
    CUSTOM("Different at weekends"),
    UNLIMITED("No weekend limit"),
}

/** Subtitle when no blocking window covers an app. */
const val NO_SCHEDULES_SUMMARY = "No blocking schedule"

/** Starting budget when an app is first switched on. */
const val DEFAULT_LIMIT_MINUTES = 30

/** Stepper granularity and bounds for a daily limit. */
const val LIMIT_STEP_MINUTES = 15
const val MIN_LIMIT_MINUTES = 15
const val MAX_LIMIT_MINUTES = 480

/**
 * Clamps a stepper adjustment to the allowed band. Kept as a named function (rather than callers
 * using [steppedValue] directly) so the limit band lives in one place next to its constants.
 */
fun adjustLimit(current: Int, deltaSteps: Int): Int =
    steppedValue(current, deltaSteps, LIMIT_STEP_MINUTES, MIN_LIMIT_MINUTES, MAX_LIMIT_MINUTES)
