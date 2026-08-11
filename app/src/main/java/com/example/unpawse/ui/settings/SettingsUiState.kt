package com.example.unpawse.ui.settings

import com.example.unpawse.data.settings.SettingsRepository
import com.example.unpawse.ui.theme.ThemeMode

/**
 * Immutable UI state for the Settings screen. The mutable-looking controls (slider, switches) are
 * driven by the values here plus callbacks on [SettingsScreen]; the hosting layer owns the state.
 *
 * Every field carries a real default so production builds this type directly. It used to be built
 * as `sample().copy(...)`, which shipped `@Preview` mockup copy as the source of truth — against the
 * project rule that `sample()` is preview-only.
 */
data class SettingsUiState(
    /** The user's display name; blank means "not set yet" (the UI shows a fallback). */
    val userName: String = "",
    /** Derived total of the enabled per-app limits, e.g. "4h 15m across 5 apps". */
    val dailyLimitLabel: String = "No limits set yet",
    val appLimitsSummary: String = "No apps limited yet",
    /** Blocking-window summary, e.g. "2 schedules · Bedtime 10:00 PM". */
    val schedulesSummary: String = "No schedules yet",
    /** Whether the user has granted usage access — without it nothing can be monitored at all. */
    val usageAccessGranted: Boolean = false,
    /** Whether we can draw over other apps — without it a reached limit can't be blocked. */
    val overlayAccessGranted: Boolean = false,
    /** Whether we may post notifications — without it the whole Notifications group is inert. */
    val notificationsGranted: Boolean = false,
    val sensitivity: Float = SettingsRepository.DEFAULT_SENSITIVITY,
    val dailySummaryEnabled: Boolean = SettingsRepository.DEFAULT_DAILY_SUMMARY,
    /** Minutes one verified cat buys back; drives the reward loop. */
    val earnedMinutesPerCat: Int = SettingsRepository.DEFAULT_EARNED_MINUTES_PER_CAT,
    /** Size of the cat-photo library, e.g. "142 photos · 38.4 MB". */
    val photosSummary: String = "No photos yet",
    /** Minutes of remaining budget at which to warn; 0 is off. */
    val warningMinutes: Int = SettingsRepository.DEFAULT_WARNING_MINUTES,
    /** Display form of [warningMinutes], e.g. "5 minutes before". */
    val warningBeforeLock: String = "5 minutes before",
    /** Owned by `UnPawseApp` (it drives the whole theme) and overlaid onto this state for display. */
    val themeMode: ThemeMode = ThemeMode.SYSTEM,

    /** Minutes between in-app reminders; 0 is off. */
    val reminderMinutes: Int = SettingsRepository.DEFAULT_REMINDER_MINUTES,
    /** Display form of [reminderMinutes], e.g. "Every 30m". */
    val reminderFrequency: String = "Off",

    /** Always supplied by the mapper from `BuildConfig`; blank only in a bare test fixture. */
    val versionLabel: String = "",
) {
    companion object {
        /** Preview-only fixture. Never build production state from this — see the class KDoc. */
        fun sample(themeMode: ThemeMode = ThemeMode.SYSTEM) = SettingsUiState(
            userName = "Sophia",
            dailyLimitLabel = "4h 15m across 5 apps",
            appLimitsSummary = "Instagram, TikTok, 3 others",
            schedulesSummary = "2 schedules · Bedtime 10:00 PM",
            usageAccessGranted = true,
            overlayAccessGranted = true,
            notificationsGranted = true,
            sensitivity = 0.65f,
            dailySummaryEnabled = false,
            photosSummary = "142 photos · 38.4 MB",
            themeMode = themeMode,
            versionLabel = "1.0 (1)",
        )
    }
}
