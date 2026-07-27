package com.example.unpawse.ui.settings

import com.example.unpawse.data.settings.SettingsRepository

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
    /** Whether the user has granted usage access — without it nothing can be monitored at all. */
    val usageAccessGranted: Boolean = false,
    /** Whether we can draw over other apps — without it a reached limit can't be blocked. */
    val overlayAccessGranted: Boolean = false,
    val sensitivity: Float = SettingsRepository.DEFAULT_SENSITIVITY,
    val dailySummaryEnabled: Boolean = SettingsRepository.DEFAULT_DAILY_SUMMARY,
    val darkMode: Boolean = false,

    // --- Rows still showing mockup copy, each replaced by a later phase of the Settings build-out.
    // They live here rather than in sample() so production has exactly one source of truth, and so
    // deleting a placeholder is a compile-time-visible change rather than a silent one.
    /** Replaced by the configurable "time earned per cat" value. */
    val breakDurationLabel: String = "15 minutes every hour",
    /** Replaced by the persisted reminder interval. */
    val reminderFrequency: String = "Every 30m",
    /** Replaced by the persisted warning threshold. */
    val warningBeforeLock: String = "5 minutes",

    /** Always supplied by the mapper from `BuildConfig`; blank only in a bare test fixture. */
    val versionLabel: String = "",
) {
    companion object {
        /** Preview-only fixture. Never build production state from this — see the class KDoc. */
        fun sample(darkMode: Boolean = false) = SettingsUiState(
            userName = "Sophia",
            dailyLimitLabel = "4h 15m across 5 apps",
            appLimitsSummary = "Instagram, TikTok, 3 others",
            usageAccessGranted = true,
            overlayAccessGranted = true,
            sensitivity = 0.65f,
            dailySummaryEnabled = false,
            darkMode = darkMode,
            versionLabel = "1.0 (1)",
        )
    }
}
