package com.example.unpawse.ui.settings

import com.example.unpawse.data.schedule.ScheduleWindow
import com.example.unpawse.data.usage.DAILY_EARNED_CAP_MINUTES
import com.example.unpawse.data.usage.MonitoredApp
import com.example.unpawse.data.usage.REWARD_COOLDOWN_MINUTES
import com.example.unpawse.ml.sensitivityToMinConfidence
import com.example.unpawse.service.BLOCK_REDEEM_WINDOW_MINUTES
import com.example.unpawse.service.REMINDER_OFF
import com.example.unpawse.service.UsageTracker
import com.example.unpawse.ui.format.formatMinutes
import com.example.unpawse.ui.format.countLabel
import com.example.unpawse.ui.photos.photoStorageSummary
import com.example.unpawse.ui.schedules.schedulesSummary
import kotlin.math.roundToInt

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
 * The Settings "Total daily limit" subtitle: how much screen time is budgeted across every limited
 * app, e.g. "4h 15m across 5 apps". It is a *derived total*, not a separate cap — unPawse enforces
 * per-app limits only, so this row reports rather than controls.
 *
 * Only *enabled* apps are counted, matching [monitoredAppsSummary]: a switched-off app keeps its
 * limit for a later re-enable but isn't spending any budget today.
 */
internal fun dailyLimitSummary(apps: List<MonitoredApp>): String {
    val enabled = apps.filter { it.enabled }
    if (enabled.isEmpty()) return "No limits set yet"

    val total = formatMinutes(enabled.sumOf { it.dailyLimitMinutes })
    return "$total across ${countLabel(enabled.size, "app")}"
}

/** The "Reminder frequency" value, e.g. "Every 30m" / "Off". */
internal fun reminderLabel(minutes: Int): String =
    if (minutes <= REMINDER_OFF) "Off" else "Every ${formatMinutes(minutes)}"

/** The "Warning before lock" value, e.g. "5 minutes before" / "Off". */
internal fun warningLabel(minutes: Int): String = when {
    minutes <= UsageTracker.WARNING_OFF -> "Off"
    minutes == 1 -> "1 minute before"
    else -> "$minutes minutes before"
}

/** Subtitle for the reward-grant row, e.g. "15m back per verified cat". */
internal fun earnedTimeSummary(minutesPerCat: Int): String =
    "${formatMinutes(minutesPerCat)} back per verified cat"

/**
 * The three bounds on earning, stated in one place: the per-app daily cap, the cooldown between
 * grants, and how long an armed block stays redeemable.
 *
 * **Read from the policy constants, never written as literals** — that is the whole point of the
 * row. Copy quoting a number the code no longer uses would be worse than the silence it replaces,
 * since the user would have no way to tell which of the two was lying.
 *
 * Deliberately takes no arguments and no UI state: every figure here is a constant, so there is
 * nothing for `toSettingsUiState` to shape. A tempting "four cats at your grant" phrasing was left
 * out because the cap *trims* a grant rather than refusing it — at a 45m grant the honest answer is
 * "one full cat and a partial one", which is not a number worth inventing a sentence for.
 */
internal fun rewardRulesSummary(): String =
    "Up to ${formatMinutes(DAILY_EARNED_CAP_MINUTES)} per app each day, " +
        "one cat every ${countLabel(REWARD_COOLDOWN_MINUTES, "minute")}, and only within " +
        "${countLabel(BLOCK_REDEEM_WINDOW_MINUTES, "minute")} of a block."

/**
 * The confidence gate the sensitivity slider currently produces, e.g. "70% match". Derived from
 * [sensitivityToMinConfidence] — the same function the detector gates on — so the number shown is
 * the number enforced, unlike the hardcoded "85% minimum match" row this replaced.
 */
internal fun minConfidenceLabel(sensitivity: Float): String =
    "${(sensitivityToMinConfidence(sensitivity) * 100).roundToInt()}% match"

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
    dailySummaryEnabled: Boolean,
    earnedMinutesPerCat: Int,
    warningMinutes: Int,
    reminderMinutes: Int,
    photoCount: Int,
    photoStorageBytes: Long,
    monitoredApps: List<MonitoredApp>,
    scheduleWindows: List<ScheduleWindow>,
    usageAccessGranted: Boolean,
    overlayAccessGranted: Boolean,
    notificationsGranted: Boolean,
    versionLabel: String,
): SettingsUiState = SettingsUiState(
    userName = userName,
    dailyLimitLabel = dailyLimitSummary(monitoredApps),
    appLimitsSummary = monitoredAppsSummary(monitoredApps),
    schedulesSummary = schedulesSummary(scheduleWindows),
    usageAccessGranted = usageAccessGranted,
    overlayAccessGranted = overlayAccessGranted,
    notificationsGranted = notificationsGranted,
    sensitivity = sensitivity,
    dailySummaryEnabled = dailySummaryEnabled,
    earnedMinutesPerCat = earnedMinutesPerCat,
    warningMinutes = warningMinutes,
    warningBeforeLock = warningLabel(warningMinutes),
    reminderMinutes = reminderMinutes,
    reminderFrequency = reminderLabel(reminderMinutes),
    photosSummary = photoStorageSummary(photoCount, photoStorageBytes),
    versionLabel = versionLabel,
)
