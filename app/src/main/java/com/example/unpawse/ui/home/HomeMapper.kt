package com.example.unpawse.ui.home

import com.example.unpawse.data.capture.Capture
import com.example.unpawse.data.capture.STREAK_CELEBRATION_DAYS
import com.example.unpawse.data.capture.currentStreakDays
import com.example.unpawse.data.capture.toLocalDate
import com.example.unpawse.data.usage.DailyUsage
import com.example.unpawse.data.usage.MonitoredApp
import com.example.unpawse.data.usage.dailyBudget
import com.example.unpawse.data.usage.effectiveLimitMinutes
import com.example.unpawse.data.usage.isLimitReached
import com.example.unpawse.ui.format.avatarInitialFor
import com.example.unpawse.ui.format.displayNameOf
import com.example.unpawse.ui.format.formatMinutes
import com.example.unpawse.ui.format.formatSeconds
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val TIME_FORMAT = DateTimeFormatter.ofPattern("h:mm a")

/** Below this much remaining budget, the banner switches to an "almost there" nudge. */
private const val LOW_REMAINING_SECONDS = 15 * 60L

/** Time-of-day greeting for the Home header. Pure, so it's unit-testable without a clock. */
internal fun greetingFor(time: LocalTime): String = when (time.hour) {
    in 5..11 -> "Good morning,"
    in 12..16 -> "Good afternoon,"
    else -> "Good evening,"
}

/**
 * Builds [HomeUiState] from today's usage + the capture history. Pure and parameterised on
 * [today]/[zone] so it's unit-testable without a clock (same shape as `GalleryMapper`).
 *
 * The greeting and the profile (name/avatar) are now real: the greeting follows the time of day and
 * the name comes from the persisted setting (blank falls back to a friendly default). Next-break
 * countdown and banner are still placeholder copy — see [HomeUiState.sample].
 */
internal fun toHomeUiState(
    monitoredApps: List<MonitoredApp>,
    todayUsage: List<DailyUsage>,
    captures: List<Capture>,
    userName: String,
    today: LocalDate = LocalDate.now(),
    zone: ZoneId = ZoneId.systemDefault(),
    time: LocalTime = LocalTime.now(zone),
): HomeUiState {
    val displayName = displayNameOf(userName)
    val enabled = monitoredApps.filter { it.enabled }
    val usageByPackage = todayUsage.associateBy { it.packageName }

    // Shared with Stats, so both screens honour weekend overrides and skip uncapped apps.
    val budget = dailyBudget(enabled, usageByPackage, today.dayOfWeek)
    val remainingSeconds = budget?.remainingSeconds ?: 0L
    // Deliberately every enabled app, uncapped ones included: this is a total, not a budget claim.
    val usedSeconds = enabled.sumOf { usageByPackage[it.packageName]?.usedSeconds ?: 0 }

    val captureDates = captures.map { it.capturedAt.toLocalDate(zone) }.toSet()
    val capturesToday = captures.filter { it.capturedAt.toLocalDate(zone) == today }
    val streakDays = currentStreakDays(captureDates, today)

    val banner = buildBanner(
        streakDays = streakDays,
        remainingSeconds = remainingSeconds,
        budgetSeconds = budget?.budgetSeconds ?: 0L,
    )

    return HomeUiState.sample().copy(
        greeting = greetingFor(time),
        userName = displayName,
        avatarInitial = avatarInitialFor(userName),
        screenTimeUsedLabel = formatSeconds(usedSeconds),
        progressFraction = budget?.usedFraction ?: 0f,
        remainingLabel = formatSeconds(remainingSeconds),
        streakDays = streakDays,
        catCount = capturesToday.size,
        pausedAppsCount = enabled.size,
        activities = buildActivities(enabled, usageByPackage, capturesToday, zone, today.dayOfWeek),
        bannerTitle = banner.title,
        bannerBody = banner.body,
    )
}

/** The Home banner's two lines. */
internal data class HomeBanner(val title: String, val body: String)

/**
 * Copy for the celebratory Home banner, derived from real metrics — never an invented number.
 * Ranked most-noteworthy first: setup guidance when nothing is monitored, then a streak celebration,
 * then budget-based nudges. Kept pure so each branch is unit-testable.
 */
internal fun buildBanner(
    streakDays: Int,
    remainingSeconds: Long,
    budgetSeconds: Long,
): HomeBanner = when {
    budgetSeconds == 0L -> HomeBanner(
        title = "Welcome to unPawse!",
        body = "Add app limits in Settings to start tracking your screen time.",
    )
    streakDays >= STREAK_CELEBRATION_DAYS -> HomeBanner(
        title = "🔥 $streakDays-day streak!",
        body = "Photograph your cat today to keep it going.",
    )
    remainingSeconds <= 0L -> HomeBanner(
        title = "Limit reached",
        body = "Photograph your cat to earn more screen time.",
    )
    remainingSeconds < LOW_REMAINING_SECONDS -> HomeBanner(
        title = "Almost there",
        body = "Only ${formatSeconds(remainingSeconds)} of screen time left today.",
    )
    else -> HomeBanner(
        title = "Looking sharp today!",
        body = "You still have ${formatSeconds(remainingSeconds)} of screen time left.",
    )
}

/**
 * Recent activity, most urgent first: apps blocked *right now*, then today's verified cats.
 *
 * Block events aren't persisted (there's no event table), so a "blocked" row is derived from an
 * app currently being over budget — which is why its time reads "Now" rather than a timestamp.
 */
private fun buildActivities(
    enabledApps: List<MonitoredApp>,
    usageByPackage: Map<String, DailyUsage>,
    capturesToday: List<Capture>,
    zone: ZoneId,
    day: DayOfWeek,
): List<ActivityItem> {
    val blocked = enabledApps.mapNotNull { app ->
        val usage = usageByPackage[app.packageName] ?: return@mapNotNull null
        // Asks the enforcement path's own question. Open-coding it against `dailyLimitMinutes` put an
        // uncapped app permanently in the list, reporting "Daily limit of -1m reached."
        val limit = effectiveLimitMinutes(app.dailyLimitMinutes, app.weekendLimitMinutes, day)
        if (!isLimitReached(limit, usage.usedSeconds, usage.earnedSeconds)) return@mapNotNull null
        ActivityItem(
            kind = ActivityKind.BLOCKED,
            title = "${app.appLabel} Blocked",
            subtitle = "Daily limit of ${formatMinutes(limit)} reached.",
            time = "Now",
        )
    }

    val verified = capturesToday.map { capture ->
        ActivityItem(
            kind = ActivityKind.VERIFIED,
            title = "Cat Verified",
            subtitle = captureSubtitle(capture),
            time = Instant.ofEpochMilli(capture.capturedAt).atZone(zone).format(TIME_FORMAT),
        )
    }

    return blocked + verified
}

/**
 * What a capture is reported as having done. This used to read "Time earned back." for *every*
 * cat, which was simply untrue for the common case: a cat photographed while nothing is blocked
 * earns nothing, and saying otherwise made spamming the shutter look like it was working.
 *
 * [Capture.earnedMinutes] is the recorded truth, so an unearned capture now says only what did
 * happen — the photo was saved.
 */
internal fun captureSubtitle(capture: Capture): String {
    val match = "${(capture.confidence * 100).toInt()}% match."
    return if (capture.earnedMinutes > 0) {
        "$match +${formatMinutes(capture.earnedMinutes)} earned back."
    } else {
        "$match Saved to your gallery."
    }
}

