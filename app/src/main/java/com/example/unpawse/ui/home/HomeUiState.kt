package com.example.unpawse.ui.home

import com.example.unpawse.ui.format.DEFAULT_AVATAR_INITIAL
import com.example.unpawse.ui.format.DEFAULT_DISPLAY_NAME
import com.example.unpawse.ui.format.NO_DATA

/**
 * Immutable UI state for the Home screen. Screens are stateless and render whatever state they are
 * given; [sample] is hardcoded mockup data and is now `@Preview`-only, matching `StatsUiState` and
 * `SettingsUiState`. Production builds this from [toHomeUiState], or from [empty] before the first
 * emission lands.
 */
data class HomeUiState(
    val greeting: String,
    val userName: String,
    val avatarInitial: Char,
    val screenTimeUsedLabel: String,
    val progressFraction: Float,
    val remainingLabel: String,
    val streakDays: Int,
    val catCount: Int,
    val pausedAppsCount: Int,
    val activities: List<ActivityItem>,
    val bannerTitle: String,
    val bannerBody: String,
) {
    companion object {
        /**
         * The honest nothing-yet state, seeded into the ViewModel's `StateFlow` for the frame before
         * the repositories emit. It used to be [sample], so every cold Home render briefly showed the
         * mockup's "Sophia", her 2h 15m and her 12-day streak to whoever opened the app.
         */
        fun empty() = HomeUiState(
            greeting = "",
            userName = DEFAULT_DISPLAY_NAME,
            avatarInitial = DEFAULT_AVATAR_INITIAL,
            screenTimeUsedLabel = NO_DATA,
            progressFraction = 0f,
            remainingLabel = NO_DATA,
            streakDays = 0,
            catCount = 0,
            pausedAppsCount = 0,
            activities = emptyList(),
            bannerTitle = "",
            bannerBody = "",
        )

        fun sample() = HomeUiState(
            greeting = "Welcome back,",
            userName = "Sophia",
            avatarInitial = 'S',
            screenTimeUsedLabel = "2h 15m",
            progressFraction = 0.72f,
            remainingLabel = "45m",
            streakDays = 12,
            catCount = 8,
            pausedAppsCount = 12,
            activities = listOf(
                ActivityItem(ActivityKind.VERIFIED, "Instagram Verified", "Calico cat detected. +5m usage.", "10:45 AM"),
                ActivityItem(ActivityKind.BLOCKED, "TikTok Blocked", "Daily limit of 30m reached.", "09:12 AM"),
                ActivityItem(ActivityKind.GOAL, "Morning Goal Met", "No phone use before 8:00 AM!", "08:00 AM"),
            ),
            bannerTitle = "Looking sharp today!",
            bannerBody = "You're in the top 5% of mindful users this week.",
        )
    }
}

/**
 * Live state of the Home "Focus" card (which replaces the mockup's placeholder next-break countdown).
 * Driven off [com.example.unpawse.service.FocusSession] with a per-second ticker in the ViewModel.
 */
data class FocusCardState(
    val active: Boolean,
    /** Remaining time as a countdown ("14:02"), empty when no session is running. */
    val remainingLabel: String,
) {
    companion object {
        val Inactive = FocusCardState(active = false, remainingLabel = "")
    }
}

/** Countdown copy for a focus session: "M:SS", or "H:MM:SS" past an hour. */
internal fun formatCountdown(remainingMillis: Long): String {
    val totalSeconds = (remainingMillis / 1000).coerceAtLeast(0)
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    return if (hours > 0) {
        "%d:%02d:%02d".format(hours, minutes, seconds)
    } else {
        "%d:%02d".format(minutes, seconds)
    }
}

/** A single Recent Activity event. [kind] drives the icon + tint in the screen. */
data class ActivityItem(
    val kind: ActivityKind,
    val title: String,
    val subtitle: String,
    val time: String,
)

enum class ActivityKind { VERIFIED, BLOCKED, GOAL }
