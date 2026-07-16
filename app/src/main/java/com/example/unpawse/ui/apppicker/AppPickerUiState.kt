package com.example.unpawse.ui.apppicker

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
                AppLimitItem("com.instagram.android", "Instagram", monitored = true, dailyLimitMinutes = 30),
                AppLimitItem("com.zhiliaoapp.musically", "TikTok", monitored = true, dailyLimitMinutes = 45),
                AppLimitItem("com.spotify.music", "Spotify", monitored = false, dailyLimitMinutes = DEFAULT_LIMIT_MINUTES),
                AppLimitItem("com.google.android.youtube", "YouTube", monitored = true, dailyLimitMinutes = 90),
                AppLimitItem("com.reddit.frontpage", "Reddit", monitored = false, dailyLimitMinutes = DEFAULT_LIMIT_MINUTES),
            ),
        )
    }
}

/** One row: an installed app plus whether/how it's limited. */
data class AppLimitItem(
    val packageName: String,
    val label: String,
    val monitored: Boolean,
    val dailyLimitMinutes: Int,
)

/** Starting budget when an app is first switched on. */
const val DEFAULT_LIMIT_MINUTES = 30

/** Stepper granularity and bounds for a daily limit. */
const val LIMIT_STEP_MINUTES = 15
const val MIN_LIMIT_MINUTES = 15
const val MAX_LIMIT_MINUTES = 480

/**
 * Formats a daily limit compactly for the stepper ("45m", "2h", "1h 30m"). Pure so it's unit-tested
 * without a device.
 */
fun formatLimit(minutes: Int): String {
    val hours = minutes / 60
    val mins = minutes % 60
    return when {
        hours == 0 -> "${mins}m"
        mins == 0 -> "${hours}h"
        else -> "${hours}h ${mins}m"
    }
}

/** Clamps a stepper adjustment to the allowed band. */
fun adjustLimit(current: Int, deltaSteps: Int): Int =
    (current + deltaSteps * LIMIT_STEP_MINUTES).coerceIn(MIN_LIMIT_MINUTES, MAX_LIMIT_MINUTES)
