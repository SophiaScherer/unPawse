package com.example.unpawse.ui.settings

import com.example.unpawse.data.usage.MonitoredApp

/** How many app names to spell out before collapsing the rest into "+N others". */
private const val NAMES_SHOWN = 2

/**
 * Builds the Settings "Individual app limits" subtitle from the monitored apps, e.g.
 * "Instagram, TikTok, 3 others" (matching the mockup). Replaces what used to be a hardcoded string.
 * Only *enabled* apps count — a switched-off app keeps its row (so its limit survives) but isn't
 * being limited, so it shouldn't be advertised as such. Pure, so it's unit-tested.
 */
internal fun monitoredAppsSummary(apps: List<MonitoredApp>): String {
    val enabled = apps.filter { it.enabled }
    val shown = enabled.take(NAMES_SHOWN).joinToString(", ") { it.appLabel }
    val others = enabled.size - NAMES_SHOWN

    return when {
        enabled.isEmpty() -> "No apps limited yet"
        others <= 0 -> shown
        others == 1 -> "$shown, 1 other"
        else -> "$shown, $others others"
    }
}

/**
 * The About row's version string, e.g. "1.0 (1)". Takes the name and code as parameters rather than
 * reading `BuildConfig` directly so it stays pure — the ViewModel's factory supplies the real values.
 */
internal fun versionLabel(versionName: String, versionCode: Int): String = "$versionName ($versionCode)"

/**
 * Shapes the persisted values and permission state into [SettingsUiState]. Pure and parameterised
 * rather than flow-aware, so the whole screen's data shaping is unit-testable — the ViewModel is
 * left doing nothing but wiring flows together (the convention every other screen follows).
 *
 * Dark mode is deliberately absent: it is owned by `UnPawseApp` so it can drive the whole theme, and
 * is overlaid onto this state by [SettingsRoute].
 *
 * Fields not passed here keep the placeholder defaults declared on [SettingsUiState]; each is
 * replaced by a later phase of the Settings build-out.
 */
internal fun toSettingsUiState(
    userName: String,
    sensitivity: Float,
    requireLivePhoto: Boolean,
    dailySummaryEnabled: Boolean,
    monitoredApps: List<MonitoredApp>,
    usageAccessGranted: Boolean,
    overlayAccessGranted: Boolean,
    versionLabel: String,
): SettingsUiState = SettingsUiState(
    userName = userName,
    appLimitsSummary = monitoredAppsSummary(monitoredApps),
    usageAccessGranted = usageAccessGranted,
    overlayAccessGranted = overlayAccessGranted,
    sensitivity = sensitivity,
    requireLivePhoto = requireLivePhoto,
    dailySummaryEnabled = dailySummaryEnabled,
    versionLabel = versionLabel,
)
