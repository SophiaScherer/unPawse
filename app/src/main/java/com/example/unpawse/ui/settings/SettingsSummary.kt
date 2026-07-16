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
