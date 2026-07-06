package com.example.unpawse.ui.settings

/**
 * Immutable UI state for the Settings screen. The mutable-looking controls (slider, switches) are
 * driven by the values here plus callbacks on [SettingsScreen]; the hosting layer owns the state.
 */
data class SettingsUiState(
    val dailyLimitLabel: String,
    val appLimitsSummary: String,
    val breakDurationLabel: String,
    val sensitivity: Float,
    val requireLivePhoto: Boolean,
    val confidenceLabel: String,
    val reminderFrequency: String,
    val warningBeforeLock: String,
    val dailySummaryEnabled: Boolean,
    val darkMode: Boolean,
    val versionLabel: String,
) {
    companion object {
        fun sample(darkMode: Boolean = false) = SettingsUiState(
            dailyLimitLabel = "2 hours 30 minutes",
            appLimitsSummary = "Instagram, TikTok, 3 others",
            breakDurationLabel = "15 minutes every hour",
            sensitivity = 0.65f,
            requireLivePhoto = false,
            confidenceLabel = "85% minimum match",
            reminderFrequency = "Every 30m",
            warningBeforeLock = "5 minutes",
            dailySummaryEnabled = false,
            darkMode = darkMode,
            versionLabel = "2.4.1-alpha",
        )
    }
}
